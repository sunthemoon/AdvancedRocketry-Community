#!/usr/bin/env python3
"""Sealed C0-C5 process cuts with independent native images and two stable restarts.

Run only under the outer W152 atomic heavy-JVM lock, using a committed diagnostic JAR.
This campaign does not certify the separate consumer-only-save residual experiment.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import uuid

import run_v180_escrow_forced_stop as escrow
from run_v180_landed_disassembly_cuts import block_at, chunk_record, nbt_file, uuid_of
from sealed_cut_oracle import elect, require_inventory

smoke = escrow.smoke
CUTS = ("NONE", "C0_BASELINE_SAVED", "PREPARE_PROOF_CURRENT", "C1_RETAIN_BEFORE", "C1_RETAIN_AFTER",
        "C1_MOVE_BEFORE", "C1_MOVE_AFTER", "C1_ACK", "C2_STAGE_BEFORE", "C2_STAGE_AFTER", "PARTIAL_C_SAVED",
        "SEALED:TERRAIN:BARRIER_STORE_AFTER", "SEALED:TERRAIN:BARRIER_FORCE_AFTER", "SEALED:TERRAIN:BARRIER_READBACK_AFTER",
        "SEALED_PROOF_CURRENT", "C3_ELECT_BEFORE", "C3_MOVE_BEFORE", "C3_MOVE_AFTER", "C3_UNKNOWN", "C3_ACK",
        "C4_DISCARD_BEFORE", "C4_DISCARD_AFTER", "COMPLETE:ENTITY:BARRIER_STORE_AFTER",
        "COMPLETE:ENTITY:BARRIER_FORCE_AFTER", "COMPLETE:ENTITY:BARRIER_READBACK_AFTER", "COMPLETE_PROOF_CURRENT",
        "C5_REMOVE_BEFORE", "C5_MOVE_BEFORE", "C5_MOVE_AFTER", "C5_UNKNOWN", "C5_ACK",
        "UNSEAL_PREPARE_BEFORE", "UNSEAL_PREPARE_AFTER", "UNSEAL_PUBLISH_BEFORE", "UNSEAL_PUBLISH_AFTER",
        "ROLLBACK_ERASE_BEFORE", "ROLLBACK_ERASE_AFTER")
MARKER = re.compile(r"W152_NATIVE_(DONE status=(\S+)|CUT boundary=(\S+))")
ORIGIN = (15, 100, 15)


def inspect(world: Path) -> dict:
    journal = world / "data/advancedrocketrycommunity_rocket_transactions.dat"
    records = nbt_file(journal, 67_108_864)["data"].get("transactions", []) if journal.exists() else []
    if len(records) > 1 or any(row.get("type") != "SEALED_DISASSEMBLY" for row in records):
        raise ValueError("Unexpected native journal cohort")
    phase = records[0]["phase"] if records else None
    entities, sealed, native, recipients, blocks, files = [], [], [], [], {}, {}
    for x in range(-1, 3):
        for z in range(-1, 3):
            region = world / "region" / f"r.{x >> 5}.{z >> 5}.mca"
            entity_file = world / "entities" / f"r.{x >> 5}.{z >> 5}.mca"
            terrain = chunk_record(region, x, z) or {}
            image = chunk_record(entity_file, x, z) or {}
            for row in image.get("Entities", []):
                if "RocketEntityData" in row:
                    entities.append(row)
                elif row.get("id") == "minecraft:item":
                    recipients.append(row)
            for dx in range(2):
                for dz in range(2):
                    at = (15 + dx, 100, 15 + dz)
                    if (at[0] >> 4, at[2] >> 4) != (x, z):
                        continue
                    name = block_at(terrain or None, *at); blocks[",".join(map(str, at))] = name
                    be = next((row for row in terrain.get("block_entities", []) if tuple(row.get(k) for k in ("x", "y", "z")) == at), None)
                    if name == "advancedrocketrycommunity:sealed_rocket_block":
                        if not be or "arce_sealed_rocket_member" not in be:
                            raise ValueError("Carrier lacks its native root")
                        sealed.append(be)
                    elif name not in ("minecraft:air", "<chunk-not-stored>", "<section-not-stored>"):
                        native.append(dict(state=name, block_entity=be))
            for file in (region, entity_file):
                if file.exists() and str(file.relative_to(world)) not in files:
                    files[str(file.relative_to(world))] = escrow.digest(file)
    if journal.exists():
        files[str(journal.relative_to(world))] = escrow.digest(journal)
    for file in (world / "playerdata").glob("*.dat"):
        recipients.append(nbt_file(file, 4_194_304))
        files[str(file.relative_to(world))] = escrow.digest(file)
    observed = elect(phase, entities, sealed, native, recipients, retained=records[0].get("snapshot") if records else None)
    require_inventory(observed, {"original": 14})
    observed.update(phase=phase, blocks=blocks, entities=sorted(uuid_of(row["UUID"]) for row in entities),
                    entity_data=sorted(({key: row[key] for key in ("id", "UUID", "Pos", "RocketEntityData")} for row in entities),
                                       key=lambda row: uuid_of(row["UUID"])),
                    records=records, native=native, sealed=sealed, files=files)
    return observed


def comparable(state: dict) -> dict:
    return {key: value for key, value in state.items() if key != "files"}


def run(args) -> dict:
    repo = Path(__file__).resolve().parents[1]
    commit = args.source_commit
    if args.source_export:
        if (repo / ".w152-export-source").read_text(encoding="ascii").strip() != commit:
            raise smoke.SmokeError("Pinned export marker differs from the tested source")
    elif (subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip() != commit
          or subprocess.check_output(["git", "status", "--porcelain"], cwd=repo, text=True)):
        raise smoke.SmokeError("Exact clean committed source required")
    token = os.environ.get("W152_LOCK_TOKEN", "")
    lock = Path("D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm/owner.txt")
    if not token or not lock.read_text(encoding="utf-8-sig").startswith(token):
        raise smoke.SmokeError("Outer atomic-lock ownership missing")
    evidence = args.evidence_dir.resolve(); evidence.mkdir(exist_ok=False)
    temp = Path("D:/GitHub/ARCE-Task-Evidence/Temp").resolve()
    session = temp / ("w152-native-" + uuid.uuid4().hex)
    owned = uuid.uuid4().hex; marker = session / ".w152-owned"
    result = dict(status="IN_PROGRESS", source_commit=commit, jar_sha256=escrow.digest(args.jar),
                  cut=args.cut, recovery_cut=args.recovery_cut, commands=[], stages=[])
    try:
        session.mkdir(); marker.write_text(owned, encoding="ascii")
        shutil.copytree(args.source_server / "libraries", session / "libraries")
        (session / "mods").mkdir(); shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = smoke.allocate_port(); smoke.write_server_configuration(session, port, True)
        props = smoke.expected_server_properties(port, True)
        props.update({"sync-chunk-writes": "true", "enable-rcon": "false", "level-name": "world", "level-type": "minecraft:flat",
                      "max-tick-time": "60000", "view-distance": "3", "simulation-distance": "3",
                      "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}], "biome": "minecraft:plains"})})
        (session / "server.properties").write_text("".join(f"{key}={value}\n" for key, value in sorted(props.items())), encoding="ascii")
        stages = ["start"] + (["recovery-cut"] if args.recovery_cut else []) + ["recover1", "recover2"]
        stable = []
        for stage in stages:
            for drive in (Path("C:/"), Path("D:/")):
                if shutil.disk_usage(drive).free < 10 * 1024**3:
                    raise smoke.SmokeError("Insufficient native-server disk headroom")
            cut = args.cut if stage == "start" else args.recovery_cut if stage == "recovery-cut" else "NONE"
            command = escrow._server_command(args.java); command[2] = "-Xmx4G"
            command[1:1] = ["-XX:ActiveProcessorCount=4", "-Dadvancedrocketrycommunity.w152Native=true",
                            "-Dadvancedrocketrycommunity.w152Stage="+stage, "-Dadvancedrocketrycommunity.w152Cut="+cut,
                            "-Dadvancedrocketrycommunity.w152Commit="+commit]
            result["commands"].append(command)
            process = smoke.CapturedProcess(command, session, evidence / (stage + ".log"))
            try:
                line = process.wait_for(MARKER, 300); match = MARKER.search(process.lines[line])
                if match[3]:
                    if match[3] != cut or cut == "NONE":
                        raise smoke.SmokeError("Unexpected native cut")
                    process.abort()
                else:
                    process.command("stop")
                    if process.finish() != 0:
                        raise smoke.SmokeError("Native stop failed")
            finally:
                if process.process.poll() is None: process.abort()
                report = session / "world/w152-native.json"
                if report.exists(): shutil.copy2(report, evidence / (stage + ".json"))
            raw = json.loads((evidence / (stage + ".json")).read_text(encoding="utf-8"))
            expected = "STABLE" if cut == "NONE" else "CUT"
            if raw["status"] != expected:
                raise smoke.SmokeError(f"{stage}: {raw['status']}: {raw.get('detail')}")
            if any("/ERROR]" in line or "/FATAL]" in line for line in process.lines):
                raise smoke.SmokeError("Native ERROR/FATAL observed")
            state = inspect(session / "world")
            (evidence / (stage + "-native.json")).write_text(json.dumps(state, indent=2), encoding="utf-8")
            result["stages"].append(dict(stage=stage, status=raw["status"], authority=state["authority"]))
            if stage in ("recover1", "recover2"):
                if state["phase"] is not None or state["sealed"]:
                    raise smoke.SmokeError("Stable restart left retained U or C")
                stable.append(comparable(state))
        if stable[0] != stable[1]:
            raise smoke.SmokeError("Second restart changed the elected native state")
        result["status"] = "PASS"
    except Exception as failure:
        result.update(status="FAIL", error=str(failure))
    finally:
        (evidence / "results.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
        if session.is_dir() and not session.is_symlink() and session.resolve().parent == temp and marker.read_text(encoding="ascii") == owned:
            shutil.rmtree(session)
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-server", type=Path, required=True)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--source-export", action="store_true", help="Outer lock owner supplied an exact git archive and source marker")
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--cut", choices=CUTS, required=True)
    parser.add_argument("--recovery-cut", choices=CUTS)
    result = run(parser.parse_args()); print(json.dumps(result, indent=2))
    return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
