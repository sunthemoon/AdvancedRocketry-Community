"""Merge completed raw-tick polls from one server lifetime; never issue a Gate verdict.

The owning harness polls ``RAW_TICK_COMMAND`` every 100 ticks or sooner and saves
the replies. Run this file against that log with ``--threshold-ms 500`` to print
whole-run observations beside the docs/17 reference values. Gaps cover only the
observed first-to-last tick span, not time before/after collection. A restart or
tick-counter rollover needs a separate log. No server attachment is performed.
"""

import argparse
from dataclasses import dataclass
import json
import math
from pathlib import Path
import re


RAW_TICK_COMMAND = "arce satellite release-test perf ticks raw"
PREFIX = "ARCE_RELEASE_TEST ticks_raw "
MAX_ENTRIES = 100
MAX_REPLY_LENGTH = 4096
MAX_TICK = 2**31 - 1
MAX_NANOS = 2**63 - 1
REFERENCE_BUDGETS = {
    "source": "docs/17 section 4; reference values only, not a verdict",
    "mean_ms": 25,
    "p95_ms": 50,
    "p99_ms": 100,
    "non_startup_save_spike_ms": 500,
    "context": "4 vCPU / 8 GiB / Debian 12; defined reference workload required",
}
_REPLY = re.compile(r"tick=([0-9]{1,10}) entries=([0-9]{1,3}) data=(.*)")
_ENTRY = re.compile(r"([0-9]{1,10}):([0-9]{1,19})")


@dataclass(frozen=True)
class RawTickPoll:
    tick: int
    samples: tuple[tuple[int, int], ...]


def parse_raw_tick_reply(line):
    """Parse one command reply, optionally preceded by a server log prefix.

    Durations stay integer nanoseconds. END snapshots can end at the command's
    current tick or the preceding tick; the in-progress tick is never exported.
    """
    if not isinstance(line, str) or line.count(PREFIX) != 1:
        raise ValueError("Expected exactly one raw tick reply")
    reply = line.split(PREFIX, 1)[1].strip()
    if len(reply) + len(PREFIX) > MAX_REPLY_LENGTH:
        raise ValueError("Raw tick reply exceeds the bounded format")
    match = _REPLY.fullmatch(reply)
    if match is None:
        raise ValueError("Malformed raw tick reply")
    tick, count = int(match[1]), int(match[2])
    if tick > MAX_TICK or count > MAX_ENTRIES:
        raise ValueError("Tick counter or entry count exceeds the format")
    data = match[3].split(",") if match[3] else []
    if len(data) != count:
        raise ValueError("Entry count does not match data")
    samples = []
    for entry in data:
        pair = _ENTRY.fullmatch(entry)
        if pair is None:
            raise ValueError("Malformed tick/duration pair")
        number, nanos = int(pair[1]), int(pair[2])
        if not 1 <= number <= tick or nanos > MAX_NANOS:
            raise ValueError("Tick/duration pair is out of range")
        if samples and number != samples[-1][0] + 1:
            raise ValueError("Poll entries must be consecutive, oldest first")
        samples.append((number, nanos))
    if samples and samples[-1][0] not in (tick, tick - 1):
        raise ValueError("Poll does not end at the latest completed tick")
    return RawTickPoll(tick, tuple(samples))


def merge_polls(polls):
    """Drop exact overlaps; reject changed durations and mixed server epochs.

    Missing ticks are compact inclusive ranges, never fabricated zero samples.
    Keep restart-separated collections separate: a reset is detectable only when
    the next command tick is lower, so the harness must also enforce run identity.
    """
    samples = {}
    previous_tick = None
    duplicates = 0
    for poll in polls:
        if previous_tick is not None and poll.tick < previous_tick:
            raise ValueError("Server tick counter went backwards; start a new run")
        previous_tick = poll.tick
        for tick, nanos in poll.samples:
            if tick in samples:
                if samples[tick] != nanos:
                    raise ValueError(f"Conflicting durations for tick {tick}")
                duplicates += 1
            else:
                samples[tick] = nanos
    ordered = sorted(samples.items())
    gaps = [{"start_tick": before + 1, "end_tick": after - 1, "count": after - before - 1}
            for (before, _), (after, _) in zip(ordered, ordered[1:]) if after > before + 1]
    return {"samples": ordered, "gaps": gaps, "duplicate_count": duplicates}


def summarize_raw_ticks(polls, threshold_ms):
    """Nearest-rank p95/p99 of distinct observed ticks, not window percentiles.

    Gaps break consecutive runs. Values strictly over the caller's threshold are
    reported, including startup/save ticks: this tool cannot classify those events.
    """
    threshold_ms = float(threshold_ms)
    if not math.isfinite(threshold_ms) or threshold_ms < 0:
        raise ValueError("Threshold must be finite and nonnegative")
    merged = merge_polls(polls)
    samples = merged["samples"]
    values = sorted(nanos for _, nanos in samples)
    count = len(values)

    def percentile(percent):
        return values[(count * percent + 99) // 100 - 1] / 1_000_000 if count else None

    runs = []
    for tick, nanos in samples:
        ms = nanos / 1_000_000
        if ms > threshold_ms:
            if runs and runs[-1]["end_tick"] == tick - 1:
                runs[-1]["end_tick"] = tick
                runs[-1]["count"] += 1
                runs[-1]["max_ms"] = max(runs[-1]["max_ms"], ms)
            else:
                runs.append({"start_tick": tick, "end_tick": tick, "count": 1, "max_ms": ms})
    return {
        "tick_count": count,
        "first_tick": samples[0][0] if samples else None,
        "last_tick": samples[-1][0] if samples else None,
        "mean_ms": sum(values) / count / 1_000_000 if count else None,
        "p95_ms": percentile(95), "p99_ms": percentile(99),
        "max_ms": values[-1] / 1_000_000 if count else None,
        "threshold_ms": threshold_ms, "threshold_runs": runs,
        "gaps": merged["gaps"], "missing_tick_count": sum(gap["count"] for gap in merged["gaps"]),
        "duplicate_count": merged["duplicate_count"],
        "basis": "distinct observed completed ticks; missing ticks excluded, not filled",
        "reference_budgets": dict(REFERENCE_BUDGETS),
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", type=Path, help="Saved replies from one server lifetime")
    parser.add_argument("--threshold-ms", type=float, required=True)
    args = parser.parse_args(argv)
    try:
        with args.log.open(encoding="utf-8") as stream:
            polls = (parse_raw_tick_reply(line) for line in stream if PREFIX in line)
            summary = summarize_raw_ticks(polls, args.threshold_ms)
        if summary["tick_count"] == 0:
            raise ValueError("No completed raw ticks found")
    except (OSError, ValueError) as error:
        parser.error(str(error))
    print(json.dumps(summary, indent=2, allow_nan=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
