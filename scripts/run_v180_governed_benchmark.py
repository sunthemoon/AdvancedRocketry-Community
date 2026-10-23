#!/usr/bin/env python3
"""Run the gated eight-flight reference workload, retaining failures and all generation samples."""

from __future__ import annotations

import argparse
from contextlib import contextmanager
import csv
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import platform
import re
import shutil
import statistics
import subprocess
import threading
import time
import uuid

import run_v180_escrow_forced_stop as escrow
from run_v040_atmosphere_server_smoke import _rss_bytes, percentile

smoke = escrow.smoke
BUDGETS = dict(mean_ms=25, p95_ms=50, p99_ms=100, nonsave_spike_ms=500, rss_bytes=int(5.5 * 1024**3))
# The Java collector's source-inventoried vocabulary; its unit test pins exact parity.
KNOWN_PHASES = frozenset(json.loads(Path(__file__).with_name("governed_benchmark_phases.json").read_text(encoding="utf-8")))
DONE = re.compile(r"ARCE_GOVERNED_BENCH_DONE ticks=(\d+) flights=8 peak=8 saves=(\d+)")
RESTART = re.compile(r"ARCE_GOVERNED_BENCH_RESTART survivors=8 player_ticks=(\d+) assembly=2048")
GENERATION_PHASES = {"generation_capture_main", "generation_encode_main", "generation_root_bytes", "generation_queue_age", "generation_request_queue_age",
                     "generation_writer_io", "generation_handoff", "generation_total_latency"}
REQUIRED_PHASES = {"capture_main", "ledger_capture_main", "ledger_io", "force_io", "readback_io",
                   "allocation_bytes", "tick_main", "tick_main_nonsave", "ordinary_start_to_c8", "flight_handoff_latency",
                   "autosave_main", "flight_writer_queue_age"} | GENERATION_PHASES
FLIGHT_POINTS = ("F1_ARM_ACK", "BENCH_F2_ACKNOWLEDGED", "BENCH_COUNTDOWN_OBSERVED", "F4_DESTINATION_ACK", "F4_PUBLICATION_AFTER",
                 "F5_SOURCE_REMOVAL_AFTER", "F6_LANDED", "F7_ACK", "F8_JOURNAL_ACK", "F8_PRUNE_ACK")


def utc() -> str:
    return datetime.now(timezone.utc).isoformat()


def java_processes() -> list[dict]:
    if platform.system() != "Windows":
        raise smoke.SmokeError("This runner's quiet-machine audit is Windows-only")
    script = ("$ErrorActionPreference='Stop'; $p=@(Get-CimInstance Win32_Process | "
              "Where-Object { $_.Name -in @('java.exe','javaw.exe') } | "
              "Select-Object ProcessId,ParentProcessId,Name,CommandLine); ConvertTo-Json -InputObject $p -Depth 3 -Compress")
    result = subprocess.run(["powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script],
                            capture_output=True, text=True, check=True, timeout=30)
    rows = json.loads(result.stdout)
    if not isinstance(rows, list) or any(not row.get("CommandLine") for row in rows):
        raise smoke.SmokeError("Incomplete JVM process inventory")
    return rows


def wait_quiet(evidence: Path, label: str, timeout: float, inventory=java_processes, sleep=time.sleep) -> None:
    deadline = time.monotonic() + timeout
    with (evidence / f"{label}-java-processes.jsonl").open("x", encoding="utf-8") as output:
        while True:
            rows = inventory()
            output.write(json.dumps(dict(at=utc(), processes=rows)) + "\n"); output.flush()
            if not rows:
                return
            if time.monotonic() >= deadline:
                raise smoke.SmokeError("Other JVMs did not exit before the quiet-machine deadline")
            sleep(min(30, max(0, deadline - time.monotonic())))


@contextmanager
def heavy_lock(lock: Path, timeout: float):
    token = f"W11b2-C45 {os.getpid()} {uuid.uuid4()} {utc()}"
    deadline = time.monotonic() + timeout
    while True:
        try:
            lock.mkdir()
            break
        except FileExistsError:
            if time.time() - lock.stat().st_mtime > 3 * 3600:
                raise smoke.SmokeError(f"Heavy-JVM lock older than three hours: {lock}")
            if time.monotonic() >= deadline:
                raise smoke.SmokeError(f"Heavy-JVM lock wait expired: {lock}")
            time.sleep(30)
    owner = lock / "owner.txt"
    owner.write_text(token, encoding="utf-8")
    try:
        yield token
    finally:
        if lock.is_symlink() or not owner.is_file() or owner.read_text(encoding="utf-8") != token:
            raise smoke.SmokeError("Heavy-JVM ownership changed; lock was not removed")
        owner.unlink(); lock.rmdir()


def read_samples(path: Path) -> tuple[dict, dict]:
    phases: dict[str, list[int]] = {}
    generations: dict[int, dict[str, list[int]]] = {}
    with path.open(newline="", encoding="utf-8") as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames != ["phase", "generation", "value"]:
            raise smoke.SmokeError("Invalid governed benchmark columns")
        for index, row in enumerate(reader):
            if index >= 131_072:
                raise smoke.SmokeError("Benchmark sample bound exceeded")
            phase = row["phase"]; generation = int(row["generation"]); value = int(row["value"])
            if phase not in KNOWN_PHASES or value < 0 or generation < -1:
                raise smoke.SmokeError("Invalid governed benchmark sample")
            if (phase in GENERATION_PHASES) != (generation >= 0):
                raise smoke.SmokeError("Generation/sample identity mismatch")
            phases.setdefault(phase, []).append(value)
            if generation >= 0:
                generations.setdefault(generation, {}).setdefault(phase, []).append(value)
    summaries = {}
    for phase, values in phases.items():
        byte_unit = phase.endswith("_bytes")
        scaled = values if byte_unit else [value / 1_000_000 for value in values]
        summaries[phase] = dict(count=len(scaled), mean=statistics.mean(scaled), p95=percentile(scaled, .95),
                                p99=percentile(scaled, .99), maximum=max(scaled), unit="bytes" if byte_unit else "ms")
    return summaries, generations


def coverage(load: dict, phases: dict, generations: dict, restart: bool) -> list[str]:
    """Missing proof is not a performance pass, even if every reported tick meets a limit."""
    gaps = []
    exact = dict(schema_version=1, players=2, active_oxygen=16, active_providers=16, stations=10, satellite_tasks=100,
                 flights_completed=8, peak_concurrent=8, assembly_blocks=2048, dimension_transfers=8)
    for name, value in exact.items():
        if type(load.get(name)) is not int or load[name] != value:
            gaps.append(f"load:{name}")
    for name, minimum in (("ticks", 1400), ("player_tick_delta", 2800), ("periodic_saves", 2)):
        if type(load.get(name)) is not int or load[name] < minimum:
            gaps.append(f"load:{name}")
    if load.get("ordinary_retired") is not True:
        gaps.append("ordinary C0-C8 retirement")
    if not restart:
        gaps.append("native restart")
    for phase in sorted(REQUIRED_PHASES - phases.keys()):
        gaps.append(f"sample:{phase}")
    if phases.get("flight_handoff_latency", {}).get("count") != 8:
        gaps.append("eight hand-off samples")
    if phases.get("tick_main", {}).get("count", 0) < 1400:
        gaps.append("full measured window")
    if not generations or any(set(values) != GENERATION_PHASES for values in generations.values()):
        gaps.append("complete per-generation accounting (including pruning)")
    topology = load.get("generation_topology", {})
    if set(topology) != {str(generation) for generation in generations} or any(
            set(counts) != {"records", "floors", "receipts", "heads", "armed"}
            or any(type(value) is not int or value < 0 for value in counts.values()) for counts in topology.values()):
        gaps.append("per-generation root topology")
    points = load.get("checkpoints", {})
    for point in FLIGHT_POINTS:
        if points.get(point, 0) < 8:
            gaps.append(f"checkpoint:{point}")
    for point in ("C8_PRUNE_AFTER", "O3_AFTER", "O4_AFTER", "WORLD_FIRST_DEFER"):
        if points.get(point, 0) < 1:
            gaps.append(f"checkpoint:{point}")
    if phases.get("root_1mib_encode_main", {}).get("count", 0) < 1 or load.get("root_encode_bytes") != 1_048_576:
        gaps.append("1 MiB root encode")
    return gaps


def compare(phases: dict, rss: list[int]) -> dict:
    tick = phases.get("tick_main", {})
    return dict(mean_ms=tick.get("mean", float("inf")) <= BUDGETS["mean_ms"],
                p95_ms=tick.get("p95", float("inf")) <= BUDGETS["p95_ms"],
                p99_ms=tick.get("p99", float("inf")) <= BUDGETS["p99_ms"],
                nonsave_spike_ms=phases.get("tick_main_nonsave", {}).get("maximum", float("inf")) <= BUDGETS["nonsave_spike_ms"],
                rss_bytes=bool(rss) and max(rss) <= BUDGETS["rss_bytes"])


def run(args) -> dict:
    repo = Path(__file__).resolve().parents[1]
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip()
    dirty = subprocess.check_output(["git", "status", "--porcelain"], cwd=repo, text=True)
    if dirty or commit != args.source_commit:
        raise smoke.SmokeError("Benchmark requires the exact clean committed source")
    session = args.session_dir.resolve(); evidence = args.evidence_dir.resolve(); source = args.source_server.resolve()
    if session.exists() or args.session_dir.is_symlink() or source == session or source in session.parents or session in source.parents:
        raise smoke.SmokeError("Session must be new and disjoint from the source server")
    if evidence == session or session in evidence.parents or evidence in session.parents:
        raise smoke.SmokeError("Evidence and disposable session must be disjoint")
    libraries = source / "libraries"
    argument_file = libraries / "net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt"
    if not argument_file.is_file() or any(p.is_symlink() for p in libraries.rglob("*")):
        raise smoke.SmokeError("Missing or linked pinned server libraries")
    required = set(re.findall(r"libraries/[^\s;:]+\.jar", argument_file.read_text(encoding="utf-8")))
    if not required or any(not (source / name).is_file() for name in required):
        raise smoke.SmokeError("Incomplete pinned server libraries")
    for path in (Path(os.environ.get("TEMP", ".")), session.parent):
        if shutil.disk_usage(path).free < 10 * 1024**3:
            raise smoke.SmokeError(f"Less than 10 GiB free: {path}")
    evidence.mkdir(parents=True, exist_ok=False)
    report = dict(status="IN_PROGRESS", source_commit=commit, jar_sha256=escrow.digest(args.jar), budgets=BUDGETS,
                  machine=dict(platform=platform.platform(), cpu=platform.processor(), logical_cpus=os.cpu_count()),
                  reference_machine=False, heap_max_bytes=4 * 1024**3, commands=[], sessions=[])
    owned = uuid.uuid4().hex; marker = session / ".arce-governed-benchmark-owned"
    try:
        with heavy_lock(args.lock.resolve(), args.quiet_timeout) as token:
            report["lock_owner"] = token
            session.mkdir(); marker.write_text(owned, encoding="ascii")
            shutil.copytree(libraries, session / "libraries"); (session / "mods").mkdir()
            shutil.copy2(args.jar, session / "mods" / args.jar.name)
            port = smoke.allocate_port(); smoke.write_server_configuration(session, port, True)
            properties = smoke.expected_server_properties(port, True)
            properties.update({"sync-chunk-writes": "true", "level-name": "world", "level-type": "minecraft:flat", "max-tick-time": "60000",
                               "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1},
                                   {"block": "minecraft:dirt", "height": 2}, {"block": "minecraft:grass_block", "height": 1}],
                                   "biome": "minecraft:plains"}, separators=(",", ":"))})
            (session / "server.properties").write_text("".join(f"{key}={value}\n" for key, value in sorted(properties.items())), encoding="ascii")
            restarted = False; all_rss = []
            for stage, pattern in (("start", DONE), ("recover", RESTART)):
                wait_quiet(evidence, stage, args.quiet_timeout)
                for path in (Path(os.environ.get("TEMP", ".")), session):
                    if shutil.disk_usage(path).free < 10 * 1024**3:
                        raise smoke.SmokeError(f"Less than 10 GiB free before {stage}: {path}")
                command = escrow._server_command(args.java); command[2] = "-Xmx4G"
                command[1:1] = ["-XX:ActiveProcessorCount=4", "-Dadvancedrocketrycommunity.escrowCase=GOVERNED_BENCH",
                                "-Dadvancedrocketrycommunity.escrowScenario=" + stage]
                report["commands"].append(command)
                process = smoke.CapturedProcess(command, session, evidence / f"{stage}.log")
                stop = threading.Event(); rss = []; foreign = []; failures = []

                def sample() -> None:
                    try:
                        while not stop.is_set():
                            value = _rss_bytes(process.process.pid)
                            if value is not None:
                                rss.append(value)
                            rows = java_processes()
                            foreign.extend(row for row in rows if row["ProcessId"] != process.process.pid)
                            stop.wait(10)
                    except Exception as failure:
                        failures.append(str(failure))

                sampler = threading.Thread(target=sample, daemon=True); sampler.start()
                try:
                    index = process.wait_for(pattern, args.startup_timeout)
                    if stage == "recover":
                        restarted = int(RESTART.search(process.lines[index])[1]) >= 40
                    process.command("stop"); code = process.finish()
                    if code != 0 or any("/ERROR]" in line or "/FATAL]" in line for line in process.lines):
                        raise smoke.SmokeError("Native benchmark exited unsuccessfully or logged ERROR/FATAL")
                finally:
                    stop.set(); sampler.join(40)
                    if process.process.poll() is None:
                        process.abort()
                    report["sessions"].append(dict(stage=stage, rss_bytes=rss, foreign_jvms=foreign, monitor_failures=failures))
                    # Preserve raw samples even if subsequent validation, restart or coverage fails.
                    for name in ("governed-benchmark.csv", "governed-benchmark.json"):
                        if (session / "world" / name).is_file():
                            shutil.copy2(session / "world" / name, evidence / name)
                if foreign or failures or sampler.is_alive():
                    raise smoke.SmokeError("Quiet-machine/monitor condition failed during native run")
                all_rss.extend(rss)
            load = json.loads((evidence / "governed-benchmark.json").read_text(encoding="utf-8"))
            phases, generations = read_samples(evidence / "governed-benchmark.csv")
            report.update(load=load, samples=phases, generations=generations, rss_max_bytes=max(all_rss) if all_rss else None,
                          restarted=restarted, coverage_gaps=coverage(load, phases, generations, restarted), comparisons=compare(phases, all_rss))
            report["status"] = ("INCOMPLETE_COVERAGE" if report["coverage_gaps"] else
                                "MEASURED_LOCAL_THRESHOLDS_MET" if all(report["comparisons"].values()) else "BUDGET_FAILED")
            return report
    except Exception as failure:
        report.update(status="FAILED", error=str(failure)); raise
    finally:
        (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        # This directory did not exist on entry; never clean the source server or another run.
        if session.is_dir() and not session.is_symlink() and marker.is_file() and marker.read_text(encoding="ascii") == owned:
            shutil.rmtree(session)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_server", type=Path)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--session-dir", type=Path, required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--lock", type=Path, default=Path("D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm"))
    parser.add_argument("--quiet-timeout", type=float, default=7200)
    parser.add_argument("--startup-timeout", type=float, default=360)
    args = parser.parse_args()
    try:
        report = run(args)
        print(report["status"], flush=True)
        return 0 if report["status"] == "MEASURED_LOCAL_THRESHOLDS_MET" else 1
    except (OSError, ValueError, subprocess.SubprocessError, smoke.SmokeError) as failure:
        print("FAIL: " + str(failure), flush=True); return 1


if __name__ == "__main__":
    raise SystemExit(main())
