"""Verify this development slice's inputs and raw receipts; no release approval."""
from pathlib import Path
import hashlib
import json
import re
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


inventory = json.loads((HERE / 'source-inventory.json').read_text(encoding='utf-8'))
for row in inventory['files']:
    assert sha(ROOT / row['path']) == row['sha256'], row['path']
summary = json.loads((HERE / 'packaged/summary.json').read_text(encoding='utf-8'))
assert summary['status'] == 'PASS'
assert len(summary['clients']) == 1 and len(summary['servers']) == 2
processes = summary['clients'] + summary['servers']
for row in processes:
    assert row['exit_code'] == 0
    assert sha(HERE / 'packaged' / row['full_log_file']) == row['full_log_sha256']
    for key in ('error_count', 'fatal_count', 'project_warning_count',
                'project_error_count', 'client_linkage_failure_count'):
        assert row['log_audit'].get(key, 0) == 0, (row.get('cycle', row.get('cell')), key)
for row in summary['owned_processes']:
    assert row['exit_code'] == 0
    for log in row['native_logs']:
        assert sha(HERE / 'packaged' / log['file']) == log['sha256']
jar = ROOT / 'build/libs/advancedrocketry-community-1.20.1-1.0.0-dev.jar'
assert sha(jar) == summary['artifact']['sha256']
counts = {key: 0 for key in ('tests', 'failures', 'errors', 'skipped')}
java_results = list((HERE / 'java-results').rglob('TEST-*.xml'))
assert len(java_results) == 72
for path in java_results:
    suite = ET.parse(path).getroot()
    for key in counts:
        counts[key] += int(suite.attrib.get(key, 0))
assert counts == dict(tests=376, failures=0, errors=0, skipped=0), counts
# The native GameTest log uses the JVM's Windows locale encoding. Match the
# ASCII completion receipt without decoding, replacing or rewriting raw bytes.
assert b'All 44 required tests passed' in (HERE / 'gametest-latest.log').read_bytes()
for doc in (HERE.parent / 'VERIFICATION.md', ROOT / 'docs/work/v1.0.0-implementation-log.md',
            ROOT / 'docs/work/v1.0.0-stabilization-gap-audit.md'):
    for target in re.findall(r'\]\(([^)]+)\)', doc.read_text(encoding='utf-8')):
        if '://' in target or target.startswith('#'):
            continue
        assert (doc.parent / target.split('#', 1)[0]).exists(), (str(doc), target)
result = dict(status='PASS', source_inputs_unchanged=len(inventory['files']),
              packaged_processes=len(processes), artifact_sha256=sha(jar),
              java_tests=counts['tests'], game_tests=44, rendered_gui_verified=False)
(HERE / 'final-audit.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
for directory in (HERE, HERE.parent):
    checksum = directory / 'SHA256SUMS.txt'
    files = sorted((path for path in directory.rglob('*') if path.is_file() and path != checksum),
                   key=lambda path: path.relative_to(directory).as_posix())
    checksum.write_text(''.join(sha(path) + '  ' + path.relative_to(directory).as_posix() + '\n'
                                for path in files), encoding='utf-8')
    for line in checksum.read_text(encoding='utf-8').splitlines():
        expected, relative = line.split('  ', 1)
        assert sha(directory / relative) == expected
    print(directory.relative_to(ROOT), len(files), 'checksums verified')
print(json.dumps(result, indent=2))
