#!/usr/bin/env python3
"""Copy-only Signature fixture proposal: old partials, labelled injected cuts, clean restarts.

Requires a freshly packaged opt-in Signature hook, not the candidate11 baseline JAR.
Injected PREPARED fixtures are NOT naturally captured old callbacks or crash evidence.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
import math
import re
import shutil
import struct
import time
import uuid
import zipfile
import zlib
from pathlib import Path

if __package__:
    from . import run_dedicated_server_smoke as server
    from . import run_v050_rocket_server_smoke as rocket
    from . import run_v180_motor_smoke as motors
    from . import v120_precision_world_fixture as regions
    from . import v140_migration_fixture as worlds
else:
    import run_dedicated_server_smoke as server
    import run_v050_rocket_server_smoke as rocket
    import run_v180_motor_smoke as motors
    import v120_precision_world_fixture as regions
    import v140_migration_fixture as worlds

NS = "advancedrocketrycommunity:"
CHUNK = (15, 15)
CASES = ("legacy_partial", "legacy_pending", "current_partial", "current_pending",
         "future_process", "future_journal", "opaque_marker")
MACHINES = ("rolling", "precision", "electrolyzer")
BLOCK_IDS = (NS + "rolling_machine", NS + "precision_assembler", NS + "electrolyzer")
RECIPES = ("rolling_iron_bars", "precision_control_circuit", "electrolyzer_water")
LEGACY_HASHES = ("575101496cbaf0038049fe2d373a662a05016d570cc54d0f7a6f2db026cb59cc",
                 "ea5522eb8e75090c8afc4b7891829648a248f380781897b7c3ea1fdfb2b6e5a9",
                 "5eacb5d39d636c9045ea0e1e198e41e89d411dc2cc8f13c9994466cdfb53d152")
OLD_SHA = "4326d3d67edf417f4073af86329508fbfa19f8b9dc4d57548e82331a9ac13bbe"
BASELINE_SHA = "ea311ed02b4d1531e902e4031ad5e1174466e6378d3a6cb15e6679ca039e1e2a"
OLD_JSON_SHAS = ("55b57f538d24d749f622588f0748ed2fa02984515dc5865413b26e175e098e73",
                 "1c52a7e168a04894ad31a84377f9614a3302eaa93f432af5b4de9061e3f8ab3f",
                 "b3cc933186fbeb1238939ed02a203dee03e3db18817584ed1f28c8de6521c415")
PROCESS, JOURNAL, MARKER, LEGACY = "arce_process", "arce_process_journal", "arce_recipe_signature", "arce_machine"
PROCESS_TYPES = {"schema_version": 3, "state": 8, "resource_revision": 4, "definition_id": 8,
                 "recipe_signature": 8, "progress_ticks": 3, "consumed_energy": 4,
                 "last_applied_transaction": 8, "failure_code": 8, "failure_subject": 8}
CONSOLE = r"^(?:\[\d{2}:\d{2}:\d{2}\] )?\[Server thread/INFO\] \[minecraft/MinecraftServer\]: "
SAY = CONSOLE + r"(?:\[Not Secure\] )?\[Server\] "
ROW = re.compile(CONSOLE + r"ARCE_SIGNATURE_REPORT (\{[^\r\n]*\})[ \t]*$")
END = re.compile(CONSOLE + r"ARCE_SIGNATURE_REPORT_END machine=(rolling|precision|electrolyzer)[ \t]*$")
FULL = re.compile(SAY + r"ARCE_SIGNATURE_FULL_LOADED[ \t]*$")
STOP = re.compile(CONSOLE + r"Stopping server[ \t]*$")
SAVED = re.compile(CONSOLE + r"ThreadedAnvilChunkStorage: All dimensions are saved[ \t]*$")
MAX_NODES, MAX_DEPTH, MAX_COLLECTION = 4096, 16, 4096
WAIT_SECONDS, MAX_PROBES = 60, 240


def require(value: bool, message: str) -> None:
    if not value:
        raise server.SmokeError(message)


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def write(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def position(row: int, machine: int) -> tuple[int, int, int]:
    require(type(row) is int and 0 <= row < 7 and type(machine) is int and 0 <= machine < 3, "Unknown fixed cell")
    return (243, 250, 254)[machine], 128 + 16 * row, 242


def offset(pos, dx=0, dy=0, dz=0):
    return pos[0] + dx, pos[1] + dy, pos[2] + dz


def port_positions(row: int, machine: int) -> tuple:
    pos = position(row, machine)
    if machine == 0:
        return tuple(offset(pos, x) for x in (-2, -1, 1, 2))
    if machine == 1:
        return tuple(offset(pos, x, 0, z) for x, z in ((-1, 0), (1, 0), (-1, 1), (1, 1),
                                                      (-1, 2), (1, 2), (-1, 3), (1, 3)))
    return ()


def footprint(row: int, machine: int) -> set:
    pos = position(row, machine)
    if machine == 0:
        cells = {offset(pos, x, y, z) for x in range(-2, 3) for y in range(3) for z in range(2)}
    elif machine == 1:
        cells = {offset(pos, x, y, z) for x in range(-1, 2) for y in range(3) for z in range(4)}
    else:
        cells = {pos}
    return cells | {offset(pos, 0, 0, -1)}


class TypedReader:
    """Lossless scalar widths/list subtypes, with the existing 4096-node fixture bound."""
    def __init__(self, data: bytes):
        require(len(data) <= regions.MAX_EXPANDED_CHUNK, "Oversized expanded native chunk")
        self.data, self.pos, self.nodes = data, 0, 0

    def take(self, count):
        require(0 <= count <= len(self.data) - self.pos, "Truncated native NBT")
        result = self.data[self.pos:self.pos + count]; self.pos += count
        return result

    def number(self, format):
        return struct.unpack(format, self.take(struct.calcsize(format)))[0]

    def text(self):
        length = self.number(">H"); require(length <= 4096, "Overlong NBT string")
        try:
            return self.take(length).decode("utf-8", "strict")
        except UnicodeError as error:
            raise server.SmokeError("Invalid NBT UTF-8") from error

    def length(self):
        count = self.number(">i"); require(0 <= count <= MAX_COLLECTION, "Overlong NBT collection")
        return count

    def payload(self, kind, depth=0):
        self.nodes += 1
        require(depth <= MAX_DEPTH and self.nodes <= MAX_NODES, "Native NBT depth/node bound exceeded")
        if kind in (1, 2, 3, 4, 5, 6):
            result = self.number({1: ">b", 2: ">h", 3: ">i", 4: ">q", 5: ">f", 6: ">d"}[kind])
            require(kind not in (5, 6) or math.isfinite(result), "Non-finite native scalar")
        elif kind == 7:
            result = self.take(self.length())
        elif kind == 8:
            result = self.text()
        elif kind == 9:
            subtype, count = self.number(">B"), self.length()
            require(subtype in range(1, 13) or subtype == 0 and count == 0, "Invalid native list subtype")
            result = subtype, [self.payload(subtype, depth + 1) for _ in range(count)]
        elif kind == 10:
            result = {}
            while True:
                subtype = self.number(">B")
                if subtype == 0: break
                name = self.text(); require(name not in result, "Duplicate native compound key")
                result[name] = self.payload(subtype, depth + 1)
        elif kind in (11, 12):
            result = [self.number(">i" if kind == 11 else ">q") for _ in range(self.length())]
        else:
            raise server.SmokeError("Unknown native tag kind")
        return kind, result

    def root(self):
        require(self.number(">B") == 10 and self.text() == "", "Unexpected native root name/type")
        result = self.payload(10)
        require(self.pos == len(self.data), "Native NBT trailing bytes")
        return result


def encode(root) -> bytes:
    nodes = 0
    def text(value):
        raw = value.encode("utf-8"); require(len(raw) <= 4096, "Overlong encoded NBT string")
        return struct.pack(">H", len(raw)) + raw
    def payload(tag, depth=0):
        nonlocal nodes
        nodes += 1; require(depth <= MAX_DEPTH and nodes <= MAX_NODES, "Encoded NBT structure bound")
        kind, value = tag
        if kind in (1, 2, 3, 4, 5, 6):
            require(kind not in (5, 6) or math.isfinite(value), "Non-finite encoded scalar")
            return struct.pack({1: ">b", 2: ">h", 3: ">i", 4: ">q", 5: ">f", 6: ">d"}[kind], value)
        if kind == 8: return text(value)
        if kind == 10:
            return b"".join(bytes([child[0]]) + text(name) + payload(child, depth + 1)
                            for name, child in value.items()) + b"\0"
        if kind == 9:
            subtype, items = value; require(len(items) <= MAX_COLLECTION, "Overlong encoded list")
            require(subtype in range(1, 13) or subtype == 0 and not items, "Invalid encoded list subtype")
            require(all(child[0] == subtype for child in items), "Mixed native list types")
            return bytes([subtype]) + struct.pack(">i", len(items)) + b"".join(payload(child, depth + 1) for child in items)
        require(kind in (7, 11, 12) and len(value) <= MAX_COLLECTION, "Invalid encoded array")
        raw = bytes(value) if kind == 7 else b"".join(struct.pack(">i" if kind == 11 else ">q", item) for item in value)
        return struct.pack(">i", len(value)) + raw
    require(root[0] == 10, "Root must be native compound")
    result = b"\x0a\0\0" + payload(root)
    require(len(result) <= regions.MAX_EXPANDED_CHUNK, "Encoded chunk exceeds byte bound")
    return result


def decode(compressed: bytes):
    require(len(compressed) <= regions.MAX_COMPRESSED_CHUNK, "Compressed native chunk bound")
    stream = zlib.decompressobj(); raw = stream.decompress(compressed, regions.MAX_EXPANDED_CHUNK + 1)
    require(len(raw) <= regions.MAX_EXPANDED_CHUNK and stream.eof and not stream.unused_data
            and not stream.unconsumed_tail, "Invalid/overlong compressed native chunk")
    reader = TypedReader(raw); root = reader.root()
    require(root[1].get("xPos") == (3, 15) and root[1].get("zPos") == (3, 15), "Wrong fixed chunk identity")
    return root, reader.nodes


def plain(tag):
    kind, value = tag
    if kind == 10: return {key: plain(child) for key, child in value.items()}
    if kind == 9: return [plain(child) for child in value[1]]
    if kind == 7: return list(value)
    return value


def block_at(root, pos):
    return motors.block_at(plain(root) | {"xPos": motors.CHUNK[0], "zPos": motors.CHUNK[1]},
                          (pos[0] + (motors.CHUNK[0] - 15) * 16, pos[1], pos[2] + (motors.CHUNK[1] - 15) * 16))


def carrier(root, pos, expected_id=None):
    entries = root[1].get("block_entities")
    require(entries is not None and entries[0] == 9 and entries[1][0] == 10, "Invalid native BE list")
    matches = [value for kind, value in entries[1][1] if kind == 10 and
               tuple(value.get(axis) for axis in ("x", "y", "z")) == tuple((3, item) for item in pos)]
    require(len(matches) == 1 and (expected_id is None or matches[0].get("id") == (8, expected_id)),
            "Missing/duplicate/wrong fixed BE identity")
    return matches[0]


def compound(tag):
    require(tag[0] == 10, "Expected native compound")
    return tag[1]


def scalar(parent, key, kind):
    require(key in parent and parent[key][0] == kind, "Wrong native field/tag width: " + key)
    return parent[key][1]


def item(tag, expected_id="", expected_count=0):
    value = compound(tag)
    if expected_count == 0:
        require(value == {}, "Expected empty native item; unexpected metadata")
    else:
        require(value == {"id": (8, expected_id), "Count": (1, expected_count)}, "Native item/count/metadata differs")


def fluid(tag, amount, canonical_empty=False):
    value = compound(tag)
    expected = {"FluidName": (8, "minecraft:empty"), "Amount": (3, 0)} if canonical_empty and amount == 0 else {} if amount == 0 else {"FluidName": (8, "minecraft:water"), "Amount": (3, amount)}
    require(value == expected,
            "Native water/Amount width/metadata differs")


def process_fields(parent):
    value = compound(parent[PROCESS]); require(set(value) == set(PROCESS_TYPES), "Process exact fields differ")
    require(all(value[key][0] == kind for key, kind in PROCESS_TYPES.items()), "Process scalar tag widths differ")
    return value


def saved_resources(root, row, machine, completed=False):
    controller = carrier(root, position(row, machine), BLOCK_IDS[machine])
    require(block_at(root, position(row, machine)).get("Name") == BLOCK_IDS[machine], "Wrong fixed controller block")
    if machine == 0:
        ports = [carrier(root, pos, NS + "rolling_machine_port") for pos in port_positions(row, machine)]
        types = ("item_input", "fluid_input", "energy_input", "item_output")
        roots = [compound(value["arce_rolling_port"]) for value in ports]
        for value, kind in zip(roots, types):
            require(value.get("schema_version") == (3, 1) and value.get("port_type") == (8, "rolling_machine_" + kind + "_port"), "Rolling native role/schema differs")
            require(set(value) == {"schema_version", "port_type", "fluid" if kind == "fluid_input" else "energy" if kind == "energy_input" else "item"}, "Rolling port fields differ")
        for pos, kind in zip(port_positions(row, machine), types):
            require(block_at(root, pos) == {"Name": NS + "rolling_machine_" + kind + "_port"}, "Rolling exact physical port role differs")
        item(roots[0]["item"], "minecraft:iron_ingot", 0 if completed else 2)
        fluid(roots[1]["fluid"], 400 if completed else 500)
        require(roots[2]["energy"] == (3, 0), "Rolling FE is not zero")
        item(roots[3]["item"], "minecraft:iron_bars", 8 if completed else 0)
        return [value["arce_rolling_port"] for value in ports]
    if machine == 1:
        resources = compound(controller["arce_precision_resources"])
        require(set(resources) == {"schema_version", "machine_id", "phase", "items"}
                and resources.get("schema_version") == (3, 1) and resources.get("phase") == (8, "active"), "Precision controller ownership/schema differs")
        require(resources.get("machine_id") == compound(controller["arce_multiblock"])["machine_instance_id"], "Precision owner mismatch")
        slots = resources["items"]; require(slots[0] == 9 and slots[1][0] == 10 and len(slots[1][1]) == 7, "Precision native slots differ")
        items = slots[1][1]
        for index in range(5):
            item(items[index], "minecraft:iron_ingot" if index == 0 else "minecraft:redstone", 2 if not completed and index < 2 else 0)
        item(items[5], NS + "advanced_circuit", 1 if completed else 0)
        item(items[6], "minecraft:redstone_torch", 2 if completed else 0)
        ports = [carrier(root, pos, NS + "precision_assembler_port") for pos in port_positions(row, machine)]
        for index, (pos, port) in enumerate(zip(port_positions(row, machine), ports)):
            role = "item_input" if index < 5 else "item_output" if index < 7 else "energy_input"
            data = compound(port["arce_precision_port"])
            require(data.get("schema_version") == (3, 1) and data.get("port_type") == (8, "precision_assembler_" + role + "_port")
                    and set(data) == {"schema_version", "port_type", "item" if index < 7 else "energy"}, "Precision physical port schema/role differs")
            require(block_at(root, pos) == {"Name": NS + "precision_assembler_" + role + "_port"}, "Precision exact physical port block differs")
            if index < 7: item(data["item"])
        require(compound(ports[-1]["arce_precision_port"]).get("energy") == (3, 0), "Precision FE is not zero")
        return [controller["arce_precision_resources"]] + [value["arce_precision_port"] for value in ports]
    resources = compound(controller[LEGACY]); require(resources.get("schema_version") == (3, 1), "Electrolyzer schema differs")
    require(resources.get("energy") == (3, 0), "Electrolyzer FE is not zero")
    inv = compound(resources["inventory"]); require(set(inv) == {"Size", "Items"} and inv.get("Size") == (3, 4), "Electrolyzer native inventory schema differs")
    children = inv["Items"]; require(children[0] == 9 and children[1][0] == 10, "Electrolyzer native item list differs")
    expected = [(2, NS + "hydrogen_canister", 1), (3, NS + "oxygen_canister", 1)] if completed else [(0, NS + "empty_canister", 2)]
    actual = []
    for child in children[1][1]:
        data = compound(child); slot = scalar(data, "Slot", 3)
        require(set(data) == {"Slot", "id", "Count"}, "Electrolyzer unexpected item metadata")
        item((10, {key: value for key, value in data.items() if key != "Slot"}), scalar(data, "id", 8), scalar(data, "Count", 1))
        actual.append((slot, data["id"][1], data["Count"][1]))
    require(sorted(actual) == expected, "Electrolyzer exact item IDs/counts/slots differ")
    fluid(resources["fluid"], 0 if completed else 1000, canonical_empty=True)
    return [controller[LEGACY]]


def partial(root, row, machine, current_signature=None):
    parent = carrier(root, position(row, machine), BLOCK_IDS[machine]); process = process_fields(parent)
    require(process.get("schema_version") == (3, 1) and process.get("definition_id") == (8, NS + RECIPES[machine])
            and process.get("recipe_signature") == (8, current_signature or LEGACY_HASHES[machine])
            and process.get("progress_ticks") == (3, 5)
            and process.get("consumed_energy") == (4, 200 if machine == 1 else 100)
            and process.get("last_applied_transaction") == (8, "") and JOURNAL not in parent, "Partial clock/signature/journal differs")
    saved_resources(root, row, machine)
    if current_signature is None:
        require(MARKER not in parent, "Old task gained a JSON provenance marker")
    else:
        marker = compound(parent[MARKER]); require(marker == {"schema_version": (3, 1), "format": (8, "json_v1"),
            "converted": (1, 0), "recipe_id": (8, NS + RECIPES[machine])}, "Current partial marker differs")
    return parent


def owner(parent, row, machine):
    if machine != 2:
        value = scalar(compound(parent["arce_multiblock"]), "machine_instance_id", 8)
        require(str(uuid.UUID(value)) == value, "Noncanonical native controller UUID")
        return value
    x, y, z = position(row, machine)
    packed = ((x & 0x3ffffff) << 38) | ((z & 0x3ffffff) << 12) | (y & 0xfff)
    packed = packed if packed < (1 << 63) else packed - (1 << 64)
    raw = hashlib.md5((NS + "electrolyzer|minecraft:overworld|" + str(packed)).encode()).digest()
    return str(uuid.UUID(bytes=raw, version=3))


def snapshot(revision, entries):
    require(type(revision) is int and revision >= 0 and 1 <= len(entries) <= 64, "Snapshot bounds")
    ordered = sorted(entries, key=lambda e: ((0 if e[0] == "item" else 1), e[1], e[2]))
    require(len({entry[:3] for entry in ordered}) == len(ordered), "Duplicate snapshot key")
    children = []
    for kind, channel, resource, amount, capacity in ordered:
        require(kind in ("item", "fluid") and 0 <= amount <= capacity and capacity <= 4000, "Snapshot resource bounds")
        children.append((10, {"kind": (8, kind), "channel": (8, channel), "resource_id": (8, resource),
                              "amount": (4, amount), "capacity": (4, capacity)}))
    return 10, {"revision": (4, revision), "entries": (9, (10, children))}


def fingerprint(value):
    data = compound(value); digest = hashlib.sha256()
    fields = [str(scalar(data, "revision", 4))]
    for tag in data["entries"][1][1]:
        entry = compound(tag)
        fields += [scalar(entry, "kind", 8).upper(), scalar(entry, "channel", 8), scalar(entry, "resource_id", 8),
                   str(scalar(entry, "amount", 4)), str(scalar(entry, "capacity", 4))]
    for field in fields:
        raw = field.encode(); digest.update(struct.pack(">i", len(raw))); digest.update(raw)
    return digest.hexdigest()


def retained_plan(parent, row, machine, alternatives):
    process = process_fields(parent); revision = scalar(process, "resource_revision", 4)
    groups = [["minecraft:iron_ingot"], ["minecraft:redstone"]] if machine == 1 else [[NS + "empty_canister" if machine == 2 else "minecraft:iron_ingot"]]
    if alternatives is not None: groups = alternatives
    require(len(groups) == (2 if machine == 1 else 1) and all(1 <= len(group) <= 32 for group in groups), "Ingredient alternatives bound/arity differs")
    entries, deltas = [], {}
    for index, group in enumerate(groups):
        expected = NS + "empty_canister" if machine == 2 else "minecraft:iron_ingot" if index == 0 else "minecraft:redstone"
        require(expected in group and len(set(group)) == len(group), "Observed ingredient alternatives differ")
        for resource in group:
            # Fixture supports only the inspected registered vanilla alternatives (all max stack64).
            require(resource in {expected, "minecraft:copper_ingot", "minecraft:glowstone_dust"}, "Unbound fixture item capacity")
            channel = "item_input_" + str(index) if machine == 1 else "item_input"
            capacity = 16 if machine == 2 else 64
            entries.append(("item", channel, resource, 2 if resource == expected else 0, capacity))
        deltas[("item", channel, expected)] = -2
    outputs = [("item_output", "minecraft:iron_bars", 8, 64)] if machine == 0 else [
        ("item_output_0", NS + "advanced_circuit", 1, 64), ("item_output_1", "minecraft:redstone_torch", 2, 64)] if machine == 1 else [
        ("item_output", NS + "hydrogen_canister", 1, 16), ("item_output", NS + "oxygen_canister", 1, 16)]
    for channel, resource, amount, capacity in outputs:
        entries.append(("item", channel, resource, 0, capacity)); deltas[("item", channel, resource)] = amount
    if machine != 1:
        entries.append(("fluid", "fluid_input", "minecraft:water", 1000 if machine == 2 else 500, 4000))
        deltas[("fluid", "fluid_input", "minecraft:water")] = -1000 if machine == 2 else -100
    before = snapshot(revision, entries)
    after = snapshot(revision + 1, [entry[:3] + (entry[3] + deltas.get(entry[:3], 0), entry[4]) for entry in entries])
    transaction = str(uuid.uuid5(uuid.NAMESPACE_URL, "arce-signature-injected-cut/" + CASES[row] + "/" + MACHINES[machine] + "/" + owner(parent, row, machine)))
    return 10, {"schema_version": (3, 1), "transaction_id": (8, transaction), "machine_id": (8, owner(parent, row, machine)),
                "definition_id": (8, NS + RECIPES[machine]), "port_revision": (4, revision), "before_fingerprint": (8, fingerprint(before)),
                "phase": (8, "prepared"), "before": before, "after": after}


def inject_pending(root, row, machine, alternatives=None):
    parent = carrier(root, position(row, machine), BLOCK_IDS[machine]); value = process_fields(parent)
    require(value["progress_ticks"] == (3, 5) and JOURNAL not in parent, "Cut requires actual partial before-state")
    journal = retained_plan(parent, row, machine, alternatives)
    value.update(state=(8, "running"), progress_ticks=(3, 20 if machine == 1 else 100),
                 consumed_energy=(4, 800 if machine == 1 else 2000), failure_code=(8, "none"), failure_subject=(8, ""))
    if machine == 2: compound(parent[LEGACY])["progress"] = value["progress_ticks"]
    parent[JOURNAL] = journal
    return journal


def project(root, row, machine):
    parent = carrier(root, position(row, machine), BLOCK_IDS[machine])
    roots = {key: copy.deepcopy(parent[key]) for key in (PROCESS, JOURNAL, MARKER, LEGACY, "arce_precision_resources") if key in parent}
    ports = []
    for pos in port_positions(row, machine):
        be = carrier(root, pos); key = "arce_rolling_port" if machine == 0 else "arce_precision_port"
        ports.append((pos, copy.deepcopy(be[key])))
    return roots, ports


def retain_equal(before, after, rows=(0, 1, 4, 5, 6), machines=(0, 1, 2)):
    for row in rows:
        for machine in machines:
            require(project(before, row, machine) == project(after, row, machine), "Raw retained root/resource loss: " + CASES[row] + "/" + MACHINES[machine])


def patch_region(path: Path, original, candidate, allowed: set, evidence: Path):
    region = regions._read_regular(path, regions.MAX_REGION_BYTES)
    before_payload = regions._region_chunk(region, *CHUNK)
    (evidence / "region-before.mca").write_bytes(region)
    (evidence / "chunk-before.nbt.zlib").write_bytes(before_payload)
    known = {position(row, machine) for row in range(7) for machine in range(3)} | set().union(
        *(set(port_positions(row, machine)) for row in range(7) for machine in range(3)))
    require(allowed and allowed <= known, "Invalid patch scope")
    old_entities = compound(original)["block_entities"][1][1]
    new_entities = compound(candidate)["block_entities"][1][1]
    require(len(old_entities) == len(new_entities), "Patch changed BE list cardinality")
    for before, after in zip(old_entities, new_entities):
        pos = tuple(compound(before).get(axis, (0, None))[1] for axis in ("x", "y", "z"))
        require(pos in allowed or before == after, "Patch escaped exact allowed controller cells")
        if pos in allowed:
            permitted = {PROCESS, JOURNAL, MARKER, LEGACY, "arce_precision_resources", "arce_rolling_port", "arce_precision_port"}
            require({k: v for k, v in compound(before).items() if k not in permitted}
                    == {k: v for k, v in compound(after).items() if k not in permitted}, "Patch changed non-process/controller identity")
            if LEGACY in compound(before) and pos[1] in (position(1, 0)[1], position(3, 0)[1]):
                require({k: v for k, v in compound(compound(before)[LEGACY]).items() if k != "progress"}
                        == {k: v for k, v in compound(compound(after)[LEGACY]).items() if k != "progress"}, "Patch changed legacy resource/recipe identity")
    require({k: v for k, v in original[1].items() if k != "block_entities"}
            == {k: v for k, v in candidate[1].items() if k != "block_entities"}, "Patch changed unrelated chunk fields")
    require(decode(before_payload)[0] == original, "Stopped source changed before patch")
    # A stopped fixture may use denser zlib framing without growing the native
    # sector allocation. The exact same allocation and typed checks still apply.
    raw = encode(candidate); after_payload = zlib.compress(raw, 9)
    require(decode(after_payload)[0] == candidate, "Typed patch round-trip differs")
    index = 4 * (15 + 15 * 32); start = int.from_bytes(region[index:index + 3], "big") * 4096
    allocated = region[index + 3] * 4096
    record = struct.pack(">I", len(after_payload) + 1) + b"\x02" + after_payload
    require(len(record) <= allocated, "Injected cut cannot fit original chunk sector allocation")
    require(not server.is_link_or_junction(path), "Linked stopped patch target")
    patched = region[:start] + record + b"\0" * (allocated - len(record)) + region[start + allocated:]
    (evidence / "region-before.mca").write_bytes(region); (evidence / "region-after.mca").write_bytes(patched)
    (evidence / "chunk-before.nbt.zlib").write_bytes(before_payload); (evidence / "chunk-after.nbt.zlib").write_bytes(after_payload)
    write(evidence / "patch.json", {"injected_fixture_not_natural_callback": True, "allowed": sorted(allowed),
          "region_before": sha(region), "region_after": sha(patched), "chunk_before": sha(before_payload),
          "chunk_after": sha(after_payload), "allocated_bytes": allocated, "record_bytes": len(record)})
    path.write_bytes(patched); require(path.read_bytes() == patched, "Stopped patch write changed")


def unique_json(pairs):
    require(len(pairs) <= 14 and len({key for key, _ in pairs}) == len(pairs), "Duplicate/overlong report fields")
    return dict(pairs)


def check_reports(lines, machine):
    position(0, machine)
    rows, ended = [], False
    fields = {"cell", "machine", "marker", "energy_simulated", "item_simulated", "fluid_simulated", "tag_generation",
              "alternatives", "signature", "status", "failure", "subject", "progress", "protected"}
    for line in lines:
        match = ROW.search(line)
        if match:
            require(not ended and len(rows) < 7 and len(match[1].encode()) <= 8192, "Oversized/post-end report")
            row = json.loads(match[1], object_pairs_hook=unique_json); require(set(row) == fields, "Report fields differ")
            require(type(row["progress"]) is int and 0 <= row["progress"] <= 72000 and type(row["marker"]) is bool
                    and type(row["protected"]) is bool, "Report value kinds differ")
            require(type(row["tag_generation"]) is int and 0 <= row["tag_generation"] < 2**63, "Tag epoch differs")
            for key in ("energy_simulated", "item_simulated", "fluid_simulated"):
                require(type(row[key]) is int and row[key] in (-1, 0, 1), "Report capability simulation value differs")
            require(all(type(row[key]) is str and len(row[key]) <= 128 for key in ("status", "failure", "subject", "signature")), "Report strings unbounded")
            require(type(row["alternatives"]) is list and len(row["alternatives"]) <= 2
                    and all(type(group) is list and 1 <= len(group) <= 32 and all(type(value) is str and len(value) <= 128 for value in group) for group in row["alternatives"]), "Report alternatives unbounded")
            rows.append(row)
        elif re.search(CONSOLE + r"ARCE_SIGNATURE_REPORT(?: |$)", line):
            raise server.SmokeError("Malformed authoritative Signature row")
        if end := END.search(line):
            require(not ended and len(rows) == 7 and end[1] == MACHINES[machine], "Early/duplicate/wrong-adapter report end"); ended = True
    require(ended and [(row["cell"], row["machine"]) for row in rows] == [(case, MACHINES[machine]) for case in CASES], "Missing/duplicate/out-of-order native reports")
    return rows


def check_refusals(rows, *, startup_pending=False):
    """Require runtime refusals; exact load-only pending may only request a retry."""
    settled = True
    for row in rows:
        index = CASES.index(row["cell"])
        if index not in (0, 1, 4, 5, 6): continue
        require(not row["marker"] if index in (0, 1, 4, 5) else row["marker"], "Retained marker presence changed")
        require(row["protected"] and row["energy_simulated"] == 0 and row["item_simulated"] == 0
                and row["fluid_simulated"] == (-1 if row["machine"] == "precision" else 0), "Refused job accepted a resource operation")
        require(row["status"] == ("INVALID_RECIPE" if row["machine"] == "electrolyzer" else "RECOVERY_REQUIRED") if index < 2
                else row["status"] == "UNSUPPORTED_DATA", "Refusal status lost typed precedence")
        if row["machine"] != "electrolyzer" and index < 2:
            require(row["failure"] == "RECOVERY_DIVERGED", "Unproved old failure code changed")
            if startup_pending and row["subject"] == "signature_migration_pending":
                settled = False  # Load state cannot satisfy a completed runtime stage.
            else:
                require(row["subject"] == "signature_migration_unproven", "Unproved old runtime refusal reason changed")
    return settled


def barrier(process, commands, texts, name, deadline):
    start = len(process.lines)
    marker = re.compile(SAY + re.escape(name) + r"[ \t]*$")
    for text in [*texts, "say " + name]: commands.append(text); process.command(text)
    remaining = deadline - time.monotonic(); require(remaining > 0, "Command exceeded original deadline")
    process.wait_for(marker, remaining, start_at=start)
    require(time.monotonic() <= deadline, "Barrier woke after original deadline")
    matches = [index for index, line in enumerate(process.lines[start:], start) if marker.search(line)]
    require(len(matches) == 1, "Missing/duplicate authoritative barrier")
    return process.lines[start:matches[0] + 1]


def wait_full(process, commands):
    deadline = time.monotonic() + WAIT_SECONDS
    for attempt in range(MAX_PROBES):
        if time.monotonic() >= deadline: break
        lines = barrier(process, commands, ["execute if loaded 254 128 242 run say ARCE_SIGNATURE_FULL_LOADED"],
                        "ARCE_SIGNATURE_LOAD_END_" + str(attempt), deadline)
        if any(FULL.search(line) for line in lines): return
        time.sleep(min(0.25, max(0, deadline - time.monotonic())))
    raise server.SmokeError("Signature fixed chunk did not become FULL within60s/240probes")


def report(process, commands, name, machine):
    return check_reports(barrier(process, commands, ["arce signature release-test " + MACHINES[machine] + " report"], name, time.monotonic() + WAIT_SECONDS), machine)


def wait_refusals(process, commands, name, machine):
    # FULL/load/report can precede the first budgeted process tick. Every poll
    # preserves the normal marker/protection/capability/status checks; only the
    # exact load-only subject can defer acceptance, under one original deadline.
    deadline = time.monotonic() + WAIT_SECONDS
    for attempt in range(MAX_PROBES):
        if time.monotonic() >= deadline: break
        rows = check_reports(barrier(process, commands,
            ["arce signature release-test " + MACHINES[machine] + " report"],
            name + "_" + str(attempt), deadline), machine)
        if check_refusals(rows, startup_pending=True):
            check_refusals(rows)
            return rows
        time.sleep(min(0.25, max(0, deadline - time.monotonic())))
    raise server.SmokeError("Unproved old runtime refusal did not settle within60s/240probes")


def clean_stop(lines):
    indices = [index for index, line in enumerate(lines) if STOP.search(line)]
    require(len(indices) == 1 and any(SAVED.search(line) for line in lines[indices[0] + 1:]), "No exact completed clean-stop trace")
    return {"stopping_line": indices[0] + 1, "saved_after_stop": True, "crash_atomicity": "NOT_CLAIMED"}


def capture(runtime, evidence):
    region = regions._read_regular(runtime / "world/region/r.0.0.mca", regions.MAX_REGION_BYTES)
    compressed = regions._region_chunk(region, *CHUNK)
    (evidence / "region.mca").write_bytes(region); (evidence / "chunk-15-15.nbt.zlib").write_bytes(compressed)
    root, nodes = decode(compressed)
    write(evidence / "chunk-identity.json", {"sha256": sha(compressed), "bytes": len(compressed), "nodes": nodes,
          "max_nodes": MAX_NODES, "max_depth": MAX_DEPTH})
    return root


def confirm_empty(root, machine):
    cells = set().union(*(footprint(row, machine) for row in range(7)))
    require(len(cells) == (217, 259, 14)[machine], "Fixed mutation footprint changed")
    require(all(block_at(root, cell) == {"Name": "minecraft:air"} for cell in cells), "Occupied Signature fixture footprint")
    entries = root[1].get("block_entities", (9, (10, [])))[1][1]
    require(not any(tuple(value.get(axis, (0, None))[1] for axis in ("x", "y", "z")) in cells for kind, value in entries if kind == 10), "Existing BE in fixture footprint")


def finished(root, row, machine, transaction=None):
    parent = carrier(root, position(row, machine), BLOCK_IDS[machine]); process = process_fields(parent)
    saved_resources(root, row, machine, True)
    require(JOURNAL not in parent and process["progress_ticks"] == (3, 0) and process["definition_id"] == (8, "")
            and process["consumed_energy"] == (4, 0), "Completed job retained active clock/journal")
    last = scalar(process, "last_applied_transaction", 8)
    require(str(uuid.UUID(last)) == last and (transaction is None or last == transaction), "Applied transaction differs/missing")
    require(compound(parent[MARKER]) == {"schema_version": (3, 1), "format": (8, "json_v1"), "converted": (1, 0)}, "Completed current marker differs")
    return last


def prepare_commands(machine):
    position(0, machine)
    commands = []
    for row in range(7):
        for machine in (machine,):
            x, y, z = position(row, machine)
            commands.append(f"setblock {x} {y} {z} {BLOCK_IDS[machine]}[facing=north]" if machine == 2
                            else f"arce {MACHINES[machine]} release-test prepare {x} {y} {z}")
    return commands


def seed_commands(rows, machine):
    position(0, machine)
    require(tuple(rows) in ((0, 1), (2, 3)), "Unsafe seed row selection")
    commands = []
    for row in rows:
        for machine in (machine,):
            x, y, z = position(row, machine)
            if machine < 2:
                commands += [f"arce {MACHINES[machine]} release-test pause {x} {y} {z}",
                             f"arce {MACHINES[machine]} release-test seed {x} {y} {z}"]
                energy = port_positions(row, machine)[2 if machine == 0 else 7]
                root = "arce_rolling_port" if machine == 0 else "arce_precision_port"
                commands.append(f"data modify block {' '.join(map(str, energy))} {root}.energy set value {200 if machine == 1 else 100}")
            else:
                commands += [f"setblock {x} {y} {z - 1} minecraft:redstone_block",
                    f'data modify block {x} {y} {z} {LEGACY} set value {{schema_version:1,inventory:{{Size:4,Items:[{{Slot:0,id:"{NS}empty_canister",Count:2b}}]}},fluid:{{FluidName:"minecraft:water",Amount:1000}},energy:100,progress:0}}']
    for row in rows:
        for machine in (machine,):
            x, y, z = position(row, machine)
            commands.append(f"setblock {x} {y} {z - 1} minecraft:air" if machine == 2
                            else f"arce {MACHINES[machine]} release-test resume {x} {y} {z}")
    return commands


def wait_partial(process, commands, rows, machine):
    deadline = time.monotonic() + WAIT_SECONDS
    for attempt in range(MAX_PROBES):
        if time.monotonic() >= deadline: break
        texts = []
        for row in rows:
            for machine in (machine,):
                coords = " ".join(map(str, position(row, machine)))
                texts.append(f"execute if data block {coords} {{{PROCESS}:{{progress_ticks:5,consumed_energy:{200 if machine == 1 else 100}L}}}} run say ARCE_SIGNATURE_PARTIAL_{row}_{machine}")
        lines = barrier(process, commands, texts, "ARCE_SIGNATURE_PARTIAL_END_" + str(attempt), deadline)
        if all(sum(re.search(SAY + f"ARCE_SIGNATURE_PARTIAL_{row}_{machine}[ \\t]*$", line) is not None for line in lines) == 1 for row in rows):
            return
        time.sleep(min(0.25, max(0, deadline - time.monotonic())))
    raise server.SmokeError("Natural partial clocks not reached within60s/240probes")


def wait_formed(process, commands, machine):
    if machine == 2: return  # Single-block adapter has no multiblock formation; stopped exact-cell oracle still applies.
    deadline = time.monotonic() + WAIT_SECONDS
    for attempt in range(MAX_PROBES):
        if time.monotonic() >= deadline: break
        texts = [f'execute if data block {" ".join(map(str, position(row, machine)))} {{arce_multiblock:{{formation_state:"FORMED"}}}} run say ARCE_SIGNATURE_FORMED_{row}_{machine}'
                 for row in range(7)]
        lines = barrier(process, commands, texts, "ARCE_SIGNATURE_FORM_END_" + str(attempt), deadline)
        if all(sum(re.search(SAY + f"ARCE_SIGNATURE_FORMED_{row}_{machine}[ \\t]*$", line) is not None for line in lines) == 1 for row in range(7)):
            return
        time.sleep(min(0.25, max(0, deadline - time.monotonic())))
    raise server.SmokeError("Fixed structures not formed within60s/240probes")


def legacy_patch(root, machine):
    position(0, machine)
    candidate = copy.deepcopy(root); allowed = set()
    for machine in (machine,):
        for row in (0, 1): partial(root, row, machine)
        inject_pending(candidate, 1, machine); allowed.add(position(1, machine))
        for row in (4, 5, 6):
            source = carrier(root, position(0, machine), BLOCK_IDS[machine])
            target = carrier(candidate, position(row, machine), BLOCK_IDS[machine])
            require(MARKER not in target and JOURNAL not in target, "Old idle fixture already has witness/journal")
            for key in (PROCESS, LEGACY, "arce_precision_resources"):
                if key in source: target[key] = copy.deepcopy(source[key])
            if machine == 1:
                compound(target["arce_precision_resources"])["machine_id"] = (8, owner(target, row, machine))
            for before_pos, after_pos in zip(port_positions(0, machine), port_positions(row, machine)):
                key = "arce_rolling_port" if machine == 0 else "arce_precision_port"
                carrier(candidate, after_pos)[key] = copy.deepcopy(carrier(root, before_pos)[key]); allowed.add(after_pos)
            if row == 4:
                compound(target[PROCESS]).update(schema_version=(3, 2), extension=(8, "native-future-process-retained"))
            elif row == 5:
                journal = retained_plan(target, row, machine, None)
                compound(journal).update(schema_version=(3, 2), extension=(8, "native-future-journal-retained"))
                target[JOURNAL] = journal
            else: target[MARKER] = (8, "native-opaque-marker-retained")
            allowed.add(position(row, machine))
    # Row0 and all prepared fresh current cells are not rewritten.
    retain_equal(root, candidate, rows=(0, 2, 3), machines=(machine,))
    return candidate, allowed


def current_patch(root, rows, machine):
    position(0, machine)
    candidate = copy.deepcopy(root); allowed = set(); transactions = []
    for machine in (machine,):
        row = rows[3]
        partial(root, 3, machine, row["signature"])
        journal = inject_pending(candidate, 3, machine, row["alternatives"])
        transactions.append(compound(journal)["transaction_id"][1]); allowed.add(position(3, machine))
    retain_equal(root, candidate, machines=(machine,))
    require(project(root, 2, machine) == project(candidate, 2, machine), "Current cut changed unrelated partial")
    return candidate, allowed, transactions


def bind_artifact(path, expected, version):
    require(re.fullmatch(r"[0-9a-f]{64}", expected) is not None, "Artifact SHA must be explicit")
    value = motors.artifact(path, version); require(value["sha256"] == expected, "Artifact pin mismatch")
    return value


def bind_recipes(old, host):
    result = {}
    with zipfile.ZipFile(old["path"]) as before, zipfile.ZipFile(host["path"]) as after:
        require("io/github/sunthemoon/advancedrocketrycommunity/machine/recipe/RecipeSignatureReleaseTestCommands.class" in after.namelist(), "Host lacks proposed Signature hook; candidate11 baseline is not runnable")
        for index, name in enumerate(RECIPES):
            entry = "data/advancedrocketrycommunity/recipes/" + name + ".json"
            require(before.namelist().count(entry) == 1 and after.namelist().count(entry) == 1, "Recipe duplicate/missing runtime entry")
            require(before.getinfo(entry).file_size <= 16384 and after.getinfo(entry).file_size <= 16384, "Recipe JSON byte bound")
            old_bytes, new_bytes = before.read(entry), after.read(entry)
            require(sha(old_bytes) == OLD_JSON_SHAS[index], "Historical recipe bytes differ")
            old_json, new_json = json.loads(old_bytes), json.loads(new_bytes)
            expected = copy.deepcopy(old_json)
            if index == 0: expected["ingredient"] = {"tag": "forge:ingots/iron"}
            if index == 1:
                expected["inputs"][0]["ingredient"] = {"tag": "forge:ingots/iron"}
                expected["inputs"][1]["ingredient"] = {"tag": "forge:dusts/redstone"}
            require(new_json == expected, "Host semantic recipe changed beyond approved selectors")
            result[name] = {"old_bytes_sha256": sha(old_bytes), "host_bytes_sha256": sha(new_bytes), "old_json": old_json, "host_json": new_json}
        # These exact codec class files are checked against the actual historical binary, not assumed from source history.
        prefixes = ("machine/process/persistence/ProcessStatePersistence", "machine/process/persistence/ProcessJournalPersistence",
                    "machine/process/ProcessResourceSnapshot", "machine/process/ProcessProgress")
        codecs = {}
        for name in before.namelist():
            if name.endswith(".class") and any(name == "io/github/sunthemoon/advancedrocketrycommunity/" + prefix + ".class" for prefix in prefixes):
                require(name in after.namelist() and before.read(name) == after.read(name), "Historical fixture codec changed")
                codecs[name] = sha(before.read(name))
        require(len(codecs) == 4, "Missing pinned historical codec facts")
        result["exact_shared_codec_classes"] = codecs
    return result


def install_tag_pack(runtime):
    pack = runtime / "world/datapacks/arce-signature-tag-reload"
    require(not pack.exists(), "Tag fixture pack already exists")
    pack.mkdir(parents=True)
    write(pack / "pack.mcmeta", {"pack": {"pack_format": 15, "description": "Signature native tag-only fixture"}})
    for name, value in (("ingots/iron", "minecraft:copper_ingot"), ("dusts/redstone", "minecraft:glowstone_dust")):
        path = pack / "data/forge/tags/items" / (name + ".json"); path.parent.mkdir(parents=True, exist_ok=True)
        write(path, {"replace": False, "values": [value]})
    return {str(path.relative_to(pack)): server.digest_file(path) for path in pack.rglob("*") if path.is_file()}


def check_original_tag_table(rows, original_alternatives, machine):
    """A validated live report must still expose the pre-install fixture table."""
    groups = rows[2]["alternatives"]
    require(groups == original_alternatives, "Fixture alternatives changed before live installation")
    require(len(groups) == (2 if machine == 1 else 1), "Original fixture ingredient groups differ")
    if machine < 2:
        require("minecraft:iron_ingot" in groups[0] and "minecraft:copper_ingot" not in groups[0],
                "Iron expansion already present before live installation")
    if machine == 1:
        require("minecraft:redstone" in groups[1] and "minecraft:glowstone_dust" not in groups[1],
                "Redstone expansion already present before live installation")


def check_live_tag_expansion(before, after, machine):
    """Tag-selector adapters observe a delta; the literal recipe is only a control."""
    require(all(new["tag_generation"] > old["tag_generation"] for old, new in zip(before, after)),
            "Tag generation did not advance from every pre-install row")
    fields = ("marker", "signature", "progress", "status", "failure", "subject", "protected",
              "energy_simulated", "item_simulated", "fluid_simulated")
    require(all(after[2][key] == before[2][key] for key in fields),
            "Live reload changed retained current-partial report")
    expected = copy.deepcopy(before[2]["alternatives"])
    if machine < 2: expected[0] = sorted([*expected[0], "minecraft:copper_ingot"])
    if machine == 1: expected[1] = sorted([*expected[1], "minecraft:glowstone_dust"])
    require(after[2]["alternatives"] == expected, "Live fixture alternative delta differs")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("runtime-template", "source-world", "v17-jar", "host-jar", "work-root"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--v17-sha256", required=True); parser.add_argument("--host-sha256", required=True)
    parser.add_argument("--fixture-jar", type=Path); parser.add_argument("--fixture-sha256")
    parser.add_argument("--machine", choices=MACHINES, required=True,
                        help="One adapter per disjoint copied-world run; all three require18host launches")
    parser.add_argument("--java", default="C:/Program Files/Java/jdk-17.0.7/bin/java.exe")
    args = parser.parse_args()
    machine = MACHINES.index(args.machine)
    require(args.v17_sha256 == OLD_SHA and args.host_sha256 != BASELINE_SHA, "Wrong old pin or hookless baseline host")
    require(bool(args.fixture_jar) == bool(args.fixture_sha256), "Fixture path/hash pair required")
    work = args.work_root.resolve(); require(not work.exists(), "Use a fresh disposable work-root")
    for path in (args.runtime_template, args.source_world, args.v17_jar, args.host_jar, args.fixture_jar):
        if path:
            resolved = path.resolve(); require(not work.is_relative_to(resolved) and not resolved.is_relative_to(work), "Input/work overlap")
    old = bind_artifact(args.v17_jar, args.v17_sha256, "1.20.1-1.7.0-dev")
    host = bind_artifact(args.host_jar, args.host_sha256, "1.20.1-1.8.0-dev")
    fixture = bind_artifact(args.fixture_jar, args.fixture_sha256, None) if args.fixture_jar else None
    recipe_facts = bind_recipes(old, host)
    runtime = work / "server"; runtime.mkdir(parents=True)
    original_world = worlds.copy_world(args.source_world, runtime / "world")
    libraries = args.runtime_template / "libraries"
    require(libraries.is_dir() and not server.is_link_or_junction(libraries), "Missing/linked runtime libraries")
    require(not any(server.is_link_or_junction(path) for path in libraries.rglob("*")), "Linked runtime dependency")
    shutil.copytree(libraries, runtime / "libraries")
    mods = runtime / "mods"; mods.mkdir()
    if fixture:
        require(fixture["name"] not in (old["name"], host["name"]), "Fixture/host collision")
        shutil.copyfile(fixture["path"], mods / fixture["name"])
    properties = server.write_server_configuration(runtime, server.allocate_port(), True)
    java, version = server.resolve_java(args.java); command = rocket._server_command(java); command[2] = "-Xmx2G"
    dependencies = [Path(module.__file__).resolve() for module in (server, rocket, motors, regions, worlds)] + [Path(__file__).resolve()]
    dependency_pins = {str(path): server.digest_file(path) for path in dependencies}
    write(work / "inputs.json", {"old": old, "current": host, "fixture": fixture, "java": version, "source_world": str(args.source_world.resolve()),
          "runtime_template": str(args.runtime_template.resolve()), "properties_sha256": properties, "dependencies": dependency_pins,
          "machine": args.machine, "planned_host_phases_this_run": 6, "planned_host_phases_all_three_disjoint_runs": 18,
          "recipes_and_shared_codec_facts": recipe_facts, "historical_pending": "INJECTED_REACHABLE_STOPPED_FIXTURE_NOT_NATURAL_CAPTURE"})
    write(work / "source-world-files.json", original_world)
    identity = runtime / "world/.v180-signature-smoke-identity.json"
    require(not identity.exists(), "Existing fixture identity")
    write(identity, {"identity": sha((str(work) + host["sha256"] + properties).encode())}); identity_hash = server.digest_file(identity)
    receipts = []

    def phase(name, artifact, actions):
        evidence = work / name; evidence.mkdir(); process, commands = None, []
        receipt = {"phase": name, "host_sha256": artifact["sha256"], "result": "IN_PROGRESS"}
        for installed in (old, host):
            path = mods / installed["name"]
            if path.exists(): path.unlink()  # Only the two explicitly pinned names in this newly created runtime.
        shutil.copyfile(artifact["path"], mods / artifact["name"])
        require(server.digest_file(mods / artifact["name"]) == artifact["sha256"], "Copied host differs")
        write(evidence / "launch.json", {"cwd": str(runtime), "command": command, "host": artifact})
        try:
            process = server.CapturedProcess(command, runtime, evidence / "stdout.txt")
            process.wait_for(server.READY_MARKER, 300)
            for text in ("forceload add 240 240", "forceload query 240 240"):
                barrier(process, commands, [text], "ARCE_SIGNATURE_TICKET_" + str(len(commands)), time.monotonic() + WAIT_SECONDS)
            wait_full(process, commands)
            actions(process, commands, receipt)
            start = len(process.lines); commands.append("save-all flush"); process.command("save-all flush")
            process.wait_for(server.SAVE_MARKER, 300, start_at=start); commands.append("stop"); process.command("stop")
            require(process.finish(300) == 0, "Native host did not clean-stop")
            receipt["clean_stop"] = clean_stop(process.lines)
            findings = server.scan_log(process.lines); write(evidence / "log-findings.json", findings)
            require(not findings, "Unexpected native log findings (raw log retained)")
            root = capture(runtime, evidence)
            require(server.digest_file(identity) == identity_hash and server.digest_file(runtime / server.SERVER_PROPERTIES_IDENTITY_FILE) == properties, "Same-world/properties identity changed")
            require(server.digest_file(mods / artifact["name"]) == artifact["sha256"], "Installed host changed")
            if fixture: require(server.digest_file(mods / fixture["name"]) == fixture["sha256"], "Installed fixture changed")
            receipt.update(result="OBSERVED_CLEAN_STOP", exit_code=0)
            return root, receipt
        except BaseException as error:
            receipt.update(result="FAIL", error=f"{type(error).__name__}: {error}")
            if process: process.abort()
            raise
        finally:
            write(evidence / "receipt.json", receipt); write(evidence / "commands.json", commands)
            for filename in ("latest.log", "debug.log"):
                path = runtime / "logs" / filename
                if path.exists(): shutil.copyfile(path, evidence / filename)
            receipts.append(receipt)

    empty, _ = phase("old-bootstrap", old, lambda p, c, r: None); confirm_empty(empty, machine)
    def old_partials(process, commands, receipt):
        barrier(process, commands, prepare_commands(machine), "ARCE_SIGNATURE_PREPARED", time.monotonic() + WAIT_SECONDS)
        wait_formed(process, commands, machine)
        barrier(process, commands, seed_commands((0, 1), machine), "ARCE_SIGNATURE_OLD_SEEDED", time.monotonic() + WAIT_SECONDS)
        wait_partial(process, commands, (0, 1), machine)
    old_saved, _ = phase("old-partials", old, old_partials)
    old_cut, allowed = legacy_patch(old_saved, machine)
    evidence = work / "injected-legacy-cut"; evidence.mkdir()
    patch_region(runtime / "world/region/r.0.0.mca", old_saved, old_cut, allowed, evidence)
    signatures = []
    def current_partials(process, commands, receipt):
        before = wait_refusals(process, commands, "ARCE_SIGNATURE_BEFORE_CURRENT", machine)
        barrier(process, commands, seed_commands((2, 3), machine), "ARCE_SIGNATURE_CURRENT_SEEDED", time.monotonic() + WAIT_SECONDS)
        wait_partial(process, commands, (2, 3), machine)
        rows = report(process, commands, "ARCE_SIGNATURE_CURRENT_PARTIALS", machine); check_refusals(rows)
        receipt["reports"] = rows; signatures.append(rows[2]["signature"])
    current_saved, current_receipt = phase("upgrade-current", host, current_partials)
    retain_equal(old_cut, current_saved, machines=(machine,))
    for row in (2, 3):
        partial(current_saved, row, machine, signatures[0])
    current_cut, allowed, transactions = current_patch(current_saved, current_receipt["reports"], machine)
    evidence = work / "injected-current-cut"; evidence.mkdir()
    patch_region(runtime / "world/region/r.0.0.mca", current_saved, current_cut, allowed, evidence)
    def reload_report(process, commands, receipt):
        rows = wait_refusals(process, commands, "ARCE_SIGNATURE_RESTART1_BEFORE_RELOAD", machine)
        before_epoch = rows[0]["tag_generation"]
        deadline = time.monotonic() + WAIT_SECONDS
        check_original_tag_table(rows, current_receipt["reports"][2]["alternatives"], machine)
        require(rows[2]["signature"] == signatures[0], "Pre-install current signature changed")
        receipt["reports_before_reload"] = rows
        pack_hashes = install_tag_pack(runtime); write(work / "tag-only-pack.json", pack_hashes)
        receipt["live_tag_pack"] = pack_hashes
        require(time.monotonic() < deadline, "Tag fixture installation exceeded original reload deadline")
        commands.append("reload"); process.command("reload")
        after = None
        for attempt in range(MAX_PROBES):
            if time.monotonic() >= deadline: break
            observed = check_reports(barrier(process, commands, ["arce signature release-test " + args.machine + " report"], "ARCE_SIGNATURE_RELOAD_" + str(attempt), deadline), machine)
            if all(row["tag_generation"] > before_epoch for row in observed): after = observed; break
            time.sleep(min(0.25, max(0, deadline - time.monotonic())))
        require(after is not None, "Actual TagsUpdatedEvent generation did not advance within60s/240probes")
        check_refusals(after)
        require(after[2]["signature"] == signatures[0], "Tag-only reload changed semantic signatures")
        if machine < 2: require("minecraft:copper_ingot" in after[2]["alternatives"][0], "Iron tag alternative not bound")
        if machine == 1: require("minecraft:glowstone_dust" in after[2]["alternatives"][1], "Redstone tag alternative not bound")
        check_live_tag_expansion(rows, after, machine)
        receipt["tag_reload_applicability"] = "new_tag_alternatives" if machine < 2 else "literal_item_generation_control"
        receipt["reports_before_reload"] = rows; receipt["reports"] = after
    restarted, _ = phase("restart-1-reload", host, reload_report); retain_equal(old_cut, restarted, machines=(machine,))
    for machine in (machine,):
        partial(restarted, 2, machine, signatures[0]); finished(restarted, 3, machine, transactions[0])
        require(process_fields(carrier(restarted, position(3, machine)))["resource_revision"]
                == compound(compound(carrier(current_cut, position(3, machine))[JOURNAL])["after"])["revision"], "Pending recovery resource revision differs")
    def finish_current(process, commands, receipt):
        deadline = time.monotonic() + WAIT_SECONDS
        powered = False
        for attempt in range(MAX_PROBES):
            if time.monotonic() >= deadline: break
            rows = check_reports(barrier(process, commands, ["arce signature release-test " + args.machine + " report"], "ARCE_SIGNATURE_FINISH_" + str(attempt), deadline), machine)
            if not check_refusals(rows, startup_pending=not powered):
                time.sleep(min(0.25, max(0, deadline - time.monotonic())))
                continue
            check_refusals(rows)
            if not powered:
                power_receipt = barrier(process, commands, ["arce signature release-test " + args.machine + " power-current"], "ARCE_SIGNATURE_POWERED", deadline)
                require(sum(re.search(CONSOLE + r"ARCE_SIGNATURE_POWER_CURRENT_END machine=" + args.machine + r"[ \t]*$", line) is not None for line in power_receipt) == 1, "Missing/duplicate ordinary FE power receipt")
                powered = True
                continue  # Completion must be observed after ordinary power, never inferred from the startup row.
            if all(row["progress"] == 0 for row in rows[2:4]): receipt["reports"] = rows; return
            time.sleep(min(0.25, max(0, deadline - time.monotonic())))
        raise server.SmokeError("Supported current batch did not complete within60s/240probes")
    completed, _ = phase("restart-2-finish", host, finish_current); retain_equal(old_cut, completed, machines=(machine,))
    finished(completed, 2, machine); finished(completed, 3, machine, transactions[0])
    def final_report(process, commands, receipt):
        rows = wait_refusals(process, commands, "ARCE_SIGNATURE_FINAL", machine); receipt["reports"] = rows
    final, _ = phase("restart-3-idempotence", host, final_report); retain_equal(old_cut, final, machines=(machine,))
    for row in (2, 3):
        for machine in (machine,):
            require(project(completed, row, machine) == project(final, row, machine), "Current batch repeated or changed resources across final clean restart")
    require(worlds.inventory(args.source_world) == original_world, "Historical input world changed")
    for artifact in (old, host, fixture):
        if artifact: require(server.digest_file(Path(artifact["path"])) == artifact["sha256"], "Original artifact changed")
    require(all(server.digest_file(Path(path)) == expected for path, expected in dependency_pins.items()), "Harness input changed during execution")
    write(work / "summary.json", {"result": "OBSERVED_FIXTURE_CHECKS_PASS", "machine": args.machine,
          "other_adapters": "NOT_COVERED_BY_THIS_RUN", "planned_all_adapter_host_launches": 18,
          "phases": receipts, "same_world_current_restarts": 3,
          "source_world_unchanged": True, "historical_pending": "INJECTED_NOT_NATURALLY_CAPTURED",
          "native_replay": "AUTHOR_HARNESS_NOT_INDEPENDENT_REPLAY", "crash_atomicity": "NOT_CLAIMED", "required_gates_passed": False})
    print("Observed Signature stopped fixtures and three clean restarts; no natural pending/crash/Gate admission")


if __name__ == "__main__": main()
