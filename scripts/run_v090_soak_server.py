#!/usr/bin/env python3
"""Run the packaged v0.9 maximum scenario and two-hour server soak."""

from __future__ import annotations

import argparse
import csv
import ctypes
import json
import os
import platform
import re
import statistics
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable

if __package__:
    from .run_dedicated_server_smoke import (
        READY_MARKER,
        SAVE_MARKER,
        SmokeError,
        digest_file,
        query_server_status,
        resolve_java,
        scan_log,
        validate_status_identity,
    )
    from .run_v040_atmosphere_server_smoke import (
        ACTIVE_MARKER,
        VENT_POSITIONS,
        _configure_rooms,
        _jstat_gc,
        _rss_bytes,
        _sample_tps,
        _vent_conditions,
        _wait_for_marker,
        percentile,
    )
    from .run_v060_flight_server_smoke import (
        ACTIVE_ROCKET_LOG,
        ASSEMBLY_LOG,
        EARTH,
        FlightHarness,
        _in_dimension,
        _load_summary,
        _verify_inputs,
    )
    from .run_v070_station_server_smoke import (
        STATION_COUNT,
        _create_stations,
        _dump_stations,
    )
    from .run_v080_satellite_server_smoke import BATCH_LOG, _report as satellite_report
else:
    from run_dedicated_server_smoke import (
        READY_MARKER,
        SAVE_MARKER,
        SmokeError,
        digest_file,
        query_server_status,
        resolve_java,
        scan_log,
        validate_status_identity,
    )
    from run_v040_atmosphere_server_smoke import (
        ACTIVE_MARKER,
        VENT_POSITIONS,
        _configure_rooms,
        _jstat_gc,
        _rss_bytes,
        _sample_tps,
        _vent_conditions,
        _wait_for_marker,
        percentile,
    )
    from run_v060_flight_server_smoke import (
        ACTIVE_ROCKET_LOG,
        ASSEMBLY_LOG,
        EARTH,
        FlightHarness,
        _in_dimension,
        _load_summary,
        _verify_inputs,
    )
    from run_v070_station_server_smoke import (
        STATION_COUNT,
        _create_stations,
        _dump_stations,
    )
    from run_v080_satellite_server_smoke import BATCH_LOG, _report as satellite_report


EXPECTED_VERSION = "1.20.1-0.9.0-beta.1"
MINIMUM_DURATION_SECONDS = 7_200.0
MAXIMUM_DURATION_SECONDS = 28_800.0
CLIENT_COUNT = 4
CLIENT_INTERVAL_SECONDS = 15.0
METRIC_INTERVAL_SECONDS = 30.0
REFILL_INTERVAL_SECONDS = 60.0
SAVE_INTERVAL_SECONDS = 300.0
REPORT_INTERVAL_SECONDS = 600.0
WARMUP_SECONDS = 60.0
MAX_ROCKET_BLOCKS = 2_048
MISSION_COUNT = 100
MAX_MEAN_TICK_MS = 50.0
MAX_RSS_GROWTH_BYTES = 256 * 1024 * 1024
MAX_OLD_GEN_GROWTH_PERCENT = 20.0
VENT_OXYGEN_UNITS = 4_000
VENT_ENERGY_UNITS = 40_000
MAX_X = 640
MAX_Y = 100
MAX_Z = 640
MAX_X2 = MAX_X + 7
MAX_Y2 = MAX_Y + 15
MAX_Z2 = MAX_Z + 15
MAX_ASSEMBLER = (MAX_X, MAX_Y - 1, MAX_Z)

OPERATOR_REPORT_LOG = re.compile(
    r"ARCE-BETA-1101 build=([^\s]+) forge=([^\s]+) jei=([^\s]+) "
    r"root_schema=(\d+) operational=(true|false) roots=([01]{5}) "
    r"bodies=(\d+) transactions=(\d+) transfers=(\d+) stations=(\d+) "
    r"missions=(\d+) players=(\d+)/(\d+) atmosphere_volume=(\d+) "
    r"atmosphere_tick=(\d+) protocols=life:(\d+),celestial:(\d+),"
    r"flight:(\d+),visual:(\d+) flight_frame_max=(\d+) "
    r"ticket_policy=([^\s]+)"
)


def validate_duration(duration_seconds: float) -> None:
    if not MINIMUM_DURATION_SECONDS <= duration_seconds <= MAXIMUM_DURATION_SECONDS:
        raise SmokeError(
            f"Soak duration must be between {MINIMUM_DURATION_SECONDS:,.0f} and "
            f"{MAXIMUM_DURATION_SECONDS:,.0f} real seconds"
        )


def _position(value: tuple[int, int, int]) -> str:
    return " ".join(str(part) for part in value)


def _refill_all_vents(process) -> None:  # type: ignore[no-untyped-def]
    """Keep every maximum-scenario vent under active load during the soak."""
    for position in VENT_POSITIONS:
        process.command(
            "execute in advancedrocketrycommunity:moon run data merge block "
            f"{_position(position)} "
            "{arce_oxygen_vent:{"
            f"oxygen_units:{VENT_OXYGEN_UNITS},energy:{VENT_ENERGY_UNITS}"
            "}}"
        )


def _load_migration_summary(
    path: Path,
    *,
    artifact_sha256: str,
    expected_version: str,
    tested_commit: str,
) -> dict[str, object]:
    summary = _load_summary(path)
    backup = summary.get("backup")
    if (
        summary.get("artifact_sha256") != artifact_sha256
        or summary.get("mod_version") != expected_version
        or summary.get("tested_commit") != tested_commit
        or summary.get("restart_current") is not True
        or summary.get("operator_report_operational") is not True
        or not isinstance(backup, dict)
        or backup.get("file_count") != 5
    ):
        raise SmokeError("Migration summary is not bound to this exact candidate and world")
    return summary


def _operator_report(
    process,  # type: ignore[no-untyped-def]
    expected_version: str,
    *,
    expected_stations: int | None = None,
    expected_missions: int | None = None,
) -> dict[str, object]:
    start = len(process.lines)
    process.command("arce beta report")
    index = process.wait_for(OPERATOR_REPORT_LOG, 30.0, start_at=start)
    match = OPERATOR_REPORT_LOG.search(process.lines[index])
    if match is None:
        raise SmokeError("Could not parse the bounded Beta operator report")
    values = match.groups()
    report: dict[str, object] = {
        "build": values[0],
        "forge": values[1],
        "jei": values[2],
        "root_schema": int(values[3]),
        "operational": values[4] == "true",
        "roots": values[5],
        "bodies": int(values[6]),
        "transactions": int(values[7]),
        "transfers": int(values[8]),
        "stations": int(values[9]),
        "missions": int(values[10]),
        "players_online": int(values[11]),
        "players_max": int(values[12]),
        "atmosphere_volume": int(values[13]),
        "atmosphere_tick": int(values[14]),
        "protocols": {
            "life": int(values[15]),
            "celestial": int(values[16]),
            "flight": int(values[17]),
            "visual": int(values[18]),
        },
        "flight_frame_max": int(values[19]),
        "ticket_policy": values[20],
    }
    if (
        report["build"] != expected_version
        or report["root_schema"] != 2
        or report["operational"] is not True
        or report["roots"] != "11111"
        or report["flight_frame_max"] != 39
        or report["ticket_policy"] != "transient_transfer_only"
    ):
        raise SmokeError(f"Beta operator report is degraded or incompatible: {report}")
    if expected_stations is not None and report["stations"] != expected_stations:
        raise SmokeError(f"Beta operator report changed the station count: {report}")
    if expected_missions is not None and report["missions"] != expected_missions:
        raise SmokeError(f"Beta operator report changed the mission count: {report}")
    return report


def _assemble_maximum_rocket(process, harness: FlightHarness) -> dict[str, object]:  # type: ignore[no-untyped-def]
    process.command(f"forceload add {MAX_X - 8} {MAX_Z - 8} {MAX_X2 + 8} {MAX_Z2 + 8}")
    process.command(_in_dimension(EARTH, "kill @e[type=advancedrocketrycommunity:rocket]"))
    process.command(
        f"fill {MAX_X - 1} {MAX_Y - 1} {MAX_Z - 1} "
        f"{MAX_X2 + 1} {MAX_Y2 + 1} {MAX_Z2 + 1} minecraft:air"
    )
    process.command(
        f"setblock {_position(MAX_ASSEMBLER)} advancedrocketrycommunity:rocket_assembler"
    )
    process.command(
        f"fill {MAX_X} {MAX_Y} {MAX_Z} {MAX_X2} {MAX_Y2} {MAX_Z2} "
        "minecraft:iron_block"
    )
    process.command(
        f"fill {MAX_X} {MAX_Y} {MAX_Z} {MAX_X2} {MAX_Y} {MAX_Z2} "
        "advancedrocketrycommunity:rocket_motor"
    )
    process.command(
        f"fill {MAX_X} {MAX_Y + 1} {MAX_Z} {MAX_X2} {MAX_Y + 1} {MAX_Z2} "
        "advancedrocketrycommunity:rocket_fuel_tank"
    )
    process.command(
        f"setblock {MAX_X} {MAX_Y + 2} {MAX_Z} "
        "advancedrocketrycommunity:rocket_seat"
    )
    process.command(
        f"setblock {MAX_X + 1} {MAX_Y + 2} {MAX_Z} "
        "advancedrocketrycommunity:guidance_computer"
    )
    start = len(process.lines)
    process.command(f"arce rocket assemble {_position(MAX_ASSEMBLER)}")
    index = process.wait_for(ASSEMBLY_LOG, 90.0, start_at=start)
    match = ASSEMBLY_LOG.search(process.lines[index])
    if match is None:
        raise SmokeError("Could not parse maximum rocket assembly receipt")
    blocks, snapshot, entity = match.groups()
    if int(blocks) != MAX_ROCKET_BLOCKS:
        raise SmokeError(f"Maximum rocket has {blocks} blocks, expected {MAX_ROCKET_BLOCKS}")
    active = re.compile(
        rf"ARCE_ROCKET_ENTITY_ACTIVE entity={re.escape(entity)} operational=true "
    )
    active_index = process.wait_for(active, 45.0, start_at=index)
    if ACTIVE_ROCKET_LOG.search(process.lines[active_index]) is None:
        raise SmokeError("Maximum rocket did not become operational")
    report = harness.report(process, EARTH, entity)
    if (
        report["state"] != "ASSEMBLED"
        or report["blocks"] != MAX_ROCKET_BLOCKS
        or report["snapshot"] != snapshot
        or report["capacity"] != 128_000
        or report["passengers"] != 0
    ):
        raise SmokeError(f"Maximum rocket report violated the fixed contract: {report}")
    return report


def _create_mission_batch(process) -> dict[str, object]:  # type: ignore[no-untyped-def]
    start = len(process.lines)
    process.command(f"arce satellite release-test batch {MISSION_COUNT}")
    index = process.wait_for(BATCH_LOG, 45.0, start_at=start)
    match = BATCH_LOG.search(process.lines[index])
    if match is None:
        raise SmokeError("Could not parse maximum mission batch receipt")
    requested, created, rejected, code, elapsed, tickets, scheduler = match.groups()
    if (
        (int(requested), int(created), int(rejected)) != (MISSION_COUNT, MISSION_COUNT, 0)
        or code != "SUCCESS"
        or int(tickets) != 0
        or scheduler != "deadline_queue"
    ):
        raise SmokeError("Maximum mission batch exceeded its fixed authority bounds")
    started = satellite_report(process)
    if (
        started["missions"] != MISSION_COUNT
        or started["active"] != MISSION_COUNT
        or started["unfinished"] != MISSION_COUNT
        or started["chunk_tickets"] != 0
    ):
        raise SmokeError(f"Maximum mission batch was not concurrently active: {started}")
    return {
        "requested": int(requested),
        "created": int(created),
        "rejected": int(rejected),
        "creation_elapsed_nanos": int(elapsed),
        "scheduler": scheduler,
        "initial": started,
    }


def _query_client(
    client_id: int,
    port: int,
    expected_version: str,
    query: Callable[[str, int, float], dict] = query_server_status,
) -> dict[str, object]:
    started = time.perf_counter()
    status = query("127.0.0.1", port, 5.0)
    validate_status_identity(status, expected_version)
    return {
        "client_id": client_id,
        "latency_ms": round((time.perf_counter() - started) * 1_000.0, 3),
    }


def probe_clients(
    port: int,
    expected_version: str,
    query: Callable[[str, int, float], dict] = query_server_status,
) -> list[dict[str, object]]:
    results: list[dict[str, object]] = []
    with ThreadPoolExecutor(max_workers=CLIENT_COUNT) as executor:
        futures = {
            executor.submit(_query_client, client_id, port, expected_version, query): client_id
            for client_id in range(1, CLIENT_COUNT + 1)
        }
        for future in as_completed(futures):
            client_id = futures[future]
            try:
                results.append(future.result())
            except BaseException as error:
                raise SmokeError(f"Simulated client {client_id} status probe failed: {error}") from error
    return sorted(results, key=lambda value: int(value["client_id"]))


def _windows_cpu_seconds(pid: int) -> float | None:
    class FileTime(ctypes.Structure):
        _fields_ = (("low", ctypes.c_ulong), ("high", ctypes.c_ulong))

    process_query_limited_information = 0x1000
    kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
    kernel32.OpenProcess.argtypes = (ctypes.c_ulong, ctypes.c_int, ctypes.c_ulong)
    kernel32.OpenProcess.restype = ctypes.c_void_p
    kernel32.GetProcessTimes.argtypes = (
        ctypes.c_void_p,
        ctypes.POINTER(FileTime),
        ctypes.POINTER(FileTime),
        ctypes.POINTER(FileTime),
        ctypes.POINTER(FileTime),
    )
    kernel32.GetProcessTimes.restype = ctypes.c_int
    kernel32.CloseHandle.argtypes = (ctypes.c_void_p,)
    kernel32.CloseHandle.restype = ctypes.c_int
    handle = kernel32.OpenProcess(process_query_limited_information, False, pid)
    if not handle:
        return None
    try:
        creation = FileTime()
        exit_time = FileTime()
        kernel = FileTime()
        user = FileTime()
        if not kernel32.GetProcessTimes(
            handle,
            ctypes.byref(creation),
            ctypes.byref(exit_time),
            ctypes.byref(kernel),
            ctypes.byref(user),
        ):
            return None
        kernel_ticks = (kernel.high << 32) | kernel.low
        user_ticks = (user.high << 32) | user.low
        return (kernel_ticks + user_ticks) / 10_000_000.0
    finally:
        kernel32.CloseHandle(handle)


def process_cpu_seconds(pid: int) -> float | None:
    if platform.system() == "Windows":
        return _windows_cpu_seconds(pid)
    stat = Path(f"/proc/{pid}/stat")
    if not stat.is_file():
        return None
    fields = stat.read_text(encoding="ascii", errors="strict").split()
    if len(fields) < 15:
        return None
    ticks = int(fields[13]) + int(fields[14])
    return ticks / float(os.sysconf("SC_CLK_TCK"))


def _advance_due(due: float, interval: float, now: float) -> float:
    while due <= now:
        due += interval
    return due


def _growth_summary(
    values: list[float],
    *,
    absolute_limit: float,
    unit: str,
) -> dict[str, object]:
    if len(values) < 4:
        raise SmokeError(f"Insufficient {unit} samples for sustained-growth analysis")
    count = max(1, len(values) // 4)
    start_index = max(1, len(values) // 10)
    early = values[start_index:start_index + count]
    late = values[-count:]
    early_median = float(statistics.median(early))
    late_median = float(statistics.median(late))
    growth = late_median - early_median
    return {
        "sample_count": len(values),
        "early_window_median": round(early_median, 3),
        "late_window_median": round(late_median, 3),
        "growth": round(growth, 3),
        "maximum_allowed_growth": absolute_limit,
        "unit": unit,
        "sustained_growth": growth > absolute_limit,
    }


def summarize_soak(
    *,
    duration_seconds: float,
    ticks: list[float],
    tps: list[float],
    rss: list[int],
    old_gen: list[float],
    cpu_percent: list[float],
    client_probe_count: int,
    save_count: int,
    refill_count: int,
    report_count: int,
    ticket_samples: list[int],
    vent_active_checks: int,
) -> dict[str, object]:
    if duration_seconds < MINIMUM_DURATION_SECONDS:
        raise SmokeError("Soak ended before 7,200 real seconds")
    if not ticks or not tps or not cpu_percent:
        raise SmokeError("Soak did not collect the required tick, TPS, and CPU metrics")
    expected_minimum_probes = CLIENT_COUNT * int(duration_seconds // CLIENT_INTERVAL_SECONDS)
    if client_probe_count < expected_minimum_probes:
        raise SmokeError("Four-client simulation did not cover the complete soak window")
    if any(value != 0 for value in ticket_samples) or not ticket_samples:
        raise SmokeError("Satellite or transfer ticket count was non-zero or unobserved")
    minimum_vent_checks = int(duration_seconds // METRIC_INTERVAL_SECONDS)
    if vent_active_checks < minimum_vent_checks:
        raise SmokeError("Sixteen active vents were not verified across the complete soak window")
    rss_growth = _growth_summary(
        [float(value) for value in rss],
        absolute_limit=float(MAX_RSS_GROWTH_BYTES),
        unit="bytes",
    )
    old_growth = _growth_summary(
        old_gen,
        absolute_limit=MAX_OLD_GEN_GROWTH_PERCENT,
        unit="old_generation_percent",
    )
    tick_summary = {
        "average": round(sum(ticks) / len(ticks), 3),
        "p95": round(percentile(ticks, 0.95), 3),
        "maximum": round(max(ticks), 3),
        "limit": MAX_MEAN_TICK_MS,
    }
    passed = (
        tick_summary["average"] <= MAX_MEAN_TICK_MS
        and tick_summary["p95"] <= MAX_MEAN_TICK_MS
        and tick_summary["maximum"] <= MAX_MEAN_TICK_MS
        and rss_growth["sustained_growth"] is False
        and old_growth["sustained_growth"] is False
    )
    if not passed:
        raise SmokeError(
            "Soak exceeded a fixed tick or sustained-memory-growth budget: "
            f"tick={tick_summary}, rss={rss_growth}, old={old_growth}"
        )
    return {
        "duration_seconds": round(duration_seconds, 3),
        "minimum_duration_seconds": MINIMUM_DURATION_SECONDS,
        "simulation": "four_concurrent_minecraft_status_clients",
        "client_count": CLIENT_COUNT,
        "client_probe_count": client_probe_count,
        "client_probe_failures": 0,
        "metric_sample_count": len(ticks),
        "tick_ms": tick_summary,
        "tps": {
            "average": round(sum(tps) / len(tps), 3),
            "minimum": round(min(tps), 3),
        },
        "cpu_percent_normalized": {
            "average": round(sum(cpu_percent) / len(cpu_percent), 3),
            "p95": round(percentile(cpu_percent, 0.95), 3),
            "maximum": round(max(cpu_percent), 3),
            "logical_processors": os.cpu_count(),
        },
        "rss": {
            "minimum_bytes": min(rss),
            "maximum_bytes": max(rss),
            "growth_analysis": rss_growth,
        },
        "old_generation": {
            "minimum_percent": round(min(old_gen), 3),
            "maximum_percent": round(max(old_gen), 3),
            "growth_analysis": old_growth,
        },
        "periodic_saves": save_count,
        "vent_refills": refill_count,
        "vent_active_checks": vent_active_checks,
        "vent_activity_failures": 0,
        "operator_reports": report_count,
        "ticket_samples": len(ticket_samples),
        "maximum_ticket_count": max(ticket_samples),
        "budgets_passed": True,
    }


def _write_json(path: Path, value: object) -> None:
    payload = json.dumps(value, ensure_ascii=True, indent=2, sort_keys=True) + "\n"
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(payload, encoding="utf-8", newline="\n")
    os.replace(temporary, path)


def _run_soak(
    process,  # type: ignore[no-untyped-def]
    *,
    java: str,
    port: int,
    expected_version: str,
    duration_seconds: float,
    rocket: dict[str, object],
    evidence: Path,
) -> dict[str, object]:
    metric_path = evidence / "metrics.csv"
    probe_path = evidence / "client-probes.csv"
    metric_stream = metric_path.open("x", encoding="utf-8", newline="")
    probe_stream = probe_path.open("x", encoding="utf-8", newline="")
    metric_writer = csv.DictWriter(
        metric_stream,
        fieldnames=(
            "elapsed_seconds", "tick_ms", "tps", "rss_bytes",
            "process_cpu_seconds", "normalized_cpu_percent", "old_generation_percent",
            "young_gc_count", "full_gc_count", "gc_time_seconds",
        ),
    )
    probe_writer = csv.DictWriter(
        probe_stream,
        fieldnames=("elapsed_seconds", "client_id", "latency_ms"),
    )
    metric_writer.writeheader()
    probe_writer.writeheader()
    ticks: list[float] = []
    tps_values: list[float] = []
    rss_values: list[int] = []
    old_gen_values: list[float] = []
    cpu_percent_values: list[float] = []
    ticket_samples: list[int] = []
    client_probe_count = 0
    save_count = 0
    refill_count = 0
    vent_active_checks = 0
    report_count = 0
    metric_index = 0
    started = time.monotonic()
    wall_started = datetime.now(timezone.utc).isoformat()
    end = started + duration_seconds
    next_client = started
    next_metric = started
    next_refill = started + REFILL_INTERVAL_SECONDS
    next_save = started + SAVE_INTERVAL_SECONDS
    next_report = started + REPORT_INTERVAL_SECONDS
    next_heartbeat = started + SAVE_INTERVAL_SECONDS
    cpu_count = max(1, os.cpu_count() or 1)
    last_cpu = process_cpu_seconds(process.process.pid)
    last_cpu_at = started
    gc_before = _jstat_gc(java, process.process.pid)
    try:
        _refill_all_vents(process)
        refill_count += 1
        while True:
            now = time.monotonic()
            if process.process.poll() is not None:
                raise SmokeError(f"Server exited during the soak with code {process.process.returncode}")
            if now >= end:
                break
            if now >= next_client:
                for result in probe_clients(port, expected_version):
                    probe_writer.writerow({
                        "elapsed_seconds": round(time.monotonic() - started, 3),
                        **result,
                    })
                    client_probe_count += 1
                probe_stream.flush()
                next_client = _advance_due(next_client, CLIENT_INTERVAL_SECONDS, time.monotonic())
            now = time.monotonic()
            if now >= next_metric:
                marker = f"V090_SOAK_VENTS_ACTIVE_{metric_index}"
                _wait_for_marker(process, _vent_conditions(True, marker), marker)
                vent_active_checks += 1
                tick_ms, tps = _sample_tps(process, metric_index)
                rss = _rss_bytes(process.process.pid)
                cpu = process_cpu_seconds(process.process.pid)
                gc = _jstat_gc(java, process.process.pid)
                if rss is None or cpu is None or gc is None or "O" not in gc:
                    raise SmokeError("Required RSS, CPU, or JVM old-generation metric is unavailable")
                cpu_elapsed = max(0.001, time.monotonic() - last_cpu_at)
                normalized_cpu = max(0.0, (cpu - (last_cpu or cpu)) / cpu_elapsed * 100.0 / cpu_count)
                metric_writer.writerow({
                    "elapsed_seconds": round(time.monotonic() - started, 3),
                    "tick_ms": tick_ms,
                    "tps": tps,
                    "rss_bytes": rss,
                    "process_cpu_seconds": round(cpu, 3),
                    "normalized_cpu_percent": round(normalized_cpu, 3),
                    "old_generation_percent": gc.get("O"),
                    "young_gc_count": gc.get("YGC"),
                    "full_gc_count": gc.get("FGC"),
                    "gc_time_seconds": gc.get("GCT"),
                })
                metric_stream.flush()
                ticks.append(tick_ms)
                tps_values.append(tps)
                rss_values.append(rss)
                old_gen_values.append(gc["O"])
                cpu_percent_values.append(normalized_cpu)
                last_cpu = cpu
                last_cpu_at = time.monotonic()
                metric_index += 1
                next_metric = _advance_due(next_metric, METRIC_INTERVAL_SECONDS, time.monotonic())
            now = time.monotonic()
            if now >= next_refill:
                _refill_all_vents(process)
                refill_count += 1
                next_refill = _advance_due(next_refill, REFILL_INTERVAL_SECONDS, time.monotonic())
            now = time.monotonic()
            if now >= next_save:
                save_start = len(process.lines)
                process.command("save-all flush")
                process.wait_for(SAVE_MARKER, 60.0, start_at=save_start)
                save_count += 1
                next_save = _advance_due(next_save, SAVE_INTERVAL_SECONDS, time.monotonic())
            now = time.monotonic()
            if now >= next_report:
                operator = _operator_report(
                    process,
                    expected_version,
                    expected_stations=STATION_COUNT,
                    expected_missions=MISSION_COUNT,
                )
                satellite = satellite_report(process)
                rocket_report = FlightHarness.report(process, EARTH, str(rocket["entity"]))
                if rocket_report["blocks"] != MAX_ROCKET_BLOCKS:
                    raise SmokeError("Periodic maximum-rocket report changed its block count")
                if satellite["chunk_tickets"] != 0:
                    raise SmokeError("Periodic mission report found a permanent chunk ticket")
                ticket_samples.append(int(satellite["chunk_tickets"]))
                report_count += 1
                _write_json(
                    evidence / "checkpoint.json",
                    {
                        "elapsed_seconds": round(time.monotonic() - started, 3),
                        "operator": operator,
                        "satellite": satellite,
                        "rocket": rocket_report,
                        "metric_samples": metric_index,
                        "client_probes": client_probe_count,
                    },
                )
                next_report = _advance_due(next_report, REPORT_INTERVAL_SECONDS, time.monotonic())
            now = time.monotonic()
            if now >= next_heartbeat:
                print(
                    f"[INFO] v0.9 soak elapsed={now - started:.0f}s/"
                    f"{duration_seconds:.0f}s samples={metric_index} probes={client_probe_count}",
                    flush=True,
                )
                next_heartbeat = _advance_due(next_heartbeat, SAVE_INTERVAL_SECONDS, now)
            wake = min(next_client, next_metric, next_refill, next_save, next_report, end)
            remaining = wake - time.monotonic()
            if remaining > 0:
                time.sleep(min(remaining, 1.0))
        elapsed = time.monotonic() - started
        _refill_all_vents(process)
        refill_count += 1
        _wait_for_marker(
            process,
            _vent_conditions(True, "V090_ALL_VENTS_FINAL_ACTIVE"),
            "V090_ALL_VENTS_FINAL_ACTIVE",
        )
        vent_active_checks += 1
        final_satellite = satellite_report(process)
        ticket_samples.append(int(final_satellite["chunk_tickets"]))
        gc_after = _jstat_gc(java, process.process.pid)
        result = summarize_soak(
            duration_seconds=elapsed,
            ticks=ticks,
            tps=tps_values,
            rss=rss_values,
            old_gen=old_gen_values,
            cpu_percent=cpu_percent_values,
            client_probe_count=client_probe_count,
            save_count=save_count,
            refill_count=refill_count,
            report_count=report_count,
            ticket_samples=ticket_samples,
            vent_active_checks=vent_active_checks,
        )
        result.update({
            "started_at": wall_started,
            "completed_at": datetime.now(timezone.utc).isoformat(),
            "gc_before": gc_before,
            "gc_after": gc_after,
            "final_satellite": final_satellite,
        })
        return result
    finally:
        metric_stream.close()
        probe_stream.close()


def _verify_restart(
    process,  # type: ignore[no-untyped-def]
    *,
    expected_version: str,
    rocket: dict[str, object],
) -> dict[str, object]:
    stations = _dump_stations(process, STATION_COUNT)
    satellite = satellite_report(process)
    rocket_report = FlightHarness.report(process, EARTH, str(rocket["entity"]))
    _wait_for_marker(process, _vent_conditions(True, "V090_ALL_VENTS_RESTARTED"), "V090_ALL_VENTS_RESTARTED")
    operator = _operator_report(
        process,
        expected_version,
        expected_stations=STATION_COUNT,
        expected_missions=MISSION_COUNT,
    )
    if (
        len(stations) != STATION_COUNT
        or satellite["missions"] != MISSION_COUNT
        or satellite["chunk_tickets"] != 0
        or rocket_report["logical"] != rocket["logical"]
        or rocket_report["snapshot"] != rocket["snapshot"]
        or rocket_report["blocks"] != MAX_ROCKET_BLOCKS
    ):
        raise SmokeError("Same-world restart changed a maximum-scenario authority invariant")
    return {
        "station_count": len(stations),
        "mission_report": satellite,
        "rocket_report": rocket_report,
        "vent_count": len(VENT_POSITIONS),
        "operator_report": operator,
        "same_authority": True,
    }


def _remove_harness_forceloads(process) -> None:  # type: ignore[no-untyped-def]
    process.command(f"forceload remove {MAX_X - 8} {MAX_Z - 8} {MAX_X2 + 8} {MAX_Z2 + 8}")
    process.command(_in_dimension("advancedrocketrycommunity:moon", "forceload remove -1 -1 14 14"))


def _write_final_evidence(
    evidence: Path,
    summary: dict[str, object],
    filtered_lines: list[str],
) -> None:
    _write_json(evidence / "summary.json", summary)
    (evidence / "filtered-lifecycle.log").write_text(
        "\n".join(filtered_lines) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    (evidence / "lifecycle.txt").write_text(
        "\n".join((
            f"artifact_sha256={summary['artifact_sha256']}",
            f"tested_commit={summary['tested_commit']}",
            f"duration_seconds={summary['soak']['duration_seconds']}",  # type: ignore[index]
            "simulated_clients=4",
            "maximum_rocket_blocks=2048",
            "oxygen_vents=16",
            "stations=10",
            "missions=100",
            "same_world_restart=true",
            "permanent_chunk_tickets=0",
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
    parser.add_argument("--migration-summary", type=Path, required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--tested-commit", required=True)
    parser.add_argument("--duration-seconds", type=float, default=MINIMUM_DURATION_SECONDS)
    parser.add_argument("--java", default=default_java)
    parser.add_argument("--expected-version", default=EXPECTED_VERSION)
    parser.add_argument("--startup-timeout", type=float, default=240.0)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    process = None
    evidence = args.evidence_dir.resolve()
    try:
        validate_duration(args.duration_seconds)
        if re.fullmatch(r"[0-9a-f]{40}", args.tested_commit) is None:
            raise SmokeError("Tested commit must be a full lowercase Git SHA-1")
        server = args.server_dir.resolve()
        baseline = _load_summary(args.baseline_summary.resolve())
        port, artifact_sha256 = _verify_inputs(server, baseline, args.expected_version)
        migration = _load_migration_summary(
            args.migration_summary.resolve(),
            artifact_sha256=artifact_sha256,
            expected_version=args.expected_version,
            tested_commit=args.tested_commit,
        )
        if evidence.exists():
            raise SmokeError(f"Refusing to overwrite v0.9 soak evidence: {evidence}")
        evidence.mkdir(parents=True)
        java, java_version = resolve_java(args.java)
        harness = FlightHarness(
            java=java,
            server=server,
            port=port,
            expected_version=args.expected_version,
            startup_timeout=args.startup_timeout,
        )

        process = harness.start("v090-combined-soak")
        rocket = _assemble_maximum_rocket(process, harness)
        _configure_rooms(process)
        _wait_for_marker(process, _vent_conditions(True, ACTIVE_MARKER), ACTIVE_MARKER)
        stations = _create_stations(process)
        missions = _create_mission_batch(process)
        operator_before = _operator_report(
            process,
            args.expected_version,
            expected_stations=STATION_COUNT,
            expected_missions=MISSION_COUNT,
        )
        combined = {
            "rocket": rocket,
            "rocket_blocks": MAX_ROCKET_BLOCKS,
            "vent_count": len(VENT_POSITIONS),
            "station_count": len(stations),
            "station_ids": [station["station_id"] for station in stations],
            "mission_batch": missions,
            "operator_report": operator_before,
            "exact_maximum_counts": True,
        }
        _write_json(evidence / "combined-scenario.json", combined)
        initial_save = len(process.lines)
        process.command("save-all flush")
        process.wait_for(SAVE_MARKER, 60.0, start_at=initial_save)
        time.sleep(WARMUP_SECONDS)
        soak = _run_soak(
            process,
            java=java,
            port=port,
            expected_version=args.expected_version,
            duration_seconds=args.duration_seconds,
            rocket=rocket,
            evidence=evidence,
        )
        harness.stop(process)
        process = None

        process = harness.start("v090-combined-restart")
        restarted = _verify_restart(
            process,
            expected_version=args.expected_version,
            rocket=rocket,
        )
        _remove_harness_forceloads(process)
        harness.stop(process)
        process = None

        findings: list[str] = []
        for document in harness.process_documents:
            log_path = server / str(document["full_log_file"])
            lines = log_path.read_text(encoding="utf-8", errors="strict").splitlines()
            findings.extend(scan_log(lines))
        if findings:
            raise SmokeError(f"Combined soak has a blocking log finding: {findings[0]}")
        summary = {
            "schema_version": 1,
            "tested_commit": args.tested_commit,
            "artifact_version": args.expected_version,
            "artifact_sha256": artifact_sha256,
            "baseline_summary_sha256": digest_file(args.baseline_summary.resolve()),
            "migration_summary_sha256": digest_file(args.migration_summary.resolve()),
            "migration_backup": migration["backup"],
            "java": java_version,
            "platform": platform.platform(),
            "logical_processors": os.cpu_count(),
            "completed_at": datetime.now(timezone.utc).isoformat(),
            "combined": combined,
            "soak": soak,
            "restart": restarted,
            "processes": harness.process_documents,
            "blocking_log_findings": 0,
            "critical_or_high_findings": 0,
            "passed": True,
        }
        _write_final_evidence(evidence, summary, harness.filtered_lines)
        print(f"[PASS] Maximum rocket blocks: {MAX_ROCKET_BLOCKS}")
        print(f"[PASS] Concurrent vents/stations/missions: {len(VENT_POSITIONS)}/{STATION_COUNT}/{MISSION_COUNT}")
        print(f"[PASS] Four-client soak duration: {soak['duration_seconds']} seconds")
        print("[PASS] Tick, CPU, RSS, GC, save, ticket, and restart budgets")
        print(f"[PASS] Artifact SHA-256: {artifact_sha256}")
        print(f"[PASS] Evidence: {evidence}")
        return 0
    except (OSError, ValueError, json.JSONDecodeError, SmokeError) as error:
        if process is not None:
            process.abort()
        if evidence.is_dir():
            _write_json(
                evidence / "failure.json",
                {
                    "failed_at": datetime.now(timezone.utc).isoformat(),
                    "error": str(error),
                },
            )
        print(f"[FAIL] {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
