"""Named cuts: decoded conservation, mutation boundary, actual fill admission and launch result are separate."""
from __future__ import annotations

import copy
from pathlib import Path
import re

import loader_cut_oracle as base
from run_v180_escrow_forced_stop import LEDGER, digest, ledger, record
from run_v180_escrow_pre_f2 import journal


def inspect(world: Path, cut: str) -> dict:
    def endpoints(chunk, at):
        slot = chunk + chunk * 32
        devices = [entry for entry in record(world / "region/r.0.0.mca", slot)["block_entities"] if entry.get("id") == base.LOADER]
        rockets = [entry for entry in record(world / "entities/r.0.0.mca", slot)["Entities"] if entry.get("id") == base.ROCKET]
        base.require(len(devices) == len(rockets) == 1 and rockets[0]["Pos"] == [at + .5, 100., at + .5],
                     "Named fixture lost, duplicated or moved endpoints")
        return dict(loaders=devices, rocket=rockets[0])

    return dict(endpoints(4, 64), ledger=ledger(world / "data" / LEDGER), transfers=journal(world)["transfers"],
                healthy=endpoints(5, 88) if cut.endswith("E5") else None,
                files={name: digest(world / name) for name in ("data/" + LEDGER, "region/r.0.0.mca", "entities/r.0.0.mca")})


def check(disk: dict, cut: str, restart: int) -> dict:
    root, rocket = disk["ledger"], disk["rocket"]
    e5 = cut.endswith("E5"); retired = bool(restart or cut.endswith("K9"))
    data = rocket["RocketEntityData"]; logical = data["assembly_transaction_id"]
    transfers = disk["transfers"]
    base.require(len(transfers) == 1 and transfers[0]["phase"] == "COMMITTED"
                 and transfers[0]["logical_rocket_id"] == logical and transfers[0]["destination_entity_id"] == rocket["UUID"],
                 "Named cut lost or changed its landed journal")
    transfer = transfers[0]["transfer_id"]
    base.require(root["issued_through"] == (2 if e5 else 1) and len(root["records"]) == int(not retired),
                 "Named cut changed issued sequences or pending records")
    base.require(len(root["floors"]) == int(retired), "Named cut installed or lost a floor")
    if retired:
        floor = root["floors"][0]
        base.require(floor["schema_version"] == 2 and floor["rocket_id"] == logical and floor["transfer_id"] == transfer
                     and floor["entity_id"] == rocket["UUID"] and floor["resource_epoch"] == 1
                     and floor["highest_seq"] == 1 and floor["signature_kind"] == "FUEL_VECTOR_V1", "Wrong named retirement floor")
    else:
        op = root["records"][0]; guard = op["transfer_guard"]
        base.require(op["seq"] == 1 and guard["rocket_id"] == logical and guard["transfer_id"] == transfer
                     and op["phase"] == ("STALLED" if e5 else "PREPARED") and op["reason"] == ("BARRIER_IO" if e5 else "NONE"),
                     "Named cut lost its exact guard, phase or persisted reason")
    # After checking the complete root/floor, reuse the unchanged resource oracle on this one fill.
    main = copy.deepcopy(disk); main["ledger"]["floors"] = []; main["ledger"]["issued_through"] = 1
    control = "K6" if e5 else "K9" if cut.endswith("K9") else "K2_ACK"
    identity = base.check(main, base.PREFIX + control, restart)
    if e5:
        healthy = copy.deepcopy(disk["healthy"])
        base.require(healthy is not None and healthy["rocket"]["Pos"] == [88.5, 100., 88.5]
                     and len(healthy["loaders"]) == 1
                     and tuple(healthy["loaders"][0][key] for key in ("x", "y", "z")) == (90, 100, 88),
                     "Healthy control moved or disappeared")
        # Normalize only the fixture's position and use the same final, pruned-resource oracle.
        healthy["rocket"]["Pos"] = [64.5, 100., 64.5]
        healthy["loaders"][0].update(x=66, z=64)
        healthy["ledger"] = dict(main["ledger"], records=[])
        identity["healthy"] = base.check(healthy, base.PREFIX + "K2_ACK", 1)
        base.require(identity["physical"] != identity["healthy"]["physical"]
                     and identity["logical"] != identity["healthy"]["logical"], "Healthy control aliases the held rocket")
    else:
        base.require(disk["healthy"] is None, "Unexpected control endpoints")
    return dict(identity, transfer=transfer)


def observations(cut: str, restart: int, lines: list[str]) -> dict:
    e5 = cut.endswith("E5")
    faults = [line for line in lines if "ARCE_LOADER_MATRIX_STORE_FAULT" in line]
    expected = 3 if e5 and not restart else 0
    base.require(len(faults) == expected and all(f"cut={cut} chunk=4,4 failure={index}" in line
                                               for index, line in enumerate(faults, 1)), "Wrong named IO fault sequence")
    if not e5 and not restart:
        return dict(fill_retired=cut.endswith("K9"), mutation_boundary="NOT_OBSERVED")
    state = "STALLED" if not restart else "RETIRED" if e5 else "F1B_HELD"
    pattern = re.compile(r"ARCE_LOADER_MATRIX_NAMED_STATE cut=" + re.escape(cut) + r" state=" + state
                         + r" records=" + str(int(not restart)) + r" tank=4000 bank=1000 healthy_tank=" + ("4000" if e5 else "-1")
                         + r" healthy_bank=" + ("1000" if e5 else "-1")
                         + r" mutation_allowed=(true|false) source_ever_loaded=false(?:\s|$)")
    matches = [m for line in lines if (m := pattern.search(line))]
    base.require(len(matches) == 1, "Missing or repeated named resource/mutation-boundary observation")
    allowed = matches[0][1] == "true"
    base.require(allowed == bool(e5 and restart), "Unexpected resource-mutation boundary")
    return dict(fill_retired=bool(restart), resources_conserved=True, mutation_allowed=allowed,
                mutation_boundary="ALLOWED" if allowed else "HELD_STALLED" if e5 else "F1B_HELD",
                old_source_loaded=False if not e5 else None)


def follow_up_observations(cut: str, lines: list[str]) -> dict:
    """A real request is not a boundary query; PREPARING proves queued admission, not durable L1 or transfer."""
    def one(pattern):
        matches = [match for line in lines if (match := re.search(pattern, line))]
        base.require(len(matches) == 1, "Missing/repeated follow-up observation: " + pattern)
        return matches[0]

    prefix = " cut=" + re.escape(cut)
    launch = one(r"ARCE_LOADER_MATRIX_LAUNCH" + prefix
                 + r" code=(\w+) required_fuel=(\d+) retained_transfer=true mutation_allowed=(true|false)(?:\s|$)")
    fill = one(r"ARCE_LOADER_MATRIX_SECOND_FILL" + prefix
               + r" attempt=(\w+) result=(\w+) admitted=(true|false) next_before=(\d+) next_after=(\d+)"
               + r" pending_before=0 pending_after=(\d+) durable_records=0 resources_unchanged=true(?:\s|$)")
    one(r"ARCE_LOADER_MATRIX_FOLLOWUP_PAUSED" + prefix + r" hook=AFTER_REQUESTS_BEFORE_L1(?:\s|$)")
    admitted = fill[3] == "true"; delta = int(admitted)
    base.require(admitted == (fill[2] == "PREPARING") and (not admitted or fill[1] == "READY")
                 and int(fill[5]) == int(fill[4]) + delta and int(fill[6]) == delta
                 and fill[2] != "READY", "Second-fill result and actual reservation disagree")
    base.require(launch[1] == "TRANSFER_JOURNAL_BLOCKED", "Unexpected launch request result: " + launch[1])
    return dict(second_fill=dict(attempt=fill[1], result=fill[2], admitted=admitted,
                                 durable=False, completed=False, resources_unchanged=True),
                launch=dict(code=launch[1], required_fuel=int(launch[2]), outcome="KNOWN_LIMITATION",
                            retained_transfer=True), mutation_allowed=launch[3] == "true")
