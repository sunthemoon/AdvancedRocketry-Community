#!/usr/bin/env python3
"""W144 native pressure/reference stages, called only by the owning detached lock driver."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import threading
import time
import uuid

import run_v180_cargo_benchmark as micro
import run_v180_escrow_benchmark as pressure
import run_v180_governed_benchmark as governed
import run_v180_escrow_forced_stop as escrow

smoke = escrow.smoke
DONE = re.compile(r"ARCE_CARGO_BENCH_DONE status=(\S+)")
SAVE_TRACE_DONE = re.compile(r"ARCE_CARGO_SAVE_TRACE_DONE status=(\S+)")
RECOVERY_DONE = re.compile(r"ARCE_CARGO_RECOVERY_DONE restart=([12]) status=(\S+)")
ROOT = Path("D:/GitHub/ARCE-Task-Evidence/root-queue/W144")
TEMP = Path("D:/GitHub/ARCE-Task-Evidence/Temp")


def probe_flags(case: str, stage: str, commit: str, diagnose_saves: bool = False) -> list[str]:
    flags = ["-Dadvancedrocketrycommunity.benchmarkCommit=" + commit]
    if case == "pressure":
        # No escrowScenario: leave historical BENCH and generic release-probe routing alone.
        flags.append("-Dadvancedrocketrycommunity.cargoPressureBenchmark=true")
    else:
        flags += ["-Dadvancedrocketrycommunity.escrowCase=GOVERNED_BENCH",
                  "-Dadvancedrocketrycommunity.escrowScenario=" + ("recover" if stage.startswith("recover") else stage),
                  "-Dadvancedrocketrycommunity.cargoBenchmark=true"]
        if stage.startswith("recover"):
            if stage not in ("recover-1", "recover-2"):
                raise ValueError("Cargo recovery index must be 1 or 2")
            flags.append("-Dadvancedrocketrycommunity.cargoRecoveryIndex=" + stage.removeprefix("recover-"))
    if diagnose_saves:
        if case != "reference" or stage != "start":
            raise ValueError("Save diagnosis is an observation-only reference start")
        flags.append("-Dadvancedrocketrycommunity.cargoSaveTrace=true")
    return flags


def native_stages(case: str, diagnose_saves: bool = False) -> list:
    if diagnose_saves:
        if case != "reference":
            raise ValueError("Save diagnosis requires reference load")
        return [("start", SAVE_TRACE_DONE)]
    return [("start", pressure.DONE)] if case == "pressure" else [("start", DONE), ("recover-1", RECOVERY_DONE), ("recover-2", RECOVERY_DONE)]


def recovery_coverage(report: dict, index: int, baseline: dict, commit: str) -> list[str]:
    expected = {"ledger"} | {f"{kind}_{i}" for kind in ("device", "rocket") for i in range(12)}
    state = report.get("state", {})
    return [] if (report.get("status") == "MATCHED" and report.get("restart") == index
                  and report.get("source_commit") == baseline.get("source_commit") == commit
                  and baseline.get("schema_version") == 1 and set(state) == expected
                  and state == baseline.get("state") and report.get("governed_flights_verified") is True
                  and type(report.get("recovery_ns")) is int and report["recovery_ns"] > 0
                  and type(report.get("recovery_ticks")) is int and report["recovery_ticks"] > 0) else [f"restart {index}: incomplete or changed state"]


def pressure_coverage(raw: dict) -> list[str]:
    gaps = []
    expected = dict(schema_version=1, status="NATIVE_MEASURED", requested_records=64, attempted_requests=64,
                    requested_interpretation="competing_requests", retained_records=3,
                    payload_millibuckets=64000, component_sizes=[4, 4] + [1] * 56)
    for key, value in expected.items():
        if raw.get(key) != value:
            gaps.append("pressure:" + key)
    sizes = raw.get("operation_bytes", [])
    if len(sizes) != 64 or any(type(n) is not int or n <= 0 for n in sizes) or sizes != raw.get("historical_operation_bytes"):
        gaps.append("pressure:unchanged operation byte load")
    if raw.get("elapsed_ticks", 0) < 1400 or raw.get("heap_max_bytes", 2**63) > 4 * 1024**3:
        gaps.append("pressure:tick window or heap")
    capacity = raw.get("effective_capacity")
    if type(capacity) is not int or not 11 < capacity < 64:
        return gaps + ["pressure:effective capacity"]
    for key, value in (("admitted_records", capacity), ("acknowledged_records", capacity), ("retired_records", capacity - 3)):
        if raw.get(key) != value:
            gaps.append("pressure:" + key)
    refusals = raw.get("refusals", [])
    if len(refusals) != 64 - capacity or {r.get("request") for r in refusals} != set(range(capacity + 1, 65)):
        gaps.append("pressure:all overflow requests accounted for")
    limit, headroom = raw.get("root_limit_bytes", 0), raw.get("floor_retirement_headroom_bytes", 0)
    if limit != micro.MIB or headroom != 15360 or not 0 < raw.get("admitted_reserved_bytes", 0) <= limit - headroom:
        gaps.append("pressure:admitted reservation and unchanged root budget")
    for row in refusals:
        if (row.get("status") != "ReservationFull" or row.get("unmutated") is not True
                or row.get("sequence") != capacity + 1 or type(row.get("wall_ns")) is not int or row["wall_ns"] <= 0
                or row.get("retirement_headroom_bytes") != headroom
                or row.get("candidate_reserved_bytes", 0) + headroom <= limit):
            gaps.append("pressure:refusal classification, bytes, mutation or cost")
    return gaps


def pressure_costs(phases: dict) -> dict:
    checks = {}
    for phase in ("admission_main", "admission_refusal_main"):
        row = phases.get(phase, {})
        for metric, limit in (("mean", 25), ("p95", 50), ("p99", 100), ("maximum", 500)):
            checks[phase + ":" + metric] = row.get(metric, float("inf")) <= limit
    return checks


def native_coverage(raw: dict) -> list[str]:
    gaps = list(raw.get("measurement_failures", []))
    if raw.get("schema_version") != 1 or raw.get("status") != "NATIVE_MEASURED":
        gaps.append("native probe did not complete")
    if raw.get("heap_max_bytes", 2**63) > 4 * 1024**3:
        gaps.append("heap bound")
    devices = raw.get("devices", [])
    if len(devices) != 8 or {(d.get("device"), d.get("large")) for d in devices} != {
            (name, large) for name in micro.DEVICES for large in (False, True)}:
        gaps.append("all four real device kinds at both sizes")
    for d in devices:
        for name in ("resources_verified", "updated_visual", "adjacent_codec_refused"):
            if d.get(name) is not True:
                gaps.append(f"{d.get('device')}:{name}")
        if d.get("large") and (d.get("blocks") != 2048 or d.get("payloads") != 128):
            gaps.append("native maximum composition")
        if not all(type(d.get(key)) is int and d[key] > 0 for key in ("cycle_ticks", "cycle_ns")):
            gaps.append("device cycle missing")
    if raw.get("fuel_device_epochs") != [1, 1] or raw.get("mixed_queue_start", 0) < 2:
        gaps.append("mixed fuel/cargo queue")
    refused = raw.get("item_nbt_refusals", [])
    if len(refused) != 2 or any(r.get("status") != "CARGO_TOO_LARGE" or r.get("unmutated") is not True for r in refused):
        gaps.append("real item NBT refusal without mutation")
    if len(raw.get("ticks", [])) < 1400:
        gaps.append("native tick window")
    return gaps


def native_budgets(raw: dict) -> list[str]:
    failures = []
    seen = set()
    for row in raw.get("ticks", []):
        tick = row.get("tick")
        if type(tick) is not int or tick in seen:
            raise ValueError("Invalid/duplicate native tick")
        seen.add(tick)
        for key, bound in (("charged_bytes", 16 * micro.MIB), ("visits", 16384), ("reconstructions", 1),
                           ("commits", 64), ("reconciliations", 8), ("detached_capture_bytes", 16 * micro.MIB)):
            if type(row.get(key)) is not int or not 0 <= row[key] <= bound:
                failures.append(f"tick {tick}:{key}")
    return failures


def run(args) -> dict:
    repo = Path(__file__).resolve().parents[1]
    evidence = args.evidence_dir.resolve()
    if ROOT.resolve() not in evidence.parents or evidence.exists():
        raise ValueError("Evidence must be new under W144")
    evidence.mkdir(parents=True)
    session = TEMP / ("W144-" + uuid.uuid4().hex)
    marker = session / ".w144-owned"
    token = uuid.uuid4().hex
    report = dict(status="STOP_MEASUREMENT_ERROR", source_commit=args.source_commit, reference_machine=False,
                  case=args.case, budgets=governed.BUDGETS, sessions=[])
    process = None
    try:
        actual = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip()
        if actual != args.source_commit or subprocess.check_output(["git", "status", "--porcelain"], cwd=repo, text=True):
            raise ValueError("Native benchmark requires exact clean committed source")
        lock_token = os.environ.get("W144_LOCK_TOKEN", "")
        owner = Path("D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm/owner.txt").read_text(encoding="utf-8-sig")
        if not lock_token.startswith("W144 pid=") or not owner.startswith(lock_token):
            raise ValueError("Not owned by the detached W144 driver")
        report["host"] = micro.preflight()
        report["jar_sha256"] = escrow.digest(args.jar)
        libraries = args.source_server.resolve() / "libraries"
        argument_file = libraries / "net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt"
        if not argument_file.is_file() or any(p.is_symlink() for p in libraries.rglob("*")):
            raise ValueError("Missing/linked pinned native libraries")
        required = set(re.findall(r"libraries/[^\s;:]+\.jar", argument_file.read_text(encoding="utf-8")))
        if not required or any(not (args.source_server / name).is_file() for name in required):
            raise ValueError("Incomplete native libraries")
        report["library_manifest"] = {name: escrow.digest(args.source_server / name) for name in sorted(required)}
        session.mkdir(); marker.write_text(token, encoding="ascii")
        shutil.copytree(libraries, session / "libraries"); (session / "mods").mkdir()
        shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = smoke.allocate_port(); smoke.write_server_configuration(session, port, True)
        props = smoke.expected_server_properties(port, True)
        props.update({"sync-chunk-writes": "true", "level-name": "world", "level-type": "minecraft:flat", "max-tick-time": "60000",
                      "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1},
                          {"block": "minecraft:dirt", "height": 2}, {"block": "minecraft:grass_block", "height": 1}],
                          "biome": "minecraft:plains"}, separators=(",", ":"))})
        (session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(props.items())), encoding="ascii")
        diagnose_saves = getattr(args, "diagnose_saves", False)
        report["diagnostic_only"] = diagnose_saves
        stages = native_stages(args.case, diagnose_saves)
        for stage, pattern in stages:
            report.setdefault("preflights", []).append(micro.preflight())
            command = escrow._server_command(args.java); command[2] = "-Xmx4G"
            command[1:1] = ["-XX:ActiveProcessorCount=4", "-XX:+UseG1GC"] + probe_flags(args.case, stage, args.source_commit, diagnose_saves)
            process = smoke.CapturedProcess(command, session, evidence / (stage + ".log"))
            launched = time.monotonic()
            ready_seconds = None
            stop = threading.Event(); rss = []; audits = []; errors = []

            def monitor():
                next_audit = 0
                try:
                    while not stop.is_set():
                        value = micro._rss_bytes(process.process.pid)
                        if value is not None:
                            rss.append(value)
                            if value > governed.BUDGETS["rss_bytes"]:
                                errors.append("RSS budget exceeded"); return
                        if time.monotonic() >= next_audit:
                            rows = micro.powershell_json("ConvertTo-Json -InputObject @(Get-CimInstance Win32_Process | "
                                "Where-Object {$_.Name -in @('java.exe','javaw.exe')} | Select-Object ProcessId,CommandLine) -Compress")
                            audits.append(dict(at=time.time(), processes=rows))
                            if any(row["ProcessId"] != process.process.pid for row in rows):
                                errors.append("Foreign JVM during native measurement"); return
                            next_audit = time.monotonic() + 10
                        if sum(p.stat().st_size for p in ROOT.rglob("*") if p.is_file()) >= 90_000_000:
                            errors.append("W144 evidence safety threshold reached"); return
                        stop.wait(.25)
                except Exception as failure:
                    errors.append(str(failure))

            sampler = threading.Thread(target=monitor, daemon=True); sampler.start()
            try:
                # Short waits preserve the global, unchanged 360-second native deadline and permit monitor failure to stop the run.
                deadline = time.monotonic() + 360
                while not any(pattern.search(line) for line in process.lines):
                    if errors or process.process.poll() is not None or time.monotonic() >= deadline:
                        raise RuntimeError("Native measurement failed: " + str(errors or ["process exit/deadline"]))
                    time.sleep(.25)
                ready_seconds = time.monotonic() - launched
                process.command("stop"); code = process.finish()
                if code != 0 or any("/ERROR]" in line or "/FATAL]" in line for line in process.lines):
                    raise RuntimeError("Native benchmark exit/ERROR/FATAL failure")
                if args.case == "pressure" and not any("ARCE_ESCROW_BENCH_CACHES terrain=256 entities=256 sync=true" in line for line in process.lines):
                    raise RuntimeError("Missing saturated caches")
            finally:
                stop.set(); sampler.join(40)
                if process.process.poll() is None:
                    process.abort()
                report["sessions"].append(dict(stage=stage, command=command, rss_bytes=rss, audits=audits, monitor_errors=errors,
                                               launch_to_ready_seconds=ready_seconds))
                for name in ("cargo-native.json", "cargo-save-trace.json", "cargo-pressure.json", "governed-benchmark.json", "governed-benchmark.csv", "escrow-benchmark.csv",
                             "cargo-recovery-baseline.json", "cargo-recovery-1.json", "cargo-recovery-2.json"):
                    if (session / "world" / name).is_file():
                        shutil.copy2(session / "world" / name, evidence / name)
            if errors or sampler.is_alive() or not rss:
                raise RuntimeError("Native resource monitoring failed: " + str(errors))
            process_lines = list(process.lines)
            process = None
            if diagnose_saves:
                trace = json.loads((evidence / "cargo-save-trace.json").read_text(encoding="utf-8"))
                raw = json.loads((evidence / "cargo-native.json").read_text(encoding="utf-8"))
                report.update(status="DIAGNOSTIC_OBSERVED" if trace.get("status") == "OBSERVED" else "STOP_DIAGNOSTIC_INCOMPLETE",
                              benchmark_status=raw.get("status"), coverage_gaps=native_coverage(raw),
                              work_budget_failures=native_budgets(raw))
                return report  # A tail observation is never benchmark acceptance or permission to restart.
            if stage == "start":
                if args.case == "pressure":
                    phases = pressure.summarize(evidence / "escrow-benchmark.csv")
                    raw = json.loads((evidence / "cargo-pressure.json").read_text(encoding="utf-8"))
                    report["pressure_workload"] = raw
                    gaps = pressure_coverage(raw) + [name for name in ("capture_main", "ledger_capture_main", "ledger_io", "force_io", "readback_io",
                        "terrain_load_queue_io", "entity_load_queue_io", "record_queue_age", "tick_main") if name not in phases]
                    if phases.get("record_queue_age", {}).get("count") != raw.get("admitted_records", 0) - 3:
                        gaps.append("all admitted healthy requests retired (3 deliberate held records)")
                    if (phases.get("admission_main", {}).get("count") != raw.get("admitted_records")
                            or phases.get("admission_refusal_main", {}).get("count") != len(raw.get("refusals", []))):
                        gaps.append("all admission/refusal costs present")
                    report["synthetic_requests"] = 64
                else:
                    raw = json.loads((evidence / "cargo-native.json").read_text(encoding="utf-8"))
                    report["native"] = raw
                    missing = [name for name in ("governed-benchmark.csv", "governed-benchmark.json") if not (evidence / name).is_file()]
                    if missing:
                        report["coverage_gaps"] = native_coverage(raw) + ["missing " + name for name in missing]
                        report["error"] = raw.get("error", "Reference measurement did not complete")
                        report["status"] = "STOP_MISSING_MEASUREMENT"
                        return report
                    phases, generations = governed.read_samples(evidence / "governed-benchmark.csv")
                    load = json.loads((evidence / "governed-benchmark.json").read_text(encoding="utf-8"))
                    gaps = native_coverage(raw) + governed.coverage(load, phases, generations, True)
                    report.update(native=raw, reference_load=load, work_budget_failures=native_budgets(raw))
                comparisons = governed.compare(phases, rss)
                if args.case == "pressure":
                    comparisons.update(pressure_costs(phases))
                report.update(samples=phases, comparisons=comparisons, coverage_gaps=gaps)
                if not all(comparisons.values()) or report.get("work_budget_failures"):
                    report["status"] = "STOP_BUDGET"; return report
                if gaps:
                    report["status"] = "STOP_MISSING_MEASUREMENT"; return report
            else:
                index = int(stage.removeprefix("recover-"))
                recovery = json.loads((evidence / f"cargo-recovery-{index}.json").read_text(encoding="utf-8"))
                baseline = json.loads((evidence / "cargo-recovery-baseline.json").read_text(encoding="utf-8"))
                gaps = recovery_coverage(recovery, index, baseline, args.source_commit)
                if not any(match and int(match[1]) >= 40 for match in (governed.RESTART.search(line) for line in process_lines)):
                    gaps.append("governed restart measurement missing")
                report.setdefault("recoveries", []).append(recovery)
                if gaps:
                    report["coverage_gaps"] = gaps; report["status"] = "STOP_RECOVERY_MISMATCH"; return report
                report["restart_verified"] = index == 2
        report["status"] = "NATIVE_STAGE_MEASURED"
        return report
    except Exception as failure:
        report["error"] = str(failure)
        return report
    finally:
        if process is not None and process.process.poll() is None:
            process.abort()
        (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        if session.parent.resolve() == TEMP.resolve() and session.name.startswith("W144-") and session.is_dir() and not session.is_symlink():
            if marker.is_file() and not marker.is_symlink() and marker.read_text(encoding="ascii") == token:
                shutil.rmtree(session)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--case", choices=("pressure", "reference"), required=True)
    parser.add_argument("--diagnose-saves", action="store_true", help="Observe saves and a bounded post-load tail, without benchmark acceptance or restart")
    parser.add_argument("--source-server", type=Path, required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    report = run(parser.parse_args()); print(report["status"], flush=True)
    return 0 if report["status"] == "NATIVE_STAGE_MEASURED" else 1


if __name__ == "__main__":
    raise SystemExit(main())
