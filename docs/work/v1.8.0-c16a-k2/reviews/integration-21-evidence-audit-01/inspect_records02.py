import json
from pathlib import Path
import zipfile
B = Path(__file__).resolve().parent
P = Path(json.loads((B / 'INTAKE-01.json').read_text(encoding='utf-8'))['origin'])
with zipfile.ZipFile(P) as z:
    for name in ['evidence/integration-gametest-12-counts.json', 'evidence/checks-21-full.json',
                 'evidence/checks-21-scoped.json', 'operations/PHASE-COMMITS-01.json',
                 'operations/PHASE-PUSH-01.json']:
        print('\n===== ' + name + ' =====\n' + z.read(name).decode('utf-8'))
    for name in ['evidence/integration-21-source-manifest.json',
                 'evidence/integration-21-source-manifest-r1.json',
                 'evidence/integration-20-source-manifest.json']:
        value = json.loads(z.read(name))
        print('\n===== ' + name + ' structure =====\n')
        if isinstance(value, dict):
            print(list(value)[:8])
            for key, item in list(value.items())[:3]:
                print(key, str(item)[:600])
        else:
            print(type(value).__name__, len(value), value[:2])
    for name in z.namelist():
        if name.endswith('.command.json'):
            print('\n===== ' + name + ' =====\n' + z.read(name).decode('utf-8'))
