"""Audit this development slice without changing native failure verdicts."""
from pathlib import Path
import hashlib
import json
import uuid
import zipfile

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[3]

def read(name):
    return json.loads((OUT / name).read_text(encoding='utf-8'))

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

inventory = read('source-inventory.json')
for row in inventory['files']:
    path = ROOT / row['path']
    assert digest(path) == row['sha256'] and path.stat().st_size == row['size'], row['path']
jar = ROOT / 'build/libs/advancedrocketry-community-1.20.1-1.0.0-dev.jar'
assert digest(jar) == inventory['artifact_sha256']
build = read('build-results.json')
assert build['java'] == dict(tests=399, failures=0, errors=0, skipped=0, suites=78)
game_log = (OUT / 'after-checks-2.txt').read_text(encoding='utf-8')
assert '46 GAME TESTS COMPLETE' in game_log and 'All 46 required tests passed' in game_log

manifest = read('legacy-world-manifest.json')
assert digest(OUT / 'legacy-world.zip') == manifest['zip_sha256']
original = Path(r'C:\Users\Administrator\AppData\Local\Temp\arce-passenger-fixed-ui\server\world')
with zipfile.ZipFile(OUT / 'legacy-world.zip') as archive:
    for row in manifest['files']:
        assert digest(original / row['file']) == row['sha256'], row['file']
        assert hashlib.sha256(archive.read(row['file'])).hexdigest() == row['sha256']

old = read('original-projection/projection.json')
before = read('before-world/projection.json')
after = read('after-world/projection.json')
resaved = read('resaved-world/projection.json')
assert len(old['rockets']) == 0 and len(before['rockets']) == 2
embedded = [p['root_vehicle'] for p in old['players'] if p['root_vehicle']]
assert len(embedded) == 1
assert after['world_owned_only'] and resaved['world_owned_only']
assert after['rockets'] == resaved['rockets'] and len(after['rockets']) == 1
rocket = after['rockets'][0]
for key in ('logical', 'snapshot', 'state', 'fuel', 'passengers', 'dimension', 'blocks', 'pos'):
    assert rocket[key] == embedded[0][key], key
    assert all(row[key] == embedded[0][key] for row in before['rockets']), key

native = read('after-native-2/summary.json')
assert native['status'] == 'FAIL' and 'failure' not in native
assert len(native['processes']) == 3 and all(p['exit_code'] == 0 for p in native['processes'])
assert len(native['cleanup_errors']) == 1 and 'REBUILD_DESTINATION' in native['cleanup_errors'][0]
assert native['artifact_sha256'] == inventory['artifact_sha256']
actions = {row['request']['id']: row for row in native['actions']}
assert all('error' not in row for row in native['actions'])
assert 'Test passed, count: 1' in actions[1]['result']['receipt']
entity = uuid.UUID(rocket['entity']).int
parts = [(entity >> shift) & 0xffffffff for shift in (96, 64, 32, 0)]
nbt = '[I; ' + ', '.join(str(x if x < 2**31 else x - 2**32) for x in parts) + ']'
assert all(nbt in actions[i]['result']['receipt'] for i in (3, 4))
assert len(native['screenshots']) == 2
for process in native['processes']:
    assert digest(OUT / 'after-native-2' / process['full_log_file']) == process['full_log_sha256']
    for row in process['native_logs']:
        assert digest(OUT / 'after-native-2' / row['file']) == row['sha256']
for row in native['screenshots']:
    assert digest(OUT / 'after-native-2' / row['file']) == row['sha256']
restart = read('clean-restart/summary.json')
assert restart['status'] == 'PASS' and restart['exit_code'] == 0
assert restart['artifact_sha256'] == inventory['artifact_sha256']
assert digest(OUT / 'clean-restart/server-full.txt') == restart['full_log_sha256']
print(json.dumps(dict(status='PASS_SCOPED_MIGRATION_AND_EVIDENCE',
    strict_migration_driver='FAIL_RETAINED', clean_restart='PASS',
    source_files=len(inventory['files']), original_files=len(manifest['files']),
    before_rockets=2, after_rockets=1, resaved_rockets=1, screenshots=2)))
