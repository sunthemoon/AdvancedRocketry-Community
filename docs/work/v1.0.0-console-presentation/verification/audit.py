"""Check this presentation slice's retained evidence, not release approval."""
from pathlib import Path
import hashlib
import json
import re
import struct
import sys
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
GUI = HERE / 'gui'
sys.path.insert(0, str(ROOT))
from scripts.run_dedicated_server_smoke import scan_log


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def test_counts(folder, suites):
    paths = list(folder.glob('TEST-*.xml'))
    assert len(paths) == suites
    counts = dict(tests=0, failures=0, errors=0, skipped=0)
    for path in paths:
        root = ET.parse(path).getroot()
        for key in counts:
            counts[key] += int(root.attrib.get(key, 0))
    return counts


inventory = read(HERE / 'source-inventory.json')
assert len(inventory['files']) == 738
for item in inventory['files']:
    path = ROOT / item['path']
    assert sha(path) == item['sha256'] and path.stat().st_size == item['size'], path
jar = ROOT / 'build/libs/advancedrocketry-community-1.20.1-1.0.0-dev.jar'
assert sha(jar) == inventory['artifact_sha256']
assert jar.stat().st_size == 1255571
assert test_counts(HERE / 'before-results', 2) == dict(tests=10, failures=4, errors=0, skipped=0)
assert test_counts(HERE / 'java-results', 75) == dict(tests=391, failures=0, errors=0, skipped=0)
assert b'All 44 required tests passed' in (HERE / 'gametest-native.log').read_bytes()
summary = read(GUI / 'summary.json')
assert summary['status'] == 'COMPLETE_WITH_OBSERVATIONS'
assert summary['artifact_sha256'] == sha(jar)
assert not summary['authentication_tested'] and not summary['cleanup_errors']
assert not summary['owner_is_operator']
assert len(summary['processes']) == 3
for process in summary['processes']:
    assert process['exit_code'] == 0
    path = GUI / process['full_log_file']
    assert sha(path) == process['full_log_sha256']
    assert not scan_log(path.read_text(encoding='utf-8').splitlines()), path
    for native in process['native_logs']:
        assert sha(GUI / native['file']) == native['sha256']
for name, pid in (('client', summary['client_pid']), ('client-2', summary['second_client_pid'])):
    log = (GUI / (name + '-full.txt')).read_text(encoding='utf-8')
    assert 'NVIDIA GeForce RTX 3070 Laptop GPU' in log
    assert 'Connected to a modded server.' in log
    reconnect = read(GUI / ('manual-reconnect.json' if name == 'client'
                            else 'second-manual-reconnect.json'))
    assert reconnect['pid'] == pid
    assert not reconnect['process_restarted'] and not reconnect['timeouts_changed']
    assert (GUI / (name + '-initial-handshake-latest.log')).is_file()
server = (GUI / 'server-full.txt').read_text(encoding='utf-8')
login_disconnects = [line for line in server.splitlines()
                     if 'ServerLoginPacketListenerImpl' in line and 'lost connection: Disconnected' in line]
assert len(login_disconnects) == 2
assert 'name=V100Visual1,' in login_disconnects[0] and '[03:28:54]' in login_disconnects[0]
assert 'name=V100Visual2,' in login_disconnects[1] and '[03:31:42]' in login_disconnects[1]
operator = '6482f9f6-ff26-3046-b7ba-86db04b972f8'
for name in ('ops-after-setup.json', 'ops-after-test.json'):
    assert [entry['uuid'] for entry in read(GUI / name)] == [operator]
intents = [line for line in server.splitlines() if 'ARCE_FLIGHT_INTENT ' in line]
assert len(intents) == 2
for line, action, player in zip(intents, ('LAUNCH', 'CANCEL'), (operator, summary['owner'])):
    for marker in (f'player={player}', f'rocket={summary["entity"]}', f'action={action}',
                   'destination=advancedrocketrycommunity:moon', 'station=null',
                   'code=SUCCESS', 'required_fuel=372'):
        assert marker in line, (marker, line)
request = re.search(r'request=([\da-f-]+)', intents[0]).group(1)
assert re.search(r'\[Server thread/INFO\].*ARCE_TRANSFER_RETURNED_TO_SOURCE transfer='
                 + request + r'.*reason=countdown_cancelled fuel=1000', server)
reports = [line.split('ARCE_RELEASE_TEST_FLIGHT_REPORT ', 1)[1]
           for line in server.splitlines() if 'ARCE_RELEASE_TEST_FLIGHT_REPORT ' in line]
assert len(reports) == 2 and len(set(reports)) == 1
assert 'state=FUELED fuel=1000 capacity=1000 passengers=0 transfer=none' in reports[0]
assert 'phase=DESTINATION_SPAWNED' not in server
restart_dir = HERE / 'restart'
restart = read(restart_dir / 'summary.json')
assert restart['status'] == 'PASS' and restart['exit_code'] == 0
assert restart['artifact_sha256'] == sha(jar)
assert restart['report'].split('ARCE_RELEASE_TEST_FLIGHT_REPORT ', 1)[1] == reports[0]
assert sha(restart_dir / 'server-full.txt') == restart['full_log_sha256']
assert not scan_log((restart_dir / 'server-full.txt').read_text(encoding='utf-8').splitlines())
for native in restart['native_logs']:
    assert sha(restart_dir / native['file']) == native['sha256']
shots = list(summary['screenshots'])
assert len(shots) == 21
probe = read(GUI / 'body-probe-records.json')
assert any(record['action'] == 'owner cancel-button click' for record in probe)
assert not any(record['action'] in ('owner Tab', 'owner Enter') for record in probe)
shots.extend(record['result'] for record in probe if isinstance(record.get('result'), dict)
             and record['result'].get('source') == 'native Minecraft F2 screenshot')
shots.extend(read(GUI / name) for name in ('initial-connection-screenshot.json',
                                         '00b-screenshot.json', '00c-screenshot.json'))
shots.extend(read(GUI / 'second-manual-reconnect.json')['screenshots'])
assert len(shots) == len({shot['file'] for shot in shots}) == 30
assert {p.name for p in GUI.glob('*.png')} == {shot['file'] for shot in shots}
for shot in shots:
    path = GUI / shot['file']
    raw = path.read_bytes()
    assert sha(path) == shot['sha256'] and len(raw) == shot['bytes']
    assert raw[:8] == b'\x89PNG\r\n\x1a\n'
    assert list(struct.unpack('>II', raw[16:24])) == shot['client_size']
assert 'guiScale:4\n' in (GUI / 'owner-final-options.txt').read_text()
assert 'guiScale:2\n' in (GUI / 'operator-final-options.txt').read_text()
assert len(list((GUI / 'control-requests').glob('*.json'))) == 46
assert not any('error' in action for action in summary['actions'])
checksums = 0
for line in (HERE.parent / 'checksums.txt').read_text().splitlines():
    expected, name = line.split('  ', 1)
    path = (HERE.parent / name).resolve()
    assert path.is_relative_to(HERE.parent) and sha(path) == expected, name
    checksums += 1
print(json.dumps(dict(status='PASS_SCOPED_AUDIT', driver_status=summary['status'],
    source_inputs=738, java_tests=391, game_tests=44, native_processes=4,
    launch_cancel_pairs=1, screenshots=30, initial_handshake_failures_retained=2,
    checksums=checksums, artifact_sha256=sha(jar), full_visual_or_release_approval=False)))
