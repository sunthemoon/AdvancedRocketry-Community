"""Native C-S3 oracle: exact retained roots/prefixes, composite floors and both stale-record entities."""
import hashlib
from pathlib import Path
import cargo_storage_oracle as storage
import loader_cut_nbt as nbt
import run_v180_cargo_reconciliation as native

require = storage.require


def floors(root, count):
    require(len(root["floors"]) == count, "R1 changed its seeded floor count")
    indices = set()
    for floor in root["floors"]:
        i = floor["entity_id"][-1]; indices.add(i)
        expected = dict(rocket_id=[0, 0x107C0, 0, i], entity_id=[0, 0x107C1, 0, i], schema_version=2,
                        resource_epoch=1, highest_seq=i + 1, signature_kind="ROCKET_COMPOSITE_V1", journal_generation=1,
                        **{key: hashlib.sha256(f"w107c-floor-{value}-{i}".encode()).hexdigest()
                           for key, value in (("signature", "signature"), ("journal_digest", "journal"), ("invariant_digest", "invariant"))})
        require(floor == expected, "R1 changed a seeded composite floor")
    require(indices == set(range(count)), "R1 duplicated a floor")


def inspect(world: Path, case: str, restarted: bool) -> dict:
    raw = storage.read(world); root = raw["ledger"]; cut = case[8:]
    moved = cut == "R3_RELOCATED" and restarted
    position = [72 if moved else 64, 100, 64]
    devices = [e for e in raw["terrain"]["block_entities"] if [e.get(k) for k in ("x", "y", "z")] == position]
    require(len(devices) == 1, "Recovery device missing or duplicated")
    device = devices[0]["arce_rocket_fluid_cargo"]
    rockets = [e for e in raw["entities"]["Entities"] if "RocketEntityData" in e]
    require(len(rockets) == (2 if cut == "R2_DUPLICATE" else 1), "Recovery rocket missing or duplicated")
    matches = [e for e in rockets if e["UUID"] == device["binding"]["physical_rocket"]]
    require(len(matches) == 1, "Recovery binding lost its exact physical rocket")
    entity = matches[0]; rocket = entity["RocketEntityData"]
    require(device["owner"] == rocket["owner_id"] and device["binding"]["logical_rocket"] == rocket["assembly_transaction_id"]
            and entity["Pos"] == [68.5, 100.0, 64.5] and device["role"] == "LOAD", "Recovery changed identity, role or placement")
    blocks = [b for b in rocket["snapshot"]["relative_blocks"] if "block_entity" in b]
    require(len(blocks) == 1 and blocks[0]["block_entity"]["adapter"] == "advancedrocketrycommunity:pressurized_tank_v1", "Recovery changed its cargo adapter")
    fluid = blocks[0]["block_entity"]["data"]["tank"]["fluid"]
    amount, cargo = device["tank"].get("Amount", 0), fluid.get("Amount", 0)
    epochs = [rocket["resource_escrow"]["resource_epoch"], device["resource_escrow"]["resource_epoch"]]
    after = cut in ("R2_DUPLICATE", "R3_RELOCATED")
    require(epochs == ([1, 1] if after else [0, 0]) and cargo == (1000 if after else 0), "Recovery changed native epochs or cargo")
    require(not fluid or fluid == dict(FluidName="minecraft:water", Amount=1000), "Recovery changed cargo fluid identity")
    require(not root["tombstones"] and not root["rocket_authorities"] and not root["armed_flights"] and root["issued_launch_serial"] == 0,
            "Recovery acquired settlement/flight authority")
    transfers = raw["journal"]["transfers"]
    op = root["records"][0] if root["records"] else None
    preserved = dict(device=device, snapshot=rocket["snapshot"], wrapper=rocket["resource_escrow"])
    if cut.startswith("R1_"):
        count = int(cut[3:]); floors(root, count); pending = int(count == 255)
        require(root["issued_through"] == count + pending and len(root["records"]) == pending
                and len(transfers) == 1 and transfers[0]["phase"] == "COMMITTED"
                and transfers[0]["destination_entity_id"] == entity["UUID"], "R1 exceeded/refused the wrong floor reservation")
        if op:
            require(op["seq"] == 256 and op["phase"] == "PREPARED" and op["transfer_guard"]["transfer_id"] == transfers[0]["transfer_id"],
                    "R1 lost its named held admission")
    else:
        require(not root["floors"], "Unexpected recovery floor")
    if cut == "R2_DUPLICATE":
        require(not op and root["issued_through"] == 1 and len(transfers) == 1 and transfers[0]["phase"] == "PREPARED",
                "R2 retired its stale journal or repeated cargo")
        other = next(e for e in rockets if e is not entity); duplicate = other["RocketEntityData"]
        require(duplicate["assembly_transaction_id"] == rocket["assembly_transaction_id"] and duplicate["owner_id"] == rocket["owner_id"]
                and duplicate["snapshot"] == transfers[0]["destination_snapshot"] and duplicate["resource_escrow"]["resource_epoch"] == 0
                and not duplicate["resource_escrow"]["markers"] and transfers[0]["source_entity_id"] == entity["UUID"],
                "R2 replaced either native entity from history")
        preserved.update(duplicate=dict(physical=other["UUID"], snapshot=duplicate["snapshot"], wrapper=duplicate["resource_escrow"]), journal=transfers)
    elif cut.startswith("R3_"):
        require(len(root["records"]) == 1 and root["issued_through"] == 1 and op["seq"] == 1 and not transfers
                and op["phase"] == ("REPAIR" if restarted else "PREPARED"), "R3 failed to retain its held repair")
        if restarted:
            require(op["reason"] not in ("NONE", "BARRIER_IO", "SUPERSEDING_SAVE"), "R3 lost its manual refusal")
        if cut == "R3_CURSOR":
            require(blocks[0]["position"] == [0, 1, 0] and op["payload"]["cursor"]["p"] == [0, 0, 0], "R3 cursor absence was repaired from history")
        if moved:
            require(not any([e.get(k) for k in ("x", "y", "z")] == [64, 100, 64] for e in raw["terrain"]["block_entities"]), "R3 duplicated its moved device")
    elif cut.startswith("G1_"):
        require(not op and root["issued_through"] == 0 and not transfers, "G1 admitted unsupported cargo")
        require(device["schema_version"] == (2 if cut == "G1_FUTURE" else 1), "G1 rewrote its unsupported schema")
        require(device.get("extension") == "W138-preserve" if cut == "G1_FUTURE" else amount == -1, "G1 rewrote its held payload")
    expected = -1 if cut == "G1_INVALID" else 4000 if after else 5000
    require(amount == expected and device["tank"].get("FluidName") == ("minecraft:lava" if cut == "R3_OTHER" else "minecraft:water"),
            "Recovery overwrote its native device payload")
    for section, role in ((rocket["resource_escrow"], "DESTINATION"), (device["resource_escrow"], "SOURCE")):
        if cut == "R3_RELOCATED":
            require(section["markers"] == [dict(op_id=op["op_id"], seq=1, role=role, epoch_after=1)] and section["ledger_id"] == root["ledger_id"],
                    "R3 relocated endpoint lost its exact held marker")
        else:
            require(not section["markers"], "Recovery retained an unexpected marker")
    return dict(ledger=root, journal=raw["journal"], rocket=rocket, device=device, epochs=epochs, amounts=dict(rocket=cargo, device=amount),
                raw_total=cargo + amount, preserved=preserved,
                identity=dict(physical=entity["UUID"], logical=rocket["assembly_transaction_id"], owner=rocket["owner_id"],
                              snapshot=rocket["snapshot"]["snapshot_id"], device=device["device"]))


def relocate(world: Path, keep: Path) -> dict:
    """Exactly one stopped, token-owned disposable fixture; originals and typed readback are retained."""
    require(not keep.exists(), "Relocation evidence already exists"); keep.mkdir()
    directory = world / "region"; path = directory / "r.0.0.mca"
    before = path.read_bytes(); (keep / path.name).write_bytes(before)
    root, info = nbt.chunk(directory, 4, 4)
    require(not info["external"], "Relocation fixture must be inline")
    at, target = (64, 100, 64), (72, 100, 64)
    entries, index = nbt.block_entity(root, *at); kind = "advancedrocketrycommunity:rocket_fluid_loader"
    require(nbt.block(root, *at) == kind and nbt.block(root, *target) == "minecraft:air", "Relocation fixture occupancy changed")
    require(nbt.plain(nbt.compound(entries[index], "id")) == kind, "Relocation selected another block entity")
    nbt.set_block(root, *target, kind); nbt.set_block(root, *at, "minecraft:air")
    nbt.compound(entries[index], "x").value = 72
    nbt.write_chunk(directory, 4, 4, root)
    require(nbt.chunk(directory, 4, 4)[0] == root, "Relocation typed readback differs")
    return dict(file="region/r.0.0.mca", change="Same block/root moved from 64,100,64 to 72,100,64",
                before_sha256=nbt.digest(before), after_sha256=nbt.digest(path.read_bytes()))
