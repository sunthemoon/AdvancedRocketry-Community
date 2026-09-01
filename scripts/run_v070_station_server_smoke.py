#!/usr/bin/env python3
"""Exercise packaged v0.7 station allocation, flight, restart, and deletion."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

if __package__:
    from .run_dedicated_server_smoke import (
        SAVE_MARKER,
        SmokeError,
        digest_file,
        resolve_java,
        scan_log,
    )
    from .run_v060_flight_server_smoke import (
        EARTH,
        MOON,
        EXPECTED_FLIGHT_EVENTS,
        PHASE_LOG,
        FlightHarness,
        _in_dimension,
        _load_summary,
        _verify_inputs,
    )
else:
    from run_dedicated_server_smoke import (
        SAVE_MARKER,
        SmokeError,
        digest_file,
        resolve_java,
        scan_log,
    )
    from run_v060_flight_server_smoke import (
        EARTH,
        MOON,
        EXPECTED_FLIGHT_EVENTS,
        PHASE_LOG,
        FlightHarness,
        _in_dimension,
        _load_summary,
        _verify_inputs,
    )


EXPECTED_VERSION = "1.20.1-0.7.0-dev"
SPACE = "advancedrocketrycommunity:space"
STATION_COUNT = 10
CONSOLE_ACTOR = "00000000-0000-0000-0000-000000000007"
TRANSFERRED_OWNER = "00000000-0000-0000-0000-0000000007ff"
RUN_TOKEN = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")

STATION_TRANSACTION_LOG = re.compile(
    r"ARCE_STATION_TRANSACTION action=creation_committed "
    r"station=([0-9a-f-]{36}) owner=([0-9a-f-]{36}) cell=(-?\d+),(-?\d+) "
    r"region=(-?\d+),(-?\d+),(-?\d+),(-?\d+) "
    r"inspected=(\d+) changed=(\d+) chunks=(\d+) detail=([^\r\n]+)"
)
STATION_REGION_LOG = re.compile(
    r"ARCE_STATION_REGION station=([0-9a-f-]{36}) owner=([0-9a-f-]{36}) "
    r"cell=(-?\d+),(-?\d+) region=(-?\d+),(-?\d+),(-?\d+),(-?\d+) members=(\d+)"
)
STATION_DUMP_LOG = re.compile(r"ARCE_STATION_REGION_DUMP count=(\d+)")
STATION_ACCESS_LOG = re.compile(
    r"ARCE_STATION_ACCESS action=([^ ]+) station=([0-9a-f-]{36}) "
    r"actor=([0-9a-f-]{36}) subject=([0-9a-f-]{36}) allowed=(true|false)"
)
STATION_DELETED_LOG = re.compile(
    r"ARCE_STATION_DELETED station=([0-9a-f-]{36}) actor=([0-9a-f-]{36}) "
    r"inspected=(\d+) removed=(\d+) chunks=(\d+)"
)
STATION_LAUNCH_LOG = re.compile(
    r"ARCE_RELEASE_TEST_STATION_LAUNCH request=([0-9a-f-]{36}) "
    r"entity=([0-9a-f-]{36}) logical=([0-9a-f-]{36}) source=([^ ]+) "
    r"station=([0-9a-f-]{36}) code=([A-Z_]+) "
    r"required_fuel=(\d+) fuel_before=(\d+)"
)


class StationHarness(FlightHarness):
    """Flight harness that permits only the intentional station-delete warnings."""

    def stop(self, process) -> None:  # type: ignore[no-untyped-def]
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
            and "ARCE_STATION_DELETE_BACKUP" not in line
            and "ARCE_STATION_DELETED" not in line
        ]
        if findings:
            raise SmokeError(f"Packaged station server has a blocking log finding: {findings[0]}")
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
            if "ARCE_" in line or SAVE_MARKER.search(line)
        )


def _station_from_match(match: re.Match[str]) -> dict[str, object]:
    groups = match.groups()
    return {
        "station_id": groups[0],
        "owner_id": groups[1],
        "cell": [int(groups[2]), int(groups[3])],
        "region": [int(value) for value in groups[4:8]],
    }


def _regions_overlap(left: list[int], right: list[int]) -> bool:
    return (
        left[0] <= right[2]
        and left[2] >= right[0]
        and left[1] <= right[3]
        and left[3] >= right[1]
    )


def _validate_station_set(stations: list[dict[str, object]]) -> None:
    if len(stations) != STATION_COUNT:
        raise SmokeError(f"Expected {STATION_COUNT} stations, found {len(stations)}")
    station_ids = {str(station["station_id"]) for station in stations}
    cells = {tuple(station["cell"]) for station in stations}
    if len(station_ids) != STATION_COUNT or len(cells) != STATION_COUNT:
        raise SmokeError("Station IDs or grid cells are not unique")
    for index, left in enumerate(stations):
        left_region = list(left["region"])
        if left_region[2] - left_region[0] + 1 != 512 or left_region[3] - left_region[1] + 1 != 512:
            raise SmokeError("Station allocation changed the fixed 512x512 region")
        for right in stations[index + 1:]:
            if _regions_overlap(left_region, list(right["region"])):
                raise SmokeError("Packaged station regions overlap")


def _create_stations(process) -> list[dict[str, object]]:  # type: ignore[no-untyped-def]
    stations: list[dict[str, object]] = []
    for index in range(STATION_COUNT):
        owner = f"00000000-0000-0000-0000-{index + 0x700:012x}"
        orbit = "earth" if index % 2 == 0 else "moon"
        start = len(process.lines)
        process.command(f"arce station admin create {owner} {orbit} Packaged-{index + 1:02d}")
        line_index = process.wait_for(STATION_TRANSACTION_LOG, 45.0, start_at=start)
        match = STATION_TRANSACTION_LOG.search(process.lines[line_index])
        if match is None:
            raise SmokeError("Could not parse packaged station creation receipt")
        station = _station_from_match(match)
        if station["owner_id"] != owner:
            raise SmokeError("Station creation changed the requested owner")
        inspected, changed, chunks = [int(value) for value in match.groups()[8:11]]
        if inspected != 289 or changed != 289 or not 1 <= chunks <= 4:
            raise SmokeError("Station platform exceeded or missed its fixed generation budget")
        station["orbit"] = orbit
        station["platform_blocks"] = changed
        station["chunks_loaded"] = chunks
        stations.append(station)
        print(f"[PASS] Packaged station allocation {index + 1}/{STATION_COUNT}", flush=True)
    _validate_station_set(stations)
    return stations


def _dump_stations(process, expected_count: int) -> list[dict[str, object]]:  # type: ignore[no-untyped-def]
    start = len(process.lines)
    process.command("arce station admin dump")
    dump_index = process.wait_for(STATION_DUMP_LOG, 30.0, start_at=start)
    dump_match = STATION_DUMP_LOG.search(process.lines[dump_index])
    if dump_match is None or int(dump_match.group(1)) != expected_count:
        raise SmokeError("Station region dump reported an unexpected count")
    if expected_count == 0:
        return []
    regions: list[dict[str, object]] = []
    scan_at = dump_index + 1
    while len(regions) < expected_count:
        line_index = process.wait_for(STATION_REGION_LOG, 30.0, start_at=scan_at)
        match = STATION_REGION_LOG.search(process.lines[line_index])
        if match is None:
            raise SmokeError("Could not parse station region dump")
        station = _station_from_match(match)
        station["members"] = int(match.group(9))
        regions.append(station)
        scan_at = line_index + 1
    return regions


def _transfer_owner(process, station_id: str) -> None:  # type: ignore[no-untyped-def]
    start = len(process.lines)
    process.command(f"arce station admin transfer {station_id} {TRANSFERRED_OWNER}")
    index = process.wait_for(STATION_ACCESS_LOG, 30.0, start_at=start)
    match = STATION_ACCESS_LOG.search(process.lines[index])
    if match is None or match.groups() != (
        "transfer",
        station_id,
        CONSOLE_ACTOR,
        TRANSFERRED_OWNER,
        "true",
    ):
        raise SmokeError("Operator ownership transfer receipt changed authority")


def _flight_phases(
    process,
    launch_index: int,
    transfer: str,
    logical: str,
    fuel_before: int,
    required: int,
) -> tuple[int, str]:  # type: ignore[no-untyped-def]
    retained = re.compile(
        rf"ARCE_TRANSFER_PHASE transfer={re.escape(transfer)} .*"
        r"event=landed_reservation_retained "
    )
    retained_index = process.wait_for(retained, 30.0, start_at=launch_index)
    phase_matches = []
    for line in process.lines[launch_index:retained_index + 1]:
        match = PHASE_LOG.search(line)
        if match is not None and match.group(1) == transfer:
            phase_matches.append(match)
    if [match.group(4) for match in phase_matches] != EXPECTED_FLIGHT_EVENTS:
        raise SmokeError("Station flight emitted unexpected transaction phases")
    for match in phase_matches:
        if match.group(2) != logical:
            raise SmokeError("Station flight changed logical rocket identity")
        before = int(match.group(6))
        after = int(match.group(7))
        if before != fuel_before or int(match.group(8)) != required or before - after != required:
            raise SmokeError("Station flight violated the exact fuel debit receipt")
    landing = next(match for match in phase_matches if match.group(4) == "landing_complete")
    return retained_index, landing.group(5)


def _launch_station(
    harness: StationHarness,
    process,
    *,
    source_dimension: str,
    entity: str,
    logical: str,
    station: dict[str, object],
) -> tuple[dict[str, object], dict[str, object]]:  # type: ignore[no-untyped-def]
    refuel = harness.refuel(process, source_dimension, entity)
    if refuel["logical"] != logical:
        raise SmokeError("Logical rocket identity changed before station launch")
    station_id = str(station["station_id"])
    start = len(process.lines)
    process.command(_in_dimension(
        source_dimension,
        f"arce rocket release-test launch-station {entity} {station_id}",
    ))
    launch_index = process.wait_for(STATION_LAUNCH_LOG, 30.0, start_at=start)
    match = STATION_LAUNCH_LOG.search(process.lines[launch_index])
    if match is None:
        raise SmokeError("Could not parse packaged station launch receipt")
    transfer, launch_entity, launch_logical, source, reported_station, code, required, fuel = match.groups()
    if (
        launch_entity != entity
        or launch_logical != logical
        or source != source_dimension
        or reported_station != station_id
        or code != "SUCCESS"
    ):
        raise SmokeError("Station launch receipt changed authority or destination")
    required_value = int(required)
    fuel_before = int(fuel)
    _, destination_entity = _flight_phases(
        process,
        launch_index,
        transfer,
        logical,
        fuel_before,
        required_value,
    )
    report = harness.report(process, SPACE, destination_entity)
    region = list(station["region"])
    origin = list(report["origin"])
    if (
        report["logical"] != logical
        or report["state"] != "LANDED"
        or report["fuel"] != fuel_before - required_value
        or not (region[0] <= origin[0] <= region[2] and region[1] <= origin[2] <= region[3])
    ):
        raise SmokeError("Station landing escaped its server-owned region or changed authority")
    return {
        "transfer_id": transfer,
        "source_dimension": source_dimension,
        "destination_dimension": SPACE,
        "station_id": station_id,
        "source_entity": entity,
        "destination_entity": destination_entity,
        "logical_rocket_id": logical,
        "required_fuel": required_value,
        "fuel_before": fuel_before,
        "fuel_after": report["fuel"],
        "exact_fuel_debit": True,
        "landing_origin": origin,
        "landing_inside_region": True,
        "phase_events": EXPECTED_FLIGHT_EVENTS,
    }, report


def _delete_station(process, station_id: str) -> dict[str, int]:  # type: ignore[no-untyped-def]
    start = len(process.lines)
    process.command(f"arce station admin delete {station_id} confirm")
    index = process.wait_for(STATION_DELETED_LOG, 45.0, start_at=start)
    match = STATION_DELETED_LOG.search(process.lines[index])
    if match is None or match.group(1) != station_id or match.group(2) != CONSOLE_ACTOR:
        raise SmokeError("Station deletion receipt changed authority")
    inspected, removed, chunks = [int(value) for value in match.groups()[2:5]]
    if inspected != 289 or not 0 <= removed <= 289 or not 1 <= chunks <= 4:
        raise SmokeError("Station deletion exceeded its exact-template budget")
    return {"inspected": inspected, "removed": removed, "chunks": chunks}


def _run_first_session(harness: StationHarness) -> dict[str, object]:
    process = harness.start("v070-stations-and-flight")
    try:
        stations = _create_stations(process)
        initial_dump = _dump_stations(process, STATION_COUNT)
        _validate_station_set(initial_dump)
        _transfer_owner(process, str(stations[0]["station_id"]))

        report = harness.assemble(process)
        logical = str(report["logical"])
        station_legs: list[dict[str, object]] = []
        ordinary_legs: list[dict[str, object]] = []

        station_leg, report = _launch_station(
            harness,
            process,
            source_dimension=EARTH,
            entity=str(report["entity"]),
            logical=logical,
            station=stations[0],
        )
        station_legs.append(station_leg)
        leg, report = harness.launch_leg(
            process,
            sequence=2,
            trip=1,
            direction="station_to_earth",
            source_dimension=SPACE,
            destination_dimension=EARTH,
            destination_name="earth",
            entity=str(report["entity"]),
            expected_logical=logical,
        )
        ordinary_legs.append(leg)
        leg, report = harness.launch_leg(
            process,
            sequence=3,
            trip=1,
            direction="earth_to_moon",
            source_dimension=EARTH,
            destination_dimension=MOON,
            destination_name="moon",
            entity=str(report["entity"]),
            expected_logical=logical,
        )
        ordinary_legs.append(leg)
        station_leg, report = _launch_station(
            harness,
            process,
            source_dimension=MOON,
            entity=str(report["entity"]),
            logical=logical,
            station=stations[1],
        )
        station_legs.append(station_leg)
        leg, report = harness.launch_leg(
            process,
            sequence=5,
            trip=2,
            direction="station_to_moon",
            source_dimension=SPACE,
            destination_dimension=MOON,
            destination_name="moon",
            entity=str(report["entity"]),
            expected_logical=logical,
        )
        ordinary_legs.append(leg)
        leg, report = harness.launch_leg(
            process,
            sequence=6,
            trip=2,
            direction="moon_to_earth",
            source_dimension=MOON,
            destination_dimension=EARTH,
            destination_name="earth",
            entity=str(report["entity"]),
            expected_logical=logical,
        )
        ordinary_legs.append(leg)
        harness.disassemble_and_verify(process, report, "V070_STATION_FLIGHT_DISASSEMBLED")
        process.command("forceload remove 384 384")
        harness.stop(process)
        return {
            "stations": stations,
            "initial_dump": initial_dump,
            "station_legs": station_legs,
            "ordinary_legs": ordinary_legs,
            "logical_rocket_id": logical,
            "material_conserved_after_disassembly": True,
            "container_inventory_conserved": True,
        }
    except BaseException:
        process.abort()
        raise


def _run_restart_session(
    harness: StationHarness,
    staged: dict[str, object],
) -> dict[str, object]:
    process = harness.start("v070-station-restart")
    try:
        before = list(staged["stations"])
        restarted = _dump_stations(process, STATION_COUNT)
        _validate_station_set(restarted)
        expected = {
            str(station["station_id"]): {
                "cell": station["cell"],
                "region": station["region"],
                "owner_id": TRANSFERRED_OWNER if index == 0 else station["owner_id"],
            }
            for index, station in enumerate(before)
        }
        actual = {
            str(station["station_id"]): {
                "cell": station["cell"],
                "region": station["region"],
                "owner_id": station["owner_id"],
            }
            for station in restarted
        }
        if actual != expected:
            raise SmokeError("Station registry changed IDs, owners, cells, or regions after restart")

        deleted_id = str(before[2]["station_id"])
        neighbor_id = str(before[3]["station_id"])
        deletion = _delete_station(process, deleted_id)
        after_delete = _dump_stations(process, STATION_COUNT - 1)
        remaining = {str(station["station_id"]) for station in after_delete}
        if deleted_id in remaining or neighbor_id not in remaining:
            raise SmokeError("Deleting one station removed or changed a neighboring station")

        for station in after_delete:
            _delete_station(process, str(station["station_id"]))
        if _dump_stations(process, 0):
            raise SmokeError("Station cleanup left unexpected registry entries")
        harness.stop(process)
        return {
            "restart_station_count": len(restarted),
            "same_ids_owners_cells_regions": True,
            "deleted_station_id": deleted_id,
            "neighbor_station_id": neighbor_id,
            "neighbor_survived": True,
            "deletion_budget": deletion,
            "cleanup_count": STATION_COUNT - 1,
        }
    except BaseException:
        process.abort()
        raise


def _write_evidence(
    directory: Path,
    summary: dict[str, object],
    staged: dict[str, object],
    restarted: dict[str, object],
    filtered_lines: list[str],
) -> None:
    if directory.exists():
        raise SmokeError(f"Refusing to overwrite v0.7 station evidence: {directory}")
    directory.mkdir(parents=True)
    documents = {
        "summary.json": summary,
        "station-map.json": {
            "schema_version": 1,
            "region_size": 512,
            "grid_spacing": 1024,
            "stations": staged["stations"],
            "pairwise_non_overlapping": True,
        },
        "flight-ledger.json": {
            "schema_version": 1,
            "logical_rocket_id": staged["logical_rocket_id"],
            "station_legs": staged["station_legs"],
            "ordinary_legs": staged["ordinary_legs"],
            "earth_station_round_trip": True,
            "moon_station_round_trip": True,
        },
        "restart-and-deletion.json": {"schema_version": 1, **restarted},
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
    (directory / "lifecycle.txt").write_text(
        "\n".join((
            f"artifact_sha256={summary['artifact_sha256']}",
            f"stations_created={STATION_COUNT}",
            "pairwise_non_overlapping=true",
            "restart_persistence=true",
            "earth_station_round_trip=true",
            "moon_station_round_trip=true",
            "exact_station_landings=2",
            "neighbor_deletion_isolated=true",
            "material_conserved=true",
            "container_inventory_conserved=true",
            "critical_or_high_findings=0",
        )) + "\n",
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
        harness = StationHarness(
            java=java,
            server=server,
            port=port,
            expected_version=args.expected_version,
            startup_timeout=args.startup_timeout,
        )
        staged = _run_first_session(harness)
        restarted = _run_restart_session(harness, staged)
        summary = {
            "schema_version": 1,
            "artifact_sha256": artifact_sha256,
            "artifact_version": args.expected_version,
            "baseline_session_id": baseline.get("session_id"),
            "completed_at": datetime.now(timezone.utc).isoformat(),
            "java": java_version,
            "port": port,
            "station_count": STATION_COUNT,
            "station_flight_legs": len(staged["station_legs"]),
            "ordinary_flight_legs": len(staged["ordinary_legs"]),
            "same_world_verified": True,
            "restart_persistence_verified": True,
            "pairwise_non_overlapping": True,
            "neighbor_deletion_isolated": True,
            "critical_or_high_findings": 0,
            "processes": harness.process_documents,
        }
        evidence = args.evidence_dir.resolve()
        _write_evidence(evidence, summary, staged, restarted, harness.filtered_lines)
        print(f"[PASS] {STATION_COUNT} packaged stations allocated without overlap")
        print("[PASS] Earth/station and Moon/station packaged round trips")
        print("[PASS] Same-world restart preserved station authority and allocation")
        print("[PASS] Exact-template deletion preserved the neighboring station")
        print(f"[PASS] Artifact SHA-256: {artifact_sha256}")
        print(f"[PASS] Evidence: {evidence}")
        return 0
    except (OSError, SmokeError, ValueError, json.JSONDecodeError) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
