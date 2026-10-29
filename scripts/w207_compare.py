"""Recompute W207's paired native measurements from retained raw rows, including failures."""
import argparse
import hashlib
import json
from pathlib import Path
from w206_warmup_summary import summarize


def campaign(root):
    result = {}
    for directory in sorted(root.iterdir()):
        if not directory.is_dir() or not (directory / "w206-result.json").exists():
            continue
        summary = summarize(directory)
        rows = [json.loads(line) for line in (directory / "w206-ticks.jsonl").read_text().splitlines()]
        events = [json.loads(line) for line in (directory / "w206-events.jsonl").read_text().splitlines()]
        requests = []
        for event in events:
            if event["kind"] != "REQUEST":
                continue
            row = next(row for row in rows if row["tick"] == event["tick"])
            requests.append(dict(tick=event["tick"], code=event["code"], api_ms=event["duration_ns"] / 1e6,
                                 tick_ms=row["tick_ns"] / 1e6,
                                 observer_ms={key: row.get(key, 0) / 1e6 for key in
                                              ("transition_observer_ns", "column_observer_ns", "recorder_tail_ns")}))
        files = ("w206-ticks.jsonl", "w206-events.jsonl", "w206-result.json", "w206-columns.jsonl",
                 "server-escrow-consumer-timing.json", "host-samples.json", "server-gc.log", "server-profile.jfr")
        result[directory.name] = dict(native=json.loads((directory / "native-result.json").read_text()),
                                     requests=requests, timing=summary["timing"], readiness=summary["readiness"],
                                     fixture={key: value for key, value in summary["fixture"].items()
                                              if not key.endswith("_union")}, host=summary["host"],
                                     asynchronous_io_ms=summary["asynchronous_io_ms"],
                                     hashes={name: hashlib.sha256((directory / name).read_bytes()).hexdigest()
                                             for name in files if (directory / name).exists()})
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("before", type=Path)
    parser.add_argument("after", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    result = dict(before=campaign(args.before), after=campaign(args.after),
                  methodology="Raw core+END ticks and API spans, no observer subtraction. All work and checkpoint-bearing cohorts remain separate. Windows/JFR diagnostic workload; not reference-node/soak certification.")
    with args.output.open("x", encoding="utf-8") as stream:
        json.dump(result, stream, indent=2)


if __name__ == "__main__":
    main()
