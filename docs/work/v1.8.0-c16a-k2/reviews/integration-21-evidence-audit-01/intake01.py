"""Read exact compact evidence archive in memory; no source/JAR export."""
import hashlib
import json
from pathlib import Path, PurePosixPath
import stat
import zipfile

B = Path(__file__).resolve().parent
P = Path('D:/ARCE-Task-Evidence/v1.8.0/k2-continuation-01/ROOT-INTEGRATION-21-COMPACT-01.zip')
def meta(raw):
    return {'bytes': len(raw), 'sha256': hashlib.sha256(raw).hexdigest()}
assert meta(P.read_bytes()) == {'bytes': 1241813, 'sha256': '9a9de4bf0f1bf41c7dabbfbbbfac48a89acdcccc919f9526349c8b7f527955f0'}
with zipfile.ZipFile(P) as z:
    names = z.namelist()
    assert len(names) == len(set(names)) == 423
    for item in z.infolist():
        p = PurePosixPath(item.filename)
        assert not p.is_absolute() and '..' not in p.parts
        assert ':' not in item.filename and '\\' not in item.filename
        assert 'Development-Docs' not in item.filename
        assert not item.is_dir() and not stat.S_ISLNK(item.external_attr >> 16)
        assert item.file_size <= 20 * 1024 * 1024
    assert z.testzip() is None
    mraw = z.read('EVIDENCE-MANIFEST.json')
    assert meta(mraw)['sha256'] == '15959b5f7fc625a2694262fd6da99cb2204385d1192a2971d35076ac1de21d66'
    envelope = json.loads(mraw)
    manifest = envelope.get('files', envelope)
    assert set(manifest) == set(names) - {'EVIDENCE-MANIFEST.json'}
    for name, pin in manifest.items():
        assert meta(z.read(name)) == pin, name
    print('\n'.join(names))
    for name in names:
        if name.rsplit('/', 1)[-1] in ('SCOPE.md', 'README.md', 'HANDOFF.md'):
            print('\n===== ' + name + ' =====\n' + z.read(name).decode('utf-8'))
    result = {'origin': str(P), **meta(P.read_bytes()), 'entries': len(names),
              'manifest_sha256': meta(mraw)['sha256'],
              'all_CRC_safe_paths_manifest_sizes_SHA_verified': True,
              'member_names': names, 'no_full_export': True, 'Java_native_launches': 0}
(B / 'INTAKE-01.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
