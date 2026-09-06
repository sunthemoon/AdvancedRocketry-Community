"""Audit this slice's retained inputs and results without approving release Gates."""
from pathlib import Path
import hashlib
import json
import re
import struct
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


inventory = json.loads((HERE / 'source-inventory.json').read_text(encoding='utf-8'))
for item in inventory['files']:
    assert sha(ROOT / item['path']) == item['sha256'], item['path']
counts = dict(tests=0, failures=0, errors=0, skipped=0)
results = list((HERE / 'java-results').glob('TEST-*.xml'))
assert len(results) == 73
for path in results:
    suite = ET.parse(path).getroot()
    for key in counts:
        counts[key] += int(suite.attrib.get(key, 0))
assert counts == dict(tests=385, failures=0, errors=0, skipped=0), counts
assert b'All 44 required tests passed' in (HERE / 'gametest-latest.log').read_bytes()
summary = json.loads((HERE / 'packaged/summary.json').read_text(encoding='utf-8'))
assert summary['status'] == 'PASS'
assert len(summary['clients']) == 1 and len(summary['servers']) == 2
for item in summary['clients'] + summary['servers']:
    assert item['exit_code'] == 0
    assert sha(HERE / 'packaged' / item['full_log_file']) == item['full_log_sha256']
    for key in ('error_count', 'fatal_count', 'project_warning_count',
                'project_error_count', 'client_linkage_failure_count'):
        assert item['log_audit'].get(key, 0) == 0
for item in summary['owned_processes']:
    assert item['exit_code'] == 0
    for log in item['native_logs']:
        assert sha(HERE / 'packaged' / log['file']) == log['sha256']
jar = ROOT / 'build/libs/advancedrocketry-community-1.20.1-1.0.0-dev.jar'
assert sha(jar) == summary['artifact']['sha256'] == inventory['artifact_sha256']
gui = json.loads((HERE / 'gui/summary.json').read_text(encoding='utf-8'))
assert gui['status'] == 'COMPLETE_WITH_OBSERVATIONS'
assert gui['artifact_sha256'] == sha(jar)
assert len(gui['processes']) == 2
for process in gui['processes']:
    assert process['exit_code'] == 0
    for log in process['native_logs']:
        assert sha(HERE / 'gui' / log['file']) == log['sha256']
assert len(gui['screenshots']) == 6
for record in gui['screenshots']:
    path = HERE / 'gui' / record['file']
    assert sha(path) == record['sha256']
    raw = path.read_bytes()
    assert len(raw) == record['bytes'] and raw[:8] == b'\x89PNG\r\n\x1a\n'
    assert list(struct.unpack('>II', raw[16:24])) == record['client_size']
assert b'NVIDIA GeForce RTX 3070 Laptop GPU' in (HERE / 'gui/client-full.txt').read_bytes()
assert b'ARCE_FLIGHT_INTENT ' not in (HERE / 'gui/server-full.txt').read_bytes()
for doc in (HERE.parent / 'VERIFICATION.md', ROOT / 'docs/work/v1.0.0-implementation-log.md',
            ROOT / 'docs/work/v1.0.0-stabilization-gap-audit.md'):
    for link in re.findall(r'\]\(([^)]+)\)', doc.read_text(encoding='utf-8')):
        if '://' not in link and not link.startswith('#'):
            assert (doc.parent / link.split('#', 1)[0]).exists(), (doc, link)
checksums = 0
for line in (HERE.parent / 'checksums.txt').read_text(encoding='utf-8').splitlines():
    expected, name = line.split('  ', 1)
    path = (HERE.parent / name).resolve()
    assert path.is_relative_to(HERE.parent) and sha(path) == expected, name
    checksums += 1
print(json.dumps(dict(status='PASS_SCOPED_AUDIT', source_inputs=len(inventory['files']),
                     java_tests=counts['tests'], game_tests=44, native_processes=5,
                     screenshots=6, checksums=checksums, artifact_sha256=sha(jar),
                     full_visual_or_release_approval=False)))
