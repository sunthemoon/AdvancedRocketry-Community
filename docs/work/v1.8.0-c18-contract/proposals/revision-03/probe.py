"""Author decision-only static scope check; not independent review or runtime proof."""
import csv
import difflib
import hashlib
import json
from collections import Counter
from pathlib import Path

ROOT = Path('D:/GitHub/AdvancedRocketry-Community')
OLD = Path('C:/Users/Administrator/AppData/Local/Temp/arce-v180-c18-contract-r2-72caa8107091470eb65132167af7121c')
OUT = Path(__file__).parent
NAME = 'ADR-066-CLASSIC-LIFE-SUPPORT-EQUIPMENT-RESEARCH-AND-PRESENTATION.md'

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def rows(path):
    with path.open(encoding='utf-8-sig', newline='') as stream:
        return list(csv.DictReader(stream))

def region(text, start, end):
    begin = text.index(start)
    return text[begin:text.index(end, begin)]

assert sha(OLD / NAME) == '7c6da4ab6f3974d537813ed3662f1f072f86c3cf13990b1d1afb685045ecb976'
assert sha(OLD / 'REPORT.md') == 'fd6372a8ff7295df5a2182a53d397656446c94079dab0f00239f9dbaf1a612f0'
assert sha(OLD / 'FILE-HASHES.json') == 'de757e62443c6e12878717074acb64e1ecdd56762f189012b47d3b53c94181c0'
old = (OLD / NAME).read_text(encoding='utf-8')
new = (OUT / NAME).read_text(encoding='utf-8')
assert 'status: PROPOSED\nrevision: 3\n' in new
assert 'accepted_at: ""' in new and 'acceptance_basis: ""' in new
assert 'owner_confirmed_decisions: [D1, D2, D3, D5]' in new
assert '**D2 OWNER-CONFIRMED**' in new and '**D3 OWNER-CONFIRMED**' in new
assert 'D3 remains pending' not in new and 'D2 still requires owner' not in new
assert 'D4 remains an unproven' in new
assert 'before implementation' in region(new, '**Feasibility gate**', '## 5.')

unchanged = {}
for label, start, end in (
    ('sources_shared_devices_gas_equipment_all_numeric', '## 1. Sources', '## 6. C18c'),
    ('ground_origin_codec_invariants', 'instance `sourceMission`', 'Required fixtures include'),
    ('ground_work_and_processor', 'On authorized START', 'The unselected D2 alternative'),
    ('world_first_native_protocol', '`advancedrocketrycommunity_classic_progression`', 'unusable until repair.'),
    ('reachability_assets_verification', '### 6.3 Reachability', '## 9. Decision status'),
    ('shared_dependency_wire_ids', 'C18a needs C15a', 'No commit/tag/push'),
):
    old_end = 'The alternative is a registry-authoritative' if label == 'ground_work_and_processor' else end
    before, after = region(old, start, old_end), region(new, start, end)
    assert before == after, label
    unchanged[label] = hashlib.sha256(after.encode()).hexdigest()

for name in ('unit-coverage.csv', 'asset-coverage.csv', 'source-files.json', 'coverage-summary.json'):
    assert sha(OUT / name) == sha(OLD / name), name
unit_rows = rows(OUT / 'unit-coverage.csv')
expected = [row for row in rows(ROOT / 'docs/work/v1.8.0-content-ledger.csv')
            if row['plan'].startswith('C18') and row['disposition'] == 'PLANNED']
assert len(unit_rows) == len(expected) == 104
assert {(row['unit_id'], row['batch']) for row in unit_rows} == {
    (row['unit_id'], row['plan']) for row in expected}
receipt_path = ROOT / 'docs/work/v1.8.0-c18-contract/OWNER-DECISIONS.md'
assert sha(receipt_path) == 'ed4352ee786435afa6573f3aede578528e9ae78914cbae2147fccae98f4d028f'
receipt = receipt_path.read_text(encoding='utf-8')
assert '| D2 | Ground observatory devices may run independent ground-owned server survey jobs without requiring a survey satellite. |' in receipt
assert '| D3 | Preserve world-first Moon/warp milestones and record winning participants, rather than converting them to per-player firsts. |' in receipt
diff = ''.join(difflib.unified_diff(old.splitlines(True), new.splitlines(True),
    fromfile='revision-02/' + NAME, tofile='revision-03/' + NAME))
(OUT / 'revision-02-to-03.diff').write_text(diff, encoding='utf-8')
result = {
    'kind': 'author_static_decision_only_scope_check_not_acceptance_or_independent_verdict',
    'proposal_sha256': sha(OUT / NAME), 'base_revision2_unchanged': True,
    'revision2_evidence_hashes_unchanged': True,
    'root_owner_receipt_path': str(receipt_path), 'root_owner_receipt_sha256': sha(receipt_path),
    'owner_confirmed': ['D1', 'D2', 'D3', 'D5'],
    'selected_branches': {'D2': 'independent_ground_jobs_no_survey_satellite',
                         'D3': 'world_first_recorded_winning_participants'},
    'D4_durability_feasibility_proven': False,
    'technical_numeric_regions_byte_identical': unchanged,
    'technical_behavior_added': False, 'companions_byte_identical_to_r2': True,
    'current_c18_planned_ids_unchanged': 104,
    'batch_counts_not_acceptance': dict(Counter(row['batch'] for row in unit_rows)),
    'proposal_status': 'PROPOSED', 'accepted_at': '',
    'root_current_version_sha256': sha(ROOT / 'docs/status/CURRENT_VERSION.md'),
    'root_implementation_log_sha256': sha(ROOT / 'docs/work/v1.8.0-implementation-log.md'),
    'root_writes_by_author': 0, 'runtime_native_gradle_jobs_run': 0,
    'command': 'python -B <own fresh revision3 Temp>/probe.py', 'exit_code': 0,
}
(OUT / 'static-checks.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
(OUT / 'FILE-HASHES.json').write_text(json.dumps({path.name: sha(path)
    for path in sorted(OUT.iterdir()) if path.is_file() and path.name != 'FILE-HASHES.json'},
    indent=2) + '\n', encoding='utf-8')
print(json.dumps({key: value for key, value in result.items()
    if key != 'technical_numeric_regions_byte_identical'}, indent=2))
