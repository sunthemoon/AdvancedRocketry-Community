#!/usr/bin/env python3
"""One counted W198 row under an externally owned heavy-JVM lock; never auto-retry."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import uuid

import run_dedicated_server_smoke as smoke
from run_v060_flight_server_smoke import _server_command
from run_w152_native import MARKER
from run_v180_landed_disassembly_cuts import nbt_file
from w152_native_inputs import preserve_native, replay_native, compact_trace, digest
import w198_terrain as oracle

LOCK = Path("D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm/owner.txt")
TEMP = Path("D:/GitHub/ARCE-Task-Evidence/Temp").resolve()
REPO = Path(__file__).resolve().parents[1]


def write(path, value):
    path.write_text(json.dumps(value, indent=2), encoding="utf-8")


def check_pin(commit, token):
    oracle.require(token and LOCK.read_text(encoding="utf-8-sig").startswith(token), "Missing owned machine lock")
    oracle.require(subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=REPO, text=True).strip() == commit
                   and not subprocess.check_output(["git", "status", "--porcelain"], cwd=REPO, text=True),
                   "Exact clean committed source required")


def command_for(args, row, stage):
    cut = row["input"]["cut"] if stage == "start" else row["input"]["recovery"] if stage == "recovery-cut" else "NONE"
    command = _server_command(args.java); command[2] = "-Xmx4G"
    command[1:1] = ["-XX:ActiveProcessorCount=4", "-Dadvancedrocketrycommunity.w152Native=true",
                    "-Dadvancedrocketrycommunity.w198Integrated=true",
                    "-Dadvancedrocketrycommunity.w152Stage=" + stage,
                    "-Dadvancedrocketrycommunity.w152Cut=" + cut,
                    "-Dadvancedrocketrycommunity.w152InitialD=" + row["input"]["initial"],
                    "-Dadvancedrocketrycommunity.w152PayloadHosts=ALL",
                    "-Dadvancedrocketrycommunity.w152Commit=" + args.source_commit]
    return command, cut


def preserve(world, evidence, stage):
    # Includes failed observations too: no failure may discard its native reproduction.
    names = [path.relative_to(world).as_posix() for path in world.rglob("*") if path.is_file()
             and (path.suffix == ".mca" or path.parent.name in ("data", "playerdata") and path.suffix == ".dat"
                  or path.name == "w198-input.nbt")]
    return preserve_native(world, evidence, stage, dict(files=dict.fromkeys(names)))


def run(args):
    row = next(item for item in oracle.plan() if item["id"] == args.row)
    tranche = args.evidence_dir.resolve(); tranche.mkdir(parents=True, exist_ok=True)
    oracle.require(sum(path.stat().st_size for path in tranche.parent.rglob("*") if path.is_file()) < 95_000_000,
                   "Evidence headroom below 5 MB; stop before launch")
    ledger_file = tranche / "starts.json"
    ledger = json.loads(ledger_file.read_text()) if ledger_file.exists() else []
    previous = [json.loads(path.read_text()) for path in tranche.parent.glob("tranche-*/row-*/result.json")]
    oracle.check_attempt(previous, args.row)
    attempt = 1 + sum(item["row"] == args.row for item in previous)
    oracle.require(len(ledger) + 4 <= 60, "Insufficient tranche headroom for four starts")
    evidence = tranche / f"row-{args.row}-attempt-{attempt}"; evidence.mkdir()
    session = TEMP / ("W198-" + uuid.uuid4().hex); session.mkdir(); token = uuid.uuid4().hex
    marker = session / ".w198-owned"; marker.write_text(token)
    result = dict(row=args.row, mapping=row, attempt=attempt, source=args.source_commit,
                  jar_sha256=digest(args.jar), status="RUNNING", stages=[])
    process = None; stable = []; fixture = None
    try:
        check_pin(args.source_commit, args.lock_owner)
        libraries = args.source_server.resolve() / "libraries"
        oracle.require(libraries.is_dir() and not smoke.is_link_or_junction(libraries)
                       and not any(smoke.is_link_or_junction(p) for p in libraries.rglob("*")), "Unsafe native template")
        shutil.copytree(libraries, session / "libraries")
        (session / "mods").mkdir(); shutil.copy2(args.jar, session / "mods" / args.jar.name)
        props = smoke.expected_server_properties(smoke.allocate_port(), True)
        props.update({"sync-chunk-writes": "true", "enable-rcon": "false", "level-name": "world", "level-type": "minecraft:flat",
                      "max-tick-time": "60000", "view-distance": "3", "simulation-distance": "3",
                      "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}], "biome": "minecraft:plains"})})
        (session / "server.properties").write_text("".join(f"{key}={value}\n" for key, value in sorted(props.items())), encoding="ascii")
        (session / "eula.txt").write_text("eula=true\n")
        world = session / "world"
        for stage in ("start", "recovery-cut", "recover1", "recover2"):
            check_pin(args.source_commit, args.lock_owner)
            oracle.require(digest(args.jar) == result["jar_sha256"], "Diagnostic JAR changed")
            for drive in ("C:/", "D:/"):
                oracle.require(shutil.disk_usage(drive).free >= 10 * 1024**3, "Disk below 10 GiB")
            command, cut = command_for(args, row, stage)
            oracle.check_launch(ledger, args.row, attempt, stage)
            entry = dict(row=args.row, attempt=attempt, stage=stage, source=args.source_commit,
                         jar_sha256=result["jar_sha256"], command=command, status="LAUNCH_ATTEMPT")
            ledger.append(entry); write(ledger_file, ledger)
            observation = dict(stage=stage, cut=cut); result["stages"].append(observation)
            try:
                process = smoke.CapturedProcess(command, session, evidence / (stage + ".log"))
                line = process.wait_for(MARKER, 300); match = MARKER.search(process.lines[line])
                if match[3]:
                    oracle.require(match[3] == cut and cut != "NONE", "Unexpected native cut")
                    process.abort()
                else:
                    process.command("stop"); oracle.require(process.finish() == 0, "Native stop failed")
            finally:
                if process is not None and process.process.poll() is None: process.abort()
                if world.exists():
                    observation["raw_inputs"] = preserve(world, evidence, stage)
                report = world / "w152-native.json"
                if report.exists(): shutil.copy2(report, evidence / (stage + ".json"))
                entry["ready"] = process is not None and any(smoke.READY_MARKER.search(line) for line in process.lines)
                write(ledger_file, ledger); write(evidence / "result.json", result)
            raw = json.loads((evidence / (stage + ".json")).read_text())
            oracle.require(raw["status"] == ("STABLE" if cut == "NONE" else "CUT"), f"{stage}: {raw.get('detail')}")
            oracle.require(not any("/ERROR]" in line or "/FATAL]" in line for line in process.lines), "Unexpected native ERROR/FATAL")
            if fixture is None:
                fixture = nbt_file(world / "w198-input.nbt", 8_388_608); write(evidence / "fixture.json", fixture)
            state = oracle.inspect(world, fixture, row, stage)
            write(evidence / (stage + "-native.json"), state)
            replay = session / ("replay-" + stage)
            replay_native(evidence / observation["raw_inputs"]["file"], replay)
            oracle.require(oracle.comparable(oracle.inspect(replay, fixture, row, stage)) == oracle.comparable(state),
                           "Retained records fail independent replay")
            observation.update(status="PASS", authority=state["authority"])
            if stage in ("recover1", "recover2"): stable.append(oracle.comparable(state))
        oracle.require(stable[0] == stable[1], "Two clean restarts changed the native state")
        result["status"] = "PASS"
    except Exception as failure:
        result.update(status="FAIL", error=str(failure))
    finally:
        if process is not None and process.process.poll() is None: process.abort()
        result["traces"] = [compact_trace(path) for path in sorted(evidence.iterdir())
                            if path.suffix in (".log", ".json") and path.name != "result.json"]
        write(evidence / "result.json", result)
        if (session.resolve().parent == TEMP and not smoke.is_link_or_junction(session)
                and marker.read_text() == token): shutil.rmtree(session)
    oracle.require(sum(path.stat().st_size for path in tranche.parent.rglob("*") if path.is_file()) < 100_000_000,
                   "Evidence exceeds 100 MB")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("source-server", "jar", "evidence-dir"): parser.add_argument("--" + name, type=Path, required=True)
    for name in ("source-commit", "java", "row", "lock-owner"): parser.add_argument("--" + name, required=True)
    args = parser.parse_args(); result = run(args)
    print(json.dumps(result, indent=2)); return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
