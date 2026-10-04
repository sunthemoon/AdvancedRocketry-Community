"""Small specification-model/static probe, not a Java/Forge/runtime test."""
import copy
import csv
import hashlib
import json
from collections import Counter
from pathlib import Path

ROOT = Path('D:/GitHub/AdvancedRocketry-Community')
UPSTREAM = Path('C:/Users/Administrator/AppData/Local/Temp/arce-v180-c14-189c61a748584cab97c5881f8bdc1251/upstream/ar')
OUT = Path(__file__).parent
LONG_MAX = (1 << 63) - 1


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def rows(path):
    with path.open(encoding='utf-8-sig', newline='') as stream:
        return list(csv.DictReader(stream))


def require_tick(value):
    if type(value) is not int or not 0 <= value <= LONG_MAX:
        raise ValueError('Invalid logical tick')
    return value


def advance(tick):
    return min(require_tick(tick) + 1, LONG_MAX)


def phase(at_epoch, rate, epoch, sample):
    require_tick(epoch)
    require_tick(sample)
    if epoch > sample or not 0 <= at_epoch < 360000 or not -60 <= rate <= 60:
        raise ValueError('Invalid phase/time ordering')
    delta = sample - epoch
    return (at_epoch + (delta % 72000) * rate * 5) % 360000


def validate_model(root):
    # Only the newly specified clock/epoch portion; not the actual full native root codec.
    if root['schema'] != 5:
        raise ValueError('Future/missing schema')
    clock = root['orbital_clock']
    if set(clock) != {'schema_version', 'logical_tick'} or type(clock['schema_version']) is not int or clock['schema_version'] != 1:
        raise ValueError('Invalid clock compound')
    tick = require_tick(clock['logical_tick'])
    for record in root['stations']:
        epoch = require_tick(record['controls']['epoch'])
        if epoch > tick:
            raise ValueError('Epoch ahead of same-root clock')
    return tick


def migrate_model(old):
    if old['schema'] == 5:
        validate_model(old)
        return copy.deepcopy(old)
    if old['schema'] != 4:
        raise ValueError('Existing earlier migrator is outside this model')
    candidate = copy.deepcopy(old)
    candidate['schema'] = 5
    candidate['orbital_clock'] = {'schema_version': 1, 'logical_tick': 0}
    for record in candidate['stations']:
        record['controls'] = {'distance': 4, 'phases': [record['sun_angle'], 0, 0],
                              'rates': [0, 0, 0], 'epoch': 0}
    validate_model(candidate)
    return candidate


proposal = OUT / 'ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md'
draft = proposal.read_text(encoding='utf-8')
coverage = rows(OUT / 'covered-ledger.csv')
expected = [row for row in rows(ROOT / 'docs/work/v1.8.0-content-ledger.csv')
            if row['plan'] in ('C17a', 'C17b', 'C17c')]
assert coverage == expected and len(coverage) == len({row['unit_id'] for row in coverage}) == 41
assert Counter(row['plan'] for row in coverage) == {'C17a': 24, 'C17b': 9, 'C17c': 8}
assert all('`' + row['unit_id'] + '`' in draft for row in coverage)
assert sha(OUT / 'covered-ledger.csv') == sha(OUT.parent / 'revision-02/covered-ledger.csv')
assert 'status: PROPOSED' in draft and 'revision: 3' in draft
assert 'owner_decisions_confirmed: [D2-A]' in draft
assert 'owner_decisions_pending: [D1, D1-disassembly, D3]' in draft
assert 'accepted_by: ""' in draft and 'accepted_at: ""' in draft
assert draft.endswith('\n') and all(line == line.rstrip() for line in draft.splitlines())
assert '](' not in draft  # Portable ADR currently uses inline source paths only.

manifest = {row['path']: row['sha256'] for row in rows(ROOT / 'legacy-manifest/java-files.csv')}
inventory = json.loads((ROOT / 'docs/work/v1.8.0-legacy-inventory.json').read_text(encoding='utf-8'))
sources = json.loads((OUT / 'upstream-source-checks.json').read_text(encoding='utf-8'))
assert len(sources) == 26
for source in sources:
    assert sha(UPSTREAM / source['path']) == source['sha256'] == manifest[source['path']] == inventory['sources'][source['path']]

model_fixtures = []
old = {'schema': 4, 'stations': [{'identity': 'unchanged-station', 'owner': 'same-owner',
       'gravity': 0, 'vacuum': True, 'sun_angle': 270000, 'team': ['member'],
       'controls': None}], 'reservations': [], 'balances': {'unchanged-station': 42}}
migrated = migrate_model(old)
assert migrated['orbital_clock']['logical_tick'] == 0
assert migrated['stations'][0]['controls']['epoch'] == 0
assert migrated['stations'][0]['controls']['rates'] == [0, 0, 0]
assert {key: value for key, value in migrated['stations'][0].items() if key != 'controls'} == {
       key: value for key, value in old['stations'][0].items() if key != 'controls'}
assert migrated['balances'] == old['balances'] and migrate_model(migrated) == migrated
model_fixtures.append('root4_initialization_exact_fields_repeat_noop')

arithmetic_cases = 0
for rate in range(-60, 61):
    for delta in (0, 1, 71999, 72000, 144001, LONG_MAX):
        for at_epoch in (0, 270000, 359999):
            assert phase(at_epoch, rate, 0, delta) == (at_epoch + delta * rate * 5) % 360000
            assert abs((delta % 72000) * rate * 5) <= 21599700
            arithmetic_cases += 1
model_fixtures.append('positive_negative_zero_all_rates_long_elapsed_modular_arithmetic')

changed = copy.deepcopy(migrated)
changed['orbital_clock']['logical_tick'] = 10000
changed['stations'][0]['controls'].update({'epoch': 10000, 'phases': [123456, 0, 0], 'rates': [-60, 0, 0]})
assert validate_model(changed) == 10000
saved = json.loads(json.dumps(changed))
restored_tick = validate_model(saved)
assert phase(123456, -60, 10000, restored_tick) == 123456
# No independently saved world/session/wall time argument is an input to this model.
for irrelevant_other_time in (0, 42, 9999, LONG_MAX):
    assert validate_model(saved) == 10000
    assert advance(restored_tick) == 10001
model_fixtures.append('clean_restart_zero_offline_advance_independent_time_ignored')

for bad_clock in ({'schema_version': 2, 'logical_tick': 10000},
                  {'schema_version': 1, 'logical_tick': -1},
                  {'schema_version': 1, 'logical_tick': True},
                  {'schema_version': 1, 'logical_tick': 10000, 'extra': 1},
                  {'schema_version': 1, 'logical_tick': 9999}):
    bad = copy.deepcopy(changed)
    bad['orbital_clock'] = bad_clock
    original_bytes = json.dumps(bad, sort_keys=True).encode()
    try:
        validate_model(bad)
    except (ValueError, KeyError):
        pass
    else:
        raise AssertionError('Invalid clock/epoch model accepted')
    assert json.dumps(bad, sort_keys=True).encode() == original_bytes
missing = copy.deepcopy(changed)
del missing['orbital_clock']
try:
    validate_model(missing)
except KeyError:
    pass
else:
    raise AssertionError('Missing clock accepted')
model_fixtures.append('same_root_epoch_ahead_future_missing_type_bounds_preserve_input')

assert advance(LONG_MAX - 1) == LONG_MAX and advance(LONG_MAX) == LONG_MAX
frozen = phase(270000, -60, 0, LONG_MAX)
assert phase(frozen, 0, LONG_MAX, LONG_MAX) == frozen
assert all(phase(frozen, 0, LONG_MAX, min(LONG_MAX + delta, LONG_MAX)) == frozen for delta in (0, 1, 40))
model_fixtures.append('saturation_no_wrap_authorized_stop_preserves_phase')

# A healthy rejected candidate retains the old co-committed pair; reload may choose
# either coherent acknowledged old root or exactly committed new candidate after unknown outcome.
for coherent in (migrated, changed):
    assert validate_model(coherent) >= coherent['stations'][0]['controls']['epoch']
assert validate_model(copy.deepcopy(migrated)) == 0
assert validate_model(copy.deepcopy(changed)) == 10000
assert phase(123456, -60, 10000, 10040) != phase(123456, -60, 10000, 10000)
assert phase(123456, -60, 10000, 10000) == 123456  # Unsaved visual advance may rewind on abrupt stop.
model_fixtures.append('old_new_coherent_reload_unknown_refusal_autosave_visual_checkpoint')
assert min(max(500, 0), 40) == 40
model_fixtures.append('wire_epoch_sample_and_render_horizon40')

base = ROOT / 'src/main/java/io/github/sunthemoon/advancedrocketrycommunity'
protocol = (ROOT / 'src/test/resources/network-protocols.txt').read_text(encoding='utf-8')
assert 'celestial_snapshot|3|1|StationSkyContextPacket|PLAY_TO_CLIENT' in protocol
assert 'rocket_flight|8|0|RocketFlightIntentPacket|PLAY_TO_SERVER' in protocol
assert 'new ApiVersion(1, 8)' in (base / 'api/version/ApiVersions.java').read_text(encoding='utf-8')
old_flight_max = 8 + 1 + 132 + 3 + 160 * (132 + 5) + 8 + 3 + 32 * (16 + 2 + 3 * 48 + 2 + 128)
assert old_flight_max + 386 <= 32 * 1024
assert 1 + 1 + 1 + (1 + 2 + 128) + 2 + 3 * 4 + 3 + 8 + 8 <= 256

result = {
    'kind': 'author_static_and_clock_specification_model_not_runtime', 'revision': 3,
    'proposal_sha256': sha(proposal), 'exact_ledger_rows': 41,
    'slice_counts': dict(Counter(row['plan'] for row in coverage)),
    'coverage_unchanged': True, 'rehashed_approved_sources': 26, 'source_mismatches': [],
    'model_fixtures': model_fixtures, 'phase_arithmetic_cases': arithmetic_cases,
    'max_reduced_rate_product': 21599700,
    'wire_bounds_unchanged': {'flight': old_flight_max + 386, 'station_sky': 167},
    'modern_clock_baseline_sha256': {path: sha(base / path) for path in (
        'station/persistence/StationRegistrySavedData.java',
        'station/persistence/StationRegistryPayload.java',
        'station/service/StationWriteBudget.java',
        'satellite/mission/MonotonicMissionClock.java')},
    'owner_confirmed': ['D2-A'], 'owner_pending': ['D1', 'D1-disassembly', 'D3'],
    'proposal_status': 'PROPOSED', 'runtime_tests_run': False,
    'repository_writes': False, 'required_gates_satisfied': False,
    'command': 'python -B revision-03/probe-03.py', 'exit_code': 0,
}
(OUT / 'static-checks-03.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
artifacts = {path.name: sha(path) for path in sorted(OUT.iterdir())
             if path.is_file() and path.name != 'artifact-hashes.json'}
(OUT / 'artifact-hashes.json').write_text(json.dumps(artifacts, indent=2) + '\n', encoding='utf-8')
print(json.dumps({key: result[key] for key in ('proposal_sha256', 'exact_ledger_rows',
      'slice_counts', 'rehashed_approved_sources', 'model_fixtures', 'phase_arithmetic_cases',
      'wire_bounds_unchanged', 'runtime_tests_run', 'repository_writes', 'exit_code')}, indent=2))
