"""Reproduce the measured preload inventory and first/later relaunch comparisons without pooling them."""
from __future__ import annotations
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re

from w212_summary import summarize

PACKAGE = "io.github.sunthemoon.advancedrocketrycommunity."


def named_mod_classes(names):
    # JFR prints hidden class identities with a process-specific +0x suffix.
    return sorted({name for name in names if name.startswith(PACKAGE) and "+0x" not in name})


def inventory(root):
    result = {}
    for mode in ("full", "timing"):
        files = sorted((root / ("native-before-" + mode)).glob("*/attribution.json"))
        if len(files) != 9:
            # The four seed stages and pre-restart cut have no relaunch recorder report.
            raise ValueError(f"Expected 9 W212 relaunch reports, got {len(files)}")
        names = [event["detail"] for path in files for row in json.loads(path.read_text())["requests"]
                 for event in row["class_loads"]]
        result[mode] = dict(events=len(names), hidden_events=sum("+0x" in name for name in names),
                            packages=dict(sorted(Counter(name.split("+")[0].rsplit(".", 1)[0] for name in names).items())),
                            named_mod_classes=named_mod_classes(names),
                            inputs={str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest() for p in files})
    if result["full"]["named_mod_classes"] != result["timing"]["named_mod_classes"]:
        raise ValueError("Observer modes disagree on the measured class inventory")
    return result


def startup_rows(log):
    return [json.loads(match.group(1)) for match in re.finditer(r"W215_STARTUP (\{[^\r\n]*\})", log)]


def campaign(root, java):
    result = dict(source=json.loads((root / "runner-result.json").read_text()),
                  jars=json.loads((root / "jar-inventory.json").read_text()), stages={})
    for stage in sorted(root.iterdir()):
        if not (stage / "native-result.json").is_file():
            continue
        native = json.loads((stage / "native-result.json").read_text())
        metrics = startup_rows((stage / "native.log").read_text(encoding="utf-8"))
        attribution = stage / "attribution.json"
        if not (stage / "w212-spans.json").exists():
            report = dict(requests=[], qualified=None, rounds={})
        elif not attribution.exists():
            report = summarize(stage, java)
            with attribution.open("x", encoding="utf-8") as stream:
                json.dump(report, stream, indent=2)
        else:
            report = json.loads(attribution.read_text())
        requests = []
        for row in report["requests"]:
            requests.append(dict(round=row["tick"]["round"], api_ms=row["request"]["duration_ns"] / 1e6,
                                 tick_ms=row["tick"]["tick_ns"] / 1e6,
                                 class_load_ms=row["class_load_union_ns"] / 1e6,
                                 class_load_events=len(row["class_loads"]),
                                 named_mod_classes=named_mod_classes(e["detail"] for e in row["class_loads"]),
                                 exclusive_ms={k: v / 1e6 for k, v in row["exclusive_ns"].items()}))
        result["stages"][stage.name] = dict(native_status=native["status"], qualified=report["qualified"],
                                          startup=metrics, relaunches=requests, rounds=report["rounds"],
                                          errors=native["errors"], overload_warnings=native["overload_warnings"])
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", type=Path)
    parser.add_argument("--inventory", action="store_true")
    parser.add_argument("--java", default="C:/Program Files/Java/jdk-17.0.7/bin/java.exe")
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if args.inventory:
        result = inventory(args.root)
    else:
        result = {name: campaign(args.root / name, args.java) for name in
                  ("native-before-full", "native-before-timing", "native-after-full", "native-after-timing")}
    with args.output.open("x", encoding="utf-8") as stream:
        json.dump(result, stream, indent=2)


if __name__ == "__main__":
    main()
