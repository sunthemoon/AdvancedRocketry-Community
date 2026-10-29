#!/usr/bin/env python3
"""One R156 B-PLAIN/B-M case: exact native cut/stop, then two clean restarts.

The caller owns the heavy-JVM lock and leaves a 90-second hand-off between
cases. Every launch attempt, including startup failures, is counted first.
The cold predecessor fixture saves S before D exists; no save/flush follows
the I6 request. The 89-row combined terrain matrix is inventory-only here.
"""
from __future__ import annotations

import argparse
import copy
import json
from pathlib import Path
import re
import shutil
import time
import uuid

import run_dedicated_server_smoke as smoke
import run_v180_predecessor_matrix as predecessor
from run_v060_flight_server_smoke import FlightHarness, _server_command
from run_v180_first_event_moon_cuts import delivered
from run_v180_predecessor_smoke import require
from run_v180_rocket_settlement_completion import require_lock
from w152_native_inputs import compact_trace
from w164_native_inputs import preserve_bounded, compact_case
from w164_i6_oracle import CASES, check_disk, read_disk

MAX_STARTS = 66
STARTS_PER_CASE = 3
CUTS = {name: "ARCE_W164_CUT boundary=" + name for name in CASES}
CUTS.update({name: "ARCE_LANDED_DISASSEMBLY_PAUSED boundary=" + name for name in ("PLAIN_ACK", "K2", "K4")})
CUTS.update({name: "ARCE_W164_CUT boundary=K10_REMOVAL_MOVED" for name in ("K10_ON", "K10_OFF", "K10_FAIL")})
CUTS["K9_LATE"] = "event=landed_reservation_restored"
JOURNAL_ERROR = "ARCE_TRANSFER_JOURNAL_UNACKNOWLEDGED retained dirty authority; dependent effects are blocked"


def read_elected_outbox(world):
    # The inherited pre-F2 reader requires an EMPTY outbox. These plain cuts
    # deliberately run after election, so use the bounded raw journal reader.
    root = predecessor.read_journal(world / "data/advancedrocketrycommunity_rocket_transfers.dat")
    require(root.get("schema_version") == 3 and root.get("format_epoch") == "v1.8.0-first-moon-landing",
            "Changed checked landing journal envelope")
    outbox = root.get("classic_first_moon_landing", {})
    require(outbox.get("schema_version") == 1 and outbox.get("state") == "ELECTED",
            "Missing checked initial landing event")
    return outbox


def command_for(args, case, restart):
    command = _server_command(args.java); command[2] = "-Xmx4G"
    if restart == 0:
        command.insert(1, "-Dadvancedrocketrycommunity.i6NativeCut=" + case)
        if case.startswith("PLAIN_"): command.insert(1, "-Darce.releaseTest.firstEventCut=FM12a")
    return command


def check_attempt(rows, case, attempt):
    # R164 permits diagnosed harness retries; every launch still spends the same ledger.
    require(case in CASES and 1 <= attempt <= MAX_STARTS, "Unknown case or attempt")
    require(len(rows) + STARTS_PER_CASE <= MAX_STARTS, "Case would exceed the 66-start tranche")
    previous = {row.get("attempt", 1) for row in rows if row["case"] == case}
    require(previous == set(range(1, attempt)), "Missing prior attempt or repeated counted case")


def case_evidence(root, case, attempt):
    return root / (case + (f"-attempt-{attempt}" if attempt > 1 else ""))


def check_retry(root, case, attempt):
    if attempt > 1:
        prior = case_evidence(root, case, attempt - 1) / "result.json"
        require(json.loads(prior.read_text(encoding="utf-8"))["status"] == "FAIL",
                "Retry requires a completed failed case")


def check_cut_lines(case, lines, transfer):
    require(any(CUTS[case] in line for line in lines), "Selected cut was not observed")
    require(not any("CUT_EXPIRED" in line or "PROBE_EXPIRED" in line for line in lines), "Native cut expired")
    require(not any("ARCE_RELEASE_TEST_DISASSEMBLY" in line and "code=SUCCESS" in line for line in lines),
            "Cut published disassembly success")
    if case.startswith("PLAIN_"):
        require(not any("event=landed_reservation_released" in line and transfer in line for line in lines),
                "Plain effects ran before the selected cut")
    if case == "K9_LATE":
        require(any("boundary=K9_LATE_REFUSED" in line for line in lines), "Late removal lacked its refused first write")
        require(any("ARCE_RELEASE_TEST_DISASSEMBLY" in line and "code=REGION_BUSY blocks=0 rolled_back=0" in line
                    for line in lines), "Expired request restored terrain or lost its refusal result")
    if case == "K10_FAIL":
        require(any("boundary=K10_FINAL_WRITE_REFUSED" in line for line in lines), "Final write failure was not exercised")


def request(process, case, dimension, entity, fuel):
    command = (f"arce progression release-test release-reservation {entity}" if case.startswith("PLAIN_") else
               f"execute in {dimension} run arce rocket release-test disassemble {entity} discard-fuel {fuel['amount']}")
    begin = len(process.lines); sent = []; deadline = time.monotonic() + 180
    # Preparation is nondestructive. Only an explicit fresh request can consume consent.
    # Once the writer reaches M1, never submit another operation after its terminal refusal.
    admitted = False
    while time.monotonic() < deadline:
        require(process.process.poll() is None, "Native process ended before its selected cut")
        if not admitted:
            process.command(command); sent.append(command)
        for _ in range(10):
            lines = process.lines[begin:]
            if any(CUTS[case] in line for line in lines): return sent
            if any("ARCE_W164_CUT boundary=" in line for line in lines): admitted = True
            time.sleep(.1)
        if case.startswith("PLAIN_"): admitted = True
    raise smoke.SmokeError("I6 request did not reach its selected cut in the existing 180-second wait")


def check_errors(case, restart, lines):
    errors = [line.rstrip() for line in lines if "/ERROR]" in line or "/FATAL]" in line]
    allowed = []
    if not restart:
        if case in ("PLAIN_UNKNOWN", "K3_UNKNOWN", "K9_REFUSED", "K9_LATE", "K10_FAIL"): allowed.append(JOURNAL_ERROR)
        if case.startswith("PLAIN_"):
            allowed += ["ARCE_PROGRESSION_RECEIPT_BARRIER_FAILED",
                        "Atomic SavedData save failed for advancedrocketrycommunity_classic_progression; retained dirty state"]
    require(all(any(marker in line for marker in allowed) for line in errors), "Unexpected native ERROR/FATAL")
    if not restart and case in ("PLAIN_UNKNOWN", "K3_UNKNOWN", "K9_LATE", "K10_FAIL"):
        require(sum(JOURNAL_ERROR in line for line in errors) == 1, "Missing/repeated injected journal failure")
    return errors


def run_case(args, evidence, started):
    case = args.case
    session = predecessor.TEMP.resolve() / ("W164-R156-" + case + "-" + uuid.uuid4().hex)
    require(not session.exists(), "Occupied native session")
    evidence.mkdir(); session.mkdir(); token = uuid.uuid4().hex
    marker = session / ".w164-r156-owned"; marker.write_text(token, encoding="ascii")
    row = dict(case=case, attempt=args.attempt, source=args.source_commit, jar_sha256=predecessor.digest(args.jar),
               status="RUNNING", starts=[])
    process = None; old = None; event = None; completed = []
    try:
        libraries = args.source_server.resolve() / "libraries"
        require(libraries.is_dir() and not smoke.is_link_or_junction(libraries)
                and not any(smoke.is_link_or_junction(p) for p in libraries.rglob("*")), "Unsafe Forge template")
        require((libraries / "net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt").is_file(), "Wrong Forge template")
        shutil.copytree(libraries, session / "libraries")
        (session / "mods").mkdir(); shutil.copy2(args.jar, session / "mods" / args.jar.name)
        properties = smoke.expected_server_properties(smoke.allocate_port(), True)
        properties.update({"level-type": "minecraft:flat", "max-tick-time": "60000", "generator-settings": json.dumps({
            "layers": [{"block": "minecraft:bedrock", "height": 1}], "biome": "minecraft:plains"})})
        (session / "server.properties").write_text("".join(f"{key}={value}\n" for key, value in sorted(properties.items())), encoding="ascii")
        (session / "eula.txt").write_text("eula=true\n", encoding="ascii")
        world = session / "world"
        for restart in range(3):
            predecessor.assert_pin(args.source_commit); require_lock(args.lock_owner)
            require(predecessor.digest(args.jar) == row["jar_sha256"], "Native JAR changed")
            entry = dict(restart=restart, command=command_for(args, case, restart),
                         disk_space=predecessor.common.disk_space(), memory=predecessor.common.physical_memory())
            row["starts"].append(entry); started(entry)
            process = smoke.CapturedProcess(entry["command"], session, evidence / f"start-{restart}.log")
            process.wait_for(smoke.READY_MARKER, args.startup_timeout)
            if restart == 0:
                seed = predecessor.assemble(process, 0); predecessor.save(process)
                process.command(f"arce rocket release-test predecessor-fixture {seed['entity']} UNSAVED")
                process.command("forceload remove all")
                index = process.wait_for(predecessor.READY, 120)
                _, transfer, logical, source, destination, loaded, dimension = predecessor.READY.search(process.lines[index]).groups()
                require(loaded == "false" and source == seed["entity"] and logical == seed["logical"], "Cold source fixture mismatch")
                old = predecessor.journal(world)["transfers"][0]
                (evidence / "predecessor.json").write_text(json.dumps(old, indent=2), encoding="utf-8")
                row.update(transfer=transfer, logical=logical, source_entity=source, destination_entity=destination, dimension=dimension)
                process.command("arce rocket release-test predecessor-land")
                process.wait_for(re.compile("ARCE_I7_FIXTURE_LANDED"), 30, start_at=index)
                FlightHarness.refuel(process, dimension, destination)
                fuel = copy.deepcopy(old["destination_flight"]["fuel"])
                fuel["amount"] = 1000; fuel["propulsion"]["primary"]["amount"] = 1000; fuel["propulsion"]["revision"] += 1
                row["expected_fuel"] = fuel
                if case.startswith("PLAIN_"):
                    process.wait_for(re.compile("first_event_cut receipt_move_refused"), 120, start_at=index)
                    outbox = read_elected_outbox(world)
                    require(outbox["state"] == "ELECTED", "Missing checked initial landing event")
                    event = row["event"] = outbox["event"]
                entry["requests"] = request(process, case, dimension, destination, fuel)
                if case in ("PLAIN_UNKNOWN", "K3_UNKNOWN"):
                    process.wait_for(re.compile(re.escape(JOURNAL_ERROR)), 60)
                if case.startswith("K10_"):
                    if case == "K10_OFF":
                        process.command("save-off"); process.wait_for(re.compile("Automatic saving is now disabled"), 30)
                    process.command("stop"); entry["exit_code"] = process.finish(180)
                    require(entry["exit_code"] == 0, "Normal stop failed")
                else:
                    process.process.kill(); entry["exit_code"] = process.finish(timeout=30)
                entry["cut_lines"] = [line.rstrip() for line in process.lines if "ARCE_W164" in line or "ARCE_LANDED" in line
                                      or "ARCE_RELEASE_TEST_DISASSEMBLY" in line or "landed_reservation" in line]
            else:
                # Observe the complete recorded ends. These operator loads do not modify the original cut copy.
                entry["observation_loads"] = []
                for endpoint in ("source_snapshot", "destination_snapshot"):
                    snapshot = old[endpoint]; x, _, z = snapshot["source_origin"]
                    command = f"execute in {snapshot['source_dimension']} run forceload add {x} {z}"
                    entry["observation_loads"].append(command); process.command(command)
                process.wait_for(re.compile("ARCE_ROCKET_ENTITY_ACTIVE entity=" + row["destination_entity"]), 120)
                entry["live_report"] = predecessor.observe_stationary(process, row["dimension"], row["destination_entity"])
                if case.startswith("PLAIN_"):
                    entry["delivery"] = delivered(case, process, str(predecessor.nbt_uuid(event["event_id"])), 120)
                time.sleep(3)
                process.command("stop"); entry["exit_code"] = process.finish(180)
                require(entry["exit_code"] == 0, "Restart did not stop normally")
            # Preserve raw inputs before asserting outcomes, so a failed oracle is independently auditable.
            entry["native_inputs"] = preserve_bounded(world, evidence, restart)
            disk = read_disk(world, old, case)
            (evidence / f"disk-{restart}.json").write_text(json.dumps(disk, indent=2), encoding="utf-8")
            if restart == 0: check_cut_lines(case, process.lines, row["transfer"])
            entry["errors"] = check_errors(case, restart, process.lines)
            authority = check_disk(case, disk, old, row["expected_fuel"], restart, event)
            if restart: completed.append(authority)
            if restart == 2: require(completed[0] == completed[1], "Second restart changed the complete authority")
            entry["status"] = "PASS"; process = None
        row["status"] = "PASS"
    except Exception as error:
        row.update(status="FAIL", error=str(error))
    finally:
        if process is not None: process.abort()
        compact_case(row, evidence)
        row["traces"] = [compact_trace(path) for path in sorted(evidence.glob("start-*.log"))]
        (evidence / "result.json").write_text(json.dumps(row, indent=2), encoding="utf-8")
        require(session.resolve().parent == predecessor.TEMP.resolve() and not smoke.is_link_or_junction(session)
                and marker.read_text(encoding="ascii") == token, "Cleanup ownership changed")
        shutil.rmtree(session)
    return row


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_server", type=Path)
    for name in ("jar", "evidence-dir"): parser.add_argument("--" + name, type=Path, required=True)
    for name in ("source-commit", "java", "lock-owner"): parser.add_argument("--" + name, required=True)
    parser.add_argument("--case", choices=CASES, required=True)
    parser.add_argument("--attempt", type=int, choices=range(1, MAX_STARTS + 1), default=1)
    parser.add_argument("--startup-timeout", type=float, default=240)
    args = parser.parse_args()
    require_lock(args.lock_owner); predecessor.assert_pin(args.source_commit)
    root = args.evidence_dir.resolve(); root.mkdir(parents=True, exist_ok=True)
    require(sum(path.stat().st_size for path in root.parent.rglob("*") if path.is_file()) < 95_000_000,
            "Insufficient evidence headroom for a native case")
    evidence = case_evidence(root, args.case, args.attempt)
    require(not evidence.exists(), "Never overwrite a counted case")
    ledger = root / "starts.json"
    rows = json.loads(ledger.read_text(encoding="utf-8")) if ledger.exists() else []
    check_attempt(rows, args.case, args.attempt)
    check_retry(root, args.case, args.attempt)
    def started(entry):
        require_lock(args.lock_owner); require(len(rows) < MAX_STARTS, "Native start ceiling reached")
        rows.append(dict(case=args.case, attempt=args.attempt, restart=entry["restart"], source=args.source_commit,
                         command=entry["command"], status="LAUNCH_ATTEMPT"))
        ledger.write_text(json.dumps(rows, indent=2), encoding="utf-8")
    row = run_case(args, evidence, started)
    require(sum(path.stat().st_size for path in root.parent.rglob("*") if path.is_file()) < 100_000_000,
            "W164 evidence exceeds the task budget")
    print(json.dumps(dict(case=args.case, status=row["status"], error=row.get("error"), counted_starts=len(rows))), flush=True)
    return 0 if row["status"] == "PASS" else 1


if __name__ == "__main__": raise SystemExit(main())
