"""Verify this persistence slice's retained evidence, not whole-version Gates."""
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


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


inventory = read(HERE / 'source-inventory.json')
assert len(inventory['files']) == len({r['path'] for r in inventory['files']}) == 742
for record in inventory['files']:
    path = ROOT / record['path']
    assert sha(path) == record['sha256'] and path.stat().st_size == record['size'], path
jar = ROOT / 'build/libs/advancedrocketry-community-1.20.1-1.0.0-dev.jar'
assert sha(jar) == inventory['artifact_sha256'] == 'e8a066ca4be3b4fcf179fad88a72b502ba65d8c8b6695bcba5aca04293739af0'
assert jar.stat().st_size == inventory['artifact_bytes'] == 1262050
assert b'Mounted rocket was excluded from world saving' in (HERE / 'before-gametest-3.txt').read_bytes()
assert b'4 required tests failed' in (HERE / 'before-gametest-3.txt').read_bytes()
assert b'compileJava FAILED' in (HERE / 'before-gametest.txt').read_bytes()
assert b'advancedrocketrycommunity:advancedrocketrycommunity:empty' in (HERE / 'before-gametest-2.txt').read_bytes()
assert b'All 45 required tests passed' in (HERE / 'after-gametest-native.log').read_bytes()
counts = dict(tests=0, failures=0, errors=0, skipped=0)
xml = list((HERE / 'java-results').glob('TEST-*.xml'))
assert len(xml) == 77
for path in xml:
    root = ET.parse(path).getroot()
    for key in counts:
        counts[key] += int(root.attrib.get(key, 0))
assert counts == dict(tests=397, failures=0, errors=0, skipped=0)
shots = {}
for name in ('native', 'restart-native'):
    run = HERE / name
    summary = read(run / 'summary.json')
    assert summary['status'] == 'COMPLETE_WITH_OBSERVATIONS' and not summary['cleanup_errors']
    assert summary['artifact_sha256'] == sha(jar) and not summary['authentication_tested']
    assert read(run / 'ops-after-setup.json') == read(run / 'ops-after-test.json') == []
    assert len(summary['processes']) == 3
    for process in summary['processes']:
        assert process['exit_code'] == 0
        full = run / process['full_log_file']
        assert sha(full) == process['full_log_sha256']
        assert not scan_log(full.read_text(encoding='utf-8').splitlines())
        for record in process['native_logs']:
            assert sha(run / record['file']) == record['sha256']
    found = {}

    def collect(value):
        if isinstance(value, dict):
            if value.get('source') == 'native Minecraft F2 screenshot':
                found[value['file']] = value
            for child in value.values():
                collect(child)
        elif isinstance(value, list):
            for child in value:
                collect(child)

    collect(summary)
    for path in run.glob('*-records.json'):
        collect(read(path))
    assert set(found) == {p.name for p in run.glob('*.png')}
    for record in found.values():
        path = run / record['file']
        raw = path.read_bytes()
        assert sha(path) == record['sha256'] and len(raw) == record['bytes']
        assert raw[:8] == b'\x89PNG\r\n\x1a\n'
        assert list(struct.unpack('>II', raw[16:24])) == record['client_size'] == [1280, 720]
    shots[name] = len(found)
    assert all('error' not in a for a in summary['actions'])

before = read(HERE / 'saved-world/projection.json')
after = read(HERE / 'resaved-world/projection.json')
for name, projection in [('saved-world', before), ('resaved-world', after)]:
    assert projection['world_owned_only'] and len(projection['rockets']) == 1
    assert len(projection['players']) == 2 and all(p['root_vehicle'] is None for p in projection['players'])
    for record in projection['files']:
        assert sha(HERE / name / 'nbt' / record['file']) == record['sha256']
assert before['rockets'] == after['rockets']
rocket = before['rockets'][0]
assert rocket['entity'] == '0316d87e-2e28-4dc5-a6f0-a554759b102a'
assert rocket['fuel'] == 618 and rocket['state'] == 'LANDED'
assert len(rocket['blocks']) == 6 and rocket['passengers']['seat_capacity'] == 2
assert len(rocket['passengers']['assignments']) == 2
cargo = [b['block_entity']['data']['Items'] for b in rocket['blocks'] if 'block_entity' in b]
assert cargo == [[dict(Slot=0, id='minecraft:diamond', Count=17)]]
server = (HERE / 'restart-native/server-full.txt').read_text(encoding='utf-8')
assert 'source_count=0 destination_count=1 action=KEEP_DESTINATION status=RECOVERED' in server
assert 'REBUILD_DESTINATION' not in server
assert 'RESTART_OWNER_MOUNTED' in server and 'RESTART_PASSENGER_MOUNTED' in server
assert (HERE / 'process-cleanup.txt').read_text().strip() == 'remaining_owned_native=0'
assert [r['exit_code'] for r in read(HERE / 'git-results.json')] == [0, 0, 1]
total = 0
for line in (HERE.parent / 'checksums.txt').read_text().splitlines():
    expected, relative = line.split('  ', 1)
    path = (HERE.parent / relative).resolve()
    assert path.is_relative_to(HERE.parent) and sha(path) == expected, relative
    total += 1
print(json.dumps(dict(status='PASS_SCOPED_PERSISTENCE_EVIDENCE', source_inputs=742,
    java_tests=397, game_tests=45, screenshots=shots, checksums=total,
    artifact_sha256=sha(jar), legacy_embedded_vehicle_or_release_approval=False)))
