#!/usr/bin/env python3
"""W142 fluid cargo S2 cuts C-S4 (FS_) and C-S5 (FF_): one forced stop and two restarts per cut, pinned source, JAR and
hooks, and an oracle that decodes the raw ledger, journal, region and entity files independently of the server."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import shutil
import uuid

import run_dedicated_server_smoke as smoke
from run_v060_flight_server_smoke import _server_command
from run_v180_cargo_reconciliation import preflight
from run_v180_escrow_forced_stop import LEDGER, digest, ledger, record
from run_v180_escrow_pre_f2 import assert_pin, journal
from run_v180_landed_disassembly_cuts import block_at
from run_v180_rocket_settlement_completion import require_lock, slot_present

REPO = Path(__file__).resolve().parents[1]
DEVICE_POS = [64, 100, 64]
ROCKET_X, PAD_X = 68.5, 80.5
TOTAL = 5000
# Endpoint states: (rocket cargo mB, epoch, markers, bound to the ledger) and (device mB, epoch, markers).
APPLIED = dict(rocket=(4000, 1, 1, True), device=(1000, 1, 1))
PRUNED = dict(rocket=(4000, 1, 0, True), device=(1000, 1, 0))
NEUTRAL = dict(rocket=(5000, 0, 0, False), device=(0, 0, 0))
NORMALIZED = dict(rocket=(5000, 0, 0, True), device=(0, 0, 0))
VETOED = dict(rocket=(5000, 0, 0, False), device=(1000, 1, 1))
# Ledger states: record phases, whether they carry the frozen intent, receipts (released flags) and READY heads.
REPAIR = dict(records=["REPAIR"], intent=False, receipts=[], heads=0)
ACCEPT = dict(records=["SETTLING_ACCEPT"], intent=True, receipts=[], heads=0)
ABANDON = dict(records=["SETTLING_ABANDON"], intent=True, receipts=[], heads=0)
RECEIPT = dict(records=["SETTLING_ACCEPT"], intent=True, receipts=[False], heads=0)
RELEASED = dict(records=[], intent=False, receipts=[True], heads=1)
ORPHANED = dict(records=[], intent=False, receipts=[True], heads=0)


def settlement(cut_ledger, cut_ends, final_ledger, final_ends, **extra) -> dict:
    return dict(family="C-S4", cut=(cut_ledger, cut_ends), final=(final_ledger, final_ends), **extra)


# The C-S4 row in the order TASK-W142 lists it.
CASES = {
    "FS_O1_BEFORE": settlement(REPAIR, APPLIED, REPAIR, APPLIED),
    "FS_O1_AFTER": settlement(ACCEPT, APPLIED, RELEASED, PRUNED),
    "FS_BIND_BEFORE": settlement(ACCEPT, NEUTRAL, RELEASED, NORMALIZED),
    "FS_BIND_AFTER": settlement(ACCEPT, NEUTRAL, RELEASED, NORMALIZED),
    "FS_O2_ROCKET_BEFORE": settlement(ACCEPT, APPLIED, RELEASED, PRUNED),
    "FS_O2_ROCKET_AFTER": settlement(ACCEPT, APPLIED, RELEASED, PRUNED),
    "FS_O2_DEVICE_BEFORE": settlement(ACCEPT, APPLIED, RELEASED, PRUNED),
    "FS_O2_DEVICE_AFTER": settlement(ACCEPT, APPLIED, RELEASED, PRUNED),
    "FS_O3_BEFORE": settlement(ACCEPT, APPLIED, RELEASED, PRUNED),
    "FS_O3_AFTER": settlement(RECEIPT, APPLIED, RELEASED, dict(rocket=(4000, 0, 0, True), device=(1000, 1, 0)), landed=True),
    "FS_O3J_BEFORE": settlement(RECEIPT, NORMALIZED, RELEASED, NORMALIZED, cut_journal=1),
    "FS_O3J_AFTER": settlement(RECEIPT, NORMALIZED, RELEASED, NORMALIZED),
    "FS_O4_BEFORE": settlement(RECEIPT, APPLIED, RELEASED, PRUNED),
    "FS_O4_AFTER": settlement(RELEASED, APPLIED, RELEASED, PRUNED),
    "FS_PRUNE_BEFORE": settlement(RELEASED, APPLIED, RELEASED, PRUNED),
    "FS_PRUNE_AFTER": settlement(RELEASED, PRUNED, RELEASED, PRUNED),
    "FS_ABANDON_ROCKET": settlement(ABANDON, dict(rocket=None, device=(0, 0, 0)), ORPHANED, dict(rocket=None, device=(0, 0, 0)), choice="ABANDON"),
    "FS_ABANDON_DEVICE": settlement(ABANDON, dict(rocket=NEUTRAL["rocket"], device=None), RELEASED, dict(rocket=NORMALIZED["rocket"], device=None),
                                    choice="ABANDON"),
    "FS_VETO_DESTINATION_SAVED": settlement(ACCEPT, VETOED, ACCEPT, VETOED),
    "FS_BIND_VETO": settlement(ACCEPT, NEUTRAL, RELEASED, NORMALIZED, cut_x=68.75),
}
LANDED = "advancedrocketrycommunity.landedDisassemblyProbe"
SECOND_POS = [76, 100, 64]
PAD = 80.5


def flight(final: dict, cut: dict | None = None, **extra) -> dict:
    """A C-S5 expectation: rockets as (x, cargo, epoch, bound), devices as (mB, epoch, markers), journal and ARMED counts."""
    return dict(family="C-S5", cut=cut, final=final, **extra)


def ends(rockets, device, second=None, journal=0, armed=0, serial=0, blocks=None) -> dict:
    return dict(rockets=rockets, device=device, second=second, journal=journal, armed=armed, serial=serial, blocks=blocks)


LOADED = (ROCKET_X, 1000, 1, True)
UNLOADED = (ROCKET_X, 4000, 1, True)
RESTORED = ends([], (1000, 1, 0), blocks=4000)
STALE = ends([UNLOADED], (1000, 1, 0), journal=1)
FLIGHTS = {
    "FF_N1": flight(ends([LOADED], (4000, 1, 0)), ends([(ROCKET_X, 0, 0, False)], (5000, 0, 0))),
    "FF_F1_AFTER_CARGO": flight(ends([LOADED], (4000, 1, 0), serial=1), ends([LOADED], (4000, 1, 0), armed=1, serial=1)),
    "FF_F6_AFTER_CARGO": flight(ends([(PAD, 1000, 0, True)], (4000, 1, 0), serial=1)),
    "FF_F8_AFTER_CARGO": flight(ends([(PAD, 1000, 0, True)], (4000, 1, 0), serial=1), ends([(PAD, 1000, 0, True)], (4000, 1, 0), serial=1)),
    "FF_DISASSEMBLY_REMOVAL": flight(RESTORED, STALE, properties={LANDED: "LANDED_DISASSEMBLY:K1"}, marker="ARCE_LANDED_DISASSEMBLY_PAUSED boundary=K1 "),
    "FF_DISASSEMBLY_RESTORATION": flight(RESTORED, ends([UNLOADED], (1000, 1, 0)), properties={LANDED: "LANDED_DISASSEMBLY:K2"}, marker="ARCE_LANDED_DISASSEMBLY_PAUSED boundary=K2 "),
    "FF_DISASSEMBLY_RELEASE": flight(RESTORED, ends([UNLOADED], (1000, 1, 0)), properties={LANDED: "LANDED_DISASSEMBLY:K5"}, marker="ARCE_LANDED_DISASSEMBLY_PAUSED boundary=K5 "),
    "FF_REBUILD_DESTINATION": flight(STALE, STALE),
    "FF_KEEP_SOURCE": flight(STALE, STALE, fence="FLOOR_CONFLICT"),
    "FF_REBUILD_SOURCE": flight(STALE, STALE),
    "FF_STALE_PREPARED": flight(ends([UNLOADED, (76.5, 5000, 0, False)], (1000, 1, 0), journal=1),
                                ends([UNLOADED, (76.5, 5000, 0, False)], (1000, 1, 0), journal=1), fence="FLOOR_CONFLICT"),
    "FF_RECONNECT": flight(STALE, STALE, fence="FLOOR_CONFLICT"),
    "FF_GOVERNED_MISSING": flight(ends([LOADED], (4000, 1, 0), journal=1), ends([LOADED], (4000, 1, 0), journal=1)),
    "FF_GOVERNED_TERMINAL": flight(None, ends([LOADED], (4000, 1, 0), journal=1, armed=1, serial=1)),
    "FF_TWO_FLIGHTS": flight(ends([(ROCKET_X, 2000, 0, True)], (4000, 1, 0), (4000, 1, 0), serial=2),
                             ends([(PAD, 2000, 1, True)], (4000, 1, 0), (4000, 1, 0), serial=1)),
    "FF_CANCEL_AFTER_CARGO": flight(ends([LOADED], (4000, 1, 0), serial=1), ends([LOADED], (4000, 1, 0), journal=1, armed=1, serial=1)),
}
CASES.update(FLIGHTS)
BOUNDARIES = dict(zip(tuple(CASES)[:20], (
    "O1_BEFORE", "O1_AFTER", "O_BIND_BEFORE", "O_BIND_AFTER", "O2_ROCKET_BEFORE", "O2_ROCKET_AFTER",
    "O2_DEVICE_BEFORE", "O2_DEVICE_AFTER", "O3_BEFORE", "O3_AFTER", "O3J_BEFORE", "O3J_REPLACED",
    "O4_BEFORE", "O4_AFTER", "PRUNE_BEFORE", "PRUNE_AFTER", "O1_AFTER", "O1_AFTER", "O_VETO", "NORMALIZATION_VETO")))
HOOK_DIR = "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/"
HOOKS = tuple(HOOK_DIR + path + ".java" for path in (
    "resourceescrow/EscrowCargoCutProbe", "resourceescrow/EscrowCargoSettlementCuts", "resourceescrow/EscrowRuntime",
    "resourceescrow/EscrowCargoCutLaunch", "resourceescrow/EscrowCargoFlightCuts", "resourceescrow/EscrowCargoRecoveryCuts",
    "resourceescrow/EscrowCargoTransferFixtures", "resourceescrow/EscrowCargoSwitches", "resourceescrow/EscrowSettlementRecovery",
    "resourceescrow/EscrowSettlementNormalization", "resourceescrow/EscrowSettlementPruning", "resourceescrow/EscrowLedgerWriter",
    "resourceescrow/EscrowReconciliationDriver", "rocket/flight/persistence/RocketTransferSavedData"))
HARNESS = ("scripts/run_v180_cargo_s4_s5_cuts.py", "scripts/run_v180_cargo_reconciliation.py", "scripts/run_v180_escrow_forced_stop.py",
           "scripts/run_v180_escrow_pre_f2.py", "scripts/run_v180_rocket_settlement_completion.py", "scripts/run_v060_flight_server_smoke.py",
           "scripts/run_dedicated_server_smoke.py", "scripts/run_v180_landed_disassembly_cuts.py", "scripts/inspect_celestial_saved_data.py",
           "scripts/inspect_escrow_receipt.py")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise smoke.SmokeError(message)


def entities(world: Path) -> list:
    """Rocket entities in the fixture chunk [4,4] and, once a flight landed, the pad chunk [5,4]."""
    path = world / "entities/r.0.0.mca"
    found = []
    for slot in (132, 133):
        if slot_present(path, slot):
            found += [entry for entry in record(path, slot)["Entities"] if "RocketEntityData" in entry]
    return found


def rocket_state(entry: dict, ledger_id) -> dict:
    data = entry["RocketEntityData"]
    blocks = [block["block_entity"] for block in data["snapshot"]["relative_blocks"] if "block_entity" in block]
    require(len(blocks) == 1 and blocks[0]["adapter"] == "advancedrocketrycommunity:pressurized_tank_v1", "Cargo rocket lost its one tank payload")
    fluid = blocks[0]["data"]["tank"]["fluid"]
    require(not fluid or fluid.get("FluidName") == "minecraft:water" and not fluid.get("Tag"), "Cargo changed its fluid identity or metadata")
    section = data["resource_escrow"]
    require("ledger_id" not in section or section["ledger_id"] == ledger_id, "Rocket names another ledger")
    return dict(uuid=entry["UUID"], logical=data["assembly_transaction_id"], owner=data["owner_id"], x=entry["Pos"][0],
                pos=entry["Pos"], cargo=fluid.get("Amount", 0), epoch=section["resource_epoch"], markers=section["markers"],
                bound="ledger_id" in section, passengers=data["flight_data"]["passengers"]["assignments"],
                fuel=data["flight_data"]["fuel"]["amount"], flight_state=data["flight_data"]["state"])


def device_state(terrain: dict, position: list, ledger_id) -> dict | None:
    blocks = [entry for entry in terrain["block_entities"] if [entry.get(k) for k in ("x", "y", "z")] == position]
    if not blocks or "arce_rocket_fluid_cargo" not in blocks[0]:
        return None
    root = blocks[0]["arce_rocket_fluid_cargo"]
    require(root["role"] in ("LOAD", "UNLOAD") and (not root["tank"] or root["tank"].get("FluidName") == "minecraft:water"), "Device changed fluid")
    section = root["resource_escrow"]
    require("ledger_id" not in section or section["ledger_id"] == ledger_id, "Device names another ledger")
    return dict(id=root["device"], owner=root["owner"], role=root["role"], logical=root["binding"]["logical_rocket"],
                amount=root["tank"].get("Amount", 0), epoch=section["resource_epoch"], markers=section["markers"])


def fluid_amount(value) -> int:
    """The water held by a restored tank's root, found by shape so the oracle does not depend on its key names."""
    if isinstance(value, dict):
        if value.get("FluidName") == "minecraft:water" and isinstance(value.get("Amount"), int):
            return value["Amount"]
        return sum(fluid_amount(item) for item in value.values())
    if isinstance(value, list):
        return sum(fluid_amount(item) for item in value)
    return 0


def inspect(world: Path) -> dict:
    root = ledger(world / "data" / LEDGER) if (world / "data" / LEDGER).is_file() else None
    require(root is not None, "The world has no checked escrow ledger")
    rockets = [rocket_state(entry, root["ledger_id"]) for entry in entities(world)]
    terrain = record(world / "region/r.0.0.mca")
    tank = [entry for entry in terrain["block_entities"] if [entry.get(k) for k in ("x", "y", "z")] == [68, 100, 64]]
    return dict(ledger=root, journal=len(journal(world)["transfers"]), rockets=rockets, rocket=rockets[0] if len(rockets) == 1 else None,
                device=device_state(terrain, DEVICE_POS, root["ledger_id"]), second=device_state(terrain, SECOND_POS, root["ledger_id"]),
                tank=fluid_amount(tank), blocks=[block_at(terrain, 68, 100, 64), block_at(terrain, 68, 101, 64)],
                files={name: digest(world / name) for name in ("data/" + LEDGER, "region/r.0.0.mca", "entities/r.0.0.mca")})


def check_settlement(case: str, disk: dict, restarted: bool) -> None:
    spec = CASES[case]; state, ends = spec["final" if restarted else "cut"]
    landed = restarted and spec.get("landed")
    require(len(disk["rockets"]) <= 1 and (disk["device"] is None or disk["device"]["role"] == "UNLOAD"), "Settlement duplicated the rocket or changed the device")
    root, rocket, device = disk["ledger"], disk["rocket"], disk["device"]
    require([op["phase"] for op in root["records"]] == state["records"], "Ledger records differ from the boundary")
    require(all(("settlement" in op) == state["intent"] for op in root["records"]), "Frozen intent presence differs from the boundary")
    require(all(op["reason"] == "OTHER_STATE" for op in root["records"] if op["phase"] == "REPAIR"), "Manual conflict lost its reason")
    require([receipt["released"] for receipt in root["tombstones"]] == state["receipts"], "Receipt boundary differs")
    require(len(root["rocket_authorities"]) == state["heads"] and not root["floors"], "Head count or floor differs")
    for receipt in root["tombstones"]:
        require(receipt["accounting_status"] == "UNKNOWN" and receipt["payload_amount"] == 0
                and receipt["choice"] == spec.get("choice", "ACCEPT_CURRENT"), "Receipt accounting or choice changed")
        require(len(receipt["missing"]) == (1 if spec.get("choice") == "ABANDON" else 0), "Receipt survivors or absences changed")
        for survivor in receipt["survivors"]:
            kind = "FUEL_VECTOR_V1" if survivor["endpoint"]["kind"] == "ENTITY" else "FLUID_TANK_V1"
            require(survivor["signature_kind"] == kind, "Settlement authority is not fuel-vector plus invariant for the rocket")
    for head in root["rocket_authorities"]:
        outcome = "HANDED_OFF" if landed else "READY"
        require(head["outcome"] == outcome, f"Head outcome: expected {outcome}, got {head['outcome']}")
        require(head["k"] == "FUEL_VECTOR_V1", "Head identity: fuel-vector authority changed")
    require(not root["armed_flights"] and root["issued_launch_serial"] == (1 if landed else 0), "Flight authority differs")
    require(disk["journal"] == (spec.get("cut_journal", 0) if not restarted else 0), "Journal record count differs")
    expected_rocket, expected_device = ends["rocket"], ends["device"]
    if expected_rocket is None:
        require(rocket is None, "An abandoned rocket reappeared")
    else:
        require(rocket is not None, "The surviving rocket is missing")
        cargo, epoch, markers, bound = expected_rocket
        require(rocket["cargo"] == cargo, f"Native cargo quantities: rocket expected {cargo}, got {rocket['cargo']}")
        require((rocket["epoch"], len(rocket["markers"]), rocket["bound"]) == (epoch, markers, bound),
                f"Markers: rocket section differs: {rocket['epoch']}/{len(rocket['markers'])}/{rocket['bound']}")
        x = PAD_X if landed else spec.get("cut_x", ROCKET_X) if not restarted else ROCKET_X
        require(rocket["x"] == x and rocket["pos"][1:] == [100.0, 64.5] and not rocket["passengers"], "Rocket placement or occupancy differs")
        if root["rocket_authorities"]:
            head = root["rocket_authorities"][0]
            require(head["p"] == rocket["uuid"] and head["e"] == epoch and head["l"] == rocket["logical"] and head["o"] == rocket["owner"],
                    "Head identity: physical rocket, logical rocket, owner or epoch differs")
            if landed:
                require(head["issued"] == head["completed"] == 1, "Head identity: completed launch serial differs")
                require(rocket["fuel"] == 700, f"Native fuel quantity: expected 700, got {rocket['fuel']}")
                require(rocket["flight_state"] == "LANDED", "Flight lifecycle: successor is not LANDED")
                receipt = root["tombstones"][0]
                require(head["a"] == receipt["anchor_op"] and head["g"] == receipt["release_generation"], "Receipt: head anchor differs")
    if expected_device is None:
        require(device is None, "An abandoned device reappeared")
    else:
        require(device is not None, "The surviving device is missing")
        require(device["amount"] == expected_device[0], f"Native cargo quantities: device expected {expected_device[0]}, got {device['amount']}")
        require((device["epoch"], len(device["markers"])) == expected_device[1:],
                f"Markers: device section differs: {device['epoch']}/{len(device['markers'])}")
    for endpoint, role in ((rocket, "SOURCE"), (device, "DESTINATION")):
        if endpoint is None or not endpoint["markers"]:
            continue
        kind, identity = ("ENTITY", "uuid") if role == "SOURCE" else ("BLOCK_ENTITY", "id")
        audit = [observation for receipt in root["tombstones"] for observation in receipt["before_choice"]
                 if not observation["missing"] and observation["endpoint"]["kind"] == kind
                 and observation["endpoint"]["identity"] == endpoint[identity]]
        # O4 retires the live record, but its frozen audit retains the pre-pruning marker multiset.
        # Compare both ways; membership alone would miss an extra or duplicate audit marker.
        if not root["records"]:
            require(len(audit) == 1 and sorted(json.dumps(m, sort_keys=True) for m in endpoint["markers"])
                    == sorted(json.dumps(m, sort_keys=True) for m in audit[0]["state"]["markers"]),
                    "Retired markers differ from the physical endpoint's receipt audit")
        for marker in endpoint["markers"]:
            live = any(op["op_id"] == marker["op_id"] and op["seq"] == marker["seq"] for op in root["records"])
            retired = any(marker in observation["state"]["markers"] and observation["state"]["epoch"] == endpoint["epoch"]
                          for observation in audit)
            require((live or retired)
                    and marker["role"] == role and marker["epoch_after"] == endpoint["epoch"],
                    "A marker differs from its operation, sequence, role or epoch")
    raw = (rocket["cargo"] if rocket else 0) + (device["amount"] if device else 0)
    # 6,000 mB can exist only as raw mixed stores while a record still locks both endpoints.
    require(raw <= TOTAL or root["records"], "More than 5,000 mB became spendable")
    if rocket and device:
        require(device["logical"] == rocket["logical"] and device["owner"] == rocket["owner"], "Device binding or owner changed")


def check_flight(case: str, disk: dict, restarted: bool) -> None:
    spec = CASES[case]; expected = spec["final" if restarted else "cut"]
    root, rockets = disk["ledger"], disk["rockets"]
    require(not root["records"] and not root["tombstones"] and not root["rocket_authorities"] and not root["floors"],
            "An ordinary cargo flight or recovery invented a record, receipt, head or floor")
    for state in rockets:
        require(not state["markers"] and not state["passengers"], "A rocket kept a marker or a passenger assignment")
    # Several raw rocket copies or more than 5,000 mB may exist only while a journal record or ARMED entry locks them.
    raw = sum(state["cargo"] for state in rockets) + sum(d["amount"] for d in (disk["device"], disk["second"]) if d) + disk["tank"]
    total = 5000 + (5000 if disk["second"] else 0)
    if case == "FF_STALE_PREPARED":
        total += 5000  # The fixture itself publishes the stale 5,000 mB destination copy; it must survive unchanged.
    require(raw <= total or disk["journal"] or root["armed_flights"], "More cargo became available than the fixture holds")
    require(len(rockets) <= 1 or disk["journal"] or root["armed_flights"] or case == "FF_STALE_PREPARED", "A stale copy won outside a locked record")
    if expected is None:
        # F6's first readback can precede the other entity slot's store. Both raw copies, when present,
        # must still carry the latest cargo under the retained journal/ARMED authority.
        require(case in ("FF_F6_AFTER_CARGO", "FF_GOVERNED_TERMINAL"), "Missing exact boundary oracle")
        require(1 <= len(rockets) <= (2 if case == "FF_F6_AFTER_CARGO" else 1)
                and all(r["cargo"] == 1000 and r["bound"] and r["epoch"] in (0, 1) for r in rockets),
                "The governed boundary lost or changed the latest cargo")
        require(disk["device"] is not None and (disk["device"]["amount"], disk["device"]["epoch"], disk["device"]["markers"]) == (4000, 1, [])
                and disk["second"] is None and disk["tank"] == 0 and root["issued_launch_serial"] == 1,
                "The governed boundary changed its native device, blocks or serial")
        if case == "FF_F6_AFTER_CARGO":
            require(disk["journal"] == 1 and len(root["armed_flights"]) == 1
                    and root["armed_flights"][0]["outcome"] == "ARMED", "F6 lost its pending governed authority")
        else:
            require(rockets[0]["x"] == ROCKET_X and rockets[0]["epoch"] == 1
                    and (disk["journal"], len(root["armed_flights"])) in ((1, 1), (0, 1), (0, 0))
                    and all(a["outcome"] == "HANDED_OFF" for a in root["armed_flights"]),
                    "Terminal authority entered a legacy recovery or changed its source")
        return
    observed = sorted((state["x"], state["cargo"], state["epoch"], state["bound"]) for state in rockets)
    require(observed == sorted(expected["rockets"]), f"Raw rockets differ: {observed}")
    for key, name in (("device", "device"), ("second", "second")):
        value, want = disk[name], expected[key]
        require((want is None) == (value is None) and (want is None or (value["amount"], value["epoch"], len(value["markers"])) == want),
                f"Raw {name} differs: {value and (value['amount'], value['epoch'], len(value['markers']))}")
    require(disk["journal"] == expected["journal"] and len(root["armed_flights"]) == expected["armed"]
            and root["issued_launch_serial"] == expected["serial"], "Journal, ARMED or launch serial differs")
    if expected["blocks"] is not None:
        require(disk["tank"] == expected["blocks"] and disk["blocks"] == ["advancedrocketrycommunity:pressurized_tank", "minecraft:iron_block"],
                f"Restored blocks differ: {disk['blocks']} tank={disk['tank']}")
    else:
        require(disk["blocks"][0] != "advancedrocketrycommunity:pressurized_tank" and disk["tank"] == 0, "Blocks were restored beside a rocket")


def fences(lines: list[str]) -> list[str]:
    return sorted({match[1] for line in lines for match in [re.search(r"ARCE_TRANSFER_FENCED transfer=\S+ reason=(\S+)", line)] if match})


def check(case: str, disk: dict, restarted: bool) -> None:
    if CASES[case]["family"] == "C-S4":
        check_settlement(case, disk, restarted)
    else:
        check_flight(case, disk, restarted)


def same_identity(cut: dict, disk: dict) -> bool:
    """Restarts keep the device identity and never show a rocket of another logical identity or owner."""
    def ids(value: dict) -> set:
        return {(json.dumps(rocket["logical"]), json.dumps(rocket["owner"])) for rocket in value["rockets"]}
    devices = (cut["device"], disk["device"])
    return (None in devices or devices[0]["id"] == devices[1]["id"]) and (not ids(cut) or ids(disk) <= ids(cut))


def raw_snapshot(world: Path) -> dict:
    """The independently decoded files of this start, kept even when the oracle rejects them."""
    terrain = record(world / "region/r.0.0.mca")
    return dict(ledger=ledger(world / "data" / LEDGER) if (world / "data" / LEDGER).is_file() else None, journal=journal(world),
                block_entities=terrain["block_entities"], entities=entities(world))


def retain_failed_files(session: Path, world: Path, destination: Path) -> dict:
    """Keep original native bytes before owned-server cleanup, including failed/absent decodes."""
    files = [world / name for name in ("level.dat", "level.dat_old", "region/r.0.0.mca", "entities/r.0.0.mca")]
    files += list((world / "data").glob("*.dat*")) + list((session / "crash-reports").glob("*.txt"))
    manifest = {}
    for source in sorted(set(files)):
        if not source.is_file():
            continue
        require(not source.is_symlink() and session.resolve() in source.resolve().parents, "Unsafe failure snapshot input")
        relative = source.relative_to(session)
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        manifest[relative.as_posix()] = dict(bytes=target.stat().st_size, sha256=digest(target))
    destination.mkdir(parents=True, exist_ok=True)
    (destination / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    return dict(directory=destination.name, files=manifest)


def marker(case: str, restart: int) -> str:
    if restart:
        return "ARCE_RELEASE_CARGO_CUT_RESULT cut=" + case + " "
    return CASES[case].get("marker", "ARCE_RELEASE_CARGO_CUT_PAUSED cut=" + case + " ")


def observations(lines: list[str], case: str, restart: int) -> None:
    require(len([line for line in lines if marker(case, restart) in line]) == 1, "Missing or repeated cut observation")
    if not restart and case in BOUNDARIES:
        require(any(marker(case, restart) + "boundary=" + BOUNDARIES[case] in line for line in lines), "Wrong settlement stop boundary")
    require(not any("/ERROR]" in line or "/FATAL]" in line for line in lines), "Native run logged ERROR or FATAL")
    wanted = CASES[case].get("fence")
    require(wanted is None or wanted in fences(lines), "The stale record was not fenced " + str(wanted) + ": " + str(fences(lines)))


def recovered_proofs(lines: list[str], frozen_digest: str) -> None:
    """O3-after must reacquire both proofs and the checked journal lease before O4 on restart 1."""
    prefix = "ARCE_RELEASE_CARGO_CUT_PROOF cut=FS_O3_AFTER checkpoint="
    checkpoints = [line.split(prefix, 1)[1].rstrip("\r\n") for line in lines if prefix in line]
    for checkpoint in ("O2_AFTER_0", "O2_AFTER_1", "O3J_AFTER", "O4_CAPTURE"):
        require(any(line.startswith(checkpoint + " digest=" + frozen_digest + " ") for line in checkpoints),
                "O3-after did not reacquire the frozen proof/lease at " + checkpoint)
    require(any(line == "O4_CAPTURE digest=" + frozen_digest + " proved=2 waiting=0" for line in checkpoints),
            "O4 did not retain both valid exact proofs")


def run(args) -> None:
    pin = not args.dev
    if pin:
        assert_pin(args.source_commit)
    require_lock(args.lock_owner); preflight()
    session, evidence = args.session_dir.resolve(), args.evidence_dir.resolve()
    require(not session.exists() and not evidence.exists() and session != evidence and session not in evidence.parents
            and evidence not in session.parents, "Existing or overlapping output")
    libraries = args.source_server.resolve() / "libraries"
    require(libraries.is_dir() and not any(path.is_symlink() for path in libraries.rglob("*")), "Unsafe or missing template libraries")
    session.mkdir(parents=True); evidence.mkdir(parents=True)
    token = uuid.uuid4().hex; sentinel = session / ".w142-owned"; sentinel.write_text(token, encoding="ascii")
    pins = dict(source_commit=args.source_commit if pin else "DEV-UNPINNED", jar_sha256=digest(args.jar),
                hooks={path: digest(REPO / path) for path in HOOKS + HARNESS})
    report = dict(status="IN_PROGRESS", development=not pin, cases=[], pins=pins, cleanup=False)
    process = None
    try:
        shutil.copytree(libraries, session / "libraries")
        (session / "mods").mkdir(); shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = smoke.allocate_port(); smoke.write_server_configuration(session, port, True)
        for case in args.case:
            row = dict(case=case, family=CASES[case]["family"], pins=pins, starts=[], findings=[]); report["cases"].append(row)
            properties = smoke.expected_server_properties(port, True)
            properties.update({"level-name": "world-" + case, "level-type": "minecraft:flat", "max-tick-time": "60000",
                               "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}], "biome": "minecraft:plains"})})
            (session / "server.properties").write_text("".join(f"{key}={value}\n" for key, value in sorted(properties.items())), encoding="ascii")
            for restart in range(3):
                if pin:
                    assert_pin(args.source_commit)
                require_lock(args.lock_owner)
                require(digest(args.jar) == pins["jar_sha256"], "JAR changed")
                command = _server_command(args.java); command[2] = "-Xmx4G"
                command[1:1] = ["-Dadvancedrocketrycommunity.escrowScenario=" + ("recover" if restart else "start"),
                                "-Dadvancedrocketrycommunity.escrowCase=" + case]
                if not restart:
                    command[1:1] = [f"-D{key}={value}" for key, value in sorted(CASES[case].get("properties", {}).items())]
                start = dict(restart=restart, preflight=preflight(), command=command); row["starts"].append(start)
                process = smoke.CapturedProcess(command, session, evidence / f"{case}-{restart}.log")
                execution_error = None
                try:
                    process.wait_for(re.compile(re.escape(marker(case, restart))), args.startup_timeout)
                    if restart:
                        process.command("stop"); start["exit_code"] = process.finish()
                    else:
                        process.process.kill(); process.abort(); start["forced_stop"] = True
                except (OSError, smoke.SmokeError) as failure:
                    execution_error = str(failure)
                    process.abort()
                    start["exit_code"] = process.process.poll()
                lines = process.lines; process = None
                world = session / ("world-" + case)
                try:
                    raw = evidence / f"{case}-{restart}-raw.json"
                    raw.write_text(json.dumps(raw_snapshot(world), indent=1), encoding="utf-8")
                    start["raw"] = dict(file=raw.name, sha256=digest(raw))
                except (OSError, ValueError, KeyError, smoke.SmokeError) as failure:
                    start["raw"] = dict(error=str(failure))
                try:
                    require(execution_error is None, "Start execution: " + str(execution_error))
                    require("error" not in start["raw"], "Raw decoding: " + str(start["raw"].get("error")))
                    observations(lines, case, restart)
                    if case == "FS_O3_AFTER" and restart == 1:
                        first_raw = json.loads((evidence / f"{case}-0-raw.json").read_text(encoding="utf-8"))
                        recovered_proofs(lines, first_raw["ledger"]["records"][0]["settlement"]["digest"])
                    require(not restart or start["exit_code"] == 0, "Restart exited unsuccessfully")
                    disk = inspect(world); check(case, disk, bool(restart))
                    start["disk"] = dict(ledger=dict(records=[(op["phase"], op["reason"], "settlement" in op) for op in disk["ledger"]["records"]],
                                                     receipts=[(r["choice"], r["released"], len(r["survivors"]), len(r["missing"])) for r in disk["ledger"]["tombstones"]],
                                                     heads=[(h["e"], h["outcome"]) for h in disk["ledger"]["rocket_authorities"]],
                                                     serial=disk["ledger"]["issued_launch_serial"]),
                                         journal=disk["journal"], rockets=disk["rockets"], device=disk["device"], second=disk["second"],
                                         tank=disk["tank"], blocks=disk["blocks"], fences=fences(lines), files=disk["files"])
                    if restart and "disk" in row["starts"][0]:
                        require(same_identity(row["starts"][0]["disk"], disk), "Restart changed the logical rocket, owner or device")
                    if case == "FS_O3_AFTER" and restart:
                        first = json.loads((evidence / f"{case}-0-raw.json").read_text(encoding="utf-8"))["ledger"]["tombstones"][0]
                        receipt = disk["ledger"]["tombstones"][0]
                        require(all(receipt[key] == first[key] for key in ("op_id", "before_choice", "decision_digest", "payload_hash")),
                                "Receipt: governed launch changed the frozen audit")
                    start["status"] = "PASS"
                except (OSError, ValueError, KeyError, smoke.SmokeError) as failure:
                    start.update(status="FAILED", error=str(failure)); row["findings"].append(f"start {restart}: {failure}")
                    start["native_files"] = retain_failed_files(session, world, evidence / f"{case}-{restart}-native")
                (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
            row["status"] = "FAILED" if row["findings"] else "PASS"
            require(not row["findings"], "First contradictory cut: " + case + ": " + "; ".join(row["findings"]))
            print(case + ": PASS (forced stop and two restarts)", flush=True)
        require(all(row["status"] == "PASS" for row in report["cases"]), "Development run had failing cuts")
        report["status"] = "PASS"
    except Exception as failure:
        report.update(status="FAILED", error=str(failure)); raise
    finally:
        if process is not None:
            process.abort()
        if session.resolve() == session and not session.is_symlink() and sentinel.is_file() and sentinel.read_text(encoding="ascii") == token:
            shutil.rmtree(session); report["cleanup"] = not session.exists()
        (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument("source_server", type=Path)
    for key in ("jar", "session-dir", "evidence-dir"):
        parser.add_argument("--" + key, type=Path, required=True)
    for key in ("source-commit", "java", "lock-owner"):
        parser.add_argument("--" + key, required=True)
    parser.add_argument("--case", action="append", choices=CASES, required=True)
    parser.add_argument("--startup-timeout", type=float, default=420)
    parser.add_argument("--dev", action="store_true", help="Development run on an uncommitted tree; never evidence")
    args = parser.parse_args()
    try:
        run(args); return 0
    except (OSError, smoke.SmokeError) as failure:
        print("FAIL: " + str(failure)); return 1


if __name__ == "__main__":
    raise SystemExit(main())
