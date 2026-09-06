"""Audit native late-handler evidence; not whole-version acceptance."""
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
assert len(inventory['files']) == 738
for entry in inventory['files']:
    path = ROOT / entry['path']
    assert sha(path) == entry['sha256'] and path.stat().st_size == entry['size'], path
jar = ROOT / 'build/libs/advancedrocketry-community-1.20.1-1.0.0-dev.jar'
assert sha(jar) == inventory['artifact_sha256']
assert read(HERE / 'native/summary.json')['status'] == 'FAIL'
failed = read(HERE / 'native-2/summary.json')
assert failed['status'] == 'FAIL' and failed['probe_exit_code'] == 1
assert failed['client_exit_code'] == failed['server_exit_code'] == 0
assert 'Ambiguous method: <init>' in (HERE / 'native-2/probe-full.txt').read_text()
run = HERE / 'native-3'
summary = read(run / 'summary.json')
assert summary['status'] == 'PASS' and not summary['cleanup_errors']
assert summary['probe_exit_code'] == summary['client_exit_code'] == summary['server_exit_code'] == 0
assert summary['artifact_sha256'] == sha(jar) and not summary['authentication_tested']
expected = ['reject-closed', 'reject-reopened', 'reject-wrong-rocket', 'positive', 'reject-no-player']
assert summary['checks'] == ['PASS ' + name + ' thread=Render thread' for name in expected]
probe = (run / 'probe-full.txt').read_text()
assert 'container=1 rocket=179' in probe and 'menu=7073 container=2 rocket=179' in probe
assert 'HELD_SENTINEL captured-envelope=true destination=MOON quotes=111,222,333' in probe
for kind in ('client', 'server'):
    text = (run / (kind + '-full.txt')).read_text(encoding='utf-8')
    assert not scan_log(text.splitlines())
    for record in summary[kind + '_native_logs']:
        assert sha(run / record['file']) == record['sha256']
client = (run / 'client-full.txt').read_text()
server = (run / 'server-full.txt').read_text()
assert 'NVIDIA GeForce RTX 3070 Laptop GPU' in client
assert 'Connected to a modded server.' in client and 'V100Visual1 joined the game' in server
assert 'Late-frame disconnected-player probe' in server
assert 'name=V100Visual1,properties={},legacy=false]' not in server
assert 'flight_state=FUELED fuel=1000' in server
assert 'ARCE_FLIGHT_INTENT ' not in server
assert len(summary['screenshots']) == 4
for record in summary['screenshots']:
    path = run / record['file']
    data = path.read_bytes()
    assert sha(path) == record['sha256'] and len(data) == record['bytes']
    assert data[:8] == b'\x89PNG\r\n\x1a\n'
    assert list(struct.unpack('>II', data[16:24])) == record['client_size'] == [1280, 720]
counts = dict(tests=0, failures=0, errors=0, skipped=0)
files = list((HERE / 'java-results').glob('TEST-*.xml'))
assert len(files) == 75
for path in files:
    root = ET.parse(path).getroot()
    for key in counts:
        counts[key] += int(root.attrib.get(key, 0))
assert counts == dict(tests=391, failures=0, errors=0, skipped=0)
assert b'All 44 required tests passed' in (HERE / 'gametest-native.log').read_bytes()
assert (HERE / 'process-cleanup.txt').read_text().strip() == 'remaining_owned_native=0'
assert [r['exit_code'] for r in read(HERE / 'git-results.json')] == [0, 0, 1]
total = 0
for line in (HERE.parent / 'checksums.txt').read_text().splitlines():
    expected, relative = line.split('  ', 1)
    path = (HERE.parent / relative).resolve()
    assert path.is_relative_to(HERE.parent) and sha(path) == expected, relative
    total += 1
print(json.dumps(dict(status='PASS_SCOPED_AUDIT', native_handler_checks=5,
    screenshots=4, source_inputs=738, java_tests=391, game_tests=44,
    retained_harness_failures=2, checksums=total, artifact_sha256=sha(jar),
    release_or_full_network_approval=False)))
