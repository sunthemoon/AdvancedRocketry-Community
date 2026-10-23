"""D-S typed-item oracle: expectations are independent of probe result text."""
from __future__ import annotations
import json
from pathlib import Path
import run_v180_cargo_reconciliation as native
from run_v180_rocket_settlement_completion import slot_present
from run_v180_landed_disassembly_cuts import block_at

ROCKET_X, PAD_X = 68.5, 80.5
APPLIED = dict(rocket=(6, 1, 1, True), device=(64, 1, 1))
PRUNED = dict(rocket=(6, 1, 0, True), device=(64, 1, 0))
NEUTRAL = dict(rocket=(10, 0, 0, False), device=(60, 0, 0))
NORMALIZED = dict(rocket=(10, 0, 0, True), device=(60, 0, 0))
ACCEPT = dict(records=["SETTLING_ACCEPT"], intent=True, receipts=[], heads=0)
ABANDON = dict(records=["SETTLING_ABANDON"], intent=True, receipts=[], heads=0)
RECEIPT = dict(records=["SETTLING_ACCEPT"], intent=True, receipts=[False], heads=0)
RELEASED = dict(records=[], intent=False, receipts=[True], heads=1)
ORPHANED = dict(records=[], intent=False, receipts=[True], heads=0)

def settlement(cut_ledger, cut_ends, final_ledger, final_ends, **extra):
    return dict(cut=(cut_ledger, cut_ends), final=(final_ledger, final_ends), **extra)

CASES = {
    "IS_O1_AFTER": settlement(ACCEPT, APPLIED, RELEASED, PRUNED),
    **{f"IS_O2_{side}_{when}": settlement(ACCEPT, APPLIED, RELEASED, PRUNED)
       for side in ("ROCKET", "DEVICE") for when in ("BEFORE", "AFTER")},
    "IS_O3_AFTER": settlement(RECEIPT, APPLIED, RELEASED, dict(rocket=(6, 0, 0, True), device=(64, 1, 0)), landed=True),
    "IS_O3J_AFTER": settlement(RECEIPT, NORMALIZED, RELEASED, NORMALIZED),
    "IS_O4_AFTER": settlement(RELEASED, APPLIED, RELEASED, PRUNED),
    "IS_PRUNE_AFTER": settlement(RELEASED, PRUNED, RELEASED, PRUNED),
    "IS_BIND_AFTER": settlement(ACCEPT, NEUTRAL, RELEASED, NORMALIZED),
    "IS_BIND_VETO": settlement(ACCEPT, NEUTRAL, RELEASED, NORMALIZED, cut_x=68.75),
    "IS_ABANDON_ROCKET": settlement(ABANDON, dict(rocket=None, device=(60, 0, 0)), ORPHANED,
                                   dict(rocket=None, device=(60, 0, 0)), choice="ABANDON"),
    "IS_ABANDON_DEVICE": settlement(ABANDON, dict(rocket=NEUTRAL["rocket"], device=None), RELEASED,
                                   dict(rocket=NORMALIZED["rocket"], device=None), choice="ABANDON"),
}

def require(condition, message):
    if not condition:
        raise native.smoke.SmokeError(message)

def exact_stack(stacks, device=False):
    total = native.item_amount(stacks, device)
    expected = [native.item_stack(total), {}, {}, {}] if device else [native.item_stack(total, 0)]
    require(native.same_typed(stacks, expected), "Typed stack placement or remainder changed")
    return total

def read(world: Path):
    terrain = native.record(world / "region/r.0.0.mca", reader=native.TypedNbtReader)
    entities = []
    for slot in (132, 133):
        path = world / "entities/r.0.0.mca"
        if slot_present(path, slot):
            entities += [e for e in native.record(path, slot, reader=native.TypedNbtReader)["Entities"] if "RocketEntityData" in e]
    return dict(ledger=native.ledger(world / "data" / native.LEDGER), terrain=terrain,
                entities=entities, transfers=native.journal(world)["transfers"])

def inspect(world: Path, case: str):
    raw = read(world); root = raw["ledger"]
    rockets = []
    for entity in raw["entities"]:
        data = entity["RocketEntityData"]
        blocks = [b["block_entity"] for b in data["snapshot"]["relative_blocks"] if "block_entity" in b]
        require(len(blocks) == 1 and blocks[0]["adapter"] == "advancedrocketrycommunity:vanilla_container_v1", "Container adapter changed")
        payload = blocks[0]["data"]
        if case == "IR_LOOT":
            require(native.same_typed(payload, dict(LootTable="minecraft:chests/simple_dungeon", LootTableSeed=native.Long(143))),
                    "Unresolved LootTable was generated, normalized or changed")
            cargo = None  # Unknown is not empty cargo.
        else:
            require(set(payload) == {"Items"}, "Container payload has extra or missing data")
            cargo = exact_stack(payload["Items"])
        section = data["resource_escrow"]
        require("ledger_id" not in section or section["ledger_id"] == root["ledger_id"], "Rocket ledger changed")
        rockets.append(dict(uuid=entity["UUID"], logical=data["assembly_transaction_id"], owner=data["owner_id"],
                            x=entity["Pos"][0], pos=entity["Pos"], cargo=cargo, epoch=section["resource_epoch"], markers=section["markers"],
                            bound="ledger_id" in section, passengers=data["flight_data"]["passengers"]["assignments"],
                            fuel=data["flight_data"]["fuel"]["amount"], flight_state=data["flight_data"]["state"], data=data))
    devices = [e for e in raw["terrain"]["block_entities"] if [e.get(k) for k in ("x", "y", "z")] == [64, 100, 64]]
    require(len(devices) <= 1, "Repeated item device")
    device = None
    if devices:
        value = devices[0]["arce_rocket_item_cargo"]; section = value["resource_escrow"]
        require("ledger_id" not in section or section["ledger_id"] == root["ledger_id"], "Device ledger changed")
        device = dict(id=value["device"], owner=value["owner"], role=value["role"], logical=value["binding"]["logical_rocket"],
                      amount=exact_stack(value["buffer"]["slots"], True), epoch=section["resource_epoch"], markers=section["markers"], data=value)
    containers = [e for e in raw["terrain"]["block_entities"] if [e.get(k) for k in ("x", "y", "z")] == [68, 100, 64]]
    restored = exact_stack(containers[0]["Items"]) if containers else 0
    return dict(ledger=root, journal=len(raw["transfers"]), transfers=raw["transfers"], rockets=rockets,
                rocket=rockets[0] if len(rockets) == 1 else None, device=device, restored=restored,
                block=block_at(raw["terrain"], 68, 100, 64))

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
            kind = "FUEL_VECTOR_V1" if survivor["endpoint"]["kind"] == "ENTITY" else "ITEM_BUFFER_V1"
            require(survivor["signature_kind"] == kind and (kind != "ITEM_BUFFER_V1" or survivor["quantity"] == 0), "Settlement authority is not fuel-vector plus invariant for the rocket")
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
    require(raw <= 70 or root["records"], "More than 70 items became spendable")
    if rocket and device:
        require(device["logical"] == rocket["logical"] and device["owner"] == rocket["owner"], "Device binding or owner changed")


def check_recovery(case, disk, restarted):
    root, rocket, device = disk["ledger"], disk["rocket"], disk["device"]
    require(device is not None and device["role"] == "UNLOAD", "Recovery device missing or changed role")
    require(not root["tombstones"] and not root["floors"] and not root["rocket_authorities"]
            and not root["armed_flights"] and root["issued_launch_serial"] == 0, "Recovery invented release/flight authority")
    if case == "IR_DISASSEMBLY" and restarted:
        require(not disk["rockets"] and disk["restored"] == 6 and disk["journal"] == 0
                and device["amount"] == 64 and device["epoch"] == 1 and not device["markers"],
                "Disassembly did not restore exactly the latest six typed items")
        require(disk["block"] == "minecraft:chest", "Restored container changed")
        require(not root["records"] and root["issued_through"] == 1, "Disassembly changed issued cargo operation")
        return
    require(len(disk["rockets"]) == 1 and rocket["pos"] == [68.5, 100.0, 64.5] and not rocket["passengers"]
            and disk["restored"] == 0, "Recovery duplicated or moved its rocket/container")
    require(device["owner"] == rocket["owner"] and device["logical"] == rocket["logical"], "Recovery binding changed")
    if case in ("IR_CONTAINER", "IR_LOOT"):
        require(root["issued_through"] == 1 and len(root["records"]) == 1, "Damage lost/repeated operation")
        op = root["records"][0]
        require(op["phase"] == ("REPAIR" if restarted else "PREPARED"), "Damage was not held in REPAIR")
        if restarted:
            require(op["reason"] == "OTHER_STATE", "Damage has a different repair reason")
        require(rocket["epoch"] == device["epoch"] == 0 and not rocket["markers"] and not device["markers"]
                and device["amount"] == 60 and disk["journal"] == 0, "Damage overwrote an endpoint")
        palette = rocket["data"]["snapshot"]["block_palette"]
        require(palette[0]["id"] == ("minecraft:barrel" if case == "IR_CONTAINER" else "minecraft:chest"), "Changed container was replaced")
        require(rocket["cargo"] == (10 if case == "IR_CONTAINER" else None), "Damaged cargo was interpreted as an old or empty payload")
    elif case == "IR_ROOT":
        require(not root["records"] and root["issued_through"] == 0 and device["data"]["schema_version"] == 2
                and device["data"]["extension"] == "W143-preserve" and device["amount"] == 60 and rocket["cargo"] == 10
                and rocket["epoch"] == device["epoch"] == 0 and not rocket["markers"] and not device["markers"]
                and disk["journal"] == 0, "Future item root was normalized, lost or admitted")
    else:
        require(case in ("IR_REBUILD", "IR_DISASSEMBLY"), "Unknown recovery case")
        require(not root["records"] and root["issued_through"] == 1 and disk["journal"] == 1
                and rocket["cargo"] == 6 and device["amount"] == 64 and rocket["epoch"] == device["epoch"] == 1
                and not rocket["markers"] and not device["markers"], "Stale journal reconstructed or replaced latest items")


def preservation(disk):
    """Exact held roots, including typed lists, are compared across both restarts before JSON serialization."""
    return dict(snapshot=disk["rocket"]["data"]["snapshot"], device=disk["device"]["data"])
