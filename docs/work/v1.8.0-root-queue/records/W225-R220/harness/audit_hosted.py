"""Read-only console-log audit; console names are abbreviated, so match class suffixes."""
import json
import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path('D:/GitHub/arce-v180-w225')
sys.path.insert(0, str(ROOT))
from scripts import check_gametest_log as check

manifest = check.load_manifest(ROOT / 'scripts/gametest_expected_log.json')
base = Path('D:/GitHub/ARCE-Task-Evidence/root-queue')
paths = [base / 'tools' / f'job-{job}.log' for job in ('124511315921', '124511316018', '124511315928')]
paths += sorted((base / 'W225-hosted').glob('job-124497*.log'))
reports = []
for path in paths:
    batch = '<startup>'
    active = False
    pending = None
    continuation = []
    entries = []
    batches = set()
    counts = Counter()
    checker = []
    def close():
        if pending is None:
            return
        number, level, logger, message, owning_batch = pending
        matches = []
        for exp in manifest:
            if exp.logger.rstrip('/').split('/')[-1].split('.')[-1] != logger.rstrip('/').split('/')[-1].split('.')[-1]:
                continue
            entry = check.Entry(number, level, exp.logger, message, owning_batch, tuple(continuation))
            if exp.describes(entry):
                matches.append(exp)
        allowed = [exp for exp in matches if exp.allows_batch(owning_batch)]
        if allowed:
            matches = allowed
        expected = matches[0] if len(matches) == 1 else None
        if expected:
            counts[expected.id, owning_batch] += 1
        entries.append(dict(line=number, level=level, batch=owning_batch, logger=logger, message=message,
                            expectation=expected.id if expected else None,
                            in_batch=bool(expected and expected.allows_batch(owning_batch))))
    for number, raw in enumerate(path.read_text(encoding='utf-8-sig').splitlines(), 1):
        line = re.sub(r'^\d{4}-\d\d-\d\dT\S+ ', '', raw)
        if line.startswith('[FAIL]'):
            checker.append(line)
        header = check.HEADER.match(line)
        if header:
            if 'Launcher/MODLAUNCHER' in header['logger'] and '--launchTarget, forgegametestserveruserdev' in header['message']:
                active = True
            if not active:
                continue
            close()
            pending, continuation = None, []
            if start := check.BATCH_START.match(header['message']):
                batch = start['name']
                batches.add(batch)
            if header['message'] == 'Game test server shutting down':
                active = False
            if header['level'] in ('WARN', 'ERROR', 'FATAL'):
                pending = number, header['level'], header['logger'], header['message'], batch
        elif active:
            continuation.append(line)
    close()
    mismatches = [dict(id=exp.id, batch=b, expected=n, actual=counts[exp.id, b])
                  for exp in manifest for b, n in (exp.counts or {}).items()
                  if b in batches and counts[exp.id, b] != n]
    unexpected = [e for e in entries if not e['in_batch']]
    report = dict(file=str(path), sha256=__import__('hashlib').sha256(path.read_bytes()).hexdigest(),
                  levels=dict(Counter(e['level'] for e in entries)),
                  error_expectations=dict(Counter(e['expectation'] for e in entries if e['level'] == 'ERROR' and e['in_batch'])),
                  unexpected=unexpected, count_mismatches=mismatches, checker=checker,
                  errors=[e for e in entries if e['level'] in ('ERROR', 'FATAL')])
    reports.append(report)
    print(path.name, report['levels'], 'unexpected', len(unexpected), 'mismatches', len(mismatches))
    for entry in unexpected:
        print(' ', entry)
    for entry in mismatches:
        print(' ', entry)
Path(__file__).with_name('hosted-audit.json').write_text(json.dumps(reports, indent=2) + '\n', encoding='utf-8')
