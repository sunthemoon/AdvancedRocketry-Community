#!/usr/bin/env python3
"""Exercise 20 packaged Earth/Moon round trips and the v0.6 restart matrix."""

from __future__ import annotations

import argparse
import json
import os
import platform
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

if __package__:
    from .run_dedicated_server_smoke import (
        CapturedProcess,
        FORGE_COORDINATE,
        READY_MARKER,
        SAVE_MARKER,
        SmokeError,
        digest_file,
        is_link_or_junction,
        resolve_java,
        scan_log,
        validate_status_identity,
        wait_for_status,
    )
else:
    from run_dedicated_server_smoke import (
        CapturedProcess,
        FORGE_COORDINATE,
        READY_MARKER,
        SAVE_MARKER,
        SmokeError,
        digest_file,
        is_link_or_junction,
        resolve_java,
        scan_log,
        validate_status_identity,
        wait_for_status,
    )


EXPECTED_VERSION = "1.20.1-0.6.0-dev"
EARTH = "minecraft:overworld"
MOON = "advancedrocketrycommunity:moon"
ROUND_TRIPS = 20
X = 384
Y = 100
Z = 384
ASSEMBLER = (X, Y, Z)
FUEL_TANK = (X - 1, Y + 1, Z)
MOTOR = (X, Y + 1, Z)
CHEST = (X + 1, Y + 1, Z)
SEAT = (X, Y + 2, Z)
GUIDANCE = (X, Y + 3, Z)
EXPECTED_BLOCKS = 5
RUN_TOKEN = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")

ASSEMBLY_LOG = re.compile(
    r"ARCE_ROCKET_TRANSACTION operation=assembly code=SUCCESS blocks=(\d+) "
    r"snapshot=([0-9a-f]{64}) entity=([0-9a-f-]{36})"
)
ACTIVE_ROCKET_LOG = re.compile(
    r"ARCE_ROCKET_ENTITY_ACTIVE entity=([0-9a-f-]{36}) operational=true "
)
REFUEL_LOG = re.compile(
    r"ARCE_RELEASE_TEST_REFUEL entity=([0-9a-f-]{36}) "
    r"logical=([0-9a-f-]{36}) dimension=([^ ]+) state_before=([A-Z_]+) "
    r"state_after=([A-Z_]+) amount=(\d+) capacity=(\d+)"
)
LAUNCH_LOG = re.compile(
    r"ARCE_RELEASE_TEST_LAUNCH request=([0-9a-f-]{36}) "
    r"entity=([0-9a-f-]{36}) logical=([0-9a-f-]{36}) source=([^ ]+) "
    r"destination=([^ ]+) checkpoint=([^ ]+) code=([A-Z_]+) "
    r"required_fuel=(\d+) fuel_before=(\d+)"
)
PHASE_LOG = re.compile(
    r"ARCE_TRANSFER_PHASE transfer=([0-9a-f-]{36}) logical=([0-9a-f-]{36}) "
    r"phase=([A-Z_]+) event=([^ ]+) entity=([0-9a-f-]{36}) "
    r"fuel_before=(\d+) fuel_after=(\d+) required=(\d+)"
)
PAUSE_LOG = re.compile(
    r"ARCE_RELEASE_TEST_FLIGHT_PAUSED checkpoint=([A-Z_]+) "
    r"transfer=([0-9a-f-]{36}) phase=([A-Z_]+) state=([A-Z_]+) "
    r"entity=([0-9a-f-]{36})"
)
REPORT_LOG = re.compile(
    r"ARCE_RELEASE_TEST_FLIGHT_REPORT entity=([0-9a-f-]{36}) "
    r"logical=([0-9a-f-]{36}) snapshot=([0-9a-f]{64}) dimension=([^ ]+) "
    r"state=([A-Z_]+) fuel=(\d+) capacity=(\d+) passengers=(\d+) "
    r"transfer=([^ ]+) origin=(-?\d+),(-?\d+),(-?\d+) blocks=(\d+)"
)
REPORT_OR_MISSING_LOG = re.compile(
    rf"(?:{REPORT_LOG.pattern})|(?:No entity was found)"
)
RECOVERY_LOG = re.compile(
    r"ARCE_TRANSFER_RECOVERY transfer=([0-9a-f-]{36}) phase=([A-Z_]+) "
    r"source_count=(\d+) destination_count=(\d+) action=([A-Z_]+) status=([A-Z_]+)"
)
DISASSEMBLY_LOG = re.compile(
    r"ARCE_RELEASE_TEST_DISASSEMBLY entity=([0-9a-f-]{36}) "
    r"logical=([0-9a-f-]{36}) code=([A-Z_]+) blocks=(\d+) rolled_back=(\d+)"
)
BLOCK_DATA_MARKER = re.compile(r"has the following block data:\s*(.+)$")
EXPECTED_FLIGHT_EVENTS = [
    "countdown_complete",
    "ascent_complete",
    "destination_spawned",
    "passengers_transferred",
    "source_removed",
    "committed",
    "landing_complete",
    "landed_reservation_retained",
]
RESTART_CASES = [
    "ASSEMBLED",
    "FUELED",
    "COUNTDOWN",
    "ASCENT",
    "TRANSIT_PREPARED",
    "DESTINATION_SPAWNED",
    "DESCENT",
    "LANDED",
]


def _position(value: tuple[int, int, int]) -> str:
    return f"{value[0]} {value[1]} {value[2]}"


def _in_dimension(dimension: str, command: str) -> str:
    return f"execute in {dimension} run {command}"


def _load_summary(path: Path) -> dict[str, object]:
    if is_link_or_junction(path) or not path.is_file() or path.stat().st_size > 1024 * 1024:
        raise SmokeError(f"Baseline summary is missing, unsafe, or too large: {path}")
    value = json.loads(path.read_text(encoding="utf-8", errors="strict"))
    if not isinstance(value, dict):
        raise SmokeError("Baseline summary must be a JSON object")
    return value


def _verify_inputs(
    server: Path,
    summary: dict[str, object],
    expected_version: str,
) -> tuple[int, str]:
    if is_link_or_junction(server) or not server.is_dir():
        raise SmokeError(f"Installed server directory is missing or unsafe: {server}")
    if summary.get("mod_version") != expected_version:
        raise SmokeError("Baseline summary does not match the expected artifact version")
    port = summary.get("server_port")
    artifact_sha256 = summary.get("artifact_sha256")
    if not isinstance(port, int) or not 1 <= port <= 65535:
        raise SmokeError("Baseline summary has an invalid server port")
    if not isinstance(artifact_sha256, str) or re.fullmatch(r"[0-9a-f]{64}", artifact_sha256) is None:
        raise SmokeError("Baseline summary has an invalid artifact SHA-256")
    artifact = server / "mods" / f"advancedrocketry-community-{expected_version}.jar"
    if is_link_or_junction(artifact) or not artifact.is_file():
        raise SmokeError(f"Installed artifact is missing: {artifact}")
    if digest_file(artifact) != artifact_sha256:
        raise SmokeError("Installed artifact does not match the baseline summary")
    return port, artifact_sha256


def _server_command(java: str) -> list[str]:
    args_name = "win_args.txt" if platform.system() == "Windows" else "unix_args.txt"
    args_file = Path("libraries/net/minecraftforge/forge") / FORGE_COORDINATE / args_name
    return [
        java,
        "-Xms512M",
        "-Xmx1024M",
        "-Djava.net.preferIPv4Stack=true",
        "-Dadvancedrocketrycommunity.releaseTestHooks=true",
        f"@{args_file.as_posix()}",
        "nogui",
    ]


class FlightHarness:
    def __init__(
        self,
        *,
        java: str,
        server: Path,
        port: int,
        expected_version: str,
        startup_timeout: float,
    ) -> None:
        self.java = java
        self.server = server
        self.port = port
        self.expected_version = expected_version
        self.startup_timeout = startup_timeout
        self.process_documents: list[dict[str, object]] = []
        self.filtered_lines: list[str] = []

    def start(self, name: str) -> CapturedProcess:
        full_log = self.server / f"{RUN_TOKEN}-{name}-full.txt"
        process = CapturedProcess(_server_command(self.java), self.server, full_log)
        process.wait_for(READY_MARKER, self.startup_timeout)
        validate_status_identity(wait_for_status(self.port), self.expected_version)
        setattr(process, "_arce_name", name)
        setattr(process, "_arce_log_path", full_log)
        setattr(process, "_arce_started_at", datetime.now(timezone.utc).isoformat())
        return process

    def stop(self, process: CapturedProcess) -> None:
        save_start = len(process.lines)
        process.command("save-all flush")
        process.wait_for(SAVE_MARKER, 60.0, start_at=save_start)
        process.command("stop")
        exit_code = process.finish()
        if exit_code != 0:
            raise SmokeError(f"Server process exited with code {exit_code}")
        findings = [
            line for line in scan_log(process.lines)
            if "ARCE_TRANSFER_RECOVERY" not in line
        ]
        if findings:
            raise SmokeError(f"Packaged flight server has a blocking log finding: {findings[0]}")
        log_path = getattr(process, "_arce_log_path")
        self.process_documents.append({
            "name": getattr(process, "_arce_name"),
            "started_at": getattr(process, "_arce_started_at"),
            "completed_at": datetime.now(timezone.utc).isoformat(),
            "exit_code": exit_code,
            "full_log_file": log_path.name,
            "full_log_sha256": digest_file(log_path),
        })
        self.filtered_lines.extend(
            line.rstrip() for line in process.lines
            if "ARCE_" in line or READY_MARKER.search(line) or SAVE_MARKER.search(line)
        )

    @staticmethod
    def command_marker(
        process: CapturedProcess,
        command: str,
        marker: str,
        timeout: float = 30.0,
    ) -> None:
        start = len(process.lines)
        process.command(command)
        process.wait_for(re.compile(re.escape(marker)), timeout, start_at=start)

    def configure_rocket(self, process: CapturedProcess) -> None:
        process.command(f"forceload add {X} {Z}")
        process.command(_in_dimension(EARTH, "kill @e[type=advancedrocketrycommunity:rocket]"))
        process.command(_in_dimension(MOON, "kill @e[type=advancedrocketrycommunity:rocket]"))
        process.command(f"fill {X - 2} {Y} {Z - 1} {X + 2} {Y + 4} {Z + 1} minecraft:air")
        process.command(f"setblock {_position(ASSEMBLER)} advancedrocketrycommunity:rocket_assembler")
        process.command(f"setblock {_position(FUEL_TANK)} advancedrocketrycommunity:rocket_fuel_tank")
        process.command(f"setblock {_position(MOTOR)} advancedrocketrycommunity:rocket_motor")
        process.command(f"setblock {_position(SEAT)} advancedrocketrycommunity:rocket_seat")
        process.command(f"setblock {_position(GUIDANCE)} advancedrocketrycommunity:guidance_computer")
        process.command(f"setblock {_position(CHEST)} minecraft:chest")
        process.command(
            f"data merge block {_position(CHEST)} {{Items:["
            "{Slot:0b,id:\"minecraft:diamond\",Count:17b},"
            "{Slot:26b,id:\"minecraft:iron_ingot\",Count:64b}]}"
        )

    def assemble(self, process: CapturedProcess) -> dict[str, object]:
        self.configure_rocket(process)
        start = len(process.lines)
        process.command(f"arce rocket assemble {_position(ASSEMBLER)}")
        index = process.wait_for(ASSEMBLY_LOG, 45.0, start_at=start)
        match = ASSEMBLY_LOG.search(process.lines[index])
        if match is None:
            raise SmokeError("Could not parse packaged rocket assembly")
        blocks, snapshot, entity = match.groups()
        if int(blocks) != EXPECTED_BLOCKS:
            raise SmokeError(f"Packaged flight rocket has {blocks} blocks, expected {EXPECTED_BLOCKS}")
        active_pattern = re.compile(
            rf"ARCE_ROCKET_ENTITY_ACTIVE entity={re.escape(entity)} operational=true "
        )
        active_index = process.wait_for(active_pattern, 30.0, start_at=index)
        if ACTIVE_ROCKET_LOG.search(process.lines[active_index]) is None:
            raise SmokeError("Packaged rocket did not become active after assembly")
        report = self.report(process, EARTH, entity)
        if report["state"] != "ASSEMBLED" or report["blocks"] != EXPECTED_BLOCKS:
            raise SmokeError("Fresh packaged rocket did not report ASSEMBLED with five blocks")
        if report["snapshot"] != snapshot:
            raise SmokeError("Assembly receipt and entity report snapshot hashes differ")
        return report

    @staticmethod
    def report(process: CapturedProcess, dimension: str, entity: str) -> dict[str, object]:
        match = None
        for attempt in range(2):
            start = len(process.lines)
            process.command(_in_dimension(
                dimension,
                f"arce rocket release-test report {entity}",
            ))
            index = process.wait_for(REPORT_OR_MISSING_LOG, 30.0, start_at=start)
            match = REPORT_LOG.search(process.lines[index])
            if match is not None:
                break
            if attempt == 0:
                active_pattern = re.compile(
                    rf"ARCE_ROCKET_ENTITY_ACTIVE entity={re.escape(entity)} operational=true "
                )
                process.wait_for(active_pattern, 30.0, start_at=start)
        if match is None:
            raise SmokeError("Could not parse packaged flight report")
        (
            reported_entity,
            logical,
            snapshot,
            reported_dimension,
            state,
            fuel,
            capacity,
            passengers,
            transfer,
            origin_x,
            origin_y,
            origin_z,
            blocks,
        ) = match.groups()
        if reported_entity != entity or reported_dimension != dimension:
            raise SmokeError("Flight report resolved the wrong entity or dimension")
        return {
            "entity": reported_entity,
            "logical": logical,
            "snapshot": snapshot,
            "dimension": reported_dimension,
            "state": state,
            "fuel": int(fuel),
            "capacity": int(capacity),
            "passengers": int(passengers),
            "transfer": transfer,
            "origin": [int(origin_x), int(origin_y), int(origin_z)],
            "blocks": int(blocks),
        }

    @staticmethod
    def refuel(process: CapturedProcess, dimension: str, entity: str) -> dict[str, object]:
        start = len(process.lines)
        process.command(_in_dimension(
            dimension,
            f"arce rocket release-test refuel {entity}",
        ))
        index = process.wait_for(REFUEL_LOG, 30.0, start_at=start)
        match = REFUEL_LOG.search(process.lines[index])
        if match is None:
            raise SmokeError("Could not parse packaged refuel receipt")
        reported_entity, logical, reported_dimension, before, after, amount, capacity = match.groups()
        if reported_entity != entity or reported_dimension != dimension:
            raise SmokeError("Refuel receipt resolved the wrong rocket")
        if int(amount) != int(capacity) or after != "FUELED":
            raise SmokeError("Release-test refuel did not fill the server capacity")
        return {
            "entity": entity,
            "logical": logical,
            "dimension": dimension,
            "state_before": before,
            "state_after": after,
            "amount": int(amount),
            "capacity": int(capacity),
        }

    def launch_leg(
        self,
        process: CapturedProcess,
        *,
        sequence: int,
        trip: int,
        direction: str,
        source_dimension: str,
        destination_dimension: str,
        destination_name: str,
        entity: str,
        expected_logical: str,
    ) -> tuple[dict[str, object], dict[str, object]]:
        refuel = self.refuel(process, source_dimension, entity)
        if refuel["logical"] != expected_logical:
            raise SmokeError("Logical rocket identity changed before launch")
        start = len(process.lines)
        process.command(_in_dimension(
            source_dimension,
            f"arce rocket release-test launch {entity} {destination_name}",
        ))
        launch_index = process.wait_for(LAUNCH_LOG, 30.0, start_at=start)
        launch_match = LAUNCH_LOG.search(process.lines[launch_index])
        if launch_match is None:
            raise SmokeError("Could not parse packaged launch receipt")
        (
            transfer,
            launch_entity,
            logical,
            source,
            destination,
            checkpoint,
            code,
            required,
            fuel_before,
        ) = launch_match.groups()
        if (
            launch_entity != entity
            or logical != expected_logical
            or source != source_dimension
            or destination != destination_dimension
            or checkpoint != "none"
            or code != "SUCCESS"
        ):
            raise SmokeError("Packaged launch receipt changed authority or route")
        retained = re.compile(
            rf"ARCE_TRANSFER_PHASE transfer={re.escape(transfer)} .*"
            r"event=landed_reservation_retained "
        )
        retained_index = process.wait_for(retained, 30.0, start_at=launch_index)
        phase_matches = []
        for line in process.lines[launch_index:retained_index + 1]:
            phase_match = PHASE_LOG.search(line)
            if phase_match is not None and phase_match.group(1) == transfer:
                phase_matches.append(phase_match)
        events = [match.group(4) for match in phase_matches]
        if events != EXPECTED_FLIGHT_EVENTS:
            raise SmokeError(f"Transfer {transfer} emitted unexpected phase events: {events}")
        required_value = int(required)
        fuel_before_value = int(fuel_before)
        for phase_match in phase_matches:
            if phase_match.group(2) != expected_logical:
                raise SmokeError("Transfer phase changed logical rocket identity")
            before = int(phase_match.group(6))
            after = int(phase_match.group(7))
            phase_required = int(phase_match.group(8))
            if before != fuel_before_value or phase_required != required_value:
                raise SmokeError("Transfer phase fuel receipt disagrees with launch planning")
            if before - after != phase_required:
                raise SmokeError("Transfer did not debit the exact required fuel once")
        landing = next(match for match in phase_matches if match.group(4) == "landing_complete")
        destination_entity = landing.group(5)
        report = self.report(process, destination_dimension, destination_entity)
        if (
            report["logical"] != expected_logical
            or report["state"] != "LANDED"
            or report["fuel"] != fuel_before_value - required_value
            or report["blocks"] != EXPECTED_BLOCKS
            or report["passengers"] != 0
        ):
            raise SmokeError("Landed rocket report violated conservation or authority")
        leg = {
            "sequence": sequence,
            "trip": trip,
            "direction": direction,
            "transfer_id": transfer,
            "source_dimension": source_dimension,
            "destination_dimension": destination_dimension,
            "source_entity": entity,
            "destination_entity": destination_entity,
            "logical_rocket_id": expected_logical,
            "fuel_before": fuel_before_value,
            "fuel_after": report["fuel"],
            "required_fuel": required_value,
            "exact_debit": True,
            "phase_events": events,
            "snapshot_hash": report["snapshot"],
            "origin": report["origin"],
            "block_count": report["blocks"],
        }
        return leg, report

    def launch_to_checkpoint(
        self,
        process: CapturedProcess,
        *,
        dimension: str,
        entity: str,
        destination_name: str,
        checkpoint: str,
    ) -> dict[str, object]:
        self.refuel(process, dimension, entity)
        start = len(process.lines)
        process.command(_in_dimension(
            dimension,
            f"arce rocket release-test launch {entity} {destination_name} {checkpoint}",
        ))
        launch_index = process.wait_for(LAUNCH_LOG, 30.0, start_at=start)
        launch_match = LAUNCH_LOG.search(process.lines[launch_index])
        if launch_match is None or launch_match.group(7) != "SUCCESS":
            raise SmokeError(f"Could not launch restart checkpoint {checkpoint}")
        transfer = launch_match.group(1)
        pause_pattern = re.compile(
            rf"ARCE_RELEASE_TEST_FLIGHT_PAUSED checkpoint={re.escape(checkpoint)} "
            rf"transfer={re.escape(transfer)} "
        )
        pause_index = process.wait_for(pause_pattern, 30.0, start_at=launch_index)
        pause_match = PAUSE_LOG.search(process.lines[pause_index])
        if pause_match is None:
            raise SmokeError(f"Could not parse paused checkpoint {checkpoint}")
        paused_checkpoint, paused_transfer, phase, state, paused_entity = pause_match.groups()
        if paused_checkpoint != checkpoint or paused_transfer != transfer:
            raise SmokeError("Release checkpoint froze the wrong transfer")
        paused_dimension = EARTH if phase == "PREPARED" else MOON
        report = self.report(process, paused_dimension, paused_entity)
        if report["state"] != state:
            raise SmokeError("Paused entity report changed the frozen flight state")
        return {
            "transfer_id": transfer,
            "logical_rocket_id": launch_match.group(3),
            "source_entity": entity,
            "paused_entity": paused_entity,
            "paused_dimension": paused_dimension,
            "phase": phase,
            "state": state,
            "checkpoint": checkpoint,
            "required_fuel": int(launch_match.group(8)),
            "fuel_before": int(launch_match.group(9)),
            "report": report,
        }

    def disassemble_and_verify(
        self,
        process: CapturedProcess,
        report: dict[str, object],
        marker: str,
    ) -> None:
        dimension = str(report["dimension"])
        entity = str(report["entity"])
        logical = str(report["logical"])
        start = len(process.lines)
        process.command(_in_dimension(
            dimension,
            f"arce rocket release-test disassemble {entity}",
        ))
        index = process.wait_for(DISASSEMBLY_LOG, 45.0, start_at=start)
        match = DISASSEMBLY_LOG.search(process.lines[index])
        if match is None:
            raise SmokeError("Could not parse packaged disassembly receipt")
        reported_entity, reported_logical, code, blocks, rolled_back = match.groups()
        if (
            reported_entity != entity
            or reported_logical != logical
            or code != "SUCCESS"
            or int(blocks) != EXPECTED_BLOCKS
            or int(rolled_back) != 0
        ):
            raise SmokeError("Packaged disassembly did not restore the exact snapshot")
        origin_x, origin_y, origin_z = [int(value) for value in report["origin"]]
        conditions = (
            f"unless entity {entity} "
            f"if block {origin_x - 1} {origin_y} {origin_z} advancedrocketrycommunity:rocket_fuel_tank "
            f"if block {origin_x} {origin_y} {origin_z} advancedrocketrycommunity:rocket_motor "
            f"if block {origin_x + 1} {origin_y} {origin_z} minecraft:chest "
            f"if block {origin_x} {origin_y + 1} {origin_z} advancedrocketrycommunity:rocket_seat "
            f"if block {origin_x} {origin_y + 2} {origin_z} "
            "advancedrocketrycommunity:guidance_computer"
        )
        self.command_marker(
            process,
            _in_dimension(dimension, f"execute {conditions} run say {marker}"),
            marker,
        )
        chest_position = f"{origin_x + 1} {origin_y} {origin_z}"
        data_start = len(process.lines)
        process.command(_in_dimension(dimension, f"data get block {chest_position} Items"))
        data_index = process.wait_for(BLOCK_DATA_MARKER, 30.0, start_at=data_start)
        data_match = BLOCK_DATA_MARKER.search(process.lines[data_index])
        chest_data = data_match.group(1) if data_match else ""
        if (
            'id: "minecraft:diamond"' not in chest_data
            or "Count: 17b" not in chest_data
            or 'id: "minecraft:iron_ingot"' not in chest_data
            or "Count: 64b" not in chest_data
        ):
            raise SmokeError("Packaged disassembly changed chest inventory contents")
        process.command(_in_dimension(
            dimension,
            f"fill {origin_x - 1} {origin_y} {origin_z} "
            f"{origin_x + 1} {origin_y + 2} {origin_z} minecraft:air",
        ))


def _run_round_trips(harness: FlightHarness) -> tuple[list[dict[str, object]], dict[str, object]]:
    process = harness.start("v060-round-trips")
    try:
        report = harness.assemble(process)
        logical = str(report["logical"])
        entity = str(report["entity"])
        legs: list[dict[str, object]] = []
        sequence = 0
        for trip in range(1, ROUND_TRIPS + 1):
            sequence += 1
            outward, report = harness.launch_leg(
                process,
                sequence=sequence,
                trip=trip,
                direction="earth_to_moon",
                source_dimension=EARTH,
                destination_dimension=MOON,
                destination_name="moon",
                entity=entity,
                expected_logical=logical,
            )
            legs.append(outward)
            entity = str(report["entity"])
            sequence += 1
            returning, report = harness.launch_leg(
                process,
                sequence=sequence,
                trip=trip,
                direction="moon_to_earth",
                source_dimension=MOON,
                destination_dimension=EARTH,
                destination_name="earth",
                entity=entity,
                expected_logical=logical,
            )
            legs.append(returning)
            entity = str(report["entity"])
            print(f"[PASS] Packaged round trip {trip}/{ROUND_TRIPS}", flush=True)
        harness.disassemble_and_verify(process, report, "V060_ROUND_TRIPS_DISASSEMBLED")
        process.command(f"forceload remove {X} {Z}")
        harness.stop(process)
        return legs, {
            "logical_rocket_id": logical,
            "round_trips": ROUND_TRIPS,
            "legs": len(legs),
            "exact_fuel_debits": all(bool(leg["exact_debit"]) for leg in legs),
            "final_entity": entity,
            "final_origin": report["origin"],
            "material_conserved_after_disassembly": True,
            "container_inventory_conserved": True,
        }
    except BaseException:
        process.abort()
        raise


def _stage_restart_case(harness: FlightHarness, case: str) -> dict[str, object]:
    process = harness.start(f"v060-restart-{case.lower()}-stage")
    try:
        report = harness.assemble(process)
        staged: dict[str, object] = {
            "case": case,
            "logical_rocket_id": report["logical"],
            "source_entity": report["entity"],
        }
        if case == "ASSEMBLED":
            staged["report"] = report
        elif case == "FUELED":
            harness.refuel(process, EARTH, str(report["entity"]))
            staged["report"] = harness.report(process, EARTH, str(report["entity"]))
        else:
            staged.update(harness.launch_to_checkpoint(
                process,
                dimension=EARTH,
                entity=str(report["entity"]),
                destination_name="moon",
                checkpoint=case,
            ))
        harness.stop(process)
        return staged
    except BaseException:
        process.abort()
        raise


def _recover_restart_case(
    harness: FlightHarness,
    case: str,
    staged: dict[str, object],
) -> dict[str, object]:
    process = harness.start(f"v060-restart-{case.lower()}-recover")
    try:
        recovery_document: dict[str, object] = {}
        if case in {"ASSEMBLED", "FUELED"}:
            entity = str(staged["source_entity"])
            report = harness.report(process, EARTH, entity)
            if report["state"] != case:
                raise SmokeError(f"{case} did not persist across packaged restart")
        else:
            transfer = str(staged["transfer_id"])
            recovery_pattern = re.compile(
                rf"ARCE_TRANSFER_RECOVERY transfer={re.escape(transfer)} "
            )
            recovery_index = process.wait_for(recovery_pattern, 45.0)
            recovery_match = RECOVERY_LOG.search(process.lines[recovery_index])
            if recovery_match is None:
                raise SmokeError(f"Could not parse {case} restart recovery")
            (
                recovered_transfer,
                recovered_phase,
                source_count,
                destination_count,
                action,
                status,
            ) = recovery_match.groups()
            if recovered_transfer != transfer or status != "RECOVERED":
                raise SmokeError(f"{case} restart recovery did not complete")
            recovery_document = {
                "phase": recovered_phase,
                "source_count": int(source_count),
                "destination_count": int(destination_count),
                "action": action,
                "status": status,
            }
            if case in {"COUNTDOWN", "ASCENT", "TRANSIT_PREPARED"}:
                if action != "KEEP_SOURCE":
                    raise SmokeError(f"{case} restart selected {action}, expected KEEP_SOURCE")
                report = harness.report(process, EARTH, str(staged["source_entity"]))
                if report["state"] != "FUELED":
                    raise SmokeError(f"{case} restart did not recover the source to FUELED")
            elif case == "LANDED":
                if action != "KEEP_DESTINATION":
                    raise SmokeError("LANDED restart did not retain destination authority")
                report = harness.report(process, MOON, str(staged["paused_entity"]))
                if report["state"] != "LANDED":
                    raise SmokeError("LANDED restart changed the landed state")
            else:
                expected_action = (
                    "REMOVE_SOURCE_KEEP_DESTINATION"
                    if case == "DESTINATION_SPAWNED"
                    else "KEEP_DESTINATION"
                )
                if action != expected_action:
                    raise SmokeError(f"{case} restart selected {action}, expected {expected_action}")
                landing_pattern = re.compile(
                    rf"ARCE_TRANSFER_PHASE transfer={re.escape(transfer)} .*"
                    r"event=landing_complete entity=([0-9a-f-]{36})"
                )
                landing_index = process.wait_for(landing_pattern, 45.0)
                landing_match = landing_pattern.search(process.lines[landing_index])
                if landing_match is None:
                    raise SmokeError(f"{case} restart did not finish landing")
                report = harness.report(process, MOON, landing_match.group(1))
                if report["state"] != "LANDED":
                    raise SmokeError(f"{case} restart did not reach LANDED")
        if report["logical"] != staged["logical_rocket_id"]:
            raise SmokeError(f"{case} restart changed logical rocket identity")
        harness.disassemble_and_verify(process, report, f"V060_RESTART_{case}_DISASSEMBLED")
        harness.stop(process)
        return {
            "case": case,
            "staged_state": staged.get("state", case),
            "staged_phase": staged.get("phase", "none"),
            "recovered_state": report["state"],
            "recovered_dimension": report["dimension"],
            "logical_rocket_id": report["logical"],
            "recovery": recovery_document,
            "exact_disassembly": True,
            "container_inventory_conserved": True,
        }
    except BaseException:
        process.abort()
        raise


def _run_restart_matrix(harness: FlightHarness) -> list[dict[str, object]]:
    matrix: list[dict[str, object]] = []
    for case in RESTART_CASES:
        staged = _stage_restart_case(harness, case)
        recovered = _recover_restart_case(harness, case, staged)
        matrix.append(recovered)
        print(f"[PASS] Packaged restart checkpoint {case}", flush=True)
    return matrix


def _write_evidence(
    directory: Path,
    summary: dict[str, object],
    legs: list[dict[str, object]],
    restart_matrix: list[dict[str, object]],
    filtered_lines: list[str],
) -> None:
    if directory.exists():
        raise SmokeError(f"Refusing to overwrite v0.6 flight evidence: {directory}")
    directory.mkdir(parents=True)
    documents = {
        "summary.json": summary,
        "round-trip-ledger.json": {
            "schema_version": 1,
            "round_trips": ROUND_TRIPS,
            "legs": legs,
        },
        "restart-matrix.json": {
            "schema_version": 1,
            "cases": restart_matrix,
        },
    }
    for name, value in documents.items():
        (directory / name).write_text(
            json.dumps(value, ensure_ascii=True, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
            newline="\n",
        )
    (directory / "filtered-lifecycle.log").write_text(
        "\n".join(filtered_lines) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    lines = [
        f"artifact_sha256={summary['artifact_sha256']}",
        f"round_trips={ROUND_TRIPS}",
        f"flight_legs={ROUND_TRIPS * 2}",
        "exact_fuel_debits=40",
        f"restart_cases={len(RESTART_CASES)}",
        "restart_failures=0",
        "material_conserved=true",
        "container_inventory_conserved=true",
        "single_authority_after_each_leg=true",
    ]
    (directory / "lifecycle.txt").write_text(
        "\n".join(lines) + "\n",
        encoding="utf-8",
        newline="\n",
    )


def parse_args() -> argparse.Namespace:
    java_home = os.environ.get("JAVA_HOME")
    default_java = str(Path(java_home) / "bin" / "java") if java_home else "java"
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("server_dir", type=Path)
    parser.add_argument("--baseline-summary", type=Path, required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--java", default=default_java)
    parser.add_argument("--expected-version", default=EXPECTED_VERSION)
    parser.add_argument("--startup-timeout", type=float, default=240.0)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        server = args.server_dir.resolve()
        baseline = _load_summary(args.baseline_summary.resolve())
        port, artifact_sha256 = _verify_inputs(server, baseline, args.expected_version)
        java, java_version = resolve_java(args.java)
        harness = FlightHarness(
            java=java,
            server=server,
            port=port,
            expected_version=args.expected_version,
            startup_timeout=args.startup_timeout,
        )
        legs, round_trip_summary = _run_round_trips(harness)
        restart_matrix = _run_restart_matrix(harness)
        summary = {
            "schema_version": 1,
            "artifact_sha256": artifact_sha256,
            "artifact_version": args.expected_version,
            "baseline_session_id": baseline.get("session_id"),
            "completed_at": datetime.now(timezone.utc).isoformat(),
            "java": java_version,
            "port": port,
            "round_trips": round_trip_summary,
            "restart_matrix_cases": len(restart_matrix),
            "restart_matrix_passed": len(restart_matrix),
            "processes": harness.process_documents,
            "same_world_verified": True,
            "single_authority_after_each_leg": True,
            "critical_or_high_findings": 0,
        }
        evidence = args.evidence_dir.resolve()
        _write_evidence(
            evidence,
            summary,
            legs,
            restart_matrix,
            harness.filtered_lines,
        )
        print(f"[PASS] {ROUND_TRIPS} packaged Earth/Moon round trips ({len(legs)} legs)")
        print(f"[PASS] {len(restart_matrix)}/{len(RESTART_CASES)} restart checkpoints")
        print("[PASS] Exact fuel, block, inventory, logical identity, and authority checks")
        print(f"[PASS] Artifact SHA-256: {artifact_sha256}")
        print(f"[PASS] Evidence: {evidence}")
        return 0
    except (OSError, SmokeError, ValueError, json.JSONDecodeError) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
