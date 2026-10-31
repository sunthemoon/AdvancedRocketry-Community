import json
from pathlib import Path
import sys

ROOT = Path('D:/GitHub/arce-v180-w225')
sys.path.insert(0, str(ROOT))
from scripts import check_gametest_log as check
from scripts import ci_gametest_shards as shards

folder = Path(sys.argv[1])
order = sys.argv[2].split(',')
raw = (folder / 'logs/latest.log').read_text(encoding='utf-8')
actual = []
for line in raw.splitlines():
    header = check.HEADER.match(line)
    if header and (start := check.BATCH_START.fullmatch(header['message'])):
        actual.append(start['name'])
assert actual == order, (actual, order)
manifest = shards.slice_manifest(shards.read_json(ROOT / 'scripts/gametest_expected_log.json'), set(order))
shards.write_json(folder / 'expected-log.json', manifest)
assert not any('arce_ci_gametest' in entry.message for entry in check.parse_log(raw).entries), 'Observer logged WARN/ERROR'
sys.exit(check.main([str(folder / 'logs/latest.log'), '--manifest', str(folder / 'expected-log.json')]))
