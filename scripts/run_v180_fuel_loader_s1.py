#!/usr/bin/env python3
"""B3-D S1: genuine schema-2 upgrade, schema-3 downgrade, loader roots and cut-G2.

Nine counted native starts: old seed and two upgrades; old-JAR downgrade and two
restarts; new devices and two restarts. No admission switches or offline edits.
The detached lock-owning caller removes its disposable server in its finally.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import shutil
import uuid

import fuel_loader_s1_oracle as oracle
import run_dedicated_server_smoke as smoke
import run_v050_rocket_server_smoke as old_smoke
from run_v060_flight_server_smoke import _server_command
from run_v180_cargo_reconciliation import preflight
from run_v180_escrow_pre_f2 import assert_pin
from run_v180_rocket_settlement_completion import require_lock
from run_v130_adapter_recovery_smoke import STARTUP_HOLD, STARTUP_HOLD_CLEARED

STAGES = ("old-seed", "old-check", "old-check", "downgrade", "downgrade", "downgrade", "seed", "check", "check")
MAX_STARTS = 60


def check_log(lines, stage):
    findings = smoke.scan_log(lines)
    holds = [i for i, line in enumerate(lines) if STARTUP_HOLD.search(line)]
    oracle.require(len(holds) <= 1 and (not holds or any(STARTUP_HOLD_CLEARED.search(line) for line in lines[holds[0] + 1:])),
                   "Startup hold repeated or not cleared")
    findings = [line for line in findings if not STARTUP_HOLD.search(line)]
    expected = []
    if stage in ("seed", "check"):
        for i, status in enumerate(("FUTURE", "INVALID", "INVALID", "INVALID", "UNREGISTERED_FLUID")):
            expected.append(f"ARCE_FLUID_FUEL_LOADER_UNSUPPORTED pos={64 + i}, 100, 79 status={status}")
    for message in expected:
        hits = [line for line in findings if line.endswith(message)]
        oracle.require(len(hits) == 1, "Missing/repeated protected-root warning: " + message)
        findings.remove(hits[0])
    oracle.require(not findings, "Unexpected native project/error/client log: " + "\n".join(findings))
    return dict(expected_warnings=len(expected), cleared_startup_holds=len(holds), unexpected=[])


def count_start(path, row):
    history = json.loads(path.read_text(encoding="utf-8")) if path.exists() else []
    oracle.require(isinstance(history, list) and len(history) < MAX_STARTS, "W176 counted native start ceiling reached")
    history.append(row); path.write_text(json.dumps(history, indent=2) + "\n", encoding="utf-8")
    return len(history)


def old_loader_command():
    return ('setblock 260 101 256 advancedrocketrycommunity:fuel_loader{arce_fuel_loader:{schema_version:2,'
            'slot_role:2,item:{id:"minecraft:bucket",Count:1b,tag:{b:7b,s:7s,i:7,l:7L,'
            'ba:[B;1b,2b],ia:[I;1,2],la:[L;1L,2L]}},buffered_units:0L,owner_id:[I;0,176,0,1]}}')


def run(args):
    assert_pin(args.source_commit); require_lock(args.lock_owner)
    session, evidence = args.session_dir.resolve(), args.evidence_dir.resolve()
    oracle.require(session.parent == Path("D:/GitHub/ARCE-Task-Evidence/Temp").resolve()
                   and not session.exists() and not smoke.is_link_or_junction(session), "Unsafe/occupied session")
    oracle.require(not evidence.exists() and session not in evidence.parents and evidence not in session.parents,
                   "Occupied/overlapping evidence")
    libraries = args.source_server.resolve() / "libraries"
    oracle.require(libraries.is_dir() and not any(smoke.is_link_or_junction(path) for path in libraries.rglob("*")), "Unsafe Forge template")
    jars = dict(old=oracle.check_jar(args.old_jar, True), new=oracle.check_jar(args.jar, False))
    session.mkdir(); evidence.mkdir(parents=True)
    token = uuid.uuid4().hex; (session / ".arce-w176-owned").write_text(token, encoding="ascii")
    report = dict(status="IN_PROGRESS", source_commit=args.source_commit, old_source_commit=args.old_source_commit,
                  jars=jars, session=str(session), ownership_token=token, starts=[])
    process = None
    try:
        shutil.copytree(libraries, session / "libraries"); (session / "mods").mkdir()
        port = smoke.allocate_port(); smoke.write_server_configuration(session, port, True)
        old_before = new_before = None
        for index, stage in enumerate(STAGES):
            assert_pin(args.source_commit); require_lock(args.lock_owner); resources = preflight()
            old_jar = stage in ("old-seed", "downgrade"); old_world = stage.startswith("old-") or stage == "downgrade"
            jar = args.old_jar if old_jar else args.jar
            oracle.require(smoke.digest_file(jar) == jars["old" if old_jar else "new"]["sha256"], "JAR changed")
            shutil.copy2(jar, session / "mods/arce-s1.jar")
            world_name = "world-W176-" + ("old" if old_world else "new")
            properties = smoke.expected_server_properties(port, True)
            properties.update({"level-name": world_name, "level-type": "minecraft:flat", "max-tick-time": "60000",
                               "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}],
                                                                  "biome": "minecraft:plains"})})
            (session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(properties.items())), encoding="ascii")
            command = _server_command(args.java); command[2] = "-Xmx4G"
            if not old_jar:
                command[1:1] = ["-Dadvancedrocketrycommunity.fuelLoaderS1Stage=" + stage]
            row = dict(index=index, stage=stage, command=command, preflight=resources, commands=[], jar_sha256=smoke.digest_file(jar))
            row["counted_start"] = count_start(args.start_counter, dict(evidence=str(evidence), index=index, stage=stage))
            report["starts"].append(row)
            (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
            process = smoke.CapturedProcess(command, session, evidence / f"{index}-{stage}.log")
            process.wait_for(smoke.READY_MARKER, args.startup_timeout)
            if stage == "old-seed":
                old_smoke._configure_rocket(process)
                process.command(old_loader_command())
                start = len(process.lines); process.command(f"arce rocket assemble {old_smoke.ASSEMBLER}")
                found = process.wait_for(old_smoke.ASSEMBLY_LOG, 45, start_at=start)
                match = old_smoke.ASSEMBLY_LOG.search(process.lines[found]); row.update(snapshot=match[1], physical_uuid=match[2])
                row["commands"] = ["run_v050_rocket_server_smoke._configure_rocket", old_loader_command(), f"arce rocket assemble {old_smoke.ASSEMBLER}"]
                old_smoke._wait_for_active_entity(process, match[2], start_at=start)
                old_smoke._verify_assembled(process, match[2], "W176_OLD_ASSEMBLED")
            elif stage == "downgrade":
                expected = re.compile(r"ARCE_ROCKET_ENTITY_ACTIVE entity=" + re.escape(report["starts"][0]["physical_uuid"])
                                      + r" operational=false snapshot=none flight_state=FAILED_RECOVERABLE fuel=0")
                process.wait_for(expected, 45)
                row["live"] = dict(quarantined=True)
            else:
                process.wait_for(re.compile(r"ARCE_W176_(RESULT|FAILURE)"), args.startup_timeout)
                row["live"] = oracle.check_result(process.lines, stage, report["starts"][0]["snapshot"] if old_world else None)
            row["commands"] += ["save-all flush", "stop"]
            row["exit_code"] = old_smoke._save_and_stop(process)
            oracle.require(row["exit_code"] == 0, "Native server failed")
            oracle.require(not any(smoke.ERROR_LINE.search(line) or smoke.FATAL_LINE.search(line)
                    or any(marker in line for marker in smoke.CLIENT_LINKAGE_MARKERS) for line in process.lines), "Console error or client linkage")
            latest = evidence / f"{index}-{stage}-latest.log"; shutil.copy2(session / "logs/latest.log", latest)
            row["log_check"] = check_log(latest.read_text(encoding="utf-8").splitlines(), stage); process = None
            disk = oracle.inspect(session / world_name, evidence / f"{index}-native", stage, old_before if old_world else new_before)
            if old_world:
                old_before = disk
            else:
                new_before = disk
            row["inspection"] = f"{index}-native/inspection.json"
            row["inspection_sha256"] = smoke.digest_file(evidence / row["inspection"])
            row["status"] = "PASS"
            print(f"{index}/{stage}: PASS", flush=True)
        assert_pin(args.source_commit); report["status"] = "PASS"
    except Exception as failure:
        report.update(status="FAILED", error=str(failure)); raise
    finally:
        if process is not None:
            process.abort()
            if (session / "logs/latest.log").is_file(): shutil.copy2(session / "logs/latest.log", evidence / "failed-latest.log")
        (evidence / "results.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument("source_server", type=Path)
    for key in ("jar", "old-jar", "session-dir", "evidence-dir", "start-counter"):
        parser.add_argument("--" + key, type=Path, required=True)
    for key in ("source-commit", "old-source-commit", "java", "lock-owner"):
        parser.add_argument("--" + key, required=True)
    parser.add_argument("--startup-timeout", type=float, default=240)
    try:
        run(parser.parse_args()); return 0
    except (OSError, ValueError, smoke.SmokeError) as failure:
        print(f"FAIL: {failure}"); return 1


if __name__ == "__main__":
    raise SystemExit(main())
