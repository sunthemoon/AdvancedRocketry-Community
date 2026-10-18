#!/usr/bin/env python3
"""Hard-cut ordinary per-record scheduling and governed returns, then restart twice.

Unavailable ordinary N2 fixtures are explicit BLOCKED rows, never passing cuts.
Uses HOLD, FM6b, FP6, RT and the passive N2_REMOVAL_MOVED release-test pause.
"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
import gzip
import json
import os
from pathlib import Path
import re
import shutil
import time
import uuid

import run_dedicated_server_smoke as smoke
from inspect_celestial_saved_data import NbtReader
from run_v060_flight_server_smoke import FlightHarness, EARTH, MOON, LAUNCH_LOG, _server_command
from run_v180_rocket_journal_failures import allocated_slots, JOURNAL
from run_v180_transfer_concurrency import assemble, nbt_uuid, check_space, LOCK, TEMP
from run_v180_escrow_forced_stop import digest, record
from run_v180_escrow_pre_f2 import assert_pin
from run_v180_governed_benchmark import utc
import run_v180_escrow_returns as governed

CASES = ("F3_HELD", "F3_UNKNOWN", "N2_REFUSED", "N2_MOVED", "N2_UNKNOWN", "N2_GOVERNED")
BLOCKED = {
    "N2_REFUSED": "No native refusal fixture; production has 128 slots, detached tests use two. Needs scope decision.",
}
GOVERNED = "RT_CANCEL_SEPARATE_0_READBACK_AFTER"
RETURN = re.compile(r"ARCE_TRANSFER_RETURNED_TO_SOURCE transfer=(\S+) logical=(\S+) reason=(\S+) fuel=(\d+)")


@contextmanager
def heavy_lock(timeout=10_800):
    deadline = time.monotonic() + timeout
    while True:
        try:
            LOCK.mkdir()
            break
        except FileExistsError:
            if time.time() - LOCK.stat().st_mtime > 10_800:
                raise smoke.SmokeError("Heavy-JVM lock is older than three hours; do not break it")
            if time.monotonic() >= deadline:
                raise smoke.SmokeError("Heavy-JVM lock wait expired")
            time.sleep(30)
    token = f"W91K PID={os.getpid()} {uuid.uuid4()} {utc()}"
    owner = LOCK / "owner.txt"
    owner.write_text(token, encoding="utf-8")
    try:
        yield token
    finally:
        if smoke.is_link_or_junction(LOCK) or owner.read_text(encoding="utf-8") != token:
            raise smoke.SmokeError("Lock ownership changed; cleanup not attempted")
        owner.unlink()
        LOCK.rmdir()


def read_disk(world: Path) -> dict:
    """Read every entity slot, not just the intended survivor or its expected region."""
    entities, files = [], {}
    for region in sorted(world.rglob("r.*.mca")):
        files[region.relative_to(world).as_posix()] = digest(region)
        if region.parent.name == "entities":
            for slot in allocated_slots(region):
                for entity in record(region, slot).get("Entities", []):
                    if "RocketEntityData" in entity:
                        entities.append(dict(region=region.relative_to(world).as_posix(), slot=slot, entity=entity))
    path = world / "data" / JOURNAL
    journal = read_journal(path)
    if journal.get("schema_version") != 3:
        raise smoke.SmokeError("Wrong journal schema")
    files["data/" + JOURNAL] = digest(path)
    result = dict(entities=entities, journal=journal, files=files)
    pending = path.with_name(path.name + ".arce-pending")
    if pending.exists():
        result["pending"] = read_journal(pending)
        files["data/" + pending.name] = digest(pending)
    return result


def read_journal(path):
    with gzip.open(path, "rb") as stream:
        payload = stream.read(146_866_177)
    if len(payload) > 146_866_176:
        raise smoke.SmokeError("Journal exceeds its existing bound")
    return NbtReader(payload).read_root()["data"]


def selected(disk: dict, flights: list[dict]) -> tuple[dict, dict]:
    ids = {flight["logical"] for flight in flights}
    entities, transfers = {}, {}
    for row in disk["entities"]:
        entity = row["entity"]
        logical = str(nbt_uuid(entity["RocketEntityData"]["assembly_transaction_id"]))
        if logical not in ids or logical in entities:
            raise smoke.SmokeError("Foreign or duplicated physical rocket")
        entities[logical] = row
    if set(entities) != ids:
        raise smoke.SmokeError("Missing physical rocket")
    for entry in disk["journal"]["transfers"]:
        logical = str(nbt_uuid(entry["logical_rocket_id"]))
        if logical not in ids or logical in transfers:
            raise smoke.SmokeError("Foreign or duplicated transfer record")
        transfers[logical] = entry
    return entities, transfers


def conserved(entity: dict) -> dict:
    data = entity["RocketEntityData"]
    flight = data["flight_data"]
    return dict(uuid=entity["UUID"], schema=data["schema_version"], logical=data["assembly_transaction_id"],
                owner=data["owner_id"], snapshot=data["snapshot"], fuel=flight["fuel"],
                escrow=data["resource_escrow"], passengers=flight["passengers"],
                mounted=entity.get("Passengers", []))


def verify_ordinary(disk: dict, flights: list[dict], case: str, restart: int, baseline: dict | None) -> dict:
    entities, transfers = selected(disk, flights)
    phase = "PREPARED" if case == "F3_HELD" else "COMMITTED"
    if restart == 0 or case == "F3_UNKNOWN":
        if len(transfers) != 2 or any(t["phase"] != phase for t in transfers.values()):
            raise smoke.SmokeError(f"Cut/restart journal does not retain both last acknowledged {phase} records")
    elif transfers:
        raise smoke.SmokeError("PREPARED source recovery did not retire its records")
    if case == "F3_HELD" and restart == 0:
        staged = {str(nbt_uuid(t["logical_rocket_id"])): t for t in disk.get("pending", {}).get("transfers", [])}
        a, b = (flight["logical"] for flight in flights)
        if (set(staged) != {a, b} or staged[a] != transfers[a]
                or staged[b]["phase"] != "DESTINATION_SPAWNED"):
            raise smoke.SmokeError("Held staged successor does not contain B's advancement and unchanged A")
    authority = {}
    for flight in flights:
        logical = flight["logical"]
        entity = entities[logical]["entity"]
        data = entity["RocketEntityData"]; state = data["flight_data"]
        expected_fuel = flight["fuel"] - (flight["required_fuel"] if case == "F3_UNKNOWN" else 0)
        if state["fuel"]["amount"] != expected_fuel or state["passengers"]["assignments"] or entity.get("Passengers", []):
            raise smoke.SmokeError("Cut changed fuel or passenger authority")
        if case == "F3_HELD":
            if str(nbt_uuid(entity["UUID"])) != flight["entity"] or state["current_dimension"] != EARTH:
                raise smoke.SmokeError("Held successor changed the source identity or dimension")
            if restart and (state["state"] != "FUELED" or entity["Pos"] != flight["source_position"]):
                raise smoke.SmokeError("Held successor did not recover its stationary source")
        else:
            if state["current_dimension"] != MOON or state["state"] != ("LANDED" if restart else "DESCENT"):
                raise smoke.SmokeError("UNKNOWN cut published or lost terminal destination state")
            if entity["UUID"] != transfers[logical]["destination_entity_id"]:
                raise smoke.SmokeError("UNKNOWN cut lost its fixed destination identity")
            if restart and entity["Pos"] != flight["destination_position"]:
                raise smoke.SmokeError("Recovered destination moved away from its landing origin")
        authority[logical] = conserved(entity)
        if baseline is not None and authority[logical] != baseline[logical]:
            raise smoke.SmokeError("Restart changed conserved identity, structure, fuel, escrow or manifest")
    if case == "F3_UNKNOWN":
        expected = "EMPTY" if restart == 0 else "DELIVERED"
        if disk["journal"]["classic_first_moon_landing"]["state"] != expected:
            raise smoke.SmokeError("UNKNOWN election incorrectly published or failed to recover its outbox")
    return authority


def launch(process, flight):
    start = len(process.lines)
    process.command(f"arce rocket release-test launch {flight['entity']} moon")
    pattern = re.compile(LAUNCH_LOG.pattern.replace("logical=([0-9a-f-]{36})", "logical=(" + flight["logical"] + ")"))
    match = pattern.search(process.lines[process.wait_for(pattern, 45, start_at=start)])
    if match.group(7) not in ("SUCCESS", "JOURNAL_PENDING"):
        raise smoke.SmokeError("Launch refused: " + match.group(7))
    flight.update(required_fuel=int(match.group(8)), transfer=match.group(1))


def save(process):
    start = len(process.lines)
    process.command("save-all flush")
    process.wait_for(smoke.SAVE_MARKER, 60, start_at=start)


def cut_ordinary(process, world, case, row):
    flights = [assemble(process, i) for i in range(2)]
    row["flights"] = flights
    for i, flight in enumerate(flights):
        flight["source_position"] = [384.5 + 32 * i, 101.0, 384.5]
    # B departs first; its advancement is held while A is still PREPARED.
    launch(process, flights[1]); time.sleep(.5); launch(process, flights[0])
    if case == "F3_HELD":
        for flight in flights:
            deadline = time.monotonic() + 30
            while FlightHarness.report(process, EARTH, flight["entity"])["state"] != "COUNTDOWN":
                if time.monotonic() >= deadline:
                    raise smoke.SmokeError("Admission never acknowledged countdown")
                time.sleep(.05)
        save(process)
        row["before_hold"] = read_disk(world)
        process.command("arce rocket release-test journal-hold " + flights[1]["logical"])
        process.wait_for(re.compile("Journal hold selected"), 30)
        deadline = time.monotonic() + 30
        while True:
            start = len(process.lines)
            process.command("arce rocket release-test journal-hold-status")
            at = process.wait_for(re.compile("Journal hold waiting=(true|false)"), 5, start_at=start)
            if "waiting=true" in process.lines[at]:
                row["boundary"] = process.lines[at].strip()
                break
            if time.monotonic() >= deadline:
                raise smoke.SmokeError("Selected record change never reached the hold")
            time.sleep(.05)
        # Never save/stop here: either would change the cut or wait for its writer.
        process.process.kill()
        code = process.finish(timeout=30)
        if code == 0:
            raise smoke.SmokeError("Expected a hard kill, not orderly stop")
    else:
        marker = re.compile(r"first_event_cut id=FM6b boundary=journal_unknown .*thread=Server thread")
        at = process.wait_for(marker, 120)
        row["boundary"] = process.lines[at].strip()
        code = process.finish(timeout=30)
        if code != 75:
            raise smoke.SmokeError("UNKNOWN seam did not hard halt with status 75")
    return code


def wait_recovered(process, flights, case):
    for flight in flights:
        entity = flight["entity"] if case == "F3_HELD" else flight["destination_entity"]
        dimension = EARTH if case == "F3_HELD" else MOON
        state = "FUELED" if case == "F3_HELD" else "LANDED"
        process.wait_for(re.compile("ARCE_ROCKET_ENTITY_ACTIVE entity=" + entity), 45)
        deadline = time.monotonic() + 120
        while FlightHarness.report(process, dimension, entity)["state"] != state:
            if time.monotonic() >= deadline:
                raise smoke.SmokeError("Restart did not reach " + state)
            time.sleep(.1)


def cut_removal(process, world, case, row):
    flights = [assemble(process, i) for i in range(2)]
    row["flights"] = flights
    for i, flight in enumerate(flights):
        flight["source_position"] = [384.5 + 32 * i, 101.0, 384.5]
    launch(process, flights[0])
    # A reaches its terminal outbox at ~12 seconds. B's first post-admission
    # mutation is its blocked-arrival removal at ~14 seconds, after DELIVERED.
    time.sleep(6)
    launch(process, flights[1])
    deadline = time.monotonic() + 30
    while True:
        root = read_journal(world / "data" / JOURNAL)
        target = next((t for t in root["transfers"] if str(nbt_uuid(t["logical_rocket_id"])) == flights[1]["logical"]), None)
        if target is not None:
            break
        if time.monotonic() >= deadline:
            raise smoke.SmokeError("B admission never became durable")
        time.sleep(.05)
    row["return_record"] = target
    x, y, z = target["destination_flight"]["current_origin"]
    process.command(f"execute in {MOON} run forceload add {x} {z}")
    process.command(f"execute in {MOON} run setblock {x} {y} {z} minecraft:bedrock")
    landing = re.compile(r"ARCE_TRANSFER_PHASE transfer=\S+ logical=" + flights[0]["logical"]
                         + r" phase=COMMITTED event=landed_reservation_retained entity=(\S+)")
    at = process.wait_for(landing, 120)
    flights[0]["destination_entity"] = landing.search(process.lines[at]).group(1)
    deadline = time.monotonic() + 30
    while read_journal(world / "data" / JOURNAL)["classic_first_moon_landing"]["state"] != "DELIVERED":
        if time.monotonic() >= deadline:
            raise smoke.SmokeError("A's election was not durably delivered before removal")
        time.sleep(.05)
    save(process)
    row["before_removal"] = read_disk(world)
    _, before = selected(row["before_removal"], flights)
    if before[flights[1]["logical"]]["phase"] != "PREPARED":
        raise smoke.SmokeError("Removal baseline was not PREPARED")
    marker = re.compile("ARCE_N2_REMOVAL_PAUSED boundary=after_atomic_move" if case == "N2_MOVED"
                        else "ARCE_TRANSFER_JOURNAL_UNACKNOWLEDGED retained dirty authority")
    at = process.wait_for(marker, 60)
    row["boundary"] = process.lines[at].strip()
    if case == "N2_UNKNOWN":
        begin = len(process.lines)
        process.command("arce progression release-test journal-proof")
        proof = process.wait_for(re.compile("first_event_fixture journal_proof result=.*outcome=UNKNOWN"), 20, start_at=begin)
        row["unknown_proof"] = process.lines[proof].strip()
    process.process.kill()
    code = process.finish(timeout=30)
    if code == 0:
        raise smoke.SmokeError("Removal cut did not hard kill")
    return code


def verify_removal(disk, row, restart):
    flights = row["flights"]
    entities, transfers = selected(disk, flights)
    before_entities, before_transfers = selected(row["before_removal"], flights)
    a, b = (flight["logical"] for flight in flights)
    if set(transfers) != {a} or transfers[a] != before_transfers[a]:
        raise smoke.SmokeError("Removal changed A's durable record or failed to remove B")
    for logical in (a, b):
        if conserved(entities[logical]["entity"]) != conserved(before_entities[logical]["entity"]):
            raise smoke.SmokeError("Removal lost, duplicated or changed conserved entity authority")
    source = entities[b]["entity"]
    if restart and (source["RocketEntityData"]["flight_data"]["state"] != "FUELED"
                    or source["Pos"] != flights[1]["source_position"]):
        raise smoke.SmokeError("Removed B has not recovered its stationary source at the origin")


def run_case(args, case, evidence):
    assert_pin(args.source_commit)
    session = TEMP / ("W91K-" + uuid.uuid4().hex)
    if session.exists() or smoke.is_link_or_junction(session) or session.resolve().parent != TEMP.resolve():
        raise smoke.SmokeError("Unsafe disposable server path")
    libraries = args.source_server.resolve() / "libraries"
    if not libraries.is_dir() or smoke.is_link_or_junction(libraries) or any(smoke.is_link_or_junction(p) for p in libraries.rglob("*")):
        raise smoke.SmokeError("Unsafe Forge library tree")
    if not (libraries / "net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt").is_file():
        raise smoke.SmokeError("Missing pinned Forge arguments")
    evidence.mkdir(); session.mkdir()
    marker = session / ".w91k-owned"; token = uuid.uuid4().hex; marker.write_text(token, encoding="ascii")
    row = dict(case=case, status="IN_PROGRESS", starts=[], findings=[], source_commit=args.source_commit, jar_sha256=digest(args.jar))
    process = None
    baseline = None
    try:
        shutil.copytree(libraries, session / "libraries")
        (session / "mods").mkdir(); shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = smoke.allocate_port(); smoke.write_server_configuration(session, port, True)
        properties = smoke.expected_server_properties(port, True)
        properties.update({"level-type": "minecraft:flat", "max-tick-time": "60000", "generator-settings": json.dumps({
            "layers": [{"block": "minecraft:bedrock", "height": 1}], "biome": "minecraft:plains"})})
        (session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(properties.items())), encoding="ascii")
        for restart in range(3):
            assert_pin(args.source_commit); check_space()
            if digest(args.jar) != row["jar_sha256"]:
                raise smoke.SmokeError("JAR changed between starts")
            command = _server_command(args.java); command[2] = "-Xmx4G"
            if case == "N2_GOVERNED":
                command[1:1] = ["-Dadvancedrocketrycommunity.escrowCase=" + GOVERNED,
                                "-Dadvancedrocketrycommunity.escrowScenario=" + ("start" if restart == 0 else "recover")]
            elif restart == 0:
                command.insert(1, {"F3_HELD": "-Dadvancedrocketrycommunity.rocketJournalFailure=HOLD",
                                   "F3_UNKNOWN": "-Darce.releaseTest.firstEventCut=FM6b",
                                   "N2_MOVED": "-Dadvancedrocketrycommunity.rocketJournalFailure=N2_REMOVAL_MOVED",
                                   "N2_UNKNOWN": "-Darce.releaseTest.firstEventCut=FP6"}[case])
            entry = dict(restart=restart, command=command, at=utc(),
                         free_bytes={drive: shutil.disk_usage(drive).free for drive in ("C:/", "D:/")})
            row["starts"].append(entry)
            process = smoke.CapturedProcess(command, session, evidence / f"start-{restart}.log")
            world = session / "world"
            if case == "N2_GOVERNED":
                pattern = ("ARCE_RELEASE_RETURN_PAUSED cut=" + GOVERNED if restart == 0 else
                           "ARCE_RELEASE_RETURN_RESULT cut=" + GOVERNED + " outcome=RETURNED armed=true")
                process.wait_for(re.compile(pattern), args.startup_timeout)
                if restart == 0:
                    process.process.kill(); code = process.finish(timeout=30)
                else:
                    process.command("stop"); code = process.finish()
                entry["disk"] = governed.inspect(world, GOVERNED, restart != 0)
                # Scan all regions in addition to the existing exact-native projection oracle.
                raw = read_disk(world)
                if len(raw["entities"]) != 1:
                    raise smoke.SmokeError("Governed return has a foreign or duplicate rocket outside its slots")
                entry["all_region_files"] = raw["files"]
                if restart == 0:
                    baseline = entry["disk"]["invariant"]
                elif entry["disk"]["invariant"] != baseline:
                    raise smoke.SmokeError("Governed return changed durable authority")
            else:
                process.wait_for(smoke.READY_MARKER, args.startup_timeout)
                if restart == 0:
                    code = (cut_removal if case.startswith("N2_") else cut_ordinary)(process, world, case, row)
                else:
                    if case.startswith("N2_"):
                        for i, flight in enumerate(row["flights"]):
                            entity = flight["entity"] if i else flight["destination_entity"]
                            process.wait_for(re.compile("ARCE_ROCKET_ENTITY_ACTIVE entity=" + entity), 45)
                        time.sleep(2)
                    else:
                        wait_recovered(process, row["flights"], case)
                    save(process); process.command("stop"); code = process.finish()
                disk = read_disk(world); entry["disk"] = disk
                if restart == 0 and case == "F3_UNKNOWN":
                    _, transfers = selected(disk, row["flights"])
                    for flight in row["flights"]:
                        transfer = transfers[flight["logical"]]
                        flight["destination_entity"] = str(nbt_uuid(transfer["destination_entity_id"]))
                        origin = transfer["destination_flight"]["current_origin"]
                        flight["destination_position"] = [origin[0] + .5, float(origin[1]), origin[2] + .5]
                if case.startswith("N2_"):
                    try:
                        verify_removal(disk, row, restart)
                    except smoke.SmokeError as finding:
                        row["findings"].append(dict(restart=restart, error=str(finding)))
                else:
                    authority = verify_ordinary(disk, row["flights"], case, restart, baseline)
                    if baseline is None:
                        baseline = authority
            entry.update(exit_code=code, hard_stop=restart == 0,
                         return_reasons=[m.groupdict() if m.groupdict() else list(m.groups())
                                         for line in process.lines if (m := RETURN.search(line))],
                         errors=[line.strip() for line in process.lines if "/ERROR]" in line or "/FATAL]" in line])
            if restart and code != 0:
                raise smoke.SmokeError("Restart did not stop normally")
            expected_unknown = case == "N2_UNKNOWN" and restart == 0
            unexpected = [line for line in entry["errors"] if not (expected_unknown and
                          "ARCE_TRANSFER_JOURNAL_UNACKNOWLEDGED retained dirty authority" in line)]
            if unexpected:
                raise smoke.SmokeError("Unexpected native ERROR/FATAL; inspect retained logs")
            if expected_unknown and len(entry["errors"]) != 1:
                raise smoke.SmokeError("UNKNOWN removal requires exactly its one injected journal failure")
            if case.startswith("N2_") and case != "N2_GOVERNED":
                if restart == 0 and entry["return_reasons"]:
                    row["findings"].append(dict(restart=restart, error="Return reason published before removal acknowledgement"))
                # These real Moon flights have no named landing pad. Do not claim named-pad coverage.
                row["named_pad_claim"] = "NOT_COVERED: ordinary Moon destination has no named pad"
            process = None
            (evidence / f"disk-{restart}.json").write_text(json.dumps(entry["disk"], indent=2), encoding="utf-8")
        row["status"] = "FINDING" if row["findings"] else "PARTIAL" if "named_pad_claim" in row else "PASS"
    except Exception as failure:
        row.update(status="FAILED", error=str(failure))
    finally:
        if process is not None:
            process.abort()
        (evidence / "results.json").write_text(json.dumps(row, indent=2), encoding="utf-8")
        if (session.resolve().parent != TEMP.resolve() or smoke.is_link_or_junction(session)
                or not marker.is_file() or marker.read_text(encoding="ascii") != token):
            raise smoke.SmokeError("Server ownership changed; cleanup not attempted: " + str(session))
        shutil.rmtree(session)
    return row


def main():
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
        assert_pin(args.source_commit)
        if args.evidence_dir.exists():
            raise smoke.SmokeError("Evidence directory must be new")
        args.evidence_dir.mkdir(parents=True)
        rows = []
        for case in args.case or CASES:
            if case in BLOCKED:
                row = dict(case=case, status="BLOCKED", reason=BLOCKED[case], starts=[])
            else:
                with heavy_lock():
                    check_space(); row = run_case(args, case, args.evidence_dir / case)
            rows.append(row)
            (args.evidence_dir / "summary.json").write_text(json.dumps(rows, indent=2), encoding="utf-8")
            print(case + ": " + row["status"], flush=True)
            if case not in BLOCKED:
                # Other workers poll the machine lock every 30 seconds. Leave an
                # actual acquisition window, not just an instantaneous unlock.
                time.sleep(30)
        return 0 if all(row["status"] == "PASS" for row in rows) else 1
    except (OSError, smoke.SmokeError) as failure:
        print("FAIL: " + str(failure), flush=True)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
