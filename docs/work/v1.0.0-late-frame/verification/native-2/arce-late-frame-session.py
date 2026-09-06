"""Short native late-frame delivery probe against a disposable fixture copy."""
from pathlib import Path
import importlib.util
import json
import re
import shutil
import sys
import time

ROOT = Path(r'D:\GitHub\AdvancedRocketry-Community')
TEMP = Path(r'C:\Users\Administrator\AppData\Local\Temp')
WORK = TEMP / 'arce-late-frame-session-2'
OUT = ROOT / 'docs/work/v1.0.0-late-frame/verification/native-2'
RUNTIME = TEMP / 'arce-v100-compatibility/runtime-1'
JAVA = r'C:\Program Files\Java\jdk-17.0.7\bin\java.exe'
ARTIFACT = ROOT / 'build/libs/advancedrocketry-community-1.20.1-1.0.0-dev.jar'
sys.path.insert(0,str(ROOT))
from scripts import run_dedicated_server_smoke as server
from scripts import run_v100_compatibility_matrix as matrix
from scripts import v100_compatibility_runtime as runtime

spec = importlib.util.spec_from_file_location('control',TEMP/'arce-console-ui-control.py')
control = importlib.util.module_from_spec(spec)
spec.loader.exec_module(control)
assert not WORK.exists() and not OUT.exists()
WORK.mkdir(); OUT.mkdir(parents=True)
for source in (Path(__file__),Path(spec.origin),TEMP/'ArceLateFrameProbe.java'):
    shutil.copy2(source,OUT/source.name)
summary = dict(status='IN_PROGRESS',artifact_sha256=server.digest_file(ARTIFACT),
               authentication_tested=False,started_at=matrix.now(),screenshots=[],checks=[])
dedicated = client = probe = None

def shot(name):
    receipt = window.screenshot(game,OUT/(name+'.png'))
    summary['screenshots'].append(receipt)
    return receipt

def check(command):
    start=len(probe.lines); probe.command(command)
    index=probe.wait_for(re.compile('PASS '+re.escape(command)),15,start_at=start)
    summary['checks'].append(probe.lines[index].strip())
    print(probe.lines[index].strip(),flush=True)

try:
    server_game=WORK/'server'
    shutil.copytree(RUNTIME/'servers/47.4.10',server_game)
    shutil.copytree(TEMP/'arce-presentation-duo-ui/server/world',server_game/'world')
    shutil.copy2(TEMP/'arce-presentation-duo-ui/server/ops.json',server_game/'ops.json')
    matrix.install_mods(server_game,ARTIFACT,None)
    port=server.allocate_port(); debug_port=server.allocate_port()
    server.write_server_configuration(server_game,port,True)
    args=server_game/'libraries/net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt'
    dedicated=server.CapturedProcess([JAVA,'-Xms512M','-Xmx2G',
        '-Dadvancedrocketrycommunity.releaseTestHooks=true','@'+str(args),'nogui'],server_game,OUT/'server-full.txt')
    summary.update(server_pid=dedicated.process.pid,port=port,debug_port=debug_port)
    dedicated.wait_for(server.READY_MARKER,240)
    dedicated.wait_for(re.compile('ARCE_ROCKET_ENTITY_ACTIVE entity=b78255b4-1f3c-4180-b513-3018f388dc6a'),30)
    game=WORK/'client'; game.mkdir(); matrix.install_mods(game,ARTIFACT,None)
    (game/'options.txt').write_text(matrix.CLIENT_OPTIONS+'pauseOnLostFocus:false\ntutorialStep:none\n')
    command=runtime.client_command(RUNTIME,game,'47.4.10','V100Visual1',port,JAVA)
    command.insert(1,f'-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:{debug_port}')
    matrix.argument_file(game/'client.args',command[1:])
    client=server.CapturedProcess([JAVA,'@client.args'],game,OUT/'client-full.txt')
    summary['client_pid']=client.process.pid
    runtime.write_json(OUT/'checkpoint.json',summary)
    print('CLIENT_START '+json.dumps(summary),flush=True)
    client.wait_for(matrix.marker('Connected to a modded server.'),240)
    dedicated.wait_for(matrix.marker('V100Visual1 joined the game'),30)
    window=control.Window(client.process.pid); window.resize(1280,720)
    time.sleep(1)
    probe=server.CapturedProcess([JAVA,'--add-modules','jdk.jdi','-cp',str(TEMP),
        'ArceLateFrameProbe',str(debug_port)],WORK,OUT/'probe-full.txt')
    summary['probe_pid']=probe.process.pid
    probe.wait_for(re.compile('CAPTURE_ARMED'),15)
    window.click(640,360,True)
    probe.wait_for(re.compile('READY_COMMANDS'),15)
    time.sleep(.6); shot('01-open')
    window.key(27); time.sleep(.4); check('reject-closed'); shot('02-closed')
    window.click(640,360,True); time.sleep(.8)
    check('reject-reopened'); check('reject-wrong-rocket'); check('positive'); shot('03-reopened')
    dedicated.command('kick V100Visual1 Late-frame disconnected-player probe')
    dedicated.wait_for(matrix.marker('V100Visual1 left the game'),15)
    time.sleep(.5); check('reject-no-player'); shot('04-disconnected')
    probe.command('stop'); code=probe.process.wait(timeout=15)
    assert code==0,code
    summary['status']='PASS'
finally:
    errors=[]
    if probe is not None:
        probe.abort(); summary['probe_exit_code']=probe.process.returncode
    if client is not None:
        try:
            if client.process.poll() is None:
                matrix.close_owned_window(client)
                assert client.process.wait(timeout=30)==0
                assert not server.scan_log(client.lines)
        except Exception as exc: errors.append('client cleanup: '+repr(exc))
        finally: client.abort()
        summary['client_exit_code']=client.process.returncode
        summary['client_native_logs']=matrix.archive_native_logs(game,OUT,'client')
    if dedicated is not None:
        try:
            if dedicated.process.poll() is None: matrix.stop_server(dedicated)
        except Exception as exc: errors.append('server cleanup: '+repr(exc))
        finally: dedicated.abort()
        summary['server_exit_code']=dedicated.process.returncode
        summary['server_native_logs']=matrix.archive_native_logs(server_game,OUT,'server')
    summary['cleanup_errors']=errors; summary['completed_at']=matrix.now()
    if errors or summary['status']!='PASS': summary['status']='FAIL'
    runtime.write_json(OUT/'summary.json',summary)
print(json.dumps(summary),flush=True)
if summary['status']!='PASS': raise SystemExit(1)
