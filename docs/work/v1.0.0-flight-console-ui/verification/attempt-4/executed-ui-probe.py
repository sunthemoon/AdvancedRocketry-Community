"""Timed native-window actions within the existing owned GUI session."""
from pathlib import Path
import importlib.util
import json
import shutil
import time

ROOT = Path(r'D:\GitHub\AdvancedRocketry-Community')
EVIDENCE = ROOT / 'docs/work/v1.0.0-flight-console-ui/verification/attempt-4'
GAME = Path(r'C:\Users\Administrator\AppData\Local\Temp\arce-console-ui-4\client')
spec = importlib.util.spec_from_file_location('window_control', Path(__file__).with_name('arce-console-ui-control.py'))
control = importlib.util.module_from_spec(spec)
spec.loader.exec_module(control)
checkpoint = json.loads(sorted(EVIDENCE.glob('checkpoint-*.json'))[-1].read_text(encoding='utf-8'))
window = control.Window(checkpoint['client_pid'])
assert window.size() == (960, 540)
shutil.copy2(__file__, EVIDENCE / 'executed-ui-probe.py')
records = []
try:
    window.focus()
    time.sleep(0.5)
    window.click(480, 302)
    time.sleep(0.5)
    records.append(window.screenshot(GAME, EVIDENCE / 'probe-01.png'))
    window.resize(1280, 720)
    time.sleep(0.5)
    records.append(window.screenshot(GAME, EVIDENCE / 'probe-02.png'))
    window.click(640, 520)
    time.sleep(0.6)
    records.append(window.screenshot(GAME, EVIDENCE / 'probe-03.png'))
    window.click(750, 342)
    window.click(640, 520)
    time.sleep(0.5)
    records.append(window.screenshot(GAME, EVIDENCE / 'probe-04.png'))
finally:
    (EVIDENCE / 'ui-probe-records.json').write_text(json.dumps(records, indent=2)+'\n', encoding='utf-8')
print(json.dumps(records, indent=2))
