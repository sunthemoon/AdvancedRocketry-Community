import hashlib
import json
from pathlib import Path
import sys
sys.stdout.reconfigure(encoding='utf-8')
B = Path(__file__).resolve().parent
R = Path('D:/GitHub/AdvancedRocketry-Community')
names = ['AGENTS.md', 'PROJECT-CONFIG.md', 'PRODUCT.md', 'docs/01-PORTING-PRINCIPLES.md',
         'docs/04-VERSION-ROADMAP.md', 'docs/versions/V1.8.0-CLASSIC-CONTENT-COMPLETION.md',
         'docs/05-MASTER-TEST-PLAN.md', 'docs/06-RELEASE-AND-ACCEPTANCE-GATES.md',
         'docs/16-POST-1.0-VERSION-ROADMAP.md', 'docs/17-V1PLUS-QUALITY-BUDGETS.md',
         'docs/14-PARALLEL-DEVELOPMENT-AND-WORKTREE-COORDINATION.md']
pins = {}
for name in names:
    raw = (R / name).read_bytes()
    pins[name] = {'bytes': len(raw), 'sha256': hashlib.sha256(raw).hexdigest()}
    print('\n==== ' + name + ' ====\n' + raw.decode('utf-8'))
(B / 'GOVERNANCE-02.json').write_text(json.dumps(pins, indent=2) + '\n', encoding='utf-8')
