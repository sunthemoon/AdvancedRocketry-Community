"""Independent static/specification-model checks; no Java/Forge/runtime execution."""
import copy
import csv
import difflib
import hashlib
import json
import random
import re
import struct
from collections import Counter
from pathlib import Path

ROOT = Path('D:/GitHub/AdvancedRocketry-Community')
SOURCE = Path('C:/Users/Administrator/AppData/Local/Temp/arce-v180-c17-contract-489381f9d14b45928ec89711c681ed16')
R3 = SOURCE / 'revision-03'
OUT = Path(__file__).parent
NAME = 'ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md'
LONG_MAX = (1 << 63) - 1
GROUPS = []

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def rows(path):
    with path.open(encoding='utf-8-sig', newline='') as stream:
        return list(csv.DictReader(stream))

def bounded(value, low, high):
    if type(value) is not int or value < low or value > high:
        raise ValueError('Invalid exact integer/range')
    return value

def increment(value):
    value = bounded(value, 0, LONG_MAX)
    return value if value == LONG_MAX else value + 1

def phase(base, rate, epoch, sample):
    bounded(base, 0, 359999)
    bounded(rate, -60, 60)
    bounded(epoch, 0, LONG_MAX)
    bounded(sample, epoch, LONG_MAX)
    residue = (sample - epoch) % 72000
    product = residue * rate * 5
    assert abs(product) <= 21599700
    return (base + product) % 360000

def clock(root):
    if root['schema_version'] != 5:
        raise ValueError('Only coherent current root modeled')
    field = root['orbital_clock']
    if set(field) != {'schema_version', 'logical_tick'}:
        raise ValueError('Clock exact field set')
    bounded(field['schema_version'], 1, 1)
    sample = bounded(field['logical_tick'], 0, LONG_MAX)
    for state in root['stations']:
        control = state['orbital_controls']
        bounded(control['epoch'], 0, sample)
        bounded(control['distance'], 4, 190)
        if len(control['phases']) != 3 or len(control['rates']) != 3:
            raise ValueError('Three axes required')
        for base, rate in zip(control['phases'], control['rates']):
            phase(base, rate, control['epoch'], sample)
    return sample

def migrate4(root):
    if root['schema_version'] == 5:
        clock(root)
        return copy.deepcopy(root)
    if root['schema_version'] != 4:
        raise ValueError('Earlier existing migration is not reimplemented')
    result = copy.deepcopy(root)
    result['schema_version'] = 5
    result['orbital_clock'] = {'schema_version': 1, 'logical_tick': 0}
    for state in result['stations']:
        state['orbital_controls'] = {
            'distance': 4, 'phases': [state['sun_angle'], 0, 0],
            'rates': [0, 0, 0], 'epoch': 0}
    clock(result)
    return result

def refuses(action):
    try:
        action()
    except (ValueError, KeyError, struct.error, UnicodeError):
        return
    raise AssertionError('Invalid modeled data was accepted')

assert sha(R3 / NAME) == '92e706ae84e6565f605227f7794fb83850a23c4f2ca1915686d28c61fa72c321'
assert sha(R3 / 'DISPOSITIONS-03.md') == 'd7720d8a46ec603991aac41b96fcd01c7dd078d31f92e6c35451384dbe3b9e28'
assert sha(SOURCE / 'revision-02' / NAME) == '24158c9bb75fe7455a3ddf2bf9781b1a4a33a99b313f8d830a381825e584f0e6'
assert sha(Path('C:/Users/Administrator/AppData/Local/Temp/arce-v180-c17-review-r2-d2f35effb88246f084b6ce11e76650af/REVIEW-02.md')) == '709b32387b9ec031830eafde7dde1a10c1ae24f035935ee028ff2744b7aba61f'
text = (R3 / NAME).read_text(encoding='utf-8')
assert 'status: PROPOSED' in text and 'revision: 3' in text
assert 'accepted_by: ""' in text and 'accepted_at: ""' in text
assert 'owner_decisions_pending: [D1, D1-disassembly, D3]' in text
(OUT / 'revision-02-to-03.diff').write_text(''.join(difflib.unified_diff(
    (SOURCE / 'revision-02' / NAME).read_text(encoding='utf-8').splitlines(True),
    text.splitlines(True), fromfile='revision-02/' + NAME,
    tofile='revision-03/' + NAME)), encoding='utf-8')

# Bounded product implementation versus exact arbitrary-precision whole-age reference.
pairs = [(0, 0), (10, 10), (10, 11), (1000, 72999), (99, 72099),
         (0, LONG_MAX), (LONG_MAX - 72000, LONG_MAX),
         (LONG_MAX - 1, LONG_MAX), (LONG_MAX, LONG_MAX)]
cases = 0
for rate in range(-60, 61):
    for epoch, sample in pairs:
        for base in (0, 270000, 359999):
            observed = phase(base, rate, epoch, sample)
            expected = (base + (sample - epoch) * rate * 5) % 360000
            assert observed == expected and 0 <= observed < 360000
            cases += 1
rng = random.Random(6503)
for _ in range(2048):
    epoch = rng.randrange(LONG_MAX + 1)
    sample = rng.randrange(epoch, LONG_MAX + 1)
    base, rate = rng.randrange(360000), rng.randrange(-60, 61)
    assert phase(base, rate, epoch, sample) == (base + (sample - epoch) * rate * 5) % 360000
    cases += 1
assert abs(71999 * 60 * 5) == 21599700
GROUPS.append('ordered_nonzero_epochs_all_rates_modular_wrap_long_max_exact_reference')

legacy = {'schema_version': 4, 'stations': [
    {'id': 'station-a', 'owner': 'same', 'sun_angle': 270000, 'team': ['member'],
     'geometry': [0, 0, 511, 511], 'vacuum': True, 'gravity': 0},
    {'id': 'station-b', 'owner': 'other', 'sun_angle': 359999, 'team': [],
     'geometry': [-1024, -1024, -513, -513], 'vacuum': True, 'gravity': 1000}],
    'reservations': ['retained'], 'warp_energy': {'station-a': 42}}
old_bytes = json.dumps(legacy, sort_keys=True)
initial = migrate4(legacy)
assert json.dumps(legacy, sort_keys=True) == old_bytes
assert clock(initial) == 0 and migrate4(initial) == initial
for before, after in zip(legacy['stations'], initial['stations']):
    assert {key: after[key] for key in before} == before
    assert after['orbital_controls']['epoch'] == 0
    assert after['orbital_controls']['rates'] == [0, 0, 0]
assert initial['warp_energy'] == legacy['warp_energy']
GROUPS.append('root4_zero_clock_epoch_migration_preserves_fields_repeat_current_noop')

changed = copy.deepcopy(initial)
changed['orbital_clock']['logical_tick'] = 10000
changed['stations'][0]['orbital_controls'].update(
    {'phases': [123456, 200, 359999], 'rates': [-60, 1, 0], 'epoch': 10000})
serialized = json.loads(json.dumps(changed))
assert clock(serialized) == 10000
for external in (0, 42, 9999, LONG_MAX):
    # Deliberately no external time feeds the clock; demonstrate exact restart state.
    assert clock(serialized) == 10000 and increment(clock(serialized)) == 10001
assert phase(123456, -60, 10000, 10000) == 123456
runtime = 10040
checkpoint = copy.deepcopy(changed)
checkpoint['orbital_clock']['logical_tick'] = runtime
assert clock(checkpoint) == runtime
assert phase(123456, -60, 10000, clock(checkpoint)) == 111456
assert phase(123456, -60, 10000, clock(changed)) == 123456
# Clean refusal publishes neither epoch nor rate; old/new coherent roots each validate.
assert clock(initial) == 0 and clock(changed) == 10000
for wrong in ({'schema_version': 1, 'logical_tick': 9999},
              {'schema_version': 1, 'logical_tick': -1},
              {'schema_version': 1, 'logical_tick': True},
              {'schema_version': 2, 'logical_tick': 10000},
              {'schema_version': 1, 'logical_tick': 10000, 'extra': 1}):
    broken = copy.deepcopy(changed)
    broken['orbital_clock'] = wrong
    before = json.dumps(broken, sort_keys=True)
    refuses(lambda: clock(broken))
    assert json.dumps(broken, sort_keys=True) == before
missing = copy.deepcopy(changed)
del missing['orbital_clock']
refuses(lambda: clock(missing))
zero_rate_ahead = copy.deepcopy(initial)
zero_rate_ahead['stations'][1]['orbital_controls']['epoch'] = 1
refuses(lambda: clock(zero_rate_ahead))
GROUPS.append('one_root_epoch_sample_checked_old_new_pairs_independent_world_rollback_unknown_hold')
GROUPS.append('ordinary_checkpoint_clean_restart_zero_offline_unsaved_visual_rewind_disclosed')
GROUPS.append('future_missing_exact_type_extra_field_epoch_ahead_even_zero_rate_preserved_input')

assert increment(LONG_MAX - 1) == increment(LONG_MAX) == LONG_MAX
frozen = phase(270000, -60, 0, LONG_MAX)
assert phase(frozen, 0, LONG_MAX, LONG_MAX) == frozen
refuses(lambda: increment(LONG_MAX + 1))
refuses(lambda: phase(0, 0, LONG_MAX, LONG_MAX - 1))
GROUPS.append('increment_saturates_no_wrap_authorized_stop_exact_phase')

# Standard-NBT encoding of the small declared clock compound, not a native root codec.
def utf(value):
    raw = value.encode('utf-8')
    return struct.pack('>H', len(raw)) + raw

clock_nbt = (b'\x0a' + utf('orbital_clock') + b'\x03' + utf('schema_version')
             + struct.pack('>i', 1) + b'\x04' + utf('logical_tick')
             + struct.pack('>q', LONG_MAX) + b'\x00')
assert len(clock_nbt) <= 128
GROUPS.append('clock_nbt_int_long_exact_layout_three_nodes_within128_bytes')

def varint(value):
    data = bytearray()
    while value >= 128:
        data.append((value & 127) | 128)
        value >>= 7
    data.append(value)
    return bytes(data)

def packet(body, phases, rates, epoch, sample, kind=2, distance=190):
    bounded(kind, 0, 2)
    bounded(distance, 4, 190)
    for base, rate in zip(phases, rates):
        phase(base, rate, epoch, sample)
    if body is None:
        encoded_body = b'\x00'
        if kind != 0:
            raise ValueError('Known planetary/singularity body required')
    else:
        raw = body.encode('ascii')
        if len(raw) > 128 or re.fullmatch(rb'[a-z0-9_.-]+:[a-z0-9_./-]+', raw) is None:
            raise ValueError('Canonical bounded ResourceLocation')
        encoded_body = b'\x01' + varint(len(raw)) + raw
    return (bytes([2, 1, kind]) + encoded_body + struct.pack('>H', distance)
            + struct.pack('>iii', *phases) + struct.pack('>bbb', *rates)
            + struct.pack('>qq', epoch, sample))

def decode(raw):
    if len(raw) > 256:
        raise ValueError('Oversized sky frame')
    pos = 0
    def get(count):
        nonlocal pos
        if pos + count > len(raw):
            raise ValueError('Truncated frame')
        value = raw[pos:pos + count]
        pos += count
        return value
    if get(1) != b'\x02':
        raise ValueError('Schema')
    present = get(1)[0]
    if present not in (0, 1):
        raise ValueError('Context flag')
    if present == 0:
        if pos != len(raw):
            raise ValueError('Trailing NONE')
        return None
    kind = get(1)[0]
    bounded(kind, 0, 2)
    body_present = get(1)[0]
    if body_present not in (0, 1):
        raise ValueError('Body flag')
    body = None
    if body_present:
        size, shift, encoded = 0, 0, bytearray()
        while True:
            value = get(1)[0]
            encoded.append(value)
            size |= (value & 127) << shift
            if not value & 128:
                break
            shift += 7
            if shift >= 35:
                raise ValueError('VarInt width')
        if bytes(encoded) != varint(size) or size > 128:
            raise ValueError('Noncanonical/big ID length')
        body = get(size).decode('ascii')
        if re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9_./-]+', body) is None:
            raise ValueError('ID')
    if kind and body is None:
        raise ValueError('Missing known body')
    distance = struct.unpack('>H', get(2))[0]
    bounded(distance, 4, 190)
    phases = struct.unpack('>iii', get(12))
    rates = struct.unpack('>bbb', get(3))
    epoch, sample = struct.unpack('>qq', get(16))
    for base, rate in zip(phases, rates):
        phase(base, rate, epoch, sample)
    if pos != len(raw):
        raise ValueError('Trailing frame')
    return {'kind': kind, 'body': body, 'distance': distance, 'phases': phases,
            'rates': rates, 'epoch': epoch, 'sample': sample}

max_body = 'a:' + 'b' * 126
max_frame = packet(max_body, [359999, 270000, 0], [-60, 60, 0], LONG_MAX, LONG_MAX)
assert len(max_frame) == 167 <= 256
view = decode(max_frame)
assert view['epoch'] == view['sample'] == LONG_MAX and view['rates'] == (-60, 60, 0)
assert decode(b'\x02\x00') is None
fallback = packet(None, [0, 0, 0], [0, 0, 0], 0, 0, kind=0, distance=4)
assert len(fallback) == 37 and decode(fallback)['body'] is None
bad_frames = [max_frame + b'\x00', max_frame[:-1], b'\x03' + max_frame[1:],
              b'\x02\x02' + max_frame[2:], max_frame[:2] + b'\x03' + max_frame[3:],
              max_frame[:3] + b'\x02' + max_frame[4:], b'\x02\x00\x00']
for bad in bad_frames:
    refuses(lambda: decode(bad))
refuses(lambda: packet(max_body, [0, 0, 0], [0, 0, 0], 2, 1))
refuses(lambda: packet(max_body, [0, 0, 0], [-61, 0, 0], 0, 0))
GROUPS.append('joint_sky_schema2_exact167_bytes_strict_flags_ranges_epoch_decode_none_clear')

def extrapolate(base, rate, epoch, sample, local):
    target = min(LONG_MAX, sample + min(40, max(0, local)))
    return phase(base, rate, epoch, target)

assert extrapolate(123456, -60, 10000, 10000, 40) == 111456
assert extrapolate(123456, -60, 10000, 10000, 500) == 111456
assert extrapolate(123456, -60, 10000, 10000, -5) == 123456
assert extrapolate(270000, -60, 0, LONG_MAX, 40) == frozen
GROUPS.append('client40_tick_horizon_hold_negative_time_clamp_longmax_saturation')

actual = rows(R3 / 'covered-ledger.csv')
expected = [row for row in rows(ROOT / 'docs/work/v1.8.0-content-ledger.csv')
            if row['plan'].startswith('C17')]
assert actual == expected and len(actual) == len({row['unit_id'] for row in actual}) == 41
assert sha(R3 / 'covered-ledger.csv') == sha(SOURCE / 'revision-02/covered-ledger.csv')
assert sha(R3 / 'upstream-source-checks.json') == sha(SOURCE / 'revision-02/upstream-source-checks.json')
old_flight_max = 8 + 1 + 132 + 3 + 160 * (132 + 5) + 8 + 3 + 32 * (16 + 2 + 3 * 48 + 2 + 128)
new_flight_max = old_flight_max + 25 + 1 + 2 * 180
assert old_flight_max == 31419 and new_flight_max == 31805 < 32768
base = ROOT / 'src/main/java/io/github/sunthemoon/advancedrocketrycommunity'
modern = [
    'station/orbit/StationSkyContextService.java',
    'station/persistence/StationRegistrySavedData.java',
    'station/persistence/StationRegistryPayload.java',
    'station/persistence/StationNbtCodec.java',
    'station/service/StationWriteBudget.java',
    'station/model/StationLimits.java',
    'persistence/migration/ManagedSavedDataType.java',
    'persistence/migration/SavedDataSchemaMigrator.java',
    'satellite/mission/MonotonicMissionClock.java',
    'celestial/network/StationSkyContextPacket.java',
    'rocket/network/RocketFlightPlanPacket.java',
    'travel/network/TravelTargetWireCodec.java',
]
result = {
    'kind': 'independent_static_and_specification_model_not_runtime_or_native_writer',
    'proposal_sha256': sha(R3 / NAME), 'dispositions_sha256': sha(R3 / 'DISPOSITIONS-03.md'),
    'preceding_evidence_hashes_verified_unchanged': True,
    'specification_model_groups': GROUPS, 'phase_reference_cases': cases,
    'max_reduced_product': 21599700, 'clock_compound_standard_nbt_bytes': len(clock_nbt),
    'wire': {'station_sky_max': len(max_frame), 'station_sky_limit': 256,
             'station_none_bytes': 2, 'fallback_no_body_bytes': len(fallback),
             'flight_old_max': old_flight_max, 'flight_new_max': new_flight_max, 'flight_limit': 32768},
    'exact_current_ledger_rows_unchanged': 41,
    'upstream_source_identity_list_byte_unchanged_from_reviewed_r2': True,
    'batch_counts_not_acceptance': dict(Counter(row['plan'] for row in actual)),
    'proposed_station_root_record_schema': [5, 3],
    'proposed_clock_format': 1, 'proposed_sky_payload': 2,
    'proposed_channel_transitions': {'celestial_snapshot': [3, 4], 'rocket_flight': [8, 9]},
    'current_network_pin': (ROOT / 'src/test/resources/network-protocols.txt').read_text(),
    'modern_source_sha256': {name: sha(base / name) for name in modern},
    'pending_owner_decisions': ['D1', 'D1-disassembly', 'D3'],
    'runtime_native_gradle_jobs_run': 0, 'root_writes_by_reviewer': 0,
    'command': 'python -B <own fresh review Temp>/probe.py', 'exit_code': 0,
}
(OUT / 'static-evidence.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
hashes = {path.name: sha(path) for path in sorted(OUT.iterdir())
          if path.is_file() and path.name != 'FILE-HASHES.json'}
(OUT / 'FILE-HASHES.json').write_text(json.dumps(hashes, indent=2) + '\n', encoding='utf-8')
print(json.dumps({key: value for key, value in result.items()
                  if key not in ('modern_source_sha256', 'current_network_pin')}, indent=2))
