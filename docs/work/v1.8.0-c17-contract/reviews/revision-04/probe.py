"""Independent read-only identity, actual-diff and contract-preservation checks."""
import csv
import difflib
import hashlib
import json
import platform
import re
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path('D:/GitHub/AdvancedRocketry-Community')
R3 = ROOT / 'docs/work/v1.8.0-c17-contract/proposals/revision-03'
R4 = Path('C:/Users/Administrator/AppData/Local/Temp/arce-v180-c17-contract-r4-b2e378bc196444a98b0822546749ba1a')
PRIOR = ROOT / 'docs/work/v1.8.0-c17-contract/reviews/revision-03'
OWNER = ROOT / 'docs/work/v1.8.0-c17-contract/OWNER-DECISIONS.md'
C18_OWNER = ROOT / 'docs/work/v1.8.0-c18-contract/OWNER-DECISIONS.md'
C18_ADR = ROOT / 'docs/decisions/ADR-066-CLASSIC-LIFE-SUPPORT-EQUIPMENT-RESEARCH-AND-PRESENTATION.md'
C18_ACCEPT = ROOT / 'docs/work/v1.8.0-c18-contract/ACCEPTANCE.md'
OUT = Path(__file__).resolve().parent
NAME = 'ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def signature(path):
    return {'path': str(path), 'bytes': path.stat().st_size, 'sha256': digest(path)}


def inventory(directory):
    return {path.name: signature(path) for path in sorted(directory.iterdir()) if path.is_file()}


def rows(path):
    with path.open(encoding='utf-8-sig', newline='') as stream:
        return list(csv.DictReader(stream))


def interval(text, start, end):
    begin = text.index(start)
    return text[begin:text.index(end, begin)]


def snapshot():
    return {'r3': inventory(R3), 'r4': inventory(R4), 'prior_review': inventory(PRIOR),
            'owner': signature(OWNER), 'c18_owner': signature(C18_OWNER),
            'c18_canonical': signature(C18_ADR), 'c18_acceptance': signature(C18_ACCEPT)}


started = datetime.now(timezone.utc).isoformat()
before = snapshot()
assert digest(R3 / NAME) == '92e706ae84e6565f605227f7794fb83850a23c4f2ca1915686d28c61fa72c321'
assert digest(R4 / NAME) == 'c50de31ea70a56749c55202bd75de258ff3d89a0ae4a9123ff8ee59e9d3545bb'
assert digest(R4 / 'revision-03-to-04.diff') == '9df8d940384580bf869a32f8515e268e61c4be131dad35ccceeea2107ec8e039'
assert digest(OWNER) == '279d7cb58682b0c0e4bc58abf93dce3a32effca4a7a94b12f5d4d246fe39c00c'
assert digest(C18_OWNER) == 'ed4352ee786435afa6573f3aede578528e9ae78914cbae2147fccae98f4d028f'
assert digest(PRIOR / 'REVIEW-03.raw.txt') == 'f22b9736e99b554d8d45fbdc3ec1b37e38ebd1f1f53b2af2ee87f7a343c00821'

manifest_entries = {}
for label, directory, name in [('r3', R3, 'artifact-hashes.json'), ('r4', R4, 'FILE-HASHES.json')]:
    entries = json.loads((directory / name).read_text(encoding='utf-8-sig'))
    checked = {}
    for filename, expected in entries.items():
        candidate = directory / filename
        assert candidate.parent == directory and candidate.is_file(), filename
        assert digest(candidate) == expected, (label, filename)
        checked[filename] = signature(candidate)
    manifest_entries[label] = checked

old_bytes, new_bytes = (R3 / NAME).read_bytes(), (R4 / NAME).read_bytes()
assert b'\r' not in old_bytes and b'\r' not in new_bytes
old, new = old_bytes.decode('utf-8'), new_bytes.decode('utf-8')
old_lines, new_lines = old.splitlines(keepends=True), new.splitlines(keepends=True)
actual_diff = ''.join(difflib.unified_diff(old_lines, new_lines,
    fromfile='revision-03/' + NAME, tofile='revision-04/' + NAME)).encode('utf-8')
assert actual_diff == (R4 / 'revision-03-to-04.diff').read_bytes()
(OUT / 'independent.diff').write_bytes(actual_diff)
changes = []
for operation, a, b, c, d in difflib.SequenceMatcher(a=old_lines, b=new_lines, autojunk=False).get_opcodes():
    if operation != 'equal':
        changes.append({'operation': operation, 'r3_lines_inclusive': [a + 1, b],
                        'r4_lines_inclusive': [c + 1, d],
                        'removed': ''.join(old_lines[a:b]), 'added': ''.join(new_lines[c:d])})

# Compare complete intervals containing the concrete technical contract, not
# the author's enumerated replacement assertions or only selected numeric words.
regions = [
    ('sources_stable_id_limits', '## 1. Scope', '## 3. D1:', None),
    ('numeric_propulsion_baseline', '| Type | Mass |', 'The owner-selected D1-A compatibility',
     'The D1-A compatibility recommendation'),
    ('external_compatibility_table_and_precedence', '| Components after',
     'propulsion is a visible player/compatibility limit', None),
    ('fluid_catalogs_consumption_abstract_hand_refill', 'Host nuclear cores',
     '**Unselected alternative D1-B:**', '**Alternative D1-B:**'),
    ('complete_disposal_vector_restoration_consent', 'to the complete propulsion vector.',
     '**Unselected alternative D1-disassembly-B:**', '**Alternative D1-disassembly-B:**'),
    ('resource_authority_snapshot_admission_loaders', '## 4. Mutable rocket resources',
     'These transfers span a BE chunk', None),
    ('monitor_checked_station_controls', '### 4.3 Monitor', '### 5.2 D2:', None),
    ('d2_geometry_controls_elevator_wire', '### 5.2 D2:', '### 5.2.1 Restart-safe', None),
    ('same_root_clock_writer_unknown_save_migration', '### 5.2.1 Restart-safe',
     'refuse migration/start intact.', None),
    ('clock_phase_saturation_client_and_native_fixtures', 'For phase, let', '### 5.3 Localization', None),
    ('localization_pad_protection_finder_bay_durable_receipts', '### 5.3 Localization',
     'recovery-contract gate.', None),
    ('solar', '## 9. Solar', '## 10. D3:', None),
    ('d3_exact_pending_coordinator_writer_admission', 'coordinator for BE <-> moving-rocket cargo',
     '**Unselected alternative D3-B:**', '**Alternative D3-B:**'),
    ('network_provenance_native_verification_exact_coverage', '## 11. Network', '## 13. Approval', None),
    ('rollback_and_source_appendix', 'Use per-profile disable switches', 'Governance:', None),
]
unchanged = {}
for label, start, end, old_end in regions:
    a, b = interval(old, start, old_end or end), interval(new, start, end)
    assert a.encode('utf-8') == b.encode('utf-8'), label
    unchanged[label] = {'bytes': len(b.encode('utf-8')), 'sha256': hashlib.sha256(b.encode('utf-8')).hexdigest()}
tables_old = re.findall(r'(?:^\|[^\n]*\n)+', old, flags=re.MULTILINE)
tables_new = re.findall(r'(?:^\|[^\n]*\n)+', new, flags=re.MULTILINE)
assert tables_old == tables_new

companions = {}
for filename in ['covered-ledger.csv', 'upstream-source-checks.json']:
    assert (R3 / filename).read_bytes() == (R4 / filename).read_bytes(), filename
    companions[filename] = signature(R4 / filename)
covered = rows(R4 / 'covered-ledger.csv')
ledger = rows(ROOT / 'docs/work/v1.8.0-content-ledger.csv')
expected_covered = [row for row in ledger if row['plan'] in ['C17a', 'C17b', 'C17c']
                    and row['disposition'] == 'PLANNED']
assert len(covered) == len(expected_covered) == len({row['unit_id'] for row in covered}) == 41
assert sorted(covered, key=lambda row: row['unit_id']) == sorted(expected_covered, key=lambda row: row['unit_id'])
assert all('`' + row['unit_id'] + '`' in new for row in covered)
sources = json.loads((R4 / 'upstream-source-checks.json').read_text(encoding='utf-8'))
java = {row['path']: row['sha256'] for row in rows(ROOT / 'legacy-manifest/java-files.csv')}
inventory_sources = json.loads((ROOT / 'docs/work/v1.8.0-legacy-inventory.json').read_text(encoding='utf-8'))['sources']
assert len(sources) == len({record['path'] for record in sources}) == 26
assert (ROOT / 'legacy-manifest/UPSTREAM_COMMIT.txt').read_text().strip() == 'c5cd5af62fc07cd4e0d24f06a16033f181c47c04'
source_checks = []
for record in sources:
    assert record['sha256'] == java[record['path']] == inventory_sources[record['path']], record['path']
    assert record['manifest_sha256'] == record['sha256'] and record['matches'] is True
    source_checks.append({'path': record['path'], 'sha256': record['sha256'], 'manifest_inventory_match': True})

previous_evidence = json.loads((PRIOR / 'static-evidence.json').read_text(encoding='utf-8'))
modern_base = ROOT / 'src/main/java/io/github/sunthemoon/advancedrocketrycommunity'
modern_observation = []
for filename, sha in previous_evidence['modern_source_sha256'].items():
    current = modern_base / filename
    modern_observation.append({'path': str(current), 'previous_sha256': sha,
                               'current_sha256': digest(current), 'matches_previous': digest(current) == sha})
network_pin = ROOT / 'src/test/resources/network-protocols.txt'
assert network_pin.read_text() == previous_evidence['current_network_pin']

assert 'status: PROPOSED\nrevision: 4\n' in new
assert 'owner_decisions_confirmed: [D1-A, D1-disassembly-A, D2-A, D3-A]' in new
assert 'owner_decisions_pending: []' in new
assert 'accepted_by: ""\naccepted_at: ""' in new
owner = OWNER.read_text(encoding='utf-8')
assert all('| ' + decision + ' |' in owner for decision in ['D1-A', 'D1-disassembly-A', 'D2-A', 'D3-A'])
c18 = C18_ADR.read_text(encoding='utf-8')
assert 'status: ACCEPTED' in c18 and 'owner_confirmed_decisions: [D1, D2, D3, D5]' in c18
assert 'station registry 4 -> 5 migration once' in c18
assert "journal 2 -> 3 migration once" in c18
assert 'station sky payload schema 1 -> 2' in c18
assert '`celestial_snapshot` channel protocol 3 -> 4' in c18
assert '`rocket_flight` channel 8 -> 9; C18 does not bump it again' in c18
assert 'D4 remains an unproven' in c18

after = snapshot()
assert before == after, 'review input changed during independent probe'
result = {
    'kind': 'independent_decision_only_static_review_checks_not_runtime_or_acceptance',
    'started_utc': started, 'finished_utc': datetime.now(timezone.utc).isoformat(),
    'python': platform.python_version(), 'inputs': before, 'input_bytes_unchanged_at_end': True,
    'verified_manifest_entries': manifest_entries,
    'actual_diff_byte_equal_to_supplied': True,
    'actual_diff_sha256': hashlib.sha256(actual_diff).hexdigest(),
    'diff_hunks': actual_diff.count(b'\n@@ '), 'changed_spans': changes,
    'complete_technical_intervals_byte_identical': unchanged,
    'all_markdown_tables_byte_identical': len(tables_new), 'companions_byte_identical': companions,
    'exact_current_c17_planned_rows': len(covered), 'batch_counts': dict(Counter(row['plan'] for row in covered)),
    'approved_upstream_commit': 'c5cd5af62fc07cd4e0d24f06a16033f181c47c04',
    'manifest_inventory_source_identity_checks': source_checks,
    'prior_modern_baseline_current_observation': modern_observation,
    'current_network_pin_identical_to_prior': signature(network_pin),
    'owner_branches': ['D1-A', 'D1-disassembly-A', 'D2-A', 'D3-A'],
    'author_status': 'PROPOSED; acceptance fields empty',
    'root_or_author_writes': 0, 'runtime_native_gradle_commands': 0,
    'gate_acceptance_or_runtime_completion': False,
}
(OUT / 'static-evidence.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
print(json.dumps({key: value for key, value in result.items()
                  if key not in ['inputs', 'verified_manifest_entries', 'changed_spans', 'manifest_inventory_source_identity_checks']}, indent=2))
