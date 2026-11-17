"""Independent X03 raw-file oracle. Imports the bounded NBT codec, never the repair planner.

All slots, Levels and passenger subtrees are read. A failed decode is not absence.
Runtime allowances are fixed declarations, not a list learned by an accepting run.
"""
from __future__ import annotations

import copy
import hashlib
from pathlib import Path
import re
import struct
import uuid

import loader_cut_nbt as nbt

JOURNAL = 'data/advancedrocketrycommunity_rocket_transfers.dat'
LEDGER = 'data/advancedrocketrycommunity_resource_escrow.dat'
REGION = re.compile(r'r\.(-?\d+)\.(-?\d+)\.mca\Z')
MAX_FILES, MAX_REGIONS, MAX_DECODED = 100_000, 4096, 256 * 1024**2
# Vanilla chunk clocks advance while the verification probe holds its tickets.
CHUNK_CLOCKS = ('LastUpdate', 'InhabitedTime')
ENTITY_CLOCKS = ()


class OracleError(ValueError):
    pass


def require(condition, message):
    if not condition:
        raise OracleError(message)


def digest(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def manifest(world):
    result = {}
    for path in sorted(world.rglob('*')):
        require(not path.is_symlink() and not path.is_junction(), 'LINKED_INPUT:' + str(path))
        if path.is_file():
            result[path.relative_to(world).as_posix()] = digest(path)
            require(len(result) <= MAX_FILES, 'FILE_LIMIT')
    return result


def differences(before, after):
    return dict(added=sorted(after.keys() - before.keys()), removed=sorted(before.keys() - after.keys()),
                changed=sorted(key for key in before.keys() & after.keys() if before[key] != after[key]))


def boundary(before, after, allowed):
    diff = differences(before, after)
    require(not diff['removed'], 'FILE_REMOVED:' + repr(diff['removed']))
    for name in diff['added'] + diff['changed']:
        require(name in allowed, 'UNLISTED_FILE:' + name)
    return diff


def chunks(world):
    result, total, regions = {}, 0, 0
    for path in sorted(world.rglob('*.mca')):
        require(path.parent.name in ('region', 'entities', 'poi'), 'UNKNOWN_REGION:' + str(path))
        match = REGION.fullmatch(path.name)
        require(match is not None and path.stat().st_size <= 64 * 1024**2, 'REGION_LIMIT')
        regions += 1
        require(regions <= MAX_REGIONS, 'REGION_LIMIT')
        with path.open('rb') as stream:
            header = stream.read(8192)
        require(len(header) == 8192, 'TRUNCATED_REGION')
        rx, rz = map(int, match.groups())
        occupied = set()
        for slot in range(1024):
            location = struct.unpack_from('>I', header, slot * 4)[0]
            if not location:
                continue
            sector, count = location >> 8, location & 255
            require(sector >= 2 and count and not occupied.intersection(range(sector, sector + count)), 'REGION_OVERLAP')
            occupied.update(range(sector, sector + count))
            x, z = rx * 32 + slot % 32, rz * 32 + slot // 32
            root, info = nbt.chunk(path.parent, x, z)
            total += info['decoded']
            require(total <= MAX_DECODED and not info['external'], 'DECODE_LIMIT_OR_EXTERNAL')
            result[(path.relative_to(world).as_posix(), x, z)] = root
    return result


def passengers(entity, depth=0):
    require(depth <= 8, 'PASSENGER_DEPTH')
    yield entity
    children = entity.value.get('Passengers')
    if children:
        require(len(children.value[1]) <= 1024, 'OBJECT_LIMIT')
        for child in children.value[1]:
            yield from passengers(child, depth + 1)


def serialized(tag):
    return nbt.write(b'', tag)


def first_difference(left, right, path=''):
    if left.type != right.type:
        return path + ':type'
    if left.type == nbt.COMPOUND:
        if list(left.value) != list(right.value):
            return path + ':keys/order'
        for key in left.value:
            found = first_difference(left.value[key], right.value[key], path + '/' + key)
            if found:
                return found
    elif left.type == nbt.LIST:
        if left.value[0] != right.value[0] or len(left.value[1]) != len(right.value[1]):
            return path + ':list'
        for i, (a, b) in enumerate(zip(left.value[1], right.value[1])):
            found = first_difference(a, b, path + '/' + str(i))
            if found:
                return found
    elif left.value != right.value:
        return path
    return None


def equal(left, right, label):
    difference = first_difference(left, right)
    require(difference is None, 'OBJECT_CHANGED:' + label + ':' + str(difference))


def read(world):
    decoded = chunks(world)
    rockets, devices, tanks = [], [], []
    for key, root in decoded.items():
        name, x, z = key
        if Path(name).parent.name == 'entities':
            for outer in root.value['Entities'].value[1]:
                for entity in passengers(outer):
                    if 'RocketEntityData' in entity.value:
                        rockets.append((key, entity))
        elif Path(name).parent.name == 'region':
            for block in root.value.get('block_entities', nbt.Tag(nbt.LIST, (nbt.END if hasattr(nbt, 'END') else 0, []))).value[1]:
                if 'arce_rocket_fluid_cargo' in block.value:
                    devices.append((key, block))
            for section in root.value.get('sections', nbt.Tag(nbt.LIST, (0, []))).value[1]:
                palette = section.value.get('block_states')
                if palette and any('pressurized_tank' in nbt.plain(entry)['Name'] for entry in palette.value['palette'].value[1]):
                    y0 = section.value['Y'].value * 16
                    for y in range(y0, y0 + 16):
                        for bx in range(x * 16, x * 16 + 16):
                            for bz in range(z * 16, z * 16 + 16):
                                if 'pressurized_tank' in nbt.block(root, bx, y, bz):
                                    tanks.append((name, bx, y, bz))
    managed = {}
    for path in world.rglob('advancedrocketrycommunity_*.dat'):
        if 'advancedrocketrycommunity-backups' not in path.parts:
            managed[path.relative_to(world).as_posix()] = nbt.read_gzip(path)[1]
    require(len(rockets) == len(devices) == 1 and not tanks, 'DUPLICATED_OR_MISSING_ROCKET_DEVICE_OR_TANK')
    entity, block = rockets[0][1], devices[0][1]
    rocket, device = nbt.plain(entity)['RocketEntityData'], nbt.plain(block)['arce_rocket_fluid_cargo']
    payloads = [b['block_entity'] for b in rocket['snapshot']['relative_blocks'] if 'block_entity' in b]
    require(len(payloads) == 1 and payloads[0]['adapter'] == 'advancedrocketrycommunity:pressurized_tank_v1', 'TANK_PAYLOAD')
    fluid = payloads[0]['data']['tank']['fluid']
    require(fluid['FluidName'] == device['tank']['FluidName'] == 'minecraft:water' and not fluid.get('Tag') and not device['tank'].get('Tag'), 'FLUID_IDENTITY')
    return dict(chunks=decoded, managed=managed, entity=entity, block=block, rocket=rocket, device=device,
                rocket_key=rockets[0][0], device_key=devices[0][0], cargo=fluid['Amount'], tank=device['tank']['Amount'],
                ledger=nbt.plain(managed[LEDGER])['data'], journal=nbt.plain(managed[JOURNAL])['data'])


def identity(state):
    rocket, device = state['rocket'], state['device']
    return dict(physical=nbt.plain(state['entity'])['UUID'], logical=rocket['assembly_transaction_id'],
                owner=rocket['owner_id'], snapshot=rocket['snapshot']['snapshot_id'], device=device['device'])


def check_state(state, baseline, *, repaired=True, unloaded=False, launched=False):
    rocket, device, ledger = state['rocket'], state['device'], state['ledger']
    require((state['cargo'], state['tank']) == ((3000, 2000) if unloaded else (4000, 1000)), 'WATER_ENDPOINTS')
    require(state['cargo'] + state['tank'] == 5000, 'WATER_CONSERVATION')
    now, old = identity(state), identity(baseline)
    for key in ('logical', 'owner', 'device') + (() if launched else ('physical', 'snapshot')):
        require(now[key] == old[key], 'IDENTITY:' + key)
    require(device['binding']['logical_rocket'] == now['logical'] and device['owner'] == now['owner'], 'DEVICE_BINDING')
    for section in (rocket['resource_escrow'], device['resource_escrow']):
        require(section['ledger_id'] == ledger['ledger_id'] and not section['markers'], 'ESCROW_BINDING_OR_MARKERS')
    require(not ledger['records'] and not ledger['floors'] and not ledger['tombstones'] and not ledger['armed_flights']
            and not ledger.get('fluid_accounts', []), 'FABRICATED_AUTHORITY')
    require(not rocket['flight_data']['passengers']['assignments'], 'PASSENGER_ASSIGNMENT')
    require(len(state['journal']['transfers']) == (0 if repaired else 1), 'JOURNAL_REMOVAL')
    require(ledger['schema_version'] == 2, 'X03_B_REQUIRES_ROOT_2')
    if not launched:
        require(rocket['flight_data']['state'] == 'FUELED' and rocket['flight_data']['fuel'] == baseline['rocket']['flight_data']['fuel'], 'PROPULSION_OR_FLIGHT')
        require(nbt.plain(state['entity'])['Pos'] == nbt.plain(baseline['entity'])['Pos'], 'ROCKET_MOVED')
        require(not ledger['rocket_authorities'] and ledger['issued_launch_serial'] == 0, 'FABRICATED_HEAD')
    else:
        require(rocket['flight_data']['state'] == 'LANDED' and nbt.plain(state['entity'])['Pos'] != nbt.plain(baseline['entity'])['Pos'], 'NOT_LANDED_ELSEWHERE')
        require(ledger['issued_launch_serial'] == 1 and len(ledger['rocket_authorities']) <= 1, 'LAUNCH_SERIAL')
    expected_epoch = 2 if unloaded else 1
    require(device['resource_escrow']['resource_epoch'] == expected_epoch, 'DEVICE_EPOCH')
    if not launched:
        require(rocket['resource_escrow']['resource_epoch'] == expected_epoch, 'ROCKET_EPOCH')


def normalized(root, kind):
    result = copy.deepcopy(root)
    if kind == 'region':
        for key in CHUNK_CLOCKS:
            result.value.pop(key, None)
    return result


def compare_chunks(before, after):
    """Full typed comparison, including non-target objects in target-containing files."""
    require(before.keys() == after.keys(), 'CHUNK_SET_CHANGED')
    for key in before:
        kind = Path(key[0]).parent.name
        equal(normalized(before[key], kind), normalized(after[key], kind), repr(key))


def compare_repair(baseline, current, transfer):
    check_state(current, baseline)
    compare_chunks(baseline['chunks'], current['chunks'])
    require(baseline['managed'].keys() == current['managed'].keys(), 'MANAGED_ROOT_SET_CHANGED')
    for name, root in baseline['managed'].items():
        expected = copy.deepcopy(root)
        if name == JOURNAL:
            records = expected.value['data'].value['transfers']
            require(len(records.value[1]) == 1 and nbt.plain(records.value[1][0])['transfer_id'] == list(nbt.uuid_tag(uuid.UUID(transfer)).value), 'WRONG_REMOVAL')
            records.value = (0, [])
        equal(expected, current['managed'][name], name)


def observe(lines, case, state, *, landed=False):
    require(not any('ARCE_TRANSFER_FENCED' in line for line in lines), 'FENCE_NOT_CLEARED')
    prefix = 'ARCE_RELEASE_CARGO_CUT_RESULT cut=' + case + ' '
    matches = [line.split(prefix, 1)[1] for line in lines if prefix in line]
    require(len(matches) == 1, 'OBSERVATION_MISSING_OR_DUPLICATE')
    fields = dict(token.split('=', 1) for token in matches[0].split() if '=' in token)
    require(fields.get('action_authority') == 'true' and fields.get('journal') == '0', 'ACTION_AUTHORITY_READBACK')
    require(fields.get('device') == str(state['tank']) and fields.get('device_markers') == '0', 'DEVICE_READBACK')
    # The restart probe intentionally loads only the source area. The landed entity
    # is read across all on-disk Levels, not inferred absent from a log counter.
    if fields.get('rocket') != 'absent':
        require(fields.get('rocket') == str(state['cargo']) and fields.get('mutation_allowed') == 'true'
                and fields.get('rocket_markers') == '0', 'ROCKET_READBACK')
    else:
        require(landed and case.endswith('OBSERVE'), 'ROCKET_READBACK_ABSENT')
    return fields


def launch_debit(fields, before, after):
    debit = fields.get('debit', '')
    match = re.fullmatch(r'primary:(\d+),oxidizer:(\d+),revision:(\d+),resource:([^,]+),catalog:(.+)', debit)
    require(match is not None, 'TYPED_PROPULSION_DEBIT_MISSING')
    primary, oxidizer = map(int, match.groups()[:2])
    old, new = before['rocket']['flight_data']['fuel'], after['rocket']['flight_data']['fuel']
    require(primary + oxidizer > 0 and old['amount'] - new['amount'] == primary + oxidizer, 'PROPULSION_DEBIT_DIFFERS')
    require(int(fields['fuel_before']) == old['amount'] and int(fields['fuel_after']) == new['amount'], 'FUEL_READBACK')
    return dict(primary=primary, oxidizer=oxidizer, revision=int(match[3]), resource=match[4], catalog=match[5])


def tamper_target(baseline, stopped, allowed):
    require(len(stopped) >= 2, 'TWO_DRY_RUNS_REQUIRED')
    for name in sorted(baseline, key=lambda text: text.encode('utf-8')):
        if name not in allowed and all(row.get(name) == baseline[name] for row in stopped):
            if name.endswith('.dat') or (name.endswith('.mca') and Path(name).parent.name in ('region', 'entities', 'poi')):
                return name
    raise OracleError('NO_TAMPER_TARGET')


def compare_functional(before, after, *, action=None):
    """Mask only specified ordinary-action leaves, then compare every other object.

    No action allowance applies to unrelated entities, terrain or managed roots.
    The target's amounts/identity are checked separately by check_state.
    """
    left, right = copy.deepcopy(before['chunks']), copy.deepcopy(after['chunks'])
    if action == 'unload':
        for state, tree in ((before, left), (after, right)):
            entity = tree[state['rocket_key']].value['Entities'].value[1]
            target = next(e for e in entity if nbt.plain(e).get('UUID') == nbt.plain(state['entity'])['UUID'])
            rocket = target.value['RocketEntityData']
            rocket.value['snapshot'].value.pop('content_hash')
            rocket.value['resource_escrow'].value.pop('resource_epoch')
            payload = next(b.value['block_entity'] for b in rocket.value['snapshot'].value['relative_blocks'].value[1]
                           if 'block_entity' in b.value)
            payload.value['data'].value['tank'].value['fluid'].value.pop('Amount')
            block = next(b for b in tree[state['device_key']].value['block_entities'].value[1]
                         if 'arce_rocket_fluid_cargo' in b.value)
            root = block.value['arce_rocket_fluid_cargo']
            root.value['tank'].value.pop('Amount')
            root.value['resource_escrow'].value.pop('resource_epoch')
    # Launch needs route-bounded world-generation/relocation comparison. Until
    # its native trace supplies the exact bound, do not accept it by masking an
    # entire entity, Level or SavedData root.
    require(action != 'launch', 'LAUNCH_OBJECT_BOUNDARY_NOT_ESTABLISHED')
    compare_chunks(left, right)
    require(before['managed'].keys() == after['managed'].keys(), 'MANAGED_ROOT_SET_CHANGED')
    for name in before['managed']:
        a, b = copy.deepcopy(before['managed'][name]), copy.deepcopy(after['managed'][name])
        if action == 'unload' and name == LEDGER:
            for root in (a, b):
                for key in ('issued_through', 'writer_generation'):
                    root.value['data'].value.pop(key)
            require(after['ledger']['issued_through'] == before['ledger']['issued_through'] + 1
                    and after['ledger']['writer_generation'] > before['ledger']['writer_generation'], 'UNLOAD_GENERATION')
        equal(a, b, name)
