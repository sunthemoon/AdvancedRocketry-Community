"""Timed actions on the two already-owned native clients; outputs are observations."""
from pathlib import Path
import importlib.util
import json
import shutil
import time
from datetime import datetime, timezone

OUT = Path(r'D:\GitHub\AdvancedRocketry-Community\docs\work\v1.0.0-flight-duo\verification\attempt-1')
WORK = Path(r'C:\Users\Administrator\AppData\Local\Temp\arce-console-duo-ui')
spec = importlib.util.spec_from_file_location('control', Path(__file__).with_name('arce-console-ui-control.py'))
control = importlib.util.module_from_spec(spec)
spec.loader.exec_module(control)
state = json.loads(sorted(OUT.glob('checkpoint-*.json'))[-1].read_text(encoding='utf-8'))
owner = control.Window(state['client_pid'])
operator = control.Window(state['second_client_pid'])
assert owner.size() == operator.size() == (1280, 720)
shutil.copy2(__file__, OUT / 'executed-duo-probe.py')
records = []


def record(action, result=None):
    records.append(dict(at=datetime.now(timezone.utc).isoformat(), action=action, result=result))


try:
    operator.click(640, 520)
    record('operator launch-button click')
    time.sleep(0.35)
    record('owner screenshot', owner.screenshot(WORK / 'client', OUT / 'probe-01-owner-active.png'))
    owner.key(9)
    record('owner Tab')
    time.sleep(0.15)
    owner.key(13)
    record('owner Enter')
    time.sleep(0.4)
    record('owner screenshot', owner.screenshot(WORK / 'client', OUT / 'probe-02-owner-after.png'))
    record('operator screenshot', operator.screenshot(WORK / 'client-2', OUT / 'probe-03-operator-after.png'))
finally:
    with (OUT / 'duo-probe-records.json').open('x', encoding='utf-8') as output:
        json.dump(records, output, indent=2)
        output.write('\n')
print(json.dumps(records, indent=2))
