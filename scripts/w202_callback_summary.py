"""Supplement W197 with observed release-callback ticks; leave its summary unchanged.

releaseTickets marks coordinator acknowledgement/discard continuations, including
the write-back ticks identified in W200-R1. Their full server-tick costs enter the
union, not just the release call duration. This is not exhaustive write-back
instrumentation: work without a port sample remains in the full phase windows.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

from w197_cost_summary import read_csv, statistics, summarize


def summarize_callbacks(root):
    original = summarize(root)
    ticks = read_csv(root, "w197-ticks.csv")
    steps = read_csv(root, "w197-steps.csv")
    # Preserve the historical classifier, and check its statistics against the unchanged module below.
    substantive = {"entry.prepare", "entry.confirm", "keepLoaded", "prepare", "projection", "capture",
                   "capture.callback", "recheck", "terminal", "ticketReady", "afterAcknowledged"}
    key = lambda row: (row["phase"], row["round"], row["tick"])
    work = {key(row) for row in steps if row["step"] in substantive}
    releases = {key(row) for row in steps if row["step"] == "releaseTickets"}
    windows = {}
    for name, old in original["windows"].items():
        phase, cohort = name.split(".round_")
        selected = [row for row in ticks if row["phase"] == phase and (cohort == "all" or row["round"] == cohort)]
        active = [row for row in selected if key(row) in work]
        legacy_stats = statistics([row["tick_ns"] for row in active], 1e6)
        if legacy_stats != {k: v for k, v in old["work_tick_ms"].items() if k != "docs17_numeric_comparison"}:
            raise ValueError("Historical work classifier changed; update the supplement explicitly")
        included = [row for row in selected if key(row) in work | releases]
        stats = statistics([row["tick_ns"] for row in included], 1e6)
        if stats["count"]:
            stats["docs17_numeric_comparison"] = {
                "mean_le_25": stats["mean"] <= 25, "p95_le_50": stats["p95"] <= 50,
                "p99_le_100": stats["p99"] <= 100, "max_le_500": stats["maximum"] <= 500}
        windows[name] = dict(
            full_tick_ms=old["tick_ms"], legacy_work_tick_ms=old["work_tick_ms"],
            work_or_release_tick_ms=stats,
            added_ticks=[dict(phase=row["phase"], round=row["round"], tick=row["tick"],
                              tick_ms=int(row["tick_ns"]) / 1e6)
                         for row in included if key(row) not in work],
            included_ticks=[row["tick"] for row in included],
            full_window_ticks_over_100=[row["tick"] for row in selected if int(row["tick_ns"]) > 100_000_000],
            full_window_ticks_over_100_outside_union=[row["tick"] for row in selected
                if int(row["tick_ns"]) > 100_000_000 and key(row) not in work | releases])
    return dict(windows=windows, fixture_qualified=original["fixture_qualified"],
                timing_boundary_qualified=original["timing_boundary_qualified"],
                methodology=__doc__.strip())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("evidence", type=Path)
    parser.add_argument("--output", type=Path, required=True,
                        help="New supplemental file; existing summaries are never overwritten")
    args = parser.parse_args()
    result = summarize_callbacks(args.evidence)
    with args.output.open("x", encoding="utf-8") as stream:
        stream.write(json.dumps(result, indent=2) + "\n")
    return 0 if result["fixture_qualified"] and result["timing_boundary_qualified"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
