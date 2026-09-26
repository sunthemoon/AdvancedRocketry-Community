#!/usr/bin/env python3
"""Run one external-cargo Earth/Moon round trip across four clean server processes.

Requires a fresh disposable Forge libraries-only directory and two explicit JARs.
Uses operator-only release-test fueling/launch, not player fuel-loader gameplay.
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import re
import shutil
import sys
import time
import uuid
import zipfile
import zlib
from datetime import datetime, timezone
from pathlib import Path

if __package__:
    from . import run_v130_adapter_recovery_smoke as recovery
    from . import run_v060_flight_server_smoke as flight
else:
    import run_v130_adapter_recovery_smoke as recovery
    import run_v060_flight_server_smoke as flight

server_smoke = recovery.server_smoke
rocket_smoke = recovery.rocket_smoke
region_nbt = recovery.region_nbt
SmokeError = recovery.SmokeError
write_json = recovery.write_json
uuid_from_nbt = recovery.uuid_from_nbt
EARTH, MOON = flight.EARTH, flight.MOON
HOST, CARGO, ROCKET = recovery.HOST, recovery.CARGO, recovery.ROCKET
ORIGIN = (264, 101, 264)
RELATIVE = {(-1, 0, 0): HOST + ":rocket_fuel_tank", (0, 0, 0): HOST + ":rocket_motor",
            (1, 0, 0): CARGO, (0, 1, 0): HOST + ":rocket_seat", (0, 2, 0): HOST + ":guidance_computer"}
PHASES = ("moon-landing", "earth-landing", "disassembly", "container-restart")
TRANSFER_JOURNAL = "advancedrocketrycommunity_rocket_transfers.dat"
OWNER = "00000000-0000-0000-0000-000000000005"
REQUIRED_FUEL = 372  # Bundled route 50, mass 210, gravity 1000/165; no data packs.
RELOCATED = {"snapshot_id", "source_dimension", "source_origin", "created_at_game_time", "content_hash"}
PROPERTIES_TIMESTAMP = re.compile(
    rb"#(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun) (?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) "
    rb"(?:0[1-9]|[12][0-9]|3[01]) (?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9] "
    rb"(?:[A-Za-z]{1,10}|GMT[+-][0-9]{2}:[0-9]{2}) [0-9]{4}\r?\n"
)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SmokeError(message)


def configuration_identity(relative: str, payload: bytes) -> str:
    if relative == "server.properties":
        lines = payload.splitlines(keepends=True)
        require(len(lines) >= 3 and lines[0] in (b"#Minecraft server properties\n", b"#Minecraft server properties\r\n")
                and PROPERTIES_TIMESTAMP.fullmatch(lines[1]) is not None,
                "server.properties lacks the exact generated header/timestamp")
        # Java rewrites this one timestamp on each boot; every other byte remains binding.
        payload = lines[0] + b"".join(lines[2:])
    return hashlib.sha256(payload).hexdigest()


def site(dimension: str, origin: list | tuple) -> dict:
    require(dimension in (EARTH, MOON) and len(origin) == 3
            and all(type(n) is int for n in origin), "Invalid observed landing site")
    x, y, z = origin
    require(abs(x) <= 4096 and abs(z) <= 4096 and -60 <= y <= 315, "Landing exceeds finite fixture bounds")
    return {"dimension": dimension, "origin": list(origin)}


def positions(location: dict) -> dict:
    x, y, z = location["origin"]
    return {(x + dx, y + dy, z + dz): name for (dx, dy, dz), name in RELATIVE.items()}


def watched_chunks(locations: list[dict]) -> set[tuple]:
    require(1 <= len(locations) <= 3, "Only assembly and two landing sites are permitted")
    chunks = set()
    for location in locations:
        checked = site(location["dimension"], location["origin"])
        x, _, z = checked["origin"]
        # Include a one-block margin around the complete structure and item observations.
        for cx in range((x - 2) // 16, (x + 2) // 16 + 1):
            for cz in range((z - 1) // 16, (z + 1) // 16 + 1):
                chunks.add((checked["dimension"], cx, cz))
    require(len(chunks) <= 12, "Touched-chunk budget exceeded")
    return chunks


def region_path(dimension: str, kind: str, x: int, z: int) -> Path:
    require(dimension in (EARTH, MOON) and kind in ("region", "entities"), "Unsupported region target")
    base = Path("world") if dimension == EARTH else Path("world/dimensions/advancedrocketrycommunity/moon")
    return base / kind / f"r.{x // 32}.{z // 32}.mca"


def decode_entities(compressed: bytes, x: int, z: int) -> dict:
    stream = zlib.decompressobj()
    expanded = stream.decompress(compressed, region_nbt.MAX_EXPANDED_CHUNK + 1)
    require(len(expanded) <= region_nbt.MAX_EXPANDED_CHUNK and stream.eof
            and not stream.unused_data and not stream.unconsumed_tail, "Invalid bounded entity NBT")
    root = recovery.NbtReader(expanded).read_root()
    require(isinstance(root, dict) and root.get("Position") == [x, z]
            and isinstance(root.get("Entities"), list), "Entity chunk coordinates/list differ")
    return root


def read_transfers(path: Path) -> dict:
    recovery.regular(path, 1024**2)
    with gzip.open(path, "rb") as stream:
        payload = stream.read(4 * 1024**2 + 1)
    require(len(payload) <= 4 * 1024**2, "Transfer journal exceeds decoded bound")
    root = recovery.NbtReader(payload).read_root()
    data = root.get("data") if isinstance(root, dict) else None
    require(isinstance(data, dict) and data.get("schema_version") == 2
            and isinstance(data.get("transfers"), list), "Invalid transfer journal root")
    return data


def capture_disk(server: Path, evidence: Path, locations: list[dict]) -> dict:
    chunks = watched_chunks(locations)
    paths = {Path("world/level.dat"), Path("world/data") / recovery.JOURNAL,
             Path("world/data") / TRANSFER_JOURNAL}
    paths.update(region_path(d, kind, x, z) for d, x, z in chunks for kind in ("region", "entities"))
    files, contents = {}, {}
    for relative in sorted(paths):
        source = server / relative
        if not source.exists() and relative.parent.name == "entities":
            files[relative.as_posix()] = {"absent": True}
            continue
        data = recovery.regular(source, region_nbt.MAX_REGION_BYTES)
        target = evidence / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        contents[relative] = data
        files[relative.as_posix()] = {"sha256": hashlib.sha256(data).hexdigest(), "bytes": len(data)}
    result = {"files": files, "chunks": [], "transactions": recovery.read_journal(evidence / "world/data" / recovery.JOURNAL),
              "transfers": read_transfers(evidence / "world/data" / TRANSFER_JOURNAL)}
    for dimension, x, z in sorted(chunks):
        block_region = contents[region_path(dimension, "region", x, z)]
        chunk = region_nbt._decode_chunk(region_nbt._region_chunk(block_region, x, z), x, z)
        raw = contents.get(region_path(dimension, "entities", x, z))
        offset = 4 * ((x & 31) + 32 * (z & 31))
        entities = [] if raw is None or raw[offset:offset + 4] == bytes(4) else decode_entities(
            region_nbt._region_chunk(raw, x, z), x, z)["Entities"]
        selected = {pos for location in locations if location["dimension"] == dimension
                    for pos in positions(location) if (pos[0] // 16, pos[2] // 16) == (x, z)}
        result["chunks"].append({"dimension": dimension, "position": [x, z], "entities": entities,
                                 "block_entities": chunk.get("block_entities", []),
                                 "blocks": [{"position": list(pos), "state": recovery.block_at(chunk, pos)} for pos in sorted(selected)]})
    return result


def validate_snapshot(snapshot: dict, report: dict) -> None:
    require(snapshot.get("schema_version") == 1 and snapshot.get("content_hash") == report["snapshot"]
            and snapshot.get("source_dimension") == report["dimension"]
            and snapshot.get("source_origin") == report["origin"]
            and snapshot.get("bounding_box") == [-1, 0, 0, 1, 2, 0], "Snapshot receipt/location/bounds differ")
    require(str(uuid.UUID(snapshot["snapshot_id"])) == snapshot["snapshot_id"], "Noncanonical snapshot UUID")
    palette, blocks = snapshot["block_palette"], snapshot["relative_blocks"]
    require(len(palette) == len(blocks) == 5, "Snapshot lost or duplicated a block")
    actual, payloads = {}, []
    for block in blocks:
        pos, index = tuple(block["position"]), block["palette"]
        require(pos not in actual and type(index) is int and 0 <= index < 5, "Invalid palette/relative position")
        state = palette[index]
        require(state.get("properties") == {}, "Fixture block state changed")
        actual[pos] = state["id"]
        if "block_entity" in block:
            require(actual[pos] == CARGO, "Inventory attached to a non-cargo block")
            payloads.append(block["block_entity"])
    require(actual == RELATIVE and payloads == [{"adapter": recovery.ADAPTER,
            "data": {"payload_version": 1, "data": recovery.INVENTORY}}], "Exact external envelope/inventory differs")
    require(snapshot.get("passenger_anchors") == [[0, 1, 0]] and snapshot.get("mass_inputs") == {
        "block_count": 5, "mass": 210, "thrust": 1000, "fuel_capacity": 1000,
        "engine_count": 1, "seat_count": 1, "guidance_count": 1, "block_entity_count": 1}, "Snapshot metrics/anchors differ")


def validate_relocation(source: dict, destination: dict) -> None:
    require({k: v for k, v in source.items() if k not in RELOCATED}
            == {k: v for k, v in destination.items() if k not in RELOCATED}, "Relocation mutated immutable structure/payload")
    require(source["snapshot_id"] != destination["snapshot_id"] and source["content_hash"] != destination["content_hash"]
            and source["source_dimension"] != destination["source_dimension"]
            and destination["created_at_game_time"] >= source["created_at_game_time"], "Relocation identity/time did not follow transfer")


def check_fuel(data: dict, amount: int, transfers: list[str]) -> None:
    require(data.get("capacity") == 1000 and data.get("amount") == amount
            and [uuid_from_nbt(entry.get("transaction_id")) for entry in data.get("committed_debits", [])] == transfers,
            "Fuel amount/capacity/exactly-once debit ledger differs")


def validate_transfer(record: dict, source: dict, destination: dict, leg: dict, previous: list[str]) -> None:
    transfer = leg["transfer"]
    require(record.get("schema_version") == 2 and record.get("phase") == "COMMITTED"
            and uuid_from_nbt(record.get("transfer_id")) == transfer
            and uuid_from_nbt(record.get("logical_rocket_id")) == source["logical"]
            and uuid_from_nbt(record.get("owner_id")) == OWNER
            and uuid_from_nbt(record.get("source_entity_id")) == source["entity"]
            and uuid_from_nbt(record.get("destination_entity_id")) == destination["entity"]
            and source["entity"] != destination["entity"]
            and record.get("required_fuel") == REQUIRED_FUEL
            and re.fullmatch(r"[0-9a-f]{64}", record.get("checksum", "")) is not None,
            "COMMITTED transfer authority/receipt differs")
    validate_snapshot(record["source_snapshot"], source)
    validate_snapshot(record["destination_snapshot"], destination)
    validate_relocation(record["source_snapshot"], record["destination_snapshot"])
    for key, report, state, ledger in (("source_flight", source, "TRANSIT", previous),
                                       ("destination_flight", destination, "DESCENT", previous + [transfer])):
        data = record[key]
        require(data.get("state") == state and uuid_from_nbt(data.get("logical_rocket_id")) == source["logical"]
                and uuid_from_nbt(data.get("active_transfer_id")) == transfer
                and data.get("current_dimension") == report["dimension"] and data.get("current_origin") == report["origin"]
                and data.get("current_body") == HOST + (":earth" if report["dimension"] == EARTH else ":moon")
                and data.get("passengers") == {"seat_capacity": 1, "assignments": []}, "Transfer flight receipt differs")
        check_fuel(data["fuel"], report["fuel"], ledger)
    plan = record["source_flight"]["plan"]
    require(plan == record["destination_flight"]["plan"] and uuid_from_nbt(plan.get("request_id")) == transfer
            and plan.get("required_fuel") == REQUIRED_FUEL
            and plan.get("source_dimension") == source["dimension"]
            and plan.get("destination_dimension") == destination["dimension"], "Transfer plans differ")


def validate_disk(state: dict, reports: list[dict], legs: list[dict], restored: bool, previous: dict | None) -> dict | None:
    require(state["transactions"].get("transactions") == [], "Pending assembly/disassembly journal remains")
    locations = [site(report["dimension"], report["origin"]) for report in reports]
    require(len(state["chunks"]) == len(watched_chunks(locations))
            and {(chunk["dimension"], *chunk["position"]) for chunk in state["chunks"]} == watched_chunks(locations),
            "Disk observation omitted or added a touched chunk")
    rockets, cargo = [], []
    final = reports[-1]
    restored_positions = positions(locations[-1]) if restored else {}
    for chunk in state["chunks"]:
        dimension = chunk["dimension"]
        expected_positions = {pos for location in locations if location["dimension"] == dimension
                              for pos in positions(location) if [pos[0] // 16, pos[2] // 16] == chunk["position"]}
        require(len(chunk["blocks"]) == len(expected_positions)
                and {tuple(block["position"]) for block in chunk["blocks"]} == expected_positions,
                "Block observation omitted or duplicated a fixture position")
        for entity in chunk["entities"]:
            require(entity.get("id") != "minecraft:item", "Dropped item observed in touched chunks")
            if entity.get("id") == ROCKET:
                rockets.append((dimension, entity))
        for block in chunk["blocks"]:
            pos = tuple(block["position"])
            expected = restored_positions.get(pos, "minecraft:air") if dimension == final["dimension"] else "minecraft:air"
            require(block["state"].get("Name") == expected, "Source/destination block material authority differs")
        for be in chunk["block_entities"]:
            if be.get("id") == CARGO:
                cargo.append((dimension, be))
            pos = tuple(be.get(axis) for axis in ("x", "y", "z"))
            watched = any(location["dimension"] == dimension and pos in positions(location) for location in locations)
            require(not watched or (restored and dimension == final["dimension"] and restored_positions.get(pos) == CARGO),
                    "Unexpected BlockEntity at a vacated rocket position")
    require(len(rockets) == (0 if restored else 1), "Duplicate/missing persisted rocket authority")
    if restored:
        require(len(cargo) == 1 and cargo[0][0] == EARTH
                and tuple(cargo[0][1].get(axis) for axis in ("x", "y", "z"))
                == (final["origin"][0] + 1, final["origin"][1], final["origin"][2])
                and cargo[0][1].get("FixtureInventory") == {"schema_version": 1, **recovery.INVENTORY},
                "Restored native cargo schema/slots/counts/metadata differ")
        require(state["transfers"].get("transfers") == [], "Landed reservation was not released")
        return cargo[0][1]
    require(not cargo and rockets[0][0] == final["dimension"], "Entity cargo duplicated in world blocks")
    entity = rockets[0][1]
    x, y, z = final["origin"]
    require(uuid_from_nbt(entity.get("UUID")) == final["entity"] and entity.get("Pos") == [x + .5, float(y), z + .5]
            and not entity.get("Passengers", []), "Persisted physical rocket identity/position differs")
    data = entity["RocketEntityData"]
    require(data.get("schema_version") == 2 and uuid_from_nbt(data.get("owner_id")) == OWNER
            and uuid_from_nbt(data.get("assembly_transaction_id")) == final["logical"], "Rocket owner/logical identity differs")
    validate_snapshot(data["snapshot"], final)
    current = data["flight_data"]
    require(current.get("schema_version") == 2 and current.get("state") == "LANDED"
            and "plan" not in current and "active_transfer_id" not in current
            and uuid_from_nbt(current.get("logical_rocket_id")) == final["logical"]
            and current.get("current_dimension") == final["dimension"] and current.get("current_origin") == final["origin"]
            and current.get("current_body") == HOST + (":earth" if final["dimension"] == EARTH else ":moon")
            and current.get("passengers") == {"seat_capacity": 1, "assignments": []}, "Landed flight authority differs")
    check_fuel(current["fuel"], 1000 - REQUIRED_FUEL * len(legs), [leg["transfer"] for leg in legs])
    entries = state["transfers"].get("transfers")
    require(isinstance(entries, list) and len(entries) == 1, "Landed transfer reservation lost/duplicated")
    validate_transfer(entries[0], reports[-2], final, legs[-1], [leg["transfer"] for leg in legs[:-1]])
    require(entries[0]["destination_snapshot"] == data["snapshot"], "Journal/entity snapshot binding differs")
    if previous is not None:
        require(entries[0]["source_snapshot"] == previous["snapshot"], "Return did not use the saved Moon snapshot")
    return data


class FlightRun(recovery.RecoveryRun):
    """Reuse process ownership, input checks and command recording; no recovery fault controls."""

    def __init__(self, *args):
        super().__init__(*args)
        self.reports, self.legs = [], []
        self.native = self.saved_data = self.saved_snbt = self.saved_transfer = None

    def entity_snbt(self, process, report: dict) -> str:
        return self.query(process, flight._in_dimension(report["dimension"],
            f"data get entity {report['entity']} RocketEntityData"), rocket_smoke.ENTITY_DATA_MARKER).group(1).strip()

    def load_sites(self, process) -> None:
        for dimension, x, z in sorted(watched_chunks([site(r["dimension"], r["origin"]) for r in self.reports])):
            process.command(f"execute in {dimension} run forceload add {x * 16} {z * 16}")
            recovery.wait_condition(process, f"execute in {dimension} if loaded {x * 16} 100 {z * 16}", f"CHUNK_{len(self.commands)}", timeout=30)

    def inspect_saved_transfer(self, process) -> None:
        source, destination = self.reports[-2:]
        expected = (f"Transfer {self.legs[-1]['transfer']} logical={destination['logical']} phase=COMMITTED "
                    f"source={source['dimension']} source_matches=0 destination={destination['dimension']} "
                    f"destination_matches=1 fuel={source['fuel']}->{destination['fuel']} passengers=0 "
                    f"checksum={self.saved_transfer['checksum']}")
        self.query(process, f"arce rocket inspect {self.legs[-1]['transfer']}", re.compile(re.escape(expected)))

    def check_live(self, process, restored: bool, marker: str) -> None:
        process.command("scoreboard players set #rockets arce_v130_flight 0")
        # An unbounded @e already visits all loaded dimensions; enumerate once.
        process.command(f"execute as @e[type={ROCKET}] run scoreboard players add #rockets arce_v130_flight 1")
        self.query(process, f"execute if score #rockets arce_v130_flight matches {0 if restored else 1} run say {marker}_COUNT",
                   re.compile(rf"\[Server\] {re.escape(marker)}_COUNT\s*$"))
        final = self.reports[-1]
        for index, report in enumerate(self.reports):
            location = site(report["dimension"], report["origin"])
            condition = f"in {location['dimension']} "
            for pos, block in positions(location).items():
                expected = block if restored and index == len(self.reports) - 1 else "minecraft:air"
                condition += f"if block {' '.join(map(str, pos))} {expected} "
            x, y, z = location["origin"]
            condition += f"unless entity @e[type=minecraft:item,x={x-2},y={y-1},z={z-1},dx=4,dy=4,dz=2] "
            if not restored and index == len(self.reports) - 1:
                condition += f"if entity {final['entity']} "
            else:
                condition += f"unless entity {report['entity']} "
            self.query(process, f"execute {condition}run say {marker}_{index}",
                       re.compile(rf"\[Server\] {re.escape(marker)}_{index}\s*$"))

    def assemble(self, process) -> None:
        for command in ("gamerule doMobSpawning false", "gamerule randomTickSpeed 0",
                        "scoreboard objectives add arce_v130_flight dummy", "forceload add 262 263 266 265"):
            process.command(command)
        recovery.wait_condition(process, "execute if loaded 264 100 264", "ASSEMBLY_CHUNK", timeout=30)
        process.command("fill 262 100 263 266 104 265 minecraft:air")
        process.command(f"setblock 264 100 264 {HOST}:rocket_assembler")
        for pos, block in positions(site(EARTH, ORIGIN)).items():
            process.command(f"setblock {' '.join(map(str, pos))} {block}")
        process.command('data merge block 265 101 264 {FixtureInventory:{schema_version:1,Items:['
                        '{Slot:0b,id:"minecraft:diamond",Count:17b,tag:{display:{Name:\'{"text":"Public adapter cargo"}\'}}},'
                        '{Slot:1b,id:"minecraft:iron_ingot",Count:64b}]}}')
        recovery.wait_condition(process, f"execute if block 263 101 264 {HOST}:rocket_fuel_tank if block 265 101 264 {CARGO}", "CRAFT_READY", timeout=30)
        match = self.query(process, "arce rocket assemble 264 100 264", flight.ASSEMBLY_LOG, 45)
        require(match.group(1) == "5", "Assembly receipt is not the five-block external craft")
        entity = match.group(3)
        rocket_smoke._wait_for_active_entity(process, entity)
        report = flight.FlightHarness.report(process, EARTH, entity)
        require(report["snapshot"] == match.group(2) and report["state"] == "ASSEMBLED"
                and report["fuel"] == 0 and report["capacity"] == 1000 and report["blocks"] == 5
                and report["origin"] == list(ORIGIN) and report["passengers"] == 0, "Initial assembly report differs")
        filled = flight.FlightHarness.refuel(process, EARTH, entity)
        require(filled["logical"] == report["logical"] and filled["amount"] == 1000, "Initial fill differs")
        self.reports.append(flight.FlightHarness.report(process, EARTH, entity))

    def launch(self, process, destination: str) -> None:
        source = self.reports[-1]
        start = len(process.lines)
        match = self.query(process, flight._in_dimension(source["dimension"],
            f"arce rocket release-test launch {source['entity']} {'moon' if destination == MOON else 'earth'}"), flight.LAUNCH_LOG)
        transfer, entity, logical, dimension, target, checkpoint, code, required, before = match.groups()
        require((entity, logical, dimension, target, checkpoint, code) ==
                (source["entity"], source["logical"], source["dimension"], destination, "none", "SUCCESS")
                and int(required) == REQUIRED_FUEL and int(before) == source["fuel"], "Launch quote/authority differs")
        end = process.wait_for(re.compile(rf"ARCE_TRANSFER_PHASE transfer={transfer} .*event=landed_reservation_retained "), 30, start_at=start)
        phases = [m for line in process.lines[start:end + 1] if (m := flight.PHASE_LOG.search(line)) and m.group(1) == transfer]
        require([m.group(4) for m in phases] == flight.EXPECTED_FLIGHT_EVENTS, "Flight phase sequence differs")
        for phase in phases:
            require(phase.group(2) == logical and int(phase.group(6)) == int(before)
                    and int(phase.group(7)) == int(before) - REQUIRED_FUEL and int(phase.group(8)) == REQUIRED_FUEL,
                    "Phase fuel debit/identity differs")
        entity = next(m.group(5) for m in phases if m.group(4) == "landing_complete")
        report = flight.FlightHarness.report(process, destination, entity)
        require(report["state"] == "LANDED" and report["logical"] == logical and report["entity"] != source["entity"]
                and report["fuel"] == 1000 - REQUIRED_FUEL * (len(self.legs) + 1) and report["capacity"] == 1000
                and report["blocks"] == 5 and report["passengers"] == 0 and report["transfer"] == "none",
                "Landed report violates round-trip conservation")
        site(report["dimension"], report["origin"])
        self.reports.append(report)
        self.legs.append({"transfer": transfer, "required": int(required), "fuel_before": int(before), "fuel_after": report["fuel"],
                          "events": [m.group(4) for m in phases]})

    def _run_phase(self, phase: str, directory: Path, document: dict) -> dict:
        if self.world_identity:
            server_smoke.complete_world_identity(self.server, self.world_identity)
        for item in self.artifacts:
            require(server_smoke.digest_file(Path(item["path"])) == item["sha256"], "Input artifact changed")
        command = rocket_smoke._server_command(self.java)
        write_json(directory / "launch.json", {"command": command, "cwd": str(self.server), "mods": self.mods(True)})
        self.commands = []
        process = server_smoke.CapturedProcess(command, self.server, directory / "stdout.txt")
        original_command = process.command

        def recorded(value):
            self.commands.append(value)
            original_command(value)

        process.command = recorded
        restored = phase in PHASES[2:]
        try:
            process.wait_for(server_smoke.READY_MARKER, self.timeout)
            status = server_smoke.wait_for_status(self.port)
            document["status_mods"] = recovery.validate_status(status, True, self.artifacts[0]["version"])
            write_json(directory / "status.json", status)
            recovery.validate_registration(process.lines, phase)
            if phase == PHASES[0]:
                self.assemble(process)
                self.check_live(process, False, "ASSEMBLED")
                self.launch(process, MOON)
            else:
                self.load_sites(process)
                if phase != PHASES[3]:
                    last = self.reports[-1]
                    rocket_smoke._wait_for_active_entity(process, last["entity"])
                    require(flight.FlightHarness.report(process, last["dimension"], last["entity"]) == last,
                            "Landing report changed across restart")
                    require(self.entity_snbt(process, last) == self.saved_snbt, "RocketEntityData changed across landing restart")
                    self.check_live(process, False, "RESTART")
                    self.inspect_saved_transfer(process)
                if phase == PHASES[1]:
                    self.launch(process, EARTH)  # Deliberately no refuel here.
                elif phase == PHASES[2]:
                    self.disassemble(process)
                    document["explicit_fuel_disposal"] = self.reports[-1]["fuel"]
            self.load_sites(process)
            self.check_live(process, restored, "FIRST")
            tick = int(self.query(process, "time query gametime", re.compile(r"The time is (\d+)")).group(1))
            deadline = time.monotonic() + 10
            while True:
                time.sleep(.5)
                now = int(self.query(process, "time query gametime", re.compile(r"The time is (\d+)")).group(1))
                if now - tick >= 20:
                    break
                require(time.monotonic() < deadline, "Bounded stability window did not advance")
            document["observation_ticks"] = now - tick
            self.check_live(process, restored, "SECOND")
            if not restored:
                self.saved_snbt = self.entity_snbt(process, self.reports[-1])
                (directory / "RocketEntityData.snbt").write_text(self.saved_snbt + "\n", encoding="utf-8")
            else:
                x, y, z = self.reports[-1]["origin"]
                self.query(process, f"data get block {x+1} {y} {z} FixtureInventory", flight.BLOCK_DATA_MARKER)
            start = len(process.lines)
            process.command("save-all flush")
            process.wait_for(server_smoke.SAVE_MARKER, 60, start_at=start)
            process.command("stop")
            document["exit_code"] = process.finish()
            require(document["exit_code"] == 0, "Server exit was not clean")
        except BaseException:
            process.abort()
            raise
        finally:
            document["exit_code"] = process.process.poll()
            write_json(directory / "commands.json", self.commands)
            for name in ("debug.log", "latest.log"):
                source = self.server / "logs" / name
                if source.exists():
                    (directory / name).write_bytes(recovery.regular(source, 32 * 1024**2))
        recovery.audit_log(process.lines, phase)
        document["log_counts"] = server_smoke.log_audit_counts(process.lines)
        document["warnings"] = [line.rstrip() for line in process.lines if "WARN" in line]
        document["active_properties"] = server_smoke.verify_active_server_properties(
            recovery.regular(self.server / "server.properties", 65536), self.port)
        configs, raw_hashes = {}, {}
        for relative in ("server.properties", "config/advancedrocketrycommunity-common.toml", "config/fml.toml"):
            data = recovery.regular(self.server / relative, 65536)
            target = directory / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            raw_hashes[relative] = hashlib.sha256(data).hexdigest()
            configs[relative] = configuration_identity(relative, data)
        document["configuration_sha256"] = raw_hashes
        require(self.config_hashes is None or configs == self.config_hashes, "Configuration changed between processes")
        self.config_hashes = configs
        self.mods(True)
        state = capture_disk(self.server, directory, [site(r["dimension"], r["origin"]) for r in self.reports])
        write_json(directory / "disk-state.json", state)
        result = validate_disk(state, self.reports, self.legs, restored, self.saved_data if phase == PHASES[1] else None)
        if restored:
            require(self.native is None or result == self.native, "Native container changed after final restart")
            self.native = result
        else:
            self.saved_data = result
            self.saved_transfer = state["transfers"]["transfers"][0]
        document.update(reports=list(self.reports), legs=list(self.legs), disk_evidence=state["files"])
        return document

    def disassemble(self, process) -> None:
        last = self.reports[-1]
        command = f"arce rocket release-test disassemble {last['entity']}"
        self.query(process, flight._in_dimension(EARTH, command),
                   re.compile("Release-test disassembly failed: FUEL_DISPOSAL_REQUIRED"))
        self.query(process, flight._in_dimension(EARTH, command + f" discard-fuel {last['fuel'] + 1}"),
                   re.compile("Release-test disassembly failed: WORLD_CHANGED"))
        require(self.entity_snbt(process, last) == self.saved_snbt, "Rejected disposal mutated rocket data")
        self.inspect_saved_transfer(process)
        self.check_live(process, False, "DISPOSAL_REJECTED")
        start = len(process.lines)
        match = self.query(process, flight._in_dimension(EARTH, command + f" discard-fuel {last['fuel']}"),
                           flight.DISASSEMBLY_LOG, 45)
        require(match.groups() == (last["entity"], last["logical"], "SUCCESS", "5", "0"), "Disassembly receipt differs")
        process.wait_for(re.compile(rf"ARCE_ROCKET_FUEL_DISCARDED entity={last['entity']} logical={last['logical']} "
                                    rf"amount={last['fuel']} reason=confirmed_disassembly$"), 30, start_at=start)
        process.wait_for(re.compile(rf"ARCE_TRANSFER_PHASE transfer={self.legs[-1]['transfer']} .*event=landed_reservation_released "),
                         30, start_at=start)


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("server_dir", type=Path)
    parser.add_argument("--host-jar", type=Path, required=True)
    parser.add_argument("--fixture-jar", type=Path, required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--expected-version", default="1.20.1-1.3.0-dev")
    parser.add_argument("--startup-timeout", type=float, default=240)
    parser.add_argument("--accept-eula", action="store_true", required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    evidence = summary = None
    try:
        require(0 < args.startup_timeout <= 240, "Startup timeout must be within (0, 240] seconds")
        server, output, artifacts, args_file = recovery.validate_inputs(
            args.server_dir, args.evidence_dir, args.host_jar, args.fixture_jar, args.expected_version)
        java, java_version = server_smoke.resolve_java(args.java)
        output.mkdir(parents=True)
        evidence = output
        port = server_smoke.allocate_port()
        properties = server_smoke.write_server_configuration(server, port, True)
        (server / "mods").mkdir()
        for item in artifacts:
            shutil.copyfile(item["path"], server / "mods" / item["name"])
        run = FlightRun(server, evidence, artifacts, java, port, args.startup_timeout)
        summary = {"schema_version": 1, "scope": "one round trip, four clean processes, one initial fill, no passengers",
                   "artifacts": artifacts, "java": java_version, "server": str(server), "port": port,
                   "forge_args_sha256": server_smoke.digest_file(args_file), "cycles": [], "result": "IN_PROGRESS"}
        write_json(evidence / "summary.json", summary)
        for phase in PHASES:
            summary["cycles"].append(run.run_phase(phase))
            if phase == PHASES[0]:
                run.world_identity = server_smoke.establish_world_identity(server, str(uuid.uuid4()), artifacts[0]["sha256"], properties)
            summary["world"] = server_smoke.complete_world_identity(server, run.world_identity)
            write_json(evidence / "summary.json", summary)
        for item in artifacts:
            require(server_smoke.digest_file(Path(item["path"])) == item["sha256"], "Input artifact changed during execution")
        summary["result"] = "PASS"
        write_json(evidence / "summary.json", summary)
        print(f"[PASS] One external-cargo round trip, four clean processes; evidence: {evidence}")
        return 0
    except (OSError, ValueError, KeyError, TypeError, IndexError, SmokeError, zipfile.BadZipFile) as exc:
        if evidence is not None:
            write_json(evidence / "failure.json", {"error": str(exc), "type": type(exc).__name__})
            if summary is not None:
                summary["result"] = "FAIL"
                write_json(evidence / "summary.json", summary)
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1
    finally:
        if evidence is not None:
            files = sorted(p for p in evidence.rglob("*") if p.is_file() and p.name != "SHA256SUMS")
            (evidence / "SHA256SUMS").write_text("".join(
                f"{server_smoke.digest_file(p)}  {p.relative_to(evidence).as_posix()}\n" for p in files), encoding="ascii", newline="\n")


if __name__ == "__main__":
    raise SystemExit(main())
