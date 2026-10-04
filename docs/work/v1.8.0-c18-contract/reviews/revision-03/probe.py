"""Read-only input checks for a decision-only contract diff; writes own evidence only."""
import csv
import difflib
import fnmatch
import hashlib
import json
import platform
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path('D:/GitHub/AdvancedRocketry-Community')
R2 = ROOT / 'docs/work/v1.8.0-c18-contract/proposals/revision-02'
R3 = Path('C:/Users/Administrator/AppData/Local/Temp/arce-v180-c18-contract-r3-db785f109ab74f34a0e1f1564eb38a99')
OUT = Path(__file__).resolve().parent
NAME = 'ADR-066-CLASSIC-LIFE-SUPPORT-EQUIPMENT-RESEARCH-AND-PRESENTATION.md'
OWNER = ROOT / 'docs/work/v1.8.0-c18-contract/OWNER-DECISIONS.md'
PRIOR = ROOT / 'docs/work/v1.8.0-c18-contract/reviews/revision-02/REVIEW-02.raw.txt'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_rows(path):
    with path.open(encoding='utf-8-sig', newline='') as stream:
        return list(csv.DictReader(stream))


def signature(path):
    return {'path': str(path), 'bytes': path.stat().st_size, 'sha256': digest(path)}


def interval(text, start, end):
    begin = text.index(start)
    return text[begin:text.index(end, begin)]


def inventory(directory):
    return {path.name: signature(path) for path in sorted(directory.iterdir()) if path.is_file()}


started = datetime.now(timezone.utc).isoformat()
inputs_before = {'r2': inventory(R2), 'r3': inventory(R3),
                 'owner': signature(OWNER), 'prior_review': signature(PRIOR)}
assert digest(R2 / NAME) == '7c6da4ab6f3974d537813ed3662f1f072f86c3cf13990b1d1afb685045ecb976'
assert digest(R3 / NAME) == 'ae4ac5a20983eac80881eeb63bbde83db60665537be48116fee3dc5aa5ce3034'
assert digest(R3 / 'revision-02-to-03.diff') == '1b0ac53d364c4e4c445a4278852bc8d2e31ed132d3eb70b2aca8f8d052544e86'
assert digest(OWNER) == 'ed4352ee786435afa6573f3aede578528e9ae78914cbae2147fccae98f4d028f'

manifest_checks = {}
for label, directory in [('r2', R2), ('r3', R3)]:
    manifest = json.loads((directory / 'FILE-HASHES.json').read_text(encoding='utf-8-sig'))
    checks = {}
    entries = ({row['path']: row for row in manifest} if isinstance(manifest, list)
               else {name: {'sha256': sha} for name, sha in manifest.items()})
    for name, entry in entries.items():
        candidate = directory / name
        assert candidate.parent == directory and candidate.is_file(), (label, name)
        assert digest(candidate) == entry['sha256'], (label, name, 'hash')
        if 'bytes' in entry:
            assert candidate.stat().st_size == entry['bytes'], (label, name, 'bytes')
        checks[name] = signature(candidate)
    manifest_checks[label] = checks

old = (R2 / NAME).read_text(encoding='utf-8')
new = (R3 / NAME).read_text(encoding='utf-8')
old_lines, new_lines = old.splitlines(keepends=True), new.splitlines(keepends=True)
independent_diff = ''.join(difflib.unified_diff(old_lines, new_lines,
    fromfile='revision-02/' + NAME, tofile='revision-03/' + NAME))
assert independent_diff == (R3 / 'revision-02-to-03.diff').read_text(encoding='utf-8')
(OUT / 'independent.diff').write_bytes(independent_diff.encode('utf-8'))
changes = []
for op, a, b, c, d in difflib.SequenceMatcher(a=old_lines, b=new_lines, autojunk=False).get_opcodes():
    if op != 'equal':
        changes.append({'operation': op, 'r2_lines_inclusive': [a + 1, b],
                        'r3_lines_inclusive': [c + 1, d],
                        'removed': ''.join(old_lines[a:b]), 'added': ''.join(new_lines[c:d])})

# Compare full technical intervals, not just numeric tokens or selected sentences.
regions = [
    ('source_authority_devices_gas_equipment', '## 1. Sources', '## 6. C18c', None),
    ('ground_origin_codec_invariants', 'instance `sourceMission`', 'Required fixtures include', None),
    ('ground_paid_work_analysis_migration', 'On authorized START', 'The unselected D2 alternative',
     'The alternative is a registry-authoritative'),
    ('world_first_schema_writer_election_ack_recovery', '`advancedrocketrycommunity_classic_progression`',
     'unusable until repair.', None),
    ('reachability_assets_verification', '### 6.3 Reachability', '## 9. Decision status', None),
    ('dependencies_shared_ids_protocols', 'C18a needs C15a', 'No commit/tag/push', None),
    ('d4_feasibility', '**Feasibility gate**', '## 5.', None),
]
unchanged_regions = {}
for label, start, end, old_end in regions:
    before, after = interval(old, start, old_end or end), interval(new, start, end)
    assert before == after, label
    unchanged_regions[label] = {'utf8_bytes_normalized_lf': len(after.encode('utf-8')),
                               'sha256_normalized_lf': hashlib.sha256(after.encode('utf-8')).hexdigest()}

same_companions = {}
for name in ['unit-coverage.csv', 'asset-coverage.csv', 'source-files.json', 'coverage-summary.json']:
    assert (R2 / name).read_bytes() == (R3 / name).read_bytes(), name
    same_companions[name] = signature(R3 / name)

units = read_rows(R3 / 'unit-coverage.csv')
ledger = read_rows(ROOT / 'docs/work/v1.8.0-content-ledger.csv')
c18_planned = [row for row in ledger if row['plan'].startswith('C18') and row['disposition'] == 'PLANNED']
assert len(units) == len(c18_planned) == 104
assert len({row['unit_id'] for row in units}) == len(units)
assert all(value for row in units for value in row.values())
assert {(row['unit_id'], row['batch']) for row in units} == {
    (row['unit_id'], row['plan']) for row in c18_planned}

java_manifest = {row['path']: row for row in read_rows(ROOT / 'legacy-manifest/java-files.csv')}
asset_manifest = {row['source_path']: row for row in read_rows(ROOT / 'legacy-manifest/assets.csv')}
source_record = json.loads((R3 / 'source-files.json').read_text(encoding='utf-8'))
commit = source_record['upstream_commit']
assert commit == 'c5cd5af62fc07cd4e0d24f06a16033f181c47c04'
assert (ROOT / 'legacy-manifest/UPSTREAM_COMMIT.txt').read_text().strip() == commit
upstream_checks = []
for source in source_record['source_files']:
    candidate = java_manifest.get(source['path'], asset_manifest.get(source['path']))
    assert candidate is not None, source['path']
    assert candidate['sha256'] == source['sha256'], source['path']
    if source['path'] in asset_manifest:
        assert candidate['source_commit'] == commit, source['path']
    upstream_checks.append({'path': source['path'], 'sha256': source['sha256'], 'manifest_match': True})
assert len(upstream_checks) == 34

rules = sorted(read_rows(ROOT / 'docs/work/v1.8.0-asset-plan.csv'), key=lambda row: int(row['order']))
prefix = 'src/main/resources/assets/advancedrocketry/'
expected_assets = []
for source, row in sorted(asset_manifest.items()):
    assert source.startswith(prefix), source
    tail = source[len(prefix):]
    chosen = next((rule for rule in rules if fnmatch.fnmatchcase(tail, rule['pattern'])), None)
    if chosen is not None and chosen['plan'] == 'C18d':
        expected_assets.append({'source_path': source, 'source_sha256': row['sha256'],
                                'handling': chosen['handling'], 'owning_units': chosen['units'],
                                'rule_order': chosen['order'], 'rule_pattern': chosen['pattern']})
actual_assets = read_rows(R3 / 'asset-coverage.csv')
assert len(actual_assets) == len(expected_assets) == 155
fields = list(expected_assets[0])
assert [{name: row[name] for name in fields} for row in actual_assets] == expected_assets
assert all(row['terminal_evidence'] for row in actual_assets)

authorities = []
for source in source_record['authorities']:
    current = ROOT / source['path']
    authorities.append({'path': source['path'], 'r2_r3_recorded_sha256': source['sha256'],
                        'current_sha256': digest(current), 'current_matches_record': digest(current) == source['sha256']})

owner = OWNER.read_text(encoding='utf-8')
assert '| D2 | Ground observatory devices may run independent ground-owned server survey jobs without requiring a survey satellite. |' in owner
assert '| D3 | Preserve world-first Moon/warp milestones and record winning participants, rather than converting them to per-player firsts. |' in owner
assert 'status: PROPOSED\nrevision: 3\n' in new
assert 'accepted_at: ""' in new and 'acceptance_basis: ""' in new
assert 'owner_confirmed_decisions: [D1, D2, D3, D5]' in new

inputs_after = {'r2': inventory(R2), 'r3': inventory(R3),
                'owner': signature(OWNER), 'prior_review': signature(PRIOR)}
assert inputs_before == inputs_after, 'review inputs changed during independent probe'
evidence = {
    'kind': 'independent_decision_only_static_checks_not_runtime_or_acceptance',
    'started_utc': started, 'finished_utc': datetime.now(timezone.utc).isoformat(),
    'python': platform.python_version(), 'inputs': inputs_before,
    'input_bytes_unchanged_at_end': True, 'verified_manifest_entries': manifest_checks,
    'supplied_diff_matches_actual_normalized_lf_diff': True,
    'diff_hunks': independent_diff.count('\n@@ '), 'changed_spans': changes,
    'byte_identical_companions': same_companions, 'unchanged_technical_regions': unchanged_regions,
    'planned_units': len(units), 'batch_counts': dict(Counter(row['batch'] for row in units)),
    'c18d_assets': len(actual_assets), 'asset_handling': dict(Counter(row['handling'] for row in actual_assets)),
    'upstream_commit': commit, 'upstream_manifest_matches': upstream_checks,
    'authority_snapshot_current_observation': authorities,
    'root_writes': 0, 'author_handoff_writes': 0, 'gradle_native_runtime_commands': 0,
    'gate_pass_or_root_acceptance': False,
}
(OUT / 'static-evidence.json').write_text(json.dumps(evidence, indent=2) + '\n', encoding='utf-8')
print(json.dumps({key: value for key, value in evidence.items()
                  if key not in ['inputs', 'verified_manifest_entries', 'changed_spans', 'upstream_manifest_matches']}, indent=2))
