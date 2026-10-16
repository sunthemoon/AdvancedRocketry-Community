#!/usr/bin/env python3
"""Force-stop live F7/F8 boundaries and verify native cleanup on two restarts."""
from __future__ import annotations

import argparse
import copy
from pathlib import Path

import run_v180_escrow_phases as phases

CASES = tuple("FT_" + stage + "_" + point
              for stage in ("HANDOFF", "JOURNAL", "PRUNE")
              for point in ("BEFORE", "REPLACED", "ACK"))


def require_state(root, transfers, source, destination, cut, restarted):
    if cut not in CASES or source is not None or destination is None:
        raise phases.smoke.SmokeError("Terminal cut lost its sole survivor or restored its source")
    pruned = restarted or cut in ("FT_PRUNE_REPLACED", "FT_PRUNE_ACK")
    has_journal = not restarted and (cut.startswith("FT_HANDOFF") or cut == "FT_JOURNAL_BEFORE")
    arms = root["armed_flights"]
    if (root["issued_launch_serial"] != 1 or root["records"] or root["floors"] or root["tombstones"]
            or root["rocket_authorities"] or len(arms) != int(not pruned) or len(transfers) != int(has_journal)):
        raise phases.smoke.SmokeError("Terminal cut violated the ledger/journal acknowledgement order")
    data = destination["RocketEntityData"]
    flight, wrapper = data["flight_data"], data["resource_escrow"]
    debits = flight["fuel"]["committed_debits"]
    if (flight["state"] != "LANDED" or "plan" in flight or "active_transfer_id" in flight
            or flight["fuel"]["amount"] != 500 or len(debits) != 1
            or flight["passengers"]["assignments"] or destination.get("Passengers", [])
            or wrapper != dict(schema_version=1, ledger_id=root["ledger_id"], resource_epoch=0, markers=[])
            or destination["Pos"] != [80.5, 100., 64.5] or destination["Motion"] != [0., 0., 0.]
            or destination["id"] != "advancedrocketrycommunity:rocket" or data["schema_version"] != 3
            or not destination.get("NoGravity") or destination.get("OnGround", False)):
        raise phases.smoke.SmokeError("Terminal cut changed its native survivor, debit, epoch or manifest")
    if arms:
        arm = arms[0]
        expected = "ARMED" if cut == "FT_HANDOFF_BEFORE" else "HANDED_OFF"
        if (arm["outcome"] != expected or arm["serial"] != 1 or arm["ledger"] != root["ledger_id"]
                or arm["d"] != destination["UUID"] or arm["src"] == destination["UUID"]
                or arm["l"] != data["assembly_transaction_id"] or arm["o"] != data["owner_id"]
                or arm["t"] != debits[0]["transaction_id"]):
            raise phases.smoke.SmokeError("Terminal cut lost its exact transfer or successor authority")
    if transfers:
        transfer = transfers[0]
        armed = copy.deepcopy(arms[0]); armed["outcome"] = "ARMED"
        expected_flight = copy.deepcopy(transfer["destination_flight"])
        expected_flight["state"] = "LANDED"
        expected_flight["state_started_game_time"] = flight["state_started_game_time"]
        expected_flight.pop("plan", None); expected_flight.pop("active_transfer_id", None)
        if (transfer["schema_version"] != 3 or transfer["phase"] != "COMMITTED"
                or transfer["native_transfer"]["armed"] != armed
                or transfer["transfer_id"] != debits[0]["transaction_id"]
                or transfer["destination_entity_id"] != destination["UUID"]
                or transfer["destination_snapshot"] != data["snapshot"] or flight != expected_flight):
            raise phases.smoke.SmokeError("Terminal cut changed its frozen journal or authorized landing projection")
    # Ignore vanilla timers, not adapter state. Both restarts must preserve this whole projection.
    projection = {key: destination.get(key) for key in ("UUID", "id", "RocketEntityData", "Pos", "Motion",
                                                       "Rotation", "NoGravity", "OnGround", "Invulnerable")}
    return dict(ledger_id=root["ledger_id"], issued=1, survivor=projection)


def inspect(world: Path, cut: str, restarted: bool):
    root = phases.ledger(world / "data" / phases.LEDGER)
    transfers = phases.journal(world)["transfers"]
    path = world / "entities/r.0.0.mca"
    slots = [[entry for entry in phases.record(path, slot)["Entities"] if "RocketEntityData" in entry]
             if phases.slot_present(path, slot) else [] for slot in (132, 133)]
    if len(slots[0]) > 1 or len(slots[1]) > 1:
        raise phases.smoke.SmokeError("Terminal native slots contain duplicate rockets")
    invariant = require_state(root, transfers, slots[0][0] if slots[0] else None,
                              slots[1][0] if slots[1] else None, cut, restarted)
    return dict(invariant=invariant, ledger=root, journal=transfers, slots=slots,
                files={name: phases.digest(world / name) for name in
                       ("data/" + phases.LEDGER, "data/" + phases.JOURNAL, "entities/r.0.0.mca")})


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
        phases.run(args, cases=CASES, inspect_state=inspect,
                   pause_prefix="ARCE_RELEASE_TERMINAL_PAUSED", result_prefix="ARCE_RELEASE_TERMINAL_RESULT",
                   result_suffix=r" journal=idle armed=0 fuel=500 epoch=0")
        return 0
    except (OSError, phases.smoke.SmokeError) as failure:
        print(f"FAIL: {failure}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
