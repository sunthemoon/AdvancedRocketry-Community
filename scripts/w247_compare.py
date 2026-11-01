"""W247: per-batch stall table for the focused groups of one or more stages (before/after).

For each group directory (latest.log and the probe's ticks.jsonl), attributes every probe tick to the batch whose
start line precedes it and reports, per batch, the longest tick period and the ticks of at least 1 s and 2 s, plus
the group's server-overloaded entries. Writes JSON with --output and prints a table.
"""
import argparse
import bisect
import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import w247_lag_summary  # noqa: E402


def group(directory):
    summary = w247_lag_summary.summarize(directory / "latest.log", [])
    starts, names = [], []
    for line in (directory / "latest.log").read_text(encoding="utf-8-sig", errors="replace").splitlines():
        match = w247_lag_summary.BATCH.search(line)
        if match:
            starts.append(w247_lag_summary.stamp(line) * 1000.0)
            names.append(match[1].split(":")[0])
    ticks = [json.loads(line) for line in (directory / "ticks.jsonl").read_text(encoding="utf-8").splitlines()]
    total = next(tick for tick in ticks if tick.get("summary"))
    ticks = [tick for tick in ticks if not tick.get("summary")]
    batches = {name: dict(longest_ms=0.0, ticks_over_1s=0, ticks_over_2s=0, generated=0, saved=0) for name in names}
    batches["(before first batch)"] = dict(longest_ms=0.0, ticks_over_1s=0, ticks_over_2s=0, generated=0, saved=0)
    for tick in ticks:
        index = bisect.bisect_right(starts, tick["start_ms"]) - 1
        row = batches[names[index] if index >= 0 else "(before first batch)"]
        row["longest_ms"] = max(row["longest_ms"], tick["period_ms"])
        row["ticks_over_1s"] += tick["period_ms"] >= 1000
        row["ticks_over_2s"] += tick["period_ms"] >= 2000
        row["generated"] += tick["generated"]
        row["saved"] += tick["saved"]
    long = [tick["period_ms"] for tick in ticks if tick["period_ms"] >= 1000]
    return dict(group=directory.name, overload_count=summary["overload_count"],
                overloads_ms=[row["behind_ms"] for row in summary["overloads"]],
                ticks=total["ticks"], longest_ms=total["longest_ms"], ticks_over_1s=len(long),
                ticks_over_2s=sum(period >= 2000 for period in long), seconds_in_ticks_over_1s=round(sum(long) / 1000, 1),
                generated=total["generated"], saved=total["saved"], batches=batches)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("stages", nargs="+", type=pathlib.Path)
    parser.add_argument("--output", type=pathlib.Path)
    args = parser.parse_args()
    result = {}
    for stage in args.stages:
        result[stage.name] = [group(path) for path in sorted(stage.iterdir()) if (path / "ticks.jsonl").is_file()]
    if args.output:
        args.output.write_text(json.dumps(result, indent=1) + "\n", encoding="utf-8", newline="\n")
    for stage, groups in result.items():
        for row in groups:
            print(f"{stage:<10} {row['group']:<11} overloads={row['overload_count']} {row['overloads_ms']} ticks={row['ticks']} "
                  f"longest={row['longest_ms']:.0f} ms >=1s={row['ticks_over_1s']} >=2s={row['ticks_over_2s']} "
                  f"in>=1s={row['seconds_in_ticks_over_1s']} s generated={row['generated']} saved={row['saved']}")
            for name, batch in row["batches"].items():
                if batch["longest_ms"] >= 500 or name.startswith(("w241_", "w236_", "station_warp_deop", "w133_")):
                    print(f"{'':<23}{name:<32} longest={batch['longest_ms']:>7.0f} ms >=1s={batch['ticks_over_1s']} "
                          f">=2s={batch['ticks_over_2s']} generated={batch['generated']} saved={batch['saved']}")


if __name__ == "__main__":
    main()
