"""W206 diagnostic oracle. Missing measurements are not successful zero measurements."""
from __future__ import annotations
import argparse
import json
import re
from pathlib import Path
from w197_cost_summary import statistics, host_load


def stats(rows):
    result = statistics([row["tick_ns"] for row in rows], 1e6)
    if result["count"]:
        result["docs17"] = dict(mean_le_25=result["mean"] <= 25, p95_le_50=result["p95"] <= 50,
                                p99_le_100=result["p99"] <= 100, max_le_500=result["maximum"] <= 500)
    return result


def summarize_rows(rows):
    # The native FLIGHT phase ends only after ARMED pruning/F8, whereas the raw work flag primarily sees W/T.
    # Include that retained cleanup and every delivered owner-thread checkpoint; never classify callbacks as idle.
    return dict(all=stats(rows), work=stats([r for r in rows if r["work"] or r["callbacks"] or r["phase"] == "FLIGHT"]),
                callbacks=stats([r for r in rows if r["callbacks"]]),
                phases={p: stats([r for r in rows if r["phase"] == p]) for p in sorted({r["phase"] for r in rows})},
                timing_qualified=bool(rows) and all(r.get("core_ns", 0) > 0 and r.get("end_ns", -1) >= 0
                    and r["tick_ns"] == r["core_ns"] + r["end_ns"] for r in rows))


def readiness(events):
    starts = {}
    requests = {(e.get("logical"), e.get("request")): e["ns"] - e["duration_ns"]
                for e in events if e["kind"] == "REQUEST"}
    for event in events:
        key = event.get("logical"), event.get("request")
        if event["kind"] == "STARTED":
            starts[key] = dict(logical=key[0], request=key[1], tick=event["tick"], ns=event["ns"],
                               allocation_tick=event.get("started", event["tick"]), allocation_ticks=None,
                               ticks=None, ms=None, request_to_ready_ms=None, status="NOT_READY")
        elif event["kind"] == "READY" and key in starts:
            row = starts[key]
            row.update(ticks=event["tick"] - row["tick"], ms=(event["ns"] - row["ns"]) / 1e6, status="READY")
            row["allocation_ticks"] = event["tick"] - row["allocation_tick"]
            if key in requests:
                row["request_to_ready_ms"] = (event["ns"] - requests[key]) / 1e6
    return list(starts.values())


def phase_latencies(events):
    rows = [dict(phase=e["to"], tick=e["tick"], ns=e["ns"], ticks=None, ms=None)
            for e in events if e["kind"] == "PHASE"]
    for previous, current in zip(rows, rows[1:]):
        previous.update(ticks=current["tick"] - previous["tick"], ms=(current["ns"] - previous["ns"]) / 1e6)
    return rows


def qualify(result):
    return (result.get("outcome") == "MEASURED" and result.get("cold") is True
            and result.get("no_other_ticket") is True and result.get("source_unchanged") is True
            and result.get("selected_columns", 0) == result.get("expected_columns", -1)
            and all(result.get(k) is True for k in ("f2", "handover", "f8", "landing_witness"))
            and result.get("tickets_after_f8") == [] and result.get("warmups_after_f8") == [])


def gc_pauses(log):
    values = [float(value) for value in re.findall(r"\[info\]\[gc\s*\].*Pause.* ([0-9.]+)ms$", log, re.MULTILINE)]
    return dict(statistics(values), total_ms=sum(values))


def summarize(root):
    data = json.loads((root / "w206-result.json").read_text())
    ticks = [json.loads(line) for line in (root / "w206-ticks.jsonl").read_text().splitlines()]
    events = [json.loads(line) for line in (root / "w206-events.jsonl").read_text().splitlines()]
    measured = [r for r in ticks if r["phase"] not in ("SETUP", "SEED", "RECOVER", "REPEAT_SETUP")]
    samples = json.loads((root / "host-samples.json").read_text())
    host = host_load(samples)
    host["scope"] = "One-second samples over the entire native process, including startup/setup and shutdown; not only measured phases."
    gc_file = root / "server-gc.log"
    consumer_file = root / "server-escrow-consumer-timing.json"
    consumer = json.loads(consumer_file.read_text()) if consumer_file.exists() else {}
    io_events = consumer.get("events", [])
    return dict(fixture=data, flight_qualified=qualify(data), timing=summarize_rows(measured),
                readiness=readiness(events), phase_latencies=phase_latencies(events), host=host,
                gc_pause_ms=gc_pauses(gc_file.read_text()) if gc_file.exists() else {"available": False},
                asynchronous_io_ms={phase: statistics([e["value"] for e in io_events if e["phase"] == phase], 1e6)
                                    for phase in ("own_store_io", "force_io", "readback_io")},
                consumer_timing_capped=consumer.get("capped"),
                io_bytes={k: max((r.get(k, 0) for r in samples), default=0) for k in ("read_bytes", "write_bytes", "other_bytes")},
                methodology="Nearest-rank percentiles; native core plus Forge END. Work includes product requests, "
                "live warm-ups and flights through F8 pruning, including callback-only ticks; callbacks are server-thread escrow checkpoint-bearing ticks, not exclusive "
                "callback durations. Complete process RSS and IO include setup. All-phase samples are retained. "
                "Windows short diagnostic workload, not reference-node/soak or real-client Gate evidence.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("evidence", type=Path)
    args = parser.parse_args()
    result = summarize(args.evidence)
    (args.evidence / "summary.json").write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, indent=2))
