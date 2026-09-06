"""Restart the migrated disposable world without changing its saved state."""
from pathlib import Path
import json
import re
import shutil
import sys

ROOT = Path(r'D:\GitHub\AdvancedRocketry-Community')
WORK = Path(r'C:\Users\Administrator\AppData\Local\Temp\arce-passenger-continued-restart')
OUT = ROOT / 'docs/work/v1.0.0-passenger-continued-use/verification/clean-restart'
JAVA = r'C:\Program Files\Java\jdk-17.0.7\bin\java.exe'
sys.path.insert(0, str(ROOT))
from scripts import run_dedicated_server_smoke as server
from scripts import run_v100_compatibility_matrix as matrix

prior = json.loads((OUT.parent / 'native/summary.json').read_text())
assert len(prior['processes']) == 3 and all(p['exit_code'] == 0 for p in prior['processes'])
projection = json.loads((OUT.parent / 'after-world/projection.json').read_text())
assert projection['world_owned_only'] and len(projection['rockets']) == 1
rocket = projection['rockets'][0]
entity = rocket['entity']
assert not WORK.exists() and not OUT.exists()
shutil.copytree(Path(r'C:\Users\Administrator\AppData\Local\Temp\arce-passenger-continued-ui\server'), WORK)
OUT.mkdir()
shutil.copy2(__file__, OUT / Path(__file__).name)
mods = list((WORK / 'mods').glob('*.jar'))
assert len(mods) == 1 and server.digest_file(mods[0]) == prior['artifact_sha256']
args = WORK / 'libraries/net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt'
process = server.CapturedProcess([JAVA, '-Xms512M', '-Xmx2G',
    '-Dadvancedrocketrycommunity.releaseTestHooks=true', '@' + str(args), 'nogui'],
    WORK, OUT / 'server-full.txt')
result = dict(status='IN_PROGRESS', pid=process.process.pid, artifact_sha256=prior['artifact_sha256'], receipts=[])
try:
    process.wait_for(server.READY_MARKER, 240)
    process.wait_for(re.compile('ARCE_ROCKET_ENTITY_ACTIVE entity=' + re.escape(entity)), 30)
    for command, marker in [
        ('execute in minecraft:overworld run arce rocket release-test report ' + entity,
         'ARCE_RELEASE_TEST_FLIGHT_REPORT entity='),
        ('execute in minecraft:overworld run data get entity ' + entity
         + ' RocketEntityData.flight_data.passengers', 'has the following entity data:'),
        ('execute in minecraft:overworld if entity @e[type=advancedrocketrycommunity:rocket]',
         'Test passed, count: 1'),
        ('execute in advancedrocketrycommunity:moon run data get block 6 80 8 arce_fuel_loader',
         'has the following block data:')]:
        start = len(process.lines)
        process.command(command)
        index = process.wait_for(re.compile(marker), 10, start_at=start)
        result['receipts'].append(dict(command=command, receipt=process.lines[index].strip()))
    report = result['receipts'][0]['receipt']
    assert 'logical=' + rocket['logical'] in report and 'snapshot=' + rocket['snapshot'] in report
    assert ('state=LANDED fuel=' + str(rocket['fuel']) + ' capacity=1000 passengers=2 transfer=none') in report
    assert 'dimension=' + rocket['dimension'] in report and 'blocks=6' in report
    assert 'buffered_units: 118L' in result['receipts'][3]['receipt']
    matrix.stop_server(process)
    result['status'] = 'PASS'
except BaseException as exc:
    result['status'] = 'FAIL'
    result['failure'] = repr(exc)
    raise
finally:
    process.abort()
    result['exit_code'] = process.process.returncode
    result['full_log_sha256'] = server.digest_file(OUT / 'server-full.txt')
    result['native_logs'] = matrix.archive_native_logs(WORK, OUT, 'server')
    (OUT / 'summary.json').write_text(json.dumps(result, indent=2)+'\n', encoding='utf-8')
print(json.dumps(result))
