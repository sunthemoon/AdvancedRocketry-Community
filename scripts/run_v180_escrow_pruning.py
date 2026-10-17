#!/usr/bin/env python3
"""Native exact-group pruning with held B, including O4 replacement before callback; two restarts."""
from __future__ import annotations

import argparse
import copy
from pathlib import Path
import re

import run_v180_escrow_phases as phases
from run_v180_rocket_settlement_intent import ACTOR

CASES = ("SR_HELD_B__O4_AFTER", "SR_HELD_B__O4_REPLACED")


def require_state(root, journal, rocket, bank, sources, restarted):
    if (len(root["records"]) != 1 or len(root["tombstones"]) != 1 or len(root["rocket_authorities"]) != 1
            or root["floors"] or root["armed_flights"] or root["issued_through"] != 2 or root["issued_launch_serial"] != 0
            or journal["transfers"] or len(sources) != 2):
        raise phases.smoke.SmokeError("Pruning lost held B, released A or its exact authority tables")
    held = root["records"][0]; receipt = root["tombstones"][0]; head = root["rocket_authorities"][0]
    if (held["phase"] != "REPAIR" or held["reason"] != "OTHER_STATE" or held.get("settlement") or held["seq"] != 2
            or receipt["op_id"] == held["op_id"] or receipt["released"] != 1 or receipt["release_generation"] <= 0
            or receipt["actor"] != ACTOR or receipt["actor_name"] != "Server" or receipt["accounting_status"] != "UNKNOWN"
            or not re.fullmatch(r"[0-9a-f]{64}", receipt["decision_digest"])
            or receipt["schema_version"] != 3 or receipt["choice"] != "ACCEPT_CURRENT" or receipt["missing"]
            or receipt["retired"] != [dict(op_id=receipt["op_id"])]):
        raise phases.smoke.SmokeError("Pruning released B or used a different/unacknowledged A decision")
    data = rocket["RocketEntityData"]
    if (head["outcome"] != "READY" or head["issued"] != 0 or head["completed"] != 0 or head["e"] != 1
            or head["a"] != receipt["op_id"] or head["g"] != receipt["release_generation"]
            or head["p"] != rocket["UUID"] or head["l"] != data["assembly_transaction_id"] or head["o"] != data["owner_id"]
            or rocket["id"] != "advancedrocketrycommunity:rocket" or rocket["Pos"] != [64.5, 100.0, 64.5]
            or data["schema_version"] != 3 or data["flight_data"]["fuel"]["amount"] != 1000
            or data["flight_data"]["fuel"]["committed_debits"]):
        raise phases.smoke.SmokeError("A pruning changed its released physical rocket, credit or epoch")
    selected = {entry["endpoint"]["kind"]: entry["endpoint"] for entry in receipt["survivors"]}
    if len(selected) != 2 or selected["ENTITY"]["identity"] != rocket["UUID"]:
        raise phases.smoke.SmokeError("A pruning lost its exact receipt survivors")
    from_b, to_b = held["source"], held["destination"]
    source_a = next((entry for entry in sources if [entry["x"], entry["y"], entry["z"]] ==
                     [selected["BLOCK_ENTITY"][key] for key in ("x", "y", "z")]), None)
    source_b = next((entry for entry in sources if [entry["x"], entry["y"], entry["z"]] ==
                     [from_b[key] for key in ("x", "y", "z")]), None)
    if (source_a is None or source_b is None or source_a is source_b or bank["UUID"] != to_b["identity"]
            or bank["id"] != "minecraft:armor_stand" or bank["Pos"] != [82.5, 100.0, 64.5]
            or any(end["before_epoch"] != 0 or end["after_epoch"] != 1 for end in (from_b, to_b))):
        raise phases.smoke.SmokeError("Pruning selected a foreign/missing group B or A endpoint")
    for entry, identity, amount in ((source_a, selected["BLOCK_ENTITY"]["identity"], 4000),
                                    (source_b, from_b["identity"], 4000), (bank, to_b["logical_rocket"], 1000)):
        fixture = entry["ForgeData"]["arce_escrow_fixture"]
        if fixture["identity"] != identity or fixture["amount"] != amount:
            raise phases.smoke.SmokeError("Pruning changed a native fixture identity or conserved quantity")
    normalized = []
    for section, operation, sequence, role, released in (
            (data["resource_escrow"], receipt["op_id"], 1, "DESTINATION", True),
            (source_a["ForgeData"]["resource_escrow"], receipt["op_id"], 1, "SOURCE", True),
            (source_b["ForgeData"]["resource_escrow"], held["op_id"], 2, "SOURCE", False),
            (bank["ForgeData"]["resource_escrow"], held["op_id"], 2, "DESTINATION", False)):
        markers = [] if released and restarted else [dict(op_id=operation, seq=sequence, role=role, epoch_after=1)]
        expected = dict(schema_version=1, ledger_id=root["ledger_id"], resource_epoch=1, markers=markers)
        if section != expected:
            raise phases.smoke.SmokeError("Pruning changed B markers/epochs or left A unpruned")
        normalized.append(dict(expected, markers=[] if released else markers))
    protected = copy.deepcopy(data); protected["resource_escrow"] = normalized[0]
    from_a = copy.deepcopy(source_a["ForgeData"]); from_a["resource_escrow"] = normalized[1]
    pose = {key: rocket.get(key) for key in ("UUID", "Pos", "Motion", "Rotation", "NoGravity", "OnGround", "Invulnerable")}
    return dict(ledger=root, journal=journal, rocket=protected, pose=pose, source_a=from_a,
                source_b=source_b["ForgeData"], bank_b=dict(UUID=bank["UUID"], Pos=bank["Pos"], ForgeData=bank["ForgeData"]))


def inspect(world: Path, cut: str, restarted: bool):
    root = phases.ledger(world / "data" / phases.LEDGER); journal = phases.journal(world)
    entities = [entry for slot in (132, 133) for entry in phases.record(world / "entities/r.0.0.mca", slot)["Entities"]]
    rockets = [entry for entry in entities if "RocketEntityData" in entry]
    banks = [entry for entry in entities if "arce_escrow_fixture" in entry.get("ForgeData", {})]
    sources = [entry for slot in (132, 133) for entry in phases.record(world / "region/r.0.0.mca", slot)["block_entities"]
               if "arce_escrow_fixture" in entry.get("ForgeData", {})]
    if len(rockets) != 1 or len(banks) != 1:
        raise phases.smoke.SmokeError("Pruning lost or duplicated a native physical candidate")
    invariant = require_state(root, journal, rockets[0], banks[0], sources, restarted)
    return dict(invariant=invariant, rocket=rockets[0], bank=banks[0], sources=sources,
                files={name: phases.digest(world / name) for name in ("data/" + phases.LEDGER, "data/" + phases.JOURNAL,
                                                                     "entities/r.0.0.mca", "region/r.0.0.mca")})


def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument("source_server", type=Path)
    for key in ("jar", "session-dir", "evidence-dir"): parser.add_argument("--" + key, type=Path, required=True)
    parser.add_argument("--source-commit", required=True); parser.add_argument("--java", required=True)
    parser.add_argument("--lock-owner", required=True); parser.add_argument("--case", action="append", choices=CASES)
    parser.add_argument("--startup-timeout", type=float, default=240)
    args = parser.parse_args()
    try:
        phases.run(args, cases=CASES, inspect_state=inspect,
                   pause_marker="ARCE_RELEASE_SETTLEMENT_COMPLETION_PAUSED cut={cut}",
                   result_marker="ARCE_RELEASE_SETTLEMENT_COMPLETION_RESULT cut={cut} state=RELEASED records=1 tombstones=1 locked=false")
        return 0
    except (OSError, phases.smoke.SmokeError) as failure:
        print(f"FAIL: {failure}"); return 1


if __name__ == "__main__": raise SystemExit(main())
