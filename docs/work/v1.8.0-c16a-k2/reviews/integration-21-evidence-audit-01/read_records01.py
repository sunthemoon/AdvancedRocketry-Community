"""Print selected exact evidence records only, no extraction or source export."""
import json
from pathlib import Path
import zipfile

B = Path(__file__).resolve().parent
P = Path(json.loads((B / 'INTAKE-01.json').read_text(encoding='utf-8'))['origin'])
names = ['SCOPE.json', 'evidence/integration-artifacts-21.json',
         'evidence/integration-21-preparation.json', 'evidence/integration-21-postimage-check.json',
         'evidence/integration-21-governance-update-01.json', 'evidence/integration-21-collection-failure-01.json',
         'evidence/integration-build-data-21-junit.json', 'evidence/integration-gametest-12-counts.json',
         'evidence/checks-21-full.json', 'evidence/checks-21-scoped.json',
         'operations/PHASE-COMMITS-01.json', 'operations/PHASE-PUSH-01.json']
with zipfile.ZipFile(P) as z:
    for name in names:
        print('\n===== ' + name + ' =====\n' + z.read(name).decode('utf-8'))
