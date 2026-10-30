"""Attribute W206 request ticks using JDK-only JFR extraction (sample counts, not CPU shares)."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import subprocess
from w199_jfr_summary import summarize


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    rows = [json.loads(line) for line in (args.directory / "w206-ticks.jsonl").read_text().splitlines()]
    # The reusable correlator takes CSV-like fields and prints its largest phase ticks.
    for row in rows:
        row["end_epoch_ms"] = row["epoch_ms"]
        if row["phase"] == "REQUEST":
            row["phase"] = "PROOF" if row["callbacks"] else "PREPARATION"
    command = ["C:/Program Files/Java/jdk-17.0.7/bin/java.exe",
               str(Path(__file__).with_name("W199JfrExtract.java")), str(args.directory / "server-profile.jfr")]
    with subprocess.Popen(command, stdout=subprocess.PIPE, text=True, encoding="utf-8") as process:
        result = summarize(rows, process.stdout)
        if process.wait() != 0:
            raise RuntimeError("JFR extraction failed")
    output = args.directory / "w207-jfr-summary.json"
    with output.open("x", encoding="utf-8") as stream:
        json.dump(result, stream, indent=2)
    print(output)


if __name__ == "__main__":
    main()
