"""W336 supplements W176's unchanged failed whole-world downgrade oracle."""
from __future__ import annotations

import loader_cut_nbt as nbt
import run_v180_fuel_loader_s1 as w176
from w336_native import NS, require
from w336_saturation_oracle import DEVICES, raw_root

OLD_COMMIT = "ba046688b0df59ce89181475be8c9d237ea33a26"
OLD_SHA256 = "d9bf5f08cd65145090a23ac3ce577da5a0d14d51ba380eb253926fb3b0a038bd"
STORES = ("advancedrocketrycommunity_bay_receipts.dat", "advancedrocketrycommunity_resource_escrow.dat",
          "advancedrocketrycommunity_rocket_transfers.dat")
PLACED = [(128 + i * 16, 80, 128) for i in range(5)]
KINDS = ("bay", "item-load", "item-unload", "fluid-load", "fluid-unload")


def placed_command(at, kind, index):
    identity, key = DEVICES[kind]
    root = 'schema_version:1,owner:[I;0,336,0,1]'
    if kind != "bay":
        root += f',device:[I;0,336,1,{index}],role:"' + ("UNLOAD" if "unload" in kind else "LOAD") + '"'
        root += ',buffer:{slots:[{},{},{},{}]}' if kind.startswith("item") else ',tank:{}'
        root += ',resource_escrow:{schema_version:1,resource_epoch:0L,markers:[]}'
    return f"setblock {' '.join(map(str, at))} {NS + identity}{{{key}:{{{root}}}}}"


def device_roots(world):
    rows = []
    for at, kind in zip(PLACED, KINDS):
        chunk, _ = nbt.chunk(world / "region", at[0] >> 4, at[2] >> 4)
        entries, index = nbt.block_entity(chunk, *at)
        identity, key = DEVICES[kind]
        require(nbt.compound(entries[index], "id").value == (NS + identity).encode("ascii"), "Placed device ID changed")
        root = nbt.compound(entries[index], key)
        require(nbt.compound(root, "schema_version").value == 1, "Placed device root is not schema 1")
        rows.append(dict(at=at, kind=kind, root_sha256=raw_root(root)))
    return rows


def registry_ids(file, registry):
    root = nbt.read_gzip(file)[1]
    entries = nbt.compound(root, "fml", "Registries", registry, "ids").value[1]
    result = [nbt.compound(entry, "K").value.decode("utf-8") for entry in entries]
    require(len(result) == len(set(result)), "Duplicate registry ID")
    return set(result)


def compare(before, after, before_level, after_level):
    original = w176.refusal_storage_result(before, after)
    removed = {registry: sorted(registry_ids(before_level, registry) - registry_ids(after_level, registry))
               for registry in ("minecraft:block", "minecraft:item")}
    expected = {NS + identity for identity, _ in DEVICES.values() if identity != "fluid_fuel_loader"}
    require(expected <= set(removed["minecraft:block"]), "Downgrade did not remove the placed bay/cargo registry entries")
    # These observations never turn W176's FAILED whole-world result into PASS.
    require(set(original["changed_world_files"]) == {"level.dat", "level.dat_old"},
            "Downgrade changed something other than the observed metadata pair")
    for name in STORES:
        key = next((k for k in before if k.replace("\\", "/") == "data/" + name), None)
        require(key is not None and before[key] == after.get(key), "Protected store absent or modified: " + name)
    return dict(w176_oracle=original, removed_registry_entries=removed,
                protected_stores="BYTE_IDENTICAL", terrain_and_device_roots="BYTE_IDENTICAL")
