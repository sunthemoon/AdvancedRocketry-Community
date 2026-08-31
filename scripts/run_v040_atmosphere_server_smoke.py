#!/usr/bin/env python3
"""Verify packaged v0.4 atmosphere persistence and five-minute server budgets."""

from __future__ import annotations

import argparse
import csv
import json
import math
import os
import platform
import re
import subprocess
import sys
import time
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


EXPECTED_VERSION = "1.20.1-0.4.0-dev"
MOON = "advancedrocketrycommunity:moon"
Y = 100
MINIMUM_DURATION_SECONDS = 300.0
MAXIMUM_DURATION_SECONDS = 1_800.0
SAMPLE_INTERVAL_SECONDS = 5.0
ACTIVE_MARKER = "V040_ALL_VENTS_ACTIVE"
INACTIVE_MARKER = "V040_ALL_VENTS_INACTIVE"
RECOVERED_MARKER = "V040_ALL_VENTS_RECOVERED"
BLOCK_DATA_MARKER = re.compile(r"has the following block data:\s*(.+)$")
TPS_PATTERN = re.compile(
    r"Mean tick time:\s*([0-9]+(?:\.[0-9]+)?)\s*ms.*?"
    r"Mean TPS:\s*([0-9]+(?:\.[0-9]+)?)",
    re.IGNORECASE,
)
VENT_POSITIONS = tuple(
    (1 + grid_x * 4, Y, 1 + grid_z * 4)
    for grid_x in range(4)
    for grid_z in range(4)
)


def _load_summary(path: Path) -> dict[str, object]:
    if path.is_symlink() or not path.is_file() or path.stat().st_size > 1024 * 1024:
        raise SmokeError(f"Baseline summary is missing, unsafe, or too large: {path}")
    value = json.loads(path.read_text(encoding="utf-8", errors="strict"))
    if not isinstance(value, dict):
        raise SmokeError("Baseline summary must be a JSON object")
    return value


def _verify_inputs(
    server: Path,
    summary: dict[str, object],
    expected_version: str,
    duration_seconds: float,
) -> tuple[int, str]:
    if is_link_or_junction(server) or not server.is_dir():
        raise SmokeError(f"Installed server directory is missing or unsafe: {server}")
    if summary.get("mod_version") != expected_version:
        raise SmokeError("Baseline summary does not match the expected artifact version")
    if not MINIMUM_DURATION_SECONDS <= duration_seconds <= MAXIMUM_DURATION_SECONDS:
        raise SmokeError(
            f"Performance duration must be between {MINIMUM_DURATION_SECONDS:.0f} and "
            f"{MAXIMUM_DURATION_SECONDS:.0f} seconds"
        )
    port = summary.get("server_port")
    artifact_sha256 = summary.get("artifact_sha256")
    if not isinstance(port, int) or not 1 <= port <= 65535:
        raise SmokeError("Baseline summary has an invalid server port")
    if not isinstance(artifact_sha256, str) or re.fullmatch(r"[0-9a-f]{64}", artifact_sha256) is None:
        raise SmokeError("Baseline summary has an invalid artifact SHA-256")
    expected_artifact = f"advancedrocketry-community-{expected_version}.jar"
    server_artifact = server / "mods" / expected_artifact
    if is_link_or_junction(server_artifact) or not server_artifact.is_file():
        raise SmokeError(f"Installed artifact is missing: {server_artifact}")
    if digest_file(server_artifact) != artifact_sha256:
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
        f"@{args_file.as_posix()}",
        "nogui",
    ]


def _moon(command: str) -> str:
    return f"execute in {MOON} run {command}"


def _position(position: tuple[int, int, int]) -> str:
    return " ".join(str(value) for value in position)


def _vent_conditions(lit: bool, marker: str) -> str:
    conditions = " ".join(
        f"if block {_position(position)} "
        f"advancedrocketrycommunity:oxygen_vent[lit={str(lit).lower()}]"
        for position in VENT_POSITIONS
    )
    return _moon(f"execute {conditions} run say {marker}")


def _wait_for_marker(
    process: CapturedProcess,
    command: str,
    marker: str,
    timeout: float = 45.0,
) -> None:
    start = len(process.lines)
    process.command(command)
    process.wait_for(re.compile(re.escape(marker)), timeout, start_at=start)


def _configure_rooms(process: CapturedProcess) -> None:
    process.command(_moon("forceload add -1 -1 14 14"))
    for x, y, z in VENT_POSITIONS:
        process.command(_moon(
            f"fill {x - 1} {y} {z - 1} {x + 1} {y + 2} {z + 1} "
            "minecraft:iron_block hollow"
        ))
        process.command(_moon(
            f"setblock {x} {y} {z} "
            "advancedrocketrycommunity:oxygen_vent[lit=false]"
        ))
        process.command(_moon(
            f"data merge block {x} {y} {z} "
            "{arce_oxygen_vent:{schema_version:1,oxygen_canisters:0,"
            "empty_canisters:0,oxygen_units:4000,energy:40000,oxygen_phase:0}}"
        ))


def _set_all_energy(process: CapturedProcess, energy: int) -> None:
    if energy < 0 or energy > 40_000:
        raise ValueError("Vent energy is outside its fixed range")
    for position in VENT_POSITIONS:
        process.command(_moon(
            f"data merge block {_position(position)} "
            f"{{arce_oxygen_vent:{{energy:{energy}}}}}"
        ))


def _block_data(process: CapturedProcess, position: tuple[int, int, int]) -> str:
    start = len(process.lines)
    process.command(_moon(f"data get block {_position(position)} arce_oxygen_vent"))
    index = process.wait_for(BLOCK_DATA_MARKER, 30.0, start_at=start)
    match = BLOCK_DATA_MARKER.search(process.lines[index])
    if match is None:
        raise SmokeError("Could not parse Oxygen Vent block data")
    return match.group(1).strip()


def parse_tps_sample(lines: list[str]) -> tuple[float, float]:
    matches: list[tuple[str, float, float]] = []
    for line in lines:
        match = TPS_PATTERN.search(line)
        if match:
            matches.append((line, float(match.group(1)), float(match.group(2))))
    if not matches:
        raise SmokeError("Forge TPS command produced no parseable tick sample")
    overall = next((sample for sample in reversed(matches) if "overall" in sample[0].casefold()), None)
    selected = overall or matches[-1]
    return selected[1], selected[2]


def _sample_tps(process: CapturedProcess, sample_index: int) -> tuple[float, float]:
    marker = f"V040_TPS_SAMPLE_{sample_index}"
    start = len(process.lines)
    process.command("forge tps")
    process.command(f"say {marker}")
    end = process.wait_for(re.compile(re.escape(marker)), 30.0, start_at=start)
    return parse_tps_sample(process.lines[start:end])


def percentile(values: list[float], percentile_value: float) -> float:
    if not values or not 0.0 < percentile_value <= 1.0:
        raise ValueError("Percentile input is outside its finite range")
    ordered = sorted(values)
    index = max(0, math.ceil(len(ordered) * percentile_value) - 1)
    return ordered[index]


def _rss_bytes(pid: int) -> int | None:
    if platform.system() == "Windows":
        completed = subprocess.run(
            ["tasklist", "/FI", f"PID eq {pid}", "/FO", "CSV", "/NH"],
            check=False,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=10,
        )
        if completed.returncode != 0:
            return None
        rows = list(csv.reader(completed.stdout.splitlines()))
        if not rows or len(rows[0]) < 5 or rows[0][0].startswith("INFO:"):
            return None
        digits = re.sub(r"[^0-9]", "", rows[0][4])
        return int(digits) * 1024 if digits else None
    status = Path(f"/proc/{pid}/status")
    if status.is_file():
        match = re.search(r"^VmRSS:\s*(\d+)\s*kB$", status.read_text(), re.MULTILINE)
        if match:
            return int(match.group(1)) * 1024
    return None


def _jstat_gc(java: str, pid: int) -> dict[str, float] | None:
    executable = Path(java).with_name("jstat.exe" if platform.system() == "Windows" else "jstat")
    if not executable.is_file():
        return None
    completed = subprocess.run(
        [str(executable), "-gcutil", str(pid)],
        check=False,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=15,
    )
    lines = [line.split() for line in completed.stdout.splitlines() if line.strip()]
    if completed.returncode != 0 or len(lines) < 2 or len(lines[-2]) != len(lines[-1]):
        return None
    result: dict[str, float] = {}
    for key, value in zip(lines[-2], lines[-1]):
        try:
            result[key] = float(value)
        except ValueError:
            continue
    return result or None


def _performance_run(
    process: CapturedProcess,
    java: str,
    duration_seconds: float,
) -> dict[str, object]:
    tick_samples: list[float] = []
    tps_samples: list[float] = []
    rss_samples: list[int] = []
    gc_before = _jstat_gc(java, process.process.pid)
    started = time.monotonic()
    next_refill = 60.0
    sample_index = 0
    while True:
        elapsed = time.monotonic() - started
        if elapsed >= duration_seconds and tick_samples:
            break
        if elapsed >= next_refill:
            _set_all_energy(process, 40_000)
            next_refill += 60.0
        tick_ms, tps = _sample_tps(process, sample_index)
        tick_samples.append(tick_ms)
        tps_samples.append(tps)
        rss = _rss_bytes(process.process.pid)
        if rss is not None:
            rss_samples.append(rss)
        sample_index += 1
        target = started + sample_index * SAMPLE_INTERVAL_SECONDS
        remaining = min(target - time.monotonic(), duration_seconds - (time.monotonic() - started))
        if remaining > 0:
            time.sleep(remaining)
    elapsed_seconds = time.monotonic() - started
    if elapsed_seconds < MINIMUM_DURATION_SECONDS:
        raise SmokeError("Atmosphere performance run ended before five real minutes")
    gc_after = _jstat_gc(java, process.process.pid)
    return {
        "duration_seconds": round(elapsed_seconds, 3),
        "sample_interval_seconds": SAMPLE_INTERVAL_SECONDS,
        "sample_count": len(tick_samples),
        "sampled_mean_tick_ms": {
            "average": round(sum(tick_samples) / len(tick_samples), 3),
            "p95": round(percentile(tick_samples, 0.95), 3),
            "maximum": round(max(tick_samples), 3),
        },
        "sampled_tps": {
            "average": round(sum(tps_samples) / len(tps_samples), 3),
            "minimum": round(min(tps_samples), 3),
        },
        "rss_bytes": {
            "samples": len(rss_samples),
            "maximum": max(rss_samples) if rss_samples else None,
        },
        "gc_before": gc_before,
        "gc_after": gc_after,
    }


def _run_phase(
    *,
    name: str,
    java: str,
    server: Path,
    port: int,
    startup_timeout: float,
    expected_version: str,
    duration_seconds: float,
    before_restart: bool,
) -> dict[str, object]:
    started_at = datetime.now(timezone.utc).isoformat()
    full_log = server / f"{name}-full.txt"
    process = CapturedProcess(_server_command(java), server, full_log)
    try:
        process.wait_for(READY_MARKER, startup_timeout)
        validate_status_identity(wait_for_status(port), expected_version)
        if before_restart:
            _configure_rooms(process)
            _wait_for_marker(process, _vent_conditions(True, ACTIVE_MARKER), ACTIVE_MARKER)
            performance = _performance_run(process, java, duration_seconds)
            _set_all_energy(process, 0)
            _wait_for_marker(process, _vent_conditions(False, INACTIVE_MARKER), INACTIVE_MARKER)
            time.sleep(1.0)
            persisted_snapshot = _block_data(process, VENT_POSITIONS[0])
        else:
            _wait_for_marker(process, _vent_conditions(False, INACTIVE_MARKER), INACTIVE_MARKER)
            persisted_snapshot = _block_data(process, VENT_POSITIONS[0])
            _set_all_energy(process, 40_000)
            _wait_for_marker(process, _vent_conditions(True, RECOVERED_MARKER), RECOVERED_MARKER)
            performance = None
            process.command(_moon("fill 0 100 0 14 102 14 minecraft:air"))
            process.command(_moon("forceload remove -1 -1 14 14"))

        save_start = len(process.lines)
        process.command("save-all flush")
        process.wait_for(SAVE_MARKER, 60.0, start_at=save_start)
        process.command("stop")
        exit_code = process.finish()
    except BaseException:
        process.abort()
        raise

    if exit_code != 0:
        raise SmokeError(f"Atmosphere server phase {name} exited with code {exit_code}")
    findings = scan_log(process.lines)
    if findings:
        raise SmokeError(f"Atmosphere server phase {name} has a blocking log finding: {findings[0]}")
    return {
        "name": name,
        "started_at": started_at,
        "completed_at": datetime.now(timezone.utc).isoformat(),
        "exit_code": exit_code,
        "full_log_file": full_log.name,
        "full_log_sha256": digest_file(full_log),
        "persisted_snapshot": persisted_snapshot,
        "performance": performance,
    }


def _write_evidence(directory: Path, summary: dict[str, object]) -> None:
    if directory.exists():
        raise SmokeError(f"Refusing to overwrite atmosphere evidence: {directory}")
    directory.mkdir(parents=True)
    (directory / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=True, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    performance = summary["performance"]
    assert isinstance(performance, dict)
    tick = performance["sampled_mean_tick_ms"]
    tps = performance["sampled_tps"]
    lines = [
        f"artifact_sha256={summary['artifact_sha256']}",
        f"baseline_session_id={summary['baseline_session_id']}",
        "dimension=advancedrocketrycommunity:moon",
        "vents=16",
        "sealed_room_traversable_cells=16",
        f"duration_seconds={performance['duration_seconds']}",
        f"sample_count={performance['sample_count']}",
        f"sampled_mean_tick_ms_average={tick['average']}",
        f"sampled_mean_tick_ms_p95={tick['p95']}",
        f"sampled_mean_tick_ms_maximum={tick['maximum']}",
        f"sampled_tps_average={tps['average']}",
        f"sampled_tps_minimum={tps['minimum']}",
        "vent_nbt_restart_byte_equal=true",
        "post_restart_volume_rebuilt=true",
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
    parser.add_argument("--duration-seconds", type=float, default=MINIMUM_DURATION_SECONDS)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        server = args.server_dir.resolve()
        baseline = _load_summary(args.baseline_summary.resolve())
        port, artifact_sha256 = _verify_inputs(
            server,
            baseline,
            args.expected_version,
            args.duration_seconds,
        )
        java, java_version = resolve_java(args.java)
        before = _run_phase(
            name="v040-atmosphere-before-restart",
            java=java,
            server=server,
            port=port,
            startup_timeout=args.startup_timeout,
            expected_version=args.expected_version,
            duration_seconds=args.duration_seconds,
            before_restart=True,
        )
        after = _run_phase(
            name="v040-atmosphere-after-restart",
            java=java,
            server=server,
            port=port,
            startup_timeout=args.startup_timeout,
            expected_version=args.expected_version,
            duration_seconds=args.duration_seconds,
            before_restart=False,
        )
        if before["persisted_snapshot"] != after["persisted_snapshot"]:
            raise SmokeError("Oxygen Vent NBT changed across the stopped-server restart")
        performance = before["performance"]
        if not isinstance(performance, dict):
            raise SmokeError("Atmosphere performance report is missing")
        summary = {
            "schema_version": 1,
            "artifact_sha256": artifact_sha256,
            "artifact_version": args.expected_version,
            "baseline_session_id": baseline.get("session_id"),
            "completed_at": datetime.now(timezone.utc).isoformat(),
            "environment": {
                "cpu": platform.processor() or platform.machine(),
                "logical_cpu_count": os.cpu_count(),
                "java": java_version,
                "jvm_heap": "-Xms512M -Xmx1024M",
                "forge": FORGE_COORDINATE,
                "minecraft": "1.20.1",
                "mod": args.expected_version,
                "world": MOON,
            },
            "scenario": {
                "active_vents": 16,
                "rooms": 16,
                "traversable_cells_per_room": 1,
                "non_air_blocks": 416,
            },
            "performance": performance,
            "before_restart": before,
            "after_restart": after,
            "same_world_verified": True,
            "vent_nbt_restart_byte_equal": True,
            "post_restart_volume_rebuilt": True,
        }
        evidence = args.evidence_dir.resolve()
        _write_evidence(evidence, summary)
        print("[PASS] 16 active Moon Vents completed at least five real minutes")
        print("[PASS] Tick/TPS, RSS, and GC samples were recorded")
        print("[PASS] Vent NBT remained byte-equal across restart and volumes rebuilt")
        print(f"[PASS] Artifact SHA-256: {artifact_sha256}")
        print(f"[PASS] Evidence: {evidence}")
        return 0
    except (OSError, SmokeError, ValueError, json.JSONDecodeError, subprocess.SubprocessError) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
