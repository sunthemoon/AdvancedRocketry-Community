"""Narrow X03 offline copier. No in-place repair, resource reconstruction or runtime authority.

Plan on a verified complete backup after a qualifying candidate start. Apply requires
the plan digest; census checks a stopped copy. Runtime clock allowances are deliberately
empty until X03-B supplies reviewed dry runs. This is not yet an approved operator tool.
"""
from __future__ import annotations

import argparse
import copy
from contextlib import ExitStack, contextmanager
import gzip
import io
import json
import math
import os
from pathlib import Path
import platform
import re
import shutil
import signal
import stat
import struct
import sys
import tempfile
import uuid
import zlib

import loader_cut_nbt as nbt

MAX_FILES = 100_000
MAX_BYTES = 256 * 1024**3
MAX_LEVELS = 64
MAX_REGIONS = 4096
MAX_OBJECTS = 1024
MAX_PASSENGER_DEPTH = 8
MAX_REPORT = 32 * 1024**2
MAX_LOG = 64 * 1024**2
MAX_REGION_BYTES = 64 * 1024**2
MAX_CENSUS_BYTES = 256 * 1024**2
JOURNAL = 'data/advancedrocketrycommunity_rocket_transfers.dat'
LEDGER = 'data/advancedrocketrycommunity_resource_escrow.dat'
LEGACY_RECORD_SCHEMA = 2  # Retained W142 R74 FF_REBUILD_SOURCE journal, not the root schema.
PROFILE_DISABLED = 'accounting-disabled'
PROFILE_ENABLED = 'accounting-enabled'
# Independent of per-record versions. Tests check the Java declaration and constants.
CURRENT_ROOTS = {
    'celestial': (2, 'v0.9.0-beta'),
    'rocket_transactions': (2, 'v0.9.0-beta'),
    'rocket_transfers': (3, 'v1.8.0-first-moon-landing'),
    'stations': (5, 'v1.8.0-station-first-warp'),
    'satellite_missions': (4, 'v1.8.0-ground-surveys'),
    'endgame': (1, 'v1.7.0-endgame'),
    'resource_escrow': (2, 'v1.8.0-resource-escrow'),
    'classic_progression': (1, 'v1.8.0-classic-progression'),
    'rocket_rejoins': (1, 'v1.8.0-rocket-rejoins'),
    'bay_receipts': (1, 'v1.8.0-bay-receipts'),
}
ROOT_REQUIRED = {
    'celestial': {'bodies': nbt.LIST},
    'rocket_transactions': {'transactions': nbt.LIST},
    'rocket_transfers': {'transfers': nbt.LIST, 'classic_first_moon_landing': nbt.COMPOUND},
    'stations': {'stations': nbt.LIST, 'reservations': nbt.LIST, 'warp_energy': nbt.LIST,
                 'orbital_clock': nbt.COMPOUND, 'classic_first_warp': nbt.COMPOUND},
    'satellite_missions': {**{k: nbt.LIST for k in ('satellites', 'missions', 'research_accounts', 'instances', 'ground_surveys')},
                           'clock': nbt.COMPOUND, 'save_epoch': nbt.LONG},
    'endgame': {**{k: nbt.LIST for k in ('endpoints', 'transits', 'elevator_pairs', 'zones')},
                'dispatched_through': nbt.LONGS, 'save_epoch': nbt.LONG},
    'resource_escrow': {**{k: nbt.LIST for k in ('records', 'floors', 'tombstones', 'rocket_authorities', 'armed_flights')},
                        'ledger_id': nbt.INTS, 'issued_through': nbt.LONG, 'writer_generation': nbt.LONG,
                        'issued_launch_serial': nbt.LONG},
    'classic_progression': {'first_moon_landing': nbt.COMPOUND, 'first_station_warp': nbt.COMPOUND},
    'rocket_rejoins': {'next_sequence': nbt.LONG, 'batches': nbt.LIST, 'entries': nbt.LIST},
    'bay_receipts': {'store_id': nbt.INTS, 'issued_through': nbt.LONG, 'writer_generation': nbt.LONG, 'receipts': nbt.LIST},
}
# No unreviewed runtime normalization; a false refusal is safer than data loss.
ENTITY_CLOCK_FIELDS = ()
ROOT_BOOKKEEPING_FIELDS = ()
REGION = re.compile(r'r\.(-?\d+)\.(-?\d+)\.mca\Z')
HEX = re.compile(r'[0-9a-f]{64}\Z')


class Refusal(ValueError):
    def __init__(self, reasons):
        if isinstance(reasons, str):
            reasons = [reasons]
        self.reasons = list(dict.fromkeys(reasons))[:32]
        super().__init__('; '.join(self.reasons))


def require(ok, reason):
    if not ok:
        raise Refusal(reason)


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=True, allow_nan=False).encode('ascii')


def read_json(path):
    require(path.stat().st_size <= MAX_REPORT, 'REPORT_LIMIT')
    def object_pairs(pairs):
        require(len({key for key, _ in pairs}) == len(pairs), 'AMBIGUOUS_JSON')
        return dict(pairs)
    def constant(text):
        raise Refusal('INVALID_JSON_NUMBER')
    document = json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=object_pairs, parse_constant=constant)
    pending = [(document, 0)]
    nodes = 0
    while pending:
        child, depth = pending.pop()
        nodes += 1
        require(nodes <= MAX_FILES * 8 + nbt.MAX_TAGS and depth <= nbt.MAX_DEPTH, 'REPORT_LIMIT')
        if isinstance(child, (dict, list)):
            pending.extend((v, depth + 1) for v in (child.values() if isinstance(child, dict) else child))
    return document


def typed(tag):
    """Byte comparison includes compound order, scalar types and float bit patterns."""
    return nbt.write(b'', nbt.Tag(nbt.COMPOUND, {'v': tag}))


def at(tag, *path, kind=None):
    result = nbt.compound(tag, *path)
    require(kind is None or result.type == kind, 'INVALID_NBT_TYPE:' + '/'.join(path))
    return result


def value(tag, *path, kind=None):
    return nbt.plain(at(tag, *path, kind=kind))


def items(tag, *path):
    result = at(tag, *path, kind=nbt.LIST)
    child, entries = result.value
    require(child == nbt.COMPOUND if entries else child in (0, nbt.COMPOUND), 'INVALID_LIST')
    return entries


def uid(tag, *path):
    raw = at(tag, *path, kind=nbt.INTS).value
    require(len(raw) == 4, 'INVALID_UUID')
    return str(uuid.UUID(bytes=struct.pack('>4i', *raw)))


def strict_path(path, exists=True):
    require('..' not in Path(path).parts, 'PATH_TRAVERSAL')
    path = Path(os.path.abspath(path))
    for part in [*reversed(path.parents), path]:
        if not part.exists() and not part.is_symlink():
            continue
        info = part.lstat()
        require(not stat.S_ISLNK(info.st_mode) and not getattr(info, 'st_file_attributes', 0) & 0x400,
                'LINKED_PATH')
    require(not exists or path.exists(), 'INPUT_MISSING')
    return path.resolve(strict=exists)


def disjoint(*paths):
    paths = [strict_path(p, exists=False) for p in paths]
    for i, left in enumerate(paths):
        for right in paths[i + 1:]:
            require(left != right and left not in right.parents and right not in left.parents, 'PATH_OVERLAP')
            require(not (left.exists() and right.exists() and os.path.samefile(left, right)), 'PATH_ALIAS')


@contextmanager
def world_lock(world):
    """One handle for the Java-compatible record lock, all reads and the manifest hash."""
    path = strict_path(Path(world) / 'session.lock')
    require(path.is_file() and path.stat().st_nlink == 1, 'LINKED_PATH')
    stream = path.open('r+b', buffering=0)
    try:
        try:
            if os.name == 'nt':
                import msvcrt
                msvcrt.locking(stream.fileno(), msvcrt.LK_NBLCK, 0x7fffffff)
            else:
                import fcntl
                fcntl.lockf(stream, fcntl.LOCK_EX | fcntl.LOCK_NB, 0, 0)
        except OSError as error:
            raise Refusal('WORLD_LOCKED') from error
        yield stream
    finally:
        stream.close()


@contextmanager
def report_lock(parent):
    path = strict_path(parent) / '.x03-repair.lock'
    try:
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    except FileExistsError as error:
        raise Refusal('WORLD_LOCKED') from error
    try:
        os.close(fd)
        yield
    finally:
        path.unlink()


def file_digest(path, handle=None):
    import hashlib
    info = strict_path(path).stat()
    require(stat.S_ISREG(info.st_mode) and info.st_nlink == 1, 'NONREGULAR_OR_SHARED_FILE')
    digest = hashlib.sha256()
    def consume(stream):
        stream.seek(0)
        while block := stream.read(1024**2):
            digest.update(block)
    if handle is None:
        with path.open('rb') as stream:
            consume(stream)
    else:
        consume(handle)
    return digest.hexdigest()


def manifest(world, lock):
    world = strict_path(world)
    result = {}
    total = 0
    for directory, dirs, files in os.walk(world, followlinks=False):
        for name in dirs + files:
            strict_path(Path(directory) / name)
        for name in sorted(files):
            path = Path(directory) / name
            info = path.stat()
            require(stat.S_ISREG(info.st_mode) and info.st_nlink == 1, 'NONREGULAR_OR_SHARED_FILE')
            total += info.st_size
            require(len(result) < MAX_FILES and total <= MAX_BYTES, 'WORK_LIMIT')
            relative = path.relative_to(world).as_posix()
            result[relative] = dict(size=info.st_size, sha256=file_digest(path, lock if relative == 'session.lock' else None))
    return dict(sorted(result.items()))


def space(files, output_parent):
    size = sum(p['size'] for p in files.values())
    require(shutil.disk_usage(output_parent).free >= 3 * size + 10 * 1024**3, 'INSUFFICIENT_SPACE')
    for volume in ('C:/', 'D:/') if os.name == 'nt' else (tempfile.gettempdir(),):
        require(shutil.disk_usage(volume).free >= 10 * 1024**3, 'INSUFFICIENT_SPACE')


def read_gzip(path):
    require(path.stat().st_size <= nbt.MAX_COMPRESSED, 'DECODE_LIMIT')
    raw = path.read_bytes()
    decoder = zlib.decompressobj(16 + zlib.MAX_WBITS)
    payload = decoder.decompress(raw, nbt.MAX_DECODED + 1)
    require(decoder.eof and not decoder.unused_data and len(payload) <= nbt.MAX_DECODED, 'CORRUPT_GZIP')
    name, root = nbt.read(payload)
    require(nbt.write(name, root) == payload, 'NONCANONICAL_NBT')
    return name, root


def compressed(payload):
    output = io.BytesIO()
    with gzip.GzipFile(filename='', mode='wb', fileobj=output, compresslevel=9, mtime=0) as stream:
        stream.write(payload)
    return output.getvalue()


def journal_candidate(name, root, transfer):
    candidate = copy.deepcopy(root)
    entries = items(candidate, 'data', 'transfers')
    found = [i for i, record in enumerate(entries) if uid(record, 'transfer_id') == transfer]
    require(len(found) == 1, 'TRANSFER_MISSING_OR_DUPLICATE')
    entries.pop(found[0])
    if not entries:
        at(candidate, 'data', 'transfers').value = (0, [])
    payload = nbt.write(name, candidate)
    require(nbt.write(*nbt.read(payload)) == payload, 'SERIALIZATION_DIFFERS')
    return payload


def toolchain():
    return dict(python=platform.python_version(), zlib=zlib.ZLIB_RUNTIME_VERSION,
                script=file_digest(Path(__file__)), nbt=file_digest(Path(nbt.__file__)))


def force_directory(path):
    # Windows rename is atomic on the same volume; directory fsync is not exposed there.
    if os.name != 'nt':
        fd = os.open(path, os.O_RDONLY | os.O_DIRECTORY)
        try:
            os.fsync(fd)
        finally:
            os.close(fd)


def write_journal(path, payload):
    """Only used in an unpublished, disposable complete copy."""
    stage = path.with_name(path.name + '.x03-stage')
    raw = compressed(payload)
    try:
        with stage.open('xb') as stream:
            stream.write(raw)
            stream.flush()
            os.fsync(stream.fileno())
        require(stage.read_bytes() == raw and nbt.write(*read_gzip(stage)) == payload, 'WRITE_READBACK_DIFFERS')
        os.replace(stage, path)
        force_directory(path.parent)
        require(path.read_bytes() == raw and nbt.write(*read_gzip(path)) == payload, 'WRITE_READBACK_DIFFERS')
    finally:
        if stage.exists():
            stage.unlink()


def regions(world, files):
    """Decode every occupied header slot, never infer absence from a failed decode."""
    count = 0
    levels = {''}
    chunks = []
    decoded = 0
    for relative in files:
        path = Path(relative)
        if path.parent.name not in ('region', 'entities', 'poi') or path.suffix != '.mca':
            continue
        require(files[relative]['size'] <= MAX_REGION_BYTES, 'DECODE_LIMIT')
        match = REGION.fullmatch(path.name)
        require(match is not None, 'UNKNOWN_REGION_NAME')
        count += 1
        level = path.parent.parent.as_posix()
        levels.add(level)
        require(count <= MAX_REGIONS and len(levels - {'.', ''}) + 1 <= MAX_LEVELS, 'WORK_LIMIT')
        with (world / path).open('rb') as stream:
            header = stream.read(8192)
        require(len(header) == 8192, 'CORRUPT_REGION')
        rx, rz = map(int, match.groups())
        used = set()
        for slot in range(1024):
            location = struct.unpack_from('>I', header, slot * 4)[0]
            if not location:
                continue
            sector, sectors = location >> 8, location & 255
            require(sector >= 2 and sectors > 0 and not used.intersection(range(sector, sector + sectors)), 'CORRUPT_REGION')
            used.update(range(sector, sector + sectors))
            x, z = rx * 32 + slot % 32, rz * 32 + slot // 32
            root, info = nbt.chunk(world / path.parent, x, z)
            decoded += info['decoded']
            require(decoded <= MAX_CENSUS_BYTES, 'DECODE_LIMIT')
            require(not (path.parent.name == 'entities' and info['external']), 'EXTERNAL_ENTITY_RECORD')
            chunks.append((level, path.parent.name, x, z, root, relative))
    return chunks


def entities(root, depth=0):
    require(depth <= MAX_PASSENGER_DEPTH, 'PASSENGER_DEPTH')
    entries = items(root, 'Entities' if depth == 0 else 'Passengers')
    require(len(entries) <= MAX_OBJECTS, 'WORK_LIMIT')
    for entity in entries:
        yield entity
        if 'Passengers' in entity.value:
            yield from entities(entity, depth + 1)


def dimension(level):
    if level in ('.', ''):
        return 'minecraft:overworld'
    if level in ('DIM-1', 'DIM1'):
        return 'minecraft:the_nether' if level == 'DIM-1' else 'minecraft:the_end'
    parts = Path(level).parts
    require(len(parts) >= 3 and parts[0] == 'dimensions', 'UNKNOWN_LEVEL')
    return parts[1] + ':' + '/'.join(parts[2:])


def roots(world, files, profile):
    require(profile in (PROFILE_DISABLED, PROFILE_ENABLED), 'UNKNOWN_PROFILE')
    table = dict(CURRENT_ROOTS)
    if profile == PROFILE_ENABLED:
        table['resource_escrow'] = (3, 'v1.8.0-resource-escrow-fluid-accounting')
    result = {}
    reasons = []
    for relative in files:
        path = Path(relative)
        if path.parent.name != 'data' or not path.name.startswith('advancedrocketrycommunity_') or path.suffix != '.dat':
            continue
        if 'advancedrocketrycommunity-backups' in path.parts:
            continue
        key = path.stem.removeprefix('advancedrocketrycommunity_')
        require(key in table, 'UNKNOWN_MANAGED_ROOT:' + relative)
        require(path.parent.as_posix() == 'data', 'UNKNOWN_MANAGED_ROOT:' + relative)
        _, root = read_gzip(world / relative)
        data = at(root, 'data', kind=nbt.COMPOUND)
        if (value(data, 'schema_version', kind=nbt.INT), value(data, 'format_epoch', kind=nbt.STRING)) != table[key]:
            reasons.append('ROOT_NOT_CURRENT:' + key)
        for field, kind in ROOT_REQUIRED[key].items():
            at(data, field, kind=kind)
        result[relative] = root
    require(not reasons, reasons)
    require(JOURNAL in result and LEDGER in result, 'ROOT_MISSING')
    return result


def reference(tag, identities):
    if tag.type == nbt.INTS and len(tag.value) == 4:
        return str(uuid.UUID(bytes=struct.pack('>4i', *tag.value))) in identities
    if tag.type == nbt.STRING:
        return any(identity.encode() in tag.value.lower() for identity in identities)
    if tag.type == nbt.BYTES and len(tag.value) == 16:
        return str(uuid.UUID(bytes=bytes(tag.value))) in identities
    if tag.type == nbt.LONGS and len(tag.value) == 2:
        return str(uuid.UUID(bytes=struct.pack('>2q', *tag.value))) in identities
    if tag.type == nbt.COMPOUND:
        return any(reference(t, identities) for t in tag.value.values())
    if tag.type == nbt.LIST:
        return any(reference(t, identities) for t in tag.value[1])
    return False


def spatial_reference(tag, positions, dim=None):
    """Coordinate-only claims also compete; absent dimension context is not proof of disjointness."""
    if tag.type == nbt.COMPOUND:
        fields = tag.value
        for key in ('dimension', 'level', 'source_dimension', 'current_dimension'):
            if key in fields and fields[key].type == nbt.STRING:
                dim = nbt.plain(fields[key])
                break
        relevant = [p[1:] for p in positions if dim is None or p[0] == dim]
        if all(k in fields and fields[k].type == nbt.INT for k in ('x', 'y', 'z')):
            if tuple(fields[k].value for k in ('x', 'y', 'z')) in relevant:
                return True
        if all(k in fields and fields[k].type == nbt.INTS and len(fields[k].value) == 3 for k in ('minimum', 'maximum')):
            if any(all(fields['minimum'].value[i] <= p[i] <= fields['maximum'].value[i] for i in range(3)) for p in relevant):
                return True
        return any(spatial_reference(child, positions, dim) for child in fields.values())
    if tag.type == nbt.INTS and len(tag.value) == 3:
        return any(tuple(tag.value) == p[1:] and (dim is None or dim == p[0]) for p in positions)
    if tag.type == nbt.LIST:
        return any(spatial_reference(child, positions, dim) for child in tag.value[1])
    return False


def footprint(snapshot):
    dim = value(snapshot, 'source_dimension', kind=nbt.STRING)
    origin = value(snapshot, 'source_origin', kind=nbt.INTS)
    require(len(origin) == 3 and all(abs(c) <= 30_000_000 for c in origin), 'INVALID_ORIGIN')
    blocks = items(snapshot, 'relative_blocks')
    require(0 < len(blocks) <= MAX_OBJECTS, 'WORK_LIMIT')
    positions = set()
    for block in blocks:
        relative = value(block, 'position', kind=nbt.INTS)
        require(len(relative) == 3, 'INVALID_ORIGIN')
        positions.add((dim, *(origin[i] + relative[i] for i in range(3))))
    require(len(positions) == len(blocks), 'DUPLICATE_BLOCK')
    return positions


def block_state(root, x, y, z):
    """Vanilla non-crossing paletted containers, including constant and 5..12-bit palettes."""
    sections = [s for s in items(root, 'sections') if value(s, 'Y') == y // 16]
    require(len(sections) == 1, 'TERRAIN_NOT_PROVEN')
    states = at(sections[0], 'block_states', kind=nbt.COMPOUND)
    palette = items(states, 'palette')
    require(0 < len(palette) <= 4096, 'INVALID_PALETTE')
    index = 0
    if len(palette) > 1:
        bits = max(4, (len(palette) - 1).bit_length())
        per = 64 // bits
        data = at(states, 'data', kind=nbt.LONGS).value
        require(len(data) == math.ceil(4096 / per), 'INVALID_PALETTE')
        cell = (y & 15) * 256 + (z & 15) * 16 + (x & 15)
        index = (data[cell // per] >> ((cell % per) * bits)) & ((1 << bits) - 1)
    require(index < len(palette), 'INVALID_PALETTE')
    return value(palette[index], 'Name', kind=nbt.STRING)


def cargo(snapshot, amount):
    blocks = items(snapshot, 'relative_blocks')
    payloads = [b for b in blocks if 'block_entity' in b.value]
    require(len(payloads) == 1, 'UNSUPPORTED_CARGO')
    payload = at(payloads[0], 'block_entity', kind=nbt.COMPOUND)
    require(value(payload, 'adapter', kind=nbt.STRING) == 'advancedrocketrycommunity:pressurized_tank_v1', 'MISSING_ADAPTER')
    fluid = at(payload, 'data', 'tank', 'fluid', kind=nbt.COMPOUND)
    require(set(fluid.value) <= {'FluidName', 'Amount', 'Tag'}, 'UNSUPPORTED_FLUID_FIELDS')
    if 'Tag' in fluid.value:
        at(fluid, 'Tag', kind=nbt.COMPOUND)
    require(value(fluid, 'FluidName', kind=nbt.STRING) == 'minecraft:water'
            and value(fluid, 'Amount', kind=nbt.INT) == amount, 'RESOURCE_DIFFERS')
    return fluid


def first_difference(left, right, path=''):
    if typed(left) == typed(right):
        return None
    if left.type == right.type == nbt.COMPOUND and list(left.value) == list(right.value):
        for key in left.value:
            diff = first_difference(left.value[key], right.value[key], path + '/' + key)
            if diff:
                return diff
    if left.type == right.type == nbt.LIST and left.value[0] == right.value[0] and len(left.value[1]) == len(right.value[1]):
        for i, (a, b) in enumerate(zip(left.value[1], right.value[1])):
            diff = first_difference(a, b, path + '/' + str(i))
            if diff:
                return diff
    return path or '/'


def snapshot_rule(before, current):
    cargo(before, 5000)
    cargo(current, 4000)
    expected = copy.deepcopy(before)
    cargo(expected, 5000).value['Amount'].value = 4000
    old_hash = value(before, 'content_hash', kind=nbt.STRING)
    new_hash = value(current, 'content_hash', kind=nbt.STRING)
    require(HEX.fullmatch(old_hash) and HEX.fullmatch(new_hash) and old_hash != new_hash, 'INVALID_CONTENT_HASH')
    expected.value['content_hash'] = copy.deepcopy(at(current, 'content_hash'))
    difference = first_difference(expected, current)
    require(difference is None, 'SNAPSHOT_DIFFERS:' + str(difference))


def fence(log, transfer):
    require(log.stat().st_size <= MAX_LOG, 'LOG_LIMIT')
    matches = []
    with log.open('r', encoding='utf-8', errors='strict') as stream:
        for line in stream:
            if 'ARCE_TRANSFER_FENCED' not in line:
                continue
            match = re.search(r'ARCE_TRANSFER_FENCED transfer=([0-9a-f-]{36}) reason=([A-Z_]+) repair=offline-whole-world(?:\s|$)', line)
            require(match is not None, 'FENCE_EVIDENCE_MISSING')
            require(match[1] == transfer, 'OTHER_TRANSFER_FENCED')
            require(match[2] == 'FLOOR_CONFLICT', 'FENCE_REASON_DIFFERS')
            matches.append(line.rstrip('\r\n'))
    require(matches, 'FENCE_EVIDENCE_MISSING')
    return dict(sha256=file_digest(log), line=matches[0])


def inspect(world, files, profile, transfer, repaired=False, retained=None):
    """Full-file census. Independent stores are read, never modified."""
    managed = roots(world, files, profile)
    journal = at(managed[JOURNAL], 'data')
    transfers = items(journal, 'transfers')
    matches = [t for t in transfers if uid(t, 'transfer_id') == transfer]
    require(len(matches) == (0 if repaired else 1), 'TRANSFER_MISSING_OR_DUPLICATE')
    record = retained if repaired else matches[0]
    require(record is not None, 'RETAINED_RECORD_MISSING')
    reasons = []
    def check(ok, reason):
        if not ok and reason not in reasons and len(reasons) < 32:
            reasons.append(reason)
    check('native_transfer' not in record.value and value(record, 'schema_version') < 3, 'GOVERNED_RECORD')
    check(value(record, 'schema_version', kind=nbt.INT) == LEGACY_RECORD_SCHEMA, 'UNSUPPORTED_RECORD_VERSION')
    check(value(record, 'phase', kind=nbt.STRING) == 'PREPARED', 'PHASE_NOT_PREPARED')
    check('destination_entity_id' not in record.value, 'DESTINATION_ENTITY')
    check(bool(HEX.fullmatch(value(record, 'checksum', kind=nbt.STRING))), 'INVALID_CHECKSUM')
    logical, physical, owner = (uid(record, k) for k in ('logical_rocket_id', 'source_entity_id', 'owner_id'))
    source, destination = (at(record, k, kind=nbt.COMPOUND) for k in ('source_snapshot', 'destination_snapshot'))
    snapshot_id = value(source, 'snapshot_id', kind=nbt.STRING)
    require(str(uuid.UUID(snapshot_id)) == snapshot_id, 'INVALID_UUID')
    identities = {transfer, logical, physical, snapshot_id, value(destination, 'snapshot_id', kind=nbt.STRING)}
    for other in transfers:
        check(other is record or not reference(other, identities), 'OTHER_TRANSFER')
    chunks = regions(world, files)
    rockets, devices, terrain = [], [], {}
    for level, kind, x, z, root, relative in chunks:
        dim = dimension(level)
        if kind == 'entities':
            for index, entity in enumerate(entities(root)):
                require(index < MAX_OBJECTS, 'WORK_LIMIT')
                if 'RocketEntityData' in entity.value:
                    data = at(entity, 'RocketEntityData')
                    if (uid(entity, 'UUID') == physical or uid(data, 'assembly_transaction_id') == logical
                            or value(data, 'snapshot', 'snapshot_id') in identities):
                        rockets.append((dim, entity))
                elif reference(entity, identities):
                    check(False, 'COMPETING_ENTITY_AUTHORITY')
        if kind == 'region':
            terrain[(dim, x, z)] = root
            block_entities = items(root, 'block_entities')
            require(len(block_entities) <= MAX_OBJECTS, 'WORK_LIMIT')
            for entity in block_entities:
                if 'arce_rocket_fluid_cargo' in entity.value:
                    data = at(entity, 'arce_rocket_fluid_cargo')
                    if reference(data, identities):
                        devices.append((dim, entity))
                else:
                    check(not reference(entity, identities), 'COMPETING_BLOCK_AUTHORITY')
    check(len(rockets) == 1, 'SECOND_PHYSICAL_ROCKET' if len(rockets) > 1 else 'ROCKET_MISSING')
    require(rockets, reasons + ['ROCKET_MISSING'])
    dim, entity = rockets[0]
    rocket = at(entity, 'RocketEntityData')
    check(value(rocket, 'schema_version', kind=nbt.INT) == 3
          and value(entity, 'id') == 'advancedrocketrycommunity:rocket', 'ROCKET_SCHEMA_DIFFERS')
    check(uid(entity, 'UUID') == physical and uid(rocket, 'assembly_transaction_id') == logical
          and uid(rocket, 'owner_id') == owner, 'ROCKET_IDENTITY_DIFFERS')
    try:
        snapshot_rule(source, at(rocket, 'snapshot'))
    except Refusal as error:
        reasons.extend(error.reasons)
    origin = value(source, 'source_origin')
    check(dim == value(source, 'source_dimension') and value(entity, 'Pos') == [origin[0] + .5, float(origin[1]), origin[2] + .5], 'ROCKET_RELOCATED')
    flight = at(rocket, 'flight_data')
    check(value(flight, 'state') == 'FUELED' and 'plan' not in flight.value and 'active_transfer_id' not in flight.value, 'ACTIVE_FLIGHT')
    check(value(flight, 'current_dimension') == dim and value(flight, 'current_origin') == origin
          and uid(flight, 'logical_rocket_id') == logical, 'FLIGHT_IDENTITY_DIFFERS')
    check(value(entity, 'Motion') == [0.0, 0.0, 0.0], 'ROCKET_MOVING')
    for f in (flight, at(record, 'source_flight'), at(record, 'destination_flight')):
        check(not items(f, 'passengers', 'assignments'), 'PASSENGER_ASSIGNMENT')
    check('Passengers' not in entity.value or not items(entity, 'Passengers'), 'PASSENGER_ASSIGNMENT')
    check(typed(at(flight, 'fuel')) == typed(at(record, 'source_flight', 'fuel')), 'PROPULSION_DIFFERS')
    fuel = at(flight, 'fuel')
    check(value(fuel, 'amount') == 1000 and value(fuel, 'propulsion', 'primary', 'amount') == 1000
          and value(fuel, 'propulsion', 'primary', 'fluid') == 'advancedrocketrycommunity:rocket_fuel', 'PROPULSION_DIFFERS')
    require(len(devices) == 1, reasons + ['DEVICE_MISSING_OR_DUPLICATE'])
    device_dim, device_entity = devices[0]
    device = at(device_entity, 'arce_rocket_fluid_cargo')
    device_id = uid(device, 'device')
    identities.add(device_id)
    check(value(device, 'schema_version') == 1 and value(device, 'role') == 'UNLOAD'
          and value(device_entity, 'id') == 'advancedrocketrycommunity:rocket_fluid_unloader', 'DEVICE_DIFFERS')
    check(uid(device, 'owner') == owner and uid(device, 'binding', 'logical_rocket') == logical
          and uid(device, 'binding', 'physical_rocket') == physical
          and value(device, 'binding', 'schema_version') == 1
          and value(device, 'binding', 'level') == dim
          and [value(device, 'binding', key) for key in ('x', 'y', 'z')] == origin, 'BINDING_DIFFERS')
    check(value(device, 'tank', 'Amount', kind=nbt.INT) == 1000
          and value(device, 'tank', 'FluidName') == 'minecraft:water', 'RESOURCE_DIFFERS')
    current_fluid = cargo(at(rocket, 'snapshot'), 4000)
    device_fluid = copy.deepcopy(at(device, 'tank'))
    device_fluid.value['Amount'].value = 4000
    check(typed(device_fluid) == typed(current_fluid), 'FLUID_TAG_DIFFERS')
    ledger = at(managed[LEDGER], 'data')
    ledger_id = uid(ledger, 'ledger_id')
    for endpoint in (rocket, device):
        escrow = at(endpoint, 'resource_escrow')
        check(value(escrow, 'schema_version', kind=nbt.INT) == 1
              and uid(escrow, 'ledger_id') == ledger_id and value(escrow, 'resource_epoch', kind=nbt.LONG) == 1
              and not items(escrow, 'markers'), 'ESCROW_DIFFERS')
    check(value(ledger, 'issued_launch_serial', kind=nbt.LONG) == 0, 'ARMED_AUTHORITY')
    for key in ('records', 'floors', 'tombstones', 'rocket_authorities', 'armed_flights'):
        for entry in items(ledger, key):
            check(not reference(entry, identities | {owner}), 'COMPETING_AUTHORITY:' + key)
    if profile == PROFILE_ENABLED:
        for entry in items(ledger, 'fluid_accounts'):
            check(not reference(entry, identities | {owner}), 'COMPETING_AUTHORITY:fluid_accounts')
    else:
        check('fluid_accounts' not in ledger.value, 'NONCANONICAL_SCHEMA')
    for root, excluded in ((ledger, set(ROOT_REQUIRED['resource_escrow']) | {'fluid_accounts'}),
                           (journal, {'transfers'})):
        remainder = nbt.Tag(nbt.COMPOUND, {k: v for k, v in root.value.items() if k not in excluded})
        check(not reference(remainder, identities), 'COMPETING_AUTHORITY:extension')
    for relative, root in managed.items():
        if relative not in (LEDGER, JOURNAL):
            check(not reference(root, identities), 'COMPETING_AUTHORITY:' + relative)
    # Unknown sidecars cannot silently become a second authority. Historical backups
    # are immutable evidence, not live managed stores.
    for relative in files:
        path = Path(relative)
        if relative in managed or 'advancedrocketrycommunity-backups' in path.parts or path.suffix not in ('.dat', '.json'):
            continue
        if path.suffix == '.dat':
            _, root = read_gzip(world / relative)
            check(not reference(root, identities), 'COMPETING_SIDECAR:' + relative)
        else:
            require(files[relative]['size'] <= MAX_REPORT, 'DECODE_LIMIT')
            text = canonical(read_json(world / relative)).decode('ascii')
            check(not any(i in text for i in identities), 'COMPETING_SIDECAR:' + relative)
    src, dst = footprint(source), footprint(destination)
    device_at = (device_dim, *(value(device_entity, k, kind=nbt.INT) for k in ('x', 'y', 'z')))
    positions = src | dst | {device_at}
    for other in transfers:
        check(other is record or not spatial_reference(other, positions), 'COMPETING_POSITION_AUTHORITY')
    for relative, root in managed.items():
        if relative not in (LEDGER, JOURNAL):
            check(not spatial_reference(root, positions), 'COMPETING_POSITION_AUTHORITY:' + relative)
    for key in ('records', 'floors', 'tombstones', 'rocket_authorities', 'armed_flights'):
        check(not spatial_reference(at(ledger, key), positions), 'COMPETING_POSITION_AUTHORITY:' + key)
    target_chunks = {(d, x // 16, z // 16) for d, x, y, z in src | {device_at}}
    check(not {(d, x // 16, z // 16) for d, x, y, z in dst}.intersection(target_chunks), 'DESTINATION_IN_TARGET_CHUNK')
    for position in src | dst:
        d, x, y, z = position
        chunk = terrain.get((d, x // 16, z // 16))
        require(chunk is not None, 'TERRAIN_NOT_PROVEN')
        check(block_state(chunk, x, y, z) == 'minecraft:air', 'FOOTPRINT_OCCUPIED')
    d, x, y, z = device_at
    require((d, x // 16, z // 16) in terrain, 'TERRAIN_NOT_PROVEN')
    check(block_state(terrain[(d, x // 16, z // 16)], x, y, z) == value(device_entity, 'id'), 'DEVICE_RELOCATED')
    # Detect the same device ID even when its binding was edited to hide it.
    all_devices = [be for _, kind, _, _, chunk, _ in chunks if kind == 'region'
                   for be in items(chunk, 'block_entities') if 'arce_rocket_fluid_cargo' in be.value
                   and uid(at(be, 'arce_rocket_fluid_cargo'), 'device') == device_id]
    check(len(all_devices) == 1, 'DEVICE_MISSING_OR_DUPLICATE')
    require(not reasons, reasons)
    # Reuse the existing bounded accounting reader rather than treating an orphan
    # abbreviated account row as disjoint simply because it lacks a rocket UUID.
    import escrow_accounting
    try:
        escrow_accounting.validate_root(nbt.plain(ledger), ledger.value.get('fluid_accounts'), fresh_fluid=False)
    except (ValueError, KeyError, TypeError, IndexError, StopIteration) as error:
        raise Refusal('INVALID_ACCOUNTING_ROOT') from error
    native_tag = current_fluid.value.get('Tag')
    return dict(record=record, entity=entity, device=device_entity, managed=managed,
                identity=dict(transfer=transfer, physical=physical, logical=logical, owner=owner,
                              snapshot=snapshot_id, device=device_id, ledger=ledger_id),
                native_tag='absent' if native_tag is None else nbt.digest(typed(native_tag)),
                amounts=dict(water_rocket=4000, water_device=1000, propulsion=1000))


def account(witness, observation):
    unknown = dict(version=1, status='UNKNOWN_BASELINE', water_current=[4000, 1000],
                   fluid='minecraft:water', native_tag=observation['native_tag'], offline_resource_delta=0)
    if witness is None:
        return unknown
    require(witness.stat().st_size <= MAX_REPORT, 'WITNESS_INVALID')
    document = read_json(witness)
    body = document.get('body')
    require(isinstance(body, dict) and document.get('sha256') == nbt.digest(canonical(body)), 'WITNESS_INVALID')
    require(body == dict(version=1, identity=observation['identity'], fluid='minecraft:water',
                         native_tag=observation['native_tag'], before=[5000, 0], after=[4000, 1000]), 'WITNESS_INVALID')
    unknown.update(status='CONFIRMED', discrepancy=0, witness_sha256=file_digest(witness),
                   witness=str(strict_path(witness)), baseline=body)
    return unknown


def publish_json(path, document):
    raw = canonical(document) + b'\n'
    require(len(raw) <= MAX_REPORT, 'REPORT_LIMIT')
    require(not path.exists(), 'OUTPUT_EXISTS')
    stage = path.with_name('.' + path.name + '.x03-' + uuid.uuid4().hex)
    created = published = False
    try:
        with stage.open('xb') as stream:
            created = True
            stream.write(raw)
            stream.flush()
            os.fsync(stream.fileno())
        # A hard link publishes a complete file atomically without replacing an
        # existing report on either platform. The private staging name is removed.
        os.link(stage, path)
        published = True
        stage.unlink()
        created = False
        force_directory(path.parent)
    except BaseException:
        if published:
            path.unlink()
        raise
    finally:
        if created and stage.exists():
            stage.unlink()


def plan(source, backup, candidate, log, report, transfer, profile, operator, reason, witness=None):
    source, backup, candidate, log = map(strict_path, (source, backup, candidate, log))
    report = strict_path(report, exists=False)
    require(bool(operator.strip()) and bool(reason.strip()) and len(operator) <= 160 and len(reason) <= 1000, 'OPERATOR_REQUIRED')
    require(str(uuid.UUID(transfer)) == transfer, 'INVALID_UUID')
    disjoint(source, backup, report.parent)
    with report_lock(report.parent), world_lock(source) as sl, world_lock(backup) as bl:
        before = manifest(backup, bl)
        require(manifest(source, sl) == before, 'BACKUP_UNVERIFIED')
        space(before, report.parent)
        observation = inspect(backup, before, profile, transfer)
        evidence = fence(log, transfer)
        name, root = read_gzip(backup / JOURNAL)
        payload = journal_candidate(name, root, transfer)
        body = dict(version=1, source=str(source), backup=str(backup), candidate=str(candidate),
                    candidate_sha256=file_digest(candidate), log=str(log), fence=evidence,
                    manifest=before, manifest_sha256=nbt.digest(canonical(before)), profile=profile,
                    toolchain=toolchain(), operator=operator, reason=reason, identity=observation['identity'],
                    epochs=[1, 1], amounts=observation['amounts'], account=account(witness, observation),
                    record_sha256=nbt.digest(typed(observation['record'])),
                    record_typed_hex=typed(observation['record']).hex(),
                    edit='/data/transfers[' + transfer + '] -> absent', writes=[JOURNAL],
                    no_edits=['ledger', 'entities', 'terrain', 'all other files'],
                    payload_sha256=nbt.digest(payload), compressed_sha256=nbt.digest(compressed(payload)))
        require(manifest(backup, bl) == before and manifest(source, sl) == before, 'INPUT_CHANGED')
        document = dict(body=body, digest=nbt.digest(canonical(body)))
        publish_json(report, document)
        return document


def load_plan(path, confirm=None):
    path = strict_path(path)
    require(path.stat().st_size <= MAX_REPORT, 'REPORT_LIMIT')
    document = read_json(path)
    body = document['body']
    require(document['digest'] == nbt.digest(canonical(body)) and type(body['version']) is int and body['version'] == 1, 'PLAN_DIFFERS')
    require(confirm is None or confirm == document['digest'], 'PLAN_UNCONFIRMED')
    require(body['toolchain'] == toolchain(), 'TOOLCHAIN_DIFFERS')
    require(body['manifest_sha256'] == nbt.digest(canonical(body['manifest'])), 'PLAN_DIFFERS')
    return body


def check_external(body):
    require(file_digest(strict_path(body['candidate'])) == body['candidate_sha256'], 'CANDIDATE_CHANGED')
    require(fence(strict_path(body['log']), body['identity']['transfer']) == body['fence'], 'FENCE_EVIDENCE_CHANGED')


def check_observation(body, observation):
    require(body['identity'] == observation['identity'] and body['amounts'] == observation['amounts']
            and body['epochs'] == [1, 1] and body['writes'] == [JOURNAL]
            and body['no_edits'] == ['ledger', 'entities', 'terrain', 'all other files']
            and body['edit'] == '/data/transfers[' + observation['identity']['transfer'] + '] -> absent'
            and body['record_typed_hex'] == typed(observation['record']).hex()
            and body['record_sha256'] == nbt.digest(typed(observation['record'])), 'PLAN_DIFFERS')
    witness = body['account'].get('witness')
    require(body['account'] == account(None if witness is None else strict_path(witness), observation), 'PLAN_ACCOUNT_DIFFERS')


def copy_world(source, destination, files, source_lock):
    destination.mkdir()
    for relative in files:
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open('xb') as out:
            if relative == 'session.lock':
                source_lock.seek(0)
                shutil.copyfileobj(source_lock, out, 1024**2)
            else:
                with (source / relative).open('rb') as inp:
                    shutil.copyfileobj(inp, out, 1024**2)
            out.flush()
            os.fsync(out.fileno())
    for directory, _, _ in os.walk(destination, topdown=False):
        force_directory(directory)


def difference(before, after):
    return dict(added=sorted(after.keys() - before.keys()), removed=sorted(before.keys() - after.keys()),
                changed=sorted(k for k in before.keys() & after.keys() if before[k] != after[k]))


def apply(plan_path, output, confirm):
    body = load_plan(plan_path, confirm)
    require(confirm is not None, 'PLAN_UNCONFIRMED')
    source, backup = (strict_path(body[k]) for k in ('source', 'backup'))
    output = strict_path(output, exists=False)
    parent = strict_path(output.parent)
    disjoint(source, backup, output, Path(plan_path).parent)
    require(not output.exists(), 'OUTPUT_EXISTS')
    stage = parent / ('.' + output.name + '.x03-' + uuid.uuid4().hex)
    published = False
    try:
        with report_lock(Path(plan_path).parent), world_lock(source) as sl, world_lock(backup) as bl:
            require(manifest(source, sl) == body['manifest'] and manifest(backup, bl) == body['manifest'], 'STALE_PLAN')
            check_external(body)
            space(body['manifest'], parent)
            observation = inspect(backup, body['manifest'], body['profile'], body['identity']['transfer'])
            check_observation(body, observation)
            payload = journal_candidate(*read_gzip(backup / JOURNAL), body['identity']['transfer'])
            require(nbt.digest(payload) == body['payload_sha256'] and nbt.digest(compressed(payload)) == body['compressed_sha256'], 'PLAN_DIFFERS')
            copy_world(backup, stage, body['manifest'], bl)
            with world_lock(stage) as cl:
                require(manifest(stage, cl) == body['manifest'], 'INCOMPLETE_COPY')
                write_journal(stage / JOURNAL, payload)
                after = manifest(stage, cl)
                require(difference(body['manifest'], after) == dict(added=[], removed=[], changed=[JOURNAL]), 'EXTRA_FILE_CHANGE')
                require(after[JOURNAL]['sha256'] == body['compressed_sha256'], 'WRITE_READBACK_DIFFERS')
                inspect(stage, after, body['profile'], body['identity']['transfer'], True, observation['record'])
                require(manifest(source, sl) == body['manifest'] and manifest(backup, bl) == body['manifest'], 'INPUT_CHANGED')
                check_external(body)
            # The lock has to close before Windows can rename the complete directory.
            require(not output.exists(), 'OUTPUT_EXISTS')
            os.rename(stage, output)
            published = True
            force_directory(parent)
    except BaseException:
        if stage.exists():
            shutil.rmtree(stage)
        # Closing an input lock or forcing the parent can fail after publication.
        # Only this invocation's newly published output may be removed.
        if published and output.exists():
            shutil.rmtree(output)
        raise
    return dict(status='COPIED_NOT_PROMOTED', output=str(output), manifest=after, plan_digest=confirm)


def census(plan_path, world, report, after_start=False):
    body = load_plan(plan_path)
    world, backup, source = map(strict_path, (world, body['backup'], body['source']))
    report = strict_path(report, exists=False)
    disjoint(source, backup, world, report.parent)
    with report_lock(report.parent), ExitStack() as stack:
        locks = [stack.enter_context(world_lock(w)) for w in (source, backup, world)]
        require(manifest(source, locks[0]) == body['manifest'] and manifest(backup, locks[1]) == body['manifest'], 'STALE_PLAN')
        check_external(body)
        current = manifest(world, locks[2])
        diff = difference(body['manifest'], current)
        require(not diff['removed'], 'FILE_REMOVED')
        require(not any('advancedrocketrycommunity-backups' in Path(p).parts for p in diff['added']), 'MIGRATION_DURING_VERIFICATION')
        require(not any('advancedrocketrycommunity-backups' in Path(p).parts for p in diff['changed']), 'BACKUP_EVIDENCE_CHANGED')
        if after_start:
            for relative in body['manifest']:
                if Path(relative).name.removesuffix('.dat').removeprefix('advancedrocketrycommunity_') in CURRENT_ROOTS:
                    before_root = at(read_gzip(backup / relative)[1], 'data')
                    after_root = at(read_gzip(world / relative)[1], 'data')
                    require(all(typed(at(before_root, k)) == typed(at(after_root, k)) for k in ('schema_version', 'format_epoch')),
                            'MIGRATION_DURING_VERIFICATION')
        retained = at(nbt.read(bytes.fromhex(body['record_typed_hex']))[1], 'v')
        old = inspect(backup, body['manifest'], body['profile'], body['identity']['transfer'])
        check_observation(body, old)
        new = inspect(world, current, body['profile'], body['identity']['transfer'], True, retained)
        if not after_start:
            require(diff == dict(added=[], removed=[], changed=[JOURNAL]), 'EXTRA_FILE_CHANGE')
            require(current[JOURNAL]['sha256'] == body['compressed_sha256'], 'WRITE_READBACK_DIFFERS')
        else:
            for key in ('entity', 'device'):
                require(typed(old[key]) == typed(new[key]), 'TARGET_CHANGED:' + key)
            require(old['managed'].keys() == new['managed'].keys(), 'MIGRATION_DURING_VERIFICATION')
            for path, root in old['managed'].items():
                expected = nbt.read(journal_candidate(*read_gzip(backup / JOURNAL), body['identity']['transfer']))[1] if path == JOURNAL else root
                # A vanilla empty ListTag is END; nonempty lists and unknown fields keep their types.
                require(typed(expected) == typed(new['managed'][path]), 'MANAGED_ROOT_CHANGED:' + path)
        # Forced-chunk lists may be newly written by the operator's loading commands,
        # but their decoded contents must return to the backup's state.
        for path in diff['added'] + diff['changed'] if after_start else []:
            if Path(path).name == 'chunks.dat':
                new_forced = at(read_gzip(world / path)[1], 'data')
                if path in body['manifest']:
                    require(typed(new_forced) == typed(at(read_gzip(backup / path)[1], 'data')), 'FORCED_CHUNKS_CHANGED')
                else:
                    require(value(new_forced, 'Forced', kind=nbt.LONGS) == [], 'FORCED_CHUNKS_CHANGED')
        require(manifest(world, locks[2]) == current, 'INPUT_CHANGED')
        require(manifest(source, locks[0]) == body['manifest'] and manifest(backup, locks[1]) == body['manifest'], 'INPUT_CHANGED')
        check_external(body)
        result = dict(version=1, status='SEMANTIC_CENSUS_ONLY' if after_start else 'OFFLINE_BOUNDARY_VERIFIED',
                      differences=diff, manifest=current, identity=new['identity'], amounts=new['amounts'],
                      promotion_approved=False, plan_digest=nbt.digest(canonical(body)))
        publish_json(report, result)
        return result


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_subparsers(dest='mode', required=True)
    p = modes.add_parser('plan')
    for flag in ('source', 'backup', 'candidate', 'log', 'report', 'transfer', 'operator', 'reason'):
        p.add_argument('--' + flag, required=True)
    p.add_argument('--profile', choices=(PROFILE_DISABLED, PROFILE_ENABLED), required=True)
    p.add_argument('--witness', type=Path)
    p = modes.add_parser('apply')
    for flag in ('plan', 'output', 'confirm'):
        p.add_argument('--' + flag, required=True)
    p = modes.add_parser('census')
    for flag in ('plan', 'world', 'report'):
        p.add_argument('--' + flag, required=True)
    p.add_argument('--after-start', action='store_true')
    args = vars(parser.parse_args(argv))
    mode = args.pop('mode')
    try:
        if mode == 'plan':
            result = plan(**args)
            print('PLAN ' + result['digest'])
        elif mode == 'apply':
            result = apply(args['plan'], args['output'], args['confirm'])
            print(result['status'])
        else:
            result = census(args['plan'], args['world'], args['report'], args['after_start'])
            print(result['status'])
        return 0
    except (Refusal, nbt.NbtError, OSError, ValueError, KeyError, TypeError, struct.error,
            zlib.error, RecursionError, KeyboardInterrupt) as error:
        reasons = error.reasons if isinstance(error, Refusal) else ['UNREADABLE_OR_INVALID_INPUT:' + str(error)]
        print('REFUSED')
        for reason in reasons[:4]:
            print(str(reason).replace('\n', ' ').replace('\r', ' ')[:160])
        return 2


if __name__ == '__main__':
    def interrupted(signum, frame):
        raise KeyboardInterrupt('interrupted')
    signal.signal(signal.SIGTERM, interrupted)
    sys.exit(main())
