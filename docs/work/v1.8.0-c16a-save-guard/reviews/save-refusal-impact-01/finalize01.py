"""Freeze a small loose report/log/result bundle; no archive or source export."""
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8')
B = Path(__file__).resolve().parent
R = Path('D:/GitHub/AdvancedRocketry-Community')
C = '3f3d62aed3980186fe0acc9592cf93ca436405fb'

def meta(raw):
    return {'bytes': len(raw), 'sha256': hashlib.sha256(raw).hexdigest()}

i = json.loads((B / 'INPUT-EVIDENCE-01.json').read_text(encoding='utf-8'))
v = json.loads((B / 'VERIFY-04.json').read_text(encoding='utf-8'))
sources = {k: val for k, val in {**i['inputs'], **v['inputs']}.items() if k.startswith(C + ':')}
post = {}
for key, expected in sources.items():
    name = key[len(C) + 1:]
    raw = subprocess.check_output(['git', 'show', C + ':' + name], cwd=R)
    assert meta(raw) == expected
    assert meta((R / name).read_bytes()) == expected
    post[key] = expected
for key, expected in i['inputs'].items():
    if key.startswith('live:'):
        assert meta((R / key[5:]).read_bytes()) == expected
report = (B / 'REVIEW-01.md').read_text(encoding='utf-8')
links = []
for target in re.findall(r'\]\(([^)]+)\)', report):
    assert not re.match(r'^[A-Za-z]:', target)
    p = (B / target.split('#', 1)[0]).resolve()
    assert p.is_relative_to(B.resolve()) and p.is_file(), target
    links.append(target)
result = {'immutable_code_commit': C, 'final_live_equal_source_count': len(post),
          'sources': post, 'live_docs_unchanged': 16, 'portable_report_links': len(links),
          'findings': {'Critical': 0, 'High': 0, 'Medium': 2, 'Low': 0},
          'Java_native_launches': 0, 'production_or_document_edits': 0,
          'current_runtime_or_risk_acceptance': False,
          'actual_static_controls': v['actual_result']}
(B / 'FINAL-POSTCHECK.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
files = {}
for p in B.iterdir():
    if p.is_file() and p.name not in ('EVIDENCE-MANIFEST.json', 'FINAL-RECEIPT.json', 'freeze-01.log', 'freeze-01.exit'):
        files[p.name] = meta(p.read_bytes())
manifest = {'scope': 'Read-only R-021/current-code save-refusal research; loose small evidence only',
            'immutable_code_commit': C, 'report_sha256': files['REVIEW-01.md']['sha256'],
            'files': dict(sorted(files.items())), 'file_count': len(files),
            'total_bytes': sum(x['bytes'] for x in files.values()),
            'external_inputs': 'INPUT-EVIDENCE-01.json, VERIFY-04.json, OBSERVATIONS-03.json; referenced only, not copied archives'}
(B / 'EVIDENCE-MANIFEST.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
receipt = {'directory': str(B), 'report': meta((B / 'REVIEW-01.md').read_bytes()),
           'manifest': meta((B / 'EVIDENCE-MANIFEST.json').read_bytes()),
           'file_count': len(files), 'total_bytes': manifest['total_bytes'], 'zip_created': False,
           'final_source_count': len(post), 'verified_own_entries': len(files), 'link_count': len(links)}
for n, expected in files.items():
    assert meta((B / n).read_bytes()) == expected
(B / 'FINAL-RECEIPT.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
print(json.dumps(receipt, indent=2))
