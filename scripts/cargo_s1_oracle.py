"""Independent, typed native-file oracle for C-D/D-D fixtures, not escrow transactions."""
from __future__ import annotations

import copy
import hashlib
import json
from pathlib import Path
import re
import struct
import zipfile

import run_dedicated_server_smoke as smoke
from run_v180_cargo_reconciliation import (Byte, Short, Int, Long, ByteArray, IntArray, LongArray,
                                          TypedNbtReader, same_typed)
from run_v180_escrow_forced_stop import record, LEDGER
from run_v180_resource_restarts import entity_slots, read_nbt, JOURNAL

NS = "advancedrocketrycommunity"
VARIANTS = ("future", "invalid", "oversized", "marked", "missing")
RESULT = re.compile(r"ARCE_W136_RESULT leaf=(fluid|item) stage=(seed|check|old-check|old-restore) "
                    r"snapshot=([0-9a-f]{64}) blocks=(\d+) protected=(\d+) g2=(true|false) tags=true restore=true$")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise smoke.SmokeError(message)


def typed_tree(value):
    """JSON evidence that does not erase equal-valued NBT number/array types."""
    if isinstance(value, dict):
        return {key: typed_tree(item) for key, item in sorted(value.items())}
    if type(value) in (str, bool) or value is None:
        return value
    if isinstance(value, list):
        return {"type": type(value).__name__, "value": [typed_tree(item) for item in value]}
    return {"type": type(value).__name__, "value": value}


def uuid_nbt(index: int) -> IntArray:
    return IntArray([0, 136, 0, index])


def typed_tag() -> dict:
    return dict(b=Byte(7), s=Short(7), i=Int(7), l=Long(7), ba=ByteArray([1, 2]),
                ia=IntArray([1, 2]), la=LongArray([1, 2]))


def stack(count: int, slot: int | None = None) -> dict:
    result = dict(id="minecraft:stone", Count=Byte(count), tag=typed_tag())
    if slot is not None:
        result["Slot"] = Byte(slot)
    return result


def binding(rocket: dict) -> dict:
    return dict(schema_version=Int(1), logical_rocket=rocket["RocketEntityData"]["assembly_transaction_id"],
                physical_rocket=rocket["UUID"], level="minecraft:overworld", x=Int(72), y=Int(100), z=Int(72))


def expected_root(items: bool, role: int, identity: int, bound: dict | None, variant: str) -> dict:
    result = dict(schema_version=Int(1), owner=uuid_nbt(1), device=uuid_nbt(identity), role=("LOAD", "UNLOAD")[role],
                  resource_escrow=dict(schema_version=Int(1), resource_epoch=Long(0), markers=[]))
    if bound is not None:
        result["binding"] = copy.deepcopy(bound)
    if items:
        result["buffer"] = dict(slots=[stack(23), {}, {}, {}])
        resource = result["buffer"]["slots"][0]
    else:
        result["tank"] = dict(FluidName="minecraft:water", Amount=Int(2345), Tag=typed_tag())
        resource = result["tank"]
    if variant == "future":
        result.update(schema_version=Int(2), extension=typed_tag())
    elif variant == "invalid":
        resource["Count" if items else "Amount"] = Byte(-1) if items else Int(-1)
    elif variant == "oversized":
        result["extension"] = "x" * 33000
    elif variant == "marked":
        result["resource_escrow"] = dict(schema_version=Int(1), resource_epoch=Long(1), ledger_id=uuid_nbt(2),
                                        markers=[dict(op_id=uuid_nbt(3), seq=Long(1),
                                                      role=("SOURCE", "DESTINATION")[role], epoch_after=Long(1))])
    elif variant == "missing":
        resource["id" if items else "FluidName"] = "s1missing:item" if items else "s1missing:fluid"
    else:
        require(variant == "valid", "Unknown protected fixture")
    return result


def block_at(chunk: dict, position: tuple[int, int, int]) -> dict:
    """Exact 1.20.1 non-spanning palette cell, with the typed reader's Long values."""
    x, y, z = position
    require((chunk.get("xPos"), chunk.get("zPos")) == (x >> 4, z >> 4), "Wrong terrain chunk")
    sections = [entry for entry in chunk.get("sections", []) if entry.get("Y") == y >> 4]
    require(len(sections) == 1, "Missing or duplicate terrain section")
    states = sections[0]["block_states"]; palette = states["palette"]
    require(1 <= len(palette) <= 4096, "Invalid palette length")
    if len(palette) == 1:
        require(not states.get("data"), "Unexpected single-palette packed data")
        return palette[0]
    bits = max(4, (len(palette) - 1).bit_length()); per_word = 64 // bits
    data = states["data"]; require(len(data) == (4096 + per_word - 1) // per_word, "Invalid palette array length")
    cell = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15)
    index = ((data[cell // per_word] & ((1 << 64) - 1)) >> (cell % per_word * bits)) & ((1 << bits) - 1)
    require(index < len(palette), "Invalid palette index")
    return palette[index]


def check_new(rocket: dict, terrain: dict, items: bool) -> dict:
    data = rocket["RocketEntityData"]; snapshot = data["snapshot"]
    require(rocket["id"] == NS + ":rocket" and rocket["Pos"] == [72.5, 100.0, 72.5]
            and not rocket.get("Passengers") and data["owner_id"] == uuid_nbt(1), "Wrong native rocket identity or position")
    require(same_typed(data["resource_escrow"], dict(schema_version=Int(1), resource_epoch=Long(0), markers=[])),
            "Snapshot fixture acquired escrow authority")
    blocks = snapshot["relative_blocks"]; palette = snapshot["block_palette"]
    require(len(blocks) == 8, "Wrong snapshot block count")
    names = [palette[entry["palette"]]["id"] for entry in blocks]
    require(sorted(names) == sorted([NS + ":rocket_motor", NS + ":rocket_seat", NS + ":guidance_computer",
                                    NS + ":rocket_fuel_tank", "minecraft:chest", "minecraft:barrel", "minecraft:chest",
                                    "minecraft:iron_block" if items else NS + ":pressurized_tank"]), "Wrong snapshot palette")
    containers = {(0, 0, -1): (17, 0), (0, 0, 1): (18, 13), (2, 0, 0): (19, 26)}
    for entry, name in zip(blocks, names):
        payload = entry.get("block_entity")
        if name in ("minecraft:chest", "minecraft:barrel"):
            count, slot = containers[tuple(entry["position"])]
            require(payload["adapter"] == NS + ":vanilla_container_v1", "Wrong container adapter")
            require(same_typed(payload["data"], dict(Items=[stack(count, slot)],
                    CustomName='{"text":"S1 retained cargo"}', Lock="S1 lock")), "Container contents/types/name/lock changed")
        elif name == NS + ":pressurized_tank":
            require(payload["adapter"] == NS + ":pressurized_tank_v1" and same_typed(payload["data"],
                    dict(tank=dict(schema=Int(1), fluid=dict(FluidName="minecraft:water", Amount=Int(3456), Tag=typed_tag())))),
                    "Tank snapshot changed its native fluid or typed metadata")
        else:
            require(payload is None, "Unexpected snapshot payload")
    root_key = "arce_rocket_" + ("item" if items else "fluid") + "_cargo"
    devices = [entity for entity in terrain.get("block_entities", []) if root_key in entity]
    require(len(devices) == 12, "Missing, extra or duplicate cargo device")
    result = {}
    for role in range(2):
        fixtures = [(72 + role * 2, 76, 10 + role, "valid", binding(rocket))]
        fixtures += [(64 + role * 5 + i, 79, 20 + role * 5 + i, variant, None) for i, variant in enumerate(VARIANTS)]
        for x, z, identity, variant, bound in fixtures:
            matches = [entry for entry in devices if [entry.get(k) for k in ("x", "y", "z")] == [x, 100, z]]
            device_id = NS + ":rocket_" + ("item" if items else "fluid") + ("_loader", "_unloader")[role]
            require(len(matches) == 1 and matches[0]["id"] == device_id, "Wrong device identity or coordinate")
            require(block_at(terrain, (x, 100, z))["Name"] == device_id, "Saved device block no longer matches its entity")
            root = matches[0][root_key]
            require(same_typed(root, expected_root(items, role, identity, bound, variant)),
                    f"Device root changed: role={role} variant={variant}")
            result[f"{role}-{variant}"] = root
    return dict(rocket={key: rocket[key] for key in ("id", "UUID", "Pos", "RocketEntityData")}, devices=result)


def compare_exact(before: dict, after: dict) -> None:
    require(same_typed(before, after), "Restart changed native fixture values or NBT tag types")


def check_old(rockets: list[dict], terrain: dict, restored: bool, before: dict | None) -> dict:
    require(len(rockets) == (0 if restored else 1), "Old rocket is missing or duplicated")
    expected_items = [dict(Slot=Byte(0), id="minecraft:diamond", Count=Byte(17)),
                      dict(Slot=Byte(26), id="minecraft:iron_ingot", Count=Byte(64))]
    if restored:
        matches = [be for be in terrain["block_entities"] if [be.get(k) for k in ("x", "y", "z")] == [257, 101, 256]]
        require(len(matches) == 1 and matches[0]["id"] == "minecraft:chest"
                and same_typed(matches[0]["Items"], expected_items), "Old restored chest contents or types changed")
        require(before is not None, "No old snapshot to compare restored geometry")
        snapshot = before["RocketEntityData"]["snapshot"]
        for block in snapshot["relative_blocks"]:
            state = snapshot["block_palette"][block["palette"]]
            at = tuple(a + b for a, b in zip(snapshot["source_origin"], block["position"]))
            expected_state = {"Name": state["id"]}
            if state["properties"]:
                expected_state["Properties"] = state["properties"]
            require(block_at(terrain, at) == expected_state, "Old restored block state changed")
        return dict(restored_items=matches[0]["Items"], restored_blocks=len(snapshot["relative_blocks"]))
    rocket = rockets[0]; data = rocket["RocketEntityData"]
    snapshot = data["snapshot"]
    require(len(snapshot["relative_blocks"]) == 4, "Old fixture has wrong geometry")
    payloads = [block["block_entity"] for block in snapshot["relative_blocks"] if "block_entity" in block]
    require(len(payloads) == 1 and payloads[0]["adapter"] == NS + ":vanilla_container_v1"
            and same_typed(payloads[0]["data"].get("Items"), expected_items), "Old snapshot lost its chest")
    result = {key: rocket[key] for key in ("id", "UUID", "Pos", "RocketEntityData")}
    if before is not None:
        compare_exact(before, result)
    return result


def capture_record(path: Path, slot: int, target: Path) -> dict:
    result = record(path, slot, reader=TypedNbtReader)
    with path.open("rb") as stream:
        stream.seek(slot * 4); location = struct.unpack(">I", stream.read(4))[0]
        stream.seek((location >> 8) * 4096); length, compression = struct.unpack(">IB", stream.read(5))
        require(compression == 2, "Unsupported compression")
        raw = stream.read(length - 1)
    target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes(raw)
    return result


def inspect(world: Path, target: Path, items: bool, old: bool, restored: bool = False, before: dict | None = None) -> dict:
    target.mkdir()
    rockets = []
    for path in sorted(world.rglob("entities/*.mca")):
        for slot in entity_slots(path):
            entry = record(path, slot, reader=TypedNbtReader)
            found = [entity for entity in entry.get("Entities", []) if "RocketEntityData" in entity]
            if found:
                capture_record(path, slot, target / path.relative_to(world).with_suffix(f".{slot}.nbt.zlib"))
                rockets.extend(found)
    terrain = capture_record(world / "region/r.0.0.mca", 528 if old else 132, target / "terrain.nbt.zlib")
    require(not (world / "data" / LEDGER).exists(), "S1 created an escrow ledger")
    journal = world / "data" / JOURNAL
    if journal.exists():
        require(not read_nbt(journal)["data"]["transfers"], "S1 created a travel record")
        (target / JOURNAL).write_bytes(journal.read_bytes())
    if old:
        result = check_old(rockets, terrain, restored, before)
    else:
        require(len(rockets) == 1, "Snapshot fixture lost or duplicated a rocket")
        result = check_new(rockets[0], terrain, items)
        if before is not None:
            compare_exact(before, result)
    (target / "inspection.json").write_text(json.dumps(typed_tree(result), indent=2) + "\n", encoding="utf-8")
    return result


def check_result(lines: list[str], leaf: str, stage: str, snapshot_hash: str | None = None) -> dict:
    matches = [match for line in lines if (match := RESULT.search(line))]
    require(len(matches) == 1, "Missing or repeated S1 result")
    match = matches[0]; old = stage.startswith("old-")
    require(match[1] == leaf and match[2] == stage and int(match[4]) == (4 if old else 8)
            and int(match[5]) == (0 if old else 10) and match[6] == str(not old).lower(), "Incomplete S1 live result")
    require(snapshot_hash is None or snapshot_hash == match[3], "Live snapshot hash changed")
    return dict(snapshot_hash=match[3], blocks=int(match[4]), protected=int(match[5]), g2=not old)


def check_jar(path: Path, old: bool) -> dict:
    with zipfile.ZipFile(path) as jar:
        names = jar.namelist(); require(len(names) == len(set(names)), "Duplicate JAR entry")
        cargo = "io/github/sunthemoon/advancedrocketrycommunity/rocket/forge/PressurizedTankRocketAdapter.class"
        require((cargo in names) != old, "Old/new cargo artifact boundary is incorrect")
        tag = json.loads(jar.read(f"data/{NS}/tags/blocks/rocket_movable.json"))
        values = tag["values"]
        for name in ("minecraft:chest", "minecraft:barrel", NS + ":rocket_motor"):
            require(name in values, "Packaged production tag lost an old entry")
        if not old:
            require(NS + ":pressurized_tank" in values and "minecraft:trapped_chest" not in values, "Packaged cargo tag boundary changed")
        else:
            require(NS + ":pressurized_tank" not in values, "Baseline already had cargo tank assembly")
    return dict(sha256=smoke.digest_file(path), movable_tag=tag, old=old)
