"""Package only owned independent-review outputs, leaving inputs unchanged."""
import hashlib
import json
import re
from datetime import datetime, timezone
from pathlib import Path

OUT = Path(__file__).resolve().parent


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def signature(path):
    return {'path': str(path), 'bytes': path.stat().st_size, 'sha256': digest(path)}


def inventory(directory):
    return {path.name: signature(path) for path in sorted(directory.iterdir()) if path.is_file()}


evidence = json.loads((OUT / 'static-evidence.json').read_text(encoding='utf-8'))
for label in ['r3', 'r4', 'prior_review']:
    initial = evidence['inputs'][label]
    directory = Path(next(iter(initial.values()))['path']).parent
    assert inventory(directory) == initial, label
for label in ['owner', 'c18_owner', 'c18_canonical', 'c18_acceptance']:
    entry = evidence['inputs'][label]
    assert signature(Path(entry['path'])) == entry, label
links = []
for path in sorted(OUT.glob('*.md')):
    content = path.read_text(encoding='utf-8')
    assert all(line == line.rstrip() for line in content.splitlines()), path.name
    for target in re.findall(r'\[[^\]]+\]\(([^)]+)\)', content):
        assert not re.match(r'(?i)[a-z]+:|[/\\]', target), (path.name, target)
        candidate = (OUT / target).resolve()
        assert candidate.parent == OUT and (candidate.is_file() or target in
                                           {'FILE-HASHES.json', 'packaging-evidence.json'}), target
        links.append({'file': path.name, 'target': target})
for path in OUT.glob('*.json'):
    json.loads(path.read_text(encoding='utf-8-sig'))
receipt = {'kind': 'independent_review_packaging_not_gate_or_root_acceptance',
           'finished_utc': datetime.now(timezone.utc).isoformat(),
           'review_inputs_unchanged_after_report': True,
           'relative_companion_links_checked': links,
           'json_markdown_whitespace_checks': True, 'root_or_author_writes': 0}
(OUT / 'packaging-evidence.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
manifest = {path.name: {'bytes': path.stat().st_size, 'sha256': digest(path)}
            for path in sorted(OUT.iterdir()) if path.is_file() and path.name != 'FILE-HASHES.json'}
(OUT / 'FILE-HASHES.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
assert all((OUT / entry['target']).is_file() for entry in links)
assert all((OUT / filename).stat().st_size == entry['bytes'] and
           digest(OUT / filename) == entry['sha256'] for filename, entry in manifest.items())
print(json.dumps({'review_sha256': digest(OUT / 'REVIEW-04.md'),
                  'manifest_sha256': digest(OUT / 'FILE-HASHES.json'),
                  'files': len(manifest), 'inputs_unchanged': True}, indent=2))
