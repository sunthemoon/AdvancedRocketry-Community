"""Package independent review evidence without writing any review input."""
import hashlib
import json
import re
from datetime import datetime, timezone
from pathlib import Path

OUT = Path(__file__).resolve().parent


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def inventory(path):
    return {item.name: {'path': str(item), 'bytes': item.stat().st_size, 'sha256': sha(item)}
            for item in sorted(path.iterdir()) if item.is_file()}


evidence = json.loads((OUT / 'static-evidence.json').read_text(encoding='utf-8'))
for label in ['r2', 'r3']:
    initial = evidence['inputs'][label]
    directory = Path(next(iter(initial.values()))['path']).parent
    assert inventory(directory) == initial, label
for label in ['owner', 'prior_review']:
    entry = evidence['inputs'][label]
    path = Path(entry['path'])
    assert sha(path) == entry['sha256'] and path.stat().st_size == entry['bytes'], label

links = []
for path in sorted(OUT.glob('*.md')):
    text = path.read_text(encoding='utf-8')
    assert all(line == line.rstrip() for line in text.splitlines()), path.name
    for target in re.findall(r'\[[^\]]+\]\(([^)]+)\)', text):
        assert not re.match(r'(?i)[a-z]+:|[/\\]', target), (path.name, target)
        candidate = (OUT / target).resolve()
        assert candidate.parent == OUT and (candidate.is_file() or target in
                                           {'FILE-HASHES.json', 'packaging-evidence.json'}), target
        links.append({'file': path.name, 'target': target})
for path in OUT.glob('*.json'):
    json.loads(path.read_text(encoding='utf-8-sig'))
receipt = {'kind': 'independent_review_packaging_not_gate_acceptance',
           'finished_utc': datetime.now(timezone.utc).isoformat(),
           'review_inputs_unchanged_after_report': True,
           'relative_companion_links_checked': links,
           'json_and_markdown_whitespace_checks': True, 'root_or_author_writes': 0}
(OUT / 'packaging-evidence.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
manifest = {path.name: {'bytes': path.stat().st_size, 'sha256': sha(path)}
            for path in sorted(OUT.iterdir()) if path.is_file() and path.name != 'FILE-HASHES.json'}
(OUT / 'FILE-HASHES.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
assert all((OUT / entry['target']).is_file() for entry in links)
print(json.dumps({'review_sha256': sha(OUT / 'REVIEW-03.md'),
                  'manifest_sha256': sha(OUT / 'FILE-HASHES.json'),
                  'files': len(manifest), 'inputs_unchanged': True}, indent=2))
