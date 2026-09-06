"""Capture real two-client station interactions on already-owned windows."""
from pathlib import Path
from datetime import datetime, timezone
import importlib.util
import json
import shutil
import time

OUT = Path(r'D:\GitHub\AdvancedRocketry-Community\docs\work\v1.0.0-console-presentation\verification\gui')
WORK = Path(r'C:\Users\Administrator\AppData\Local\Temp\arce-presentation-duo-ui')
spec = importlib.util.spec_from_file_location('control', Path(__file__).with_name('arce-console-ui-control.py'))
control = importlib.util.module_from_spec(spec)
spec.loader.exec_module(control)
state = json.loads(sorted(OUT.glob('checkpoint-*.json'))[-1].read_text(encoding='utf-8'))
owner = control.Window(state['client_pid'])
operator = control.Window(state['second_client_pid'])
assert owner.size() == operator.size() == (1280, 720)
if (OUT / 'body-probe-records.json').exists():
    raise SystemExit('Refusing to repeat or overwrite the recorded probe')
shutil.copy2(__file__, OUT / Path(__file__).name)
records = []


def record(action, result=None):
    records.append(dict(at=datetime.now(timezone.utc).isoformat(), action=action, result=result))


try:
    operator.click(640, 520)
    record('operator launch-button click')
    time.sleep(0.35)
    record('owner screenshot', owner.screenshot(WORK / 'client', OUT / '04-owner-active-moon.png'))
    owner.click(640, 520)
    record('owner cancel-button click')
    time.sleep(0.4)
    record('owner screenshot', owner.screenshot(WORK / 'client', OUT / '05-owner-after-cancel.png'))
    record('operator screenshot', operator.screenshot(WORK / 'client-2', OUT / '06-operator-after-cancel.png'))
finally:
    with (OUT / 'body-probe-records.json').open('x', encoding='utf-8') as output:
        json.dump(records, output, indent=2)
        output.write('\n')
print(json.dumps(records, indent=2))
