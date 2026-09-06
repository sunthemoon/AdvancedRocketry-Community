#!/usr/bin/env python3
"""Bounded, read-only projections of the representative Beta world fixture."""

from __future__ import annotations

import gzip
import uuid
from pathlib import Path

if __package__:
    from .inspect_celestial_saved_data import NbtReader
    from .run_dedicated_server_smoke import SmokeError, digest_file, is_link_or_junction
else:
    from inspect_celestial_saved_data import NbtReader
    from run_dedicated_server_smoke import SmokeError, digest_file, is_link_or_junction


MANAGED = ("celestial", "rocket_transactions", "rocket_transfers", "stations", "satellite_missions")
MAX_FILES = 20_000
MAX_WORLD_BYTES = 2 * 1024**3


def read_nbt(path: Path) -> dict:
    if is_link_or_junction(path) or not path.is_file() or not 0 < path.stat().st_size <= 1024**2:
        raise SmokeError(f"Fixture NBT is missing, linked or exceeds 1 MiB: {path.name}")
    with gzip.open(path, "rb") as stream:
        payload = stream.read(4 * 1024**2 + 1)
    if len(payload) > 4 * 1024**2:
        raise SmokeError("Fixture NBT exceeds 4 MiB expanded")
    data = NbtReader(payload).read_root()
    if not isinstance(data, dict) or data.get("DataVersion") != 3465:
        raise SmokeError("Fixture must retain Minecraft 1.20.1 DataVersion 3465")
    if not isinstance(data.get("data"), dict):
        raise SmokeError("Fixture NBT has no data compound")
    return data["data"]


def manifest(world: Path) -> list[dict]:
    if is_link_or_junction(world) or not world.is_dir():
        raise SmokeError("World fixture is missing or linked")
    result = []
    total = 0
    for path in sorted(world.rglob("*")):
        if is_link_or_junction(path):
            raise SmokeError("World fixture contains a linked path")
        if path.is_file():
            size = path.stat().st_size
            total += size
            if len(result) >= MAX_FILES or total > MAX_WORLD_BYTES:
                raise SmokeError("World fixture exceeds file-count/byte bounds")
            result.append({"file": path.relative_to(world).as_posix(), "bytes": size, "sha256": digest_file(path)})
    if not result or not (world / "level.dat").is_file():
        raise SmokeError("World fixture has no level.dat")
    return result


def read_world(world: Path) -> dict:
    result = {name: read_nbt(world / "data" / f"advancedrocketrycommunity_{name}.dat") for name in MANAGED}
    for name, data in result.items():
        if data.get("schema_version") != 2 or data.get("format_epoch") != "v0.9.0-beta":
            raise SmokeError(f"Managed root changed schema/epoch: {name}")
    storage = read_nbt(world / "data/command_storage_arce_v100_upgrade.dat")
    state = storage.get("contents", {}).get("snapshot", {}).get("state")
    if not isinstance(state, dict) or state.get("schema") != 1:
        raise SmokeError("World has no completed native state capture")
    result["observed"] = state
    return result


def nbt_uuid(value) -> str:
    if not isinstance(value, list) or len(value) != 4 or any(type(part) is not int or not -2**31 <= part < 2**31 for part in value):
        raise SmokeError("Invalid persisted UUID")
    return str(uuid.UUID(int=sum((part & 0xffffffff) << (96 - 32 * index) for index, part in enumerate(value))))


def indexed(records: list, key: str) -> dict:
    if not isinstance(records, list) or len(records) > 128:
        raise SmokeError("Fixture record list is missing or excessive")
    result = {}
    for record in records:
        if not isinstance(record, dict):
            raise SmokeError("Fixture record is not a compound")
        identity = nbt_uuid(record.get(key))
        if identity in result:
            raise SmokeError(f"Duplicate {key}: {identity}")
        result[identity] = record
    return result


def machine_ledger(machine: dict, *, running: bool) -> dict:
    if not isinstance(machine, dict) or machine.get("schema_version") != 1:
        raise SmokeError("Machine schema changed")
    inventory = machine.get("inventory", {})
    if inventory.get("Size") != 4 or not isinstance(inventory.get("Items"), list):
        raise SmokeError("Machine inventory shape changed")
    items = {}
    names = {0: "empty", 2: "hydrogen", 3: "oxygen"}
    for item in inventory["Items"]:
        slot = item.get("Slot")
        if slot not in names or slot in items or set(item) != {"Slot", "id", "Count"}:
            raise SmokeError("Machine inventory has duplicate/extra slots or fields")
        if item["id"] != f"advancedrocketrycommunity:{names[slot]}_canister" or not 1 <= item["Count"] <= 64:
            raise SmokeError("Machine inventory item or count changed")
        items[slot] = item["Count"]
    progress = machine.get("progress")
    energy = machine.get("energy")
    if type(progress) is not int or not 0 <= progress < 100 or type(energy) is not int:
        raise SmokeError("Machine progress or energy is invalid")
    completed = items.get(2, 0)
    input_count, water, initial_energy = (8, 4000, 20000) if running else (2, 1000, 800)
    if (items.get(3, 0) != completed or items.get(0, 0) != input_count - completed * 2
            or machine.get("fluid", {}).get("Amount") != water - completed * 1000
            or energy != initial_energy - 20 * (completed * 100 + progress)):
        raise SmokeError("Machine lost or duplicated material, energy or processing progress")
    if water - completed * 1000 > 0 and machine["fluid"].get("FluidName") != "minecraft:water":
        raise SmokeError("Machine fluid identity changed")
    if not running and (progress, energy, completed) != (40, 0, 0):
        raise SmokeError("Actual energy-paused recipe was reset or advanced")
    if progress > 0 and machine.get("active_recipe") != "advancedrocketrycommunity:electrolyzer_water":
        raise SmokeError("Mid-recipe identity was lost")
    return {"completed": completed, "progress": progress, "energy": energy,
            "processing_ticks": completed * 100 + progress, "items": items,
            "water": machine["fluid"]["Amount"]}


def vent_ledger(vent: dict) -> dict:
    required = {"schema_version", "oxygen_canisters", "empty_canisters", "oxygen_units", "energy", "oxygen_phase"}
    if set(vent) != required or vent["schema_version"] != 1 or vent["oxygen_canisters"] or vent["empty_canisters"]:
        raise SmokeError("Vent schema or canister inventory changed")
    oxygen, energy, phase = (vent[key] for key in ("oxygen_units", "energy", "oxygen_phase"))
    if any(type(value) is not int for value in (oxygen, energy, phase)) or not (0 < oxygen <= 4000 and 0 < energy <= 40000 and 0 <= phase < 20):
        raise SmokeError("Supplied vent has invalid or exhausted resources")
    ticks, remainder = divmod(40000 - energy, 20)
    if remainder:
        raise SmokeError("Vent energy is not a whole number of supply ticks")
    return {"oxygen": oxygen, "energy": energy, "phase": phase, "supply_ticks": ticks,
            "oxygen_accounted_ticks": (4000 - oxygen) * 20 + phase}


def rocket_projection(raw: dict) -> dict:
    required = ("UUID", "Pos", "RocketEntityData", "observed_dimension")
    if any(key not in raw for key in required):
        raise SmokeError("Captured rocket is missing identity, position or complete authority NBT")
    if raw.get("id") != "advancedrocketrycommunity:rocket" or raw.get("Passengers"):
        raise SmokeError("Fixture must contain unmanned ARCE rockets")
    return {key: raw[key] for key in required}


def compare_worlds(before: dict, after: dict) -> dict:
    a, b = before["observed"], after["observed"]
    if b["game_time"] < a["game_time"]:
        raise SmokeError("World game time moved backwards")
    if a["paused"] != b["paused"]:
        raise SmokeError("Energy-paused machine NBT changed across upgrade/restart")
    paused = machine_ledger(b["paused"], running=False)
    first, last = (machine_ledger(value["running"], running=True) for value in (a, b))
    if last["processing_ticks"] < first["processing_ticks"]:
        raise SmokeError("Running machine processing moved backwards")
    for value in (a, b):
        if value.get("vent_lit") != 1:
            raise SmokeError("Reloaded sealed oxygen room is not supplied")
    old_vent, new_vent = vent_ledger(a["vent"]), vent_ledger(b["vent"])
    if new_vent["supply_ticks"] < old_vent["supply_ticks"] or new_vent["oxygen"] > old_vent["oxygen"]:
        raise SmokeError("Vent resources increased without a supply operation")
    if new_vent["oxygen_accounted_ticks"] - old_vent["oxygen_accounted_ticks"] != new_vent["supply_ticks"] - old_vent["supply_ticks"]:
        raise SmokeError("Vent oxygen phase/energy consumption is not conserved across restart")
    rockets = [indexed(value["rockets"], "UUID") for value in (a, b)]
    if len(rockets[0]) != 2 or rockets[0].keys() != rockets[1].keys():
        raise SmokeError("Rocket entity identity/count changed across upgrade")
    for identity in rockets[0]:
        if rocket_projection(rockets[0][identity]) != rocket_projection(rockets[1][identity]):
            raise SmokeError(f"Rocket position, structure, inventory, ownership or flight data changed: {identity}")
    for name in ("celestial", "rocket_transactions", "rocket_transfers", "stations"):
        if before[name] != after[name]:
            raise SmokeError(f"Managed {name} state changed without an operation")
    old, new = before["satellite_missions"], after["satellite_missions"]
    for key in ("schema_version", "format_epoch", "satellites", "research_accounts"):
        if old[key] != new[key]:
            raise SmokeError(f"Satellite {key} changed without an operation")
    for key in ("logical_game_time", "last_observed_game_time"):
        if new["clock"][key] < old["clock"][key]:
            raise SmokeError("Satellite clock moved backwards")
    old_missions, new_missions = (indexed(value["missions"], "mission_id") for value in (old, new))
    if old_missions.keys() != new_missions.keys():
        raise SmokeError("Satellite mission IDs changed")
    transitions = []
    for identity, mission in old_missions.items():
        updated = new_missions[identity]
        if updated == mission:
            continue
        fields = set(mission) | set(updated)
        if (mission["status"] != "active" or updated["status"] != "ready"
                or updated.get("ready_at", -1) < mission["completes_at"]
                or updated.get("ready_at", 2**63) > new["clock"]["logical_game_time"]
                or any(mission.get(key) != updated.get(key) for key in fields - {"status", "ready_at"})):
            raise SmokeError(f"Satellite mission changed outside its due-time transition: {identity}")
        transitions.append(identity)
    return {"paused_machine": paused, "running_machine": last,
            "additional_processing_ticks": last["processing_ticks"] - first["processing_ticks"],
            "vent": new_vent, "rocket_ids": sorted(rockets[0]), "missions_became_ready": transitions}
