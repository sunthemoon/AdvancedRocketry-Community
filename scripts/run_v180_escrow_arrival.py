#!/usr/bin/env python3
"""Store and force-stop governed descent/landing, then verify two native restarts with ARMED retained."""
from __future__ import annotations

import argparse
import copy
import math
from pathlib import Path

import run_v180_escrow_phases as phases

CASES = ("FD_DESCENT", "FD_LANDED")


def require_state(root, transfer, source, destination, cut, restarted):
    if source is not None or destination is None:
        raise phases.smoke.SmokeError("Arrival lost its destination or restored its removed source")
    flight = destination["RocketEntityData"]["flight_data"]
    expected_state = "LANDED" if restarted or cut == "FD_LANDED" else "DESCENT"
    started = flight["state_started_game_time"]
    frozen = transfer["destination_flight"]
    y = destination["Pos"][1]
    if (flight["state"] != expected_state or not math.isfinite(y)
            or started < frozen["state_started_game_time"]
            or expected_state == "LANDED" and (y != 100 or started < frozen["state_started_game_time"] + 80)
            or expected_state == "DESCENT" and not 112 < y < 136):
        raise phases.smoke.SmokeError("Invalid stored descent or terminal pose")
    expected = copy.deepcopy(frozen)
    expected["state"] = expected_state
    expected["state_started_game_time"] = started
    if expected_state == "LANDED":
        expected.pop("plan", None)
        expected.pop("active_transfer_id", None)
    if flight != expected:
        raise phases.smoke.SmokeError("Arrival changed non-transition flight fields")
    # Retain all of F5's exact checks after checking the only authorized F6 field differences.
    comparison = copy.deepcopy(destination)
    comparison["RocketEntityData"]["flight_data"] = copy.deepcopy(frozen)
    comparison["Pos"][1] = 148.0
    return phases.require_state(root, transfer, None, comparison, "FP_COMMITTED", True)


def inspect(world: Path, cut: str, restarted: bool):
    root = phases.ledger(world / "data" / phases.LEDGER)
    transfers = phases.journal(world)["transfers"]
    if len(transfers) != 1:
        raise phases.smoke.SmokeError("Arrival changed its retained journal count")
    path = world / "entities/r.0.0.mca"
    sources = [entry for entry in phases.record(path)["Entities"] if "RocketEntityData" in entry] if phases.slot_present(path, 132) else []
    destinations = [entry for entry in phases.record(path, 133)["Entities"] if "RocketEntityData" in entry] if phases.slot_present(path, 133) else []
    if len(sources) > 1 or len(destinations) > 1:
        raise phases.smoke.SmokeError("Arrival duplicated a physical candidate")
    invariant = require_state(root, transfers[0], sources[0] if sources else None,
                              destinations[0] if destinations else None, cut, restarted)
    return dict(invariant=invariant, sources=sources, destinations=destinations,
                files={name: phases.digest(world / name) for name in ("data/" + phases.LEDGER, "data/" + phases.JOURNAL, "entities/r.0.0.mca")})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_server", type=Path)
    for key in ("jar", "session-dir", "evidence-dir"):
        parser.add_argument("--" + key, type=Path, required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--lock-owner", required=True)
    parser.add_argument("--case", action="append", choices=CASES)
    parser.add_argument("--startup-timeout", type=float, default=240)
    args = parser.parse_args()
    try:
        phases.run(args, cases=CASES, inspect_state=inspect)
        return 0
    except (OSError, phases.smoke.SmokeError) as failure:
        print(f"FAIL: {failure}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
