#!/usr/bin/env python3
"""Native-command fixtures for an unchanged, packaged Beta server."""

from __future__ import annotations

import json
import re
import time
from datetime import datetime, timezone
from pathlib import Path

if __package__:
    from . import run_dedicated_server_smoke as server
    from . import run_v060_flight_server_smoke as flight
    from . import run_v070_station_server_smoke as station
    from . import run_v080_satellite_server_smoke as satellite
else:
    import run_dedicated_server_smoke as server
    import run_v060_flight_server_smoke as flight
    import run_v070_station_server_smoke as station
    import run_v080_satellite_server_smoke as satellite


NAMESPACE = "arce_v100_upgrade"
STORAGE = NAMESPACE + ":snapshot"
PAUSED = "0 100 0"
RUNNING = "4 100 0"
VENT = "64 100 64"
OWNER = "00000000-0000-0000-0000-000000001001"
SUCCESSOR = "00000000-0000-0000-0000-000000001002"
CAPTURED = "ARCE_V100_WORLD_CAPTURED"


def chat(marker: str) -> re.Pattern[str]:
    return re.compile(r"\[Server\] " + re.escape(marker) + r"\s*$")


def wait_condition(process, condition: str, marker: str, timeout: float = 30.0) -> None:
    start = len(process.lines)
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        process.command(f"{condition} run say {marker}")
        try:
            process.wait_for(chat(marker), min(1.0, max(.01, deadline - time.monotonic())), start_at=start)
            return
        except server.SmokeError:
            if process.process.poll() is not None:
                raise
    raise server.SmokeError(f"Fixture condition was not satisfied: {marker}")


def capture_functions() -> dict[str, list[str]]:
    prefix = f"data modify storage {STORAGE}"
    capture = [f'{prefix} state set value {{schema:1,rockets:[]}}']
    for name, position in (("paused", PAUSED), ("running", RUNNING)):
        capture.append(f"{prefix} state.{name} set from block {position} arce_machine")
        capture.append(f"execute store success storage {STORAGE} state.{name}_lit byte 1 "
                       f"run execute if block {position} advancedrocketrycommunity:electrolyzer[lit=true]")
    capture += [
        flight._in_dimension(flight.MOON, f"{prefix} state.vent set from block {VENT} arce_oxygen_vent"),
        flight._in_dimension(flight.MOON, f"execute store success storage {STORAGE} state.vent_lit byte 1 "
                             f"run execute if block {VENT} advancedrocketrycommunity:oxygen_vent[lit=true]"),
        f"execute store result storage {STORAGE} state.game_time long 1 run time query gametime",
        f"execute as @e[type=advancedrocketrycommunity:rocket] at @s run function {NAMESPACE}:rocket",
        f"data remove storage {STORAGE} scratch",
        f"say {CAPTURED}",
        "save-all flush",
        "stop",
    ]
    rocket = [f"{prefix} scratch set from entity @s"]
    for dimension in (flight.EARTH, flight.MOON):
        rocket.append(f'execute if dimension {dimension} run {prefix} scratch.observed_dimension '
                      f'set value "{dimension}"')
    rocket.append(f"{prefix} state.rockets append from storage {STORAGE} scratch")
    return {"capture": capture, "rocket": rocket}


def install_capture_pack(session: Path) -> None:
    pack = session / "world/datapacks" / NAMESPACE
    pack.mkdir(parents=True, exist_ok=False)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {
        "pack_format": 15, "description": "ARCE development-world read-only state capture"
    }}) + "\n", encoding="utf-8", newline="\n")
    functions = pack / "data" / NAMESPACE / "functions"
    functions.mkdir(parents=True)
    for name, lines in capture_functions().items():
        (functions / f"{name}.mcfunction").write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    properties = session / "server.properties"
    # Only this disposable server grants its local capture function save/stop.
    text = properties.read_text(encoding="utf-8")
    text = re.sub(r"(?m)^function-permission-level=.*(?:\n|$)", "", text)
    properties.write_text(text.rstrip() + "\nfunction-permission-level=4\n", encoding="utf-8", newline="\n")


class WorldHarness(flight.FlightHarness):
    """Owned processes; the second rocket must not delete the first Moon rocket."""

    def start(self, name: str):
        path = self.server / f"v100-upgrade-{name}-full.txt"
        process = server.CapturedProcess(flight._server_command(self.java), self.server, path)
        try:
            process.wait_for(server.READY_MARKER, self.startup_timeout)
            server.validate_status_identity(server.wait_for_status(self.port), self.expected_version)
            setattr(process, "_arce_name", name)
            setattr(process, "_arce_log_path", path)
            setattr(process, "_arce_started_at", datetime.now(timezone.utc).isoformat())
            return process
        except BaseException:
            process.abort()
            raise

    def configure_rocket(self, process) -> None:
        x, y, z = flight.ASSEMBLER
        process.command(f"forceload add {x - 2} {z - 1} {x + 2} {z + 1}")
        wait_condition(process, f"execute if loaded {x - 2} {y} {z - 1} "
                       f"if loaded {x + 2} {y + 4} {z + 1}", "ARCE_V100_ROCKET_AREA_LOADED")
        # The fixture is new and the preceding flight has removed the source.
        # Never clear or kill entities in either dimension.
        process.command(f"fill {x - 2} {y} {z - 1} {x + 2} {y + 4} {z + 1} minecraft:air")
        for position, block in ((flight.ASSEMBLER, "rocket_assembler"), (flight.FUEL_TANK, "rocket_fuel_tank"),
                                (flight.MOTOR, "rocket_motor"), (flight.SEAT, "rocket_seat"),
                                (flight.GUIDANCE, "guidance_computer")):
            process.command(f"setblock {flight._position(position)} advancedrocketrycommunity:{block}")
        process.command(f"setblock {flight._position(flight.CHEST)} minecraft:chest")
        process.command(f"data merge block {flight._position(flight.CHEST)} {{Items:["
                        '{Slot:0b,id:"minecraft:diamond",Count:17b},'
                        '{Slot:26b,id:"minecraft:iron_ingot",Count:64b}]}')

    def capture_and_stop(self, process) -> None:
        start = len(process.lines)
        process.command(f"function {NAMESPACE}:capture")
        process.wait_for(chat(CAPTURED), 30.0, start_at=start)
        process.wait_for(server.SAVE_MARKER, 60.0, start_at=start)
        code = process.finish()
        path = getattr(process, "_arce_log_path")
        self.process_documents.append({
            "name": getattr(process, "_arce_name"), "started_at": getattr(process, "_arce_started_at"),
            "completed_at": datetime.now(timezone.utc).isoformat(), "exit_code": code,
            "full_log_file": path.name, "full_log_sha256": server.digest_file(path),
        })
        if code != 0:
            raise server.SmokeError(f"World capture process exited with code {code}")
        findings = [line for line in server.scan_log(process.lines) if "ARCE_TRANSFER_RECOVERY" not in line]
        findings.extend(line for line in process.lines if "That position is not loaded" in line)
        if findings:
            raise server.SmokeError(f"World capture log has a blocking finding: {findings[0]}")


def machine_setup(process, position: str, *, energy: int, inputs: int, water: int) -> None:
    process.command(f"setblock {position} advancedrocketrycommunity:electrolyzer[facing=north]")
    process.command(f"data merge block {position} {{arce_machine:{{schema_version:1,"
                    f'inventory:{{Size:4,Items:[{{Slot:0,id:"advancedrocketrycommunity:empty_canister",Count:{inputs}b}}]}},'
                    f'fluid:{{FluidName:"minecraft:water",Amount:{water}}},energy:{energy},progress:0}}}}')


def room_setup(process) -> None:
    process.command(flight._in_dimension(flight.MOON, "forceload add 63 63 65 65"))
    wait_condition(process, f"execute in {flight.MOON} if loaded 63 100 63 if loaded 65 102 65",
                   "ARCE_V100_ROOM_AREA_LOADED")
    process.command(flight._in_dimension(flight.MOON, "fill 63 100 63 65 102 65 minecraft:iron_block hollow"))
    process.command(flight._in_dimension(flight.MOON, f"setblock {VENT} advancedrocketrycommunity:oxygen_vent"))
    process.command(flight._in_dimension(flight.MOON, f"data merge block {VENT} "
                    "{arce_oxygen_vent:{schema_version:1,oxygen_canisters:0,empty_canisters:0,"
                    "oxygen_units:4000,energy:40000,oxygen_phase:0}}"))


def setup_world(harness: WorldHarness, process) -> dict:
    process.command("gamerule doMobSpawning false")
    process.command("gamerule doDaylightCycle false")
    process.command("forceload add 0 0")
    machine_setup(process, PAUSED, energy=800, inputs=2, water=1000)
    process.command(flight._in_dimension(flight.MOON, "forceload add 8 8"))
    room_setup(process)
    rocket = harness.assemble(process)
    leg, moon = harness.launch_leg(process, sequence=1, trip=1, direction="earth_to_moon",
        source_dimension=flight.EARTH, destination_dimension=flight.MOON, destination_name="moon",
        entity=rocket["entity"], expected_logical=rocket["logical"])
    earth = harness.assemble(process)
    harness.refuel(process, flight.EARTH, earth["entity"])
    earth = harness.report(process, flight.EARTH, earth["entity"])
    start = len(process.lines)
    process.command(f"arce station admin create {OWNER} earth Beta-Upgrade-Station")
    index = process.wait_for(station.STATION_TRANSACTION_LOG, 45.0, start_at=start)
    match = station.STATION_TRANSACTION_LOG.search(process.lines[index])
    created = station._station_from_match(match)
    if created["owner_id"] != OWNER or tuple(map(int, match.groups()[8:10])) != (289, 289):
        raise server.SmokeError("Fixture station creation lost identity or platform blocks")
    start = len(process.lines)
    process.command(f"arce station admin transfer {created['station_id']} {SUCCESSOR}")
    index = process.wait_for(station.STATION_ACCESS_LOG, 30.0, start_at=start)
    if station.STATION_ACCESS_LOG.search(process.lines[index]).groups() != (
            "transfer", created["station_id"], station.CONSOLE_ACTOR, SUCCESSOR, "true"):
        raise server.SmokeError("Fixture ownership transfer did not execute")
    missions = [satellite._launch(process, OWNER, "earth"), satellite._launch(process, SUCCESSOR, "moon")]
    satellite._wait_until_ready(process, 2, 0)
    claim = satellite._claim(process, missions[0], "SUCCESS")
    wait_condition(process, f"execute if data block {PAUSED} "
                   "{arce_machine:{energy:0,progress:40}}", "ARCE_V100_REAL_MACHINE_PROGRESS")
    wait_condition(process, f"execute in {flight.MOON} "
                   f"if block {VENT} advancedrocketrycommunity:oxygen_vent[lit=true]",
                   "ARCE_V100_SEALED_ROOM")
    missions.append(satellite._launch(process, OWNER, "moon"))
    machine_setup(process, RUNNING, energy=20000, inputs=8, water=4000)
    wait_condition(process, f"execute if block {RUNNING} advancedrocketrycommunity:electrolyzer[lit=true]",
                   "ARCE_V100_RUNNING_MACHINE")
    return {"rockets": {"earth": earth, "moon": moon}, "flight": leg, "station": created,
            "station_after": station._dump_stations(process, 1), "missions": missions, "claim": claim,
            "satellite_report": satellite._report(process)}


def observe_world(harness: WorldHarness, process, setup: dict) -> dict:
    rockets = {name: harness.report(process, previous["dimension"], previous["entity"])
               for name, previous in setup["rockets"].items()}
    wait_condition(process, f"execute in {flight.MOON} if block {VENT} "
                   "advancedrocketrycommunity:oxygen_vent[lit=true]", "ARCE_V100_RELOADED_ROOM")
    start = len(process.lines)
    process.command("arce beta report")
    pattern = re.compile(r"ARCE-BETA-1101 build=[^ ]+ .*root_schema=2 operational=true roots=11111 .*")
    index = process.wait_for(pattern, 30.0, start_at=start)
    return {"rockets": rockets, "stations": station._dump_stations(process, 1),
            "satellite_report": satellite._report(process), "operator_report": process.lines[index].strip()}
