"""K10 load observations. Windows qualification is never REF acceptance."""
from __future__ import annotations

import csv
from datetime import datetime, timezone
import math
import re
import time

from scripts.campaign_observe import raw_ticks
from scripts.campaign_observe.common import schedule
from scripts.campaign_observe.process import read_process
from scripts.campaign_prep import k03_chain as chain

HOOK = "arce endgame release-test refload "


def utc_now():
    return datetime.now(timezone.utc).isoformat()


def windows(warmup, duration, qualification):
    chain.require(all(math.isfinite(v) and 0 < v <= 3600 for v in (warmup, duration)),
                  "Finite windows in (0,3600] required")
    chain.require(qualification or (warmup >= 120 and duration >= 600),
                  "Short windows require --qualification")
    return dict(warmup_seconds=warmup, sample_seconds=duration,
                classification="QUALIFICATION_NOT_REF" if qualification else "PROCEDURE_NOT_REF_ACCEPTANCE")


def status(line):
    chain.require("ARCE_RELEASE_TEST refload status " in line, "Missing refload status")
    fields = dict(re.findall(r"(\w+)=([^\s]+)", line))
    chain.require(fields.get("built") == "true", "Runtime refload is unbuilt; cannot reattach after restart")
    for key in ("launches_requested", "launches_escrowed", "rides_requested", "players",
                "drills_running", "fields_active", "generators_burning", "elevators_bound"):
        fields[key] = int(fields[key])
        chain.require(fields[key] >= 0, "Negative refload counter")
    return fields


def throughput(before, after, ticks, seconds):
    chain.require(type(ticks) is int and ticks > 0 and math.isfinite(seconds) and seconds > 0,
                  "Positive tick and wall intervals required")
    requested = after["launches_requested"] - before["launches_requested"]
    escrowed = after["launches_escrowed"] - before["launches_escrowed"]
    chain.require(0 <= escrowed <= requested, "Invalid/reset throughput counters")
    # Compare per game tick as well as wall time. The hook counts an escrow on
    # its NEXT 20-tick pass, so this is a lagged proxy, never delivery proof.
    required = 16 * ticks / 20
    return dict(requested=requested, achieved_escrows_lagged_proxy=escrowed,
        game_ticks=ticks, wall_seconds=seconds, required_launches_per_20_ticks=16,
        required_launches_in_interval=required, requested_per_game_second=requested * 20 / ticks,
        escrowed_per_game_second=escrowed * 20 / ticks, escrowed_per_wall_second=escrowed / seconds,
        shortfall=max(0, required - escrowed), load_obligation="NOT_MET" if escrowed < required else "RATE_REACHED_PROXY_ONLY",
        delivery_throughput="NOT_COVERED: hook observes lastLaunch/OK on the next pass, not delivered cargo",
        counter_window_uncertainty="Status/raw-tick replies are separate console commands, not an atomic pair; next-pass escrow counting has up to 20 ticks of phase lag.",
        source="ReferenceLoadReleaseTestCommands.launchRailguns; unchanged ADR-054 section 7")


def readbacks(console):
    commands = ["arce beta data-report", "arce endgame status", "arce endgame timing",
                "arce endgame audit all", "arce endgame release-test ledger",
                "execute in minecraft:overworld run forceload query",
                "execute in advancedrocketrycommunity:space run forceload query"]
    return {cmd: console.all(cmd) for cmd in commands}


def observation_plan():
    return dict(before="Capture status, timing, audit, ledger, both dimension force-load replies and RSS before drive on.",
        during="Poll raw ticks and refload status/RSS each second; status/timing/audit/ledger/tickets every 30 seconds; retain ramp and all returned flush entries.",
        after="drive off; wait for rides to settle; capture same replies. Remove ONLY tickets added by this run, kick ONLY listed fixture UUIDs, wait for loaded checks to become false, capture status/tickets again.",
        cache_scope="structures tracked, validations_waiting, gravity_fields active, intent_players are available runtime counts. PatternBlockCache/patternCache internal sizes have no console getter: NOT_COVERED.",
        ticket_scope="forceload query is vanilla persistent tickets only; status ride_tickets is elevator transient count, not every Forge ticket.",
        campaign="K17 supplies combined world and periodic save/restart schedule; O7 supplies two real authenticated connections. Four continuous hours and REF hardware remain unrun.")


def sample(console, evidence, args, receipt):
    receipt["windows"] = windows(args.warmup, args.duration, args.qualification)
    if args.jfr:
        from scripts.campaign_observe import jdk
        target = jdk.CampaignJvm(console.process.process, "server")
        jcmd = args.java.with_name("jcmd.exe" if args.java.suffix == ".exe" else "jcmd")
        receipt["jfr_start"] = jdk.start_jfr(target, evidence, "load.jfr", name="k10",
            duration_s=math.ceil(args.warmup + args.duration + 120), max_mib=16, jcmd=jcmd)
    receipt["before_drive"] = readbacks(console)
    if args.variant != "idle":
        console.query(HOOK + "drive on", r"ARCE_RELEASE_TEST refload drive on")
    time.sleep(args.warmup)
    first = raw_ticks.parse_raw_tick_reply(console.query(raw_ticks.RAW_TICK_COMMAND, raw_ticks.PREFIX))
    before = status(console.query(HOOK + "status", "ARCE_RELEASE_TEST refload status"))
    started = time.monotonic()
    polls, statuses, host, checkpoints = [], [], [], []
    with (evidence / "polls.jsonl").open("x", encoding="utf-8") as stream:
        import json
        def collect():
            line = console.query(raw_ticks.RAW_TICK_COMMAND, raw_ticks.PREFIX)
            poll = raw_ticks.parse_raw_tick_reply(line)
            polls.append(raw_ticks.RawTickPoll(poll.tick, tuple((t, ns) for t, ns in poll.samples if t > first.tick)))
            current = status(console.query(HOOK + "status", "ARCE_RELEASE_TEST refload status"))
            statuses.append(current)
            memory = read_process(console.process.process.pid)
            host.append(memory)
            stream.write(json.dumps(dict(utc=utc_now(), raw=line, refload=current, host=memory)) + "\n")
            stream.flush()
            if not checkpoints or time.monotonic() - checkpoints[-1][0] >= 30:
                checkpoints.append((time.monotonic(), readbacks(console)))
            return poll
        for _ in schedule(args.duration, 1):
            collect()
        last = collect()
    elapsed = time.monotonic() - started
    summary = raw_ticks.summarize_raw_ticks(polls, 500)
    summary.pop("reference_budgets")
    chain.require(summary["tick_count"] > 0, "No completed sample ticks")
    with (evidence / "ticks.csv").open("x", newline="", encoding="ascii") as stream:
        writer = csv.writer(stream)
        writer.writerow(("server_tick", "total_nanos"))
        writer.writerows(raw_ticks.merge_polls(polls)["samples"])
    receipt["sampling"] = dict(summary=summary, checkpoints=[v for _, v in checkpoints],
        reference_budget_verdict="NOT_PERFORMED", ramp="console.log includes warmup and ramp; not discarded from flush evidence")
    receipt["load_start"], receipt["load_end"] = before, statuses[-1]
    receipt["load_counts"] = dict(minimums={key: min(s[key] for s in statuses) for key in
        ("drills_running", "fields_active", "generators_burning", "elevators_bound", "players")},
        required=dict(drills_running=20, fields_active=64, generators_burning=16, elevators_bound=8),
        caveats="Running is not proof that a drill completed work. Max-depth shafts are finite; COMPLETE/TARGET_UNLOADED codes and any count shortfall remain failures of the full load, never a reason to lower it. Fixture players are not the two real connections.")
    receipt["throughput"] = throughput(before, statuses[-1], last.tick - first.tick, elapsed)
    receipt["during_end"] = readbacks(console)
    console.query(HOOK + "drive off", r"ARCE_RELEASE_TEST refload drive off")
    # A ride's ordinary countdown lasts 100 ticks. This short qualification is
    # not a promise that all cargo settles; the readback retains pending work.
    time.sleep(6)
    receipt["after_drive"] = readbacks(console)
    if args.jfr:
        receipt["jfr_stop"] = jdk.stop_jfr(target, evidence, "load.jfr", name="k10", jcmd=jcmd)
        receipt["jfr"] = jdk.summarize_recording(evidence / "load.jfr", evidence / "jfr-events.json",
            jfr=args.java.with_name("jfr.exe" if args.java.suffix == ".exe" else "jfr"))
    receipt["observations"] = observation_plan()
