import csv
import hashlib
import json
from collections import Counter
from pathlib import Path

ROOT = Path('D:/GitHub/AdvancedRocketry-Community')
PROPOSAL = Path('C:/Users/Administrator/AppData/Local/Temp/arce-v180-c17-contract-489381f9d14b45928ec89711c681ed16/revision-02')
C18 = Path('C:/Users/Administrator/AppData/Local/Temp/arce-v180-c18-contract-r2-72caa8107091470eb65132167af7121c/ADR-066-CLASSIC-LIFE-SUPPORT-EQUIPMENT-RESEARCH-AND-PRESENTATION.md')
UPSTREAM = Path('C:/Users/Administrator/AppData/Local/Temp/arce-v180-c14-189c61a748584cab97c5881f8bdc1251/upstream/ar')
OUT = Path(__file__).parent

def rows(path):
    with path.open(encoding='utf-8-sig', newline='') as source:
        return list(csv.DictReader(source))

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

draft = PROPOSAL / 'ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md'
assert sha(draft) == '24158c9bb75fe7455a3ddf2bf9781b1a4a33a99b313f8d830a381825e584f0e6'
assert sha(C18) == '7c6da4ab6f3974d537813ed3662f1f072f86c3cf13990b1d1afb685045ecb976'
actual = rows(PROPOSAL / 'covered-ledger.csv')
expected = [row for row in rows(ROOT / 'docs/work/v1.8.0-content-ledger.csv') if row['plan'].startswith('C17')]
assert actual == expected
assert len(actual) == len({row['unit_id'] for row in actual}) == 41
assert all(row['disposition'] == 'PLANNED' for row in actual)
assert all('`' + row['unit_id'] + '`' in draft.read_text(encoding='utf-8') for row in actual)
manifest = {row['path']: row['sha256'] for row in rows(ROOT / 'legacy-manifest/java-files.csv')}
checked = []
for source in json.loads((PROPOSAL / 'upstream-source-checks.json').read_text()):
    identity = sha(UPSTREAM / source['path'])
    assert identity == source['sha256'] == manifest[source['path']]
    checked.append({'path': source['path'], 'sha256': identity})
assert len(checked) == 26
# Values independently read from actual wire constants/layout and proposed fixed detail.
old_max = 8 + 1 + (1 + 1 + 2 + 128) + 3 + 160 * (132 + 5) + 8 + 3 + 32 * (16 + 2 + 3 * 48 + 2 + 128)
bank = 2 + 1 + 2 + 128 + 3 * 4 + 32
new_max = old_max + 25 + 1 + 2 * 180
sky_max = 1 + 1 + 1 + 1 + 2 + 128 + 2 + 3 * 4 + 3 + 2 * 8
assert bank <= 180 and new_max <= 32768 and sky_max <= 256
base = ROOT / 'src/main/java/io/github/sunthemoon/advancedrocketrycommunity'
modern = [
    'api/endgame/EndgameEffect.java', 'api/endgame/EndgameEffectEvent.java',
    'api/version/ApiVersions.java', 'api/rocket/RocketComponentDefinition.java',
    'rocket/server/RocketDisassemblyService.java',
    'satellite/mission/SatelliteMissionRegistry.java',
    'satellite/mission/MonotonicMissionClock.java',
    'station/orbit/StationSkyContextService.java',
    'station/persistence/StationRegistrySavedData.java',
    'station/persistence/StationNbtCodec.java',
    'rocket/network/RocketFlightPlanPacket.java',
    'rocket/network/RocketFlightIntentPacket.java',
    'travel/network/TravelTargetWireCodec.java',
]
result = {
    'draft_sha256': sha(draft), 'dispositions_sha256': sha(PROPOSAL / 'DISPOSITIONS-02.md'),
    'covered_ledger_sha256': sha(PROPOSAL / 'covered-ledger.csv'),
    'joint_c18_draft_sha256': sha(C18),
    'exact_current_ledger_rows': 41, 'unique_ids': 41,
    'batches': dict(Counter(row['plan'] for row in actual)),
    'rehashed_approved_sources': checked,
    'current_network_table': (ROOT / 'src/test/resources/network-protocols.txt').read_text(),
    'independent_static_wire_bounds': {'old_flight_max': old_max, 'actual_bank_max': bank,
                                    'new_flight_max': new_max, 'sky_max': sky_max},
    'modern_source_sha256': {name: sha(base / name) for name in modern},
    'runtime_tests_run': False, 'native_performance_run': False, 'repository_writes': False,
    'command': 'python -B <own review Temp>/probe.py', 'exit_code': 0,
}
(OUT / 'static-evidence.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
(OUT / 'FILE-HASHES.json').write_text(json.dumps({path.name: sha(path) for path in sorted(OUT.iterdir())
    if path.is_file() and path.name != 'FILE-HASHES.json'}, indent=2) + '\n', encoding='utf-8')
print(json.dumps({key: value for key, value in result.items()
                  if key not in ('rehashed_approved_sources', 'current_network_table', 'modern_source_sha256')}))
