#!/usr/bin/env python3
"""W44L: refuse four production-migrated roots on a pinned pre-W44 jar, preserving every raw file."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

if __package__:
    from . import run_v180_first_event_cuts as cuts
else:
    import run_v180_first_event_cuts as cuts

CASES = {case: (cuts.JOURNAL if case.startswith("FM") else cuts.STATIONS, "REFUSE", None, None, None)
         for case in ("FM11a", "FM11b", "FW10a", "FW10b")}


def run(args):
    args.case_contracts = CASES
    report = cuts.run(args)
    report.update(harness_commit=args.harness_commit, fixture_commit=args.harness_commit,
                  scope="W44L migrated V roots: pre-W44 downgrade refusal, initial start plus two restarts")
    try:
        for row in report["cases"]:
            expected = "ARCE-BETA-2001" if row["case"].startswith("FM") else "ARCE-BETA-2000"
            for entry in row["starts"]:
                if entry["diagnostic"] != expected:
                    raise cuts.smoke.SmokeError(f"{row['case']}: wrong refusal diagnostic: {entry['diagnostic']}")
                text = (args.evidence_dir / entry["log"]).read_text(encoding="utf-8")
                reason = "uses future schema 3" if row["case"].startswith("FM") else "has an invalid format epoch"
                if reason not in text:
                    raise cuts.smoke.SmokeError(f"{row['case']}: no exact schema/epoch refusal reason")
                entry["refusal_reason"] = reason
        return report
    except Exception as failure:
        report.update(status="FAILED", error=str(failure))
        raise
    finally:
        (args.evidence_dir / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_server", type=Path)
    for name in ("jar", "fixtures", "session-dir", "evidence-dir"):
        parser.add_argument("--" + name, type=Path, required=True)
    for name in ("source-commit", "harness-commit", "java"):
        parser.add_argument("--" + name, required=True)
    parser.add_argument("--case", action="append", choices=CASES)
    parser.add_argument("--startup-timeout", type=float, default=240)
    args = parser.parse_args()
    try: run(args); return 0
    except (OSError, cuts.smoke.SmokeError) as failure: print(f"FAIL: {failure}"); return 1


if __name__ == "__main__": raise SystemExit(main())
