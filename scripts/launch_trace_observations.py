"""Read the gated launch checks independently of the unchanged resource/cut oracles."""
from __future__ import annotations

import re

from run_dedicated_server_smoke import SmokeError

TRACE = re.compile(r"ARCE_LAUNCH_TRACE cut=(\S+) request=(\S+) check=(\w+) detail=(.*)$")


def observations(lines: list[str]) -> list[dict]:
    requests = {}
    for line in lines:
        match = TRACE.search(line)
        if not match:
            continue
        cut, request, check, detail = match.groups()
        row = requests.setdefault(request, dict(cut=cut, request=request, events=[]))
        if row["cut"] != cut:
            raise SmokeError("Launch trace changed case")
        row["events"].append(dict(check=check, detail=detail))
    for row in requests.values():
        events = row["events"]
        checks = [e["check"] for e in events]
        results = [e["detail"] for e in events if e["check"] == "RESULT"]
        if checks.count("REQUEST") != 1 or len(results) != 1:
            raise SmokeError("Launch trace lacks a unique request/result")
        row["code"] = results[0]
        decisions = [e for e in events if e["check"] in (
            "BEGIN_PRECONDITION", "TRANSFER_PRECONDITION", "FLIGHT_PRECONDITION", "PREPARE_EXCEPTION",
            "COUNTDOWN", "WARMUP", "CHECKPOINT_REFUSED")]
        if len(decisions) != 1:
            raise SmokeError("Launch trace lacks a unique answering check")
        row["answer"] = decisions[0]
        row["prepare_reached"] = "PREPARE_ENTER" in checks
        if decisions[0]["check"] == "COUNTDOWN" and not (row["prepare_reached"] and "PREPARE_OK" in checks):
            raise SmokeError("COUNTDOWN trace did not pass governed prepare")
        if decisions[0]["check"] == "PREPARE_EXCEPTION" and not row["prepare_reached"]:
            raise SmokeError("Exception trace did not enter prepare")
    return list(requests.values())
