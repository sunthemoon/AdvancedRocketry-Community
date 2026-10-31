import hashlib
import json
import re
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(__file__).resolve().parents[2]
evidence = Path(__file__).resolve().parent
source = 'ba789707fd538b6ffeafcb5f2dfce6fcc1654d34'
baseline = json.loads((evidence / 'baseline/result.json').read_text(encoding='utf-8-sig'))
runs = []
cases = [('baseline', 42, 1), ('green-1', 47, 0), ('green-2', 42, 0), ('green-3', 42, 0), ('green-4', 42, 0),
         ('alone', 13, 1), ('beacon-diagnostic', 1, 1), ('alone-final', 13, 1),
         ('alone-complete', 13, 0), ('green-8', 42, 0), ('green-9', 42, 0), ('green-10', 42, 0), ('full-final', 1557, 0)]
for name, count, failures in cases:
    folder = evidence / name
    raw = (folder / 'logs/latest.log').read_text(encoding='utf-8-sig')
    native = int((folder / 'native-exit.txt').read_text(encoding='utf-8-sig'))
    checked = int((folder / 'checker-exit.txt').read_text(encoding='utf-8-sig'))
    commit = (folder / 'source-sha.txt').read_text(encoding='utf-8-sig').strip()
    state = (folder / 'source-status.txt').read_text(encoding='utf-8-sig').strip()
    assert not state, (name, state)
    if failures:
        assert native == 1 and checked == 1, (name, native, checked)
        assert f'{failures} required tests failed' in raw
        if name == 'baseline':
            assert 'Another ticket keeps the margin entity-ticking' in raw
        elif name == 'alone-final':
            assert 'Timed out waiting for the isolated chunk to unload' in raw
        else:
            assert 'Unloading changed or rewrote the active index record' in raw
        if name == 'beacon-diagnostic':
            assert 'removed=false, dirty=false' in raw
    else:
        assert native == 0 and checked == 0, (name, native, checked)
        assert f'All {count} required tests passed' in raw, name
        if name in {'alone-complete', 'green-8', 'green-9', 'green-10', 'full-final'}:
            assert commit == source, (name, commit)
    if name != 'full-final':
        result = json.loads((folder / 'result.json').read_text(encoding='utf-8-sig'))
        assert baseline['inventory'] == result['inventory'], name
        assert result['complete'] and len(result['failed']) == failures and len(result['executed']) == count
        order = re.findall(r"Running test batch '([^']+):\d+'", raw)
        if name not in {'alone', 'alone-final', 'alone-complete', 'beacon-diagnostic'}:
            assert order.index('endgame_destruction_blast') + 1 == order.index('w164_adapter_fault')
            assert order.index('propulsion_nuclear') + 1 == order.index('w05_planks')
            assert order.index('w05_planks') + 1 == order.index('machine_menu_channel')
    runs.append(dict(name=name, source=commit, total=count, native=native, checker=checked,
                     log_sha256=hashlib.sha256((folder / 'logs/latest.log').read_bytes()).hexdigest(),
                     complete_summary=re.findall(r'All \d+ required tests passed|\d+ required tests failed', raw),
                     unexpected_fixture_leftovers=bool(re.search(r'ARCE_W95_AFTER_BATCH|Duplicate inventory drops|Fixture chunk is not ticker-ready|Fill 0 was not admitted', raw))))
tests = [ET.parse(p).getroot() for p in (root / 'build/test-results/test').glob('TEST-*.xml')]
unit = {key: sum(int(t.get(key, 0)) for t in tests) for key in ('tests', 'failures', 'errors', 'skipped')}
assert unit['tests'] == 5879 and all(unit[k] == 0 for k in ('failures', 'errors', 'skipped')), unit
report = dict(source=source, runs=runs, unit=unit, unit_classes=len(tests), registered_test_count=len(baseline['inventory']))
(evidence / 'verification-summary.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
print(json.dumps(report, indent=2))
