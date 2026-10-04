"""Finite native log contexts and exact named Git source-line attribution."""
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile
B=Path(__file__).resolve().parent
R=Path('D:/GitHub/AdvancedRocketry-Community')
C='3f3d62aed3980186fe0acc9592cf93ca436405fb'
P=Path('D:/ARCE-Task-Evidence/v1.8.0/k2-continuation-01/ROOT-INTEGRATION-21-COMPACT-01.zip')
OLD=R/'docs/work/v1.8.0-c16a-integration/ROOT-INTEGRATION-20.zip'
HEADER=re.compile(r'^\[[^\]\r\n]+\] \[[^\]\r\n]+/(?:TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\]')
BATCH=re.compile(r"Running test batch '([^']+)' \((\d+) tests\)")
def sha(b):return hashlib.sha256(b).hexdigest()
def analyze(raw):
    lines=raw.decode('utf-8').splitlines()
    heads=[i for i,l in enumerate(lines) if HEADER.match(l)]
    contexts=[]
    for k,i in enumerate(heads):
        if '/ERROR]' not in lines[i]:continue
        end=heads[k+1] if k+1<len(heads) else len(lines)
        text='\n'.join(lines[i:end])
        head=lines[i]
        if 'oversized recipe input' in head:category='recipe_guard_negative'
        elif 'machine_menu] rejected' in head:category='menu_protocol_negative'
        elif 'Precision' in head or 'Energy-only migration' in head:category='precision_migration_negative'
        elif 'Failed to save chunk' in head and ('PrecisionAssemblerMigration' in text):category='precision_migration_negative'
        elif 'Satellite' in head:category='satellite_negative'
        elif 'Disabling machine recipe' in head:category='unbound_recipe_negative'
        elif 'oversized combustion input' in head:category='combustion_guard_negative'
        else:category='unclassified'
        frames=[l.strip() for l in lines[i+1:end] if 'at ' in l and 'io.github.sunthemoon' in l]
        contexts.append({'line':i+1,'category':category,'header':head,'context_lines':end-i,
                         'sha256':sha(text.encode()),'frames':frames})
    batches=[{'line':i+1,'name':m.group(1),'tests':int(m.group(2))} for i,l in enumerate(lines) if (m:=BATCH.search(l))]
    return {'batches':len(batches),'batch_test_sum':sum(b['tests'] for b in batches),'batch_rows':batches,
            'ERROR_headers':len(contexts),'ERROR_categories':dict(Counter(c['category'] for c in contexts)),
            'contexts':contexts,'log_bytes':len(raw),'log_sha256':sha(raw)}
with zipfile.ZipFile(P) as z:
    actual=analyze(z.read('evidence/integration-gametest-12-latest.log'))
    names=json.loads(z.read('evidence/integration-21-source-manifest-r1.json'))
with zipfile.ZipFile(OLD) as z:
    matches=[n for n in z.namelist() if n.endswith('integration-gametest-11-latest.log')]
    assert len(matches)==1
    old=analyze(z.read(matches[0]))
assert actual['batch_test_sum']==464
assert actual['ERROR_categories']==old['ERROR_categories']
assert 'unclassified' not in actual['ERROR_categories']
assert not any('ClassicNbtCanonicalBytes' in c['header']+'\n'+'\n'.join(c['frames']) for c in actual['contexts'])
print('Actual batches',actual['batches'],actual['batch_test_sum'],'baseline',old['batches'],old['batch_test_sum'])
print('Actual/baseline ERROR categories',actual['ERROR_categories'])
source_requests={}
for context in actual['contexts']:
    for frame in context['frames']:
        match=re.search(r'\(([^():]+\.java):(\d+)\)',frame)
        if match and ('GameTests' in match.group(1) or match.group(1)=='GuardedChunkSaves.java'):
            file,line=match.group(1),int(match.group(2))
            pathmatches=[n for n in names if n.endswith('/'+file)]
            assert len(pathmatches)==1,(file,pathmatches)
            source_requests.setdefault(pathmatches[0],set()).add(line)
# Stackless log categories get relevant exact test/recipe scope, not inferred call stacks.
for file in ('RecipeMenuChannelGameTests.java','RecipeSignatureGameTests.java','RecipeSignatureProtection.java','MachineRecipeRepository.java'):
    paths=[n for n in names if n.endswith('/'+file)]
    assert len(paths)==1,paths
    source_requests.setdefault(paths[0],set())
sources=[]
for path,lines in sorted(source_requests.items()):
    p=subprocess.run(['git','show',C+':'+path],cwd=R,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=30)
    assert p.returncode==0
    raw=p.stdout;txt=raw.decode('utf-8').splitlines()
    assert {'bytes':len(raw),'sha256':sha(raw)}==names[path]
    if not lines:
        words=('reject','assert','throw','unbound','empty','EMPTY','rebind','reload','bind','oversized')
        lines={i for i,l in enumerate(txt,1) if any(w in l for w in words)}
    snippets=[]
    selected=set()
    for center in sorted(lines):
        for line in range(max(1,center-4),min(len(txt),center+5)+1):
            if line not in selected:
                selected.add(line);snippets.append({'line':line,'text':txt[line-1]})
    source={'path':path,'bytes':len(raw),'sha256':sha(raw),'selected_evidence_lines':snippets}
    sources.append(source)
    print('\nSOURCE',path,sha(raw))
    for s in snippets:print(s['line'],s['text'])
result={'candidate':actual,'baseline':{k:v for k,v in old.items() if k not in ('contexts','batch_rows')},
        'source_evidence':sources,'interpretation_limits':['Finite context attribution is not a clean-log waiver',
        'Stackless unbound recipe logs correlate with recipe_signatures batch and explicit negative rebind test; not an exact callback trace',
        'No K2 GameTest consumer/runtime/persistence execution is inferred','No copied baseline native world proof']}
(B/'GT-CONTEXT-04.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print('GT finite-context check completed')
