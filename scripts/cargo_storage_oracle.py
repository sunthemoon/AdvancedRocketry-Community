"""Independent bounded native reads for W138 storage cuts, including external rocket records."""
from __future__ import annotations

import hashlib
from pathlib import Path

import loader_cut_nbt as nbt
import run_v180_cargo_reconciliation as native


def require(ok, message):
    if not ok:
        raise native.smoke.SmokeError(message)


def read(world: Path) -> dict:
    terrain, terrain_info = nbt.chunk(world / "region", 4, 4)
    entities, entity_info = nbt.chunk(world / "entities", 4, 4)
    raw = dict(ledger=native.ledger(world / "data" / native.LEDGER), terrain=nbt.plain(terrain),
               entities=nbt.plain(entities), journal=native.journal(world),
               records=dict(terrain=terrain_info, entities=entity_info))
    # Keep all cargo/identity data. Fixture-only noise is recorded by exact length and hash, not a million JSON integers.
    for entity in raw["entities"]["Entities"]:
        forge = entity.get("ForgeData", {})
        if "arce_w138_noise" in forge:
            noise = bytes(value & 255 for value in forge["arce_w138_noise"])
            forge["arce_w138_noise"] = dict(bytes=len(noise), sha256=hashlib.sha256(noise).hexdigest())
    return raw


def inspect(world: Path, case: str, restarted: bool) -> dict:
    raw = read(world)
    root = raw["ledger"]
    entities = [e for e in raw["entities"]["Entities"] if "RocketEntityData" in e]
    devices = [e for e in raw["terrain"]["block_entities"] if [e.get(k) for k in ("x", "y", "z")] == [64, 100, 64]]
    require(len(entities) == len(devices) == 1 and not raw["journal"]["transfers"], "Storage fixture identity count/journal changed")
    entity, device = entities[0], devices[0]["arce_rocket_fluid_cargo"]
    rocket = entity["RocketEntityData"]; snapshot = rocket["snapshot"]
    blocks = [b["block_entity"] for b in snapshot["relative_blocks"] if "block_entity" in b]
    require(len(blocks) == 1 and blocks[0]["adapter"] == "advancedrocketrycommunity:pressurized_tank_v1", "Storage fixture adapter changed")
    require(device["owner"] == rocket["owner_id"] and device["binding"]["logical_rocket"] == rocket["assembly_transaction_id"]
            and device["binding"]["physical_rocket"] == entity["UUID"] and entity["Pos"] == [68.5, 100.0, 64.5]
            and not rocket["flight_data"]["passengers"]["assignments"] and not root["floors"] and not root["tombstones"],
            "Storage fixture identity, placement, passengers or ordinary authority changed")
    amount, cargo = device["tank"].get("Amount", 0), blocks[0]["data"]["tank"]["fluid"].get("Amount", 0)
    epochs = [rocket["resource_escrow"]["resource_epoch"], device["resource_escrow"]["resource_epoch"]]
    require(all(e in (0, 1) for e in epochs) and cargo == epochs[0] * 1000 and amount == 5000 - epochs[1] * 1000,
            "Storage quantities do not match independently saved epochs")
    external = case == "FC_LOAD_E2_ROCKET_EXTERNAL"
    require(root["issued_through"] == 1, "Storage failure lost/repeated its issued operation")
    if restarted and not external:
        require(not root["records"] and epochs == [1, 1] and cargo + amount == 5000, "Storage restart failed exact retirement")
    else:
        require(len(root["records"]) == 1 and root["records"][0]["phase"] == "PREPARED", "Failed proof lost durable L1")
        if external:
            require(epochs[0] == 1 and raw["records"]["entities"]["external"], "Rocket near-limit fixture did not cross to external AFTER storage")
            noise = entity.get("ForgeData", {}).get("arce_w138_noise", {})
            require(noise.get("bytes") == 1_048_576 and len(noise.get("sha256", "")) == 64, "External rocket noise was lost")
        elif case == "FC_LOAD_E3_CACHE_CLOSE":
            require(epochs[1] == 0, "Cache eviction failure unexpectedly stored its device")
        else:
            require(epochs[1] == 1, "Failed terrain store/newer save did not persist the device AFTER state")
    return dict(ledger=root, device=device, rocket=rocket, raw_total=cargo + amount, epochs=epochs,
                amounts=dict(rocket=cargo, device=amount), storage=raw["records"],
                identity=dict(physical=entity["UUID"], logical=rocket["assembly_transaction_id"], owner=rocket["owner_id"],
                              snapshot=snapshot["snapshot_id"], device=device["device"]),
                files={str(p.relative_to(world)): native.digest(p) for p in [world / "data" / native.LEDGER,
                       world / "region/r.0.0.mca", world / "entities/r.0.0.mca", *sorted((world / "entities").glob("*.mcc"))]})
