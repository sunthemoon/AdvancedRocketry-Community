"""Correlate sampled stacks and blocking/GC intervals with W197's full tick windows.

The CSV endpoint has millisecond precision, so attribution at a tick boundary is
approximate. Sample counts are not exact CPU durations. Blocking/GC durations
are separate, possibly overlapping measurements, never subtracted from samples.
"""
from __future__ import annotations

import argparse
from bisect import bisect_left
from collections import Counter
import csv
import json
from pathlib import Path
import subprocess

SAMPLES = {"jdk.ExecutionSample", "jdk.NativeMethodSample"}


class Profile:
    def __init__(self):
        self.samples = Counter()
        self.frames = Counter()
        self.stacks = Counter()
        self.blocking = Counter()
        self.blocking_stacks = Counter()

    def add(self, kind, start, end, thread, stack, lo, hi):
        if kind in SAMPLES:
            if not lo <= start <= hi:
                return
            self.samples[thread] += 1
            if thread == "Server thread":
                self.stacks[stack] += 1
                self.frames.update(sorted(set(stack.split(";")) - {""}))
        elif thread == "Server thread" or kind.startswith("jdk.GC") or kind.startswith("jdk.Safepoint"):
            overlap = max(0, min(end, hi) - max(start, lo))
            if overlap:
                self.blocking[kind] += overlap
                self.blocking_stacks[kind + " " + stack] += overlap

    def result(self):
        return dict(samples_by_thread=dict(self.samples), server_frames=self.frames.most_common(35),
                    server_stacks=self.stacks.most_common(15),
                    blocking_ms={key: value / 1e6 for key, value in self.blocking.items()},
                    blocking_stacks_ms=[(key, value / 1e6) for key, value in self.blocking_stacks.most_common(10)])


def summarize(rows, events):
    ticks = sorted(rows, key=lambda row: int(row["end_epoch_ms"]))
    ends = [int(row["end_epoch_ms"]) * 1_000_000 for row in ticks]
    starts = [end - int(row["tick_ns"]) for end, row in zip(ends, ticks)]
    selected = {int(row["tick"]) for row in ticks if int(row["tick_ns"]) > 500_000_000}
    for phase in ("PREPARATION", "PROOF"):
        selected.update(int(row["tick"]) for row in sorted(
            (row for row in ticks if row["phase"] == phase), key=lambda row: int(row["tick_ns"]), reverse=True)[:5])
    profiles, individual, counts = {}, {}, Counter()
    for line in events:
        kind, start, end, thread, stack = line.rstrip("\n").split("\t", 4)
        start, end = int(start), int(end)
        counts[kind] += 1
        index = bisect_left(ends, start)
        while index < len(ticks) and starts[index] <= end:
            row = ticks[index]
            profiles.setdefault(row["phase"], Profile()).add(kind, start, end, thread, stack, starts[index], ends[index])
            if int(row["tick"]) in selected:
                individual.setdefault(row["tick"], Profile()).add(kind, start, end, thread, stack, starts[index], ends[index])
            index += 1
    return dict(event_counts=dict(counts), phases={key: value.result() for key, value in profiles.items()},
                ticks=[dict(row=row, profile=individual.get(row["tick"], Profile()).result())
                       for row in ticks if int(row["tick"]) in selected])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--java", default="C:/Program Files/Java/jdk-17.0.7/bin/java.exe")
    parser.add_argument("--output", default="jfr-summary.json")
    args = parser.parse_args()
    with (args.directory / "w197-ticks.csv").open() as stream:
        rows = list(csv.DictReader(stream))
    command = [args.java, str(Path(__file__).with_name("W199JfrExtract.java")),
               str(args.directory / "server-profile.jfr")]
    with subprocess.Popen(command, stdout=subprocess.PIPE, text=True, encoding="utf-8") as process:
        summary = summarize(rows, process.stdout)
        if process.wait() != 0:
            raise RuntimeError("JFR extraction failed")
    target = args.directory / args.output
    if target.exists():
        raise FileExistsError(target)
    target.write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(target)


if __name__ == "__main__":
    main()
