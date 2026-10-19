#!/usr/bin/env python3
"""Loader creation, normal, chain and reconciliation cuts on committed source, with two restarts and per-cut cleanup.

K4/K5 record the first store chosen by the
unmodified driver; an opposite order is NOT_COVERED, not a passed cut. A new invocation may observe the other order.
"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import shutil
import time
import uuid

import loader_cut_oracle as oracle
import run_dedicated_server_smoke as smoke
from run_v060_flight_server_smoke import _server_command
from run_v180_escrow_forced_stop import digest
from run_v180_escrow_pre_f2 import assert_pin
from run_v180_rocket_settlement_completion import require_lock

REPO = Path(__file__).resolve().parents[1]
LOCK = Path("D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm")
HOOK_FILES = tuple("src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/" + name + ".java"
                   for name in ("EscrowLoaderReleaseProbe", "EscrowLoaderCutProbe", "EscrowLoaderStorageProbe", "EscrowReleaseProbe", "EscrowLedgerWriter", "EscrowFillQueue",
                                "EscrowReconciliationAction", "EscrowReconciliationDriver", "EscrowRuntime", "EscrowBarrier"))
FIRST = re.compile(r"ARCE_LOADER_MATRIX_FIRST cut=(\S+) recovery=(true|false) delegate=(\w+) issued=(\d+) records=(\d+) pending=(\d+)")
PAUSED = re.compile(r"ARCE_LOADER_MATRIX_PAUSED cut=(\S+) hook=(\S+) first_store=(\w+) thread=(.+)")
BASELINE_FAILURE = re.compile(r"ARCE_LOADER_MATRIX_BASELINE_FAILURE cut=(\S+) failure=(\d+) outcome=(\w+) pending=(\d+) unavailable=(true|false) status=(\w+)")
STORAGE_FAILURE = re.compile(r"ARCE_LOADER_MATRIX_FAILURE cut=(\S+) unknown=(true|false) records=(\d+) pending=(\d+) issued=(\d+)")


def storage_observations(cut: str, lines: list[str]) -> None:
    name = cut.removeprefix(oracle.PREFIX)
    if name == "E2_CAPTURE":
        one_match(re.compile(r"ARCE_LOADER_MATRIX_SUPERSEDED cut=" + re.escape(cut)
                             + r" saves=1 io_failures=0 records=1 pending=1"), lines)
    if name.endswith(("_FAIL", "_UNKNOWN")) or name.startswith("E4L2_RESAVE"):
        failures = [match for line in lines if (match := STORAGE_FAILURE.search(line))]
        unknown = not name.endswith("_FAIL")
        l1 = name.startswith("E4L1")
        expected = [(unknown, int(not l1), int(unknown or not l1), int(not l1))]
        if "RESAVE" in name:
            expected.append(expected[0])
            one_match(re.compile(r"ARCE_LOADER_MATRIX_RESAVE cut=" + re.escape(cut) + r" proof_before=true proof_after=false"), lines)
        if name.endswith("_RESOLVED"):
            expected.append((False, 1, 1, 1))
        oracle.require(all(m[1] == cut for m in failures)
                       and [(m[2] == "true", int(m[3]), int(m[4]), int(m[5])) for m in failures] == expected,
                       "Failure outcome changed records, issued sequence or uncertain reservations")


def creation_observations(cut: str, lines: list[str]) -> None:
    if cut in ("LF_M_N2_DEFINITE", "LF_M_N2_UNKNOWN"):
        unknown = cut.endswith("UNKNOWN")
        failures = [match for line in lines if (match := BASELINE_FAILURE.search(line))]
        oracle.require(len(failures) == 3, "Baseline did not expose exactly three unresolved failures")
        for index, match in enumerate(failures, 1):
            oracle.require(match[1] == cut and int(match[2]) == index
                           and match[3] == ("WRITE_UNKNOWN" if unknown else "WRITE_FAILED_BEFORE_REPLACE")
                           and int(match[4]) == int(unknown) and match[5] == str(index == 3).lower(),
                           "Wrong baseline failure outcome, reservation or unavailable transition")
        oracle.require(failures[-1][6] == "LEDGER_UNAVAILABLE", "Final baseline display was not LEDGER_UNAVAILABLE")
        oracle.require(any("phase=pending " in line and "status=PREPARING" in line for line in lines),
                       "Missing PREPARING observation before baseline failure")
    if cut == "LF_M_N3_PREFLIGHT":
        one_match(re.compile(r"ARCE_LOADER_MATRIX_PREFLIGHT cut=LF_M_N3_PREFLIGHT admitted=5 refused=ESCROW_FULL next=6 pending=5 issued=0 records=0"), lines)


def disk_space() -> dict:
    free = {drive: shutil.disk_usage(drive).free for drive in ("C:/", "D:/")}
    oracle.require(min(free.values()) > 10 * 1024**3, "Heavy start refused: C: and D: need more than 10 GiB free")
    return free


@contextmanager
def heavy_lock(owner: str):
    deadline = time.monotonic() + 3 * 3600
    while True:
        try:
            LOCK.mkdir()
            break
        except FileExistsError:
            oracle.require(time.time() - LOCK.stat().st_mtime < 3 * 3600 and time.monotonic() < deadline,
                           "Heavy-JVM lock is over three hours old or wait expired; not breaking it")
            # Catch short between-cut handoffs without changing the three-hour bound or stealing a held lock.
            time.sleep(1)
    try:
        (LOCK / "owner.txt").write_text(owner, encoding="utf-8")
        disk_space()
        yield
    finally:
        require_lock(owner)
        # Only our exact, nonrecursive lock contents. A foreign extra file is not deleted.
        (LOCK / "owner.txt").unlink()
        LOCK.rmdir()


def cleanup(session: Path, token: str, parent: Path) -> None:
    oracle.require(session.resolve().parent == parent.resolve() and not session.is_symlink()
                   and (session / ".w97-owned").read_text(encoding="ascii") == token,
                   "Refusing cleanup outside the owned session")
    shutil.rmtree(session)


def one_match(pattern: re.Pattern, lines: list[str]):
    matches = [match for line in lines if (match := pattern.search(line))]
    oracle.require(len(matches) == 1, "Missing/repeated native marker: " + pattern.pattern)
    return matches[0]


def first_tick(lines: list[str], cut: str, restart: int, previous: dict | None) -> dict:
    match = one_match(FIRST, lines)
    root = None if previous is None else previous["ledger"]
    expected = ("LOADED" if root else "NO_FILE", root["issued_through"] if root else 0, len(root["records"]) if root else 0)
    actual = (match[3], int(match[4]), int(match[5]))
    oracle.require(match[1] == cut and match[2] == str(bool(restart)).lower() and actual == expected
                   and int(match[6]) == actual[2], "First-tick authority differs from independently decoded native stores")
    return dict(delegate=actual[0], issued=actual[1], records=actual[2], pending=int(match[6]))


def run_cut(args, cut: str, evidence: Path, owner: str, pins: dict) -> dict:
    session = args.session_parent.resolve() / (cut + "-" + uuid.uuid4().hex)
    oracle.require(not session.exists() and not session.is_symlink(), "Session already exists")
    session.mkdir(); token = uuid.uuid4().hex
    (session / ".w97-owned").write_text(token, encoding="ascii")
    evidence.mkdir()
    row = dict(cut=cut, status="IN_PROGRESS", pins=pins, starts=[], session=str(session), cleanup=False)
    process = None
    try:
        libraries = args.source_server.resolve() / "libraries"
        oracle.require(libraries.is_dir() and not any(p.is_symlink() for p in libraries.rglob("*")), "Unsafe Forge template")
        shutil.copytree(libraries, session / "libraries")
        (session / "mods").mkdir(); shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = smoke.allocate_port(); smoke.write_server_configuration(session, port, True)
        properties = smoke.expected_server_properties(port, True)
        properties.update({"level-name": "world", "level-type": "minecraft:flat", "max-tick-time": "60000",
                           "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}], "biome": "minecraft:plains"})})
        (session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(properties.items())), encoding="ascii")
        previous = None; identity = None; ordered = True
        for restart in range(3):
            assert_pin(args.source_commit); require_lock(owner)
            free = disk_space()
            oracle.require(digest(args.jar) == pins["jar_sha256"] == digest(session / "mods" / args.jar.name), "Pinned jar changed")
            command = _server_command(args.java); command[2] = "-Xmx4G"
            command[1:1] = ["-Dadvancedrocketrycommunity.escrowScenario=" + ("recover" if restart else "start"),
                            "-Dadvancedrocketrycommunity.escrowCase=" + cut]
            creation = cut.removeprefix(oracle.PREFIX) in oracle.CREATION + oracle.CREATION_SUPPLEMENTAL
            if creation and restart == 2:
                command.insert(1, "-Dadvancedrocketrycommunity.escrowLoaderRetry=true")
            start = dict(restart=restart, command=command, free_bytes=free, lock_owner=owner)
            row["starts"].append(start)
            process = smoke.CapturedProcess(command, session, evidence / f"start-{restart}.log")
            if restart == 0:
                process.wait_for(re.compile(r"ARCE_LOADER_MATRIX_PAUSED cut=" + re.escape(cut) + r" "), args.startup_timeout)
                process.process.kill(); process.abort()
                paused = one_match(PAUSED, process.lines)
                start["pause"] = dict(hook=paused[2], first_store=paused[3], thread=paused[4])
                if cut in ("LF_M_K4", "LF_M_K5"):
                    ordered = paused[3] == ("SOURCE" if cut.endswith("K4") else "DESTINATION")
                actual_cut = ("LF_M_K4" if paused[3] == "SOURCE" else "LF_M_K5") if not ordered else cut
                if cut == "LF_M_D2_C4_BEFORE":
                    actual_cut = "LF_M_K5"
                    ordered = paused[3] == "DESTINATION"
                if cut == "LF_M_E2_CAPTURE":
                    # The source capture is superseded. A destination-first driver's earlier proof already stored
                    # its bank; a source-first driver has not stored the bank. Neither case permits retirement.
                    actual_cut = "LF_M_K6" if paused[3] == "DESTINATION" else "LF_M_K4"
            else:
                process.wait_for(re.compile(r"ARCE_LOADER_MATRIX_RESULT cut=" + re.escape(cut) + r"(?:\s|$)"), args.startup_timeout)
                process.command("stop"); start["exit_code"] = process.finish()
                oracle.require(start["exit_code"] == 0, "Native restart exit was not zero")
                actual_cut = cut
            lines = list(process.lines); process = None
            start["observations"] = [line for line in lines if "ARCE_LOADER_MATRIX_" in line]
            disk = oracle.inspect(session / "world"); start["disk"] = disk
            try:
                start["first_tick"] = first_tick(lines, cut, restart, previous)
                oracle.require(not any("/ERROR]" in line or "/FATAL]" in line for line in lines), "Native start logged ERROR/FATAL")
                if restart == 0 and cut.removeprefix(oracle.PREFIX) in oracle.CHAINS and cut != "LF_M_C1":
                    one_match(re.compile(r"ARCE_LOADER_MATRIX_CHAIN cut=" + re.escape(cut) + r" latest=2 reverse_refused=true records=2"), lines)
                if cut in ("LF_M_K5", "LF_M_D2_C4_BEFORE") and restart == 0:
                    one_match(re.compile(r"ARCE_LOADER_MATRIX_PRIMED cut=" + re.escape(cut) + r" tank=5000 epoch=0 stores=0"), lines)
                if creation and restart == 0:
                    creation_observations(cut, lines)
                if cut in oracle.S4 and restart == 0:
                    storage_observations(cut, lines)
                if creation and restart:
                    oracle.require(any("phase=creation_restart " in line for line in lines), "Missing unchanged creation-restart observation")
            except smoke.SmokeError as failure:
                start["observation_error"] = str(failure)
                row.setdefault("findings", []).append(dict(restart=restart, error=str(failure)))
            try:
                observed_identity = oracle.check(disk, actual_cut, restart)
                oracle.require(identity is None or observed_identity == identity, "Restart changed native identities or binding")
                identity = observed_identity
            except smoke.SmokeError as failure:
                # A raw-state finding must remain a failure, but still collect both restart outcomes.
                start["validation_error"] = str(failure)
                row.setdefault("findings", []).append(dict(restart=restart, error=str(failure)))
            if previous and previous["ledger"] and disk["ledger"]:
                oracle.require(previous["ledger"]["ledger_id"] == disk["ledger"]["ledger_id"]
                               and disk["ledger"]["writer_generation"] >= previous["ledger"]["writer_generation"], "Ledger identity/generation regressed")
            previous = disk
            (evidence / "result.json").write_text(json.dumps(row, indent=2), encoding="utf-8")
        row["status"] = "FAILED" if row.get("findings") else "PASS" if ordered else "NOT_COVERED"
        if row.get("findings"):
            row["error"] = "Validation failed; see findings and both restart observations."
        if not ordered:
            row["reason"] = "Unmodified driver chose the opposite first store; this is not evidence for the requested cut."
        return row
    except Exception as failure:
        row.update(status="FAILED", error=str(failure))
        return row
    finally:
        if process is not None:
            process.abort()
        try:
            cleanup(session, token, args.session_parent)
            row["cleanup"] = True
        except OSError as failure:
            row.update(cleanup_error=str(failure), retained_session=str(session), status="FAILED")
            print(f"CLEANUP FAILED: {session}: {failure}", flush=True)
        (evidence / "result.json").write_text(json.dumps(row, indent=2), encoding="utf-8")


def run(args) -> dict:
    assert_pin(args.source_commit)
    evidence = args.evidence_dir.resolve(); parent = args.session_parent.resolve()
    oracle.require(not evidence.exists() and parent.is_dir() and not parent.is_symlink()
                   and evidence != parent and parent not in evidence.parents and evidence not in parent.parents,
                   "Evidence must be new and outside the disposable sessions")
    evidence.mkdir(parents=True)
    pins = dict(source_commit=args.source_commit, jar_sha256=digest(args.jar),
                hooks={path: digest(REPO / path) for path in HOOK_FILES},
                harness={path: digest(REPO / path) for path in ("scripts/run_v180_loader_cuts.py", "scripts/loader_cut_oracle.py")})
    report = dict(status="IN_PROGRESS", pins=pins, cases=[], not_run=oracle.NOT_RUN,
                  limits="Selected S2 process-stop cuts only; no power-loss guarantee. R35 extends existing fault generation selection.")
    for cut in args.case or oracle.CASES:
        owner = f"{args.task_id} {cut} pid={os.getpid()} {datetime.now(timezone.utc).isoformat()}"
        print(f"Waiting for heavy-JVM lock: {cut}", flush=True)
        with heavy_lock(owner):
            row = run_cut(args, cut, evidence / cut, owner, pins)
            report["cases"].append(row)
        (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        print(f"{cut}: {row['status']} {row.get('error', row.get('reason', ''))}", flush=True)
        # Give a waiting worker a chance between every cut; never keep the shared lock for the whole matrix.
        time.sleep(2)
    report["status"] = "PASS_SELECTED" if all(row["status"] == "PASS" for row in report["cases"]) else "FINDINGS"
    assert_pin(args.source_commit)
    (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_server", type=Path)
    for key in ("jar", "session-parent", "evidence-dir"):
        parser.add_argument("--" + key, type=Path, required=True)
    parser.add_argument("--source-commit", required=True); parser.add_argument("--java", required=True)
    parser.add_argument("--task-id", default="W107", help="Owner of this invocation's heavy-JVM lock")
    parser.add_argument("--case", action="append", choices=oracle.CASES)
    parser.add_argument("--s3", action="store_true", help="Select the complete B3-S3 matrix (unless --case is supplied)")
    parser.add_argument("--s4", action="store_true", help="Select implemented B3-S4 cuts; this is not a claim of full family coverage")
    parser.add_argument("--startup-timeout", type=float, default=240)
    args = parser.parse_args()
    if args.s3 and not args.case:
        args.case = oracle.S3
    if args.s4 and not args.case:
        args.case = oracle.S4
    try:
        report = run(args)
        return 0 if report["status"] == "PASS_SELECTED" else 1
    except (OSError, smoke.SmokeError) as failure:
        print(f"FAIL: {failure}"); return 1


if __name__ == "__main__":
    raise SystemExit(main())
