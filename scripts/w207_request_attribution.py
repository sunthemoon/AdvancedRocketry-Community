"""Correlate request ticks with JFR; sample shares are not CPU-time or wall-time shares."""
from __future__ import annotations

import argparse
from collections import Counter
import json
from pathlib import Path
import subprocess


def category(stack):
    # Callers of a product entry include the fixture. Classify the actual sampled work,
    # not every stack that happens to have that caller at its bottom.
    if "cpw.mods.cl.ModuleClassLoader" in stack or "cpw.mods.modlauncher.ClassTransformer" in stack:
        return "class_loading"
    if "net.minecraft.world.level.levelgen" in stack or "net.minecraft.world.level.biome" in stack:
        return "vanilla_generation"
    if "W206Observation" in stack or any("W206NativeWarmup." + name in stack for name in
            ("snapshot", "captureTick", "observeWarmups", "finish", "write")):
        return "observer_or_fixture"
    if "net.minecraft.server.level.ChunkMap" in stack or "net.minecraft.world.level.chunk" in stack:
        return "vanilla_chunk_pipeline"
    if "io.github.sunthemoon" in stack:
        return "mod_or_entry_fixture"
    return "other"


def attribute(root, java):
    rows = [json.loads(line) for line in (root / "w206-ticks.jsonl").read_text().splitlines()]
    rows = [row for row in rows if row["phase"] == "REQUEST"]
    reports = [dict(tick=row["tick"], tick_ms=row["tick_ns"] / 1e6,
                    column_observer_ms=row["column_observer_ns"] / 1e6,
                    transition_observer_ms=row.get("transition_observer_ns", 0) / 1e6,
                    recorder_tail_ms=row.get("recorder_tail_ns", 0) / 1e6,
                    execution=Counter(), native=Counter(), blocking_ns=Counter(), server_stacks=Counter())
               for row in rows]
    command = [java, str(Path(__file__).with_name("W199JfrExtract.java")), str(root / "server-profile.jfr")]
    with subprocess.Popen(command, stdout=subprocess.PIPE, text=True, encoding="utf-8") as process:
        for line in process.stdout:
            kind, start, end, thread, stack = line.rstrip("\n").split("\t", 4)
            start, end = int(start), int(end)
            for row, report in zip(rows, reports):
                hi = row["epoch_ms"] * 1_000_000
                lo = hi - row["tick_ns"]
                if kind in ("jdk.ExecutionSample", "jdk.NativeMethodSample") and lo <= start <= hi:
                    report["execution" if kind == "jdk.ExecutionSample" else "native"][category(stack)] += 1
                    if thread == "Server thread":
                        report["server_stacks"][stack] += 1
                elif thread == "Server thread" or kind.startswith(("jdk.GC", "jdk.Safepoint")):
                    overlap = max(0, min(end, hi) - max(start, lo))
                    if overlap:
                        report["blocking_ns"][kind] += overlap
        if process.wait() != 0:
            raise RuntimeError("JFR extraction failed")
    for report in reports:
        report["server_stacks"] = report["server_stacks"].most_common(12)
        total = sum(report["execution"].values())
        report["execution_sample_total"] = total
        report["execution_sample_shares"] = {key: count / total for key, count in report["execution"].items()} if total else {}
    return dict(methodology="Millisecond endpoint correlation. Stack-category counts across all threads; sampling shares are not wall-time/CPU-time shares. Long blocking/GC overlaps are separate and may overlap; short waits are below JFR thresholds. No subtraction from whole ticks.", requests=reports)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directories", nargs="+", type=Path)
    parser.add_argument("--java", default="C:/Program Files/Java/jdk-17.0.7/bin/java.exe")
    args = parser.parse_args()
    for root in args.directories:
        output = root / "request-attribution-v2.json"
        if output.exists():
            raise FileExistsError(output)
        result = attribute(root, args.java)
        with output.open("x", encoding="utf-8") as stream:
            json.dump(result, stream, indent=2)
        print(output)


if __name__ == "__main__":
    main()
