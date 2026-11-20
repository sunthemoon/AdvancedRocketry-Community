"""Console-only aggregate tick observer and pure per-tick arithmetic helpers."""

import csv
import math
import re
from statistics import fmean

from .common import finite_nonnegative, safe_path, schedule, utc_now

TICK_COMMAND = "arce satellite release-test perf ticks"
REFERENCE_COMMAND = "arce endgame release-test refload status"
REFERENCE_BUDGETS = {
    "source": "docs/17 sections 4 and 8; reference values only, not a verdict",
    "heap_limit_gib": 4, "sustained_rss_gib": 5.5, "mean_ms": 25,
    "p95_ms": 50, "p99_ms": 100, "non_startup_save_spike_ms": 500,
    "watchdog_oom_count": 0, "unexpected_forced_chunks": 0,
    "soak": "4 h; no monotonic memory growth; reference workload required",
    "client": "matched Vanilla/Forge baseline; no absolute FPS floor",
}


def percentile(samples, percent):
    """Nearest rank, matching the command's 95th/99th elements of 100."""
    if not 0 < percent <= 100:
        raise ValueError("Percentile must be (0, 100]")
    values = sorted(finite_nonnegative(x) for x in samples)
    return values[max(0, math.ceil(len(values) * percent / 100) - 1)] if values else None


def summarize_ticks(samples, threshold_ms):
    """Only for actual (tick number, MSPT) pairs, never aggregate windows."""
    threshold_ms = finite_nonnegative(threshold_ms)
    samples = [(tick, finite_nonnegative(ms)) for tick, ms in samples]
    if any(not isinstance(tick, int) or tick < 0 for tick, _ in samples):
        raise ValueError("Invalid tick number")
    if any(b[0] <= a[0] for a, b in zip(samples, samples[1:])):
        raise ValueError("Tick numbers must be strictly increasing")
    runs = []
    for tick, ms in samples:
        if ms > threshold_ms:
            if runs and runs[-1]["end_tick"] == tick - 1:
                runs[-1]["end_tick"] = tick
                runs[-1]["count"] += 1
                runs[-1]["max_ms"] = max(ms, runs[-1]["max_ms"])
            else:
                runs.append({"start_tick": tick, "end_tick": tick, "count": 1, "max_ms": ms})
    values = [ms for _, ms in samples]
    return {"tick_count": len(samples), "mean_ms": fmean(values) if values else None,
            "p95_ms": percentile(values, 95), "p99_ms": percentile(values, 99),
            "max_ms": max(values) if values else None, "threshold_ms": threshold_ms,
            "threshold_runs": runs, "reference_budgets": REFERENCE_BUDGETS}


def parse_tick_reply(lines, *, reference=False):
    if isinstance(lines, str):
        lines = lines.splitlines()
    prefix = "ARCE_RELEASE_TEST refload status " if reference else "ARCE_RELEASE_TEST ticks "
    matches = set(line.split(prefix, 1)[1].strip() for line in lines if prefix in line)
    if len(matches) != 1:
        raise ValueError("Expected one unambiguous tick reply; check hooks and console routing")
    fields = dict(re.findall(r"(\w+)=([^\s]+)", matches.pop()))
    if reference:
        return {"mean_ms": finite_nonnegative(fields["mspt"])}
    result = {key: int(fields[key]) for key in ("tick", "window")}
    if result["window"] != 100 or result["tick"] < 0:
        raise ValueError("Unsupported tick window or tick number")
    for key in ("mean_ms", "p95_ms", "p99_ms", "max_ms"):
        result[key] = finite_nonnegative(fields[key])
    return result


def sample_ticks(channel, output, *, duration, threshold_ms, interval=1.0, reference=False,
                 scheduler=schedule):
    """W399 supplies channel(command)->reply lines with a bounded reply timeout.

    Poll <=1 s (nominally 20 ticks). A delta >100 flags lost windows. Aggregates
    cannot be de-overlapped or reconstruct consecutive slow ticks. CSV preserves
    window statistics, but whole-run p95/p99 and raw tick count remain unavailable.
    A restart requires a new file, rather than mixing two tick-number epochs.
    """
    if not 0.1 <= interval <= 1:
        raise ValueError("Tick polling interval must be [0.1, 1] seconds")
    threshold_ms = finite_nonnegative(threshold_ms)
    means, maxima = [], []
    last_tick = None
    lost = 0
    columns = ("utc", "tick", "window", "mean_ms", "window_p95_ms", "window_p99_ms",
               "window_max_ms", "overlap_ticks", "gap_ticks")
    with safe_path(output).open("x", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=columns)
        writer.writeheader()
        for _ in scheduler(duration, interval):
            sample = parse_tick_reply(channel(REFERENCE_COMMAND if reference else TICK_COMMAND), reference=reference)
            overlap, gap = None, None
            if not reference:
                tick = sample["tick"]
                if last_tick is not None:
                    if tick < last_tick:
                        raise ValueError("Server tick epoch changed; start a new sampler file")
                    if tick == last_tick:
                        continue
                    delta = tick - last_tick
                    overlap, gap = max(0, 100 - delta), max(0, delta - 100)
                    lost += gap
                last_tick = tick
                maxima.append(sample["max_ms"])
            means.append(sample["mean_ms"])
            writer.writerow({"utc": utc_now(), "tick": sample.get("tick"), "window": sample.get("window"),
                             "mean_ms": sample["mean_ms"], "window_p95_ms": sample.get("p95_ms"),
                             "window_p99_ms": sample.get("p99_ms"), "window_max_ms": sample.get("max_ms"),
                             "overlap_ticks": overlap, "gap_ticks": gap})
            stream.flush()
    return {"mode": "aggregate_only", "window_count": len(means), "tick_count": None,
            "mean_ms": fmean(means) if means else None, "mean_basis": "unweighted mean of sampled window means",
            "p95_ms": None, "p99_ms": None, "max_ms": None,
            "max_reported_window_ms": max(maxima) if maxima else None,
            "detected_gap_ticks": lost if not reference else None, "threshold_ms": threshold_ms,
            "threshold_runs": None, "limit": "No individual durations; overlaps cannot be removed; startup ring may be incomplete",
            "reference_budgets": REFERENCE_BUDGETS}


def sample_forced_chunks(channel, dimensions):
    """Read vanilla persistent force-load tickets only, not all Forge/mod tickets.

    Does not load chunks. Keep the native reply because it is locale-dependent;
    unexpected tickets require a matched baseline and investigation by the reader.
    """
    results = {}
    for dimension in dimensions:
        if re.fullmatch(r"[a-z0-9_.-]+:[a-z0-9_./-]+", dimension) is None:
            raise ValueError("Invalid dimension ID")
        results[dimension] = list(channel(f"execute in {dimension} run forceload query"))
    return {"utc": utc_now(), "scope": "vanilla persistent tickets only", "replies": results}
