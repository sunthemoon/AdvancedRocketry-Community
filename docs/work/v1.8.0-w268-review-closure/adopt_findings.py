"""Install reviewed findings only after draft markers are removed by authorized review.

This is a structural guard, not owner authorization or provenance clearance.
It is never called by the draft-only scratch verifier.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re


def check_findings(data: dict) -> None:
    findings = data.get("findings")
    if not isinstance(findings, list) or not findings:
        raise ValueError("A non-empty findings list is required.")
    for finding in findings:
        if not isinstance(finding, dict):
            raise ValueError("Each finding must be an object.")
        reviewer = finding.get("reviewer")
        if (not isinstance(reviewer, str) or not reviewer.strip() or
                re.search(r"[<>]|PENDING|PLACEHOLDER|\bTODO\b|\bTBD\b", reviewer, re.I)):
            raise ValueError("Unresolved reviewer placeholder: " + str(finding.get("asset")))
        if "draft_status" in finding:
            raise ValueError("Remove draft_status after recorded review: " + str(finding.get("asset")))


def adopt(source: Path, output: Path) -> None:
    raw = source.read_bytes()
    check_findings(json.loads(raw))
    # Validate the entire document before touching the destination. Refusal
    # must leave an existing tracked findings file byte-identical.
    output.write_bytes(raw)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--findings", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    adopt(args.findings, args.output)
