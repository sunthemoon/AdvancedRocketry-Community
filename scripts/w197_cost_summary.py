"""Summarize W197 raw samples without diluting active work with idle ticks."""
from __future__ import annotations

import argparse
import csv
import json
import math
from collections import Counter, defaultdict
from pathlib import Path


def statistics(values, scale=1):
    ordered = sorted(float(value) / scale for value in values)
    if not ordered:
        return {"count": 0}
    return dict(count=len(ordered), mean=sum(ordered) / len(ordered),
                p50=ordered[math.ceil(len(ordered) * .50) - 1],
                p95=ordered[math.ceil(len(ordered) * .95) - 1],
                p99=ordered[math.ceil(len(ordered) * .99) - 1], maximum=ordered[-1])


def read_csv(root, name):
    with (root / name).open(newline="", encoding="utf-8") as stream:
        return list(csv.DictReader(stream))


def summarize(root):
    ticks = read_csv(root, "w197-ticks.csv")
    steps = read_csv(root, "w197-steps.csv")
    proofs = read_csv(root, "w197-proofs.csv")
    events = read_csv(root, "w197-events.csv")
    result = json.loads((root / "w197-result.json").read_text())
    substantive = {"entry.prepare", "entry.confirm", "keepLoaded", "prepare", "projection", "capture",
                   "capture.callback", "recheck", "terminal", "ticketReady", "afterAcknowledged"}
    work = {(row["phase"], row["round"], row["tick"]) for row in steps if row["step"] in substantive}
    windows = {}
    for phase in sorted({row["phase"] for row in ticks}):
        for cohort in ("all", "1") if phase == "PROOF" else ("all",):
            selected = [row for row in ticks if row["phase"] == phase and (cohort == "all" or row["round"] == cohort)]
            active = [row for row in selected if (row["phase"], row["round"], row["tick"]) in work]
            windows[phase + ".round_" + cohort] = {
                "tick_ms": statistics([row["tick_ns"] for row in selected], 1e6),
                "work_tick_ms": statistics([row["tick_ns"] for row in active], 1e6),
                "observed_ms": statistics([row["observed_ns"] for row in active], 1e6),
                "peak_pending": max((int(row["pending"]) for row in selected), default=0),
                "peak_active": max((int(row["active"]) for row in selected), default=0),
            }
            for name in ("tick_ms", "work_tick_ms"):
                stats = windows[phase + ".round_" + cohort][name]
                if stats["count"]:
                    stats["docs17_numeric_comparison"] = {
                        "mean_le_25": stats["mean"] <= 25, "p95_le_50": stats["p95"] <= 50,
                        "p99_le_100": stats["p99"] <= 100, "max_le_500": stats["maximum"] <= 500}
    grouped = defaultdict(list)
    for row in steps:
        grouped[row["phase"] + "." + row["step"]].append(row)
    step_stats = {key: dict(exclusive_ms_per_invoked_tick=statistics([row["exclusive_ns"] for row in rows], 1e6),
                           calls=sum(int(row["calls"]) for row in rows),
                           largest_inclusive_call_ms=max(int(row["max_inclusive_ns"]) for row in rows) / 1e6)
                  for key, rows in sorted(grouped.items())}
    accepted = [row for row in proofs if row["accepted"] == "true"]
    qualification = (result.get("outcome") == "MEASURED" and result.get("prepared") == 64
                     and result.get("handed_off") == 64 and result.get("peak_pending") == 64
                     and len({row["index"] for row in accepted}) == 64
                     and result.get("confirmed_captures", 0) >= 128
                     and result.get("blocks") == 2048 and result.get("volume") == 32768
                     and result.get("snapshot_bytes") == 1048576 and result.get("columns_per_end") == 16)
    return dict(fixture_qualified=qualification, fixture=result, windows=windows, steps=step_stats,
                proof_attempt_ticks=statistics([row["attempt_ticks"] for row in accepted]),
                proof_queue_ticks=statistics([row["queue_ticks"] for row in accepted]),
                terminal_attempts=Counter(row["attempt"] for row in accepted),
                handed_off_by_round=Counter(row["round"] for row in accepted),
                requests_not_current=sum(row["event"] == "request_not_current" for row in events),
                largest_tick=max(ticks, key=lambda row: int(row["tick_ns"])),
                methodology="Nearest-rank percentiles. Step samples are exclusive server-thread nanoseconds summed per "
                "method per tick with calls, not individual-call percentiles. Inclusive call maxima are retained. "
                "Admission is included; setup, forced warmup and offer construction are separate. Async IO is not timed. "
                "Observer overhead, other server work and concurrent B2 work remain in whole-tick timings. "
                "Round 1 starts at 64; later rounds use fresh consent for remaining unproved requests. "
                "This Windows synthetic workload is not docs/17's Debian reference-load or soak Gate.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("evidence", type=Path)
    args = parser.parse_args()
    summary = summarize(args.evidence)
    (args.evidence / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: value for key, value in summary.items() if key != "steps"}, indent=2))
    return 0 if summary["fixture_qualified"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
