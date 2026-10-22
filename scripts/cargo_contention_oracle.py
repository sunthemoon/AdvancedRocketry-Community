"""Two independent real-cargo components; STALLED work may not block the healthy transfer."""
from pathlib import Path
import cargo_storage_oracle as storage
import loader_cut_nbt as nbt
import run_v180_cargo_reconciliation as native

require = storage.require


def healthy(world: Path):
    return dict(terrain=nbt.plain(nbt.chunk(world / "region", 5, 5)[0]),
                entities=nbt.plain(nbt.chunk(world / "entities", 5, 5)[0]))


def inspect(world: Path, restarted: bool) -> dict:
    raw = storage.read(world); control = healthy(world); root = raw["ledger"]
    require(root["issued_through"] == 2 and not root["floors"] and not root["tombstones"] and not raw["journal"]["transfers"],
            "E5 changed issued authority, floors or journal")
    require(not root["rocket_authorities"] and not root["armed_flights"] and root["issued_launch_serial"] == 0, "E5 acquired flight authority")
    require(len(root["records"]) == (0 if restarted else 1), "E5 did not isolate/retire its held component")
    op = None if restarted else root["records"][0]
    if op:
        require(op["phase"] == "STALLED" and op["reason"] == "BARRIER_IO" and op["seq"] in (1, 2), "E5 lost its exact stalled refusal")
    identities, states = {}, {}
    for name, data, pos in (("held", raw, [64, 100, 64]), ("healthy", control, [88, 100, 88])):
        rockets = [e for e in data["entities"]["Entities"] if "RocketEntityData" in e]
        devices = [e for e in data["terrain"]["block_entities"] if [e.get(k) for k in ("x", "y", "z")] == pos]
        require(len(rockets) == len(devices) == 1, "E5 endpoint missing or duplicated")
        entity = rockets[0]; rocket = entity["RocketEntityData"]; device = devices[0]["arce_rocket_fluid_cargo"]
        require(device["owner"] == rocket["owner_id"] and device["binding"]["logical_rocket"] == rocket["assembly_transaction_id"]
                and device["binding"]["physical_rocket"] == entity["UUID"] and entity["Pos"] == [pos[0] + 4.5, 100.0, pos[2] + 0.5],
                "E5 changed endpoint identity/binding")
        blocks = [b["block_entity"] for b in rocket["snapshot"]["relative_blocks"] if "block_entity" in b]
        require(len(blocks) == 1 and blocks[0]["adapter"] == "advancedrocketrycommunity:pressurized_tank_v1", "E5 changed cargo adapter")
        fluid = blocks[0]["data"]["tank"]["fluid"]
        require(fluid == dict(FluidName="minecraft:water", Amount=1000) and device["tank"] == dict(FluidName="minecraft:water", Amount=4000),
                "E5 repeated or changed a transfer")
        for section, role in ((rocket["resource_escrow"], "DESTINATION"), (device["resource_escrow"], "SOURCE")):
            require(section["resource_epoch"] == 1 and section["ledger_id"] == root["ledger_id"], "E5 changed epoch or ledger binding")
            markers = section["markers"]
            if name == "held" and not restarted:
                require(markers == [dict(op_id=op["op_id"], seq=op["seq"], role=role, epoch_after=1)], "E5 lost its exact held marker")
            else:
                require(not markers, "E5 retired component retained a marker")
        identities[name] = dict(physical=entity["UUID"], logical=rocket["assembly_transaction_id"], device=device["device"],
                                owner=rocket["owner_id"], snapshot=rocket["snapshot"]["snapshot_id"])
        states[name] = dict(rocket=rocket, device=device, epochs=[1, 1], amounts=dict(rocket=1000, device=4000), raw_total=5000)
    return dict(ledger=root, identity=identities, components=states, raw_total=10000)
