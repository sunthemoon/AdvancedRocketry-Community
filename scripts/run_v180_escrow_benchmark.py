#!/usr/bin/env python3
"""Measure saturated original-worker escrow costs; explicitly not docs/17 reference-load acceptance."""

from __future__ import annotations

import argparse
import csv
import json
import os
from pathlib import Path
import platform
import re
import shutil
import statistics
import threading
import uuid

import run_v180_escrow_forced_stop as escrow
from run_v040_atmosphere_server_smoke import _rss_bytes, percentile


DONE = re.compile(r"ARCE_ESCROW_BENCH_DONE ticks=(\d+) records=3 sync=(true|false) samples=(\d+) gc_count=(\d+) gc_ms=(\d+)")
BUDGETS = {"mean_ms": 25, "p95_ms": 50, "p99_ms": 100, "nonsave_spike_ms": 500, "rss_bytes": int(5.5 * 1024**3)}


def summarize(path: Path) -> dict:
    phases: dict[str, list[float]] = {}
    with path.open(newline="", encoding="utf-8") as stream:
        for row in csv.DictReader(stream):
            value = int(row["value"])
            if value < 0:
                raise escrow.smoke.SmokeError("Negative benchmark sample")
            phases.setdefault(row["phase"], []).append(value if row["phase"] == "allocation_bytes" else value / 1_000_000)
    return {name: dict(count=len(values), mean=statistics.mean(values), p50=percentile(values, .5),
                       p95=percentile(values, .95), p99=percentile(values, .99), maximum=max(values),
                       unit="bytes" if name == "allocation_bytes" else "ms") for name, values in sorted(phases.items())}


def run(args) -> dict:
    session = args.session_dir.resolve()
    evidence = args.evidence_dir.resolve()
    if session.exists() or session.is_symlink() or evidence == session or session in evidence.parents:
        raise escrow.smoke.SmokeError("Unsafe benchmark session/evidence directory")
    if shutil.disk_usage(session.parent).free < 10 * 1024**3:
        raise escrow.smoke.SmokeError("Benchmark requires 10 GiB free")
    libraries = args.source_server.resolve() / "libraries"
    argument_file = libraries / "net/minecraftforge/forge/1.20.1-47.4.10" / ("win_args.txt" if platform.system() == "Windows" else "unix_args.txt")
    if not libraries.is_dir() or any(p.is_symlink() for p in libraries.rglob("*")) or not argument_file.is_file():
        raise escrow.smoke.SmokeError("Unsafe or missing benchmark libraries")
    required = set(re.findall(r"libraries/[^\s;:]+\.jar", argument_file.read_text(encoding="utf-8")))
    if not required or any(not (args.source_server / path).is_file() for path in required):
        raise escrow.smoke.SmokeError("Incomplete pinned benchmark libraries")
    session.mkdir()
    owned = uuid.uuid4().hex
    marker = session / ".arce-w11b1-benchmark-owned"
    marker.write_text(owned, encoding="ascii")
    evidence.mkdir(parents=True, exist_ok=True)
    report = dict(status="IN_PROGRESS", source_commit=args.source_commit, jar_sha256=escrow.digest(args.jar), budgets=BUDGETS,
                  machine=dict(platform=platform.platform(), hostname=platform.node(), cpu=platform.processor(), logical_cpus=os.cpu_count()),
                  reference_machine=False, xmx_bytes=4 * 1024**3, runs=[],
                  acceptance="Not docs/17 reference load: gated vanilla fixture endpoints, no connected players/oxygen/stations/satellites/production rockets; no four-hour soak.")
    process = None
    stop_sampling = threading.Event()
    sampler = None
    try:
        shutil.copytree(libraries, session / "libraries")
        (session / "mods").mkdir()
        shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = escrow.smoke.allocate_port()
        escrow.smoke.write_server_configuration(session, port, True)
        for sync in (args.sync or ["true", "false"]):
            properties = escrow.smoke.expected_server_properties(port, True)
            properties.update({"sync-chunk-writes": sync, "level-name": "world-" + sync, "level-type": "minecraft:flat", "max-tick-time": "60000"})
            (session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(properties.items())), encoding="ascii")
            command = escrow._server_command(args.java)
            command[2] = "-Xmx4G"
            command[1:1] = ["-Dadvancedrocketrycommunity.escrowScenario=start", "-Dadvancedrocketrycommunity.escrowCase=BENCH"]
            process = escrow.smoke.CapturedProcess(command, session, evidence / f"sync-{sync}.log")
            rss: list[int] = []
            stop_sampling.clear()
            pid = process.process.pid

            def sample_rss() -> None:
                while not stop_sampling.is_set():
                    value = _rss_bytes(pid)
                    if value is not None:
                        rss.append(value)
                    stop_sampling.wait(1)

            sampler = threading.Thread(target=sample_rss, daemon=True)
            sampler.start()
            index = process.wait_for(DONE, args.startup_timeout)
            match = DONE.search(process.lines[index])
            process.command("stop")
            code = process.finish()
            stop_sampling.set()
            sampler.join(15)
            if code != 0 or any("/ERROR]" in line or "/FATAL]" in line for line in process.lines):
                raise escrow.smoke.SmokeError("Benchmark exited unsuccessfully or logged ERROR")
            if not any("ARCE_ESCROW_BENCH_CACHES terrain=256 entities=256 sync=" + sync in line for line in process.lines):
                raise escrow.smoke.SmokeError("Both caches were not measured at saturation")
            process = None
            csv_file = evidence / f"sync-{sync}-samples.csv"
            shutil.copy2(session / ("world-" + sync) / "escrow-benchmark.csv", csv_file)
            phases = summarize(csv_file)
            for required_phase in ("capture_main", "ledger_capture_main", "ledger_io", "force_io", "readback_io", "terrain_load_queue_io", "entity_load_queue_io", "record_queue_age", "tick_main"):
                if required_phase not in phases:
                    raise escrow.smoke.SmokeError("Missing benchmark phase: " + required_phase)
            tick = phases["tick_main"]
            comparison = dict(mean_ms=tick["mean"] <= 25, p95_ms=tick["p95"] <= 50, p99_ms=tick["p99"] <= 100,
                              nonsave_spike_ms=phases["tick_main_nonsave"]["maximum"] <= 500,
                              rss_bytes=bool(rss) and max(rss) <= BUDGETS["rss_bytes"])
            report["runs"].append(dict(sync_chunk_writes=sync, command=command, samples=phases, rss_max_bytes=max(rss) if rss else None,
                                       rss_samples=len(rss), gc_count=int(match[4]), gc_ms=int(match[5]), comparisons=comparison,
                                       local_thresholds_met=all(comparison.values()), csv_sha256=escrow.digest(csv_file)))
        report["status"] = "MEASURED_WITH_REFERENCE_LOAD_GAPS"
        return report
    except Exception as failure:
        report["status"] = "FAILED"
        report["error"] = str(failure)
        raise
    finally:
        stop_sampling.set()
        if sampler is not None:
            sampler.join(15)
        if process is not None:
            process.abort()
        (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        if session.exists() and not session.is_symlink() and session.resolve() == session and marker.is_file() and not marker.is_symlink() and marker.read_text(encoding="ascii") == owned:
            shutil.rmtree(session)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_server", type=Path)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--session-dir", type=Path, required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--sync", action="append", choices=("true", "false"))
    parser.add_argument("--java", default=str(Path(os.environ.get("JAVA_HOME", "")) / "bin/java.exe"))
    parser.add_argument("--startup-timeout", type=float, default=300)
    args = parser.parse_args()
    try:
        run(args)
        return 0
    except (OSError, escrow.smoke.SmokeError) as failure:
        print("FAIL: " + str(failure), flush=True)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
