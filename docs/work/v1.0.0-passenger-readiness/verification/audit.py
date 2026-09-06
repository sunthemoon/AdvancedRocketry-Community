"""Audit readiness unit/Forge and packaged-restart evidence, not native queue timing."""
from pathlib import Path
import hashlib
import json

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[3]

def read(name):
    return json.loads((OUT / name).read_text(encoding='utf-8'))

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

inventory = read('source-inventory.json')
assert len(inventory['files']) == 747
for row in inventory['files']:
    assert digest(ROOT / row['path']) == row['sha256'], row['path']
assert digest(ROOT / 'build/libs/advancedrocketry-community-1.20.1-1.0.0-dev.jar') == inventory['artifact_sha256']
assert read('build-results.json')['java'] == dict(tests=405, failures=0, errors=0, skipped=0, suites=79)
log = (OUT / 'after-checks-2.txt').read_text(encoding='utf-8')
assert '50 GAME TESTS COMPLETE' in log and 'All 50 required tests passed' in log
before_log = (OUT / 'before-gametest.txt').read_text(encoding='utf-8')
assert 'loginwaitsfortheplayersentitychunk failed! Login moved the player before entity storage was ready' in before_log
restart = read('clean-restart/summary.json')
assert restart['status'] == 'PASS' and restart['exit_code'] == 0
assert restart['artifact_sha256'] == inventory['artifact_sha256']
assert digest(OUT / 'clean-restart/server-full.txt') == restart['full_log_sha256']
for row in restart['native_logs']:
    assert digest(OUT / 'clean-restart' / row['file']) == row['sha256']
assert 'buffered_units: 118L' in restart['receipts'][3]['receipt']
before = json.loads((ROOT / 'docs/work/v1.0.0-passenger-continued-use/verification/resaved-world/projection.json').read_text())
after = read('resaved-world/projection.json')
assert after['world_owned_only'] and len(after['rockets']) == 1
assert before['rockets'] == after['rockets']
original = Path(r'C:\Users\Administrator\AppData\Local\Temp\arce-passenger-continued-restart\world')
for row in before['files']:
    assert digest(original / row['file']) == row['sha256'], row['file']
print(json.dumps(dict(status='PASS_SCOPED_READINESS_AND_RESTART',
    source_files=747, java_tests=405, gametests=50, restart='PASS',
    native_deferred_queue_timing='NOT_TESTED', saved_rockets=1)))
