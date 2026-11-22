"""K09 sampling/restart and crash-oracle support; never writes native save data."""
from datetime import datetime, timezone
import csv
import json
import time

from scripts.campaign_observe import raw_ticks
from scripts.campaign_observe.common import schedule
from scripts.campaign_prep import k03_chain as chain


def utc_now():
    return datetime.now(timezone.utc).isoformat()


def source_for(args):
    from . import k09_missions as m
    folder = args.output / args.variant
    if args.mode == "build" or args.mode.startswith(("baseline-", "cut-")):
        return m.PARENT
    if args.mode == "load-restart":
        previous = folder / "load-start"
        receipt = json.loads((previous / "receipt.json").read_text())
        stopped = datetime.fromisoformat(receipt["stopped_utc"])
        chain.require((datetime.now(timezone.utc) - stopped).total_seconds() >= args.stop_seconds,
                      "Stop interval has not elapsed; release lock before waiting")
        return previous / "source-world"
    if args.mode.startswith("recover-"):
        _, cut, step = args.mode.split("-")
        return folder / ("cut-" + cut if step == "1" else "recover-" + cut + "-1") / "source-world"
    return folder / "source-world"


def sample(console, evidence, args, receipt):
    """K15 raw parser/merger; discard warm-up, poll bounded 100-tick rings at 1 s."""
    receipt["sampling"] = dict(warmup_seconds=args.warmup_seconds, sample_seconds=args.sample_seconds,
        stop_seconds=args.stop_seconds, warmup_discarded=True, interval_seconds=1,
        qualification=args.qualification, reference_budget_comparison=False)
    time.sleep(args.warmup_seconds)
    polls = []
    with (evidence / "tick-replies.log").open("x", encoding="utf-8") as stream:
        for _ in schedule(args.sample_seconds, 1):
            line = console.query(raw_ticks.RAW_TICK_COMMAND, raw_ticks.PREFIX)
            stream.write(utc_now() + " " + line + "\n")
            stream.flush()
            polls.append(raw_ticks.parse_raw_tick_reply(line))
    summary = raw_ticks.summarize_raw_ticks(polls, 500)
    # The threshold is descriptive only; do not apply REF budgets on this PC.
    summary.pop("reference_budgets")
    chain.require(summary["tick_count"] > 0, "No completed ticks sampled")
    chain.write(evidence / "tick-summary.json", summary)
    with (evidence / "ticks.csv").open("x", encoding="ascii", newline="") as stream:
        writer = csv.writer(stream)
        writer.writerow(("server_tick", "total_nanos"))
        writer.writerows(raw_ticks.merge_polls(polls)["samples"])
    receipt["sampling"]["summary"] = summary


def recover(console, receipt, args):
    from . import k09_missions as m
    x, y, z = m.TERMINAL
    console.query(f"forceload add {x} {z}", "force load")
    deadline = time.monotonic() + 60
    while True:
        line = console.query(m.HOOK + f"terminal {x} {y} {z} inspect", r"ARCE_RELEASE_TEST inspect")
        if "loaded=true" in line:
            break
        chain.require(time.monotonic() < deadline, "Terminal did not load")
        time.sleep(.1)
    # Save observation then allow normal reconciliation, without an artificial receipt.
    console.query("save-all flush", r"Saved the game")
    time.sleep(1)
    console.query(m.HOOK + "perf flush 1", r"ARCE_RELEASE_TEST flush")
    time.sleep(1)
    receipt["terminal"] = console.query(m.HOOK + f"terminal {x} {y} {z} inspect", r"ARCE_RELEASE_TEST inspect")
    if args.mode.endswith("-1"):
        # Synthetic logical retention qualification only; NOT the G02 online/offline test.
        receipt["reclaim"] = console.query(m.HOOK + "advance 1220", r"ARCE_RELEASE_TEST advance")
        time.sleep(2)
    console.query(f"forceload remove {x} {z}", "force load")


def check_cut(args, before, after, world, receipt):
    from . import k09_missions as m
    terminal, tickets = m.terminal_disk(world)
    mission = m.resource_mission(after)
    previous = m.resource_mission(before)
    reward = sum(entry["count"] for entry in mission["reward"])
    buffer = sum(entry["count"] for entry in terminal["reward_buffer"])
    receipt.update(mission=mission, terminal_disk=terminal, reward_items=reward, buffer_items=buffer,
                   forced_chunks=tickets, instance_scope="Logical registry records, no instance chunk/region")
    chain.require(mission["reward"] == previous["reward"], "Reward snapshot changed")
    chain.require(before["research_accounts"] == after["research_accounts"], "Resource cut changed research")
    if args.mode == "cut-A":
        chain.require(mission["status"] == "ready" and buffer == reward and len(terminal["receipts"]) == 1,
                      "Cut A did not leave registry behind the chunk reward write")
    elif args.mode == "cut-B":
        chain.require(mission["status"] == "claimed" and not mission["acknowledged"]
                      and buffer == 0 and not terminal["receipts"], "Cut B did not precede the chunk reward write")
    else:
        chain.require(mission["status"] == "claimed" and buffer == reward, "Recovery did not preserve exactly one reward")
        chain.require(bool(mission["acknowledged"]) and not terminal["receipts"], "Acknowledgement/receipt drop incomplete")
        instance_id = next(iter(chain.saved_uuids(mission["instance_id"])))
        chain.require(not any(instance_id in chain.saved_uuids(i["instance_id"]) for i in after["instances"]),
                      "Depleted logical instance was not reclaimed")
        receipt["reclaimed_instance"] = instance_id
    # Compare native persistent tickets across the cut, including other dimensions.
    source_terminal, source_tickets = m.terminal_disk(source_for(args))
    chain.require(ticket_projection(tickets) == ticket_projection(source_tickets), "Unexpected persistent chunk ticket")
    receipt["tickets_unchanged"] = True


def ticket_projection(tickets):
    """Vanilla serializes a set with unstable iteration order; retain raw replies too."""
    result = json.loads(json.dumps(tickets))
    for root in result.values():
        values = root["data"]["Forced"]
        chain.require(all(type(v) is int for v in values) and len(values) == len(set(values)), "Invalid forced set")
        root["data"]["Forced"] = sorted(values)
    return result
