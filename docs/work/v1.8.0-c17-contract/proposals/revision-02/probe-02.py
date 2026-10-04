import csv
import hashlib
import json
from collections import Counter
from pathlib import Path

ROOT = Path('D:/GitHub/AdvancedRocketry-Community')
UPSTREAM = Path('C:/Users/Administrator/AppData/Local/Temp/arce-v180-c14-189c61a748584cab97c5881f8bdc1251/upstream/ar')
OUT = Path(__file__).parent


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def rows(path):
    with path.open(encoding='utf-8-sig', newline='') as stream:
        return list(csv.DictReader(stream))


proposal = OUT / 'ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md'
draft = proposal.read_text(encoding='utf-8')
coverage = rows(OUT / 'covered-ledger.csv')
expected = [row for row in rows(ROOT / 'docs/work/v1.8.0-content-ledger.csv')
            if row['plan'] in ('C17a', 'C17b', 'C17c')]
assert coverage == expected
assert len(coverage) == len({row['unit_id'] for row in coverage}) == 41
assert Counter(row['plan'] for row in coverage) == {'C17a': 24, 'C17b': 9, 'C17c': 8}
assert all('`' + row['unit_id'] + '`' in draft for row in coverage)
assert all(row['disposition'] == 'PLANNED' for row in coverage)
assert sha(OUT / 'covered-ledger.csv') == sha(OUT.parent / 'covered-ledger.csv')
assert 'status: PROPOSED' in draft and 'revision: 2' in draft
assert 'owner_decisions_confirmed: [D2-A]' in draft
assert 'owner_decisions_pending: [D1, D1-disassembly, D3]' in draft
assert 'accepted_by: ""' in draft and 'accepted_at: ""' in draft
assert 'EndgameEffectAction' not in draft and 'LASER_MINING' not in draft
assert draft.endswith('\n') and all(line == line.rstrip() for line in draft.splitlines())
dispositions = (OUT / 'DISPOSITIONS-02.md').read_text(encoding='utf-8')
assert all('| ' + finding + ' ' in dispositions
           for finding in ('M1', 'M2', 'M3', 'M4', 'M5', 'L1', 'L2', 'L3'))

manifest = {row['path']: row['sha256'] for row in rows(ROOT / 'legacy-manifest/java-files.csv')}
inventory = json.loads((ROOT / 'docs/work/v1.8.0-legacy-inventory.json').read_text(encoding='utf-8'))
sources = json.loads((OUT / 'upstream-source-checks.json').read_text(encoding='utf-8'))
assert inventory['upstream_commit'] == 'c5cd5af62fc07cd4e0d24f06a16033f181c47c04'
assert len(sources) == 26
for source in sources:
    actual = sha(UPSTREAM / source['path'])
    assert actual == source['sha256'] == manifest[source['path']] == inventory['sources'][source['path']]

base = ROOT / 'src/main/java/io/github/sunthemoon/advancedrocketrycommunity'
protocol = (ROOT / 'src/test/resources/network-protocols.txt').read_text(encoding='utf-8')
assert 'celestial_snapshot|3|1|StationSkyContextPacket|PLAY_TO_CLIENT' in protocol
assert 'rocket_flight|8|0|RocketFlightIntentPacket|PLAY_TO_SERVER' in protocol
assert 'new ApiVersion(1, 8)' in (base / 'api/version/ApiVersions.java').read_text(encoding='utf-8')
enum = (base / 'api/endgame/EndgameEffect.java').read_text(encoding='utf-8')
assert all(kind in enum for kind in ('BLOCK_BREAK', 'ENTITY_GRAVITY', 'TELEPORT'))
assert 'FIELD_PROJECTION' not in enum
assert 'satellites.remove(satelliteId)' in (base / 'satellite/mission/SatelliteMissionRegistry.java').read_text(encoding='utf-8')

# Exact conservative arithmetic from current MAX_QUOTES/MAX_ACCESSIBLE_DESTINATIONS and wire widths.
old_flight_max = 8 + 1 + 132 + 3 + 160 * (132 + 5) + 8 + 3 + 32 * (16 + 2 + 3 * 48 + 2 + 128)
bank_detail_actual_max = 2 + (1 + 2 + 128) + 3 * 4 + 32
assert bank_detail_actual_max <= 180
new_flight_max = old_flight_max + 25 + 1 + 2 * 180
assert new_flight_max <= 32 * 1024
sky_max = 1 + 1 + 1 + (1 + 2 + 128) + 2 + 3 * 4 + 3 + 8 + 8
assert sky_max <= 256

modern_paths = [
    'api/endgame/EndgameEffect.java', 'api/endgame/EndgameEffectEvent.java',
    'api/version/ApiVersions.java', 'api/rocket/RocketComponentDefinition.java',
    'rocket/server/RocketDisassemblyService.java',
    'satellite/mission/SatelliteMissionRegistry.java',
    'celestial/network/StationSkyContextPacket.java',
    'rocket/network/RocketFlightIntentPacket.java',
    'rocket/network/RocketFlightPlanPacket.java',
    'rocket/menu/RocketFlightQuotes.java', 'station/model/StationLimits.java',
]
result = {
    'schema': 1, 'revision': 2, 'proposal_sha256': sha(proposal),
    'exact_ledger_rows': 41, 'slice_counts': dict(Counter(row['plan'] for row in coverage)),
    'missing_unit_ids': [], 'coverage_byte_identical_to_revision_1': True,
    'upstream_commit': inventory['upstream_commit'], 'rehashed_approved_sources': 26,
    'source_manifest_inventory_mismatches': [],
    'proposal_status': 'PROPOSED', 'owner_confirmed': ['D2-A'],
    'owner_pending': ['D1', 'D1-disassembly', 'D3'],
    'dispositions_present': ['M1', 'M2', 'M3', 'M4', 'M5', 'L1', 'L2', 'L3'],
    'proposal_trailing_whitespace': False,
    'actual_protocol_table': protocol,
    'actual_api_version': '1.8',
    'proposed_api_version': '1.9',
    'proposed_protocol_transitions': {'celestial_snapshot': [3, 4], 'rocket_flight': [8, 9], 'classic_controls': [None, 1]},
    'static_wire_bounds': {'old_flight_max': old_flight_max, 'new_flight_max': new_flight_max,
                          'bank_detail_actual_max': bank_detail_actual_max, 'sky_max': sky_max},
    'modern_source_sha256': {path: sha(base / path) for path in modern_paths},
    'runtime_tests_run': False, 'native_performance_run': False,
    'repository_writes': False, 'required_gates_satisfied': False,
    'command': 'python -B revision-02/probe-02.py', 'exit_code': 0,
}
(OUT / 'static-checks-02.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
artifacts = {path.name: sha(path) for path in sorted(OUT.iterdir())
             if path.is_file() and path.name != 'artifact-hashes.json'}
(OUT / 'artifact-hashes.json').write_text(json.dumps(artifacts, indent=2) + '\n', encoding='utf-8')
print(json.dumps({key: result[key] for key in ('revision', 'proposal_sha256', 'exact_ledger_rows',
      'slice_counts', 'rehashed_approved_sources', 'owner_confirmed', 'owner_pending',
      'static_wire_bounds', 'runtime_tests_run', 'repository_writes', 'exit_code')}, indent=2))
