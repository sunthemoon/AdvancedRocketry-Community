#!/usr/bin/env python3
"""Run W144 cargo microbenchmarks; missing native measurements always retain a stop verdict."""

from __future__ import annotations

import argparse
import json
import math
import os
from pathlib import Path
import platform
import shutil
import statistics
import subprocess
import time

from run_v040_atmosphere_server_smoke import _rss_bytes

MIB = 1024**2
BUDGETS = dict(mean_ms=25, p95_ms=50, p99_ms=100, maximum_ms=500, rss_bytes=int(5.5 * 1024**3))
PHASES = {"prepare_tick", "capture_tick", "recompute_tick", "readback_hash_io", "visual_encode_main"}
DEVICES = {"fluid_loader", "fluid_unloader", "item_loader", "item_unloader"}
NATIVE = ["native_reference_load", "four_device_cycle_times", "mixed_fuel_cargo_queue", "64_competing_records",
          "native_commit_and_reconciliation_counts", "native_detached_memory", "native_visual_republication",
          "effective_depth_and_node_maxima", "effective_framed_byte_maxima", "timed_no_mutation_refusals"]


def evaluate(report: dict, rss: list[int]) -> dict:
    """Recompute from raw samples; a microbenchmark never supplies native tick/load coverage."""
    missing = list(NATIVE)
    failures = []
    phases = {}
    if report.get("schema_version") != 1 or report.get("environment") != "A0_MICROBENCHMARK":
        missing.append("microbenchmark schema/environment")
    heap = report.get("max_heap_bytes")
    if type(heap) is not int or not 0 < heap <= 4 * 1024**3:
        failures.append("heap")
    if not rss or any(type(value) is not int or value <= 0 for value in rss):
        missing.append("RSS")
    elif max(rss) > BUDGETS["rss_bytes"]:
        failures.append("RSS")
    fixtures = report.get("fixtures", [])
    if {f.get("device") for f in fixtures} != DEVICES or len(fixtures) != 4:
        missing.append("all four device fixtures")
    for fixture in fixtures:
        device = fixture.get("device", "unknown")
        if fixture.get("blocks") != 2048 or fixture.get("payloads") != 128:
            missing.append(device + ": maximum composition")
        if (fixture.get("oversized_aggregate_bytes") != MIB or fixture.get("one_mib_aggregate_refused") is not True
                or fixture.get("adjacent_aggregate_refused") is not True):
            missing.append(device + ": aggregate/adjacent refusal")
        grouped = {}
        operations = {}
        identities = set()
        for row in fixture.get("samples", []):
            phase = row.get("phase")
            value = row.get("wall_ns")
            operation = row.get("operation")
            if phase not in PHASES or type(value) is not int or value < 0 or type(operation) is not int or not 5 <= operation < 55:
                raise ValueError("Invalid cargo sample")
            tick = row.get("tick") if phase.endswith("_tick") else None
            if phase.endswith("_tick") and (type(tick) is not int or not 1 <= tick < 100):
                raise ValueError("Invalid logical tick")
            identity = (phase, operation, tick)
            if identity in identities:
                raise ValueError("Duplicate cargo sample")
            identities.add(identity)
            grouped.setdefault(phase, []).append(value / 1_000_000)
            operations.setdefault(phase, set()).add(operation)
            if phase.endswith("_tick"):
                for field, maximum in (("charged_bytes", 16 * MIB), ("visits", 16_384), ("reconstructions", 1),
                                       ("preparation_retained_native_bytes", 16 * MIB)):
                    if type(row.get(field)) is not int or not 0 <= row[field] <= maximum:
                        failures.append(device + ":" + field)
        for phase in PHASES:
            if operations.get(phase) != set(range(5, 55)):
                missing.append(device + ": complete " + phase)
        for phase, values in grouped.items():
            values.sort()
            summary = dict(count=len(values), mean_ms=statistics.mean(values), p95_ms=values[math.ceil(.95 * len(values)) - 1],
                           p99_ms=values[math.ceil(.99 * len(values)) - 1], maximum_ms=max(values))
            phases[device + ":" + phase] = summary
            if phase != "readback_hash_io":
                failures.extend(device + ":" + phase + ":" + key for key, bound in BUDGETS.items()
                                if key != "rss_bytes" and summary[key] > bound)
    if report.get("status") == "STOP_MEASUREMENT_ERROR":
        missing.append("measurement error: " + str(report.get("error")))
    return dict(status="STOP_BUDGET" if failures else "STOP_MISSING_MEASUREMENT", budget_failures=failures,
                missing_measurements=missing, phases=phases)


def preflight() -> dict:
    if platform.system() != "Windows":
        raise RuntimeError("This development-host runner requires Windows resource auditing")
    script = ("$o=Get-CimInstance Win32_OperatingSystem; $c=Get-CimInstance Win32_Processor; "
              "@{free_memory_bytes=[long]$o.FreePhysicalMemory*1024; total_memory_bytes=[long]$o.TotalVisibleMemorySize*1024; "
              "cpu=@($c|Select-Object Name,NumberOfCores,NumberOfLogicalProcessors); "
              "os=$o.Caption; java=@(Get-CimInstance Win32_Process|Where-Object {$_.Name -in @('java.exe','javaw.exe')}|"
              "Select-Object ProcessId,CommandLine)}|ConvertTo-Json -Depth 5")
    result = subprocess.run(["powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script],
                            capture_output=True, text=True, check=True, timeout=30)
    data = json.loads(result.stdout)
    data["disk_free_bytes"] = {drive: shutil.disk_usage(drive).free for drive in ("C:/", "D:/")}
    if data["free_memory_bytes"] < 6 * 1024**3 or any(value < 10 * 1024**3 for value in data["disk_free_bytes"].values()):
        raise RuntimeError("Requires 6 GiB free memory and 10 GiB free on both C: and D:")
    if data["java"]:
        raise RuntimeError("Other JVMs remain before microbenchmark: " + json.dumps(data["java"]))
    return data


def run(args) -> dict:
    repo = Path(__file__).resolve().parents[1]
    evidence = args.evidence_dir.resolve()
    evidence.mkdir(parents=True, exist_ok=False)
    result = dict(status="STOP_MEASUREMENT_ERROR", source_commit=args.source_commit, reference_machine=False, budgets=BUDGETS)
    try:
        commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip()
        dirty = subprocess.check_output(["git", "status", "--porcelain"], cwd=repo, text=True)
        if dirty or commit != args.source_commit:
            raise RuntimeError("Requires exact clean committed source")
        token = os.environ.get("W144_LOCK_TOKEN", "")
        owner = (Path("D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm") / "owner.txt").read_text(encoding="utf-8-sig")
        if not token.startswith("W144 pid=") or not owner.startswith(token):
            raise RuntimeError("Detached driver does not own the heavy-JVM lock")
        result["host"] = preflight()
        command = [args.java, "-Xms512m", "-Xmx4G", "-XX:+UseG1GC", "-XX:ActiveProcessorCount=4", "-cp",
                   args.classpath_file.read_text(encoding="utf-8").strip(),
                   "io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.CargoBenchmark",
                   str(evidence / "micro.json"), commit]
        result["command"] = command
        rss = []
        foreign_jvms = []
        next_audit = 0.0
        started = time.monotonic()
        with (evidence / "java.log").open("x", encoding="utf-8") as log:
            child = subprocess.Popen(command, cwd=repo, stdout=log, stderr=subprocess.STDOUT)
            try:
                while child.poll() is None:
                    value = _rss_bytes(child.pid)
                    if value is not None:
                        rss.append(value)
                        if value > BUDGETS["rss_bytes"]:
                            raise RuntimeError("RSS budget exceeded")
                    if time.monotonic() >= next_audit:
                        audit = subprocess.run(["powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                            "ConvertTo-Json -InputObject @(Get-CimInstance Win32_Process | Where-Object { $_.Name -in "
                            "@('java.exe','javaw.exe') } | Select-Object ProcessId,CommandLine) -Compress"],
                            capture_output=True, text=True, check=True, timeout=30)
                        foreign_jvms.extend(row for row in json.loads(audit.stdout) if row["ProcessId"] != child.pid)
                        if foreign_jvms:
                            raise RuntimeError("Other JVMs appeared during the microbenchmark")
                        next_audit = time.monotonic() + 10
                    if time.monotonic() - started > 1800:
                        raise RuntimeError("Microbenchmark exceeded 1800-second timeout")
                    if sum(p.stat().st_size for p in evidence.rglob("*") if p.is_file()) >= 90_000_000:
                        raise RuntimeError("Evidence reached 90 MB safety threshold")
                    time.sleep(.25)
            finally:
                if child.poll() is None:
                    child.kill()
                result["exit_code"] = child.wait(timeout=30)
                result.update(rss_bytes=rss, rss_max_bytes=max(rss) if rss else None, foreign_jvms=foreign_jvms,
                              elapsed_seconds=time.monotonic() - started)
        raw = json.loads((evidence / "micro.json").read_text(encoding="utf-8"))
        result.update(evaluate(raw, rss))
        if result["exit_code"] != 0:
            result["status"] = "STOP_MEASUREMENT_ERROR"
        return result
    except Exception as failure:
        result["error"] = str(failure)
        raise
    finally:
        (evidence / "results.json").write_text(json.dumps(result, indent=2), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java", required=True)
    parser.add_argument("--classpath-file", type=Path, required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--source-commit", required=True)
    args = parser.parse_args()
    try:
        print(run(args)["status"], flush=True)
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as failure:
        print("STOP: " + str(failure), flush=True)
    return 1  # No native coverage is produced by this standalone microbenchmark driver.


if __name__ == "__main__":
    raise SystemExit(main())
