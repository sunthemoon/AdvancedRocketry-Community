"""Typed, stopped-world oracles for W336's shared Level save-refusal pool."""
from __future__ import annotations

import hashlib
import re
import struct

import cargo_s1_oracle
import loader_cut_nbt as nbt
from w336_native import NS, require

DEVICES = {
    "bay": ("rocket_satellite_bay", "arce_satellite_bay"),
    "item-load": ("rocket_item_loader", "arce_rocket_item_cargo"),
    "item-unload": ("rocket_item_unloader", "arce_rocket_item_cargo"),
    "fluid-load": ("rocket_fluid_loader", "arce_rocket_fluid_cargo"),
    "fluid-unload": ("rocket_fluid_unloader", "arce_rocket_fluid_cargo"),
    "fuel": ("fluid_fuel_loader", "arce_fluid_fuel_loader"),
}
BACKGROUND = [(8192 + (i % 16) * 16, 80, 8192 + (i // 16) * 16) for i in range(255)]
TARGET = (8704, 80, 8192)
# Stay outside the target's load/ticking halo: a nearby root would enter the pool early.
OVERFLOW = (9216, 80, 8192)
TERRAIN = (1024, 80, 1024)


def generated_chunks(world):
    """Closed allowlist from the stopped seed, including vanilla's generated load halos."""
    result = set()
    files = list((world / "region").glob("r.*.*.mca"))
    require(len(files) <= 128, "Unexpectedly large saturation fixture")
    for path in files:
        match = re.fullmatch(r"r\.(-?\d+)\.(-?\d+)\.mca", path.name)
        require(match is not None, "Invalid fixture region name")
        rx, rz = map(int, match.groups())
        with path.open("rb") as stream:
            header = stream.read(4096)
        require(len(header) == 4096, "Truncated fixture region header")
        for slot in range(1024):
            if header[slot * 4:slot * 4 + 4] != b"\x00" * 4:
                result.add((rx * 32 + slot % 32, rz * 32 + slot // 32))
    require(1 <= len(result) <= 16384, "Unexpected fixture chunk count")
    return result


def raw_root(root):
    return hashlib.sha256(nbt.write(b"", root)).hexdigest()


def unsupported():
    # A noncanonical quiet NaN survives the Python raw reader but not Java's native writer.
    return nbt.Tag(nbt.COMPOUND, {"schema_version": nbt.Tag(nbt.INT, 99),
                                  "opaque": nbt.Tag(nbt.FLOAT, struct.unpack(">f", bytes.fromhex("7fc00001"))[0])})


def inject(world, positions, kind):
    identity, key = DEVICES[kind]
    changed = []
    for at in positions:
        chunk, _ = nbt.chunk(world / "region", at[0] >> 4, at[2] >> 4)
        entries, index = nbt.block_entity(chunk, *at)
        require(nbt.compound(entries[index], "id").value == (NS + identity).encode("ascii"),
                "Unexpected device identity in offline fixture")
        entries[index].value[key] = unsupported()
        nbt.write_chunk(world / "region", at[0] >> 4, at[2] >> 4, chunk)
        check, _ = nbt.chunk(world / "region", at[0] >> 4, at[2] >> 4)
        rows, row = nbt.block_entity(check, *at)
        actual = nbt.compound(rows[row], key)
        require(nbt.write(b"", actual) == nbt.write(b"", unsupported()), "Offline NaN payload changed")
        changed.append(dict(at=at, kind=kind, root_sha256=raw_root(actual)))
    return changed


def terrain(world):
    chunk, _ = nbt.chunk(world / "region", TERRAIN[0] >> 4, TERRAIN[2] >> 4)
    require(not nbt.compound(chunk, "block_entities").value[1], "Terrain control chunk contains a device")
    return cargo_s1_oracle.block_at(nbt.plain(chunk), TERRAIN)["Name"]


def check_roots(world, fixtures):
    for row in fixtures:
        at = row["at"]
        chunk, _ = nbt.chunk(world / "region", at[0] >> 4, at[2] >> 4)
        entries, index = nbt.block_entity(chunk, *at)
        identity, key = DEVICES[row["kind"]]
        require(nbt.compound(entries[index], "id").value == (NS + identity).encode("ascii"), "Device identity changed")
        require(raw_root(nbt.compound(entries[index], key)) == row["root_sha256"], "Unsupported device root changed")


def check_stage(world, fixtures, expected):
    check_roots(world, fixtures)
    actual = terrain(world)
    require(actual == expected, f"Device-free terrain persisted {actual}; expected {expected}")
    return dict(device_roots=len(fixtures), terrain=actual)


def check_boundary(observed):
    require(observed == {255: "minecraft:gold_block", 256: "minecraft:diamond_block", 257: "minecraft:diamond_block"},
            "Shared denial boundary did not preserve 255/256 saves and refuse 257th overflow")
    return dict(retained_capacity=256, saturation_denial=257, shared_level=True)
