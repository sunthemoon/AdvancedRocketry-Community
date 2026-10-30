"""C1 wall/CPU/allocation deltas and sampled attribution; W208 whole-tick statistics stay unchanged."""
from __future__ import annotations

import argparse
from collections import Counter
import json
from pathlib import Path
import subprocess

from w208_summary import sample


def c1_intervals(raw):
    pairs = []
    before = None
    ticks = {row["tick"]: row["tick_ns"] for row in raw["ticks"]}
    for event in raw["events"]:
        if event["label"] == "C1_RETAIN_BEFORE":
            if before is not None:
                raise ValueError("Overlapping C1 markers")
            before = event
        elif event["label"] == "C1_RETAIN_AFTER":
            if before is None or (before["tick"], before["thread"]) != (event["tick"], event["thread"]):
                raise ValueError("Unpaired C1 markers")
            deltas = {}
            for key in ("ns", "cpu_ns", "allocated_bytes", "epoch_ms"):
                if min(before[key], event[key]) < 0 or event[key] < before[key]:
                    raise ValueError("Unavailable/nonmonotonic C1 metric: " + key)
                deltas[key] = event[key] - before[key]
            pairs.append(dict(tick=event["tick"], wall_ms=deltas["ns"] / 1e6,
                              cpu_ms=deltas["cpu_ns"] / 1e6, allocated_bytes=deltas["allocated_bytes"],
                              epoch_begin_ms=before["epoch_ms"], epoch_end_ms=event["epoch_ms"],
                              whole_tick_ms=ticks[event["tick"]] / 1e6))
            before = None
    if before is not None:
        raise ValueError("Missing final C1 marker")
    return pairs


def profile_intervals(intervals, events):
    rows = [dict(samples=Counter(), frames=Counter(), gc_overlap_ns=0) for _ in intervals]
    for line in events:
        kind, start, end, thread, stack = line.rstrip("\n").split("\t", 4)
        start, end = int(start), int(end)
        for interval, row in zip(intervals, rows):
            lo, hi = interval["epoch_begin_ms"] * 1_000_000, interval["epoch_end_ms"] * 1_000_000
            if kind in ("jdk.ExecutionSample", "jdk.NativeMethodSample") and thread == "Server thread" and lo <= start <= hi:
                # Exclusive sample categories, not exact exclusive durations.
                category = "other-retain"
                if "RocketTransactionSavedData.canAppend" in stack:
                    category = "admission-other"
                    if "BoundedNbt.measure" in stack:
                        category = "admission-bounded-measurer"
                    elif "RocketTransactionCodec.encodeEntry" in stack:
                        category = "admission-encoder"
                elif "RocketTransactionSavedData.write" in stack:
                    category = "ram-write"
                row["samples"][category] += 1
                row["frames"].update(set(stack.split(";")) - {""})
            elif kind == "jdk.GCPhasePause":
                row["gc_overlap_ns"] += max(0, min(end, hi) - max(start, lo))
    return [dict(samples=dict(row["samples"]), frames=row["frames"].most_common(25),
                 gc_overlap_ms=row["gc_overlap_ns"] / 1e6) for row in rows]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--java", default="C:/Program Files/Java/jdk-17.0.7/bin/java.exe")
    args = parser.parse_args()
    output = args.directory / "admission-summary.json"
    if output.exists():
        raise FileExistsError(output)
    rows = []
    for case in ("small-quiet", "near-quiet"):
        for stage in ("start", "recover1", "recover2"):
            folder = args.directory / case
            raw = json.loads((folder / f"{stage}.json").read_text())
            c1 = c1_intervals(raw)
            command = [args.java, str(Path(__file__).with_name("W199JfrExtract.java")), str(folder / f"{stage}.jfr")]
            with subprocess.Popen(command, stdout=subprocess.PIPE, text=True, encoding="utf-8") as process:
                attribution = profile_intervals(c1, process.stdout)
                if process.wait() != 0:
                    raise RuntimeError("JFR extraction failed")
            rows.append(dict(case=case, stage=stage, source=raw["source"], c1=c1, c1_jfr=attribution,
                             observation=sample(raw, (folder / f"{stage}.log").read_text(errors="replace"))))
    output.write_text(json.dumps(dict(notes=[
        "CPU counters may be quantized; zero is below counter resolution, not zero CPU work.",
        "Allocation is an exact current-thread counter delta, not retained memory or whole-process allocation.",
        "C1 includes canAppend, journalFor/write and observer bookkeeping; JFR samples are not exclusive CPU durations.",
        "Epoch timestamps have millisecond resolution; boundary samples/GC overlap are approximate.",
        "Recovery with no C1 markers is not a measured zero-cost admission."
    ], rows=rows), indent=2), encoding="utf-8")
    print(output)


if __name__ == "__main__":
    main()
