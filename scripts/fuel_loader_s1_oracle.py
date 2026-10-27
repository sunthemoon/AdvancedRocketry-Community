"""Typed native-file assertions for the fuel loader's B3-D S1 rows; no world edits."""
from __future__ import annotations

import copy
import hashlib
import json
from pathlib import Path
import re
import zipfile

import cargo_s1_oracle as native
from cargo_s1_oracle import require, same_typed, Int, Long, Byte, IntArray, ByteArray
from run_v180_resource_restarts import entity_slots, read_nbt, JOURNAL
from run_v180_escrow_forced_stop import record, LEDGER

NS = native.NS
KEY = "arce_fluid_fuel_loader"
OLD_KEY = "arce_fuel_loader"
VARIANTS = ("future", "invalid", "oversized", "work-limit", "missing")
RESULT = re.compile(r"ARCE_W176_RESULT stage=(seed|check|old-check) snapshot=([0-9a-f]{64}) "
                    r"schema=3 protected=(\d+) g2=(true|false) old_loader=(true|false)$")


def uuid_nbt(index):
    return IntArray([0, 176, 0, index])


def neutral():
    return dict(schema_version=Int(1), resource_epoch=Long(0), markers=[])


def expected_root(identity, binding, variant):
    root = dict(schema_version=Int(1), owner=uuid_nbt(1), device=uuid_nbt(identity),
                tank=dict(FluidName=NS + ":rocket_fuel", Amount=Int(2345), Tag=native.typed_tag()),
                resource_escrow=neutral())
    if binding is not None:
        root["binding"] = copy.deepcopy(binding)
    if variant == "future":
        root.update(schema_version=Int(2), extension=native.typed_tag())
    elif variant == "invalid":
        root["tank"]["Amount"] = Int(-1)
    elif variant == "oversized":
        root["extension"] = "x" * 33000
    elif variant == "work-limit":
        root["opaque"] = ByteArray([0] * 2_097_153)
    elif variant == "missing":
        root["tank"]["FluidName"] = "s1missing:fluid"
    else:
        require(variant == "valid", "Unknown variant")
    return root


def old_root():
    return dict(schema_version=Int(2), slot_role=Int(2),
                item=dict(id="minecraft:bucket", Count=Byte(1), tag=native.typed_tag()),
                buffered_units=Long(0), owner_id=uuid_nbt(1))


def one_device(terrain, at, identity, key):
    matches = [be for be in terrain.get("block_entities", []) if [be.get(k) for k in ("x", "y", "z")] == list(at)]
    require(len(matches) == 1 and matches[0].get("id") == NS + ":" + identity, "Missing/duplicate/misplaced device")
    require(native.block_at(terrain, at)["Name"] == NS + ":" + identity, "Device block and BE disagree")
    require(key in matches[0], "Device root missing")
    return matches[0][key]


def check_new(rocket, terrain):
    data = rocket["RocketEntityData"]
    require(rocket["id"] == NS + ":rocket" and rocket["Pos"] == [72.5, 100.0, 72.5]
            and not rocket.get("Passengers") and same_typed(data["owner_id"], uuid_nbt(1)), "Wrong rocket identity/position")
    require(type(data["schema_version"]) is Int and data["schema_version"] == 3
            and same_typed(data["resource_escrow"], neutral()), "New rocket schema/escrow changed")
    snapshot = data["snapshot"]; blocks = snapshot["relative_blocks"]
    names = [snapshot["block_palette"][block["palette"]]["id"] for block in blocks]
    require(sorted(names) == sorted(NS + ":" + value for value in ("rocket_motor", "rocket_seat", "guidance_computer", "rocket_fuel_tank")),
            "New rocket geometry changed")
    require(sum(KEY in be for be in terrain.get("block_entities", [])) == 6, "Loader count changed")
    devices = {}
    fixtures = [((72, 100, 76), 10, "valid", native.binding(rocket))]
    fixtures += [((64 + i, 100, 79), 20 + i, variant, None) for i, variant in enumerate(VARIANTS)]
    for at, identity, variant, binding in fixtures:
        root = one_device(terrain, at, "fluid_fuel_loader", KEY)
        require(same_typed(root, expected_root(identity, binding, variant)), "Loader root/types changed: " + variant)
        devices[variant] = root
    return dict(rocket={key: rocket[key] for key in ("id", "UUID", "Pos", "RocketEntityData")}, devices=devices)


def check_old(rocket, terrain, schema, before=None):
    result = native.check_old([rocket], terrain, False, None)
    data = result["RocketEntityData"]
    require(type(data["schema_version"]) is Int and data["schema_version"] == schema, "Wrong old-world entity schema")
    if schema == 2:
        require("resource_escrow" not in data, "Old seed already has escrow")
    else:
        require(same_typed(data["resource_escrow"], neutral()), "Upgrade created non-neutral rocket escrow")
    loader = one_device(terrain, (260, 101, 256), "fuel_loader", OLD_KEY)
    require(same_typed(loader, old_root()), "Old item loader changed")
    result = dict(rocket=result, old_loader=loader)
    if before is not None:
        expected = copy.deepcopy(before)
        if expected["rocket"]["RocketEntityData"]["schema_version"] == 2:
            expected["rocket"]["RocketEntityData"].update(schema_version=Int(3), resource_escrow=neutral())
        native.compare_exact(expected, result)
    return result


def evidence_tree(value):
    # Retain the complete native compressed record, but do not expand a 2 MiB zero array into 40 MiB of JSON.
    if isinstance(value, dict):
        return {key: evidence_tree(item) for key, item in sorted(value.items())}
    if isinstance(value, list) and len(value) > 1024:
        return dict(type=type(value).__name__, length=len(value),
                    sha256=hashlib.sha256(json.dumps(value, separators=(",", ":")).encode()).hexdigest())
    if isinstance(value, list):
        return dict(type=type(value).__name__, value=[evidence_tree(item) for item in value])
    return native.typed_tree(value)


def inspect(world: Path, target: Path, stage: str, before=None):
    target.mkdir(); rockets = []
    for path in sorted(world.rglob("entities/*.mca")):
        for slot in entity_slots(path):
            entry = record(path, slot, reader=native.NativeS1Reader)
            found = [entity for entity in entry.get("Entities", []) if "RocketEntityData" in entity]
            if found:
                native.capture_record(path, slot, target / path.relative_to(world).with_suffix(f".{slot}.nbt.zlib"))
                rockets.extend(found)
    require(len(rockets) == 1, "Missing or duplicate native rocket")
    old = stage.startswith("old-") or stage == "downgrade"
    terrain = native.capture_record(world / "region/r.0.0.mca", 528 if old else 132, target / "terrain.nbt.zlib")
    require(not (world / "data" / LEDGER).exists(), "S1 created a ledger")
    journal = world / "data" / JOURNAL
    if journal.exists():
        require(not read_nbt(journal)["data"]["transfers"], "S1 created a travel record")
        (target / JOURNAL).write_bytes(journal.read_bytes())
    if old:
        result = check_old(rockets[0], terrain, 2 if stage == "old-seed" else 3, before)
    else:
        result = check_new(rockets[0], terrain)
        if before is not None:
            native.compare_exact(before, result)
    (target / "inspection.json").write_text(json.dumps(evidence_tree(result), indent=2) + "\n", encoding="utf-8")
    return result


def check_result(lines, stage, snapshot=None):
    matches = [match for line in lines if (match := RESULT.search(line))]
    require(len(matches) == 1, "Missing/repeated native loader result")
    match = matches[0]; old = stage == "old-check"
    require(match[1] == stage and int(match[3]) == (0 if old else 5)
            and match[4] == str(not old).lower() and match[5] == str(old).lower(), "Incomplete S1 live result")
    require(snapshot is None or match[2] == snapshot, "Live snapshot hash changed")
    return dict(snapshot=match[2], g2=not old, old_loader=old)


def check_jar(path, old):
    prefix = "io/github/sunthemoon/advancedrocketrycommunity/rocket/"
    with zipfile.ZipFile(path) as jar:
        names = jar.namelist()
        require(len(names) == len(set(names)), "Duplicate JAR entry")
        require(prefix + "fuel/FuelLoaderBlockEntity.class" in names, "Old item loader is absent")
        require((prefix + "fuel/loader/FluidFuelLoaderBlockEntity.class" in names) != old, "Wrong pre-loader/new JAR boundary")
        if not old:
            require(prefix + "command/FuelLoaderS1ReleaseProbe.class" in names, "New S1 observer missing")
    return dict(path=str(path), sha256=native.smoke.digest_file(path), old=old)
