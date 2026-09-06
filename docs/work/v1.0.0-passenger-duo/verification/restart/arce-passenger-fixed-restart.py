"""Short restart of the stopped, owned two-passenger landed fixture."""
from pathlib import Path
import json
import re
import shutil
import sys

ROOT = Path(r'D:\GitHub\AdvancedRocketry-Community')
WORK = Path(r'C:\Users\Administrator\AppData\Local\Temp\arce-passenger-fixed-ui\server')
OUT = ROOT / 'docs/work/v1.0.0-passenger-duo/verification/restart'
JAVA = r'C:\Program Files\Java\jdk-17.0.7\bin\java.exe'
sys.path.insert(0, str(ROOT))
from scripts import run_dedicated_server_smoke as server
from scripts import run_v100_compatibility_matrix as matrix

gui = json.loads((OUT.parent / 'fixed-native/summary.json').read_text())
assert not gui['cleanup_errors'] and all(p['exit_code'] == 0 for p in gui['processes'])
reports = [a['result']['receipt'] for a in gui['actions'] if a['request']['id'] == 54]
assert len(reports) == 1
entity = re.search(r'entity=([0-9a-f-]+)', reports[0]).group(1)
expected = reports[0].split('ARCE_RELEASE_TEST_FLIGHT_REPORT ', 1)[1]
mods = list((WORK / 'mods').glob('*.jar'))
assert len(mods) == 1 and server.digest_file(mods[0]) == gui['artifact_sha256']
OUT.mkdir(exist_ok=False)
shutil.copy2(__file__, OUT / Path(__file__).name)
args = WORK / 'libraries/net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt'
process = server.CapturedProcess([JAVA, '-Xms512M', '-Xmx2G',
    '-Dadvancedrocketrycommunity.releaseTestHooks=true', '@' + str(args), 'nogui'],
    WORK, OUT / 'server-full.txt')
result = dict(status='IN_PROGRESS', pid=process.process.pid, artifact_sha256=gui['artifact_sha256'])
try:
    process.wait_for(server.READY_MARKER, 240)
    process.wait_for(re.compile('ARCE_ROCKET_ENTITY_ACTIVE entity=' + re.escape(entity)), 30)
    result['receipts'] = []
    for command, marker in [
        ('execute in advancedrocketrycommunity:moon run arce rocket release-test report ' + entity,
         'ARCE_RELEASE_TEST_FLIGHT_REPORT entity='),
        ('execute in advancedrocketrycommunity:moon run data get entity ' + entity
         + ' RocketEntityData.flight_data.passengers', 'has the following entity data:')]:
        start = len(process.lines)
        process.command(command)
        index = process.wait_for(re.compile(marker), 10, start_at=start)
        result['receipts'].append(dict(command=command, receipt=process.lines[index].strip()))
    assert result['receipts'][0]['receipt'].split('ARCE_RELEASE_TEST_FLIGHT_REPORT ', 1)[1] == expected
    prior = next(a['result']['receipt'] for a in gui['actions'] if a['request']['id'] == 32)
    assert result['receipts'][1]['receipt'].split('has the following entity data:', 1)[1] == prior.split('has the following entity data:', 1)[1]
    matrix.stop_server(process)
    result['status'] = 'PASS'
finally:
    process.abort()
    result['exit_code'] = process.process.returncode
    result['full_log_sha256'] = server.digest_file(OUT / 'server-full.txt')
    result['native_logs'] = matrix.archive_native_logs(WORK, OUT, 'server')
    (OUT / 'summary.json').write_text(json.dumps(result, indent=2)+'\n', encoding='utf-8')
print(json.dumps(result))
