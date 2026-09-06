"""Collect bounded build evidence; preserve all earlier failed attempts."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[4]
OUT = Path(__file__).resolve().parent

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

previous = json.loads((ROOT / 'docs/work/v1.0.0-passenger-persistence/verification/source-inventory.json').read_text())
paths = {row['path'] for row in previous['files']}
paths.update([
    'src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/flight/RocketTransferAuthorityOrder.java',
    'src/test/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/flight/RocketTransferAuthorityOrderTest.java',
])
artifact = ROOT / 'build/libs/advancedrocketry-community-1.20.1-1.0.0-dev.jar'
inventory = dict(source_state='development_worktree', artifact_sha256=digest(artifact),
                 artifact_bytes=artifact.stat().st_size,
                 files=[dict(path=p, sha256=digest(ROOT / p), size=(ROOT / p).stat().st_size) for p in sorted(paths)])
(OUT / 'source-inventory.json').write_text(json.dumps(inventory, indent=2) + '\n', encoding='utf-8')
results = OUT / 'java-results'
results.mkdir(exist_ok=False)
counts = dict(tests=0, failures=0, errors=0, skipped=0, suites=0)
for path in sorted((ROOT / 'build/test-results/test').glob('TEST-*.xml')):
    shutil.copy2(path, results / path.name)
    root = ET.parse(path).getroot()
    counts['suites'] += 1
    for key in ('tests', 'failures', 'errors', 'skipped'):
        counts[key] += int(root.attrib[key])
shutil.copy2(ROOT / 'build/gametest/logs/latest.log', OUT / 'after-gametest-native.log')
commands = []
for name, args in [('diff-check', ['git', 'diff', '--check']),
                   ('generated-diff', ['git', 'diff', '--exit-code', '--', 'src/generated']),
                   ('worktree-diff', ['git', 'diff', '--exit-code'])]:
    result = subprocess.run(args, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    (OUT / (name + '.txt')).write_bytes(result.stdout)
    commands.append(dict(command=args, exit_code=result.returncode))
result = dict(java=counts, git=commands, artifact_sha256=inventory['artifact_sha256'],
              artifact_bytes=inventory['artifact_bytes'], source_files=len(paths))
(OUT / 'build-results.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
print(json.dumps(result))
