"""Audit this bounded passenger-position slice, not release acceptance."""
from pathlib import Path
import hashlib
import json
import struct
import sys
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
sys.path.insert(0, str(ROOT))
from scripts.run_dedicated_server_smoke import scan_log


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


inventory = read(HERE / 'source-inventory.json')
assert len(inventory['files']) == 740
assert len({r['path'] for r in inventory['files']}) == 740
for entry in inventory['files']:
    path = ROOT / entry['path']
    assert sha(path) == entry['sha256'] and path.stat().st_size == entry['size'], path
jar = ROOT / 'build/libs/advancedrocketry-community-1.20.1-1.0.0-dev.jar'
assert sha(jar) == inventory['artifact_sha256'] == 'e7511d8896b2d700d7834325c390886f6841397c47bf16b445c02e191234dba3'
assert jar.stat().st_size == 1257973
shot_counts = {}
for name, artifact in [('native', '46cb577653981a50638ff3d2bc32d0407dc4adaa5d7092fe73e65ae82bcd229c'),
                       ('fixed-native', sha(jar))]:
    run = HERE / name
    summary = read(run / 'summary.json')
    assert summary['status'] == 'COMPLETE_WITH_OBSERVATIONS'
    assert not summary['cleanup_errors'] and not summary['authentication_tested']
    assert not summary['owner_is_operator'] and summary['artifact_sha256'] == artifact
    assert read(run / 'ops-after-setup.json') == read(run / 'ops-after-test.json') == []
    assert len(summary['processes']) == 3
    for process in summary['processes']:
        assert process['exit_code'] == 0
        full = run / process['full_log_file']
        assert sha(full) == process['full_log_sha256']
        assert not scan_log(full.read_text(encoding='utf-8').splitlines())
        for record in process['native_logs']:
            assert sha(run / record['file']) == record['sha256']
    server = (run / 'server-full.txt').read_text(encoding='utf-8')
    intents = [line for line in server.splitlines() if 'ARCE_FLIGHT_INTENT ' in line]
    assert len(intents) == 3 and all('code=SUCCESS' in line for line in intents)
    assert sum('action=BOARD' in line for line in intents) == 2
    assert any('action=BOARD' in line and 'player=62bbb9cb-b2fa-39aa-9a6b-430b71f5493f' in line for line in intents)
    assert any('action=BOARD' in line and 'player=6482f9f6-ff26-3046-b7ba-86db04b972f8' in line for line in intents)
    assert any('action=LAUNCH' in line and 'required_fuel=382' in line for line in intents)
    assert server.index('event=countdown_complete') < server.index('V100Visual2 left the game') < server.index('event=destination_spawned') < server.index('event=landing_complete')
    actions = {a['request']['id']: a for a in summary['actions']}
    assert all('error' not in a for a in summary['actions'])
    for identifier in (2, 3, 31, 50, 51):
        assert ' on vehicle if entity @s[nbt={UUID:' in actions[identifier]['request']['command']
        assert 'MOUNTED' in actions[identifier]['result']['receipt']
    assert actions[50]['request']['command'].split('UUID:')[1] == actions[51]['request']['command'].split('UUID:')[1].replace('V100Visual2', 'V100Visual1')
    for identifier in (30, 54):
        assert 'state=LANDED fuel=618 capacity=1000 passengers=2 transfer=none origin=8,80,8 blocks=6' in actions[identifier]['result']['receipt']
    assert actions[4]['result']['receipt'].split('entity data:')[1] == actions[32]['result']['receipt'].split('entity data:')[1]
    assert '81.15d' in actions[34]['result']['receipt'] and '81.15d' in actions[53]['result']['receipt']
    assert 'advancedrocketrycommunity:moon' in actions[52]['result']['receipt']
    shots = {}

    def collect(value):
        if isinstance(value, dict):
            if value.get('source') == 'native Minecraft F2 screenshot':
                shots[value['file']] = value
            for child in value.values():
                collect(child)
        elif isinstance(value, list):
            for child in value:
                collect(child)

    collect(summary)
    for path in run.glob('*-records.json'):
        collect(read(path))
    if (run / 'look-input.json').exists():
        collect(read(run / 'look-input.json'))
    assert set(shots) == {p.name for p in run.glob('*.png')}
    for record in shots.values():
        path = run / record['file']
        data = path.read_bytes()
        assert sha(path) == record['sha256'] and len(data) == record['bytes']
        assert data[:8] == b'\x89PNG\r\n\x1a\n'
        assert list(struct.unpack('>II', data[16:24])) == record['client_size'] == [1280, 720]
    shot_counts[name] = len(shots)

restart = read(HERE / 'restart/summary.json')
assert restart['status'] == 'IN_PROGRESS' and restart['exit_code'] == 1
assert restart['artifact_sha256'] == sha(jar)
assert sha(HERE / 'restart/server-full.txt') == restart['full_log_sha256']
restart_text = (HERE / 'restart/server-full.txt').read_text(encoding='utf-8')
findings = scan_log(restart_text.splitlines())
assert any('action=REBUILD_DESTINATION' in line for line in findings)
assert 'entity=c7e43cc6-26eb-4268-a262-72501b30a7e2 operational=true' in restart_text
assert 'Timed out waiting for server marker' in (HERE / 'restart.txt').read_text(encoding='utf-8')
assert 'receipts' not in restart
for record in restart['native_logs']:
    assert sha(HERE / 'restart' / record['file']) == record['sha256']
counts = dict(tests=0, failures=0, errors=0, skipped=0)
files = list((HERE / 'java-results').glob('TEST-*.xml'))
assert len(files) == 76
for path in files:
    root = ET.parse(path).getroot()
    for key in counts:
        counts[key] += int(root.attrib.get(key, 0))
assert counts == dict(tests=395, failures=0, errors=0, skipped=0)
assert b'All 44 required tests passed' in (HERE / 'gametest-native.log').read_bytes()
assert (HERE / 'process-cleanup.txt').read_text().strip() == 'remaining_owned_native=0'
assert [r['exit_code'] for r in read(HERE / 'git-results.json')] == [0, 0, 1]
total = 0
for line in (HERE.parent / 'checksums.txt').read_text().splitlines():
    expected, relative = line.split('  ', 1)
    path = (HERE.parent / relative).resolve()
    assert path.is_relative_to(HERE.parent) and sha(path) == expected, relative
    total += 1
print(json.dumps(dict(status='PASS_EVIDENCE_INTEGRITY_WITH_FAILED_RESTART', screenshots=shot_counts,
    source_inputs=740, java_tests=395, game_tests=44, checksums=total,
    retained_before_visual_failure=True, restart_passed=False, visual_coordinates_require_image_review=True,
    artifact_sha256=sha(jar), full_passenger_or_release_approval=False)))
