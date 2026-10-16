#!/usr/bin/env python3
"""Cut both sides of each returned-source/joint-slot store, force and readback; restart twice."""
from __future__ import annotations

import argparse
import copy
from pathlib import Path

import run_v180_escrow_phases as phases

CASES = tuple(f"RT_{kind}_{layout}_{slot}_{operation}_{side}"
              for kind in ("CANCEL", "PAD", "SPAWN") for layout, slots in (("SEPARATE", (0, 1)), ("SHARED", (0,)))
              for slot in slots for operation in ("STORE", "FORCE", "READBACK") for side in ("BEFORE", "AFTER"))


def require_state(root, transfer, source, destination, cut, restarted):
    _, kind, layout, slot, operation, side = cut.split("_")
    volatile_return = kind in ("CANCEL", "SPAWN") and slot == "0" and operation == "STORE" and side == "BEFORE"
    if (len(root["armed_flights"]) != 1 or root["issued_launch_serial"] != 1 or root["records"] or root["floors"]
            or root["tombstones"] or root["rocket_authorities"]):
        raise phases.smoke.SmokeError("Return changed or released its retained ledger authority")
    arm = root["armed_flights"][0]
    if (transfer["schema_version"] != 3 or arm != transfer["native_transfer"]["armed"] or arm["outcome"] != "ARMED"
            or arm["serial"] != 1 or arm["epoch"] != 7 or arm["src"] != transfer["source_entity_id"]
            or arm["t"] != transfer["transfer_id"]):
        raise phases.smoke.SmokeError("Return changed its fixed physical authority")
    if (source is None) == (destination is None):
        raise phases.smoke.SmokeError("Return lost or duplicated its physical survivor")
    arrived = destination is not None
    if arrived and not (restarted and volatile_return and transfer["phase"] == "COMMITTED"
                        and transfer["destination_entity_id"] == arm["d"]):
        raise phases.smoke.SmokeError("Return fabricated a destination after a durable return")
    if not arrived and (transfer["phase"] != ("DESTINATION_SPAWNED" if kind == "SPAWN" else "PREPARED")
                        or (transfer.get("destination_entity_id") != arm["d"] if kind == "SPAWN" else "destination_entity_id" in transfer)):
        raise phases.smoke.SmokeError("Return changed its retained PREPARED record")
    fuel = transfer["source_flight"]["fuel"]
    prior = fuel["committed_debits"]
    if (fuel["amount"] != 1000 or len(prior) != 1 or prior[0]["transaction_id"] == arm["t"]
            or transfer["source_flight"]["passengers"]["assignments"]):
        raise phases.smoke.SmokeError("Return fixture lost its prior debit or empty native manifest")
    end = "destination" if arrived else "source"
    entity = destination if arrived else source
    data = entity["RocketEntityData"]; flight = data["flight_data"]
    projection = transfer["native_transfer"][end]
    initial = copy.deepcopy(transfer[end + "_flight"])
    initial.update(projection["flight_changes"])
    for field in projection["flight_absent"]: initial.pop(field)
    time = flight["state_started_game_time"]
    returned = flight["state"] == "FUELED" and "plan" not in flight
    if arrived:
        expected = copy.deepcopy(initial)
        expected.update(state="LANDED", state_started_game_time=time)
        expected.pop("plan", None); expected.pop("active_transfer_id", None)
        if (time < initial["state_started_game_time"] + 80 or expected["fuel"]["amount"] != 500
                or expected["fuel"]["committed_debits"] != prior + [dict(transaction_id=arm["t"])]):
            raise phases.smoke.SmokeError("Unacknowledged cancellation replay changed its one debit or descent")
    elif returned:
        expected = copy.deepcopy(initial)
        expected.update(state="FUELED", state_started_game_time=time)
        expected.pop("plan", None); expected.pop("active_transfer_id", None)
        if time <= initial["state_started_game_time"] or time < transfer["created_at_game_time"]:
            raise phases.smoke.SmokeError("F1 source was mistaken for a returned source")
    else:
        if restarted or slot != "0" or operation != "STORE" or side != "BEFORE":
            raise phases.smoke.SmokeError("Return store failed to persist its stationary survivor")
        expected = copy.deepcopy(transfer["source_flight"])
        expected.update(state="COUNTDOWN" if kind == "CANCEL" else "TRANSIT", state_started_game_time=time)
        if kind != "CANCEL": expected["active_transfer_id"] = arm["t"]
        else: expected.pop("active_transfer_id", None)
        if time < transfer["created_at_game_time"] + (0 if kind == "CANCEL" else 140):
            raise phases.smoke.SmokeError("Return cut has a backwards source clock")
    x = (68.5 if layout == "SHARED" else 80.5) if arrived else 64.5
    y = 148.0 if not arrived and not returned and kind != "CANCEL" else 100.0
    if (entity["id"] != "advancedrocketrycommunity:rocket" or data["schema_version"] != 3 or entity["UUID"] != arm["d" if arrived else "src"]
            or data["assembly_transaction_id"] != arm["l"] or data["owner_id"] != transfer["owner_id"]
            or data["snapshot"] != transfer[end + "_snapshot"] or flight != expected
            or data["resource_escrow"] != dict(schema_version=1, ledger_id=root["ledger_id"], resource_epoch=0 if arrived else 7, markers=[])
            or entity["Pos"] != [x, y, 64.5] or entity["Motion"] != projection["motion"]
            or entity["Rotation"] != [projection["yaw"], projection["pitch"]]
            or entity.get("NoGravity", 0) != projection["no_gravity"] or entity.get("OnGround", 0) != projection["on_ground"]
            or entity.get("Invulnerable", 0) != projection["invulnerable"] or entity.get("Passengers", [])):
        raise phases.smoke.SmokeError("Return changed its complete native survivor projection")
    invariant = copy.deepcopy(transfer)
    for field in ("phase", "checksum", "destination_entity_id"): invariant.pop(field, None)
    return dict(ledger=root, transfer=invariant)


def inspect(world: Path, cut: str, restarted: bool):
    root = phases.ledger(world / "data" / phases.LEDGER); transfers = phases.journal(world)["transfers"]
    if len(transfers) != 1: raise phases.smoke.SmokeError("Return lost or duplicated its journal record")
    path = world / "entities/r.0.0.mca"
    slots = (132,) if "_SHARED_" in cut else (132, 133)
    entities = [entity for slot in slots if phases.slot_present(path, slot)
                for entity in phases.record(path, slot)["Entities"] if "RocketEntityData" in entity]
    arm = root["armed_flights"][0]
    sources = [entity for entity in entities if entity["UUID"] == arm["src"]]
    destinations = [entity for entity in entities if entity["UUID"] == arm["d"]]
    if len(entities) != 1 or len(sources) + len(destinations) != 1:
        raise phases.smoke.SmokeError("Return native slots contain a duplicate or foreign physical candidate")
    invariant = require_state(root, transfers[0], sources[0] if sources else None,
                              destinations[0] if destinations else None, cut, restarted)
    return dict(invariant=invariant, sources=sources, destinations=destinations,
                files={name: phases.digest(world / name) for name in ("data/" + phases.LEDGER, "data/" + phases.JOURNAL, "entities/r.0.0.mca")})


def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument("source_server", type=Path)
    for key in ("jar", "session-dir", "evidence-dir"): parser.add_argument("--" + key, type=Path, required=True)
    parser.add_argument("--source-commit", required=True); parser.add_argument("--java", required=True)
    parser.add_argument("--lock-owner", required=True); parser.add_argument("--case", action="append", choices=CASES)
    parser.add_argument("--startup-timeout", type=float, default=240)
    args = parser.parse_args()
    try:
        phases.run(args, cases=CASES, inspect_state=inspect, pause_marker="ARCE_RELEASE_RETURN_PAUSED cut={cut}",
                   result_marker="ARCE_RELEASE_RETURN_RESULT cut={cut} outcome=(RETURNED|LANDED) armed=true")
        return 0
    except (OSError, phases.smoke.SmokeError) as failure:
        print(f"FAIL: {failure}"); return 1


if __name__ == "__main__":
    raise SystemExit(main())
