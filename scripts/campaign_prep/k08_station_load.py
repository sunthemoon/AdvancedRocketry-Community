"""One C10 station load window per externally locked C6 server lifetime.

Run baseline-1, loaded, baseline-2 back to back on the same source, host and plan.
The caller releases its lock and yields >=90 seconds between invocations.
Short qualification windows demonstrate tooling, never a measurement or Gate.
"""
from __future__ import annotations

import csv
import json
import math
from pathlib import Path
import re
import sys
import time

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.campaign_prep import k08_stations as stations
from scripts.campaign_observe import raw_ticks
from scripts.campaign_observe.common import schedule


def windows(warmup, duration, qualification):
    if not all(math.isfinite(v) and v > 0 for v in (warmup, duration)):
        raise ValueError("Positive finite windows required")
    if warmup > 86400 or duration > 86400:
        raise ValueError("Window exceeds one day")
    if not qualification and (warmup < 120 or duration < 600):
        raise ValueError("Short windows require explicit --qualification")
    return dict(warmup_seconds=warmup, sample_seconds=duration,
                classification="QUALIFICATION_NOT_MEASUREMENT" if qualification else "CAMPAIGN_MEASUREMENT_NO_GATE_VERDICT")


def chunks(rows):
    # Complete 17x17 vanilla station platforms, not the entire 512x512 claim.
    return sorted({(x >> 4, z >> 4) for row in rows
        for x in (row["pad"][0]-8, row["pad"][0]+8)
        for z in (row["pad"][2]-8, row["pad"][2]+8)})


def forced_reply(lines):
    text = " ".join(lines)
    return sorted(set((int(x),int(z)) for x,z in re.findall(r"\[(-?\d+),\s*(-?\d+)\]", text)))


def tickets(console, coords, add):
    for x,z in coords:
        console.all(f"execute in {stations.SPACE} run forceload {'add' if add else 'remove'} {x*16} {z*16}")


def wait_chunks(console, coords, loaded, *, clock=time.monotonic, sleep=time.sleep):
    deadline = clock() + 60
    pending = set(coords)
    while pending:
        for x,z in list(pending):
            command = f"execute in {stations.SPACE} {'if' if loaded else 'unless'} loaded {x*16} 128 {z*16} run say K08_CHUNK_{x}_{z}"
            lines = console.all(command)
            if any(f"[Server] K08_CHUNK_{x}_{z}" in line for line in lines):
                pending.remove((x,z))
        if pending:
            stations.chain.require(clock() < deadline, f"Station chunks did not {'load' if loaded else 'unload'}: {sorted(pending)}")
            sleep(.5)
    return dict(loaded=loaded, chunks=[list(v) for v in coords], witness="execute if/unless loaded; no player or arbitrary query loads")


def sample(console, evidence, duration):
    # Poll snapshots include the earlier ring: cut every pair at the explicit start
    # tick so warm-up/unload/load work is never included in the sampled window.
    first = raw_ticks.parse_raw_tick_reply(console.query(raw_ticks.RAW_TICK_COMMAND, raw_ticks.PREFIX))
    polls, lines = [], []
    for _ in schedule(duration, 1.0):
        line = console.query(raw_ticks.RAW_TICK_COMMAND, raw_ticks.PREFIX)
        lines.append(line)
        poll = raw_ticks.parse_raw_tick_reply(line)
        polls.append(raw_ticks.RawTickPoll(poll.tick, tuple((t,n) for t,n in poll.samples if t > first.tick)))
    line = console.query(raw_ticks.RAW_TICK_COMMAND, raw_ticks.PREFIX)
    lines.append(line)
    last = raw_ticks.parse_raw_tick_reply(line)
    polls.append(raw_ticks.RawTickPoll(last.tick, tuple((t,n) for t,n in last.samples if t > first.tick)))
    merged = raw_ticks.merge_polls(polls)
    summary = raw_ticks.summarize_raw_ticks(polls, 500)
    stations.chain.require(summary["tick_count"] > 0, "No sampled completed ticks")
    (evidence / "raw-ticks.log").write_text("\n".join(lines)+"\n", encoding="utf-8")
    with (evidence / "samples.csv").open("x", newline="", encoding="utf-8") as stream:
        writer = csv.writer(stream)
        writer.writerow(("tick", "nanoseconds"))
        writer.writerows(merged["samples"])
    # K15 includes reference numbers as metadata; this driver never compares them.
    return dict(summary=summary, start_after_tick=first.tick, end_tick=last.tick,
        reference_comparison="NOT_PERFORMED: this is not REF; no budget or percentage verdict")


def action(console, rows, evidence, args):
    plan = windows(args.warmup, args.duration, args.qualification)
    coords = chunks(rows)
    command = f"execute in {stations.SPACE} run forceload query"
    original_reply = console.all(command)
    original = forced_reply(original_reply)
    # Remove only this disposable copy's inherited Space tickets, then restore the
    # exact set before saving. Overworld/Moon tickets are left alone.
    tickets(console, original, False)
    unloaded = wait_chunks(console, coords, False)
    tickets(console, coords, True)
    reloaded = wait_chunks(console, coords, True)
    if args.run_label != "loaded":
        tickets(console, coords, False)
        baseline = wait_chunks(console, coords, False)
    else:
        baseline = None
    time.sleep(args.warmup)
    observed = sample(console, evidence, args.duration)
    tickets(console, coords, False)
    tickets(console, original, True)
    after = console.all(command)
    stations.chain.require(forced_reply(after) == original, "Native tickets not restored")
    result = dict(plan=plan, role=args.run_label, stations=len(rows), source_world="Same immutable source per count",
        baseline_definition="All station platform chunks unloaded", treatment_definition="All station platform chunks force-loaded",
        unload=unloaded, reload=reloaded, baseline=baseline, original_force_reply=original_reply,
        restored_force_reply=after, observed=observed, player_count=0,
        limitations="Platform chunks, not entire station claims. No players, live charging or warp commits. Qualification is not performance evidence. Review matched full windows and the two baseline spreads manually; no REF comparison.")
    stations.chain.write(evidence / "load.json", result)
    return result


def parser():
    result = stations.parser()
    # The shared runner has one native lifetime per call; these choices cannot
    # accidentally build/rewrite a source during a measurement.
    result._option_string_actions["--mode"].choices = ("load",)
    result._option_string_actions["--variant"].choices = ("ten", "hundred")
    result.add_argument("--run-label", choices=("baseline-1", "loaded", "baseline-2"), required=True)
    result.add_argument("--warmup", type=float, default=120)
    result.add_argument("--duration", type=float, default=600)
    result.add_argument("--qualification", action="store_true")
    return result


if __name__ == "__main__":
    args = parser().parse_args()
    windows(args.warmup, args.duration, args.qualification)
    print(json.dumps(stations.run(args, action), indent=2))
