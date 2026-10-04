import csv
import fnmatch
import hashlib
import json
from collections import Counter
from pathlib import Path

ROOT = Path('D:/GitHub/AdvancedRocketry-Community')
PROPOSAL = Path('C:/Users/Administrator/AppData/Local/Temp/arce-v180-c18-contract-r2-72caa8107091470eb65132167af7121c')
UPSTREAM = Path('C:/Users/Administrator/AppData/Local/Temp/arce-v180-c14-189c61a748584cab97c5881f8bdc1251/upstream/ar')
OUT = Path(__file__).parent


def rows(path):
    with path.open(encoding='utf-8-sig', newline='') as stream:
        return list(csv.DictReader(stream))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


draft_path = PROPOSAL / 'ADR-066-CLASSIC-LIFE-SUPPORT-EQUIPMENT-RESEARCH-AND-PRESENTATION.md'
expected_hash = '7c6da4ab6f3974d537813ed3662f1f072f86c3cf13990b1d1afb685045ecb976'
assert sha(draft_path) == expected_hash
coverage = rows(PROPOSAL / 'unit-coverage.csv')
ledger = [row for row in rows(ROOT / 'docs/work/v1.8.0-content-ledger.csv')
          if row['plan'] in ('C18a', 'C18b', 'C18c', 'C18d')]
assert len(coverage) == len(ledger) == len({row['unit_id'] for row in coverage}) == 104
assert {row['unit_id']: row['batch'] for row in coverage} == {row['unit_id']: row['plan'] for row in ledger}
assert all(row['disposition'] == 'PLANNED' for row in ledger)
assert all(all(value.strip() for value in row.values()) for row in coverage)
assert Counter(row['batch'] for row in coverage) == {'C18a': 28, 'C18b': 23, 'C18c': 19, 'C18d': 34}

java_manifest = {row['path']: row['sha256'] for row in rows(ROOT / 'legacy-manifest/java-files.csv')}
asset_manifest_rows = rows(ROOT / 'legacy-manifest/assets.csv')
asset_manifest = {row['source_path']: row['sha256'] for row in asset_manifest_rows}
source_identity = json.loads((PROPOSAL / 'source-files.json').read_text(encoding='utf-8'))
assert source_identity['upstream_commit'] == 'c5cd5af62fc07cd4e0d24f06a16033f181c47c04'
assert len(source_identity['source_files']) == 34
checked_sources = []
for source in source_identity['source_files']:
    actual = sha(UPSTREAM / source['path'])
    manifest = java_manifest if source['path'].endswith('.java') else asset_manifest
    assert actual == source['sha256'] == manifest[source['path']]
    checked_sources.append({'path': source['path'], 'sha256': actual})

rules = rows(ROOT / 'docs/work/v1.8.0-asset-plan.csv')
expected_assets = []
for asset in asset_manifest_rows:
    relative = asset['source_path'].removeprefix('src/main/resources/assets/advancedrocketry/')
    rule = next(rule for rule in rules if fnmatch.fnmatchcase(relative, rule['pattern']))
    if rule['plan'] == 'C18d':
        expected_assets.append({
            'source_path': asset['source_path'], 'source_sha256': asset['sha256'],
            'handling': rule['handling'], 'owning_units': rule['units'],
            'rule_order': rule['order'], 'rule_pattern': rule['pattern'],
        })
asset_coverage = rows(PROPOSAL / 'asset-coverage.csv')
assert [{key: value for key, value in row.items() if key != 'terminal_evidence'}
        for row in asset_coverage] == expected_assets
assert len(expected_assets) == 155
assert Counter(row['handling'] for row in expected_assets) == {'REVIEW': 77, 'IMPORT': 77, 'REGENERATE': 1}

draft = draft_path.read_text(encoding='utf-8')
assert 'status: PROPOSED' in draft and 'revision: 2' in draft
assert 'owner_confirmed_decisions: [D1, D5]' in draft
assert 'accepted_at: ""' in draft and 'acceptance_basis: ""' in draft
assert sha(draft_path) == expected_hash  # Stable reviewed identity, not a concurrent author edit.

result = {
    'kind': 'independent_c18_revision_2_static_review_evidence',
    'draft_sha256': expected_hash,
    'unit_coverage_sha256': sha(PROPOSAL / 'unit-coverage.csv'),
    'asset_coverage_sha256': sha(PROPOSAL / 'asset-coverage.csv'),
    'author_dispositions_sha256': sha(PROPOSAL / 'review-01-dispositions.md'),
    'exact_current_planned_units': 104, 'unique_unit_ids': 104,
    'batch_counts': dict(Counter(row['batch'] for row in coverage)),
    'ordered_first_match_assets': 155,
    'asset_handling_counts': dict(Counter(row['handling'] for row in expected_assets)),
    'independently_rehashed_approved_sources': 34, 'source_mismatches': [],
    'source_files': checked_sources,
    'runtime_tests_run': False, 'repository_writes': False,
    'command': 'python -B review-02/probe.py', 'exit_code': 0,
}
(OUT / 'static-evidence.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
print(json.dumps({key: result[key] for key in ('draft_sha256', 'exact_current_planned_units',
      'batch_counts', 'ordered_first_match_assets', 'asset_handling_counts',
      'independently_rehashed_approved_sources', 'source_mismatches', 'runtime_tests_run',
      'repository_writes', 'exit_code')}, indent=2))
