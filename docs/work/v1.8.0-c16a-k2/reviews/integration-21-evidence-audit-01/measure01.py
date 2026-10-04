"""Bounded named-input measurement; source/artifacts consumed in memory only."""
from collections import Counter
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET
import zipfile

B = Path(__file__).resolve().parent
R = Path('D:/GitHub/AdvancedRocketry-Community')
P = Path('D:/ARCE-Task-Evidence/v1.8.0/k2-continuation-01/ROOT-INTEGRATION-21-COMPACT-01.zip')
C = '3f3d62aed3980186fe0acc9592cf93ca436405fb'
A = Path('D:/ARCE-Task-Evidence/v1.8.0/integration-072fbcf821ce4ceb9fbcb3d98ec27d41/integration-artifacts-21')
def meta(b):
    return {'bytes': len(b), 'sha256': hashlib.sha256(b).hexdigest()}
def save(name, value):
    (B / name).write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
def cmd(args, **kw):
    p = subprocess.run(args, cwd=R, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=180, **kw)
    assert p.returncode == 0, (args, p.returncode, p.stderr.decode('utf-8', 'replace'))
    return p.stdout
def measure_xml(z, prefix):
    suites = []
    count = Counter()
    for name in z.namelist():
        if name.startswith(prefix) and name.endswith('.xml'):
            raw = z.read(name)
            root = ET.fromstring(raw)
            assert root.tag == 'testsuite', name
            cases = root.findall('testcase')
            actual = {'tests': len(cases), 'failures': sum(c.find('failure') is not None for c in cases),
                      'errors': sum(c.find('error') is not None for c in cases),
                      'skipped': sum(c.find('skipped') is not None for c in cases)}
            for key, val in actual.items():
                assert int(root.attrib[key]) == val, (name, key)
            count.update(actual)
            suites.append({'path': name, 'suite_name': root.attrib['name'], **actual, **meta(raw)})
    assert suites
    return {'suites': len(suites), **count, 'suite_details': suites}

with zipfile.ZipFile(P) as z:
    get = lambda n: json.loads(z.read(n))
    old = get('evidence/integration-20-source-manifest.json')
    original = get('evidence/integration-21-source-manifest.json')
    current = get('evidence/integration-21-source-manifest-r1.json')
    source_delta = {'added': sorted(set(original)-set(old)), 'removed': sorted(set(old)-set(original)),
                    'changed': sorted(n for n in old.keys() & original.keys() if old[n] != original[n])}
    governance_delta = {'added': sorted(set(current)-set(original)), 'removed': sorted(set(original)-set(current)),
                        'changed': sorted(n for n in current.keys() & original.keys() if current[n] != original[n])}
    # One named batch object query; no working-tree source enumeration/export.
    requested = sorted(current)
    names_input = ''.join(C + ':' + n + '\n' for n in requested).encode('utf-8')
    raw = cmd(['git', 'cat-file', '--batch'], input=names_input)
    stream = io.BytesIO(raw)
    blobs = {}
    absent = []
    mismatches = []
    for name in requested:
        header = stream.readline().decode('utf-8').rstrip('\n')
        if header.endswith(' missing'):
            absent.append(name)
            continue
        oid, kind, size = header.split(' ')
        assert kind == 'blob', (name, kind)
        body = stream.read(int(size))
        assert stream.read(1) == b'\n' and len(body) == int(size)
        pin = meta(body)
        if pin != current[name]:
            mismatches.append({'path': name, 'commit': pin, 'manifest': current[name]})
        blobs[name] = body
    assert stream.read() == b''
    source_result = {'commit': C, 'counts': {'baseline': len(old), 'original': len(original), 'R1': len(current)},
                     'baseline_to_original': source_delta, 'original_to_R1': governance_delta,
                     'commit_missing': absent, 'commit_mismatches': mismatches,
                     'commit_exact': len(blobs)-len(mismatches), 'batch_bytes_consumed_in_memory': len(raw),
                     'no_source_export': True}
    save('SOURCE-MEASUREMENT-01.json', source_result)
    print(json.dumps(source_result, indent=2))

    xml = measure_xml(z, 'evidence/xml/')
    collector = get('evidence/integration-build-data-21-junit.json')
    print('collector XML shape', list(collector) if isinstance(collector, dict) else type(collector).__name__)
    save('XML-MEASUREMENT-01.json', xml)
    print('XML totals', {k:v for k,v in xml.items() if k != 'suite_details'})

    jars = {}
    for pin in get('evidence/integration-artifacts-21.json'):
        path = A / pin['name']
        jarraw = path.read_bytes()
        assert meta(jarraw) == {k:pin[k] for k in ('bytes', 'sha256')}
        with zipfile.ZipFile(io.BytesIO(jarraw)) as j:
            assert j.testzip() is None
            members = j.namelist()
            assert len(members) == len(set(members)) == pin['entries']
            files = {n:j.read(n) for n in members if not n.endswith('/')}
        jars[pin['name']] = {'raw': jarraw, 'files': files, 'names': members}
    generated = {n.removeprefix('src/generated/resources/'): b for n,b in blobs.items()
                 if n.startswith('src/generated/resources/') and not n.startswith('src/generated/resources/.cache/')}
    java = {n.removeprefix('src/main/java/'): b for n,b in blobs.items() if n.startswith('src/main/java/') and n.endswith('.java')}
    jar_details = []
    for name, jar in jars.items():
        is_api = name.endswith('-api.jar')
        gen_missing = [n for n in generated if n not in jar['files']] if not is_api else []
        gen_different = [n for n in generated if n in jar['files'] and generated[n] != jar['files'][n]] if not is_api else []
        java_missing = [n for n in java if n not in jar['files']] if name.endswith('-sources.jar') else []
        java_different = [n for n in java if n in jar['files'] and java[n] != jar['files'][n]] if name.endswith('-sources.jar') else []
        jar_details.append({'name': name, **meta(jar['raw']), 'entries':len(jar['names']),
                            'generated_count':len(generated) if not is_api else 0,
                            'generated_missing':gen_missing, 'generated_different':gen_different,
                            'main_java_count':len(java) if name.endswith('-sources.jar') else 0,
                            'main_java_missing':java_missing,'main_java_different':java_different})

    # Named immutable Root20 baseline, read in memory, never exported/copied.
    baseline = Path('D:/GitHub/AdvancedRocketry-Community/docs/work/v1.8.0-c16a-integration/ROOT-INTEGRATION-20.zip')
    assert meta(baseline.read_bytes())['sha256'] == '3b6176f549d8bde523df1245fe7c4902e4633f093c40c6bce87db78fbf364bd2'
    with zipfile.ZipFile(baseline) as o:
        assert o.testzip() is None
        old_xml_prefixes = sorted({n.rsplit('/',1)[0]+'/' for n in o.namelist() if '/TEST-' in n and n.endswith('.xml')})
        assert len(old_xml_prefixes) == 1, old_xml_prefixes
        old_xml = measure_xml(o, old_xml_prefixes[0])
        save('BASELINE-XML-MEASUREMENT-01.json', old_xml)
        for detail in jar_details:
            name = detail['name']
            matches = [n for n in o.namelist() if n.rsplit('/',1)[-1] == name]
            assert len(matches) == 1, matches
            oldraw = o.read(matches[0])
            with zipfile.ZipFile(io.BytesIO(oldraw)) as oj:
                assert oj.testzip() is None
                oldfiles = {n:oj.read(n) for n in oj.namelist() if not n.endswith('/')}
                oldnames = oj.namelist()
            newfiles = jars[name]['files']
            detail['baseline_member'] = matches[0]
            detail['baseline_identity'] = meta(oldraw)
            detail['whole_bytes_equal_baseline'] = oldraw == jars[name]['raw']
            detail['member_delta'] = {'added':sorted(set(newfiles)-set(oldfiles)),
                                      'removed':sorted(set(oldfiles)-set(newfiles)),
                                      'changed':sorted(n for n in newfiles.keys() & oldfiles.keys() if newfiles[n] != oldfiles[n]),
                                      'unchanged_file_count':sum(newfiles[n] == oldfiles[n] for n in newfiles.keys() & oldfiles.keys()),
                                      'baseline_entries':len(oldnames)}
    save('ARTIFACT-MEASUREMENT-01.json', jar_details)
    print('ARTIFACTS', json.dumps(jar_details, indent=2))
    print('BASELINE XML totals', {k:v for k,v in old_xml.items() if k != 'suite_details'})
    print('XML suite additions', sorted(set(t['suite_name'] for t in xml['suite_details'])-set(t['suite_name'] for t in old_xml['suite_details'])))
    print('ALL MEASUREMENTS COMPLETED; no Java/native run/source export')
