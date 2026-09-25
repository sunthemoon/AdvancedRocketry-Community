#!/usr/bin/env python3
"""Exercise packaged Rolling Machine persistence through a forced stop and restart."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable

if __package__:
    from .run_dedicated_server_smoke import (
        READY_MARKER,
        SAVE_MARKER,
        CapturedProcess,
        SmokeError,
        complete_world_identity,
        digest_file,
        is_link_or_junction,
        resolve_java,
        scan_log,
    )
    from .run_v060_flight_server_smoke import FlightHarness, _load_summary, _verify_inputs
else:
    from run_dedicated_server_smoke import (
        READY_MARKER,
        SAVE_MARKER,
        CapturedProcess,
        SmokeError,
        complete_world_identity,
        digest_file,
        is_link_or_junction,
        resolve_java,
        scan_log,
    )
    from run_v060_flight_server_smoke import FlightHarness, _load_summary, _verify_inputs


EXPECTED_VERSION = "1.20.1-1.1.1-dev"
X = 132
Y = 80
Z = 132
POSITION = f"{X} {Y} {Z}"
POSITION_COMPACT = f"{X},{Y},{Z}"
IRON_INGOT = "minecraft:iron_ingot"
IRON_BARS = "minecraft:iron_bars"
AIR = "minecraft:air"
SEEDED_RESOURCE_REVISION = 6
COMPLETED_RESOURCE_REVISION = 7
PREPARE_MARKER = f"ARCE_RELEASE_TEST_ROLLING_PREPARE controller={POSITION_COMPACT} cells=30"
SEED_MARKER = (
    f"ARCE_RELEASE_TEST_ROLLING_SEED controller={POSITION_COMPACT} "
    "input=2 water=500 energy=4000"
)
PAUSE_MARKER = f"ARCE_RELEASE_TEST_ROLLING_POWER controller={POSITION_COMPACT} paused=true"
RESUME_MARKER = f"ARCE_RELEASE_TEST_ROLLING_POWER controller={POSITION_COMPACT} paused=false"
REPORT_LOG = re.compile(
    rf"ARCE_RELEASE_TEST_ROLLING_REPORT controller={re.escape(POSITION_COMPACT)} "
    r"formation=([A-Z_]+) generation=(\d+) process=([A-Z_]+) failure=([A-Z_]+) "
    r"progress=(\d+) total=(\d+) input_item=([^ ]+) input_count=(-?\d+) "
    r"water=(-?\d+) energy=(-?\d+) output_item=([^ ]+) output_count=(-?\d+) "
    r"revision=(\d+) journal=([^ ]+) last_applied=([^ ]+)"
)
UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")


def _command_marker(
    process: CapturedProcess,
    command: str,
    marker: str,
    timeout: float = 30.0,
) -> None:
    start = len(process.lines)
    process.command(command)
    process.wait_for(re.compile(re.escape(marker)), timeout, start_at=start)


def _report(process: CapturedProcess) -> dict[str, object]:
    start = len(process.lines)
    process.command(f"arce rolling release-test report {POSITION}")
    index = process.wait_for(REPORT_LOG, 30.0, start_at=start)
    match = REPORT_LOG.search(process.lines[index])
    if match is None:
        raise SmokeError("Could not parse packaged Rolling Machine report")
    (
        formation,
        generation,
        state,
        failure,
        progress,
        total,
        input_item,
        input_count,
        water,
        energy,
        output_item,
        output_count,
        revision,
        journal,
        last_applied,
    ) = match.groups()
    return {
        "formation": formation,
        "generation": int(generation),
        "state": state,
        "failure": failure,
        "progress": int(progress),
        "total": int(total),
        "input_item": input_item,
        "input_count": int(input_count),
        "water": int(water),
        "energy": int(energy),
        "output_item": output_item,
        "output_count": int(output_count),
        "revision": int(revision),
        "journal": journal,
        "last_applied": last_applied,
    }


def _wait_report(
    process: CapturedProcess,
    predicate: Callable[[dict[str, object]], bool],
    description: str,
    timeout: float = 30.0,
) -> dict[str, object]:
    deadline = time.monotonic() + timeout
    last: dict[str, object] | None = None
    while time.monotonic() < deadline:
        last = _report(process)
        if predicate(last):
            return last
        time.sleep(0.1)
    raise SmokeError(f"Timed out waiting for {description}; last report: {last}")


def _assert_active(report: dict[str, object], *, paused: bool) -> None:
    progress = int(report["progress"])
    expected_state = "REDSTONE_DISABLED" if paused else "RUNNING"
    expected_failure = "REDSTONE_DISABLED" if paused else "NONE"
    if (
        report["formation"] != "FORMED"
        or report["state"] != expected_state
        or report["failure"] != expected_failure
        or not 1 <= progress < 100
        or report["total"] != 100
        or report["input_item"] != IRON_INGOT
        or report["input_count"] != 2
        or report["water"] != 500
        or report["energy"] != 4_000 - progress * 20
        or report["output_item"] != AIR
        or report["output_count"] != 0
        or report["revision"] != SEEDED_RESOURCE_REVISION
        or report["journal"] != "none"
        or report["last_applied"] != "none"
    ):
        raise SmokeError(f"Active Rolling Machine resources are inconsistent: {report}")


def _assert_complete(report: dict[str, object], last_applied: str | None = None) -> None:
    marker = str(report["last_applied"])
    if (
        report["formation"] != "FORMED"
        or report["state"] != "IDLE"
        or report["failure"] != "NONE"
        or report["progress"] != 0
        or report["input_item"] != AIR
        or report["input_count"] != 0
        or report["water"] != 400
        or report["energy"] != 2_000
        or report["output_item"] != IRON_BARS
        or report["output_count"] != 8
        or report["revision"] != COMPLETED_RESOURCE_REVISION
        or report["journal"] != "none"
        or UUID.fullmatch(marker) is None
        or (last_applied is not None and marker != last_applied)
    ):
        raise SmokeError(f"Completed Rolling Machine resources are inconsistent: {report}")


def _record_forced_stop(
    harness: FlightHarness,
    process: CapturedProcess,
    *,
    durable_region_sha256: str,
) -> int:
    process.process.kill()
    exit_code = process.finish()
    if exit_code == 0:
        raise SmokeError("Forced-stop server exited successfully instead of recording termination")
    findings = scan_log(process.lines)
    if findings:
        raise SmokeError(f"Forced-stop server has a blocking log finding: {findings[0]}")
    log_path: Path = getattr(process, "_arce_log_path")
    harness.process_documents.append({
        "name": getattr(process, "_arce_name"),
        "started_at": getattr(process, "_arce_started_at"),
        "completed_at": datetime.now(timezone.utc).isoformat(),
        "exit_code": exit_code,
        "forced": True,
        "graceful_stop_command_sent": False,
        "durable_save_observed": True,
        "durable_region_sha256": durable_region_sha256,
        "full_log_file": log_path.name,
        "full_log_sha256": digest_file(log_path),
    })
    harness.filtered_lines.extend(
        line.rstrip()
        for line in process.lines
        if "ARCE_" in line or READY_MARKER.search(line) or SAVE_MARKER.search(line)
    )
    return exit_code


def _write_evidence(
    directory: Path,
    summary: dict[str, object],
    filtered_lines: list[str],
) -> None:
    if directory.exists():
        raise SmokeError(f"Refusing to overwrite Rolling evidence directory: {directory}")
    directory.mkdir(parents=True)
    (directory / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=True, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    (directory / "filtered-lifecycle.log").write_text(
        "\n".join(filtered_lines) + "\n",
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
    parser.add_argument("--tested-commit", required=True)
    parser.add_argument("--java", default=default_java)
    parser.add_argument("--expected-version", default=EXPECTED_VERSION)
    parser.add_argument("--startup-timeout", type=float, default=240.0)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    process: CapturedProcess | None = None
    try:
        if re.fullmatch(r"[0-9a-f]{40}", args.tested_commit) is None:
            raise SmokeError("Tested commit must be a full lowercase Git SHA-1")
        server = args.server_dir.resolve()
        baseline = _load_summary(args.baseline_summary.resolve())
        port, artifact_sha256 = _verify_inputs(server, baseline, args.expected_version)
        baseline_world = baseline.get("world")
        if not isinstance(baseline_world, dict) or baseline_world.get("same_world_verified") is not True:
            raise SmokeError("Baseline dedicated-server summary lacks same-world evidence")
        java, java_version = resolve_java(args.java)
        harness = FlightHarness(
            java=java,
            server=server,
            port=port,
            expected_version=args.expected_version,
            startup_timeout=args.startup_timeout,
        )

        process = harness.start("v120-rolling-forced-stop")
        process.command(f"forceload add {X} {Z}")
        _command_marker(
            process,
            f"arce rolling release-test prepare {POSITION}",
            PREPARE_MARKER,
        )
        initial = _wait_report(
            process,
            lambda value: value["formation"] == "FORMED",
            "formed Rolling Machine fixture",
        )
        if initial["generation"] != 1:
            raise SmokeError(f"Fresh Rolling fixture has an unexpected generation: {initial}")
        _command_marker(
            process,
            f"arce rolling release-test seed {POSITION}",
            SEED_MARKER,
        )
        running = _wait_report(
            process,
            lambda value: value["state"] == "RUNNING" and 5 <= int(value["progress"]) <= 60,
            "bounded active Rolling progress",
        )
        _assert_active(running, paused=False)
        _command_marker(
            process,
            f"arce rolling release-test pause {POSITION}",
            PAUSE_MARKER,
        )
        before_stop = _wait_report(
            process,
            lambda value: value["state"] == "REDSTONE_DISABLED",
            "redstone-paused Rolling progress",
        )
        _assert_active(before_stop, paused=True)

        save_start = len(process.lines)
        process.command("save-all flush")
        process.wait_for(SAVE_MARKER, 60.0, start_at=save_start)
        region = server / "world" / "region" / "r.0.0.mca"
        if is_link_or_junction(region) or not region.is_file():
            raise SmokeError("Durable Rolling fixture region file is missing after save-all flush")
        region_before_stop = {
            "path": "world/region/r.0.0.mca",
            "sha256": digest_file(region),
            "size": region.stat().st_size,
        }
        forced_exit_code = _record_forced_stop(
            harness,
            process,
            durable_region_sha256=str(region_before_stop["sha256"]),
        )
        process = None

        process = harness.start("v120-rolling-recovery")
        after_restart = _wait_report(
            process,
            lambda value: value["formation"] == "FORMED"
            and value["state"] == "REDSTONE_DISABLED",
            "same-world paused Rolling restart",
        )
        _assert_active(after_restart, paused=True)
        if after_restart != before_stop:
            raise SmokeError(
                "Forced-stop restart changed paused Rolling authority: "
                f"before={before_stop}, after={after_restart}"
            )
        _command_marker(
            process,
            f"arce rolling release-test resume {POSITION}",
            RESUME_MARKER,
        )
        completed = _wait_report(
            process,
            lambda value: value["output_item"] == IRON_BARS
            and value["output_count"] == 8,
            "single completed Rolling batch",
        )
        _assert_complete(completed)
        time.sleep(1.0)
        stable = _report(process)
        _assert_complete(stable, str(completed["last_applied"]))
        if stable != completed:
            raise SmokeError(
                f"Completed Rolling batch changed without new input: {completed} -> {stable}"
            )
        harness.stop(process)
        process = None

        process = harness.start("v120-rolling-final-restart")
        final_restart = _wait_report(
            process,
            lambda value: value["formation"] == "FORMED"
            and value["output_item"] == IRON_BARS,
            "completed Rolling final restart",
        )
        _assert_complete(final_restart, str(completed["last_applied"]))
        if final_restart != completed:
            raise SmokeError(
                f"Final restart changed completed Rolling authority: {completed} -> {final_restart}"
            )
        harness.stop(process)
        process = None

        world = complete_world_identity(server, baseline_world)
        region_after_restart = {
            "path": "world/region/r.0.0.mca",
            "sha256": digest_file(region),
            "size": region.stat().st_size,
        }
        summary = {
            "schema_version": 1,
            "version": "v1.2.0",
            "slice": "V120-ROLL-05",
            "build": args.expected_version,
            "artifact_sha256": artifact_sha256,
            "tested_implementation_commit": args.tested_commit,
            "completed_at": datetime.now(timezone.utc).isoformat(),
            "java": java_version,
            "port": port,
            "same_world_verified": True,
            "durable_save_before_forced_stop": True,
            "graceful_stop_command_sent_before_kill": False,
            "forced_exit_code": forced_exit_code,
            "progress_preserved_exactly": after_restart["progress"] == before_stop["progress"],
            "exact_once_output_verified": True,
            "expected_output_count": 8,
            "world": world,
            "region_before_forced_stop": region_before_stop,
            "region_after_final_restart": region_after_restart,
            "before_forced_stop": before_stop,
            "after_forced_stop_restart": after_restart,
            "completed": completed,
            "stable_after_completion": stable,
            "after_final_restart": final_restart,
            "processes": harness.process_documents,
        }
        evidence = args.evidence_dir.resolve()
        _write_evidence(evidence, summary, harness.filtered_lines)
        print("[PASS] Packaged Rolling progress survived a durable-save forced stop")
        print("[PASS] Same-world restart preserved exact Item/Fluid/Energy state")
        print("[PASS] Resume produced exactly eight iron bars and one replay marker")
        print("[PASS] Idle wait and final restart produced no duplicate output")
        print(f"[PASS] Artifact SHA-256: {artifact_sha256}")
        print(f"[PASS] Evidence: {evidence}")
        return 0
    except (OSError, SmokeError, ValueError, json.JSONDecodeError) as exc:
        if process is not None:
            process.abort()
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
