"""Freeze this own loose evidence directory; no archives/input copies."""
import hashlib
import json
from pathlib import Path
import re
import zipfile
B=Path(__file__).resolve().parent
target=B/'EVIDENCE-MANIFEST.json'
assert not target.exists()
expected={
 Path('D:/ARCE-Task-Evidence/v1.8.0/k2-continuation-01/ROOT-INTEGRATION-21-COMPACT-01.zip'):
 '9a9de4bf0f1bf41c7dabbfbbbfac48a89acdcccc919f9526349c8b7f527955f0',
 Path('D:/GitHub/AdvancedRocketry-Community/docs/work/v1.8.0-c16a-integration/ROOT-INTEGRATION-20.zip'):
 '3b6176f549d8bde523df1245fe7c4902e4633f093c40c6bce87db78fbf364bd2',
 Path('D:/GitHub/AdvancedRocketry-Community/docs/work/v1.8.0-c16a-hatches/canonical-bytes-k2-source-01.zip'):
 '3442f9b7c3fc06ff694c08f385a8cdfc8fc8b0a527a225b305e7cc7213788ace',
 Path('D:/GitHub/AdvancedRocketry-Community/docs/work/v1.8.0-c16a-hatches/reviews/canonical-bytes-k2-independent-01.zip'):
 'e4b46ec2657b66e88e8cf90c188bad5a7fe7c9f0a9f78c62906aa705bb0427e4',
}
def meta(raw):return {'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest()}
for p,sha in expected.items():assert meta(p.read_bytes())['sha256']==sha,p
with zipfile.ZipFile(next(iter(expected))) as z:
    for pin in json.loads(z.read('SCOPE.json'))['large_artifacts_external_not_in_git_packet']:
        assert meta(Path(pin['location']).read_bytes())=={k:pin[k] for k in ('bytes','sha256')}
links=[]
for name in ('REVIEW-01.md','TOOL-FAILURES.md'):
    raw=(B/name).read_text(encoding='utf-8')
    for url in re.findall(r'\[[^\]]*\]\(([^)]+)\)',raw):
        assert not url.startswith('/') and ':' not in url and '..' not in url.split('/')
        assert (B/url).is_file(),url
        links.append({'source':name,'target':url})
files={}
for p in sorted(B.iterdir()):
    assert p.is_file(),p
    assert p.suffix not in ('.zip','.jar','.class')
    files[p.name]=meta(p.read_bytes())
report=files['REVIEW-01.md']
body={'schema':1,'files':files,'scope':'Source21 compact actual-evidence audit, loose bundle only',
      'report':report,'file_count':len(files),'evidence_bytes':sum(v['bytes'] for v in files.values()),
      'relative_report_links':links,'redundant_archive':False,'Root_artifacts_inputs_postchecked_unchanged':True,
      'Root_or_author_writes':False,'Java_GameTest_native_launches':0}
target.write_text(json.dumps(body,indent=2)+'\n',encoding='utf-8')
raw=target.read_bytes()
assert all(meta((B/n).read_bytes())==pin for n,pin in files.items())
print(json.dumps({'directory':str(B),'manifest':meta(raw),'report':report,
                  'files':len(files)+1,'total_bytes_including_manifest':body['evidence_bytes']+len(raw),
                  'findings':{'Critical':0,'High':0,'Medium':0,'Low':0},'ZIP_created':False},indent=2))
