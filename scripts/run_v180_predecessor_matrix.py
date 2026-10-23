#!/usr/bin/env python3
"""W150 I7 native matrix: cold saved S, controlled D preimages, actual cuts and two restarts.

The fixture builds a retained ordinary T from a saved, naturally unloaded S. It
does not claim the synthetic same-column historical layout is a supported route.
No save is issued at a cut; restart observations never modify the raw cut copy.
"""
from __future__ import annotations

import argparse
import copy
import json
import os
from pathlib import Path
import re
import shutil
import time
import uuid

import run_dedicated_server_smoke as smoke
import run_v180_loader_cuts as common
from run_v060_flight_server_smoke import FlightHarness, EARTH, MOON, _server_command
from run_v180_transfer_concurrency import assemble, TEMP, nbt_uuid
from run_v180_f3_n2_cuts import read_disk, save, read_journal
from run_v180_escrow_forced_stop import digest, record
from run_v180_rocket_journal_failures import allocated_slots
from run_v180_escrow_pre_f2 import assert_pin, journal
from run_v180_predecessor_smoke import require, retain_raw

PRE = dict(STORE_D="BARRIER_STORE_BEFORE:1", STORE_S="BARRIER_STORE_BEFORE:2",
           FORCE_D="BARRIER_FORCE_BEFORE:1", FORCE_S="BARRIER_FORCE_BEFORE:2", TERMINAL="ORDINARY_REMOVAL_BEFORE")
CASES = {f"{image}_{cut}": (image, cut) for image in ("UNSAVED", "DESCENT") for cut in PRE}
CASES.update({f"{image}_{cut}": (image, cut) for image in ("UNSAVED", "NAMED", "SAME_SLOT")
              for cut in ("MOVE", "MOVED", "UNKNOWN", "ACK")})
CASES.update({mode: (mode, "HOLD") for mode in ("SAVE_OFF", "STORAGE", "SATURATED", "COLD_SPENT", "CREW", "REFUSED", "STOLEN")})
READY = re.compile(r"ARCE_I7_FIXTURE_READY mode=(\w+) transfer=([\w-]+) logical=([\w-]+) source=([\w-]+) destination=([\w-]+) source_loaded=(\w+) dimension=(\S+)")


def identity(entity):
    return str(nbt_uuid(entity["UUID"]))


def observe_stationary(process, dimension, survivor):
    # Use the existing 120-second restart observation window, not an assumed three-second flight duration.
    deadline = time.monotonic() + 120
    while time.monotonic() < deadline:
        report = FlightHarness.report(process, dimension, survivor)
        if report["state"] in ("LANDED", "FUELED", "ASSEMBLED") and report["transfer"] == "none":
            return report
        require(report["state"] == "DESCENT", "Unexpected restart flight progression")
        time.sleep(0.25)
    raise smoke.SmokeError("Recovered descent did not reach its stationary authority")


def inspect(disk, old, expected_fuel, restart, boundary, survivor):
    """Validate the bounded physical census and complete resources, not just state/fuel scalars."""
    logical = str(nbt_uuid(old["logical_rocket_id"]))
    source_id = str(nbt_uuid(old["source_entity_id"]))
    destination_id = str(nbt_uuid(old["destination_entity_id"]))
    entities = [row["entity"] for row in disk["entities"]]
    require(all(str(nbt_uuid(e["RocketEntityData"]["assembly_transaction_id"])) == logical for e in entities), "Foreign logical rocket")
    require(len({identity(e) for e in entities}) == len(entities), "Repeated physical identity")
    records = disk["journal"]["transfers"]
    require(len(records) <= 1, "Multiple transfer authorities")
    post_move = boundary in ("MOVED", "UNKNOWN", "ACK")
    completed = boundary in ("TERMINAL", "MOVE", "MOVED", "UNKNOWN", "ACK", "FORCE_S")
    if not restart:
        require(len(records) == 1 and records[0]["logical_rocket_id"] == old["logical_rocket_id"], "Lost journal authority at cut")
        require(records[0]["phase"] == ("PREPARED" if post_move else "COMMITTED"), "Wrong cut phase")
        require((records[0]["transfer_id"] == old["transfer_id"]) != post_move, "Wrong cut generation")
        if post_move:
            require(records[0]["source_entity_id"] == old["destination_entity_id"]
                    and records[0]["source_snapshot"] == old["destination_snapshot"]
                    and records[0]["source_flight"]["fuel"] == expected_fuel, "Successor captured a stale source")
        if boundary == "MOVE":
            staged = disk.get("pending", {}).get("transfers", [])
            require(len(staged) == 1 and staged[0]["phase"] == "PREPARED"
                    and staged[0]["source_entity_id"] == old["destination_entity_id"]
                    and staged[0]["source_snapshot"] == old["destination_snapshot"]
                    and staged[0]["source_flight"]["fuel"] == expected_fuel, "Pre-move cut lacks the exact staged source")
    else:
        require(len(entities) == 1, "Restart did not leave exactly one bounded authority")
        require(identity(entities[0]) == survivor, "Restart changed physical authority")
        if survivor == source_id or post_move:
            require(not records, "Source recovery did not retire its transfer")
        else:
            require(len(records) == 1 and records[0] == old, "Destination recovery changed retained predecessor")
    if completed:
        require(all(identity(e) != source_id for e in entities), "Completed exclusion still contains old S")
        require(any(identity(e) == destination_id for e in entities), "Completed proof lost D")
    for entity in entities:
        data = entity["RocketEntityData"]; flight = data["flight_data"]
        at_source = identity(entity) == source_id
        require(at_source or identity(entity) == destination_id, "Unexpected physical candidate")
        require(data["owner_id"] == old["owner_id"], "Changed owner")
        require(data["snapshot"] == old["source_snapshot" if at_source else "destination_snapshot"], "Changed complete blocks/items/fluids/energy snapshot")
        snapshot = data["snapshot"]; origin = snapshot["source_origin"]
        require(entity["Pos"] == [origin[0] + 0.5, origin[1], origin[2] + 0.5]
                and flight["current_origin"] == origin
                and flight["current_dimension"] == snapshot["source_dimension"]
                and flight["logical_rocket_id"] == old["logical_rocket_id"], "Changed current location or flight identity")
        require(flight["passengers"]["seat_capacity"] == old["destination_flight"]["passengers"]["seat_capacity"], "Changed crew capacity")
        fuel = old["source_flight"]["fuel"] if at_source else expected_fuel
        require(flight["fuel"] == fuel, "Changed complete fuel banks or debit history")
        # CREW retains its old pending obligation in T, not in the deliberately empty current manifest.
        require(not entity.get("Passengers") and not flight["passengers"]["assignments"], "Changed current crew")
        if restart or completed or not at_source and boundary in ("FORCE_D", "STORE_S"):
            require(flight["state"] in ("LANDED", "FUELED", "ASSEMBLED") and not flight.get("active_transfer_id"), "Authority is not stationary")
    return entities


def world_authority(world, old):
    """The assembly is already complete: no U or restored payload blocks may accompany an entity authority."""
    path = world / "data/advancedrocketrycommunity_rocket_transactions.dat"
    transactions = read_journal(path) if path.exists() else {}
    require(not transactions.get("transactions"), "Unexpected assembly/disassembly authority")
    observed = []
    cache = {}
    for endpoint in ("source_snapshot", "destination_snapshot"):
        snapshot = old[endpoint]; dimension = snapshot["source_dimension"]
        directory = world / "region" if dimension == EARTH else world / "dimensions" / Path(*dimension.split(":")) / "region"
        origin = snapshot["source_origin"]
        for block in snapshot["relative_blocks"]:
            x, y, z = [origin[i] + block["position"][i] for i in range(3)]
            region = directory / f"r.{x >> 9}.{z >> 9}.mca"; slot = ((x >> 4) & 31) + (((z >> 4) & 31) * 32)
            key = (str(region), slot)
            if key not in cache:
                cache[key] = record(region, slot) if region.exists() and slot in allocated_slots(region) else None
            chunk = cache[key]
            if chunk is None:
                require(endpoint != "source_snapshot", "Initial saved source terrain is missing")
                observed.append(dict(end=endpoint, position=[x, y, z], state="UNWRITTEN")); continue
            sections = [section for section in chunk.get("sections", []) if section["Y"] == y >> 4]
            require(len(sections) <= 1, "Duplicate terrain section")
            name = "minecraft:air"
            if sections:
                states = sections[0]["block_states"]; palette = states["palette"]
                require(0 < len(palette) <= 4096, "Terrain palette is outside its bound")
                index = 0
                if len(palette) > 1:
                    bits = max(4, (len(palette) - 1).bit_length()); per_long = 64 // bits
                    pos = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15)
                    index = (states["data"][pos // per_long] >> ((pos % per_long) * bits)) & ((1 << bits) - 1)
                name = palette[index]["Name"]
            require(name in ("minecraft:air", "minecraft:cave_air", "minecraft:void_air"), "Restored payload block duplicates the entity snapshot")
            require(not any([be.get("x"), be.get("y"), be.get("z")] == [x, y, z] for be in chunk.get("block_entities", [])), "Payload block entity survived assembly")
            observed.append(dict(end=endpoint, position=[x, y, z], state=name))
    return dict(transactions=transactions, payload_blocks=observed)


def command_for(args, mode, cut, restart):
    command = _server_command(args.java); command[2] = "-Xmx4G"
    if not restart:
        if cut in PRE:
            command.insert(1, "-Dadvancedrocketrycommunity.ordinaryRetirementCut=" + PRE[cut])
        elif cut == "MOVE":
            command.insert(1, "-Dadvancedrocketrycommunity.landedDisassemblyProbe=LANDED_DISASSEMBLY:K1")
        elif cut in ("MOVED", "UNKNOWN"):
            command.insert(1, "-Dadvancedrocketrycommunity.ordinaryReplacementCut=" + cut)
        elif cut == "ACK":
            command.insert(1, "-Dadvancedrocketrycommunity.ordinaryRetirementCut=I7_ACK_BEFORE_EFFECTS")
    return command


def run_case(args, name, evidence):
    assert_pin(args.source_commit)
    mode, cut = CASES[name]
    session = TEMP.resolve() / ("W150-R89-" + name + "-" + uuid.uuid4().hex)
    session.mkdir(); evidence.mkdir(); token = uuid.uuid4().hex
    marker = session / ".w150-owned"; marker.write_text(token, encoding="ascii")
    row = dict(case=name, mode=mode, cut=cut, source=args.source_commit, jar_sha256=digest(args.jar), starts=[], status="RUNNING")
    process = None
    try:
        libraries = args.source_server.resolve() / "libraries"
        require(libraries.is_dir() and not any(smoke.is_link_or_junction(p) for p in libraries.rglob("*")), "Unsafe template")
        shutil.copytree(libraries, session / "libraries")
        (session / "mods").mkdir(); shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = smoke.allocate_port(); smoke.write_server_configuration(session, port, True)
        properties = smoke.expected_server_properties(port, True)
        properties.update({"level-type": "minecraft:flat", "max-tick-time": "60000", "generator-settings": json.dumps({
            "layers": [{"block": "minecraft:bedrock", "height": 1}], "biome": "minecraft:plains"})})
        (session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(properties.items())), encoding="ascii")
        world = session / "world"
        for restart in range(3):
            assert_pin(args.source_commit); require(digest(args.jar) == row["jar_sha256"], "JAR changed")
            command = command_for(args, mode, cut, restart)
            entry = dict(restart=restart, command=command, disk_space=common.disk_space(), memory=common.physical_memory())
            row["starts"].append(entry)
            process = smoke.CapturedProcess(command, session, evidence / f"start-{restart}.log")
            process.wait_for(smoke.READY_MARKER, args.startup_timeout)
            if not restart:
                first = assemble(process, 0); save(process)
                process.command(f"arce rocket release-test predecessor-fixture {first['entity']} {mode}")
                process.command("forceload remove all")
                index = process.wait_for(READY, 120)
                observed_mode, transfer, logical, source_id, destination_id, loaded, dimension = READY.search(process.lines[index]).groups()
                require(observed_mode == mode and source_id == first["entity"] and logical == first["logical"], "Changed seed identity")
                require(mode == "SAME_SLOT" or loaded == "false", "Old S was not cold at fixture admission")
                old = journal(world)["transfers"][0]
                (evidence / "predecessor.json").write_text(json.dumps(old, indent=2), encoding="utf-8")
                row.update(source_entity=source_id, destination_entity=destination_id, dimension=dimension, logical=logical)
                if mode == "DESCENT":
                    save(process)  # Deliberate initial DESCENT image, before landing and before the request.
                process.command("arce rocket release-test predecessor-land")
                process.wait_for(re.compile("ARCE_I7_FIXTURE_LANDED"), 30, start_at=index)
                expected_fuel = copy.deepcopy(old["destination_flight"]["fuel"])
                if cut not in PRE:
                    FlightHarness.refuel(process, dimension, destination_id)
                    expected_fuel["amount"] = 1000
                    expected_fuel["propulsion"]["primary"]["amount"] = 1000
                    expected_fuel["propulsion"]["revision"] += 1
                if cut == "HOLD":
                    save(process)  # Held requests begin with a durable current D; never a save after the request.
                row["expected_fuel"] = expected_fuel
                start = len(process.lines); process.command("arce rocket release-test predecessor-launch")
                if cut in PRE:
                    label, _, ordinal = PRE[cut].partition(":")
                    boundary = "ARCE_N3_RETIREMENT_PAUSED boundary=" + label + " occurrence=" + (ordinal or "1")
                elif cut == "MOVE": boundary = "ARCE_LANDED_DISASSEMBLY_PAUSED boundary=K1"
                elif cut == "MOVED": boundary = "ARCE_I7_PAUSED boundary=MOVED"
                elif cut == "UNKNOWN": boundary = "ARCE_TRANSFER_JOURNAL_UNACKNOWLEDGED retained dirty authority"
                elif cut == "ACK": boundary = "ARCE_N3_RETIREMENT_PAUSED boundary=I7_ACK_BEFORE_EFFECTS"
                else:
                    reason = {"SAVE_OFF": "SAVE_OFF", "STORAGE": "UNSUPPORTED_ENTITY_STORAGE", "CREW": "CREW_WAITING",
                              "REFUSED": "REQUEST_CHANGED", "STOLEN": "DEMAND_SPENT", "COLD_SPENT": "DEMAND_SPENT", "SATURATED": "FENCED"}[mode]
                    boundary = r"ARCE_TRANSFER_RETIREMENT_(?:WAIT|ABANDONED).*reason=" + reason
                process.wait_for(re.compile(boundary if cut == "HOLD" else re.escape(boundary)), 180, start_at=start)
                process.process.kill(); entry["exit_code"] = process.finish(timeout=30)
            else:
                survivor = row["survivor"]
                if restart == 2 and not journal(world)["transfers"]:
                    # A standalone saved rocket has no transfer ticket after a fresh boot. Observe only its known
                    # origin column; this does not edit an entity or journal, nor save at the original cut.
                    snapshot = old["source_snapshot" if survivor == row["source_entity"] else "destination_snapshot"]
                    x, _, z = snapshot["source_origin"]
                    entry["observation_load"] = f"execute in {snapshot['source_dimension']} run forceload add {x} {z}"
                    process.command(entry["observation_load"])
                process.wait_for(re.compile("ARCE_ROCKET_ENTITY_ACTIVE entity=" + survivor), 120)
                if survivor == row["source_entity"] or cut in ("MOVED", "UNKNOWN", "ACK"):
                    if restart == 1:
                        process.wait_for(re.compile(r"ARCE_TRANSFER_RETIREMENT_PROVED .*site=RECOVERED_SOURCE"), 120)
                time.sleep(3)
                report = observe_stationary(process, EARTH if survivor == row["source_entity"] else row["dimension"], survivor)
                entry["live_report"] = report
                process.command("stop"); entry["exit_code"] = process.finish()
                require(entry["exit_code"] == 0, "Restart did not stop normally")
            entry["errors"] = [line for line in process.lines if "/ERROR]" in line or "/FATAL]" in line]
            expected = "ARCE_TRANSFER_JOURNAL_UNACKNOWLEDGED retained dirty authority; dependent effects are blocked"
            require(all(cut == "UNKNOWN" and restart == 0 and line.rstrip().endswith(expected) for line in entry["errors"]), "Unexpected native error")
            process = None
            retain_raw(world, evidence, restart); disk = read_disk(world)
            disk["world_authority"] = world_authority(world, old)
            (evidence / f"disk-{restart}.json").write_text(json.dumps(disk, indent=2), encoding="utf-8")
            if not restart:
                destinations = [e for e in disk["entities"] if identity(e["entity"]) == row["destination_entity"]]
                row["survivor"] = row["destination_entity"] if destinations else row["source_entity"]
            inspect(disk, old, expected_fuel, restart, cut, row["survivor"])
            entry["status"] = "PASS"
        row["status"] = "PASS"
    except Exception as error:
        row.update(status="FAIL", error=str(error))
    finally:
        if process is not None: process.abort()
        (evidence / "result.json").write_text(json.dumps(row, indent=2), encoding="utf-8")
        require(session.resolve().parent == TEMP.resolve() and not smoke.is_link_or_junction(session)
                and marker.read_text(encoding="ascii") == token, "Changed cleanup ownership")
        shutil.rmtree(session)
    return row


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_server", type=Path)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--case", action="append", choices=CASES)
    parser.add_argument("--startup-timeout", type=float, default=240)
    args = parser.parse_args(); args.evidence_dir.mkdir(parents=True)
    rows = []
    for index, name in enumerate(args.case or CASES):
        if index: time.sleep(90)
        with common.heavy_lock(f"W150 R89 pid={os.getpid()} {uuid.uuid4()} case={name}"):
            common.disk_space(); common.physical_memory()
            rows.append(run_case(args, name, args.evidence_dir / name))
        (args.evidence_dir / "summary.json").write_text(json.dumps(rows, indent=2), encoding="utf-8")
        print(name + ": " + rows[-1]["status"], flush=True)
    return 0 if all(row["status"] == "PASS" for row in rows) else 1


if __name__ == "__main__": raise SystemExit(main())
