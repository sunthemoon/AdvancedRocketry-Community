#!/usr/bin/env python3
"""Loader creation, normal, chain and reconciliation cuts on committed source, with two restarts and per-cut cleanup.

K4/K5 record the first store chosen by the
unmodified driver; an opposite order is NOT_COVERED, not a passed cut. A new invocation may observe the other order.
"""
from __future__ import annotations

import argparse
import ctypes
from contextlib import contextmanager
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import shutil
import time
import uuid

import loader_cut_nbt as nbt
import loader_cut_oracle as oracle
import run_dedicated_server_smoke as smoke
from run_v060_flight_server_smoke import _server_command
from run_v180_escrow_forced_stop import digest
from run_v180_escrow_pre_f2 import assert_pin
from run_v180_rocket_settlement_completion import require_lock

REPO = Path(__file__).resolve().parents[1]
LOCK = Path("D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm")
HOOK_FILES = tuple("src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/" + name + ".java"
                   for name in ("EscrowLoaderReleaseProbe", "EscrowLoaderCutProbe", "EscrowLoaderStorageProbe", "EscrowLoaderRecoveryProbe", "EscrowReleaseProbe", "EscrowLedgerWriter", "EscrowResourceQueue",
                                "EscrowReconciliationAction", "EscrowReconciliationDriver", "EscrowRuntime", "EscrowBarrier",
                                "EscrowLoaderFloorProbe", "EscrowLoaderNamedProbe", "EscrowLoaderFollowUpProbe", "EscrowStoreFault", "EscrowChunkStore"))
HARNESS_FILES = ("scripts/run_v180_loader_cuts.py", "scripts/loader_cut_oracle.py", "scripts/loader_cut_nbt.py", "scripts/loader_named_cut_oracle.py")
FIRST = re.compile(r"ARCE_LOADER_MATRIX_FIRST cut=(\S+) recovery=(true|false) delegate=(\w+) issued=(\d+) records=(\d+) pending=(\d+)")
PAUSED = re.compile(r"ARCE_LOADER_MATRIX_PAUSED cut=(\S+) hook=(\S+) first_store=(\w+) thread=(.+)")
BASELINE_FAILURE = re.compile(r"ARCE_LOADER_MATRIX_BASELINE_FAILURE cut=(\S+) failure=(\d+) outcome=(\w+) pending=(\d+) unavailable=(true|false) status=(\w+)")
FIXTURE_SAVE = re.compile(r"ARCE_LOADER_MATRIX_FIXTURE_SAVE cut=LF_M_C1 terrain=4,4 entities=4,4 tanks=\[4000, 4000\] "
                          r"epochs=\[1, 1\] source_markers=\[1, 1\] bank=1000 rocket_epoch=1 rocket_markers=1(?:\s|$)")
STORAGE_FAILURE = re.compile(r"ARCE_LOADER_MATRIX_FAILURE cut=(\S+) unknown=(true|false) records=(\d+) pending=(\d+) issued=(\d+)")
PROOF_FAILED = re.compile(r"ARCE_LOADER_MATRIX_PROOF_FAILED cut=(\S+) reason=(\w+) io_failures=(\d+) refusals=(\d+) superseding=(\d+) "
                          r"records=1 pending=1(?:\s|$)")
# W107C: the expected first failed proof of each R43 storage cut, and whether it counts as an IO failure (otherwise as a
# refusal); a different reason or counter is a finding.
PROOF_EXPECTED = {"LF_M_E1_OWN_STORE": ("BARRIER_IO", True), "LF_M_E2_EXTERNAL": ("BARRIER_EXTERNAL", False)}
R3_REPAIR = {"R3_ENDPOINT_FOREIGN": ("66", "4000", "1", "ESCROW_PENDING"), "R3_ENDPOINT_OTHER": ("66", "3000", "1", "ESCROW_PENDING"),
             "R3_ENDPOINT_RELOCATED": ("68", "4000", "1", "ESCROW_PENDING"), "R3_ENDPOINT_MISSING": ("ABSENT", "-1", "-1", "NONE")}
R3_ORPHAN = {"R3_LEDGER_MISSING": "NONE", "R3_LEDGER_REPLACED": "OTHER", "R3_LEDGER_BEHIND": "SAME"}
W107C = ("LF_M_E1_OWN_STORE", "LF_M_E2_EXTERNAL") + oracle.S5_FLOORS + oracle.S5_R3
FOLLOW_UPS = oracle.NAMED + tuple(cut for cut in oracle.S5_FLOORS if not cut.endswith("256"))


def w107c_observations(cut: str, restart: int, lines: list[str]) -> None:
    """R43's E1, E2, R1 and R3 outcomes at every start; R43 item 3's E2 and R3's reasons are compared, not assumed."""
    name = cut.removeprefix(oracle.PREFIX); c = re.escape(cut)
    oracle.require(not any("ARCE_LOADER_MATRIX_HOLD_TIMEOUT" in line or "ARCE_LOADER_MATRIX_FLOOR_UNSETTLED" in line
                           or "ARCE_LOADER_MATRIX_LEDGER_CAPTURE_FAILED" in line for line in lines),
                   "The held, repaired or settled state was not reached within the unchanged bound")
    if name == "E1_OWN_STORE":
        faults = [line for line in lines if "ARCE_LOADER_MATRIX_STORE_FAULT" in line]
        oracle.require(len(faults) == (0 if restart else 1) and all("cut=LF_M_E1_OWN_STORE chunk=4,4" in line for line in faults),
                       "E1's own-store fault fired other than once, at the start, for the fixture slot")
    if name == "E2_EXTERNAL" and restart:
        one_match(re.compile(r"ARCE_LOADER_MATRIX_HELD cut=" + c + r" reason=BARRIER_EXTERNAL phase=STALLED records=1 issued=1 pending=1 "
                             r"refusals=[1-9]\d* io_failures=0 tank=4000 bank=1000(?:\s|$)"), lines)
    if name in R3_REPAIR and restart:
        loader, tank, markers, attempt = R3_REPAIR[name]
        match = one_match(re.compile(r"ARCE_LOADER_MATRIX_REPAIR cut=" + c + r" reason=(\w+) phase=REPAIR records=1 issued=1 pending=1 "
                                     r"loader=(\S+) tank=(-?\d+) loader_markers=(-?\d+) rocket_markers=1 bank=1000 attempt=(\w+)(?:\s|$)"), lines)
        oracle.require(match[1] in oracle.MANUAL and match.groups()[1:] == (loader, tank, markers, attempt),
                       "R3 endpoint restart is not a locked manual repair of the edited endpoint")
    if name in R3_ORPHAN:
        if restart:
            ledger = name.removeprefix("R3_LEDGER_")
            one_match(re.compile(r"ARCE_LOADER_MATRIX_ORPHAN cut=" + c + r" ledger=" + ledger + r" binding=" + R3_ORPHAN[name]
                                 + r" issued=0 marker_seq=1 records=0 loader_markers=1 rocket_markers=1 tank=4000 bank=1000 attempt=ESCROW_PENDING(?:\s|$)"), lines)
        elif name != "R3_LEDGER_MISSING":
            one_match(re.compile(r"ARCE_LOADER_MATRIX_LEDGER_CAPTURED cut=" + c + r" generation=1 bytes=\d+ sha256=[0-9a-f]{64}(?:\s|$)"), lines)
    if name in oracle.FLOORS:
        floor_observations(cut, name, restart, lines)


def floor_observations(cut: str, name: str, restart: int, lines: list[str]) -> None:
    name = name.removesuffix("_ACK")
    c = re.escape(cut); coalesced = name.endswith("COALESCED"); saturated = name.endswith("256")
    seeded = 256 if saturated else 255; issued = seeded + (2 if coalesced else 1)
    refused = re.compile(r"ARCE_LOADER_MATRIX_FLOOR_REFUSED cut=" + c + r" phase=(\w+) status=ESCROW_FULL used=(\d+) reserved=(\d+) total=(\d+) next=(\d+)(?:\s|$)")
    refusals = [tuple(m.groups()) for line in lines if (m := refused.search(line))]
    admitted = re.compile(r"ARCE_LOADER_MATRIX_FLOOR_ADMIT cut=" + c + r" phase=(\w+) named=(true|false) used=(\d+) reserved=(\d+) total=(\d+)(?:\s|$)")
    admissions = [tuple(m.groups()) for line in lines if (m := admitted.search(line))]
    named = str(not saturated).lower(); pending = ("255", "1", "256") if not saturated else ("256", "0", "256")
    if not restart:
        one_match(re.compile(r"ARCE_LOADER_MATRIX_FLOORS cut=" + c + r" seeded=" + str(seeded) + r" named=" + named
                             + r" extra_named=" + str(not coalesced).lower() + r" ledger=\S+(?:\s|$)"), lines)
        oracle.require(admissions == [("first", named) + pending] + ([("same_key", named) + pending] if coalesced else []),
                       "Floor admission did not reserve exactly one new key")
        oracle.require(refusals == ([] if coalesced else [("start",) + pending + (str(seeded + 2),)]),
                       "The 257th key was not refused before mutation")
        return
    settled = one_match(re.compile(r"ARCE_LOADER_MATRIX_FLOOR_SETTLED cut=" + c + r" fills=(\d+) floors=256 named_floor=(\S+) used=256 reserved=0(?:\s|$)"), lines)
    oracle.require(settled[1] == str(issued - seeded) and settled[2] == ("NONE" if saturated else f"epoch{issued - seeded}_seq{issued}"),
                   "Retirement did not install exactly the named fill's one floor")
    expected = [] if coalesced else ([("restart_pending",) + pending + (str(issued + 1),)] if restart == 1 else []) + [
        ("restart_settled", "256", "0", "256", str(issued + 1))]
    oracle.require(refusals == expected, "A restart admitted or failed to refuse the distinct key")


def storage_observations(cut: str, lines: list[str]) -> None:
    name = cut.removeprefix(oracle.PREFIX)
    if cut in PROOF_EXPECTED:
        reason, io = PROOF_EXPECTED[cut]
        failure = one_match(PROOF_FAILED, lines); failures, refusals = int(failure[3]), int(failure[4])
        counted = failures >= 1 and refusals == 0 if io else failures == 0 and refusals >= 1
        oracle.require(failure[1] == cut and failure[2] == reason and counted and failure[5] == "0",
                       f"The first failed proof was not {reason}: {failure[0]}")
        if name == "E2_EXTERNAL":
            one_match(re.compile(r"ARCE_LOADER_MATRIX_NOISE cut=LF_M_E2_EXTERNAL pos=76,100,76 bytes=1572864(?:\s|$)"), lines)
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


def unload_observations(cut: str, lines: list[str]) -> None:
    failures = [match for line in lines if (match := STORAGE_FAILURE.search(line))]
    oracle.require(bool(failures) and all(m[1] == cut and tuple(m.groups()[1:]) == ("true", "0", "1", "0") for m in failures),
                   "UNKNOWN unload did not retain its unacknowledged reservation")
    one_match(re.compile(r"ARCE_LOADER_MATRIX_UNLOADED cut=" + re.escape(cut)
                         + r" ticks=20 records=0 pending=1 issued=0 same_candidate=true"), lines)


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


def physical_memory() -> int:
    class Status(ctypes.Structure):
        _fields_ = [("length", ctypes.c_ulong), ("load", ctypes.c_ulong)] + [
            (key, ctypes.c_ulonglong) for key in ("total", "available", "page_total", "page_available", "virtual_total", "virtual_available", "extended")]
    status = Status(); status.length = ctypes.sizeof(status)
    oracle.require(bool(ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(status))), "Cannot check physical memory")
    oracle.require(status.available > 6 * 1024**3, "Heavy start refused: physical memory needs more than 6 GiB free")
    return status.available


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
            follow_up = bool(getattr(args, "follow_ups", False) and cut in FOLLOW_UPS and restart)
            if follow_up:
                command.insert(1, "-Dadvancedrocketrycommunity.escrowLoaderFollowUps=true")
            creation = cut.removeprefix(oracle.PREFIX) in oracle.CREATION + oracle.CREATION_SUPPLEMENTAL
            if creation and restart == 2:
                command.insert(1, "-Dadvancedrocketrycommunity.escrowLoaderRetry=true")
            start = dict(restart=restart, command=command, free_bytes=free, free_physical_memory=physical_memory(), lock_owner=owner)
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
                if cut in ("LF_M_E2_CAPTURE", "LF_M_E1_OWN_STORE", "LF_M_E2_EXTERNAL"):
                    # The source capture is superseded. A destination-first driver's earlier proof already stored
                    # its bank; a source-first driver has not stored the bank. Neither case permits retirement.
                    # W107C: E1's vanilla store and E2's external store do write the source before their proofs fail.
                    actual_cut = "LF_M_K6" if paused[3] == "DESTINATION" else "LF_M_K4"
            elif follow_up:
                process.wait_for(re.compile(r"ARCE_LOADER_MATRIX_FOLLOWUP_PAUSED cut=" + re.escape(cut)
                                            + r" hook=AFTER_REQUESTS_BEFORE_L1(?:\s|$)"), args.startup_timeout)
                # The first-fill checkpoint was saved before requests. Kill before the admitted queue can write L1;
                # this preserves the original, unchanged raw oracle and second-restart first-fill replay check.
                process.process.kill(); process.abort()
                start["stop"] = "FORCED_AFTER_REQUESTS_BEFORE_SECOND_FILL_L1"
            else:
                process.wait_for(re.compile(r"ARCE_LOADER_MATRIX_RESULT cut=" + re.escape(cut) + r"(?:\s|$)"), args.startup_timeout)
                process.command("stop"); start["exit_code"] = process.finish()
                oracle.require(start["exit_code"] == 0, "Native restart exit was not zero")
                actual_cut = cut
            lines = list(process.lines); process = None
            start["observations"] = [line for line in lines if "ARCE_LOADER_MATRIX_" in line]
            disk = oracle.inspect(session / "world", cut); start["disk"] = disk
            if restart and cut in oracle.S5_R3:
                disk["edited"] = row["edited"]  # R3 restarts compare with the offline-edited stopped copy.
            try:
                start["first_tick"] = first_tick(lines, cut, restart, previous)
                oracle.require(not any("/ERROR]" in line or "/FATAL]" in line for line in lines), "Native start logged ERROR/FATAL")
                if restart == 0 and cut.removeprefix(oracle.PREFIX) in oracle.CHAINS and cut != "LF_M_C1":
                    one_match(re.compile(r"ARCE_LOADER_MATRIX_CHAIN cut=" + re.escape(cut) + r" latest=2 reverse_refused=true records=2"), lines)
                if restart == 0 and cut == "LF_M_C1":
                    # R36 item 1: the gated autosave at the stop point writes exactly the required live prefix.
                    one_match(FIXTURE_SAVE, lines)
                if cut in ("LF_M_K5", "LF_M_D2_C4_BEFORE") and restart == 0:
                    one_match(re.compile(r"ARCE_LOADER_MATRIX_PRIMED cut=" + re.escape(cut) + r" tank=5000 epoch=0 stores=0"), lines)
                if creation and restart == 0:
                    creation_observations(cut, lines)
                if cut in oracle.S4 and restart == 0:
                    storage_observations(cut, lines)
                if cut in oracle.S5 and restart == 0:
                    unload_observations(cut, lines)
                if creation and restart:
                    oracle.require(any("phase=creation_restart " in line for line in lines), "Missing unchanged creation-restart observation")
                if cut in W107C:
                    w107c_observations(cut, restart, lines)
                if cut in oracle.S5_FLOORS and restart:
                    boundary = one_match(re.compile(r"ARCE_LOADER_MATRIX_MUTATION_BOUNDARY cut=" + re.escape(cut)
                                                     + r" retired=true conserved=true mutation_allowed=(true|false)(?:\s|$)"), lines)
                    start["floor_outcome"] = dict(fill_retired=True, resources_conserved=True, mutation_allowed=boundary[1] == "true")
                if cut in oracle.NAMED:
                    import loader_named_cut_oracle
                    start["named_outcome"] = loader_named_cut_oracle.observations(cut, restart, lines)
                if follow_up:
                    import loader_named_cut_oracle
                    start["request_outcomes"] = loader_named_cut_oracle.follow_up_observations(cut, lines)
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
            if restart == 0 and cut in oracle.S5_R3:
                # R36 item 2: the one offline edit of the stopped disposable copy, with the original bytes kept.
                row["edit"] = nbt.r3_edit(session / "world", cut.removeprefix(oracle.PREFIX), evidence / "edit-original")
                previous = row["edited"] = oracle.inspect(session / "world", cut)
                identity = None  # Identities are compared between the restarts of the edited copy.
            (evidence / "result.json").write_text(json.dumps(row, indent=2), encoding="utf-8")
        row["status"] = "FAILED" if row.get("findings") else "PASS" if ordered else "NOT_COVERED"
        if row["status"] == "PASS" and getattr(args, "follow_ups", False) and cut in FOLLOW_UPS:
            row.update(status="KNOWN_LIMITATION", conservation="PASS",
                       reason="First fill retired/conserved on both restarts; launch requests refused. See separate second-fill outcomes.")
        elif row["status"] == "PASS" and "NAMED_CROSS_" in cut:
            row.update(status="KNOWN_LIMITATION", reason="Fill retired/conserved on both restarts; F1b blocks resource mutation. Launch not observed.")
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
                harness={path: digest(REPO / path) for path in HARNESS_FILES})
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
    parser.add_argument("--follow-ups", action="store_true", help="R54: observe real requests on each named restart and stop before second-fill L1")
    parser.add_argument("--case", action="append", choices=oracle.CASES)
    parser.add_argument("--s3", action="store_true", help="Select the complete B3-S3 matrix (unless --case is supplied)")
    parser.add_argument("--s4", action="store_true", help="Select implemented B3-S4 cuts; this is not a claim of full family coverage")
    parser.add_argument("--w107c", action="store_true", help="Select W107C's E1, E2, R1 and R3 cuts (unless --case is supplied)")
    parser.add_argument("--startup-timeout", type=float, default=240)
    args = parser.parse_args()
    if args.s3 and not args.case:
        args.case = oracle.S3
    if args.s4 and not args.case:
        args.case = oracle.S4
    if args.w107c and not args.case:
        args.case = W107C
    try:
        report = run(args)
        return 0 if report["status"] == "PASS_SELECTED" else 1
    except (OSError, smoke.SmokeError) as failure:
        print(f"FAIL: {failure}"); return 1


if __name__ == "__main__":
    raise SystemExit(main())
