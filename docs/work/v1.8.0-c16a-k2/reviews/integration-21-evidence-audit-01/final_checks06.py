"""Final raw execution/old failure/pin checks; no source/artifact copies."""
from datetime import datetime
import hashlib
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET
import zipfile
B=Path(__file__).resolve().parent
R=Path('D:/GitHub/AdvancedRocketry-Community')
P=Path('D:/ARCE-Task-Evidence/v1.8.0/k2-continuation-01/ROOT-INTEGRATION-21-COMPACT-01.zip')
C='3f3d62aed3980186fe0acc9592cf93ca436405fb'
def meta(b):return {'bytes':len(b),'sha256':hashlib.sha256(b).hexdigest()}
def suite(raw):
    root=ET.fromstring(raw)
    cases=root.findall('testcase')
    totals={'tests':len(cases),'failures':sum(c.find('failure') is not None for c in cases),
            'errors':sum(c.find('error') is not None for c in cases),'skipped':sum(c.find('skipped') is not None for c in cases)}
    assert totals=={k:int(root.attrib[k]) for k in totals}
    return {'suite':root.attrib['name'],**totals,'failure_names':[c.attrib['name'] for c in cases if c.find('failure') is not None],**meta(raw)}
def git(args):
    p=subprocess.run(['git']+args,cwd=R,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=30)
    assert p.returncode==0,(args,p.stderr)
    return p.stdout
with zipfile.ZipFile(P) as z:
    j=lambda n:json.loads(z.read(n))
    mf=j('evidence/integration-21-source-manifest-r1.json')
    assert meta(z.read('context/AGENTS.md.raw.txt'))==mf['AGENTS.md']
    update=j('evidence/integration-21-governance-update-01.json')
    assert update['revised_manifest_sha256']==meta(z.read('evidence/integration-21-source-manifest-r1.json'))['sha256']
    assert update['changes']['AGENTS.md']['after']==mf['AGENTS.md']
    drift=j('evidence/integration-21-collection-failure-01.json')
    assert drift['artifact_collection_exit']==1 and drift['GameTest_started'] is False
    assert drift['input_drift'][0]['after']==meta(z.read('evidence/integration-21-live-AGENTS-drift-01.raw.txt'))
    assert j('evidence/owned-evidence-relocation-01.json')['InitialWholeRootMoveExit']==1

    phases=[]
    for phase in ('integration-build-data-21','integration-gametest-12'):
        command=j('evidence/'+phase+'.command.json')
        start=z.read('evidence/'+phase+'.started').decode().strip()
        end=z.read('evidence/'+phase+'.finished').decode().strip()
        assert command['started_utc']==start
        elapsed=(datetime.fromisoformat(end)-datetime.fromisoformat(start)).total_seconds()
        assert elapsed>0
        assert command['JAVA_HOME']=='C:/Program Files/Java/jdk-17.0.7'
        for flag in ('--offline','--no-daemon','--max-workers=2','-Dorg.gradle.jvmargs=-Xmx2G'):
            assert flag in command['command']
        phases.append({'phase':phase,'command':command,'started':start,'finished':end,'elapsed_seconds':elapsed,
                       'exit':int(z.read('evidence/'+phase+'.exit').strip()),
                       'log':meta(z.read('evidence/'+phase+'.log'))})
    assert datetime.fromisoformat(phases[0]['finished'])<datetime.fromisoformat(phases[1]['started'])
    disk=j('evidence/integration-gametest-12-disk-precheck.json')
    assert disk['C']>=disk['ThresholdBytes'] and disk['D']>=disk['ThresholdBytes']
    assert datetime.fromisoformat(disk['CheckedUtc'])<datetime.fromisoformat(phases[1]['started'])

    bound=j('evidence/repository-boundary-21.json')
    assert bound['validator_sha256']==mf['scripts/validate_repository.py']['sha256']
    assert bound['exit']==0 and bound['release_gate_claimed'] is False
    for call in bound['calls']:
        if '--others' in call['command']:
            assert call['foreign_directory_excluded'] and '--exclude=/AdvancedRocketry-Community-v1plus-Development-Docs/' in call['command']
    links=j('evidence/markdown-links-17.json')
    assert len(links['inputs'])==links['files']==675 and links['links']==3101 and not links['errors']
    assert all('Development-Docs' not in row['path'] for row in links['inputs'])
    linkgov=[v for v in links['inputs'] if v['path']=='AGENTS.md']
    assert len(linkgov)==1 and linkgov[0]['sha256']==drift['input_drift'][0]['after']['sha256']

    push=j('operations/PHASE-PUSH-01.json')
    assert push['exit']==int(z.read('operations/phase-push01.exit').strip())==0
    assert push['force'] is False and '--force' not in push['command']
    assert push['log_sha256']==meta(z.read('operations/phase-push01.log'))['sha256']
    commit_meta=json.loads(git(['show','-s','--format={"commit":"%H","tree":"%T","subject":"%s"}',C]))
    commits=j('operations/PHASE-COMMITS-01.json')
    # Full code-object pin stays immutable even if Root has advanced documentation HEAD.
    refs={'observed_HEAD':git(['rev-parse','HEAD']).decode().strip(),
          'observed_origin_branch':git(['rev-parse','refs/remotes/origin/codex/v1.8.0-classic-content']).decode().strip(),
          'pinned_code_commit':commit_meta,
          'remote_network_verification':False,'force_push':False,'actual_recorded_push':push}

    oldpath=R/'docs/work/v1.8.0-c16a-integration/ROOT-INTEGRATION-20.zip'
    with zipfile.ZipFile(oldpath) as old:
        assert meta(old.read('EVIDENCE-MANIFEST.json'))['sha256']=='3e949645da7787df3e5b708d8b465dfc3217db0640028463c0bb934f5847f363'
        om=json.loads(old.read('EVIDENCE-MANIFEST.json'))['files']
        assert set(om)==set(old.namelist())-{'EVIDENCE-MANIFEST.json'}
        assert len(set(old.namelist()))==len(old.namelist())==3403
        for name,pin in om.items():assert meta(old.read(name))==pin,name
        assert old.read('evidence/integration-20-source-manifest.json')==z.read('evidence/integration-20-source-manifest.json')
    prior=[]
    for path, selected in [(R/'docs/work/v1.8.0-c16a-hatches/canonical-bytes-k2-source-01.zip',
                         ['scoped-01-xml/','scoped-02-xml/']),
                         (R/'docs/work/v1.8.0-c16a-hatches/reviews/canonical-bytes-k2-independent-01.zip',
                         ['reviewer-scoped-01-xml/'])]:
        with zipfile.ZipFile(path) as pz:
            rows=[]
            for prefix in selected:
                for n in pz.namelist():
                    if n.startswith(prefix) and n.endswith('.xml'):
                        rows.append({'member':n,**suite(pz.read(n))})
            prior.append({'path':str(path),'selected_actual_XML':rows,'not_independently_reexecuted_here':True})
    result={'phases':phases,'disk_precheck':disk,'governance_initial_collector_nonpass':drift,
            'R1_governance_observation':update,'links_observation_scope':{'files':675,'links':3101,'errors':0,
            'AGENTS_snapshot_at_link_run':linkgov[0],'does_not_cover_later_Root_docs':True},
            'boundary_actual_wrapper':bound,'code_and_push':refs,'prior_K2_test_evidence':prior,
            'baseline_full3402_manifested_entries_verified':True,
            'compact_postcheck':meta(P.read_bytes()),'Java_GT_native_reexecutions':0}
    assert result['compact_postcheck']['sha256']=='9a9de4bf0f1bf41c7dabbfbbbfac48a89acdcccc919f9526349c8b7f527955f0'
    (B/'FINAL-CHECKS-06.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print('Raw phase durations',[(v['phase'],v['elapsed_seconds']) for v in phases])
    print('Pinned code commit / observed refs',refs)
    print('Prior red/final/reviewer K2 evidence',json.dumps(prior,indent=2))
    print('Full baseline manifest, links/static receipts, preserved non-PASS and final compact postcheck verified')
