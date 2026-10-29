"""Native B-PLAIN/B-M oracle. Never accept W85's pre-I6 unsaved-D loss."""
from __future__ import annotations

import copy
from pathlib import Path

import run_v180_predecessor_matrix as predecessor
from run_v180_predecessor_smoke import require
from run_v180_first_event_moon_cuts import PROGRESSION, MOON_RECEIPT, WARP_RECEIPT, read_root

# Whether the actual file must contain the identical predecessor at the cut and both restarts.
CASES = dict(PLAIN_MOVE=True, PLAIN_MOVED=False, PLAIN_UNKNOWN=False, PLAIN_ACK=False,
             K1=True, K2=False, K3_MOVE=False, K3_UNKNOWN=True, K4=True,
             K9_REFUSED=True, K9_LATE=True, K10_ON=True, K10_OFF=True, K10_FAIL=False)
OBSTRUCTED = {"K3_MOVE", "K3_UNKNOWN", "K4"}


def check_disk(case, disk, old, fuel, restart, event=None):
    require(case in CASES and restart in (0, 1, 2), "Unknown I6 observation")
    entities = disk["entities"]
    require(len(entities) == 1 and entities[0].get("depth", 0) == 0, "Exactly one top-level durable D1 is required")
    entity = entities[0]["entity"]; data = entity["RocketEntityData"]; flight = data["flight_data"]
    require(entity["UUID"] == old["destination_entity_id"] and not entity.get("Passengers"), "Wrong physical D1 or nested authority")
    require(data["assembly_transaction_id"] == old["logical_rocket_id"] and data["owner_id"] == old["owner_id"], "Changed logical/owner identity")
    snapshot = old["destination_snapshot"]; origin = snapshot["source_origin"]
    require(data["snapshot"] == snapshot, "Changed complete blocks/items/fluids/energy snapshot")
    require(flight["fuel"] == fuel, "Changed current fuel banks or debit history")
    require(entity["Pos"] == [origin[0] + .5, origin[1], origin[2] + .5]
            and flight["current_origin"] == origin and flight["current_dimension"] == snapshot["source_dimension"]
            and flight["logical_rocket_id"] == old["logical_rocket_id"], "Changed D1 location or flight identity")
    require(flight["state"] in ("LANDED", "FUELED", "ASSEMBLED") and not flight.get("active_transfer_id"), "D1 is not stationary")
    require(flight["passengers"]["seat_capacity"] == old["destination_flight"]["passengers"]["seat_capacity"]
            and not flight["passengers"]["assignments"], "Changed crew")
    root = disk["journal"]
    require(root.get("schema_version") == 3 and root.get("format_epoch") == "v1.8.0-first-moon-landing", "Changed journal envelope")
    require(root["transfers"] == ([old] if CASES[case] else []), "Wrong actual T authority for " + case)
    if restart == 0 and case in ("PLAIN_MOVE", "K1", "K3_MOVE"):
        require(disk.get("pending", {}).get("transfers") == ([old] if case == "K3_MOVE" else []), "Wrong staged T at pre-move cut")
    world = disk["world_authority"]
    require(not world["transactions"].get("transactions"), "Unexpected retained U")
    obstruction = None
    if case in OBSTRUCTED:
        obstruction = [origin[i] + snapshot["relative_blocks"][0]["position"][i] for i in range(3)]
    for block in world["payload_blocks"]:
        allowed = block["state"] in ("UNWRITTEN", "minecraft:air", "minecraft:cave_air", "minecraft:void_air")
        require(allowed or block["end"] == "destination_snapshot" and block["position"] == obstruction
                and block["state"] == "minecraft:stone", "Restored payload duplicates D1")
    outbox = root["classic_first_moon_landing"]
    if case.startswith("PLAIN_"):
        require(event is not None, "Plain cut must preserve a nonempty elected landing event")
        require(outbox == dict(schema_version=1, state="ELECTED" if restart == 0 else "DELIVERED", event=event),
                "Plain removal changed its exact landing outbox")
        require(disk.get("receipt", dict(occurred=0)) == (dict(occurred=0) if restart == 0 else
                dict(occurred=1, source="rocket_transfers", event=event)),
                "Receipt is not the exact durable R(E)")
    else:
        require(outbox == dict(schema_version=1, state="EMPTY"), "Unexpected landing event")
    return dict(entity=copy.deepcopy(data), transfer=copy.deepcopy(root["transfers"]), outbox=copy.deepcopy(outbox),
                receipt=copy.deepcopy(disk.get("receipt")), world=copy.deepcopy(world))


def read_disk(world: Path, old: dict, case: str) -> dict:
    disk = predecessor.read_disk(world)
    # Also census nested rockets and players, including passengers of non-rocket roots.
    census = []
    def visit(entity, region, slot, depth=0):
        require(depth <= 64, "Nested native entity exceeds the reader bound")
        if "RocketEntityData" in entity or entity.get("id") == "minecraft:player":
            require("RocketEntityData" in entity, "Saved player authority in entity storage")
            census.append(dict(entity=entity, region=region, slot=slot, depth=depth))
        for child in entity.get("Passengers", []): visit(child, region, slot, depth + 1)
    for path in sorted(world.rglob("r.*.mca")):
        if path.parent.name != "entities": continue
        for slot in predecessor.allocated_slots(path):
            for entity in predecessor.record(path, slot).get("Entities", []):
                visit(entity, path.relative_to(world).as_posix(), slot)
    disk["entities"] = census
    # Reuse the terrain reader's full block/BE check. The sole test-owned stone is
    # observed separately, not deleted or replaced in the native input files.
    disk["world_authority"] = world_authority(world, old, case)
    path = world / "data" / PROGRESSION
    if path.exists():
        progression = read_root(path)
        require(progression.get(WARP_RECEIPT) == dict(occurred=0), "Changed station event")
        disk["receipt"] = progression.get(MOON_RECEIPT)
    return disk


def world_authority(world: Path, old: dict, case: str) -> dict:
    if case not in OBSTRUCTED: return predecessor.world_authority(world, old)
    # Read original terrain without coercing the obstruction into air. All other
    # cells and every payload BE remain subject to the same absence assertion.
    from run_v180_landed_disassembly_cuts import block_at
    path = world / "data/advancedrocketrycommunity_rocket_transactions.dat"
    transactions = predecessor.read_journal(path) if path.exists() else {}
    require(not transactions.get("transactions"), "Unexpected assembly/disassembly authority")
    observations = []; cache = {}
    for endpoint in ("source_snapshot", "destination_snapshot"):
        snapshot = old[endpoint]; dimension = snapshot["source_dimension"]; origin = snapshot["source_origin"]
        base = world if dimension == "minecraft:overworld" else world / "dimensions" / Path(*dimension.split(":"))
        for block in snapshot["relative_blocks"]:
            x, y, z = [origin[i] + block["position"][i] for i in range(3)]
            region = base / "region" / f"r.{x >> 9}.{z >> 9}.mca"; slot = ((x >> 4) & 31) + (((z >> 4) & 31) * 32)
            key = str(region), slot
            if key not in cache:
                cache[key] = predecessor.record(region, slot) if region.exists() and slot in predecessor.allocated_slots(region) else None
            chunk = cache[key]
            require(chunk is not None or endpoint != "source_snapshot", "Saved source terrain is missing")
            name = "UNWRITTEN" if chunk is None else block_at(chunk, x, y, z)
            if name == "<section-not-stored>": name = "minecraft:air"
            require(not any([be.get("x"), be.get("y"), be.get("z")] == [x, y, z]
                            for be in (chunk or {}).get("block_entities", [])), "Payload BE beside D1")
            observations.append(dict(end=endpoint, position=[x, y, z], state=name))
    return dict(transactions=transactions, payload_blocks=observations)
