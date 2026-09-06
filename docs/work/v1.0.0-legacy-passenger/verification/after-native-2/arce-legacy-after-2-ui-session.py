"""Interactive JSON control for one fresh, bounded local GUI test session."""
from pathlib import Path
import hashlib
import importlib.util
import json
import queue
import re
import shutil
import sys
import threading
import time
import uuid

ROOT = Path(r'D:\GitHub\AdvancedRocketry-Community')
WORK = Path(r'C:\Users\Administrator\AppData\Local\Temp\arce-legacy-after-2-ui')
EVIDENCE = ROOT / 'docs/work/v1.0.0-legacy-passenger/verification/after-native-2'
RUNTIME = Path(r'C:\Users\Administrator\AppData\Local\Temp\arce-v100-compatibility\runtime-1')
JAVA = r'C:\Program Files\Java\jdk-17.0.7\bin\java.exe'
ARTIFACT = ROOT / 'build/libs/advancedrocketry-community-1.20.1-1.0.0-dev.jar'
USER = 'V100Visual1'
sys.path.insert(0, str(ROOT))
from scripts import run_v100_compatibility_matrix as matrix
from scripts import run_dedicated_server_smoke as server
from scripts import run_v060_flight_server_smoke as flight
from scripts import run_v070_station_server_smoke as station
from scripts import v100_compatibility_runtime as runtime

spec = importlib.util.spec_from_file_location('window_control', Path(__file__).with_name('arce-console-ui-control.py'))
control = importlib.util.module_from_spec(spec)
spec.loader.exec_module(control)

if WORK.exists() or EVIDENCE.exists():
    raise SystemExit('Refusing to overwrite a prior GUI session')
WORK.mkdir()
(WORK / 'requests').mkdir()
EVIDENCE.mkdir(parents=True)
for path in (Path(__file__), Path(spec.origin)):
    shutil.copy2(path, EVIDENCE / path.name)
summary = dict(started_at=matrix.now(), status='IN_PROGRESS', artifact_sha256=server.digest_file(ARTIFACT),
               source_state='development_worktree', authentication_tested=False, actions=[], screenshots=[], processes=[])
dedicated = client = second = None
second_game = WORK / 'client-2'
game = WORK / 'client'
server_game = WORK / 'server'
checkpoint = 0


def save():
    global checkpoint
    checkpoint += 1
    runtime.write_json(EVIDENCE / f'checkpoint-{checkpoint:04}.json', summary)


try:
    original = Path(r'C:\Users\Administrator\AppData\Local\Temp\arce-passenger-fixed-ui\server')
    shutil.copytree(original, server_game)
    mods = list((server_game / 'mods').glob('*.jar'))
    assert len(mods) == 1 and mods[0].name == ARTIFACT.name
    shutil.copy2(ARTIFACT, mods[0])
    args = server_game / 'libraries/net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt'
    port = server.allocate_port()
    properties = server_game / 'server.properties'
    properties.write_text(re.sub(r'^server-port=.*$', 'server-port=' + str(port), properties.read_text(), flags=re.MULTILINE), encoding='utf-8')
    dedicated = server.CapturedProcess([JAVA, '-Xms512M', '-Xmx2G',
        '-Dadvancedrocketrycommunity.releaseTestHooks=true', '@' + str(args), 'nogui'],
        server_game, EVIDENCE / 'server-full.txt')
    dedicated.wait_for(server.READY_MARKER, 240)
    index = dedicated.wait_for(re.compile('ARCE_ROCKET_ENTITY_ACTIVE entity=([0-9a-f-]+)'), 30)
    entity = re.search('entity=([0-9a-f-]+)', dedicated.lines[index]).group(1)
    summary['startup_recovery_findings'] = server.scan_log(dedicated.lines)
    prior = dict(owner='62bbb9cb-b2fa-39aa-9a6b-430b71f5493f')
    for name, directory, label in ((USER, game, 'client'), ('V100Visual2', second_game, 'client-2')):
        directory.mkdir()
        matrix.install_mods(directory, ARTIFACT, None)
        (directory / 'options.txt').write_text(matrix.CLIENT_OPTIONS + 'pauseOnLostFocus:false\ntutorialStep:none\n', encoding='utf-8')
        command = runtime.client_command(RUNTIME, directory, '47.4.10', name, port, JAVA)
        matrix.argument_file(directory / 'client.args', command[1:])
        process = server.CapturedProcess([JAVA, '@client.args'], directory, EVIDENCE / (label + '-full.txt'))
        if label == 'client':
            client = process
        else:
            second = process
        process.wait_for(matrix.marker('Connected to a modded server.'), 240)
        dedicated.wait_for(matrix.marker(name + ' joined the game'), 60)
    window = control.Window(client.process.pid)
    second_window = control.Window(second.process.pid)
    window.resize(1280,720)
    second_window.resize(1280,720)
    owner = prior['owner']
    stations = []
    shutil.copy2(server_game / 'ops.json', EVIDENCE / 'ops-after-setup.json')
    summary.update(second_client_pid=second.process.pid, second_username='V100Visual2',
        second_window=second_window.handle, owner_is_operator=False, entity=entity, stations=stations,
        owner=owner, username=USER, port=port, client_pid=client.process.pid,
        server_pid=dedicated.process.pid, window=window.handle)
    save()
    print('READY_GUI ' + json.dumps({key:summary[key] for key in ('entity','stations','client_pid','server_pid','window')}), flush=True)
    commands = queue.Queue()

    def read_commands():
        seen = set()
        while len(seen) < 256:
            for path in sorted((WORK / 'requests').glob('*.json')):
                if path.name not in seen:
                    if path.stat().st_size > 8192:
                        raise ValueError('Oversized control request')
                    commands.put(path.read_text(encoding='utf-8-sig'))
                    seen.add(path.name)
            time.sleep(0.15)

    threading.Thread(target=read_commands, daemon=True).start()
    deadline = time.monotonic() + 1200
    while time.monotonic() < deadline:
        line = commands.get(timeout=max(1, deadline-time.monotonic()))
        if line is None:
            break
        try:
            request = json.loads(line)
            action = request['op']
            target = request.get('player', 'owner')
            if target not in ('owner', 'operator'):
                raise ValueError('Unknown owned client')
            active_window = window if target == 'owner' else second_window
            active_game = game if target == 'owner' else second_game
            result = {}
            if action == 'stop':
                summary['status'] = 'COMPLETE_WITH_OBSERVATIONS'
                break
            if action == 'click':
                active_window.click(int(request['x']), int(request['y']), request.get('right', False))
            elif action == 'key':
                active_window.key(int(request['key']))
            elif action == 'focus':
                result['focused'] = active_window.focus()
            elif action == 'resize':
                active_window.resize(int(request['width']), int(request['height']))
            elif action == 'screenshot':
                name = request['name']
                if not re.fullmatch(r'[a-z0-9-]{1,80}', name):
                    raise ValueError('Invalid screenshot name')
                result = active_window.screenshot(active_game, EVIDENCE / (name + '.png'))
                summary['screenshots'].append(result)
            elif action == 'server':
                start = len(dedicated.lines)
                dedicated.command(request['command'])
                if request.get('wait'):
                    index = dedicated.wait_for(re.compile(request['wait']), 30, start_at=start)
                    result['receipt'] = dedicated.lines[index].strip()
            elif action == 'status':
                result = dict(size=active_window.size(), server=dedicated.process.poll(), client=client.process.poll(),
                              server_tail=dedicated.lines[-8:], client_tail=client.lines[-4:])
            else:
                raise ValueError('Unknown operation')
            summary['actions'].append(dict(at=matrix.now(), request=request, result=result))
            save()
            print('RESULT ' + json.dumps(dict(id=request.get('id'), result=result)), flush=True)
        except Exception as exc:
            summary['actions'].append(dict(at=matrix.now(), request=line.strip(), error=str(exc)))
            save()
            print('ACTION_ERROR ' + repr(exc), flush=True)
except BaseException as exc:
    summary['status'] = 'FAIL'
    summary['failure'] = type(exc).__name__ + ': ' + str(exc)
    raise
finally:
    cleanup_errors = []
    for label, process, directory in (('client-2', second, second_game), ('client', client, game)):
        if process is None:
            continue
        try:
            if process.process.poll() is None:
                matrix.close_owned_window(process)
                code = process.finish()
                if code or server.scan_log(process.lines):
                    raise RuntimeError('Native client exit/audit failed')
        except Exception as exc:
            cleanup_errors.append(label + ': ' + repr(exc))
        finally:
            process.abort()
            summary['processes'].append(dict(kind=label, exit_code=process.process.returncode,
                full_log_file=label + '-full.txt',
                full_log_sha256=server.digest_file(EVIDENCE / (label + '-full.txt')),
                native_logs=matrix.archive_native_logs(directory, EVIDENCE, label)))
    if dedicated is not None:
        try:
            if dedicated.process.poll() is None:
                matrix.stop_server(dedicated)
        except Exception as exc:
            cleanup_errors.append('server stop audit: ' + repr(exc))
        finally:
            dedicated.abort()
            summary['processes'].append(dict(kind='server', exit_code=dedicated.process.returncode,
                full_log_file='server-full.txt',
                full_log_sha256=server.digest_file(EVIDENCE / 'server-full.txt'),
                native_logs=matrix.archive_native_logs(server_game, EVIDENCE, 'server')))
    summary['cleanup_errors'] = cleanup_errors
    if cleanup_errors:
        summary['status'] = 'FAIL'
    summary['completed_at'] = matrix.now()
    save()
    runtime.write_json(EVIDENCE / 'summary.json', summary)
    if cleanup_errors:
        print('FAILED_CLEANUP_AUDIT ' + json.dumps(cleanup_errors), flush=True)
        sys.exit(1)


