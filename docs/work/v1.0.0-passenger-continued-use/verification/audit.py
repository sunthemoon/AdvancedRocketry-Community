"""Check scoped continued-use evidence, including retained harness failures."""
from pathlib import Path
import hashlib
import json
import uuid

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[3]

def read(name):
    return json.loads((OUT / name).read_text(encoding='utf-8'))

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

inventory = read('source-inventory.json')
assert len(inventory['files']) == 745
for row in inventory['files']:
    path = ROOT / row['path']
    assert digest(path) == row['sha256'] and path.stat().st_size == row['size'], row['path']
assert digest(ROOT / 'build/libs/advancedrocketry-community-1.20.1-1.0.0-dev.jar') == inventory['artifact_sha256']
assert read('build-results.json')['java'] == dict(tests=399, failures=0, errors=0, skipped=0, suites=78)
log = (OUT / 'after-checks-2.txt').read_text(encoding='utf-8')
assert '49 GAME TESTS COMPLETE' in log and 'All 49 required tests passed' in log
assert 'active_vents=16 tracked=16 pending=0' in log

native = read('native/summary.json')
assert native['artifact_sha256'] == inventory['artifact_sha256']
assert native['status'] == 'COMPLETE_WITH_OBSERVATIONS' and not native['cleanup_errors']
assert len(native['processes']) == 3 and all(p['exit_code'] == 0 for p in native['processes'])
errors = [a for a in native['actions'] if 'error' in a]
assert sorted(json.loads(a['request'])['id'] for a in errors) == [2, 15]
actions = {a['request']['id']: a for a in native['actions'] if 'error' not in a}
assert 'passengers=1' in actions[7]['result']['receipt']
assert '[48.5d, 80.0d, 8.5d]' in actions[14]['result']['receipt']
assert 'passengers=2' in actions[22]['result']['receipt']
assert 'state=FUELED fuel=1000' in actions[26]['result']['receipt']
assert all('buffered_units: 118L' in actions[i]['result']['receipt'] for i in (25, 38))
assert 'state=LANDED fuel=618 capacity=1000 passengers=2' in actions[33]['result']['receipt']
server_log = (OUT / 'native/server-full.txt').read_text(encoding='utf-8')
assert 'action=LEAVE destination=advancedrocketrycommunity:earth station=null code=SUCCESS' in server_log
assert 'action=BOARD destination=advancedrocketrycommunity:earth station=null code=SUCCESS' in server_log
assert 'action=LAUNCH destination=advancedrocketrycommunity:earth station=null code=SUCCESS required_fuel=382' in server_log
assert 618 + 500 == 1000 + 118 == 618 + 118 + 382
for process in native['processes']:
    assert digest(OUT / 'native' / process['full_log_file']) == process['full_log_sha256']
    for row in process['native_logs']:
        assert digest(OUT / 'native' / row['file']) == row['sha256']
for row in native['screenshots']:
    assert digest(OUT / 'native' / row['file']) == row['sha256']

before = json.loads((ROOT / 'docs/work/v1.0.0-legacy-passenger/verification/resaved-world/projection.json').read_text())
after = read('after-world/projection.json')
resaved = read('resaved-world/projection.json')
assert after['world_owned_only'] and resaved['world_owned_only']
assert len(after['rockets']) == 1 and after['rockets'] == resaved['rockets']
rocket = after['rockets'][0]
old = before['rockets'][0]
for key in ('logical', 'state', 'fuel', 'passengers', 'blocks'):
    assert rocket[key] == old[key], key
assert old['dimension'] == 'advancedrocketrycommunity:moon' and rocket['dimension'] == 'minecraft:overworld'
assert rocket['entity'] != old['entity']
entity = uuid.UUID(rocket['entity']).int
parts = [(entity >> shift) & 0xffffffff for shift in (96, 64, 32, 0)]
nbt = '[I; ' + ', '.join(str(x if x < 2**31 else x - 2**32) for x in parts) + ']'
assert all(nbt in actions[i]['result']['receipt'] for i in (34, 35))
restart = read('clean-restart/summary.json')
assert restart['status'] == 'PASS' and restart['exit_code'] == 0
assert restart['artifact_sha256'] == inventory['artifact_sha256']
assert 'buffered_units: 118L' in restart['receipts'][3]['receipt']
assert digest(OUT / 'clean-restart/server-full.txt') == restart['full_log_sha256']
print(json.dumps(dict(status='PASS_SCOPED_CONTINUED_USE', source_files=745,
    java_tests=399, gametests=49, native_java_exits=[0, 0, 0], clean_restart='PASS',
    retained_control_errors=[2, 15], saved_rockets=1, fuel=618, buffered_fuel=118)))
