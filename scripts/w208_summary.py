"""Summarize W208 native observations without promoting holds or diagnostic controls to availability success."""
import argparse
import json
import math
from pathlib import Path
import re


def stats(values):
    values = sorted(values)
    result = dict(samples=len(values))
    if values:
        result.update(mean=sum(values) / len(values), maximum=values[-1],
                      **{name: values[max(0, math.ceil(len(values) * p) - 1)] for name, p in (("p50", .5), ("p95", .95), ("p99", .99))})
    return result


def sample(raw, log):
    events = raw["events"]
    timings = {label: stats([e["ns"] / 1e6 for e in events if e["label"] == label])
               for label in ("ROOT_capture", "ROOT_write", "ROOT_publish", "WRITE_REQUEST", "IO_JOIN")}
    deadlines = [line for line in log.splitlines() if "ARCE_DISASSEMBLY_DURABILITY_HELD" in line and "reason=DEADLINE" in line]
    captures = [e["tick"] for e in events if e["label"] == "ROOT_capture"]
    publishes = [e["tick"] for e in events if e["label"] == "ROOT_publish"]
    return dict(status=raw["status"], completion=raw.get("completion"), observed_ticks=raw["observation_ticks"],
                observed_seconds=raw["observation_ns"] / 1e9, timing_ms=timings,
                tick_ms=stats([row["tick_ns"] / 1e6 for row in raw["ticks"]]), deadline_warnings=deadlines,
                stop_close=raw["stop_close"], near_root_bytes=raw.get("near_root_bytes"), traffic=raw.get("traffic"),
                unknown_publications=sum(e["label"] == "PUBLISHED_UNKNOWN" for e in events),
                capture_to_publish_ticks=[b - a for a, b in zip(captures, publishes)],
                checkpoint_request_ticks=[e["tick"] for e in events if e["label"] == "WRITE_REQUEST"],
                effect_checks=sum("authorized" in e for e in events))


def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument("evidence", type=Path)
    args = parser.parse_args(); rows = []
    for result in sorted(args.evidence.glob("*/result.json")):
        case = json.loads(result.read_text()); row = dict(case=case["case"], status=case["status"], stages=[])
        for stage in ("start", "recover1", "recover2"):
            raw = result.parent / (stage + ".json")
            if not raw.exists(): continue
            item = sample(json.loads(raw.read_text()), (result.parent / (stage + ".log")).read_text(errors="replace"))
            process = json.loads((result.parent / (stage + "-process.json")).read_text())
            item.update(stage=stage, stop_to_exit_seconds=process["stop_to_exit_seconds"],
                        peak_rss_bytes=max((s["peak_rss"] for s in process["rss_samples"]), default=0))
            gc = (result.parent / (stage + "-gc.log")).read_text(errors="replace")
            item["gc_pause_ms"] = stats([float(m) for m in re.findall(r"Pause[^\n]* ([0-9.]+)ms", gc)])
            native = result.parent / (stage + "-native.json")
            if native.exists():
                n = json.loads(native.read_text()); item.update(authority=n["authority"], effective=n["effective"],
                        u=len(n["records"]), carriers=len(n["sealed"]), entities=len(n["entities"]))
            row["stages"].append(item)
        rows.append(row)
    (args.evidence / "summary.json").write_text(json.dumps(rows, indent=2), encoding="utf-8")
    print(json.dumps(rows, indent=2))


if __name__ == "__main__":
    main()
