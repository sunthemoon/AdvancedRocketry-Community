"""Replay unchanged committed artifact validator; no output in Root/artifact."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import zipfile
B=Path(__file__).resolve().parent
R=Path('D:/GitHub/AdvancedRocketry-Community')
P=Path('D:/ARCE-Task-Evidence/v1.8.0/k2-continuation-01/ROOT-INTEGRATION-21-COMPACT-01.zip')
with zipfile.ZipFile(P) as z:
    manifest=json.loads(z.read('evidence/integration-21-source-manifest-r1.json'))
    original=json.loads(z.read('evidence/artifact-21.command.json'))
path=R/'scripts/validate_build_artifact.py'
raw=path.read_bytes()
pin={'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest()}
assert pin==manifest['scripts/validate_build_artifact.py']
args=[sys.executable,'-B']+original['command'][2:]
assert '--content-manifest' not in args
p=subprocess.run(args,cwd=R,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=90)
(B/'artifact-replay-05.raw.log').write_bytes(p.stdout)
(B/'artifact-replay-05.raw.exit').write_text(str(p.returncode)+'\n',encoding='utf-8')
result={'command':args,'cwd':str(R),'validator':pin,'actual_exit':p.returncode,
        'raw_log_sha256':hashlib.sha256(p.stdout).hexdigest(),
        'Root_writes':False,'Java_or_native_runs':0}
(B/'ARTIFACT-REPLAY-05.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(p.stdout.decode('utf-8'))
assert p.returncode==0
