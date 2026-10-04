import csv
import difflib
import hashlib
import json
from collections import Counter
from pathlib import Path

ROOT = Path('D:/GitHub/AdvancedRocketry-Community')
OUT = Path(__file__).parent
OLD = Path('C:/Users/Administrator/AppData/Local/Temp/arce-v180-c18-contract-8de9f06d9ef04b2b95996d5a254cea92')
NAME = 'ADR-066-CLASSIC-LIFE-SUPPORT-EQUIPMENT-RESEARCH-AND-PRESENTATION.md'

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def rows(path):
    with path.open(encoding='utf-8-sig', newline='') as stream:
        return list(csv.DictReader(stream))

units = rows(OUT / 'unit-coverage.csv')
ledger = [row for row in rows(ROOT / 'docs/work/v1.8.0-content-ledger.csv')
          if row['plan'].startswith('C18') and row['disposition'] == 'PLANNED']
assert len(units) == len(ledger) == 104
assert len({row['unit_id'] for row in units}) == 104
assert {(row['unit_id'], row['batch']) for row in units} == {
    (row['unit_id'], row['plan']) for row in ledger}
assert all(all(row[key] for key in ('proposed_target', 'contract_sections',
                                  'migration', 'verification', 'provenance',
                                  'player_limitations')) for row in units)
assets = rows(OUT / 'asset-coverage.csv')
assert len(assets) == len({row['source_path'] for row in assets}) == 155
assert Counter(row['handling'] for row in assets) == {
    'REVIEW': 77, 'IMPORT': 77, 'REGENERATE': 1}
sources = json.loads((OUT / 'source-files.json').read_text(encoding='utf-8'))
assert len(sources['source_files']) == 34
assert all(row['manifest_match'] for row in sources['source_files'])
assert digest(OLD / NAME) == '6f8c13b0aedf440420575cef377bc90ca68751e6c923fecf7b10608b026f7c86'
draft = (OUT / NAME).read_text(encoding='utf-8')
assert 'status: PROPOSED\nrevision: 2\n' in draft
assert 'owner_confirmed_decisions: [D1, D5]' in draft
assert 'instance `sourceMission`' in draft
assert 'origin_kind' in draft and 'source acknowledgment' in draft
assert 'NON_BREATHABLE_ATMOSPHERE' in draft and 'arce_unlit_torch' in draft
assert 'C17\'s stable `beacon_finder`' in draft
assert 'channel 8 -> 9' in draft and '`celestial_snapshot` channel protocol 3 -> 4' in draft
old_lines = (OLD / NAME).read_text(encoding='utf-8').splitlines(keepends=True)
(OUT / 'revision-01-to-02.diff').write_text(''.join(difflib.unified_diff(
    old_lines, draft.splitlines(keepends=True), fromfile='revision-01/' + NAME,
    tofile='revision-02/' + NAME)), encoding='utf-8')
checks = {
    'kind': 'author_static_handoff_check_not_independent_review_or_runtime',
    'draft_sha256': digest(OUT / NAME),
    'revision_1_unchanged': True,
    'exact_current_planned_unit_ids': 104,
    'batches': dict(Counter(row['batch'] for row in units)),
    'asset_rows': 155,
    'manifest_matched_upstream_files': 34,
    'owner_confirmed_decisions': ['D1', 'D5'],
    'pending_owner_decisions': ['D2', 'D3'],
    'pending_feasibility_gate': 'D4',
    'root_writes_by_this_worker': 0,
    'runtime_native_gradle_tests_run': 0,
}
(OUT / 'static-checks.json').write_text(json.dumps(checks, indent=2) + '\n', encoding='utf-8')
hashes = [{'path': file.name, 'bytes': file.stat().st_size, 'sha256': digest(file)}
          for file in sorted(OUT.iterdir()) if file.is_file() and file.name != 'FILE-HASHES.json']
(OUT / 'FILE-HASHES.json').write_text(json.dumps(hashes, indent=2) + '\n', encoding='utf-8')
print(json.dumps(checks))
