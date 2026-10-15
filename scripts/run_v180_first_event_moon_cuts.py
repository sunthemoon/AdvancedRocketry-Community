#!/usr/bin/env python3
"""W44 native headless (H) Moon rows: a real Moon flight elects the first landing under a gated cut, then two restarts.

Each case gets its own flat world in one disposable server session. The initial start runs with
arce.releaseTest.firstEventCut=<ID> and the release-test hooks, assembles and fuels the packaged rocket, and launches
it to the Moon with nobody aboard; the participant seam injects the fixed sorted list [P1, P2] after the empty-server
gate. The process must halt with the release-test status at the row's named boundary. The two restarts run without
the cut, except FM8a, whose receipt refusal remains through both restarts and is removed only for a recovery start and
one more idempotent start. Every completed start checks the raw roots: the same event E in the journal outbox, the
receipt R(E) with the identical event and fingerprint, DELIVERED, no second election and no unexpected ERROR line.
It also waits until the Moon rocket that recovery loaded or rebuilt reports LANDED, and the last two completed starts
must load the same rocket.
Server copies are deleted afterwards.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
from pathlib import Path
import re
import shutil
import time
import uuid

if __package__:
    from . import run_dedicated_server_smoke as smoke
    from .inspect_celestial_saved_data import NbtReader
    from .run_v060_flight_server_smoke import FlightHarness, EARTH, MOON, LAUNCH_LOG, _server_command
else:
    import run_dedicated_server_smoke as smoke
    from inspect_celestial_saved_data import NbtReader
    from run_v060_flight_server_smoke import FlightHarness, EARTH, MOON, LAUNCH_LOG, _server_command

JOURNAL = "advancedrocketrycommunity_rocket_transfers.dat"
PROGRESSION = "advancedrocketrycommunity_classic_progression.dat"
OUTBOX = "classic_first_moon_landing"
MOON_RECEIPT = "first_moon_landing"
WARP_RECEIPT = "first_station_warp"
P1 = uuid.UUID("00000000-0000-4000-8000-0000000000a1")
P2 = uuid.UUID("00000000-0000-4000-8000-0000000000a2")
# Case: (boundary, cut kept on restarts, source state and durable receipt at the halt, ERROR markers it may log).
CASES = {
    "FM1": ("before_election_move", False, "EMPTY", False, ()),
    "FM2a": ("after_election_force", False, "ELECTED", False, ()),
    "FM2b": ("after_election_force", False, "ELECTED", False, ()),
    "FM6a": ("journal_unknown", False, "ELECTED", False, ()),
    "FM6b": ("journal_unknown", False, "EMPTY", False, ()),
    "FM7": ("fixture_stop", True, "EMPTY", False, ("ARCE_TRANSFER_JOURNAL_UNACKNOWLEDGED",)),
    "FM3": ("receipt_confirmed", False, "ELECTED", True, ()),
    "FM4a": ("awards_dispatched", False, "ELECTED", True, ()),
    "FM4b": ("awards_dispatched", False, "ELECTED", True, ()),
    "FM5": ("delivered_published", False, "DELIVERED", True, ()),
    "FM8a": ("receipt_barrier_failed", True, "ELECTED", False, ("ARCE_PROGRESSION_RECEIPT_BARRIER_FAILED",)),
    # The receipt move succeeded before the directory force failed, so R(E) is on disk at the halt.
    "FM8b": ("receipt_barrier_failed", False, "ELECTED", True, ("ARCE_PROGRESSION_RECEIPT_BARRIER_FAILED",)),
    "FM12a": ("fixture_stop", False, "ELECTED", False, ("ARCE_PROGRESSION_RECEIPT_BARRIER_FAILED",)),
    "FM12b": ("fixture_stop", False, "ELECTED", False, ("ARCE_PROGRESSION_RECEIPT_BARRIER_FAILED",)),
}
HALT = re.compile(r"ARCE_RELEASE_TEST first_event_cut id=(\S+) boundary=(\S+) event=(\S+) thread=(.+)$")
STATUS = re.compile(r"kind=first_moon_landing source=(\S+) event=(\S+) receipt=(\S+) hold=(\S+)")
ELECTED = "ARCE_FIRST_EVENT_ELECTED kind=first_moon_landing"
ACTIVE = re.compile(r"ARCE_ROCKET_ENTITY_ACTIVE entity=([0-9a-f-]{36}) operational=true .*flight_state=([A-Z_]+)")


def uuid_ints(value: uuid.UUID) -> list[int]:
    raw = value.int.to_bytes(16, "big")
    return [int.from_bytes(raw[index:index + 4], "big", signed=True) for index in range(0, 16, 4)]


def ints_uuid(values: list[int]) -> str:
    return str(uuid.UUID(bytes=b"".join(value.to_bytes(4, "big", signed=True) for value in values)))


def read_root(path: Path) -> dict:
    with gzip.open(path, "rb") as stream:
        raw = stream.read(8 * 1024 * 1024 + 1)
    if len(raw) > 8 * 1024 * 1024:
        raise smoke.SmokeError(f"Oversized root {path.name}")
    return NbtReader(raw).read_root()["data"]


def inspect(world: Path, case: str, event_id: str, source: str, durable_receipt: bool) -> dict:
    journal_path, progression_path = world / "data" / JOURNAL, world / "data" / PROGRESSION
    journal = read_root(journal_path)
    outbox = journal.get(OUTBOX, {})
    event = outbox.get("event")
    if source == "EMPTY":
        if outbox != {"schema_version": 1, "state": "EMPTY"} or event_id is not None:
            raise smoke.SmokeError(f"{case}: an uncommitted candidate became authority")
        if progression_path.exists() and read_root(progression_path)[MOON_RECEIPT] != {"occurred": 0}:
            raise smoke.SmokeError(f"{case}: a receipt exists without source authority")
        transfers = journal.get("transfers", [])
        if len(transfers) != 1 or transfers[0]["phase"] != "COMMITTED":
            raise smoke.SmokeError(f"{case}: the saved DESCENT fixture lost its committed record")
        return dict(outbox="EMPTY", receipt="absent", transfers=1,
                    journal_sha256=smoke.digest_file(journal_path))
    if event is None or ints_uuid(event["event_id"]) != event_id:
        raise smoke.SmokeError(f"{case}: the journal outbox does not hold E {event_id}: {outbox.get('state')}")
    if event["participants"] != uuid_ints(P1) + uuid_ints(P2):
        raise smoke.SmokeError(f"{case}: E does not hold exactly [P1, P2]")
    fingerprint = hashlib.sha256(event["kind"].encode("utf-8") + b"\0" + uuid.UUID(event_id).bytes
                                 + (2).to_bytes(4, "big") + P1.bytes + P2.bytes).hexdigest()
    if event["fingerprint"] != fingerprint:
        raise smoke.SmokeError(f"{case}: E's participant fingerprint is invalid")
    if outbox["state"] != source:
        raise smoke.SmokeError(f"{case}: the source is {outbox['state']}, not {source}")
    transfers = [entry for entry in journal.get("transfers", []) if entry.get("transfer_id") == event["transfer_id"]]
    if case in ("FM12a", "FM12b"):
        all_transfers = journal.get("transfers", [])
        if transfers:
            raise smoke.SmokeError(f"{case}: the winning reservation was not superseded or released")
        if case == "FM12a" and all_transfers:
            raise smoke.SmokeError("FM12a: transfers is not empty")
        if case == "FM12b" and (len(all_transfers) != 1
                                or all_transfers[0]["destination_snapshot"]["source_dimension"] != EARTH):
            raise smoke.SmokeError("FM12b: the single superseding record is not a return to Earth")
    elif len(transfers) != 1 or transfers[0]["phase"] != "COMMITTED":
        raise smoke.SmokeError(f"{case}: the winning landed reservation is not the one COMMITTED record")
    state = dict(outbox=outbox["state"], event=event, journal_sha256=smoke.digest_file(journal_path),
                 transfers=len(journal.get("transfers", [])))
    if not progression_path.exists():
        if durable_receipt:
            raise smoke.SmokeError(f"{case}: the progression root is missing")
        state.update(receipt="no file")
        return state
    progression = read_root(progression_path)
    receipt = progression.get(MOON_RECEIPT, {})
    if progression.get(WARP_RECEIPT) != {"occurred": 0}:
        raise smoke.SmokeError(f"{case}: the station receipt changed")
    if durable_receipt and (receipt.get("occurred") != 1 or receipt.get("event") != event):
        raise smoke.SmokeError(f"{case}: the receipt is not R(E) with the identical event and fingerprint")
    if not durable_receipt and receipt.get("occurred") != 0:
        raise smoke.SmokeError(f"{case}: a refused receipt became durable")
    state.update(receipt="R(E)" if durable_receipt else "absent",
                 progression_sha256=smoke.digest_file(progression_path))
    return state


def check_errors(case: str, lines: list[str]) -> list[str]:
    allowed = CASES[case][4]
    errors = [line.rstrip() for line in lines if "/ERROR]" in line or "/FATAL]" in line]
    unexpected = [line for line in errors if not any(marker in line for marker in allowed)]
    if unexpected:
        raise smoke.SmokeError(f"{case}: unexpected ERROR line: {unexpected[0]}")
    return errors


def command(java: str, cut: str | None) -> list[str]:
    value = _server_command(java)
    value[2] = "-Xmx4G"
    if cut is not None:
        value.insert(1, "-Darce.releaseTest.firstEventCut=" + cut)
    else:
        value.insert(1, "-Darce.releaseTest.firstEventParticipants=true")
    return value


def halted(case: str, process: smoke.CapturedProcess, timeout: float) -> dict:
    code = process.finish(timeout=timeout)
    # The halt is logged and also printed raw before Runtime.halt, so the same marker appears twice.
    marks = {tuple(part.strip() for part in mark.groups()) for mark in map(HALT.search, process.lines) if mark}
    if code != 75 or len(marks) != 1:
        raise smoke.SmokeError(f"{case}: expected one release-test halt with status 75, got {code} and {marks}")
    cut, boundary, event, thread = next(iter(marks))
    expected_thread = "ARCE-rocket-journal" if case in ("FM1", "FM2a", "FM2b") else "Server thread"
    if cut != case or boundary != CASES[case][0] or thread.strip() != expected_thread:
        raise smoke.SmokeError(f"{case}: halted at {cut} {boundary} on {thread}")
    return dict(exit_code=code, boundary=boundary, event=event, thread=thread.strip())


def delivered(case: str, process: smoke.CapturedProcess, event_id: str, timeout: float) -> str:
    deadline = time.monotonic() + timeout
    while True:
        start = len(process.lines)
        process.command("arce progression status")
        index = process.wait_for(STATUS, 30, start_at=start)
        source, event, receipt, hold = STATUS.search(process.lines[index]).groups()
        if source == "KNOWN:DELIVERED" and receipt == "CONFIRMED" and hold == "none":
            if event_id is not None and event != event_id:
                raise smoke.SmokeError(f"{case}: DELIVERED names {event}, not E {event_id}")
            return process.lines[index].rstrip()
        if hold not in ("none", "SOURCE_UNCONFIRMED"):
            raise smoke.SmokeError(f"{case}: the Moon kind is held: {process.lines[index].rstrip()}")
        if time.monotonic() > deadline:
            raise smoke.SmokeError(f"{case}: no DELIVERED within {timeout} s: {process.lines[index].rstrip()}")
        time.sleep(2)


def landed(case: str, process: smoke.CapturedProcess, timeout: float, dimension: str = MOON, world: Path | None = None) -> dict:
    """The Moon rocket that recovery loaded (or rebuilt) reaches LANDED: a lagging entity is projected (FM4b)."""
    deadline = time.monotonic() + timeout
    while True:
        loaded = [match for match in map(ACTIVE.search, process.lines) if match]
        entity = loaded[-1].group(1) if loaded else None
        if dimension == EARTH:
            records = read_root(world / "data" / JOURNAL)["transfers"]
            entity = records[0].get("destination_entity_id") if records and records[0]["phase"] == "COMMITTED" else None
            if entity is not None: entity = ints_uuid(entity)
        if entity:
            report = FlightHarness.report(process, dimension, entity)
            if report["state"] == "LANDED":
                return dict(entity=entity, loaded_state=loaded[-1].group(2) if loaded else "unknown", state=report["state"],
                            landing_logged=any("event=landing_complete" in line for line in process.lines))
        if time.monotonic() > deadline:
            raise smoke.SmokeError(f"{case}: the Moon rocket did not reach LANDED within {timeout} s")
        time.sleep(2)


def snapshot(world: Path, evidence: Path, case: str, start: int) -> dict:
    """Preserve the actual roots at each boundary, not only their interpreted contents."""
    destination = evidence / f"{case}-start-{start}-roots"
    destination.mkdir()
    files = {}
    for path in sorted((world / "data").glob("advancedrocketrycommunity_*.dat*")):
        if path.is_file():
            shutil.copy2(path, destination / path.name)
            files[path.name] = smoke.digest_file(destination / path.name)
    # H uses no players: advancement files must stay absent, never treated as actual awards.
    advancements = world / "advancements"
    if advancements.exists() and any(advancements.iterdir()):
        raise smoke.SmokeError(f"{case}: a headless fixture unexpectedly has player advancement files")
    return dict(files=files, award_oracle="N", connected_players=0, advancement_files=[])


def pending(case: str, process: smoke.CapturedProcess, timeout: float) -> dict:
    """Observe a complete failed-election retry interval without saving or stopping a blocked writer."""
    refusal = re.compile(r"first_event_cut election_move_refused")
    index = process.wait_for(refusal, timeout)
    process.wait_for(refusal, timeout, start_at=index + 1)
    begin = len(process.lines)
    process.command("arce progression status")
    index = process.wait_for(STATUS, 30, start_at=begin)
    source, event, receipt, hold = STATUS.search(process.lines[index]).groups()
    if receipt == "CONFIRMED" or source in ("KNOWN:ELECTED", "KNOWN:DELIVERED"):
        raise smoke.SmokeError(f"{case}: an uncommitted election produced durable delivery")
    loaded = [match for match in map(ACTIVE.search, process.lines) if match]
    if not loaded:
        raise smoke.SmokeError(f"{case}: no loaded destination to inspect")
    rocket = FlightHarness.report(process, MOON, loaded[-1].group(1))
    if rocket["state"] != "DESCENT" or any("event=landing_complete" in line for line in process.lines):
        raise smoke.SmokeError(f"{case}: the refused election published LANDED")
    return dict(status=process.lines[index].rstrip(), rocket=rocket, retries_observed=2)


def run(args) -> dict:
    session, evidence = args.session_dir.resolve(), args.evidence_dir.resolve()
    intended = Path("D:/GitHub/ARCE-Task-Evidence/Temp").resolve()
    if session.parent != intended or session.exists() or smoke.is_link_or_junction(session):
        raise smoke.SmokeError("Unsafe or occupied disposable native directory")
    if evidence.exists() or evidence == session or session in evidence.parents:
        raise smoke.SmokeError("Evidence must be new and outside the disposable directory")
    for drive in (Path("C:/"), intended):
        if shutil.disk_usage(drive).free < 10 * 1024**3:
            raise smoke.SmokeError("Native cuts require 10 GiB on both drives")
    libraries = args.source_server.resolve() / "libraries"
    if not (libraries / "net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt").is_file():
        raise smoke.SmokeError("Missing pinned Forge arguments")
    session.mkdir()
    evidence.mkdir(parents=True)
    token = uuid.uuid4().hex
    marker = session / ".arce-w44i-moon-cuts-owned"
    marker.write_text(token, encoding="ascii")
    report = dict(status="IN_PROGRESS", source_commit=args.source_commit, jar_sha256=smoke.digest_file(args.jar),
                  scope="W44 Moon H rows: real Moon flight, gated cut, two restarts; no player-award claim", cases=[])
    process = None
    try:
        shutil.copytree(libraries, session / "libraries")
        (session / "mods").mkdir()
        shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = smoke.allocate_port()
        smoke.write_server_configuration(session, port, True)
        harness = FlightHarness(java=args.java, server=session, port=port,
                                expected_version="1.20.1-1.8.0-dev", startup_timeout=args.startup_timeout)
        for case in args.case or list(CASES):
            _, kept, halt_source, halt_receipt, _ = CASES[case]
            world = session / ("world-" + case)
            properties = smoke.expected_server_properties(port, True)
            properties.update({"level-name": world.name, "level-type": "minecraft:flat", "max-tick-time": "60000",
                               "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}],
                                                                 "biome": "minecraft:plains"})})
            (session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(properties.items())),
                                                       encoding="ascii")
            row = dict(case=case, starts=[])
            report["in_progress"] = row
            # The initial start, the two restarts and, for FM8a, the recovery start and one more idempotent start.
            plan = [case, case if kept else None, case if kept else None] + ([None] if case == "FM7" else
                                                                         [None, None] if kept else [])
            event_id = None
            for start, cut in enumerate(plan):
                process = smoke.CapturedProcess(command(args.java, cut), session, evidence / f"{case}-start-{start}.log")
                entry = dict(start=start, role="initial" if start == 0 else f"restart-{start}",
                             cut=cut, log=f"{case}-start-{start}.log")
                process.wait_for(smoke.READY_MARKER, args.startup_timeout)
                if start == 0:
                    entity = harness.assemble(process)["entity"]
                    harness.refuel(process, EARTH, entity)
                    begin = len(process.lines)
                    process.command(f"arce rocket release-test launch {entity} moon")
                    index = process.wait_for(LAUNCH_LOG, 45, start_at=begin)
                    entry["launch"] = LAUNCH_LOG.search(process.lines[index]).group(7)
                    if entry["launch"] not in ("SUCCESS", "JOURNAL_PENDING"):
                        raise smoke.SmokeError(f"{case}: the Moon launch was refused: {entry['launch']}")
                if case in ("FM12a", "FM12b") and start == 0:
                    process.wait_for(re.compile("first_event_cut receipt_move_refused"), args.flight_timeout)
                    entry["landed_before_action"] = landed(case, process, args.delivery_timeout)
                    entity = entry["landed_before_action"]["entity"]
                    begin = len(process.lines)
                    process.command("save-all flush")
                    process.wait_for(smoke.SAVE_MARKER, 60, start_at=begin)
                    elected = read_root(world / "data" / JOURNAL)[OUTBOX]
                    if elected["state"] != "ELECTED":
                        raise smoke.SmokeError(f"{case}: the action did not start with confirmed ELECTED(E)")
                    event_id = ints_uuid(elected["event"]["event_id"])
                    if case == "FM12a":
                        process.command(f"arce progression release-test release-reservation {entity}")
                        process.wait_for(re.compile("event=landed_reservation_released"), 45)
                    else:
                        harness.refuel(process, MOON, entity)
                        begin = len(process.lines)
                        process.command(f"execute in {MOON} run arce rocket release-test launch {entity} earth")
                        index = process.wait_for(LAUNCH_LOG, 45, start_at=begin)
                        entry["return_launch"] = LAUNCH_LOG.search(process.lines[index]).group(7)
                        if entry["return_launch"] not in ("SUCCESS", "JOURNAL_PENDING"):
                            raise smoke.SmokeError(f"FM12b: return launch was refused: {entry['return_launch']}")
                    deadline = time.monotonic() + 45
                    while True:
                        begin = len(process.lines)
                        process.command("arce progression status")
                        index = process.wait_for(STATUS, 15, start_at=begin)
                        state = STATUS.search(process.lines[index]).groups()
                        records = read_root(world / "data" / JOURNAL)["transfers"]
                        changed = not records if case == "FM12a" else len(records) == 1 and records[0]["transfer_id"] != elected["event"]["transfer_id"]
                        if state[0] == "KNOWN:ELECTED" and changed:
                            entry["after_action_status"] = process.lines[index].rstrip()
                            break
                        if time.monotonic() > deadline:
                            raise smoke.SmokeError(f"{case}: the action generation was not confirmed")
                        time.sleep(1)
                    process.command("arce progression release-test halt")
                    entry.update(halted(case, process, 60))
                    entry["disk"] = inspect(world, case, event_id, "ELECTED", False)
                elif case == "FM7" and cut is not None:
                    entry["pending"] = pending(case, process, args.flight_timeout)
                    entry["disk"] = inspect(world, case, None, "EMPTY", False)
                    entry["roots"] = snapshot(world, evidence, case, start)
                    if start < 2:
                        process.command("arce progression release-test halt")
                        entry.update(halted(case, process, args.flight_timeout))
                    else:
                        process.command("arce progression release-test release")
                        entry["status"] = delivered(case, process, None, args.delivery_timeout)
                        event_id = STATUS.search(entry["status"]).group(2)
                        entry["rocket"] = landed(case, process, args.delivery_timeout)
                        save_start = len(process.lines)
                        process.command("save-all flush")
                        process.wait_for(smoke.SAVE_MARKER, 60, start_at=save_start)
                        process.command("stop")
                        entry["exit_code"] = process.finish()
                        if entry["exit_code"] != 0:
                            raise smoke.SmokeError(f"{case}: online recovery did not stop cleanly")
                        entry["recovered_disk"] = inspect(world, case, event_id, "DELIVERED", True)
                        entry["online_recovery"] = True
                elif cut is not None:
                    entry.update(halted(case, process, args.flight_timeout))
                    if start == 0:
                        event_id = entry["event"]
                        if case in ("FM1", "FM2a", "FM2b"):
                            if event_id != "null":
                                raise smoke.SmokeError(f"{case}: the writer-thread seam unexpectedly supplied an event UUID")
                            if halt_source != "EMPTY":
                                event_id = ints_uuid(read_root(world / "data" / JOURNAL)[OUTBOX]["event"]["event_id"])
                                entry.update(event=event_id, event_read_from="committed journal", marker_event="null")
                        elections = [line for line in process.lines if "ARCE_FIRST_EVENT_ELECTED" in line]
                        if len(elections) != 1 or ELECTED not in elections[0]:
                            raise smoke.SmokeError(f"{case}: the flight did not elect exactly E")
                        if halt_source == "EMPTY":
                            entry["uncommitted_candidate"] = re.search(r"event=([0-9a-f-]{36})", elections[0]).group(1)
                            event_id = None
                        elif event_id not in elections[0]:
                            raise smoke.SmokeError(f"{case}: the election does not match disk authority")
                    elif entry["event"] != event_id:
                        raise smoke.SmokeError(f"{case}: the restart halted on another event")
                    entry["disk"] = inspect(world, case, event_id, halt_source, halt_receipt)
                    if case in ("FM1", "FM2a", "FM2b", "FM6a", "FM6b") and any("event=landing_complete" in line for line in process.lines):
                        raise smoke.SmokeError(f"{case}: LANDED was published before the server confirmation")
                else:
                    entry["status"] = delivered(case, process, event_id, args.delivery_timeout)
                    if event_id is None:
                        event_id = STATUS.search(entry["status"]).group(2)
                    entry["rocket"] = landed(case, process, args.delivery_timeout, EARTH if case == "FM12b" else MOON, world)
                    save_start = len(process.lines)
                    process.command("save-all flush")
                    process.wait_for(smoke.SAVE_MARKER, 60, start_at=save_start)
                    process.command("stop")
                    entry["exit_code"] = process.finish()
                    if entry["exit_code"] != 0:
                        raise smoke.SmokeError(f"{case}: start {start} exited {entry['exit_code']}")
                    entry["disk"] = inspect(world, case, event_id, "DELIVERED", True)
                if start > 0 and not (case in ("FM1", "FM6b") and start == 1 or case == "FM7" and start <= 2) and any(ELECTED in line for line in process.lines):
                    raise smoke.SmokeError(f"{case}: start {start} elected a second Moon landing")
                if case in ("FM1", "FM2a", "FM6b", "FM7") and start == 0 and not any(
                        "first_event_fixture descent_saved" in line for line in process.lines):
                    raise smoke.SmokeError(f"{case}: DESCENT was not saved before the election")
                if "roots" not in entry:
                    entry["roots"] = snapshot(world, evidence, case, start)
                entry["landing_complete_logged"] = any("event=landing_complete" in line for line in process.lines)
                entry["errors"] = check_errors(case, process.lines)
                process = None
                row["starts"].append(entry)
            done = [entry for entry in row["starts"] if entry["cut"] is None or entry.get("online_recovery")]
            if any(entry.get("recovered_disk", entry["disk"])["event"] !=
                   done[0].get("recovered_disk", done[0]["disk"])["event"] for entry in done):
                raise smoke.SmokeError(f"{case}: the restarts disagree on E")
            if done[-1]["rocket"]["entity"] != done[-2]["rocket"]["entity"]:
                raise smoke.SmokeError(f"{case}: the second recovered start loaded another Moon rocket")
            if case == "FM2a" and done[0]["rocket"]["entity"] != ints_uuid(row["starts"][0]["disk"]["event"]["terminal_rocket_id"]):
                raise smoke.SmokeError("FM2a: the saved DESCENT destination was missing and had to be rebuilt")
            row["event"] = event_id
            row["status"] = "PASS"
            report["cases"].append(row)
            print(f"{case}: PASS (halt at {CASES[case][0]}, {len(plan) - 1} restarts)", flush=True)
        report.pop("in_progress", None)
        report["status"] = "PASS"
        return report
    except Exception as failure:
        report.update(status="FAILED", error=str(failure))
        if process is not None:
            process.abort()
            process = None
        if "entry" in locals():
            report["failed_start"] = entry
            try:
                if "roots" not in entry:
                    entry["roots"] = snapshot(world, evidence, case, start)
            except Exception as snapshot_failure:
                report["snapshot_error"] = str(snapshot_failure)
        raise
    finally:
        if process is not None:
            process.abort()
        (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        if (session.parent == intended and not smoke.is_link_or_junction(session) and marker.is_file()
                and marker.read_text(encoding="ascii") == token):
            shutil.rmtree(session)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_server", type=Path)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--session-dir", type=Path, required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--case", action="append", choices=list(CASES))
    parser.add_argument("--startup-timeout", type=float, default=240)
    parser.add_argument("--flight-timeout", type=float, default=240)
    parser.add_argument("--delivery-timeout", type=float, default=120)
    args = parser.parse_args()
    try:
        run(args)
        return 0
    except (OSError, smoke.SmokeError) as failure:
        print(f"FAIL: {failure}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
