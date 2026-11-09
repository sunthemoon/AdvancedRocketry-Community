"""W336 supplements W176's unchanged failed whole-world downgrade oracle."""
from __future__ import annotations

import loader_cut_nbt as nbt
import run_v180_fuel_loader_s1 as w176
from w336_native import NS, require
from w336_saturation_oracle import DEVICES

OLD_COMMIT = "ba046688b0df59ce89181475be8c9d237ea33a26"
OLD_SHA256 = "d9bf5f08cd65145090a23ac3ce577da5a0d14d51ba380eb253926fb3b0a038bd"
STORES = ("advancedrocketrycommunity_bay_receipts.dat", "advancedrocketrycommunity_resource_escrow.dat",
          "advancedrocketrycommunity_rocket_transfers.dat")


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
