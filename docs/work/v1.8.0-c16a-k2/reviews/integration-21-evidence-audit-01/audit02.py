"""Independent compact logs/XML/artifact/commit evidence checks, no runtime."""
from collections import Counter
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import zipfile

B = Path(__file__).resolve().parent
R = Path('D:/GitHub/AdvancedRocketry-Community')
P = Path('D:/ARCE-Task-Evidence/v1.8.0/k2-continuation-01/ROOT-INTEGRATION-21-COMPACT-01.zip')
C = '3f3d62aed3980186fe0acc9592cf93ca436405fb'
A = Path('D:/ARCE-Task-Evidence/v1.8.0/integration-072fbcf821ce4ceb9fbcb3d98ec27d41/integration-artifacts-21')
def meta(b):
    return {'bytes':len(b),'sha256':hashlib.sha256(b).hexdigest()}
def save(n, v):
    (B/n).write_text(json.dumps(v, indent=2, ensure_ascii=False)+'\n', encoding='utf-8')
def git(path):
    p = subprocess.run(['git','show',C+':'+path],cwd=R,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=30)
    assert p.returncode == 0, (path,p.stderr)
    return p.stdout
def validate_zip(path, sha):
    assert meta(path.read_bytes())['sha256'] == sha, path
    z = zipfile.ZipFile(path)
    assert z.testzip() is None
    names = z.namelist()
    assert len(names)==len(set(names))
    for n in names:
        assert not n.startswith('/') and '..' not in n.split('/') and ':' not in n and '\\' not in n
        assert 'Development-Docs' not in n
    candidates = [n for n in names if n.rsplit('/',1)[-1] in ('EVIDENCE-MANIFEST.json','PACKET-MANIFEST.json')]
    root = [n for n in candidates if '/' not in n]
    assert len(root) == 1, root
    envelope = json.loads(z.read(root[0]))
    manifest = envelope.get('files',envelope)
    assert set(manifest) == set(names)-{root[0]}, (path, len(manifest),len(names))
    for n,pin in manifest.items():
        assert meta(z.read(n)) == pin, (path,n)
    return z, {'path':str(path),**meta(path.read_bytes()),'entries':len(names),
                'manifest_sha256':meta(z.read(root[0]))['sha256'],'all_manifested_files':len(manifest)}

with zipfile.ZipFile(P) as z:
    get = lambda n: json.loads(z.read(n))
    manifest = get('evidence/integration-21-source-manifest-r1.json')
    generated = {n.removeprefix('src/generated/v1.8/resources/'):pin for n,pin in manifest.items()
                 if n.startswith('src/generated/v1.8/resources/') and '/.cache/' not in n}
    assert len(generated)==771
    generated_results = []
    for pin in get('evidence/integration-artifacts-21.json'):
        if pin['name'].endswith('-api.jar'):
            continue
        with zipfile.ZipFile(A/pin['name']) as j:
            compared = {}
            for n,expected in generated.items():
                actual = meta(j.read(n))
                assert actual == expected, (pin['name'],n)
                compared[n] = actual
        generated_results.append({'name':pin['name'],'exact_v180_generated_files':len(compared),
                                  'comparison_set_sha256':meta(json.dumps(compared,sort_keys=True).encode())['sha256']})
    # Old source/resources has overlay writers and is not the v1.8 generated input domain.
    lineending = git('gradlew.bat')
    disk = (R/'gradlew.bat').read_bytes()
    assert meta(disk) == manifest['gradlew.bat']
    framing = {'path':'gradlew.bat','commit':meta(lineending),'tested_snapshot':meta(disk),
               'equal_after_CRLF_to_LF':disk.replace(b'\r\n',b'\n')==lineending,
               'Git_LF_lines':lineending.count(b'\n'),'snapshot_CRLF_pairs':disk.count(b'\r\n')}
    save('GENERATED-FRAMING-02.json', {'generated':generated_results,'wrapper_framing':framing,
          'initial_selector_note':'measure01 selected29 historical src/generated/resources and exposed2 later overlay tag differences, not v1.8 generator delta'})
    print('Generated/framing',json.dumps({'generated':generated_results,'wrapper':framing},indent=2))

    # All329 XML byte hashes and counts must match the Root collector record.
    independent = json.loads((B/'XML-MEASUREMENT-01.json').read_text(encoding='utf-8'))
    collector = get('evidence/integration-build-data-21-junit.json')
    assert collector['suites']==independent['suites']
    expected_counts = {k:independent[k] for k in ('tests','failures','errors','skipped')}
    assert collector['declared']==collector['testcase_elements']==expected_counts
    assert collector['xml']=={s['path'].rsplit('/',1)[-1]:{k:s[k] for k in ('bytes','sha256')} for s in independent['suite_details']}
    k2suite = [s for s in independent['suite_details'] if s['suite_name'].endswith('ClassicNbtCanonicalBytesTest')]
    assert len(k2suite)==1
    print('K2 added suite',json.dumps(k2suite[0],indent=2))

    jobs=[]
    for group in ('full','scoped'):
        table=get('evidence/checks-21-'+group+'.json')
        assert table['release_gate_claimed'] is False
        for job in table['jobs']:
            label=job['name'];command=get('evidence/'+label+'.command.json')
            exit_code=int(z.read('evidence/'+label+'.exit').strip())
            raw=z.read('evidence/'+label+'.log')
            assert exit_code==job['exit']==job['documented_exit']
            assert job['log_sha256']==meta(raw)['sha256']
            assert job['documented_failure_not_pass']==(exit_code!=0)
            jobs.append({'group':group,'label':label,'command':command,'exit':exit_code,
                         'documented_nonpass':job['documented_failure_not_pass'],'log':meta(raw)})
            print('JOB',label,'exit',exit_code,'command',command)
            print(raw.decode('utf-8','replace')[-1800:])
    save('JOBS-MEASUREMENT-02.json',jobs)

    # Exact known boundary wrapper variant only alters its output filename.
    oldwrapper=z.read('evidence/validate_repository_boundary15.py')
    newwrapper=z.read('evidence/validate_repository_boundary21.py')
    assert oldwrapper.replace(b'repository-boundary-15.json',b'repository-boundary-21.json')==newwrapper
    oldlinks=z.read('evidence/verify_current_links15.py')
    newlinks=z.read('evidence/verify_current_links17.py')
    assert oldlinks.replace(b'markdown-links-15.json',b'markdown-links-17.json')==newlinks
    print('WRAPPER exact output-name-only differences verified')

    for phase in ('integration-build-data-21','integration-gametest-12'):
        print('PHASE',phase)
        for suffix in ('command.json','started','finished','exit'):
            n='evidence/'+phase+'.'+suffix
            print(n,z.read(n).decode('utf-8').strip())
        log=z.read('evidence/'+phase+'.log').decode('utf-8')
        assert int(z.read('evidence/'+phase+'.exit').strip())==0
        assert len(re.findall(r'^BUILD SUCCESSFUL in ',log,re.M))==1
        print('Build/data tail',log[-1000:])
        if phase.endswith('data-21'):
            for line_no,line in enumerate(log.splitlines(),1):
                if 'Caching: total files:' in line:
                    print('DATAGEN',line_no,line)

    raw=z.read('evidence/integration-gametest-12-latest.log')
    log=raw.decode('utf-8')
    lines=log.splitlines()
    counts=get('evidence/integration-gametest-12-counts.json')
    assert counts['minecraft_log']==meta(raw)
    assert counts['gradle_log']==meta(z.read('evidence/integration-gametest-12.log'))
    batches=[]
    for i,l in enumerate(lines,1):
        if 'batch' in l.lower() and ('tests)' in l or 'tests:' in l):
            print('BATCH SAMPLE',i,l)
            if len(batches)<6:
                batches.append({'line':i,'text':l})
    headers=[]
    for i,l in enumerate(lines):
        if re.match(r'^\[\d\d:\d\d:\d\d\] \[[^\]]+\]',l):
            headers.append((i,l))
    errors=[]
    for index,(start,head) in enumerate(headers):
        if '/ERROR]' not in head:
            continue
        end=headers[index+1][0] if index+1<len(headers) else len(lines)
        body='\n'.join(lines[start:end])
        errors.append({'line':start+1,'header':head,'context_lines':end-start,
                       'context_sha256':meta(body.encode())['sha256'],
                       'frames':[l.strip() for l in lines[start+1:end] if 'io.github.sunthemoon' in l]})
    assert len(errors)==counts['logger_ERROR_lines']==61
    completions=[{'line':i,'text':l} for i,l in enumerate(lines,1) if 'GAME TESTS COMPLETE' in l or 'required tests passed' in l]
    assert len(completions)==2
    assert re.findall(r'All (\d+) required tests passed',log)==['464']
    assert re.findall(r'All (\d+) required tests passed',z.read('evidence/integration-gametest-12.log').decode('utf-8'))==['464']
    result={'launch_header':lines[0], 'completions':completions,'stop_tail':lines[-25:],
            'ERROR_count':len(errors),'errors':errors,'batch_samples':batches}
    save('GT-MEASUREMENT-02.json',result)
    print('GT completion',completions,'ERROR61 categories',Counter(e['header'].split('/ERROR] ')[-1] for e in errors))

    # Read exact named original packets in memory only. Their author review is distinct.
    packets=[('docs/work/v1.8.0-c16a-hatches/canonical-bytes-k2-source-01.zip',
              '3442f9b7c3fc06ff694c08f385a8cdfc8fc8b0a527a225b305e7cc7213788ace'),
             ('docs/work/v1.8.0-c16a-hatches/reviews/canonical-bytes-k2-independent-01.zip',
              'e4b46ec2657b66e88e8cf90c188bad5a7fe7c9f0a9f78c62906aa705bb0427e4')]
    pin_results=[]
    for relative,sha in packets:
        pz,pin=validate_zip(R/relative,sha)
        with pz:
            matched=[]
            for path in ('src/main/java/io/github/sunthemoon/advancedrocketrycommunity/machine/classic/adapter/ClassicNbtCanonicalBytes.java',
                         'src/test/java/io/github/sunthemoon/advancedrocketrycommunity/machine/classic/adapter/ClassicNbtCanonicalBytesTest.java'):
                suffix=path.rsplit('/',1)[-1]
                source_matches=[n for n in pz.namelist() if n.endswith('/'+suffix) and n.endswith('.java')]
                assert source_matches, (relative,path)
                codepin=meta(git(path))
                hits=[{'member':n,**meta(pz.read(n))} for n in source_matches if meta(pz.read(n))==codepin]
                assert hits, (relative,path)
                matched.append({'committed_path':path,'exact_postimage_matches':hits})
            pin['two_committed_postimages']=matched
            pin_results.append(pin)
            for n in pz.namelist():
                if n.rsplit('/',1)[-1] in ('HANDOFF.md','REVIEW-01.md','REPORT.md') and '/prior' not in n.lower():
                    text=pz.read(n).decode('utf-8','replace')
                    if len(text)<60000:
                        print('PACKET REPORT',relative,n,'\n',text[:16000])
    save('K2-PRIOR-PACKET-CROSSCHECK-02.json',pin_results)
    print('ALL SECOND MEASUREMENTS COMPLETE')
