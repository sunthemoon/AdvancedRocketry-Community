#!/usr/bin/env python3
"""Check one cross-chunk Precision Assembler on a packaged, disposable server."""

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
        READY_MARKER, SAVE_MARKER, CapturedProcess, SmokeError,
        complete_world_identity, digest_file, is_link_or_junction,
        resolve_java, scan_log,
    )
    from .run_v060_flight_server_smoke import FlightHarness, _load_summary, _verify_inputs
else:
    from run_dedicated_server_smoke import (
        READY_MARKER, SAVE_MARKER, CapturedProcess, SmokeError,
        complete_world_identity, digest_file, is_link_or_junction,
        resolve_java, scan_log,
    )
    from run_v060_flight_server_smoke import FlightHarness, _load_summary, _verify_inputs


EXPECTED_VERSION = "1.20.1-1.1.1-dev"
POSITION = "143 80 143"
POSITION_COMPACT = "143,80,143"
EMPTY = "minecraft:air:0"
INPUTS_SEEDED = ("minecraft:iron_ingot:2", "minecraft:redstone:2", EMPTY, EMPTY, EMPTY)
INPUTS_EMPTY = (EMPTY,) * 5
OUTPUTS_EMPTY = (EMPTY,) * 2
OUTPUTS_COMPLETE = ("advancedrocketrycommunity:advanced_circuit:1", "minecraft:redstone_torch:2")
SEEDED_REVISION = 4
COMPLETED_REVISION = 5
PREPARE_MARKER = f"ARCE_RELEASE_TEST_PRECISION_PREPARE controller={POSITION_COMPACT} cells=36"
SEED_MARKER = f"ARCE_RELEASE_TEST_PRECISION_SEED controller={POSITION_COMPACT} iron=2 redstone=2 energy=1600"
PAUSE_MARKER = f"ARCE_RELEASE_TEST_PRECISION_POWER controller={POSITION_COMPACT} paused=true"
RESUME_MARKER = f"ARCE_RELEASE_TEST_PRECISION_POWER controller={POSITION_COMPACT} paused=false"
REPORT_LOG = re.compile(
    rf"ARCE_RELEASE_TEST_PRECISION_REPORT controller={re.escape(POSITION_COMPACT)} "
    r"formation=([A-Z_]+) generation=(\d+) process=([A-Z_]+) failure=([A-Z_]+) "
    r"progress=(\d+) total=(\d+) inputs=([^ ]+) outputs=([^ ]+) "
    r"energy=(-?\d+) revision=(\d+) journal=([^ ]+) last_applied=([^ ]+)"
)
UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")


def _command_marker(process: CapturedProcess, command: str, marker: str) -> None:
    start = len(process.lines)
    process.command(command)
    process.wait_for(re.compile(re.escape(marker)), 30.0, start_at=start)


def _report(process: CapturedProcess) -> dict[str, object]:
    start = len(process.lines)
    process.command(f"arce precision release-test report {POSITION}")
    index = process.wait_for(REPORT_LOG, 30.0, start_at=start)
    match = REPORT_LOG.search(process.lines[index].strip())
    if match is None:
        raise SmokeError("Could not parse packaged Precision Assembler report")
    (formation, generation, state, failure, progress, total, inputs, outputs,
     energy, revision, journal, last_applied) = match.groups()
    parsed_inputs = tuple(inputs.split(","))
    parsed_outputs = tuple(outputs.split(","))
    if len(parsed_inputs) != 5 or len(parsed_outputs) != 2:
        raise SmokeError("Precision Assembler report has an invalid port count")
    return {
        "formation": formation, "generation": int(generation),
        "state": state, "failure": failure, "progress": int(progress),
        "total": int(total), "inputs": parsed_inputs, "outputs": parsed_outputs,
        "energy": int(energy), "revision": int(revision),
        "journal": journal, "last_applied": last_applied,
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
        time.sleep(0.05)
    raise SmokeError(f"Timed out waiting for {description}; last report: {last}")


def _assert_active(report: dict[str, object], *, paused: bool) -> None:
    progress = int(report["progress"])
    expected = "REDSTONE_DISABLED" if paused else "RUNNING"
    failure = "REDSTONE_DISABLED" if paused else "NONE"
    if (
        report["formation"] != "FORMED"
        or report["state"] != expected or report["failure"] != failure
        or not 1 <= progress < 20 or report["total"] != 20
        or report["inputs"] != INPUTS_SEEDED or report["outputs"] != OUTPUTS_EMPTY
        or report["energy"] != 1_600 - progress * 40
        or report["revision"] != SEEDED_REVISION
        or report["journal"] != "none" or report["last_applied"] != "none"
    ):
        raise SmokeError(f"Active Precision resources are inconsistent: {report}")


def _assert_complete(report: dict[str, object], last_applied: str | None = None) -> None:
    marker = str(report["last_applied"])
    if (
        report["formation"] != "FORMED"
        or report["state"] != "IDLE" or report["failure"] != "NONE"
        or report["progress"] != 0 or report["total"] != 0
        or report["inputs"] != INPUTS_EMPTY or report["outputs"] != OUTPUTS_COMPLETE
        or report["energy"] != 800 or report["revision"] != COMPLETED_REVISION
        or report["journal"] != "none" or UUID.fullmatch(marker) is None
        or (last_applied is not None and marker != last_applied)
    ):
        raise SmokeError(f"Completed Precision resources are inconsistent: {report}")


def _record_forced_stop(harness: FlightHarness, process: CapturedProcess, region_sha: str) -> int:
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
        "exit_code": exit_code, "forced": True,
        "graceful_stop_command_sent": False, "durable_save_observed": True,
        "durable_region_sha256": region_sha,
        "full_log_file": log_path.name, "full_log_sha256": digest_file(log_path),
    })
    harness.filtered_lines.extend(
        line.rstrip() for line in process.lines
        if "ARCE_" in line or READY_MARKER.search(line) or SAVE_MARKER.search(line)
    )
    return exit_code


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
            java=java, server=server, port=port,
            expected_version=args.expected_version, startup_timeout=args.startup_timeout,
        )

        process = harness.start("v120-precision-forced-stop")
        process.command("forceload add 142 143 144 146")
        _command_marker(process, f"arce precision release-test prepare {POSITION}", PREPARE_MARKER)
        initial = _wait_report(process, lambda value: value["formation"] == "FORMED", "formed fixture")
        if initial["generation"] != 1:
            raise SmokeError(f"Fresh Precision fixture has an unexpected generation: {initial}")
        _command_marker(process, f"arce precision release-test seed {POSITION}", SEED_MARKER)
        running = _wait_report(
            process, lambda value: value["state"] == "RUNNING"
            and 1 <= int(value["progress"]) < 20, "active process",
        )
        _assert_active(running, paused=False)
        _command_marker(process, f"arce precision release-test pause {POSITION}", PAUSE_MARKER)
        before_stop = _wait_report(
            process, lambda value: value["state"] == "REDSTONE_DISABLED", "paused process",
        )
        _assert_active(before_stop, paused=True)

        save_start = len(process.lines)
        process.command("save-all flush")
        process.wait_for(SAVE_MARKER, 60.0, start_at=save_start)
        region = server / "world" / "region" / "r.0.0.mca"
        if is_link_or_junction(region) or not region.is_file():
            raise SmokeError("Durable Precision fixture region file is missing after save-all flush")
        region_before_stop = {
            "path": "world/region/r.0.0.mca",
            "sha256": digest_file(region), "size": region.stat().st_size,
        }
        forced_exit_code = _record_forced_stop(harness, process, str(region_before_stop["sha256"]))
        process = None

        process = harness.start("v120-precision-recovery")
        after_restart = _wait_report(
            process, lambda value: value["formation"] == "FORMED"
            and value["state"] == "REDSTONE_DISABLED", "same-world paused restart",
        )
        _assert_active(after_restart, paused=True)
        if after_restart != before_stop:
            raise SmokeError(f"Forced-stop restart changed Precision state: {before_stop} -> {after_restart}")
        _command_marker(process, f"arce precision release-test resume {POSITION}", RESUME_MARKER)
        completed = _wait_report(
            process, lambda value: value["outputs"] == OUTPUTS_COMPLETE, "single completed batch",
        )
        _assert_complete(completed)
        time.sleep(1.0)
        stable = _report(process)
        _assert_complete(stable, str(completed["last_applied"]))
        if stable != completed:
            raise SmokeError(f"Completed Precision batch changed without input: {completed} -> {stable}")
        harness.stop(process)
        process = None

        process = harness.start("v120-precision-final-restart")
        final_restart = _wait_report(
            process, lambda value: value["formation"] == "FORMED"
            and value["outputs"] == OUTPUTS_COMPLETE, "completed final restart",
        )
        _assert_complete(final_restart, str(completed["last_applied"]))
        if final_restart != completed:
            raise SmokeError(f"Final restart changed Precision resources: {completed} -> {final_restart}")
        harness.stop(process)
        process = None

        evidence = args.evidence_dir.resolve()
        if evidence.exists():
            raise SmokeError(f"Refusing to overwrite Precision evidence directory: {evidence}")
        evidence.mkdir(parents=True)
        summary = {
            "schema_version": 1, "version": "v1.2.0", "slice": "V120-PREC-06",
            "build": args.expected_version, "artifact_sha256": artifact_sha256,
            "tested_implementation_commit": args.tested_commit,
            "completed_at": datetime.now(timezone.utc).isoformat(),
            "java": java_version, "port": port, "same_world_verified": True,
            "fixture_crosses_chunk_boundary": True,
            "durable_save_before_forced_stop": True,
            "graceful_stop_command_sent_before_kill": False,
            "forced_exit_code": forced_exit_code,
            "progress_preserved_exactly": after_restart["progress"] == before_stop["progress"],
            "exact_once_output_verified": True, "world": complete_world_identity(server, baseline_world),
            "region_before_forced_stop": region_before_stop,
            "region_after_final_restart": {
                "path": "world/region/r.0.0.mca", "sha256": digest_file(region),
                "size": region.stat().st_size,
            },
            "before_forced_stop": before_stop, "after_forced_stop_restart": after_restart,
            "completed": completed, "stable_after_completion": stable,
            "after_final_restart": final_restart, "processes": harness.process_documents,
        }
        (evidence / "summary.json").write_text(
            json.dumps(summary, ensure_ascii=True, indent=2, sort_keys=True) + "\n", encoding="utf-8",
        )
        (evidence / "filtered-lifecycle.log").write_text(
            "\n".join(harness.filtered_lines) + "\n", encoding="utf-8",
        )
        print("[PASS] Packaged cross-chunk Precision progress survived a saved forced stop")
        print("[PASS] Same-world resume produced one batch and no duplicate after final restart")
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
