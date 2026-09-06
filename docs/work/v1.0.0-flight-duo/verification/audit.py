"""Audit scoped native observations while preserving the failed whole-run status."""
from pathlib import Path
import hashlib
import json
import struct
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
RUN = HERE / 'attempt-1'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


source = json.loads((ROOT / 'docs/work/v1.0.0-flight-quote/verification/source-inventory.json').read_text())
for row in source['files']:
    assert sha(ROOT / row['path']) == row['sha256'], row['path']
assert sha(ROOT / 'build/libs/advancedrocketry-community-1.20.1-1.0.0-dev.jar') == source['artifact_sha256']
summary = json.loads((RUN / 'summary.json').read_text(encoding='utf-8'))
assert summary['artifact_sha256'] == source['artifact_sha256']
assert summary['status'] == 'FAIL' and summary['cleanup_errors']
assert any('countdown_cancelled' in error for error in summary['cleanup_errors'])
assert len(summary['processes']) == 3
for row in summary['processes']:
    assert row['exit_code'] == 0
    for log in row['native_logs']:
        assert sha(RUN / log['file']) == log['sha256']
owner = b'player=62bbb9cb-b2fa-39aa-9a6b-430b71f5493f'
operator = b'player=6482f9f6-ff26-3046-b7ba-86db04b972f8'
lines = (RUN / 'server-full.txt').read_bytes().splitlines()
launch = [line for line in lines if b'ARCE_FLIGHT_INTENT request=ba1d6bdd-a439-4b48-bb7a-b12156a0dcec' in line]
assert len(launch) == 1 and operator in launch[0] and b'action=LAUNCH' in launch[0]
cancel = [line for line in lines if b'ARCE_FLIGHT_INTENT request=52c6b99f-2186-413a-a14c-3a37a54c9139' in line]
assert len(cancel) == 1 and owner in cancel[0] and b'action=CANCEL' in cancel[0]
for line in launch + cancel:
    assert b'destination=advancedrocketrycommunity:moon station=null code=SUCCESS required_fuel=247' in line
assert any(b'ARCE_RELEASE_TEST_FLIGHT_REPORT ' in line and b'state=FUELED fuel=670' in line
           and b'transfer=none origin=1024,128,0 blocks=5' in line for line in lines)
assert any(b'Made V100Visual1 no longer a server operator' in line for line in lines)
ops = json.loads((RUN / 'ops-after-test.json').read_text())
assert [entry['uuid'] for entry in ops] == ['6482f9f6-ff26-3046-b7ba-86db04b972f8']
assert b'lost connection: Disconnected' in (RUN / 'server-before-reconnect-latest.log').read_bytes()
for prefix in ('client', 'client-2'):
    raw = (RUN / (prefix + '-full.txt')).read_bytes()
    assert b'Connected to a modded server.' in raw
    assert b'NVIDIA GeForce RTX 3070 Laptop GPU' in raw
records = list(summary['screenshots'])
for name in ('duo-probe-records.json', 'duo-probe-2-records.json'):
    records.extend(row['result'] for row in json.loads((RUN / name).read_text()) if row['result'])
records.append(json.loads((RUN / 'disconnection-screenshot-receipt.json').read_text()))
for record in records:
    path = RUN / record['file']
    assert sha(path) == record['sha256']
    raw = path.read_bytes()
    assert len(raw) == record['bytes'] and raw[:8] == b'\x89PNG\r\n\x1a\n'
    assert list(struct.unpack('>II', raw[16:24])) == record['client_size']
assert len(list(RUN.glob('*.png'))) == 22
counts = dict(tests=0, failures=0, errors=0, skipped=0)
results = list((HERE / 'java-results').glob('TEST-*.xml'))
assert len(results) == 73
for path in results:
    suite = ET.parse(path).getroot()
    for key in counts:
        counts[key] += int(suite.attrib.get(key, 0))
assert counts == dict(tests=385, failures=0, errors=0, skipped=0), counts
assert b'All 44 required tests passed' in (HERE / 'gametest-latest.log').read_bytes()
checked = 0
for line in (HERE.parent / 'checksums.txt').read_text().splitlines():
    expected, name = line.split('  ', 1)
    path = (HERE.parent / name).resolve()
    assert path.is_relative_to(HERE.parent) and sha(path) == expected, name
    checked += 1
print(json.dumps(dict(status='PASS_SCOPED_OBSERVATION_AUDIT', driver_status='FAIL',
                     source_inputs=len(source['files']), native_processes=3, screenshots=22,
                     screenshot_receipts=len(records), checksums=checked,
                     java_tests=counts['tests'], game_tests=44,
                     station_uuid_subcase_complete=False, release_gate_approval=False)))
