#!/usr/bin/env python3
"""Measure gated ordinary-flight journal scheduling on an isolated packaged server."""
from __future__ import annotations

import argparse
from contextlib import contextmanager
import gzip
import json
import math
import os
from pathlib import Path
import re
import shutil
import statistics
import time
import uuid

import run_dedicated_server_smoke as smoke
from inspect_celestial_saved_data import NbtReader
from run_v060_flight_server_smoke import FlightHarness, EARTH, MOON, ASSEMBLY_LOG, LAUNCH_LOG, PHASE_LOG, _server_command
from run_v180_rocket_journal_failures import allocated_slots, JOURNAL
from run_v180_escrow_forced_stop import digest, record, ledger, LEDGER
from run_v180_governed_benchmark import java_processes, utc
from run_v180_escrow_pre_f2 import assert_pin

CONSUMERS = {"f1": "PF_CONSUMER_F1", "cancel": "PF_CONSUMER_CANCEL", "settlement": "SR_ACCEPT__CONSUMER"}
CASES = ("staggered", "simultaneous", "held", *CONSUMERS)
PROBE = "rocket-journal-probe.json"
CONSUMER_PROBE = "escrow-consumer-timing.json"
LOCK = Path("D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm")
TEMP = Path("D:/GitHub/ARCE-Task-Evidence/Temp")


def summary(values: list[float]) -> dict:
    if not values:
        return dict(count=0, mean=None, p50=None, p95=None, p99=None, maximum=None)
    ordered = sorted(values)
    def percentile(q):
        return ordered[max(0, math.ceil(len(ordered) * q) - 1)]
    return dict(count=len(values), mean=statistics.mean(values), p50=percentile(.5),
                p95=percentile(.95), p99=percentile(.99), maximum=max(values))


def analyze(probe: dict, logical_ids: list[str]) -> dict:
    """Missing or capped evidence is a failure, not a zero-miss observation."""
    if probe.get("schema") != 1 or probe.get("capped") is not False or len(set(logical_ids)) != 8:
        raise smoke.SmokeError("Incomplete, capped or invalid eight-flight observation")
    if not probe.get("ticks"):
        raise smoke.SmokeError("Missing tick samples")
    completions = sorted((e for e in probe["global"] if e["kind"] == "completion"), key=lambda e: e["nanos"])
    snapshots = sorted((e for e in probe["global"] if e["kind"] == "snapshot"), key=lambda e: e["nanos"])
    if not completions or not snapshots:
        raise smoke.SmokeError("Missing writer timeline")
    failures = [e for e in completions if e["detail"] != "COMMITTED"]
    flights, missed, bounds, latencies, mutation_times = {}, [], [], [], []
    largest_queue = 0
    intervals = []
    for logical in logical_ids:
        events = probe["records"].get(logical, [])
        visits = [e for e in events if e["kind"] == "visit"]
        if not visits:
            raise smoke.SmokeError("No dispatcher visits for " + logical)
        steps = {e["tick"] for e in events if e["kind"] == "step"}
        for visit in visits:
            if visit["value"] == 1 and visit["tick"] not in steps:
                missed.append(dict(logical=logical, tick=visit["tick"], phase=visit["detail"], reason="before_phase_dispatch"))
        by_tick = {}
        for e in events:
            by_tick.setdefault(e["tick"], []).append(e)
        for tick, row in by_tick.items():
            kinds = {e["kind"] for e in row}
            if "due" in kinds and not kinds.intersection({"work", "mutation", "owned_wait"}):
                missed.append(dict(logical=logical, tick=tick, reason="due_without_work_or_owned_wait"))
        timeline = []
        for change in (e for e in events if e["kind"] == "mutation"):
            mutation_times.append(change["nanos"])
            capture = next((e for e in snapshots if e["sequence"] >= change["sequence"] and e["nanos"] >= change["nanos"]), None)
            ack = next((e for e in completions if e["sequence"] >= change["sequence"]
                        and e["detail"] == "COMMITTED" and e["nanos"] >= change["nanos"]), None)
            if capture is None or ack is None:
                raise smoke.SmokeError("Unacknowledged mutation in observation")
            writes = sum(change["nanos"] <= e["nanos"] <= ack["nanos"] for e in completions)
            entry = dict(phase=change["detail"], tick=change["tick"], mutation_sequence=change["sequence"],
                         first_snapshot_sequence=capture["sequence"], acknowledged_sequence=ack["sequence"],
                         acknowledged_tick=ack["tick"], writes_to_ack=writes,
                         acknowledgement_ms=(ack["nanos"] - change["nanos"]) / 1e6)
            timeline.append(entry)
            if not failures and writes > 3:
                bounds.append(dict(logical=logical, **entry))
        pending = {}
        for e in events:
            if e["kind"] == "continuation_submit":
                pending[e["sequence"]] = e
                largest_queue = max(largest_queue, e["value"])
            elif e["kind"] in ("continuation_run", "continuation_discard"):
                submitted = pending.pop(e["sequence"], None)
                if submitted is None:
                    raise smoke.SmokeError("Continuation without observed submission")
                latencies.append((e["nanos"] - submitted["nanos"]) / 1e6)
                if e["kind"] == "continuation_discard":
                    raise smoke.SmokeError("Ordinary flight continuation was discarded")
        if pending:
            raise smoke.SmokeError("Pending continuation at probe dump")
        states, last = [], None
        for e in (e for e in events if e["kind"] == "flight"):
            if e["detail"] != last:
                states.append(dict(state=e["detail"], tick=e["tick"], nanos=e["nanos"]))
                last = e["detail"]
        flights[logical] = dict(phases=timeline, entity_states=states, visits=len(visits),
                               actionable_visits=sum(e["value"] == 1 for e in visits))
        landed = next((e for e in events if e["kind"] == "flight" and e["detail"] == "LANDED"), None)
        if landed is None or not timeline:
            raise smoke.SmokeError("Missing ordinary landing or mutation timeline")
        intervals.append((min(e["nanos"] for e in events if e["kind"] == "mutation"), landed["nanos"]))
    mutation_times += [e["nanos"] for e in probe["global"] if e["kind"] == "mutation"]
    times = [0] + sorted(mutation_times) + [probe["elapsed_nanos"]]
    tick_ms = summary([t["nanos"] / 1e6 for t in probe["ticks"]])
    budget = tick_ms["mean"] <= 25 and tick_ms["p95"] <= 50 and tick_ms["p99"] <= 100
    peak = max(sum(a <= instant <= b for a, b in intervals) for instant in [a for a, _ in intervals])
    if peak != 8:
        raise smoke.SmokeError("Ordinary traffic never reached eight concurrent flights")
    return dict(flights=flights, peak_concurrent=peak, missed_actionable_steps=missed, hard_criterion=len(missed),
                three_write_bound_findings=bounds, writer_failures=failures,
                continuation_ms=summary(latencies), writer_ms=summary([e["value"] / 1e6 for e in completions]),
                largest_continuation_queue=largest_queue, largest_writer_queue=max(e["value"] for e in snapshots),
                main_thread_ms=tick_ms, main_thread_budget_pass=budget,
                spikes_over_500_ms=[dict(tick=t["tick"], ms=t["nanos"] / 1e6) for t in probe["ticks"] if t["nanos"] > 500_000_000],
                acknowledged_writes=sum(e["detail"] == "COMMITTED" for e in completions),
                w11b2_reference_writes=48,
                longest_no_mutation_ms=max(b - a for a, b in zip(times, times[1:])) / 1e6,
                window_ms=probe["elapsed_nanos"] / 1e6)


def analyze_consumer(timing: dict, journal: dict, case: str, logical_ids: list[str]) -> dict:
    if timing.get("schema") != 1 or timing.get("capped") is not False or journal.get("capped") is not False:
        raise smoke.SmokeError("Missing or capped consumer measurement")
    events = timing.get("events", [])
    start_phase = case + (".start" if case == "settlement" else ".attempt")
    starts = [e for e in events if e["phase"] == start_phase]
    markers = [e for e in journal["global"] if e["kind"] == "consumer"]
    began = next((e for e in markers if e["detail"] == start_phase), None)
    if not starts or began is None:
        raise smoke.SmokeError("Consumer never started")
    finish = next((e for e in events if e["phase"] == case + ".complete"), None)
    ended = next((e for e in markers if e["detail"] == case + ".complete"), None)
    if bool(finish) != bool(ended):
        raise smoke.SmokeError("Consumer completion observations disagree")
    # Use journal-native timestamps for gaps; the independent sink shares no relative origin.
    end = ended["nanos"] if ended else journal["elapsed_nanos"]
    admissions, landings = [], []
    mutations = [e["nanos"] for e in journal["global"] if e["kind"] == "mutation"]
    for logical, rows in journal["records"].items():
        mutations.extend(e["nanos"] for e in rows if e["kind"] == "mutation")
        if logical in logical_ids:
            admissions.append(min(e["nanos"] for e in rows if e["kind"] == "mutation"))
            landings.append(min(e["nanos"] for e in rows if e["kind"] == "flight" and e["detail"] == "LANDED"))
    if len(admissions) != 8 or not max(admissions) <= began["nanos"] < min(landings):
        raise smoke.SmokeError("Consumer did not start during the eight-flight overlap")
    boundaries = [began["nanos"]] + sorted(n for n in mutations if began["nanos"] < n < end) + [end]
    window = [e for e in events if e["nanos"] >= starts[0]["nanos"]
              and (finish is None or e["nanos"] <= finish["nanos"])]
    reasons = {}
    for e in window:
        if any(e["phase"].startswith(case + "." + label + ".") for label in ("wait", "stall", "refused")):
            reasons[e["phase"]] = reasons.get(e["phase"], 0) + 1
    return dict(completed=finish is not None, start_tick=began["tick"], end_tick=ended["tick"] if ended else None,
                completion_ms=(finish["nanos"] - starts[0]["nanos"]) / 1e6 if finish else None,
                observation_ms=(end - began["nanos"]) / 1e6,
                attempts=sum(e["phase"] == case + ".attempt" for e in window), reasons=reasons,
                confirmation_attempts=sum(e["phase"] == case + ".confirm_attempt" for e in window),
                barrier_ms=summary([e["value"] / 1e6 for e in window if e["phase"] == case + ".barrier"]),
                ledger_write_ms=summary([e["value"] / 1e6 for e in window if e["phase"] == "ledger_io"]),
                timing_samples=window,
                longest_no_mutation_while_waiting_ms=max(b - a for a, b in zip(boundaries, boundaries[1:])) / 1e6)


def consumer_disk(world: Path, logical_ids: list[str], case: str) -> dict:
    root = ledger(world / "data" / LEDGER)
    others = []
    for region in sorted(world.rglob("r.*.mca")):
        if region.parent.name != "entities":
            continue
        for slot in allocated_slots(region):
            for entity in record(region, slot).get("Entities", []):
                data = entity.get("RocketEntityData")
                if data and str(nbt_uuid(data["assembly_transaction_id"])) not in logical_ids:
                    others.append(dict(entity=entity["UUID"], data=data, position=entity["Pos"]))
    if len(others) != 1:
        raise smoke.SmokeError("Consumer has missing or duplicate survivor")
    flight = others[0]["data"]["flight_data"]
    if flight["fuel"]["amount"] != 1000 or flight["fuel"].get("committed_debits", []):
        raise smoke.SmokeError("Consumer changed its fuel")
    if root["records"] or root["armed_flights"] or root["floors"]:
        raise smoke.SmokeError("Consumer did not retire its authority")
    if case == "settlement":
        if len(root["tombstones"]) != 1 or not root["tombstones"][0]["released"]:
            raise smoke.SmokeError("Settlement lacks acknowledged release")
    elif root["issued_launch_serial"] != 1 or root["tombstones"]:
        raise smoke.SmokeError("Pre-F2 cancellation changed serial or receipts")
    return dict(ledger=root, survivor=others[0])


def disk_authority(world: Path, logical_ids: list[str]) -> dict:
    selected = {uuid.UUID(value) for value in logical_ids}
    survivors = {}
    for region in sorted(world.rglob("r.*.mca")):
        if region.parent.name != "entities":
            continue
        for slot in allocated_slots(region):
            for entity in record(region, slot).get("Entities", []):
                data = entity.get("RocketEntityData")
                if not data:
                    continue
                logical = nbt_uuid(data["assembly_transaction_id"])
                if logical not in selected:
                    continue
                if str(logical) in survivors:
                    raise smoke.SmokeError("Duplicated logical rocket on disk")
                flight = data["flight_data"]
                if flight["state"] != "LANDED" or flight["current_dimension"] != MOON or flight["passengers"]["assignments"]:
                    raise smoke.SmokeError("Ordinary flight did not reach empty Moon landing")
                survivors[str(logical)] = dict(entity=entity["UUID"], data=data, position=entity["Pos"])
    if len(survivors) != 8:
        raise smoke.SmokeError("Missing ordinary survivors on disk")
    with gzip.open(world / "data" / JOURNAL, "rb") as stream:
        raw = stream.read(146_866_177)
    if len(raw) > 146_866_176:
        raise smoke.SmokeError("Journal exceeds existing bound")
    journal = NbtReader(raw).read_root()["data"]
    entries = [e for e in journal["transfers"] if nbt_uuid(e["logical_rocket_id"]) in selected]
    if len(entries) != 8 or any(e["phase"] != "COMMITTED" for e in entries):
        raise smoke.SmokeError("Wrong durable ordinary journal authority")
    return dict(survivors=survivors, entries=entries, journal_sha256=digest(world / "data" / JOURNAL))


def nbt_uuid(values: list[int]) -> uuid.UUID:
    return uuid.UUID(hex="".join(f"{value & 0xffffffff:08x}" for value in values))


def assemble(process, index: int) -> dict:
    x, y, z = 384 + 32 * index, 100, 384
    process.command(f"forceload add {x - 2} {z - 1} {x + 2} {z + 1}")
    process.command(f"fill {x - 2} {y} {z - 1} {x + 2} {y + 4} {z + 1} minecraft:air")
    for dx, dy, block in ((0, 0, "rocket_assembler"), (-1, 1, "rocket_fuel_tank"),
                          (0, 1, "rocket_motor"), (0, 2, "rocket_seat"), (0, 3, "guidance_computer")):
        process.command(f"setblock {x + dx} {y + dy} {z} advancedrocketrycommunity:{block}")
    process.command(f"setblock {x + 1} {y + 1} {z} minecraft:chest")
    process.command(f'data merge block {x + 1} {y + 1} {z} {{Items:[{{Slot:0b,id:"minecraft:diamond",Count:17b}}]}}')
    start = len(process.lines)
    process.command(f"arce rocket assemble {x} {y} {z}")
    match = ASSEMBLY_LOG.search(process.lines[process.wait_for(ASSEMBLY_LOG, 45, start_at=start)])
    if match.group(1) != "5":
        raise smoke.SmokeError("Unexpected fixture block count")
    entity = match.group(3)
    process.wait_for(re.compile("ARCE_ROCKET_ENTITY_ACTIVE entity=" + entity), 30, start_at=start)
    fueled = FlightHarness.refuel(process, EARTH, entity)
    return dict(entity=entity, logical=fueled["logical"], fuel=fueled["amount"])


def launch_wave(process, flights: list[dict], staggered: bool, start: int) -> None:
    for flight in flights:
        process.command(f"arce rocket release-test launch {flight['entity']} moon")
        if staggered:
            # A slow first launch can exceed the wall delay. Wait for its actual
            # admission receipt before delaying, so requests do not pile into one tick.
            pattern = re.compile(LAUNCH_LOG.pattern.replace("logical=([0-9a-f-]{36})", "logical=(" + flight["logical"] + ")"))
            process.wait_for(pattern, 45, start_at=start)
            time.sleep(.5)


@contextmanager
def heavy_lock(timeout=10_800):
    deadline = time.monotonic() + timeout
    while True:
        try:
            LOCK.mkdir(); break
        except FileExistsError:
            if time.time() - LOCK.stat().st_mtime > 10_800:
                raise smoke.SmokeError("Heavy-JVM lock is older than three hours; do not break it")
            if time.monotonic() >= deadline:
                raise smoke.SmokeError("Heavy-JVM lock wait expired")
            time.sleep(30)
    token = f"W91N native PID={os.getpid()} {uuid.uuid4()} {utc()}"
    owner = LOCK / "owner.txt"; owner.write_text(token, encoding="utf-8")
    try:
        yield
    finally:
        if smoke.is_link_or_junction(LOCK) or owner.read_text(encoding="utf-8") != token:
            raise smoke.SmokeError("Lock ownership changed; cleanup not attempted")
        owner.unlink(); LOCK.rmdir()


def check_space():
    for drive in (Path("C:/"), Path("D:/")):
        if shutil.disk_usage(drive).free <= 10 * 1024**3:
            raise smoke.SmokeError("Native start requires more than 10 GiB on C: and D:")


def run_case(args, case: str, evidence: Path) -> dict:
    session = TEMP / ("W91N-" + uuid.uuid4().hex)
    if session.exists() or smoke.is_link_or_junction(session) or session.resolve().parent != TEMP.resolve():
        raise smoke.SmokeError("Unsafe disposable server path")
    libraries = args.source_server.resolve() / "libraries"
    if not libraries.is_dir() or smoke.is_link_or_junction(libraries) or any(smoke.is_link_or_junction(p) for p in libraries.rglob("*")):
        raise smoke.SmokeError("Unsafe Forge library tree")
    evidence.mkdir(); session.mkdir()
    marker = session / ".w91n-owned"; token = uuid.uuid4().hex; marker.write_text(token, encoding="ascii")
    row = dict(case=case, status="IN_PROGRESS", starts=[], source_commit=args.source_commit, jar_sha256=digest(args.jar))
    process = None
    try:
        shutil.copytree(libraries, session / "libraries")
        (session / "mods").mkdir(); shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = smoke.allocate_port(); smoke.write_server_configuration(session, port, True)
        properties = smoke.expected_server_properties(port, True)
        properties.update({"level-type": "minecraft:flat", "max-tick-time": "60000", "generator-settings": json.dumps({
            "layers": [{"block": "minecraft:bedrock", "height": 1}], "biome": "minecraft:plains"})})
        (session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(properties.items())), encoding="ascii")
        command = _server_command(args.java); command[2] = "-Xmx4G"
        command.insert(1, "-Dadvancedrocketrycommunity.rocketJournalFailure=" + ("HOLD" if case == "held" else "OBSERVE"))
        for restart in range(2):
            assert_pin(args.source_commit)
            check_space()
            start_command = list(command)
            if case in CONSUMERS and restart == 0:
                start_command[1:1] = ["-Dadvancedrocketrycommunity.escrowConsumerTiming=true",
                                      "-Dadvancedrocketrycommunity.escrowScenario=start",
                                      "-Dadvancedrocketrycommunity.escrowCase=" + CONSUMERS[case]]
            row["starts"].append(dict(restart=restart, at=utc(), other_java=java_processes(), command=start_command))
            process = smoke.CapturedProcess(start_command, session, evidence / f"start-{restart}.log")
            process.wait_for(smoke.READY_MARKER, args.startup_timeout)
            if restart == 0:
                flights = [assemble(process, i) for i in range(8)]; row["flights"] = flights
                row["timing_start_other_java"] = [p for p in java_processes() if p["ProcessId"] != process.process.pid]
                process.command("arce rocket release-test journal-probe-start")
                process.wait_for(re.compile("ARCE_JOURNAL_PROBE_STARTED"), 30)
                start = len(process.lines)
                launch_wave(process, flights, case != "simultaneous", start)
                for flight in flights:
                    pattern = re.compile(LAUNCH_LOG.pattern.replace("logical=([0-9a-f-]{36})", "logical=(" + flight["logical"] + ")"))
                    match = pattern.search(process.lines[process.wait_for(pattern, 45, start_at=start)])
                    if match.group(7) not in ("JOURNAL_PENDING", "SUCCESS"):
                        raise smoke.SmokeError("Ordinary launch refused: " + match.group(7))
                    flight["required_fuel"] = int(match.group(8))
                if case == "held":
                    process.command("arce rocket release-test journal-hold " + flights[0]["logical"])
                    process.wait_for(re.compile("Journal hold selected"), 30)
                    deadline = time.monotonic() + 30
                    while True:
                        status_start = len(process.lines)
                        process.command("arce rocket release-test journal-hold-status")
                        status = process.wait_for(re.compile("Journal hold waiting=(true|false)"), 5, start_at=status_start)
                        if "waiting=true" in process.lines[status]:
                            break
                        if time.monotonic() >= deadline:
                            raise smoke.SmokeError("Selected write was not held within 30 seconds")
                        time.sleep(.1)
                    time.sleep(3)
                    process.command("arce rocket release-test journal-hold-release")
                for flight in flights:
                    landing = re.compile(r"ARCE_TRANSFER_PHASE transfer=([0-9a-f-]{36}) logical=" + flight["logical"]
                                         + r" phase=COMMITTED event=landed_reservation_retained entity=([0-9a-f-]{36})")
                    match = landing.search(process.lines[process.wait_for(landing, 120, start_at=start)])
                    flight["destination_entity"] = match.group(2)
                if case in CONSUMERS:
                    done = re.compile("ARCE_CONSUMER_FIXTURE_DONE" if case != "settlement"
                                      else "ARCE_RELEASE_SETTLEMENT_COMPLETION_RESULT.*state=RELEASED")
                    deadline = time.monotonic() + 120
                    while not any(done.search(line) for line in process.lines[start:]) and time.monotonic() < deadline:
                        if process.process.poll() is not None:
                            raise smoke.SmokeError("Consumer server exited before observation ended")
                        time.sleep(.1)
                    row["consumer_fixture_completed"] = any(done.search(line) for line in process.lines[start:])
                process.command("arce rocket release-test journal-probe-dump")
                process.wait_for(re.compile("ARCE_JOURNAL_PROBE_WRITTEN"), 30)
                shutil.copy2(session / PROBE, evidence / PROBE)
                row["metrics"] = analyze(json.loads((evidence / PROBE).read_text(encoding="utf-8")), [f["logical"] for f in flights])
                if row["metrics"]["hard_criterion"] != 0:
                    raise smoke.SmokeError("Nonzero hard criterion; retain evidence and stop without changing predicates")
            else:
                for flight in flights:
                    entity = flight["destination_entity"]
                    process.wait_for(re.compile("ARCE_ROCKET_ENTITY_ACTIVE entity=" + entity), 45)
                    report = FlightHarness.report(process, MOON, entity)
                    if report["logical"] != flight["logical"] or report["state"] != "LANDED" or report["passengers"] != 0:
                        raise smoke.SmokeError("Restart changed ordinary survivor")
            save = len(process.lines); process.command("save-all flush")
            process.wait_for(smoke.SAVE_MARKER, 60, start_at=save)
            process.command("stop"); code = process.finish()
            if code != 0 or any("/ERROR]" in line or "/FATAL]" in line for line in process.lines):
                raise smoke.SmokeError("Native server failed or logged ERROR/FATAL")
            process = None
            if case in CONSUMERS and restart == 0:
                shutil.copy2(session / CONSUMER_PROBE, evidence / CONSUMER_PROBE)
                row["consumer"] = analyze_consumer(json.loads((evidence / CONSUMER_PROBE).read_text(encoding="utf-8")),
                        json.loads((evidence / PROBE).read_text(encoding="utf-8")), case, [f["logical"] for f in flights])
            disk = disk_authority(session / "world", [f["logical"] for f in flights])
            if case in CONSUMERS and row.get("consumer_fixture_completed"):
                disk["consumer"] = consumer_disk(session / "world", [f["logical"] for f in flights], case)
            for flight in flights:
                fuel = disk["survivors"][flight["logical"]]["data"]["flight_data"]["fuel"]["amount"]
                if fuel != flight["fuel"] - flight["required_fuel"]:
                    raise smoke.SmokeError("Ordinary flight did not conserve its quoted fuel")
            (evidence / f"disk-{restart}.json").write_text(json.dumps(disk, indent=2), encoding="utf-8")
            if restart == 0:
                baseline = disk
            elif disk["survivors"] != baseline["survivors"] or disk["entries"] != baseline["entries"]:
                raise smoke.SmokeError("Restart changed durable flight authority")
            elif disk.get("consumer") != baseline.get("consumer"):
                raise smoke.SmokeError("Restart changed consumer authority or survivor")
        row["status"] = "PASS" if row["metrics"]["main_thread_budget_pass"] and not row["metrics"]["three_write_bound_findings"] else "FINDING"
        if case in CONSUMERS and (not row["consumer"]["completed"] or not row["consumer_fixture_completed"]):
            row["status"] = "FINDING"
        return row
    except Exception as failure:
        row.update(status="FAILED", error=str(failure)); raise
    finally:
        if process is not None:
            process.abort()
        (evidence / "results.json").write_text(json.dumps(row, indent=2), encoding="utf-8")
        if session.resolve().parent == TEMP.resolve() and not smoke.is_link_or_junction(session) and marker.read_text(encoding="ascii") == token:
            shutil.rmtree(session)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_server", type=Path)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--case", action="append", choices=CASES)
    parser.add_argument("--startup-timeout", type=float, default=240)
    args = parser.parse_args()
    try:
        if args.evidence_dir.exists():
            raise smoke.SmokeError("Evidence directory must be new")
        args.evidence_dir.mkdir(parents=True)
        for case in args.case or CASES:
            with heavy_lock():
                check_space(); result = run_case(args, case, args.evidence_dir / case)
                print(case + ": " + result["status"], flush=True)
        return 0
    except (OSError, smoke.SmokeError) as failure:
        print("FAIL: " + str(failure), flush=True); return 1


if __name__ == "__main__":
    raise SystemExit(main())
