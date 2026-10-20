"""W107C: bounded, type-preserving NBT and Anvil-record tools for the loader cuts' offline fixture states.

Root decision R36 item 2 (and R43 item 5) lets the harness edit only the disposable server copy between the forced stop
and a restart. These tools read and rewrite exactly one ledger file or one inline chunk record, keep every other tag's
type and bytes (strings stay raw modified UTF-8), and refuse anything outside fixed bounds. They also read an external
``.mcc`` record, for cut E2's oracle, without ever writing one.
"""
from __future__ import annotations

import gzip
import hashlib
import io
from pathlib import Path
import struct
import uuid
import zlib

MAX_DECODED = 4 * 1024 * 1024
MAX_COMPRESSED = 4 * 1024 * 1024
MAX_TAGS = 262_144
MAX_DEPTH = 64
SECTOR = 4096
BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTES, STRING, LIST, COMPOUND, INTS, LONGS = range(1, 13)
_SCALAR = {BYTE: ">b", SHORT: ">h", INT: ">i", LONG: ">q", FLOAT: ">f", DOUBLE: ">d"}
_ARRAY = {INTS: ">i", LONGS: ">q"}


class NbtError(ValueError):
    """A deterministic bounded-NBT or Anvil refusal."""


class Tag:
    """One typed value: scalars as numbers, strings as raw bytes, lists as (element type, items), compounds as dicts."""
    __slots__ = ("type", "value")

    def __init__(self, tag_type: int, value):
        self.type, self.value = tag_type, value

    def __eq__(self, other):
        return isinstance(other, Tag) and self.type == other.type and self.value == other.value

    def __repr__(self):
        return f"Tag({self.type}, {self.value!r})"


class _Reader:
    def __init__(self, payload: bytes):
        if len(payload) > MAX_DECODED:
            raise NbtError("Decoded NBT exceeds its bound")
        self.payload, self.offset, self.tags = payload, 0, 0

    def take(self, size: int) -> bytes:
        end = self.offset + size
        if size < 0 or end > len(self.payload):
            raise NbtError("NBT ended unexpectedly")
        value = self.payload[self.offset:end]; self.offset = end
        return value

    def unpack(self, shape: str, size: int):
        return struct.unpack(shape, self.take(size))[0]

    def length(self) -> int:
        value = self.unpack(">i", 4)
        if value < 0 or value > MAX_DECODED:
            raise NbtError("Negative or oversized NBT length")
        return value

    def tag(self, tag_type: int, depth: int) -> Tag:
        self.tags += 1
        if depth > MAX_DEPTH or self.tags > MAX_TAGS:
            raise NbtError("NBT exceeds its depth or tag bound")
        if tag_type in _SCALAR:
            shape = _SCALAR[tag_type]; return Tag(tag_type, self.unpack(shape, struct.calcsize(shape)))
        if tag_type == BYTES:
            return Tag(BYTES, self.take(self.length()))
        if tag_type == STRING:
            return Tag(STRING, self.take(self.unpack(">H", 2)))
        if tag_type == LIST:
            child = self.unpack(">B", 1); count = self.length()
            if child == 0 and count:
                raise NbtError("Non-empty NBT list of TAG_End")
            return Tag(LIST, (child, [self.tag(child, depth + 1) for _ in range(count)]))
        if tag_type == COMPOUND:
            value = {}
            while (child := self.unpack(">B", 1)) != 0:
                name = self.take(self.unpack(">H", 2)).decode("utf-8")
                if name in value:
                    raise NbtError("Duplicate NBT compound key")
                value[name] = self.tag(child, depth + 1)
            return Tag(COMPOUND, value)
        if tag_type in _ARRAY:
            shape = _ARRAY[tag_type]; count = self.length()
            return Tag(tag_type, list(struct.unpack(f">{count}{shape[1]}", self.take(count * struct.calcsize(shape)))))
        raise NbtError(f"Unknown NBT tag type {tag_type}")


def read(payload: bytes) -> tuple[bytes, Tag]:
    """The named root compound of one complete NBT payload."""
    reader = _Reader(payload)
    if reader.unpack(">B", 1) != COMPOUND:
        raise NbtError("NBT root must be a compound")
    name = reader.take(reader.unpack(">H", 2)); root = reader.tag(COMPOUND, 0)
    if reader.offset != len(payload):
        raise NbtError("NBT has trailing bytes")
    return name, root


def _write(tag: Tag, out: list) -> None:
    if tag.type in _SCALAR:
        out.append(struct.pack(_SCALAR[tag.type], tag.value))
    elif tag.type == BYTES:
        out.append(struct.pack(">i", len(tag.value))); out.append(bytes(tag.value))
    elif tag.type == STRING:
        out.append(struct.pack(">H", len(tag.value))); out.append(tag.value)
    elif tag.type == LIST:
        child, items = tag.value
        if any(item.type != child for item in items):
            raise NbtError("Mixed NBT list")
        out.append(struct.pack(">Bi", child, len(items)))
        for item in items:
            _write(item, out)
    elif tag.type == COMPOUND:
        for name, child in tag.value.items():
            encoded = name.encode("utf-8")
            out.append(struct.pack(">BH", child.type, len(encoded))); out.append(encoded); _write(child, out)
        out.append(b"\x00")
    elif tag.type in _ARRAY:
        out.append(struct.pack(">i", len(tag.value))); out.append(struct.pack(f">{len(tag.value)}{_ARRAY[tag.type][1]}", *tag.value))
    else:
        raise NbtError(f"Unknown NBT tag type {tag.type}")


def write(name: bytes, root: Tag) -> bytes:
    if root.type != COMPOUND:
        raise NbtError("NBT root must be a compound")
    out = [struct.pack(">BH", COMPOUND, len(name)), name]; _write(root, out)
    payload = b"".join(out)
    if len(payload) > MAX_DECODED:
        raise NbtError("Encoded NBT exceeds its bound")
    return payload


def plain(tag: Tag):
    """The untyped form that ``inspect_celestial_saved_data.NbtReader`` gives, for the existing oracle checks."""
    if tag.type == STRING:
        return tag.value.decode("utf-8")
    if tag.type == BYTES:
        return list(tag.value)
    if tag.type == LIST:
        return [plain(item) for item in tag.value[1]]
    if tag.type == COMPOUND:
        return {name: plain(child) for name, child in tag.value.items()}
    return tag.value if tag.type not in _ARRAY else list(tag.value)


def uuid_tag(value: uuid.UUID) -> Tag:
    """Minecraft's int-array UUID."""
    return Tag(INTS, list(struct.unpack(">4i", value.bytes)))


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def read_gzip(path: Path) -> tuple[bytes, Tag]:
    raw = path.read_bytes()
    if len(raw) > MAX_COMPRESSED:
        raise NbtError("Compressed SavedData exceeds its bound")
    with gzip.GzipFile(fileobj=io.BytesIO(raw)) as stream:
        payload = stream.read(MAX_DECODED + 1)
    return read(payload)


def write_gzip(path: Path, name: bytes, root: Tag) -> None:
    path.write_bytes(gzip.compress(write(name, root)))


def _slot(chunk_x: int, chunk_z: int) -> tuple[str, int]:
    return f"r.{chunk_x >> 5}.{chunk_z >> 5}.mca", (chunk_x & 31) + ((chunk_z & 31) << 5)


def _header(data: bytes, slot: int) -> tuple[int, int, int, int]:
    if len(data) < 2 * SECTOR or len(data) % SECTOR:
        raise NbtError("Region file is short or not sector aligned")
    location = struct.unpack_from(">I", data, slot * 4)[0]; sector, count = location >> 8, location & 255
    if sector < 2 or count == 0 or (sector + count) * SECTOR > len(data):
        raise NbtError("Missing or invalid Anvil slot")
    length, compression = struct.unpack_from(">IB", data, sector * SECTOR)
    if not 1 <= length or length + 4 > count * SECTOR:
        raise NbtError("Invalid Anvil record length")
    return sector, count, length, compression


def _expand(encoded: bytes, compression: int) -> bytes:
    if compression == 1:
        with gzip.GzipFile(fileobj=io.BytesIO(encoded)) as stream:
            raw = stream.read(MAX_DECODED + 1)
    elif compression == 2:
        decoder = zlib.decompressobj(); raw = decoder.decompress(encoded, MAX_DECODED + 1)
        if not decoder.eof or decoder.unused_data:
            raise NbtError("Invalid zlib Anvil record")
    elif compression == 3:
        raw = encoded
    else:
        raise NbtError("Unsupported Anvil compression")
    if len(raw) > MAX_DECODED:
        raise NbtError("Decoded Anvil record exceeds 4 MiB")
    return raw


def chunk(directory: Path, chunk_x: int, chunk_z: int) -> tuple[Tag, dict]:
    """One chunk record, inline or vanilla's external ``c.x.z.mcc``; the info says which and how large."""
    name, slot = _slot(chunk_x, chunk_z)
    data = (directory / name).read_bytes()
    sector, count, length, compression = _header(data, slot)
    external = bool(compression & 128)
    if external:
        path = directory / f"c.{chunk_x}.{chunk_z}.mcc"
        if path.is_symlink() or not path.is_file() or path.stat().st_size > MAX_COMPRESSED:
            raise NbtError("Missing or oversized external Anvil record")
        encoded = path.read_bytes()
    else:
        encoded = data[sector * SECTOR + 5:sector * SECTOR + 4 + length]
    raw = _expand(encoded, compression & 127)
    return read(raw)[1], dict(external=external, compressed=len(encoded), decoded=len(raw), sectors=count,
                              compression=compression & 127)


def write_chunk(directory: Path, chunk_x: int, chunk_z: int, root: Tag) -> None:
    """Rewrites one inline zlib record in its own sectors, or appends it; external records are never written."""
    name, slot = _slot(chunk_x, chunk_z); path = directory / name
    data = bytearray(path.read_bytes())
    sector, count, _, compression = _header(bytes(data), slot)
    if compression != 2:
        raise NbtError("Only inline zlib Anvil records are rewritten")
    encoded = zlib.compress(write(b"", root))
    record = struct.pack(">IB", len(encoded) + 1, 2) + encoded
    needed = -(-len(record) // SECTOR)
    if needed > 255:
        raise NbtError("A rewritten record would need an external file")
    if needed > count:
        sector, count = len(data) // SECTOR, needed
        data.extend(b"\x00" * needed * SECTOR)
        struct.pack_into(">I", data, slot * 4, sector << 8 | needed)
    data[sector * SECTOR:(sector + count) * SECTOR] = record + b"\x00" * (count * SECTOR - len(record))
    path.write_bytes(bytes(data))


def compound(tag: Tag, *path: str) -> Tag:
    for key in path:
        if tag.type != COMPOUND or key not in tag.value:
            raise NbtError("Missing NBT path " + "/".join(path))
        tag = tag.value[key]
    return tag


def block_entity(root: Tag, x: int, y: int, z: int) -> tuple[list, int]:
    """The chunk's block-entity list and the index of the one entry at x, y, z."""
    entries = compound(root, "block_entities").value[1]
    found = [index for index, entry in enumerate(entries)
             if [compound(entry, key).value for key in ("x", "y", "z")] == [x, y, z]]
    if len(found) != 1:
        raise NbtError("Missing or duplicated block entity")
    return entries, found[0]


def _states(root: Tag, y: int) -> tuple[list, list]:
    sections = [section for section in compound(root, "sections").value[1] if compound(section, "Y").value == y >> 4]
    if len(sections) != 1:
        raise NbtError("Missing or duplicated chunk section")
    states = compound(sections[0], "block_states")
    palette = compound(states, "palette").value[1]
    if "data" not in states.value or not 2 <= len(palette) <= 16 or len(states.value["data"].value) != 256:
        raise NbtError("Fixture section is not a 4-bit paletted section")
    return [plain(entry)["Name"] for entry in palette], states.value["data"].value


def block(root: Tag, x: int, y: int, z: int) -> str:
    names, longs = _states(root, y); index = (y & 15) << 8 | (z & 15) << 4 | x & 15
    return names[longs[index >> 4] >> ((index & 15) * 4) & 15]


def set_block(root: Tag, x: int, y: int, z: int, name: str) -> None:
    """Sets one 4-bit palette entry to an existing palette state; the palette itself never changes."""
    names, longs = _states(root, y)
    if names.count(name) != 1:
        raise NbtError("Target state is not exactly one palette entry")
    index = (y & 15) << 8 | (z & 15) << 4 | x & 15; shift = (index & 15) * 4
    value = longs[index >> 4] & 0xFFFFFFFFFFFFFFFF
    value = value & ~(15 << shift) | names.index(name) << shift
    longs[index >> 4] = value - (1 << 64) if value >= 1 << 63 else value


# W107C cut R3's offline fixture states (R36 item 2). Each edit touches one file of the stopped disposable copy, keeps the
# original bytes and reports before and after SHA-256; anything unexpected refuses the edit rather than guessing.
LEDGER_FILE = "advancedrocketrycommunity_resource_escrow.dat"
CAPTURE = Path("arce_w107c_fixture") / "ledger-generation-1.dat"
LOADER_ID = "advancedrocketrycommunity:fluid_fuel_loader"
LOADER_AT = (66, 100, 64)
RELOCATED_AT = (68, 100, 64)
R3_EDITS = ("R3_LEDGER_MISSING", "R3_LEDGER_REPLACED", "R3_LEDGER_BEHIND", "R3_ENDPOINT_FOREIGN",
            "R3_ENDPOINT_MISSING", "R3_ENDPOINT_OTHER", "R3_ENDPOINT_RELOCATED")


def _require(ok: bool, message: str) -> None:
    if not ok:
        raise NbtError(message)


def r3_edit(world: Path, name: str, keep: Path) -> dict:
    """Applies one R3 state to the stopped copy at ``world``; ``keep`` must be new and receives the original bytes."""
    _require(name in R3_EDITS, "Unknown R3 edit")
    keep.mkdir()
    if name.startswith("R3_LEDGER_"):
        return dict(cut=name, edits=[_ledger_edit(world, name, keep)])
    directory = world / "region"; path = directory / "r.0.0.mca"
    before = path.read_bytes(); (keep / "r.0.0.mca").write_bytes(before)
    root, info = chunk(directory, 4, 4)
    _require(not info["external"], "R3 edits rewrite only inline records")
    entries, index = block_entity(root, *LOADER_AT)
    _require(plain(compound(entries[index], "id")) == LOADER_ID and block(root, *LOADER_AT) == LOADER_ID, "No fixture loader at 66,100,64")
    loader = compound(entries[index], "arce_fluid_fuel_loader")
    if name == "R3_ENDPOINT_FOREIGN":
        markers = compound(loader, "resource_escrow", "markers").value[1]
        _require(len(markers) == 1, "Fixture loader needs exactly one marker")
        foreign = uuid.uuid4(); markers[0].value["op_id"] = uuid_tag(foreign); change = f"marker op_id -> {foreign}"
    elif name == "R3_ENDPOINT_OTHER":
        amount = compound(loader, "tank", "Amount")
        _require(amount.type == INT and amount.value == 4000, "Fixture loader tank is not the applied 4,000 mB")
        amount.value = 3000; change = "tank Amount 4000 -> 3000"
    elif name == "R3_ENDPOINT_MISSING":
        set_block(root, *LOADER_AT, "minecraft:air"); entries.pop(index); change = "loader block and block entity removed"
    else:
        _require(block(root, *RELOCATED_AT) == "minecraft:air" and not any(
            [compound(entry, key).value for key in ("x", "y", "z")] == list(RELOCATED_AT) for entry in entries), "Relocation target is occupied")
        set_block(root, *RELOCATED_AT, LOADER_ID); set_block(root, *LOADER_AT, "minecraft:air")
        compound(entries[index], "x").value = RELOCATED_AT[0]; change = "loader block and root moved from x 66 to x 68"
    write_chunk(directory, 4, 4, root)
    after = path.read_bytes()
    _require(chunk(directory, 4, 4)[0] == root, "The rewritten record does not read back")
    return dict(cut=name, edits=[dict(file="region/r.0.0.mca", chunk="4,4", change=change,
                                      before_sha256=digest(before), after_sha256=digest(after))])


def _ledger_edit(world: Path, name: str, keep: Path) -> dict:
    target = world / "data" / LEDGER_FILE
    _require(target.is_file() and not target.is_symlink(), "Stopped copy has no ledger file")
    before = target.read_bytes(); (keep / LEDGER_FILE).write_bytes(before)
    entry = dict(file="data/" + LEDGER_FILE, before_sha256=digest(before))
    if name == "R3_LEDGER_MISSING":
        target.unlink(); entry.update(after_sha256=None, change="ledger file removed")
        return entry
    captured_path = world / CAPTURE
    _require(captured_path.is_file() and not captured_path.is_symlink(), "No generation-1 ledger was captured in this run")
    captured = captured_path.read_bytes(); outer_name, outer = read_gzip(captured_path); data = compound(outer, "data")
    _require(compound(data, "writer_generation").value == 1 and compound(data, "issued_through").value == 0
             and not compound(data, "records").value[1], "The captured ledger is not the empty creation baseline")
    current = compound(read_gzip(target)[1], "data")
    _require(compound(current, "ledger_id") == compound(data, "ledger_id") and compound(current, "issued_through").value >= 1,
             "The stopped ledger is not this run's L1 generation")
    if name == "R3_LEDGER_BEHIND":
        after = captured; entry.update(change="the generation-1 copy captured in this run put back")
    else:
        replacement = uuid.uuid4(); data.value["ledger_id"] = uuid_tag(replacement)
        after = gzip.compress(write(outer_name, outer))
        entry.update(change="the generation-1 copy with another ledger_id", replacement_ledger_id=str(replacement))
    target.write_bytes(after)
    entry.update(after_sha256=digest(after), captured_sha256=digest(captured))
    return entry
