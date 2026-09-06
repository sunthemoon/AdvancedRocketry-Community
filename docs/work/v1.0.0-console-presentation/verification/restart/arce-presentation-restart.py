"""Restart the stopped owned presentation fixture and report the same rocket."""
from pathlib import Path
import json
import re
import shutil
import sys

ROOT = Path(r'D:\GitHub\AdvancedRocketry-Community')
WORK = Path(r'C:\Users\Administrator\AppData\Local\Temp\arce-presentation-duo-ui\server')
OUT = ROOT / 'docs/work/v1.0.0-console-presentation/verification/restart'
JAVA = r'C:\Program Files\Java\jdk-17.0.7\bin\java.exe'
sys.path.insert(0, str(ROOT))
from scripts import run_dedicated_server_smoke as server
from scripts import run_v100_compatibility_matrix as matrix

gui = json.loads((OUT.parent / 'gui/summary.json').read_text())
assert all(p['exit_code'] == 0 for p in gui['processes'])
OUT.mkdir(exist_ok=False)
shutil.copy2(__file__, OUT / Path(__file__).name)
args = WORK / 'libraries/net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt'
process = server.CapturedProcess([JAVA, '-Xms512M', '-Xmx2G',
    '-Dadvancedrocketrycommunity.releaseTestHooks=true', '@' + str(args), 'nogui'],
    WORK, OUT / 'server-full.txt')
result = dict(status='IN_PROGRESS', pid=process.process.pid, artifact_sha256=gui['artifact_sha256'])
try:
    process.wait_for(server.READY_MARKER, 240)
    process.wait_for(re.compile('ARCE_ROCKET_ENTITY_ACTIVE entity=' + re.escape(gui['entity'])), 30)
    start = len(process.lines)
    process.command('arce rocket release-test report ' + gui['entity'])
    index = process.wait_for(re.compile('ARCE_RELEASE_TEST_FLIGHT_REPORT entity='), 10, start_at=start)
    result['report'] = process.lines[index].strip()
    assert 'state=FUELED fuel=1000 capacity=1000 passengers=0 transfer=none' in result['report']
    matrix.stop_server(process)
    result['status'] = 'PASS'
finally:
    process.abort()
    result['exit_code'] = process.process.returncode
    result['full_log_sha256'] = server.digest_file(OUT / 'server-full.txt')
    result['native_logs'] = matrix.archive_native_logs(WORK, OUT, 'server')
    (OUT / 'summary.json').write_text(json.dumps(result, indent=2)+'\n', encoding='utf-8')
print(json.dumps(result))
