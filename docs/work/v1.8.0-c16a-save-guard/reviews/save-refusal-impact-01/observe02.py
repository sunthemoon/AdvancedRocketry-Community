"""Additional named source/archival observations, not a persistence replay."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import zipfile

sys.stdout.reconfigure(encoding='utf-8')
B = Path(__file__).resolve().parent
R = Path('D:/GitHub/AdvancedRocketry-Community')
C = '3f3d62aed3980186fe0acc9592cf93ca436405fb'
I = json.loads((B / 'INPUT-EVIDENCE-01.json').read_text(encoding='utf-8'))

def meta(raw):
    return {'bytes': len(raw), 'sha256': hashlib.sha256(raw).hexdigest()}

post = {}
for key, expected in I['inputs'].items():
    if key.startswith('live:'):
        observed = meta((R / key[5:]).read_bytes())
        post[key] = {'initial': expected, 'final': observed, 'unchanged': observed == expected}
    elif key.startswith(C + ':'):
        name = key[len(C) + 1:]
        committed = subprocess.check_output(['git', 'show', C + ':' + name], cwd=R)
        assert meta(committed) == expected
        observed = meta((R / name).read_bytes())
        post[key] = {'committed': expected, 'live': observed, 'unchanged': observed == expected}
primary = I['inputs']['primary:C:/Users/Administrator/.gradle/caches/forge_gradle/maven_downloader/net/minecraftforge/forge/1.20.1-47.4.10/forge-1.20.1-47.4.10-sources.jar']
f = Path('C:/Users/Administrator/.gradle/caches/forge_gradle/maven_downloader/net/minecraftforge/forge/1.20.1-47.4.10/forge-1.20.1-47.4.10-sources.jar')
assert meta(f.read_bytes()) == primary
extra = {}
with zipfile.ZipFile(f) as z:
    for name in ('patches/net/minecraft/world/level/Level.java.patch',
                 'patches/net/minecraft/server/level/ServerLevel.java.patch',
                 'net/minecraftforge/common/util/LevelCapabilityData.java'):
        raw = z.read(name)
        lines = raw.decode('utf-8').splitlines()
        centers = {i for i, l in enumerate(lines, 1) if any(s in l for s in
                   ('CapabilityProvider', 'gatherCapabilities', 'LevelCapabilityData',
                    'serializeNBT', 'invalidateCaps', 'close(', 'initCapabilities'))}
        indices = set()
        for i in centers:
            indices.update(range(max(1, i - 2), min(len(lines), i + 6) + 1))
        extra[name] = {**meta(raw), 'lines': [{'line': i, 'text': lines[i-1]} for i in sorted(indices)]}
native = R / 'docs/work/v1.8.0-c16a-save-guard/reviews/native-correction-03-evidence.zip'
with zipfile.ZipFile(native) as z:
    commands_raw = z.read('native/tank-native-03/oversized-refusal/commands.json')
    stdout = z.read('native/tank-native-03/oversized-refusal/stdout.txt').decode('utf-8').splitlines()
    commands = json.loads(commands_raw)
    assert 'setblock 190 180 180 minecraft:stone' in commands
    marker_success = [{'line': i, 'text': l} for i, l in enumerate(stdout, 1)
                      if 'Changed the block at 190, 180, 180' in l]
    assert len(marker_success) == 1
    capture_raw = z.read('native/tank-native-03/inputs.json')
    native_commands = {'archive': str(native), 'commands_identity': meta(commands_raw),
                       'commands': commands, 'successful_marker_change': marker_success,
                       'host_inputs_identity': meta(capture_raw), 'host_inputs': json.loads(capture_raw)}
prior = R / 'docs/work/v1.8.0-c16a-save-guard/reviews/native-incident-01-evidence.zip'
with zipfile.ZipFile(prior) as z:
    raw = z.read('capture-patched-manifest.json')
    entries = json.loads(raw)
    declaration = [e for e in entries if e.get('sha256') ==
                   '1dcf74ad4961877f5c79d29b361596fd4222c8849f6db31fbcf663d1ba5ff072']
    assert len(declaration) == 1
    primary_runtime = {'archive_member_identity': meta(raw), 'declaration': declaration,
                       'runtime_JAR_reread': False, 'historical_disassembly_only': True}
result = {'commit': C, 'read_only_postchecks': post, 'primary_extra': extra,
          'historical_native_marker_observation': native_commands,
          'historical_patched_runtime_identity': primary_runtime,
          'Java_native_launches': 0}
(B / 'OBSERVATIONS-02.json').write_text(json.dumps(result, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
print(json.dumps({'commit': C, 'postchecks': len(post),
                  'unchanged_code': sum(v['unchanged'] for k,v in post.items() if k.startswith(C)),
                  'live_doc_drift': [k for k,v in post.items() if k.startswith('live:') and not v['unchanged']],
                  'historical_marker_change_confirmed': len(marker_success),
                  'extra_primary_inputs': len(extra), 'Java_native_launches': 0}, indent=2))
