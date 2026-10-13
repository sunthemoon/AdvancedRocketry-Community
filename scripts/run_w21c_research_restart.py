#!/usr/bin/env python3
"""Disposable native W21C normal-restart and lost-flush fault verification."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import platform
import re
import shutil
import socket
import time

from run_dedicated_server_smoke import CapturedProcess, READY_MARKER, SAVE_MARKER, SmokeError, digest_file

SNAPSHOT = re.compile(r"ARCE_RESEARCH_SNAPSHOT (\{.*\})")
FORGE = "1.20.1-47.4.10"
MAX_EVIDENCE = 100 * 1024 * 1024


def prepare(template: Path, target: Path, jar: Path, port: int) -> None:
    target.mkdir()
    shutil.copytree(template / "libraries", target / "libraries")
    (target / "mods").mkdir()
    shutil.copy2(jar, target / "mods" / jar.name)
    (target / "eula.txt").write_text("eula=true\n", encoding="ascii")
    (target / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nlevel-name=world\n"
        "level-type=minecraft:normal\ngenerate-structures=false\nview-distance=2\nsimulation-distance=2\n"
        "max-players=1\nspawn-protection=0\nsync-chunk-writes=true\nenable-rcon=false\n", encoding="ascii")


def launch(args: argparse.Namespace, host: Path, evidence: Path, cycle: int) -> CapturedProcess:
    name = "win_args.txt" if platform.system() == "Windows" else "unix_args.txt"
    arg_file = Path("libraries/net/minecraftforge/forge") / FORGE / name
    process = CapturedProcess([args.java, "-Xms512M", "-Xmx2G",
        "-Dadvancedrocketrycommunity.releaseTestHooks=true",
        "-Dadvancedrocketrycommunity.researchVerificationPaused=true",
        f"@{arg_file.as_posix()}", "nogui"], host, evidence / f"cycle-{cycle:02d}.log")
    try:
        process.wait_for(READY_MARKER, args.startup_timeout)
        process.command("gamerule doDaylightCycle false")
        process.command("forceload add 0 0 47 15")
    except BaseException:
        process.abort()
        raise
    return process


def command(process: CapturedProcess, action: str) -> dict:
    start = len(process.lines)
    process.command("arce research-test " + action)
    marker = re.compile(r'ARCE_RESEARCH_SNAPSHOT .*"action":"' + re.escape(action.split()[0]) + r'"')
    index = process.wait_for(marker, 60, start_at=start)
    match = SNAPSHOT.search(process.lines[index])
    if not match:
        raise SmokeError("Native snapshot could not be parsed")
    return json.loads(match.group(1))


def ready(process: CapturedProcess) -> dict:
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        snapshot = command(process, "snapshot")
        if snapshot["ready"]:
            return snapshot
        time.sleep(0.2)
    raise SmokeError("Research fixtures did not become ticking and sky-lit")


def stop(process: CapturedProcess) -> None:
    start = len(process.lines)
    process.command("save-all flush")
    process.wait_for(SAVE_MARKER, 60, start_at=start)
    process.command("stop")
    if process.finish() != 0:
        raise SmokeError("Native server did not stop normally")
    if any("/ERROR]" in line or "/FATAL]" in line for line in process.lines):
        raise SmokeError("Native run emitted ERROR/FATAL")


def same(before: dict, after: dict, *, include_fe: bool = True) -> None:
    keys = ["device0", "device1", "device2", "job", "instance", "source", "processor", "phase",
            "survey_progress", "analysis_progress", "analysis", "balance"]
    if include_fe:
        keys += ["fe0", "fe1", "fe2"]
    differences = {key: [before.get(key), after.get(key)] for key in keys if before.get(key) != after.get(key)}
    if differences:
        raise SmokeError("Native restart differed: " + json.dumps(differences))


def cut_recovery(before: dict, cut: dict, restored: dict) -> None:
    same(before, restored, include_fe=False)
    # The FE store is independent. Only the acknowledged or cut chunk value is valid;
    # allowing either is not permission for arbitrary FE changes or an atomicity claim.
    for key in ("fe0", "fe1", "fe2"):
        if restored[key] not in (before[key], cut[key]):
            raise SmokeError(f"Crash FE differs from both actual chunk checkpoints: {key}={restored[key]}")


def run(args: argparse.Namespace, summary: dict) -> None:
    work, evidence = args.work_dir.resolve(), args.evidence_dir.resolve()
    if work.exists() or evidence.exists() or work == evidence or work in evidence.parents or evidence in work.parents:
        raise SmokeError("Disposable work and evidence must be new and disjoint")
    # This script owns only this newly created directory. Template libraries and worlds are read-only.
    work.mkdir(parents=True)
    evidence.mkdir(parents=True)
    marker = work / ".w21c-owned"
    marker.write_text("W21C\n", encoding="ascii")
    with socket.socket() as binding:
        binding.bind(("127.0.0.1", 0))
        port = binding.getsockname()[1]
    host = work / "server"
    processes: list[CapturedProcess] = []
    snapshots: list[dict] = []
    summary.update(tested_commit=args.tested_commit, candidate_jar_sha256=digest_file(args.candidate_jar), forge=FORGE)
    try:
        prepare(args.template, host, args.candidate_jar, port)
        def cycle(number: int) -> CapturedProcess:
            process = launch(args, host, evidence, number)
            processes.append(process)
            return process
        process = cycle(1)
        command(process, "setup"); ready(process)
        command(process, "start")
        if not any("failed_flush=WAITING_SAVE" in line for line in process.lines):
            raise SmokeError("Failed-flush probe was absent")
        mid_survey = command(process, "step 300"); snapshots.append(mid_survey); stop(process)
        process = cycle(2); restored = ready(process); snapshots.append(restored); same(mid_survey, restored)
        survey_cut_base = command(process, "step 699"); snapshots.append(survey_cut_base); stop(process)
        process = cycle(3); same(survey_cut_base, ready(process))
        cut_survey = command(process, "cut-survey"); snapshots.append(cut_survey)
        if cut_survey["phase"] != "READY" or cut_survey["survey_progress"] != 1000:
            raise SmokeError("Survey crash cut was not after completion")
        process.abort()
        process = cycle(4); restored = ready(process); snapshots.append(restored); cut_recovery(survey_cut_base, cut_survey, restored)
        command(process, "step 1"); command(process, "claim")
        if not any("claim=WAITING_SAVE" in line for line in process.lines):
            raise SmokeError("Claim barrier probe was absent")
        mid_analysis = command(process, "step 50"); snapshots.append(mid_analysis); stop(process)
        process = cycle(5); restored = ready(process); snapshots.append(restored); same(mid_analysis, restored)
        rebound = command(process, "rebind"); snapshots.append(rebound)
        if rebound["analysis_progress"] != 50 or rebound["processor"] != rebound["device2"]:
            raise SmokeError("Rebind lost paid progress or did not move the processor")
        if not any("rebind=WAITING_SAVE" in line for line in process.lines):
            raise SmokeError("Rebind claim barrier probe was absent")
        credit_cut_base = command(process, "step 149"); snapshots.append(credit_cut_base); stop(process)
        process = cycle(6); same(credit_cut_base, ready(process))
        cut_credit = command(process, "cut-credit"); snapshots.append(cut_credit)
        if cut_credit["balance"] != 120 or cut_credit["phase"] != "PROCESSED":
            raise SmokeError("Credit crash cut was not after the credit mutation")
        process.abort()
        process = cycle(7); restored = ready(process); snapshots.append(restored); cut_recovery(credit_cut_base, cut_credit, restored)
        terminal = command(process, "step 1"); snapshots.append(terminal)
        replay = command(process, "replay"); same(terminal, replay); stop(process)
        process = cycle(8); restored = ready(process); snapshots.append(restored); same(terminal, restored); stop(process)
        if restored["balance"] != 120 or restored["analysis_progress"] != 200 or restored["phase"] != "PROCESSED":
            raise SmokeError("Final exactly-once credit differs")
        summary.update(status="PASS", cycles=8, failed_flush_waiting=True, claim_waiting=True, rebind_waiting=True,
                       lost_completion_flush=True, lost_credit_flush=True, credit_exactly_once=True,
                       fe_crash_atomic=False, fe_limitation="Plug FE and registry progress are separate saves; crashes can lose paid FE or roll back FE independently.")
    finally:
        for process in processes:
            if process.process.poll() is None:
                process.abort()
        (evidence / "snapshots.json").write_text(json.dumps(snapshots, indent=2) + "\n", encoding="utf-8")
        if work.is_dir() and marker.read_text(encoding="ascii") == "W21C\n" and work.parent == args.work_dir.resolve().parent:
            shutil.rmtree(work)
        summary["temporary_server_removed"] = not work.exists()
        summary["evidence_bytes"] = sum(path.stat().st_size for path in evidence.rglob("*") if path.is_file())
        (evidence / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
        if summary["evidence_bytes"] > MAX_EVIDENCE:
            raise SmokeError("Native evidence exceeds 100 MiB")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("template", "candidate-jar", "work-dir", "evidence-dir"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--tested-commit", required=True)
    parser.add_argument("--java", default=str(Path(os.environ["JAVA_HOME"]) / "bin" / "java.exe"))
    parser.add_argument("--startup-timeout", type=float, default=240)
    args = parser.parse_args(); summary = {"status": "FAIL"}
    try:
        run(args, summary); print(json.dumps(summary, indent=2)); return 0
    except (SmokeError, OSError, ValueError) as failure:
        print(f"FAIL: {failure}"); return 1


if __name__ == "__main__":
    raise SystemExit(main())
