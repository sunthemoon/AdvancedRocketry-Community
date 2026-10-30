#!/usr/bin/env python3
"""Counted S6 loader tranches, with two restarts and no changes to shared runners.

Implements A1, A2's unloaded E5 and R2, seven A3 shutdown rows, A4 guards and selected B operator cuts. Other matrix rows
remain missing; PASS_SELECTED never means B3-S6 acceptance. Run as a detached
process so its own finally releases its lock and removes its disposable world.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import shutil
import time
import uuid

import loader_s6_oracle as oracle
import loader_s6_chain as chain
import run_v180_loader_cuts as shared
from run_dedicated_server_smoke import CapturedProcess, SmokeError, allocate_port, write_server_configuration, expected_server_properties
from run_v060_flight_server_smoke import _server_command

CASES = (oracle.E3, oracle.E5) + oracle.R2 + oracle.G3 + oracle.G1 + oracle.OPERATOR + chain.CASES
HOOKS = shared.HOOK_FILES + tuple(
    "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/" + name + ".java"
    for name in ("EscrowLoaderS6Probe", "EscrowLoaderS6Recovery", "EscrowLoaderS6Accounting", "EscrowLoaderS6UnloadedProbe",
                 "EscrowLoaderS6UnloadObservation", "EscrowLoaderS6R2Probe", "EscrowLoaderS6ShutdownProbe", "EscrowLoaderS6GuardProbe", "EscrowLoaderS6SettlementProbe", "EscrowLoaderS6ChainProbe", "EscrowLoaderS6NamedSetup", "EscrowCargoContentionProbe", "EscrowNamedFillTestHook")) + tuple(
    "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/flight/persistence/" + name + ".java"
    for name in ("RocketI6NativeCut", "RocketJournalFailurePolicy", "RocketTransferSavedData"))
HARNESSES = shared.HARNESS_FILES + ("scripts/run_v180_loader_s6.py", "scripts/loader_s6_oracle.py", "scripts/loader_s6_integrity.py", "scripts/loader_s6_chain.py")


def write(path: Path, data: dict) -> None:
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def check_log(lines, restart, cut=oracle.E3, retired_operation=None, image=None):
    if cut in chain.CASES:
        return chain.check_logs(lines, restart, cut)
    oracle.check_observations(lines, restart, cut, retired_operation)
    if cut in oracle.O3J:
        oracle.check_named_baseline(lines, restart, cut, image)


def journal_cut_properties(cut, restart):
    if not restart and cut in oracle.O3J[:2]:
        return ["-Dadvancedrocketrycommunity.i6NativeCut=" + oracle.O_HOOKS[cut]]
    return []


def finish_initial(process, cut, diagnostic):
    if cut in oracle.G3:
        if cut not in oracle.G3_SELF_STOP:
            process.command("stop")
        code = process.finish()
        oracle.legacy.require(code == 0, "Selected normal shutdown exited abnormally")
        return dict(stop="NORMAL_DRAIN_FROM_SELECTED_WRITER_STATE", exit_code=code)
    process.process.kill()
    process.abort()
    return dict(stop="FORCED_AFTER_DIAGNOSTIC_WINDOW" if diagnostic else "FORCED_AT_SELECTED_CHECKPOINT")


def run_cut(args, cut, evidence, owner, pins):
    diagnostic = getattr(args, "diagnose_unload", False)
    oracle.legacy.require(not diagnostic or cut == oracle.E5, "Unload diagnosis is only defined for E5")
    session = args.session_parent.resolve() / ("W211-" + uuid.uuid4().hex)
    token = uuid.uuid4().hex
    session.mkdir()
    (session / ".w97-owned").write_text(token, encoding="ascii")
    evidence.mkdir()
    row = dict(cut=cut, diagnostic=diagnostic, pins=pins, status="IN_PROGRESS", starts=[], findings=[],
               session=str(session), cleanup=False)
    process = None
    try:
        libraries = args.source_server.resolve() / "libraries"
        oracle.legacy.require(libraries.is_dir() and not any(p.is_symlink() for p in libraries.rglob("*")),
                              "Unsafe Forge template")
        shutil.copytree(libraries, session / "libraries")
        (session / "mods").mkdir()
        shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = allocate_port()
        write_server_configuration(session, port, True)
        properties = expected_server_properties(port, True)
        properties.update({"level-name": "world", "level-type": "minecraft:flat", "max-tick-time": "60000",
                           "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}],
                                                              "biome": "minecraft:plains"})})
        (session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(properties.items())), encoding="ascii")
        identity = None
        for restart in range(1 if diagnostic else 3):
            shared.assert_pin(args.source_commit)
            shared.require_lock(owner)
            oracle.legacy.require(shared.digest(args.jar) == pins["jar_sha256"] == shared.digest(session / "mods" / args.jar.name),
                                  "JAR changed during campaign")
            command = _server_command(args.java)
            command[2] = "-Xmx4G"
            native_case = "LF_M_K7" if cut in oracle.G3 else cut
            if cut in oracle.G3_UNKNOWN:
                native_case = "LF_M_E4L1_UNKNOWN" if cut == oracle.G3_U1 else "LF_M_E4L2_UNKNOWN"
            command[1:1] = ["-Dadvancedrocketrycommunity.escrowScenario=" + ("recover" if restart else "start"),
                            "-Dadvancedrocketrycommunity.escrowCase=" + native_case]
            command[1:1] = journal_cut_properties(cut, restart)
            if cut in oracle.G3:
                command[1:1] = ["-Dadvancedrocketrycommunity.escrowS6Shutdown=" + cut]
            if cut in oracle.G1:
                command[1:1] = ["-Dadvancedrocketrycommunity.escrowS6G1Round=" + str(restart)]
            if cut == oracle.G3_L2 and restart:
                oracle.legacy.require(identity is not None and "op_id" in identity, "Missing confirmed L2 operation for recovery")
                command[1:1] = ["-Dadvancedrocketrycommunity.escrowS6RetiredOperation=" + identity["op_id"]]
            if diagnostic:
                command[1:1] = ["-Dadvancedrocketrycommunity.escrowS6UnloadDiagnostic=true"]
            start = dict(restart=restart, command=command, free_bytes=shared.disk_space(),
                         free_physical_memory=shared.physical_memory(), lock_owner=owner)
            row["starts"].append(start)
            write(evidence / "result.json", row)
            # Count before launch, including failed startup attempts.
            process = CapturedProcess(command, session, evidence / f"start-{restart}.log")
            start["pid"] = process.process.pid
            try:
                hook = "UNLOADED_COMPETITOR_RETIRED" if cut == oracle.E5 else "PROPAGATED_CLOSE_FAILURE"
                result_marker = "ARCE_LOADER_S6_FAIR_RESULT" if cut == oracle.E5 else "ARCE_LOADER_S6_RESULT"
                marker = (r"ARCE_LOADER_S6_PAUSED cut=" + re.escape(cut) + r" hook=" + hook
                          if not restart else result_marker + r" cut=" + re.escape(cut) + r"(?:\s|$)")
                if diagnostic:
                    marker = r"ARCE_LOADER_S6_UNLOAD_DIAGNOSTIC_END cut=" + re.escape(cut) + r"(?:\s|$)"
                elif cut in oracle.R2:
                    marker = r"ARCE_LOADER_S6_R2_" + ("RESULT" if restart else "PAUSED") + r" cut=" + re.escape(cut) + r"(?:\s|$)"
                elif cut in oracle.G3:
                    marker = (r"ARCE_LOADER_S6_RESULT cut=" + re.escape(oracle.E3) + r"(?:\s|$)" if restart
                              else r"ARCE_LOADER_S6_DRAIN_READY cut=" + re.escape(cut) + r"(?:\s|$)")
                    if cut == oracle.G3_U1 and restart:
                        marker = r"ARCE_LOADER_MATRIX_RESULT cut=LF_M_E4L1_UNKNOWN(?:\s|$)"
                elif cut in oracle.G1:
                    marker = r"ARCE_LOADER_S6_G1_RESULT cut=" + re.escape(cut) + " round=" + str(restart) + r"(?:\s|$)"
                elif cut in oracle.OPERATOR:
                    marker = r"ARCE_LOADER_S6_O_" + ("RESULT" if restart else "PAUSED") + r" cut=" + re.escape(cut) + r"(?:\s|$)"
                    if not restart and cut in oracle.O3J[:2]:
                        marker = r"ARCE_W164_CUT boundary=" + oracle.O_HOOKS[cut] + r"(?:\s|$)"
                elif cut in chain.CASES:
                    marker = r"ARCE_LOADER_S6_CHAIN_" + ("RESULT" if restart else "PAUSED") + r" cut=" + re.escape(cut) + r"(?:\s|$)"
                process.wait_for(re.compile(marker), args.startup_timeout)
                start["checkpoint_reached"] = True
                if restart and cut not in oracle.G1 and cut not in chain.VETO:
                    process.command("stop")
                    start["exit_code"] = process.finish()
                    oracle.legacy.require(start["exit_code"] == 0, "Restart stopped abnormally")
                else:
                    start.update(finish_initial(process, cut, diagnostic))
            except Exception as failure:
                start["process_error"] = str(failure)
                row["findings"].append(dict(restart=restart, kind="PROCESS", error=str(failure)))
                process.abort()
            lines = list(process.lines)
            if any("S6 native image is not explained by its durable record" in line
                   or "S6 endpoint is not an exact record-backed BEFORE/AFTER prefix" in line for line in lines):
                row["unexplained_image"] = True
            process = None
            start["observations"] = [line for line in lines if "ARCE_LOADER_" in line or "ARCE_CARGO_CACHE_" in line or "ARCE_W164_CUT" in line]
            start["image"] = oracle.inspect(session / "world", cut)
            if diagnostic:
                # The healthy pair is deliberately never queued. Its image is retained,
                # not passed through the completed-fairness oracle or reported as PASS.
                write(evidence / "result.json", row)
                continue
            raw_check = oracle.check_e5 if cut == oracle.E5 else oracle.check_e3
            if cut in (oracle.G3_L1, oracle.G3_QUEUED):
                raw_check = lambda image, restart, identity: oracle.check_g3_l1(image, restart, identity, cut)
            elif cut == oracle.G3_L2_QUEUED:
                raw_check = oracle.check_g3_l2_queued
            elif cut == oracle.G3_L2:
                raw_check = lambda image, restart, identity: oracle.check_g3_l2(
                    image, restart, identity, operation=oracle.shutdown_operation(lines, cut) if not restart else None)
            elif cut in oracle.G3_UNKNOWN:
                raw_check = lambda image, restart, identity: oracle.check_g3_unknown(image, restart, identity, cut)
            if cut in oracle.R2:
                raw_check = lambda image, restart, identity: oracle.check_r2(image, restart, identity, cut)
            if cut in oracle.G1:
                raw_check = lambda image, restart, identity: oracle.check_g1(image, restart, identity, cut)
            if cut in oracle.OPERATOR:
                raw_check = lambda image, restart, identity: oracle.check_o1(image, restart, identity, cut)
            if cut in chain.CASES:
                raw_check = lambda image, restart, identity: chain.check(image, restart, identity, cut)
            for kind, check in (("LOG", lambda: check_log(lines, restart, cut,
                                      identity["op_id"] if cut == oracle.G3_L2 and restart and identity else None, start["image"])),
                                ("RAW", lambda: raw_check(start["image"], restart, identity))):
                try:
                    result = check()
                    if kind == "RAW":
                        start["oracle"] = result
                        identity = result["identity"]
                        if cut == oracle.E3 and not restart and getattr(args, "require_initial_prefix", None):
                            oracle.legacy.require(result["prefix"] == args.require_initial_prefix,
                                                  "Selected initial prefix not reached: " + result["prefix"])
                except SmokeError as failure:
                    row["findings"].append(dict(restart=restart, kind=kind, error=str(failure)))
            write(evidence / "result.json", row)
        row["status"] = "FAILED" if row["findings"] else ("DIAGNOSTIC_OBSERVED" if diagnostic else "PASS")
    except Exception as failure:
        row["status"] = "FAILED"
        row["findings"].append(dict(kind="HARNESS", error=f"{type(failure).__name__}: {failure}"))
    finally:
        if process is not None:
            process.abort()
        try:
            shared.cleanup(session, token, args.session_parent)
            row["cleanup"] = True
        except OSError as failure:
            row.update(status="FAILED", cleanup_error=str(failure))
        write(evidence / "result.json", row)
    return row


def run(args):
    shared.assert_pin(args.source_commit)
    diagnostic = getattr(args, "diagnose_unload", False)
    cuts = args.case or ((oracle.E5,) if diagnostic else CASES)
    oracle.legacy.require(not diagnostic or list(cuts) == [oracle.E5], "Diagnose exactly one E5 start")
    parent, evidence = args.session_parent.resolve(), args.evidence_dir.resolve()
    oracle.legacy.require(parent.is_dir() and not parent.is_symlink() and not evidence.exists()
                          and parent not in evidence.parents and evidence not in parent.parents,
                          "Evidence must be new and outside disposable sessions")
    evidence.mkdir(parents=True)
    pins = dict(source_commit=args.source_commit, jar_sha256=shared.digest(args.jar),
                hooks={p: shared.digest(shared.REPO / p) for p in HOOKS},
                harness={p: shared.digest(shared.REPO / p) for p in HARNESSES})
    report = dict(status="IN_PROGRESS", coverage="Selected A1/E5/R2/G3/G1/operator rows only; not all B3-S6", pins=pins, cases=[], starts=0, restarts=0)
    if diagnostic:
        report["coverage"] = "R186 unload lifecycle diagnosis only; not fairness or restart coverage"
    write(evidence / "results.json", report)
    for cut in cuts:
        for attempt in (1, 2):
            owner = f"W211 {cut} attempt={attempt} pid={os.getpid()} {datetime.now(timezone.utc).isoformat()}"
            print(f"Waiting for heavy-JVM lock: {owner}", flush=True)
            with shared.heavy_lock(owner):
                row = run_cut(args, cut, evidence / f"{cut}-attempt-{attempt}", owner, pins)
            report["cases"].append(row)
            report["starts"] += len(row["starts"])
            report["restarts"] += sum(s["restart"] > 0 for s in row["starts"])
            write(evidence / "results.json", report)
            print(f"{cut} attempt {attempt}: {row['status']}", flush=True)
            # Even a failed cut gives other workers their normal acquisition window.
            time.sleep(90)
            if diagnostic:
                report["status"] = row["status"]
                shared.assert_pin(args.source_commit)
                write(evidence / "results.json", report)
                return report
            if row.get("unexplained_image"):
                report["status"] = "STOPPED_UNEXPLAINED_IMAGE"
                write(evidence / "results.json", report)
                return report
            if row["status"] == "PASS":
                break
        else:
            report["status"] = "STOPPED_AFTER_TWO_FAILURES"
            write(evidence / "results.json", report)
            return report
    report["status"] = "PASS_SELECTED" if all(r["status"] == "PASS" for r in report["cases"]) else "FINDINGS"
    shared.assert_pin(args.source_commit)
    write(evidence / "results.json", report)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_server", type=Path)
    for key in ("jar", "session-parent", "evidence-dir"):
        parser.add_argument("--" + key, type=Path, required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--case", action="append", choices=CASES)
    parser.add_argument("--startup-timeout", type=float, default=240)
    parser.add_argument("--require-initial-prefix", choices=("K2", "K4", "K5", "K6"))
    parser.add_argument("--diagnose-unload", action="store_true", help="One E5 lifecycle observation, never a fairness PASS")
    args = parser.parse_args()
    return 0 if run(args)["status"] == "PASS_SELECTED" else 1


if __name__ == "__main__":
    raise SystemExit(main())
