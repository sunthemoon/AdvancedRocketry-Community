#!/usr/bin/env python3
"""Upgrade a disposable pre-planet world, fly Earth/Mars/Venus/Earth, and restart.

Three clean packaged processes; operator fueling/launch only, no clients, crash
campaign or sustained load. Inputs and complete pre-upgrade world stay retained.
"""
from __future__ import annotations

import argparse
import json
import os
import platform
import re
import shutil
import sys
import uuid
from pathlib import Path

if __package__:
    from . import run_v140_celestial_schema_smoke as schema
    from . import run_v130_adapter_flight_smoke as native
else:
    import run_v140_celestial_schema_smoke as schema
    import run_v130_adapter_flight_smoke as native

support, server = schema.support, schema.server
flight, region = native.flight, native.region_nbt
require, write_json, SmokeError = schema.require, support.write_json, support.SmokeError
HOST, EARTH = schema.HOST, "minecraft:overworld"
BODIES = ("mars", "venus", "gas_giant")
DIMENSIONS = (EARTH, *(HOST + ":" + name for name in ("moon", "space", "mars", "venus")))
ORIGIN = [264, 101, 264]
PARTS = {(-1, 0, 0): HOST + ":rocket_fuel_tank", (1, 0, 0): HOST + ":rocket_fuel_tank",
         (0, 0, 0): HOST + ":rocket_motor", (0, 1, 0): HOST + ":rocket_seat",
         (0, 2, 0): HOST + ":guidance_computer", (0, 0, 1): "minecraft:chest"}
LAUNCH = re.compile(r"ARCE_RELEASE_TEST_SURFACE_LAUNCH request=([0-9a-f-]{36}) entity=([0-9a-f-]{36}) "
                    r"logical=([0-9a-f-]{36}) source=(\S+) body=(\S+) code=(\S+) required_fuel=(\d+) fuel_before=(\d+)")


def world_directory(dimension: str) -> Path:
    require(dimension in DIMENSIONS, "Unexpected fixture dimension")
    return Path("world") if dimension == EARTH else Path("world/dimensions") / dimension.replace(":", "/")


def check_bindings(before: dict, after: dict) -> None:
    old, new = before["bindings"], after["bindings"]
    require(before["schema_version"] == after["schema_version"] == 1 and len(old) == 3 and len(new) == 6,
            "Binding upgrade count/schema differs")
    expected = old + [{"body_id": HOST + ":" + body, **({"level": HOST + ":" + body} if body != "gas_giant" else {})}
                      for body in BODIES]
    require(sorted(new, key=lambda entry: entry["body_id"]) == sorted(expected, key=lambda entry: entry["body_id"]),
            "Upgrade changed, omitted or aliased a binding")


def structure(snapshot: dict) -> dict:
    return {key: value for key, value in snapshot.items() if key not in native.RELOCATED}


def surface_target(report: dict) -> dict:
    require(report["dimension"] in (EARTH, HOST + ":mars", HOST + ":venus"), "Unexpected flight surface")
    body = HOST + ":earth" if report["dimension"] == EARTH else report["dimension"]
    return {"schema_version": 1, "type": HOST + ":body_surface", "body_id": body}


def validate_flight(data: dict, report: dict, legs: list[dict], state: str, active: str | None = None):
    target = surface_target(report)
    require(data.get("schema_version") == 2 and data.get("state") == state
            and support.uuid_from_nbt(data.get("logical_rocket_id")) == report["logical"]
            and data.get("current_dimension") == report["dimension"] and data.get("current_origin") == report["origin"]
            and data.get("current_body") == target["body_id"] and data.get("current_target") == target
            and data.get("passengers") == {"seat_capacity": 1, "assignments": []}, "Flight authority differs")
    fuel = data.get("fuel", {})
    require(fuel.get("amount") == report["fuel"] and fuel.get("capacity") == 2000
            and [support.uuid_from_nbt(entry.get("transaction_id")) for entry in fuel.get("committed_debits", [])]
            == [leg["transfer"] for leg in legs], "Native fuel debit ledger differs")
    if active is None:
        require("plan" not in data and "active_transfer_id" not in data, "Stationary rocket retained an active plan")
    else:
        require(support.uuid_from_nbt(data.get("active_transfer_id")) == active, "Active transfer differs")


def validate_transfer(journal: dict, reports: list[dict], legs: list[dict], rocket: dict):
    require(journal.get("schema_version") == 2 and len(journal.get("transfers", [])) == (1 if legs else 0),
            "Transfer reservation count/schema differs")
    if not legs:
        return
    entry, source, destination, leg = journal["transfers"][0], reports[-2], reports[-1], legs[-1]
    require(entry.get("schema_version") == 2 and entry.get("phase") == "COMMITTED"
            and support.uuid_from_nbt(entry.get("transfer_id")) == leg["transfer"]
            and support.uuid_from_nbt(entry.get("logical_rocket_id")) == source["logical"]
            and support.uuid_from_nbt(entry.get("owner_id")) == native.OWNER
            and support.uuid_from_nbt(entry.get("source_entity_id")) == source["entity"]
            and support.uuid_from_nbt(entry.get("destination_entity_id")) == destination["entity"]
            and entry.get("required_fuel") == leg["required"]
            and re.fullmatch(r"[0-9a-f]{64}", entry.get("checksum", "")) is not None,
            "Committed transfer identity/fuel differs")
    for key, report in (("source_snapshot", source), ("destination_snapshot", destination)):
        snapshot = entry[key]
        require(snapshot["content_hash"] == report["snapshot"] and snapshot["source_dimension"] == report["dimension"]
                and snapshot["source_origin"] == report["origin"] and structure(snapshot) == structure(rocket["snapshot"]),
                "Transfer snapshot authority differs")
    require(entry["destination_snapshot"] == rocket["snapshot"], "Journal/entity snapshot differs")
    native.validate_relocation(entry["source_snapshot"], entry["destination_snapshot"])
    validate_flight(entry["source_flight"], source, legs[:-1], "TRANSIT", leg["transfer"])
    validate_flight(entry["destination_flight"], destination, legs, "DESCENT", leg["transfer"])
    plan = entry["source_flight"]["plan"]
    require(plan == entry["destination_flight"]["plan"] and plan.get("schema_version") == 3
            and support.uuid_from_nbt(plan.get("request_id")) == leg["transfer"] and plan.get("required_fuel") == leg["required"]
            and plan.get("source_dimension") == source["dimension"] and plan.get("destination_dimension") == destination["dimension"]
            and plan.get("source_body") == surface_target(source)["body_id"] and plan.get("destination_body") == surface_target(destination)["body_id"]
            and plan.get("destination_target") == surface_target(destination) and "destination_station_id" not in plan,
            "Captured transfer plan differs")


def validate_stations(stations: dict, requests: list[dict]):
    observed = [{"station_id": support.uuid_from_nbt(station["station_id"]),
                 "owner_id": support.uuid_from_nbt(station["owner_id"]),
                 "orbit_body": station["orbit_body"], "name": station["name"]} for station in stations["stations"]]
    require(sorted(observed, key=lambda station: station["station_id"])
            == sorted(requests, key=lambda station: station["station_id"]), "Native station creation authority differs")


def capture_rocket(root: Path, output: Path, report: dict, baseline: dict | None, legs: list[dict]) -> dict:
    entities, files, chunks, material_regions, empty_regions = [], [], 0, 0, 0
    for dimension in DIMENSIONS:
        base = root / world_directory(dimension) / "entities"
        if not base.exists():
            continue
        for source in sorted(base.glob("r.*.*.mca")):
            match = re.fullmatch(r"r\.(-?\d+)\.(-?\d+)\.mca", source.name)
            require(match is not None, "Invalid entity region name")
            rx, rz = map(int, match.groups())
            require(abs(rx) <= 128 and abs(rz) <= 128, "Entity region outside fixture bound")
            payload = support.regular(source, region.MAX_REGION_BYTES)
            # Vanilla may create a zero-byte region when no entity chunk is ever written.
            # A nonempty partial header is still corruption, and the global rocket count must be one.
            require(len(payload) == 0 or len(payload) >= 8192, "Truncated entity region: " + str(source))
            # Empty handles are metadata, not decoded storage: three station cells and
            # two planetary footprints can create more of them than material regions.
            material_regions += bool(payload)
            empty_regions += not payload
            require(material_regions <= 16 and empty_regions <= 32, "Entity region file budget exceeded")
            target = output / source.relative_to(root)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(payload)
            files.append({"path": source.relative_to(root).as_posix(), "sha256": server.digest_file(target), "bytes": len(payload)})
            if not payload:
                continue
            for index in range(1024):
                if payload[index * 4:index * 4 + 4] == bytes(4):
                    continue
                chunks += 1
                require(chunks <= 128, "Entity chunk budget exceeded")
                x, z = rx * 32 + index % 32, rz * 32 + index // 32
                for entity in native.decode_entities(region._region_chunk(payload, x, z), x, z)["Entities"]:
                    if entity.get("id") == HOST + ":rocket":
                        entities.append((dimension, entity))
    require(len(entities) == 1 and entities[0][0] == report["dimension"], "Missing/duplicate native rocket")
    entity = entities[0][1]
    x, y, z = report["origin"]
    require(support.uuid_from_nbt(entity["UUID"]) == report["entity"]
            and entity.get("Pos") == [x + .5, float(y), z + .5] and not entity.get("Passengers", []),
            "Native physical identity/passengers differ")
    data, expected = entity["RocketEntityData"], report
    snapshot, state = data["snapshot"], data["flight_data"]
    require(data["schema_version"] == state["schema_version"] == 2
            and support.uuid_from_nbt(data["assembly_transaction_id"]) == expected["logical"]
            and support.uuid_from_nbt(data["owner_id"]) == native.OWNER, "Rocket schema/owner/logical identity differs")
    require(snapshot["source_dimension"] == state["current_dimension"] == expected["dimension"]
            and snapshot["source_origin"] == state["current_origin"] == expected["origin"]
            and snapshot["content_hash"] == expected["snapshot"] and state["state"] == expected["state"],
            "Native rocket location/state/receipt differs")
    validate_flight(state, report, legs, report["state"])
    blocks = snapshot["relative_blocks"]
    actual = {tuple(block["position"]): snapshot["block_palette"][block["palette"]]["id"] for block in blocks}
    require(len(blocks) == len(actual) == 6 and actual == PARTS, "Native structure differs")
    if baseline is not None:
        require(structure(snapshot) == structure(baseline["snapshot"]), "Travel changed structure or cargo")
    payloads = [block["block_entity"] for block in blocks if "block_entity" in block]
    require(len(payloads) == 1 and payloads[0]["data"]["Items"] == [
        {"Slot": 0, "id": "minecraft:diamond", "Count": 17},
        {"Slot": 1, "id": "minecraft:iron_ingot", "Count": 64}], "Native cargo counts or metadata differ")
    write_json(output / "entity-regions.json", {"files": files, "chunks": chunks, "rocket": data})
    return data


class Harness:
    def __init__(self, root: Path, output: Path, java: str, port: int, research_unlocks: bool = False):
        self.root, self.output, self.java, self.port = root, output, java, port
        self.reports, self.legs, self.commands = [], [], []
        self.previous_snbt = self.original = self.stations = self.bindings = None
        self.station_requests, self.configuration = [], None
        self.research_unlocks = research_unlocks
        self.research = []
        self.research_saved = None

    def query(self, process, command: str, marker, timeout: float = 30):
        regex = re.compile(marker) if isinstance(marker, str) else marker
        start = len(process.lines)
        process.command(command)
        index = process.wait_for(regex, timeout, start_at=start)
        return regex.search(process.lines[index])

    def condition(self, process, condition: str, label: str):
        return self.query(process, f"execute {condition} run say ARCE_MAP02_{label}", rf"\[Server\] ARCE_MAP02_{label}\s*$")

    def unlock_planets(self, process):
        before = self.reports[-1]
        self.query(process, f"arce rocket release-test launch-surface {before['entity']} {HOST}:mars",
                   "Release-test surface launch failed: DISCOVERY_REQUIRED")
        require(flight.FlightHarness.report(process, before["dimension"], before["entity"]) == before,
                "Undiscovered launch changed rocket or fuel")
        start = len(process.lines)
        for body in BODIES:
            owner = native.OWNER if body != "gas_giant" else str(uuid.uuid4())
            receipt = self.query(process, f"arce satellite release-test launch {owner} {body}",
                                 r"ARCE_RELEASE_TEST_SATELLITE_LAUNCH satellite=([0-9a-f-]{36}) "
                                 r"mission=([0-9a-f-]{36}) owner=(\S+) target=(\S+) code=SUCCESS deadline=(\d+)")
            satellite, mission, observed_owner, target, deadline = receipt.groups()
            require(observed_owner == owner and target == HOST + ":" + body, "Research launch identity differs")
            self.research.append({"satellite": satellite, "mission": mission, "owner": owner, "body": target})
        process.wait_for(re.compile(r"ARCE_SATELLITE_SCHEDULER completed=\d+ inspected=\d+ remaining=0"), 30, start_at=start)
        for mission in self.research:
            self.claim_research(process, mission, "SUCCESS")
            self.claim_research(process, mission, "ALREADY_CLAIMED")

    def claim_research(self, process, mission: dict, code: str):
        receipt = self.query(process, "arce satellite release-test claim " + mission["mission"],
                             r"ARCE_RELEASE_TEST_SATELLITE_CLAIM mission=(\S+) owner=(\S+) target=(\S+) "
                             r"code=(\S+) status=(\S+) research=(\d+) discovered=(\S+)")
        observed_id, owner, body, observed_code, status, balance, discovered = receipt.groups()
        require((observed_id, owner, body, observed_code, status, discovered)
                == (mission["mission"], mission["owner"], mission["body"], code, "CLAIMED", "true"),
                "Research claim identity/status/discovery differs")

    def capture_research(self, directory: Path, phase: str):
        decoded = {}
        for name in ("celestial", "satellite_missions"):
            source = self.root / "world/data" / (HOST + "_" + name + ".dat")
            shutil.copyfile(source, directory / source.name)
            decoded[name] = schema.read_nbt(directory / source.name)["data"]
        progress, missions = decoded["celestial"], decoded["satellite_missions"]
        require(progress["schema_version"] == missions["schema_version"] == 2, "Research persistence schema changed")
        require(len(progress["bodies"]) == 3
                and {entry["id"] for entry in progress["bodies"]} == {mission["body"] for mission in self.research},
                "Native discoveries differ")
        require(len(missions["missions"]) == len(missions["satellites"]) == 3, "Native mission/satellite count differs")
        by_mission = {support.uuid_from_nbt(entry["mission_id"]): entry for entry in missions["missions"]}
        by_satellite = {support.uuid_from_nbt(entry["satellite_id"]): entry for entry in missions["satellites"]}
        require(set(by_mission) == {entry["mission"] for entry in self.research}
                and set(by_satellite) == {entry["satellite"] for entry in self.research}, "Native research IDs differ")
        expected_accounts = {}
        for mission in self.research:
            expected_accounts[mission["owner"]] = expected_accounts.get(mission["owner"], 0) + 1
            saved, satellite = by_mission[mission["mission"]], by_satellite[mission["satellite"]]
            require(saved["schema_version"] == satellite["schema_version"] == 1
                    and saved["definition_id"] == satellite["definition_id"] == HOST + ":data_satellite"
                    and saved["completes_at"] - saved["started_at"] == 200
                    and saved["status"] == "claimed" and saved["target_body_id"] == mission["body"]
                    and support.uuid_from_nbt(saved["owner_id"]) == mission["owner"]
                    and support.uuid_from_nbt(saved["satellite_id"]) == mission["satellite"]
                    and (saved["research_yield"], saved["discovery_cost"], saved["discovery_required"]) == (120, 100, 1),
                    "Native claimed mission differs")
            require(satellite["status"] == "operational" and "current_mission_id" not in satellite
                    and support.uuid_from_nbt(satellite["owner_id"]) == mission["owner"], "Native satellite owner/current mission differs")
        accounts = {support.uuid_from_nbt(entry["owner_id"]): entry for entry in missions["research_accounts"]}
        require(len(accounts) == len(missions["research_accounts"]) and set(accounts) == set(expected_accounts), "Unexpected research owner")
        for owner, count in expected_accounts.items():
            require(accounts[owner]["schema_version"] == 1
                    and (accounts[owner]["balance"], accounts[owner]["lifetime_earned"], accounts[owner]["lifetime_spent"])
                    == (20 * count, 120 * count, 100 * count), "Research claim/replay changed private totals")
        authority = {"progress": progress, **{key: missions[key] for key in ("missions", "satellites", "research_accounts")}}
        require(phase != "restart" or authority == self.research_saved, "Restart/replay changed research authority")
        self.research_saved = authority
        write_json(directory / "research.json", authority)

    def snbt(self, process) -> str:
        last = self.reports[-1]
        return self.query(process, f"execute in {last['dimension']} run data get entity {last['entity']} RocketEntityData",
                          r"has the following entity data: (.+)$").group(1)

    def create_station(self, process, body: str, name: str):
        owner = str(uuid.uuid4())
        created = self.query(process, f"arce station admin create {owner} {HOST}:{body} {name}",
                             re.escape("Created " + name + " id=") + r"([0-9a-f-]{36})")
        station = str(uuid.UUID(created.group(1)))
        self.station_requests.append({"station_id": station, "owner_id": owner, "orbit_body": HOST + ":" + body, "name": name})
        self.query(process, f"arce station admin inspect {station}", re.escape("orbit=" + HOST + ":" + body) + r" gravity_milli=0 vacuum=true")

    def assemble(self, process):
        for command in ("gamerule doMobSpawning false", "gamerule randomTickSpeed 0",
                        "scoreboard objectives add arce_map02 dummy", "forceload add 262 263 266 266"):
            process.command(command)
        support.wait_condition(process, "execute if loaded 264 100 264", "MAP02_ASSEMBLY", timeout=30)
        process.command("fill 262 100 263 266 104 266 minecraft:air")
        process.command(f"setblock 264 100 264 {HOST}:rocket_assembler")
        for (dx, dy, dz), block in PARTS.items():
            process.command(f"setblock {264+dx} {101+dy} {264+dz} {block}")
        process.command('data merge block 264 101 265 {Items:[{Slot:0b,id:"minecraft:diamond",Count:17b},'
                        '{Slot:1b,id:"minecraft:iron_ingot",Count:64b}]}')
        assembled = self.query(process, "arce rocket assemble 264 100 264", flight.ASSEMBLY_LOG, 45)
        require(assembled.group(1) == "6", "Wrong assembled block count")
        entity = assembled.group(3)
        support.rocket_smoke._wait_for_active_entity(process, entity)
        flight.FlightHarness.refuel(process, EARTH, entity)
        report = flight.FlightHarness.report(process, EARTH, entity)
        require(report["capacity"] == report["fuel"] == 2000 and report["state"] == "FUELED"
                and report["origin"] == ORIGIN and report["snapshot"] == assembled.group(2), "Initial rocket differs")
        self.reports.append(report)

    def live_conservation(self, process):
        process.command("scoreboard players set #rockets arce_map02 0")
        process.command(f"execute as @e[type={HOST}:rocket] run scoreboard players add #rockets arce_map02 1")
        self.condition(process, "if score #rockets arce_map02 matches 1", "ONE_ROCKET")
        for index, report in enumerate(self.reports):
            x, y, z = report["origin"]
            require(abs(x) <= 4096 and abs(z) <= 4096 and -60 <= y <= 250, "Landing outside fixture bounds")
            condition = f"in {report['dimension']} " + " ".join(
                f"if block {x+dx} {y+dy} {z+dz} minecraft:air" for dx, dy, dz in PARTS)
            condition += f" unless entity @e[type=minecraft:item,x={x-2},y={y-1},z={z-1},dx=4,dy=4,dz=3]"
            self.condition(process, condition, f"VACATED_{index}")

    def launch(self, process, body: str):
        before = self.reports[-1]
        start = len(process.lines)
        launch = self.query(process, f"execute in {before['dimension']} run arce rocket release-test launch-surface {before['entity']} {HOST}:{body}", LAUNCH)
        transfer, entity, logical, source, target, code, required, fuel = launch.groups()
        required, fuel = int(required), int(fuel)
        require((entity, logical, source, target, code, fuel) == (before["entity"], before["logical"], before["dimension"],
                HOST + ":" + body, "SUCCESS", before["fuel"]) and 0 < required <= fuel, "Typed launch receipt differs")
        end = process.wait_for(re.compile(rf"ARCE_TRANSFER_PHASE transfer={transfer} .*event=landed_reservation_retained "), 30, start_at=start)
        events = [match for line in process.lines[start:end+1] if (match := flight.PHASE_LOG.search(line)) and match.group(1) == transfer]
        require([match.group(4) for match in events] == flight.EXPECTED_FLIGHT_EVENTS, "Transfer phase sequence differs")
        require(all(match.group(2) == logical and int(match.group(6)) == fuel and int(match.group(7)) == fuel-required
                    and int(match.group(8)) == required for match in events), "Phase fuel/identity differs")
        dimension = EARTH if body == "earth" else HOST + ":" + body
        landed = flight.FlightHarness.report(process, dimension, next(match.group(5) for match in events if match.group(4) == "landing_complete"))
        require(landed["logical"] == logical and landed["entity"] != entity and landed["state"] == "LANDED"
                and landed["fuel"] == fuel-required and landed["blocks"] == 6 and landed["passengers"] == 0
                and landed["capacity"] == 2000 and landed["transfer"] == "none", "Landed conservation differs")
        x, _, z = landed["origin"]
        process.command(f"execute in {dimension} run forceload add {x-2} {z-1} {x+2} {z+2}")
        self.reports.append(landed)
        self.legs.append({"transfer": transfer, "required": required, "fuel_before": fuel, "fuel_after": landed["fuel"]})
        self.live_conservation(process)

    def cycle(self, phase: str, artifact: dict):
        directory = self.output / phase
        directory.mkdir()
        command = support.rocket_smoke._server_command(self.java)
        write_json(directory / "launch.json", {"command": command, "cwd": str(self.root), "artifact": artifact})
        process, self.commands = None, []
        receipt = {"result": "IN_PROGRESS", "phase": phase}
        try:
            process = server.CapturedProcess(command, self.root, directory / "stdout.txt")
            original_command = process.command
            def recorded(value):
                self.commands.append(value)
                original_command(value)
            process.command = recorded
            process.wait_for(server.READY_MARKER, 240)
            status = server.wait_for_status(self.port)
            server.validate_status_identity(status, artifact["version"])
            require(set(server.forge_mod_versions(status)) == {HOST, "minecraft", "forge"}
                    and status.get("players", {}).get("online") == 0, "Unexpected mod set or connected player")
            write_json(directory / "status.json", status)
            self.query(process, "arce celestial validate", rf"Celestial catalog generation 1 is valid with {3 if phase == 'baseline' else 6} bodies")
            if phase == "baseline":
                self.create_station(process, "moon", "Pre-upgrade station")
                self.assemble(process)
            else:
                previous = self.reports[-1]
                support.rocket_smoke._wait_for_active_entity(process, previous["entity"])
                require(flight.FlightHarness.report(process, previous["dimension"], previous["entity"]) == previous, "Saved report changed across process boundary")
                require(self.snbt(process) == self.previous_snbt, "RocketEntityData changed across upgrade/restart")
                schema.validate_unchanged_station(schema.read_nbt(self.root / schema.STATIONS)["data"], self.stations)
            for body in BODIES:
                dimension = HOST + ":" + body
                missing = phase == "baseline" or body == "gas_giant"
                self.query(process, f"execute in {dimension} run time query gametime",
                           re.escape(f"Unknown dimension '{dimension}'") if missing else r"The time is (\d+)")
            if phase == "upgrade":
                if self.research_unlocks:
                    self.unlock_planets(process)
                for body in ("mars", "gas_giant"):
                    self.create_station(process, body, "Planetary " + body)
                before = self.reports[-1]
                self.query(process, f"arce rocket release-test launch-surface {before['entity']} {HOST}:gas_giant",
                           "Release-test surface launch failed: INVALID_DESTINATION")
                require(flight.FlightHarness.report(process, before["dimension"], before["entity"]) == before, "Gas surface denial changed rocket")
                for body in ("mars", "venus", "earth"):
                    self.launch(process, body)
            if phase == "restart" and self.research_unlocks:
                for mission in self.research:
                    self.claim_research(process, mission, "ALREADY_CLAIMED")
            self.live_conservation(process)
            self.previous_snbt = self.snbt(process)
            (directory / "rocket-entity.snbt").write_text(self.previous_snbt, encoding="utf-8")
            self.query(process, "save-all flush", server.SAVE_MARKER, 60)
            process.command("stop")
            require(process.finish() == 0, "Dedicated process did not exit cleanly")
            schema.validate_log(process.lines, [])
            if self.research_unlocks and phase != "baseline":
                self.capture_research(directory, phase)
            bindings = json.loads(schema.capture_bindings(self.root, directory / "bindings.json"))
            if phase == "upgrade":
                check_bindings(self.bindings, bindings)
            elif phase == "restart":
                require(bindings == self.bindings, "Restart changed binding authority")
            self.bindings = bindings
            shutil.copyfile(self.root / schema.STATIONS, directory / "stations.dat")
            stations = schema.read_nbt(directory / "stations.dat")["data"]
            validate_stations(stations, self.station_requests)
            if phase == "upgrade":
                require(len(stations["stations"]) == 3 and all(old in stations["stations"] for old in self.stations["stations"]),
                        "Upgrade changed the old station or omitted the two new orbits")
            elif phase == "restart":
                schema.validate_unchanged_station(stations, self.stations)
            self.stations = stations
            data = capture_rocket(self.root, directory / "native", self.reports[-1], self.original, self.legs)
            if phase == "baseline":
                self.original = data
                shutil.copytree(self.root / "world", directory / "world-backup")
            elif phase == "restart":
                require(data == json.loads((self.output / "upgrade/native/entity-regions.json").read_text(encoding="utf-8"))["rocket"],
                        "Native rocket changed across final restart")
            journal_path = self.root / "world/data" / native.TRANSFER_JOURNAL
            require(not self.legs or journal_path.is_file(), "Landed native transfer journal is missing")
            if journal_path.exists():
                shutil.copyfile(journal_path, directory / "transfers.dat")
                journal = native.read_transfers(directory / "transfers.dat")
                validate_transfer(journal, self.reports, self.legs, data)
            require(server.digest_file(self.root / "mods" / artifact["name"]) == artifact["sha256"], "Installed artifact changed")
            configuration = native.configuration_identity("server.properties", support.regular(self.root / "server.properties", 65536))
            require(self.configuration is None or self.configuration == configuration, "Active server configuration changed")
            self.configuration = configuration
            for relative in ("world/level.dat", "server.properties", server.SERVER_PROPERTIES_IDENTITY_FILE,
                             "eula.txt", "world/" + server.WORLD_IDENTITY_FILE):
                source = self.root / relative
                if source.exists():
                    target = directory / "inputs" / relative
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(support.regular(source, 4 * 1024**2))
            receipt.update(result="PASS", reports=self.reports, legs=self.legs, station_requests=self.station_requests,
                           configuration=configuration, log_counts=server.log_audit_counts(process.lines))
        except BaseException as exc:
            receipt.update(result="FAIL", error=f"{type(exc).__name__}: {exc}")
            if process is not None:
                process.abort()
            raise
        finally:
            receipt["exit_code"] = process.process.poll() if process else None
            write_json(directory / "observations.json", receipt)
            write_json(directory / "commands.json", self.commands)
            for name in ("latest.log", "debug.log"):
                source = self.root / "logs" / name
                if source.exists():
                    (directory / name).write_bytes(support.regular(source, 32 * 1024**2))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("server_dir", type=Path)
    parser.add_argument("--baseline-jar", type=Path, required=True)
    parser.add_argument("--host-jar", type=Path, required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--accept-eula", action="store_true", required=True)
    parser.add_argument("--research-unlocks", action="store_true",
                        help="Exercise discovery denial, private research, shared unlock and replay persistence")
    args, output = parser.parse_args(), None
    try:
        root, evidence, baseline, host = map(support.safe_path, (args.server_dir, args.evidence_dir, args.baseline_jar, args.host_jar))
        require(root.is_dir() and {path.name for path in root.iterdir()} == {"libraries"}, "Disposable server must contain only libraries")
        require(not evidence.exists() and root != evidence and root not in evidence.parents and evidence not in root.parents,
                "Evidence must be new and disjoint from server")
        require(all(root not in path.parents and evidence not in path.parents for path in (baseline, host)), "Artifacts must be outside outputs")
        for directory, children, files in os.walk(root / "libraries", followlinks=False):
            for name in children + files:
                support.safe_path(Path(directory) / name)
        artifacts = [support.artifact(path, HOST, "1.20.1-1.4.0-dev") for path in (baseline, host)]
        require(schema.packaged_counts(baseline) == (3, 4) and schema.packaged_counts(host) == (6, 9), "Not the pre-planet and expanded catalogs")
        args_file = root / "libraries/net/minecraftforge/forge" / server.FORGE_COORDINATE / ("win_args.txt" if platform.system() == "Windows" else "unix_args.txt")
        support.regular(args_file, 65536)
        java, version = server.resolve_java(args.java)
        evidence.mkdir(parents=True)
        output = evidence
        port = server.allocate_port()
        properties = server.write_server_configuration(root, port, True)
        (root / "mods").mkdir()
        summary = {"schema_version": 1, "result": "IN_PROGRESS", "scope": "pre-planet upgrade, three surface legs, native restart; no clients/load campaign",
                   "artifacts": artifacts, "java": version, "server": str(root), "port": port, "forge_args_sha256": server.digest_file(args_file)}
        write_json(output / "summary.json", summary)
        harness = Harness(root, output, java, port, args.research_unlocks)
        shutil.copyfile(baseline, root / "mods" / artifacts[0]["name"])
        harness.cycle("baseline", artifacts[0])
        identity = server.establish_world_identity(root, str(uuid.uuid4()), artifacts[0]["sha256"], properties)
        write_json(output / "baseline/world-identity.json", identity)
        server.complete_world_identity(root, identity)
        installed = root / "mods" / artifacts[0]["name"]
        require(server.digest_file(installed) == artifacts[0]["sha256"], "Baseline installation changed before upgrade")
        installed.unlink()
        shutil.copyfile(host, root / "mods" / artifacts[1]["name"])
        harness.cycle("upgrade", artifacts[1])
        harness.cycle("restart", artifacts[1])
        require(all(server.digest_file(Path(artifact["path"])) == artifact["sha256"] for artifact in artifacts), "Input artifact changed")
        summary.update(result="PASS", world=server.complete_world_identity(root, identity), reports=harness.reports,
                       legs=harness.legs, research=harness.research, research_unlocks=args.research_unlocks)
        write_json(output / "summary.json", summary)
        print(f"[PASS] Three bounded planetary-world processes; evidence: {output}")
        return 0
    except (OSError, ValueError, KeyError, TypeError, IndexError, SmokeError) as exc:
        if output is not None:
            write_json(output / "failure.json", {"error": str(exc), "type": type(exc).__name__})
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1
    finally:
        if output is not None:
            paths = sorted(path for path in output.rglob("*") if path.is_file() and path.name != "SHA256SUMS")
            (output / "SHA256SUMS").write_text("".join(f"{server.digest_file(path)}  {path.relative_to(output).as_posix()}\n" for path in paths),
                                               encoding="ascii", newline="\n")


if __name__ == "__main__":
    raise SystemExit(main())
