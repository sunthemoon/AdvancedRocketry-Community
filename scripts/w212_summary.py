"""Direct elapsed attribution; children are removed from their parent, never from the whole tick."""
from collections import defaultdict
import argparse
import json
from pathlib import Path
import subprocess
from w206_warmup_summary import summarize_rows, qualify


def partition(rows, tick_ns):
    totals = defaultdict(int)
    for row in rows:
        children = sum(r["end"] - r["start"] for r in rows
                       if r["depth"] == row["depth"] + 1 and row["start"] <= r["start"] and r["end"] <= row["end"])
        exclusive = row["end"] - row["start"] - children
        if exclusive < 0: raise ValueError("Overlapping or malformed spans")
        totals[row["label"]] += exclusive
    remainder = tick_ns - sum(totals.values())
    if remainder < 0: raise ValueError("Spans exceed whole tick")
    totals["unassigned"] += remainder
    return dict(totals)


def union(intervals):
    merged = []
    for start, end in sorted(intervals):
        if end <= start: continue
        if merged and start <= merged[-1][1]: merged[-1][1] = max(end, merged[-1][1])
        else: merged.append([start, end])
    return merged


def summarize(root, java):
    ticks = [json.loads(s) for s in (root / "w206-ticks.jsonl").read_text().splitlines()]
    events = [json.loads(s) for s in (root / "w206-events.jsonl").read_text().splitlines()]
    recorder = json.loads((root / "w212-spans.json").read_text())
    if recorder["capped"] or recorder["depth"]: raise ValueError("Incomplete recorder")
    requests = {e["tick"]: e for e in events if e["kind"] == "REQUEST" and e["code"] == "JOURNAL_PENDING"}
    reports = []
    for tick in ticks:
        if tick["tick"] not in requests: continue
        lo, hi = tick["window_start_ns"], tick["window_end_ns"]
        spans = [dict(r, start=max(r["start"], lo), end=min(r["end"], hi)) for r in recorder["rows"]
                 if r["start"] < hi and r["end"] > lo]
        report = dict(tick=tick, request=requests[tick["tick"]],
                      exclusive_ns=partition(spans, tick["tick_ns"]), spans=spans, class_loads=[], pauses=[])
        reports.append(report)
    # JFR uses epoch Instants; the recorder anchors monotonic nanos to millisecond wall time.
    # Its <1 ms quantization uncertainty is disclosed; elapsed spans do not use that conversion.
    jfr = root / "server-profile.jfr"
    if jfr.exists():
        command = [java, str(Path(__file__).with_name("W212JfrExtract.java")), str(jfr)]
        shift = recorder["clockNanos"] - recorder["clockEpochMillis"] * 1_000_000
        with subprocess.Popen(command, stdout=subprocess.PIPE, text=True, encoding="utf-8") as process:
            for line in process.stdout:
                kind, start, end, thread, detail = line.rstrip("\n").split("\t", 4)
                start, end = int(start) + shift, int(end) + shift
                for report in reports:
                    lo, hi = report["tick"]["window_start_ns"], report["tick"]["window_end_ns"]
                    if start >= hi or end <= lo: continue
                    if kind == "jdk.ClassLoad" and thread == "Server thread":
                        report["class_loads"].append(dict(start=max(lo, start), end=min(hi, end), detail=detail))
                    elif kind != "jdk.ClassLoad" and (thread == "Server thread" or kind == "jdk.GCPhasePause"):
                        report["pauses"].append(dict(kind=kind, start=max(lo, start), end=min(hi, end)))
            if process.wait(): raise RuntimeError("JFR extraction failed")
    for report in reports:
        merged = union((r["start"], r["end"]) for r in report["class_loads"])
        report["class_load_union_ns"] = sum(hi - lo for lo, hi in merged)
        # Overlay, not a separate additive bucket: class loading occurs inside directly timed calls.
        report["class_load_overlay_ns"] = {
            label: sum(end - start for start, end in union(
                (max(lo, r["start"]), min(hi, r["end"])) for lo, hi in merged
                for r in report["spans"] if r["label"] == label))
            for label in sorted({r["label"] for r in report["spans"]})}
    fixture = json.loads((root / "w206-result.json").read_text())
    measured = [r for r in ticks if r["phase"] not in ("SETUP", "SEED", "RECOVER", "REPEAT_SETUP")]
    return dict(requests=reports, timing=summarize_rows(measured),
                rounds={str(i): summarize_rows([r for r in measured if r["round"] == i])
                        for i in sorted({r["round"] for r in measured})},
                qualified=qualify(fixture) and all(qualify(r) for r in fixture.get("completed_prior_rounds", [])),
                methodology="Direct nested elapsed spans on the owner thread; exclusive buckets plus unassigned equal "
                "the whole native-core + Forge-END tick. JFR class-load union is an overlapping overlay, never added "
                "to or subtracted from those buckets; wall-clock correlation has <1 ms anchor quantization. "
                "Repeat reset/setup is separate; first cold flights and later generated flights are not pooled for P99.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", type=Path)
    parser.add_argument("--java", default="C:/Program Files/Java/jdk-17.0.7/bin/java.exe")
    args = parser.parse_args()
    for root in sorted(args.root.iterdir()):
        if not (root / "w212-spans.json").exists(): continue
        result = summarize(root, args.java)
        with (root / "attribution.json").open("x", encoding="utf-8") as stream:
            json.dump(result, stream, indent=2)
        print(root.name, "qualified=" + str(result["qualified"]), "relaunches=" + str(len(result["requests"])))
