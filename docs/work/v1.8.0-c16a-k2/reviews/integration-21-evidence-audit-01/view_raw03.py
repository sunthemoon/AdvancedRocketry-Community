import json
from pathlib import Path
import zipfile
B = Path(__file__).resolve().parent
P = Path(json.loads((B/'INTAKE-01.json').read_text(encoding='utf-8'))['origin'])
with zipfile.ZipFile(P) as z:
    for name in ['evidence/collect_build21.py', 'evidence/checks21.py',
                 'evidence/validate_repository_boundary21.py', 'evidence/run_root_job21.py']:
        print('\n===== ' + name + ' =====\n' + z.read(name).decode('utf-8'))
    for name in ['evidence/integration-build-data-21.log', 'evidence/integration-gametest-12-latest.log',
                 'evidence/repository-boundary-21.log', 'evidence/focused-python-21.log',
                 'evidence/ledger-closure-21.log', 'evidence/owned-evidence-relocation-01.json']:
        lines = z.read(name).decode('utf-8', errors='replace').splitlines()
        print('\n===== ' + name + ' excerpts =====\n')
        if name.endswith('latest.log'):
            selection = list(enumerate(lines,1))[:8] + [(n,s) for n,s in enumerate(lines,1) if 'Starting game test batch' in s][:4] + list(enumerate(lines,1))[-30:]
        else:
            selection = list(enumerate(lines,1))[-60:]
        for n,s in selection: print(str(n)+': '+s)
