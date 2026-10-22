#!/usr/bin/env python3
"""C-D/D-D: pre-cargo chest upgrade and new cargo snapshot/device S1 restarts.

Six native starts per leaf: old JAR assembly; new-JAR read and real disassembly;
new fixture assembly plus two restarts. No escrow switches are enabled.
The detached lock-owning caller removes the newly owned server in its finally.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import shutil
import uuid

import cargo_s1_oracle as oracle
import run_dedicated_server_smoke as smoke
import run_v050_rocket_server_smoke as old_smoke
from run_v060_flight_server_smoke import _server_command
from run_v180_cargo_reconciliation import preflight
from run_v180_escrow_pre_f2 import assert_pin
from run_v180_rocket_settlement_completion import require_lock


def check_log(lines: list[str], leaf: str, stage: str) -> dict:
    findings = smoke.scan_log(lines)
    expected = []
    if stage in ("seed", "check"):
        for role in range(2):
            for index, status in ((0, "FUTURE"), (1, "INVALID"), (2, "INVALID"),
                                  (4, "UNREGISTERED_ITEM" if leaf == "item" else "UNREGISTERED_FLUID")):
                expected.append(f"ARCE_{leaf.upper()}_CARGO_UNSUPPORTED pos={64 + role * 5 + index}, 100, 79 status={status} keepable=true")
    for message in expected:
        hits = [line for line in findings if line.endswith(message)]
        oracle.require(len(hits) == 1, "Missing/repeated native protected-root warning: " + message)
        findings.remove(hits[0])
    oracle.require(not findings, "Unexpected native project/client/error log: " + "\n".join(findings))
    return dict(expected_protected_warnings=len(expected), unexpected_findings=[])


def run(args) -> dict:
    assert_pin(args.source_commit); require_lock(args.lock_owner)
    session, evidence = args.session_dir.resolve(), args.evidence_dir.resolve()
    oracle.require(session.parent == Path("D:/GitHub/ARCE-Task-Evidence/Temp").resolve()
                   and not session.exists() and not smoke.is_link_or_junction(session), "Unsafe/occupied disposable session")
    oracle.require(not evidence.exists() and session not in evidence.parents and evidence not in session.parents,
                   "Occupied/overlapping evidence directory")
    libraries = args.source_server.resolve() / "libraries"
    oracle.require(libraries.is_dir() and not any(smoke.is_link_or_junction(path) for path in libraries.rglob("*")),
                   "Unsafe or missing Forge template libraries")
    jars = {"old": oracle.check_jar(args.old_jar, True), "new": oracle.check_jar(args.jar, False)}
    session.mkdir(); evidence.mkdir(parents=True)
    token = uuid.uuid4().hex; (session / ".arce-w136-owned").write_text(token, encoding="ascii")
    report = dict(status="IN_PROGRESS", source_commit=args.source_commit, old_source_commit=args.old_source_commit,
                  leaf=args.leaf, jars=jars, session=str(session), ownership_token=token, starts=[])
    process = None
    try:
        shutil.copytree(libraries, session / "libraries"); (session / "mods").mkdir()
        port = smoke.allocate_port(); smoke.write_server_configuration(session, port, True)
        installed = session / "mods/arce-s1.jar"
        old_before = new_before = None
        for index, stage in enumerate(("old-seed", "old-check", "old-restore", "seed", "check", "check")):
            assert_pin(args.source_commit); require_lock(args.lock_owner); resources = preflight()
            jar = args.old_jar if index == 0 else args.jar
            oracle.require(smoke.digest_file(jar) == jars["old" if index == 0 else "new"]["sha256"], "JAR changed during run")
            shutil.copy2(jar, installed)
            name = "world-W136-" + args.leaf + ("-old" if stage.startswith("old-") else "-new")
            properties = smoke.expected_server_properties(port, True)
            properties.update({"level-name": name, "level-type": "minecraft:flat", "max-tick-time": "60000",
                               "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}],
                                                                  "biome": "minecraft:plains"})})
            (session / "server.properties").write_text("".join(f"{key}={value}\n" for key, value in sorted(properties.items())), encoding="ascii")
            command = _server_command(args.java); command[2] = "-Xmx4G"
            if index:
                command[1:1] = ["-Dadvancedrocketrycommunity.cargoS1Leaf=" + args.leaf,
                                "-Dadvancedrocketrycommunity.cargoS1Stage=" + stage]
            row = dict(index=index, stage=stage, command=command, preflight=resources, commands=[], jar_sha256=smoke.digest_file(jar))
            report["starts"].append(row)
            (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
            process = smoke.CapturedProcess(command, session, evidence / f"{index}-{stage}.log")
            process.wait_for(smoke.READY_MARKER, args.startup_timeout)
            if index == 0:
                # Actual pre-cargo assembly, using the existing production command and its chest fixture.
                old_smoke._configure_rocket(process)
                start = len(process.lines); process.command(f"arce rocket assemble {old_smoke.ASSEMBLER}")
                found = process.wait_for(old_smoke.ASSEMBLY_LOG, 45, start_at=start)
                match = old_smoke.ASSEMBLY_LOG.search(process.lines[found])
                row.update(snapshot_hash=match[1], physical_uuid=match[2])
                row["commands"] = ["run_v050_rocket_server_smoke._configure_rocket", f"arce rocket assemble {old_smoke.ASSEMBLER}"]
                old_smoke._verify_assembled(process, match[2], "W136_OLD_ASSEMBLED")
            else:
                process.wait_for(re.compile(r"ARCE_W136_(RESULT|FAILURE)"), args.startup_timeout)
                row["live"] = oracle.check_result(process.lines, args.leaf, stage,
                        report["starts"][0]["snapshot_hash"] if stage.startswith("old-") else None)
                if stage == "old-restore":
                    command_text = "arce rocket release-test disassemble " + report["starts"][0]["physical_uuid"]
                    row["commands"].append(command_text); start = len(process.lines); process.command(command_text)
                    process.wait_for(re.compile("Release-test disassembly completed"), 45, start_at=start)
                    old_smoke._verify_recovered(process, report["starts"][0]["physical_uuid"], "W136_OLD_RESTORED")
            row["commands"] += ["save-all flush", "stop"]
            code = old_smoke._save_and_stop(process); row["exit_code"] = code
            oracle.require(code == 0, "Native process exited unsuccessfully")
            # The file logger retains full project names; the console may abbreviate them.
            # Keep both records, and reject console ERROR/FATAL/client-linkage lines as well.
            oracle.require(not any(smoke.ERROR_LINE.search(line) or smoke.FATAL_LINE.search(line)
                    or any(marker in line for marker in smoke.CLIENT_LINKAGE_MARKERS) for line in process.lines),
                    "Native console reported an error or client linkage")
            latest = evidence / f"{index}-{stage}-latest.log"
            shutil.copy2(session / "logs/latest.log", latest)
            row["log_check"] = check_log(latest.read_text(encoding="utf-8").splitlines(), args.leaf, stage); process = None
            disk = oracle.inspect(session / name, evidence / f"{index}-native", args.leaf == "item", stage.startswith("old-"),
                                  stage == "old-restore", old_before if stage.startswith("old-") else new_before)
            if index == 0:
                old_before = disk
            elif index == 3:
                new_before = disk
            row["native_inspection"] = f"{index}-native/inspection.json"
            row["inspection_sha256"] = smoke.digest_file(evidence / row["native_inspection"])
            row["status"] = "PASS"
            print(f"{args.leaf}/{index}/{stage}: PASS", flush=True)
        assert_pin(args.source_commit); report["status"] = "PASS"
    except Exception as failure:
        report.update(status="FAILED", error=str(failure)); raise
    finally:
        if process is not None:
            process.abort()
        (evidence / "results.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument("source_server", type=Path)
    for key in ("jar", "old-jar", "session-dir", "evidence-dir"):
        parser.add_argument("--" + key, type=Path, required=True)
    for key in ("source-commit", "old-source-commit", "java", "lock-owner"):
        parser.add_argument("--" + key, required=True)
    parser.add_argument("--leaf", choices=("fluid", "item"), required=True)
    parser.add_argument("--startup-timeout", type=float, default=240)
    try:
        run(parser.parse_args()); return 0
    except (OSError, ValueError, smoke.SmokeError) as failure:
        print(f"FAIL: {failure}"); return 1


if __name__ == "__main__":
    raise SystemExit(main())
