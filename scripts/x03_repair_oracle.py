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
# The runner fixes the first, virgin Moon landing pad at (8, 8). Its one-column
# footprint is in chunk (0, 0); two ticket columns plus eight vanilla generation
# neighbours bound the disposable descendant. A different selected pad refuses.
FUNCTIONAL_BOXES = {'': (-6, 15, -6, 14), 'dimensions/advancedrocketrycommunity/moon': (-10, 10, -10, 10)}
FUNCTIONAL_DATA_FILES = {
    LEDGER: 'Ordinary unload and flight authority writes.',
    'data/advancedrocketrycommunity_stations.dat': 'The admin flight resolves the station registry even for a body route.',
    'data/advancedrocketrycommunity_classic_progression.dat': 'The ordinary first Moon landing receipt.',
}


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
        require(ledger['issued_launch_serial'] == 1 and not ledger['rocket_authorities'], 'LAUNCH_SERIAL_OR_FABRICATED_HEAD')
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
    require(oxidizer == 0 and old['propulsion']['primary']['fluid'] == 'advancedrocketrycommunity:rocket_fuel'
            and old['propulsion']['primary']['amount'] - new['propulsion']['primary']['amount'] == primary,
            'PER_FLUID_PROPULSION_DEBIT_DIFFERS')
    require(old['propulsion']['oxidizer'] == new['propulsion']['oxidizer'], 'OXIDIZER_CHANGED')
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
    if action == 'launch':
        compare_launched_rocket(before, after)
        # Remove only the independently checked physical target, not its chunk
        # or other entities. Disappearance of an otherwise empty entity record
        # is vanilla's ordinary deletion after the handoff.
        for state, tree in ((before, left), (after, right)):
            entries = tree[state['rocket_key']].value['Entities'].value[1]
            entries[:] = [entry for entry in entries if nbt.plain(entry).get('UUID') != nbt.plain(state['entity'])['UUID']]
            if not entries:
                del tree[state['rocket_key']]
        require(not left.keys() - right.keys(), 'COLLATERAL_CHUNK_REMOVED')
        for key in right.keys() - left.keys():
            require(in_functional_box(key), 'OUTSIDE_FUNCTIONAL_BOX:' + repr(key))
            require(Path(key[0]).parent.name != 'entities' or not right[key].value['Entities'].value[1], 'NEW_UNRELATED_ENTITY')
        right = {key: right[key] for key in left}
    compare_chunks(left, right)
    new_roots = after['managed'].keys() - before['managed'].keys()
    allowed_new = set(FUNCTIONAL_DATA_FILES) - {LEDGER} if action == 'launch' else set()
    require(not before['managed'].keys() - after['managed'].keys() and new_roots <= allowed_new, 'MANAGED_ROOT_SET_CHANGED')
    event = moon_event(after) if action == 'launch' else None
    for name in new_roots:
        validate_new_functional_root(name, after['managed'][name], event)
    for name in before['managed']:
        a, b = copy.deepcopy(before['managed'][name]), copy.deepcopy(after['managed'][name])
        if action in ('unload', 'launch') and name == LEDGER:
            for root in (a, b):
                for key in (('issued_through', 'writer_generation') if action == 'unload' else
                            ('writer_generation', 'issued_launch_serial')):
                    root.value['data'].value.pop(key)
            require(after['ledger']['writer_generation'] > before['ledger']['writer_generation'], 'WRITER_GENERATION')
            if action == 'unload':
                require(after['ledger']['issued_through'] == before['ledger']['issued_through'] + 1, 'UNLOAD_GENERATION')
        if action == 'launch' and name == JOURNAL:
            require(nbt.plain(a)['data']['classic_first_moon_landing'] == dict(schema_version=1, state='EMPTY'), 'PREEXISTING_MOON_ELECTION')
            for root in (a, b):
                root.value['data'].value.pop('classic_first_moon_landing')
        if action == 'launch' and name.endswith('_stations.dat'):
            for root in (a, b):
                root.value['data'].value['orbital_clock'].value.pop('logical_tick')
        if action == 'launch' and name.endswith('_classic_progression.dat'):
            require(nbt.plain(a)['data']['first_moon_landing'] == dict(occurred=0), 'PREEXISTING_MOON_RECEIPT')
            equal(b.value['data'].value['first_moon_landing'].value['event'], event, 'first-moon-receipt')
            for root in (a, b):
                root.value['data'].value.pop('first_moon_landing')
        equal(a, b, name)


def moon_event(state):
    """The ordinary launch's one world-first event; unrelated authority is not normalized."""
    outbox = state['managed'][JOURNAL].value['data'].value['classic_first_moon_landing']
    root = nbt.plain(outbox)
    require(set(root) == {'schema_version', 'state', 'event'} and root['schema_version'] == 1
            and root['state'] == 'DELIVERED', 'MOON_OUTBOX_NOT_DELIVERED')
    event = outbox.value['event']
    value = nbt.plain(event)
    require(set(value) == {'kind', 'event_id', 'transfer_id', 'logical_rocket_id', 'owner_id', 'terminal_rocket_id',
                           'record_checksum', 'level', 'terminal_origin', 'completed_at_game_time', 'participants', 'fingerprint'}, 'MOON_EVENT_FIELDS')
    for field in ('event_id', 'transfer_id', 'logical_rocket_id', 'owner_id', 'terminal_rocket_id'):
        require(event.value[field].type == nbt.INTS and len(value[field]) == 4, 'MOON_EVENT_UUID')
    require(value['kind'] == 'first_moon_landing' and value['logical_rocket_id'] == state['rocket']['assembly_transaction_id']
            and value['owner_id'] == state['rocket']['owner_id'] and value['terminal_rocket_id'] == nbt.plain(state['entity'])['UUID']
            and value['terminal_origin'] == state['rocket']['flight_data']['current_origin']
            and value['level'] == 'advancedrocketrycommunity:moon' and value['participants'] == [], 'MOON_EVENT_IDENTITY')
    require(event.value['participants'].type == nbt.INTS and event.value['terminal_origin'].type == nbt.INTS
            and event.value['completed_at_game_time'].type == nbt.LONG
            and value['completed_at_game_time'] >= 0
            and all(re.fullmatch('[0-9a-f]{64}', value[field]) for field in ('record_checksum', 'fingerprint')), 'MOON_EVENT_TYPES')
    return event


def validate_new_functional_root(name, root, event):
    require(set(root.value) == {'data', 'DataVersion'} and root.value['DataVersion'].type == nbt.INT
            and root.value['DataVersion'].value == 3465, 'NEW_MANAGED_ROOT_WRAPPER')
    data = nbt.plain(root)['data']
    typed_data = root.value['data']
    require(typed_data.type == nbt.COMPOUND and typed_data.value['schema_version'].type == nbt.INT
            and typed_data.value['format_epoch'].type == nbt.STRING, 'NEW_MANAGED_ROOT_TYPES')
    if name.endswith('_stations.dat'):
        require(set(data) == {'schema_version', 'format_epoch', 'stations', 'reservations', 'warp_energy', 'orbital_clock', 'classic_first_warp'}
                and data['schema_version'] == 5 and data['format_epoch'] == 'v1.8.0-station-first-warp'
                and not data['stations'] and not data['reservations'] and not data['warp_energy']
                and set(data['orbital_clock']) == {'schema_version', 'logical_tick'}
                and data['orbital_clock']['schema_version'] == 1 and data['orbital_clock']['logical_tick'] >= 0
                and data['classic_first_warp'] == dict(schema_version=1, state='EMPTY'), 'NEW_STATION_AUTHORITY')
        require(all(typed_data.value[key].type == nbt.LIST for key in ('stations', 'reservations', 'warp_energy'))
                and typed_data.value['orbital_clock'].type == typed_data.value['classic_first_warp'].type == nbt.COMPOUND
                and typed_data.value['orbital_clock'].value['logical_tick'].type == nbt.LONG, 'NEW_STATION_TYPES')
    elif name.endswith('_classic_progression.dat'):
        require(set(data) == {'schema_version', 'format_epoch', 'first_moon_landing', 'first_station_warp'}
                and data['schema_version'] == 1 and data['format_epoch'] == 'v1.8.0-classic-progression'
                and data['first_station_warp'] == dict(occurred=0)
                and set(data['first_moon_landing']) == {'occurred', 'event'}
                and data['first_moon_landing']['occurred'] == 1, 'NEW_CLASSIC_AUTHORITY')
        require(all(typed_data.value[key].type == nbt.COMPOUND and typed_data.value[key].value['occurred'].type == nbt.BYTE
                    for key in ('first_moon_landing', 'first_station_warp')), 'NEW_CLASSIC_TYPES')
        equal(root.value['data'].value['first_moon_landing'].value['event'], event, 'first-moon-receipt')
    else:
        raise OracleError('UNEXPECTED_NEW_ROOT:' + name)


def in_functional_box(key):
    name, x, z = key
    path = Path(name)
    level = path.parent.parent.as_posix()
    box = FUNCTIONAL_BOXES.get('' if level == '.' else level)
    return box is not None and box[0] <= x <= box[1] and box[2] <= z <= box[3]


def functional_boundary(before, after, runtime_files):
    allowed = set(runtime_files) | set(FUNCTIONAL_DATA_FILES)
    for name in before.keys() | after.keys():
        path = Path(name)
        match = REGION.fullmatch(path.name)
        level = path.parent.parent.as_posix()
        box = FUNCTIONAL_BOXES.get('' if level == '.' else level)
        if box and path.parent.name in ('region', 'entities', 'poi') and match:
            rx, rz = map(int, match.groups())
            if rx * 32 <= box[1] and rx * 32 + 31 >= box[0] and rz * 32 <= box[3] and rz * 32 + 31 >= box[2]:
                allowed.add(name)
    return boundary(before, after, allowed)


def compare_launched_rocket(before, after):
    a, b = copy.deepcopy(before['entity']), copy.deepcopy(after['entity'])
    old, new = before['rocket'], after['rocket']
    require(new['flight_data']['current_dimension'] == 'advancedrocketrycommunity:moon'
            and new['flight_data']['current_origin'][::2] == [8, 8], 'LANDING_OUTSIDE_PINNED_FIRST_PAD')
    origin = new['flight_data']['current_origin']
    require(after['rocket_key'][0].startswith('dimensions/advancedrocketrycommunity/moon/entities/')
            and nbt.plain(after['entity'])['Pos'] == [origin[0] + .5, float(origin[1]), origin[2] + .5]
            and new['snapshot']['source_origin'] == origin
            and new['snapshot']['source_dimension'] == new['flight_data']['current_dimension'], 'LANDED_PHYSICAL_LOCATION')
    for entity in (a, b):
        for field in ('UUID', 'Pos'):
            entity.value.pop(field)
        data = entity.value['RocketEntityData']
        snapshot = data.value['snapshot']
        for field in ('snapshot_id', 'source_dimension', 'source_origin', 'content_hash', 'created_at_game_time'):
            snapshot.value.pop(field)
        escrow = data.value['resource_escrow']
        escrow.value.pop('resource_epoch')
        flight = data.value['flight_data']
        for field in ('state', 'current_body', 'current_dimension', 'current_origin', 'state_started_game_time', 'current_target'):
            flight.value.pop(field)
        fuel = flight.value['fuel']
        for field in ('amount', 'committed_debits'):
            fuel.value.pop(field)
        fuel.value['propulsion'].value.pop('revision')
        fuel.value['propulsion'].value['primary'].value.pop('amount')
    require(new['resource_escrow']['resource_epoch'] == 0 and new['flight_data']['state'] == 'LANDED', 'LANDED_EPOCH_OR_STATE')
    old_fuel, new_fuel = old['flight_data']['fuel'], new['flight_data']['fuel']
    require(len(new_fuel['committed_debits']) == len(old_fuel['committed_debits']) + 1
            and new_fuel['committed_debits'][:-1] == old_fuel['committed_debits']
            and new_fuel['propulsion']['revision'] == old_fuel['propulsion']['revision'] + 1, 'DEBIT_HISTORY_OR_REVISION')
    equal(a, b, 'landed-rocket')
