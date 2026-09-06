"""Audit retained console observations without asserting full release acceptance."""
from pathlib import Path
import hashlib
import json
import re
import struct
import sys
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
RUN = HERE / 'attempt-1'
sys.path.insert(0, str(ROOT))
from scripts.run_dedicated_server_smoke import scan_log


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


inventory = json.loads((HERE / 'source-inventory.json').read_text())
assert len(inventory['files']) == 736
for item in inventory['files']:
    assert sha(ROOT / item['path']) == item['sha256'], item['path']
jar = ROOT / 'build/libs/advancedrocketry-community-1.20.1-1.0.0-dev.jar'
assert sha(jar) == inventory['artifact_sha256']
counts = dict(tests=0, failures=0, errors=0, skipped=0)
results = list((HERE / 'java-results').glob('TEST-*.xml'))
assert len(results) == 74
for path in results:
    suite = ET.parse(path).getroot()
    for key in counts:
        counts[key] += int(suite.attrib.get(key, 0))
assert counts == dict(tests=387, failures=0, errors=0, skipped=0), counts
assert b'All 44 required tests passed' in (HERE / 'gametest-native.log').read_bytes()
summary = json.loads((RUN / 'summary.json').read_text())
assert summary['status'] == 'COMPLETE_WITH_OBSERVATIONS'
assert summary['artifact_sha256'] == sha(jar)
assert not summary['authentication_tested'] and not summary['cleanup_errors']
assert not summary['owner_is_operator']
assert len(summary['processes']) == 3
assert {p['kind'] for p in summary['processes']} == {'client', 'client-2', 'server'}
for process in summary['processes']:
    assert process['exit_code'] == 0
    log = RUN / process['full_log_file']
    assert sha(log) == process['full_log_sha256']
    assert not scan_log(log.read_text(encoding='utf-8').splitlines()), log
    for native in process['native_logs']:
        assert sha(RUN / native['file']) == native['sha256']
for label in ('client', 'client-2'):
    log = (RUN / (label + '-full.txt')).read_text(encoding='utf-8')
    assert 'NVIDIA GeForce RTX 3070 Laptop GPU' in log
    assert 'Connected to a modded server.' in log
server = (RUN / 'server-full.txt').read_text(encoding='utf-8')
owner = summary['owner']
operator = '6482f9f6-ff26-3046-b7ba-86db04b972f8'
for filename in ('ops-after-setup.json', 'ops-after-test.json'):
    operators = json.loads((RUN / filename).read_text())
    assert [item['uuid'] for item in operators] == [operator]
receipts = [line for line in server.splitlines() if 'ARCE_FLIGHT_INTENT ' in line]
assert len(receipts) == 8
for index in range(4):
    launch, cancel = receipts[index * 2:index * 2 + 2]
    destination = 'space' if index < 2 else 'moon'
    station = summary['stations'][1] if index < 2 else 'null'
    fuel = '330' if index < 2 else '372'
    for line, player, action in ((launch, operator, 'LAUNCH'), (cancel, owner, 'CANCEL')):
        for marker in (f'player={player}', f'rocket={summary["entity"]}',
                       f'action={action}', f'destination=advancedrocketrycommunity:{destination}',
                       f'station={station}', 'code=SUCCESS', f'required_fuel={fuel}'):
            assert marker in line, (marker, line)
    transfer = re.search(r'request=([\da-f-]+)', launch).group(1)
    assert re.search(r'\[Server thread/INFO\].*ARCE_TRANSFER_RETURNED_TO_SOURCE transfer='
                     + transfer + r'.*reason=countdown_cancelled fuel=1000', server)
assert 'phase=DESTINATION_SPAWNED' not in server
reports = [line.split('ARCE_RELEASE_TEST_FLIGHT_REPORT ', 1)[1]
           for line in server.splitlines() if 'ARCE_RELEASE_TEST_FLIGHT_REPORT ' in line]
assert len(reports) == 3 and len(set(reports)) == 1
assert 'state=FUELED fuel=1000 capacity=1000 passengers=0 transfer=none' in reports[0]
assert f'entity={summary["entity"]}' in reports[0]
assert 'origin=384,101,384 blocks=5' in reports[0]
shots = list(summary['screenshots'])
assert len(shots) == 28
for kind in ('station', 'pointer', 'body', 'reopen'):
    records = json.loads((RUN / (kind + '-probe-records.json')).read_text())
    if kind in ('pointer', 'body'):
        assert not any(r['action'] in ('owner Tab', 'owner Enter') for r in records)
        assert any(r['action'] == 'owner cancel-button click' for r in records)
    shots.extend(r['result'] for r in records if isinstance(r.get('result'), dict)
                 and r['result'].get('source') == 'native Minecraft F2 screenshot')
assert len(shots) == len({shot['file'] for shot in shots}) == 41
assert {p.name for p in RUN.glob('*.png')} == {s['file'] for s in shots}
for shot in shots:
    path = RUN / shot['file']
    raw = path.read_bytes()
    assert sha(path) == shot['sha256'] and len(raw) == shot['bytes']
    assert raw[:8] == b'\x89PNG\r\n\x1a\n'
    assert list(struct.unpack('>II', raw[16:24])) == shot['client_size']
assert 'guiScale:4\n' in (RUN / 'owner-options-final.txt').read_text()
assert 'guiScale:2\n' in (RUN / 'operator-options-final.txt').read_text()
assert len(list((RUN / 'requests').glob('*.json'))) == 70
assert not any('error' in action for action in summary['actions'])
checksums = 0
for line in (HERE.parent / 'checksums.txt').read_text().splitlines():
    expected, name = line.split('  ', 1)
    path = (HERE.parent / name).resolve()
    assert path.is_relative_to(HERE.parent) and sha(path) == expected, name
    checksums += 1
print(json.dumps(dict(status='PASS_SCOPED_AUDIT', driver_status=summary['status'],
    source_inputs=736, java_tests=387, game_tests=44, native_processes=3,
    launch_cancel_pairs=4, screenshots=41, checksums=checksums,
    artifact_sha256=sha(jar), full_visual_or_release_approval=False)))
