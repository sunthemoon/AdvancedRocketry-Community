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
WORK = Path(r'C:\Users\Administrator\AppData\Local\Temp\arce-console-ui-4')
EVIDENCE = ROOT / 'docs/work/v1.0.0-flight-console-ui/verification/attempt-4'
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
dedicated = client = None
game = WORK / 'client'
server_game = WORK / 'server'
checkpoint = 0


def save():
    global checkpoint
    checkpoint += 1
    runtime.write_json(EVIDENCE / f'checkpoint-{checkpoint:04}.json', summary)


try:
    source = RUNTIME / 'servers/47.4.10'
    if any((source / name).exists() for name in ('world', 'mods', 'server.properties')):
        raise RuntimeError('Server template is not pristine')
    shutil.copytree(source, server_game)
    matrix.install_mods(server_game, ARTIFACT, None)
    port = server.allocate_port()
    server.write_server_configuration(server_game, port, True)
    args = server_game / 'libraries/net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt'
    dedicated = server.CapturedProcess([JAVA, '-Xms512M', '-Xmx2G',
        '-Dadvancedrocketrycommunity.releaseTestHooks=true', '@' + str(args), 'nogui'],
        server_game, EVIDENCE / 'server-full.txt')
    dedicated.wait_for(server.READY_MARKER, 240)
    game.mkdir()
    matrix.install_mods(game, ARTIFACT, None)
    (game / 'options.txt').write_text(matrix.CLIENT_OPTIONS + 'pauseOnLostFocus:false\ntutorialStep:none\n', encoding='utf-8')
    command = runtime.client_command(RUNTIME, game, '47.4.10', USER, port, JAVA)
    matrix.argument_file(game / 'client.args', command[1:])
    client = server.CapturedProcess([JAVA, '@client.args'], game, EVIDENCE / 'client-full.txt')
    client.wait_for(matrix.marker('Connected to a modded server.'), 240)
    dedicated.wait_for(matrix.marker(USER + ' joined the game'), 60)
    window = control.Window(client.process.pid)
    owner = str(uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:' + USER).encode()).digest(), version=3))
    dedicated.command('op ' + USER)
    dedicated.command('gamemode creative ' + USER)
    dedicated.command('gamerule doMobSpawning false')
    dedicated.command('gamerule doDaylightCycle false')
    dedicated.command('time set day')
    dedicated.command('weather clear')
    dedicated.command('forceload add 380 380 389 389')
    time.sleep(2)
    dedicated.command('fill 380 99 380 389 104 389 minecraft:air')
    dedicated.command('fill 380 99 380 389 99 389 minecraft:stone')
    for position, block in ((flight.ASSEMBLER, 'rocket_assembler'), (flight.FUEL_TANK, 'rocket_fuel_tank'),
                            (flight.MOTOR, 'rocket_motor'), (flight.SEAT, 'rocket_seat'),
                            (flight.GUIDANCE, 'guidance_computer')):
        dedicated.command(f'setblock {flight._position(position)} advancedrocketrycommunity:{block}')
    dedicated.command(f'setblock {flight._position(flight.CHEST)} minecraft:chest')
    dedicated.command(f'data merge block {flight._position(flight.CHEST)} {{Items:[{{Slot:0b,id:"minecraft:diamond",Count:17b}}]}}')
    dedicated.command('tp ' + USER + ' 384.5 100 380.5 facing 383.5 102 384.5')
    start = len(dedicated.lines)
    dedicated.command(f'execute as {USER} at @s run arce rocket assemble {flight._position(flight.ASSEMBLER)}')
    index = dedicated.wait_for(flight.ASSEMBLY_LOG, 30, start_at=start)
    entity = flight.ASSEMBLY_LOG.search(dedicated.lines[index]).group(3)
    dedicated.wait_for(re.compile('ARCE_ROCKET_ENTITY_ACTIVE entity=' + re.escape(entity)), 20, start_at=index)
    dedicated.command('arce rocket release-test refuel ' + entity)
    dedicated.wait_for(flight.REFUEL_LOG, 20, start_at=index)
    stations = []
    for name in ('ConsoleAlpha', 'ConsoleBeta'):
        start = len(dedicated.lines)
        dedicated.command(f'arce station admin create {owner} earth {name}')
        index = dedicated.wait_for(station.STATION_TRANSACTION_LOG, 45, start_at=start)
        stations.append(station.STATION_TRANSACTION_LOG.search(dedicated.lines[index]).group(1))
    summary.update(entity=entity, stations=stations, owner=owner, username=USER, port=port,
                   client_pid=client.process.pid, server_pid=dedicated.process.pid, window=window.handle)
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
            result = {}
            if action == 'stop':
                summary['status'] = 'COMPLETE_WITH_OBSERVATIONS'
                break
            if action == 'click':
                window.click(int(request['x']), int(request['y']), request.get('right', False))
            elif action == 'key':
                window.key(int(request['key']))
            elif action == 'focus':
                result['focused'] = window.focus()
            elif action == 'resize':
                window.resize(int(request['width']), int(request['height']))
            elif action == 'screenshot':
                name = request['name']
                if not re.fullmatch(r'[a-z0-9-]{1,80}', name):
                    raise ValueError('Invalid screenshot name')
                result = window.screenshot(game, EVIDENCE / (name + '.png'))
                summary['screenshots'].append(result)
            elif action == 'server':
                start = len(dedicated.lines)
                dedicated.command(request['command'])
                if request.get('wait'):
                    index = dedicated.wait_for(re.compile(request['wait']), 30, start_at=start)
                    result['receipt'] = dedicated.lines[index].strip()
            elif action == 'status':
                result = dict(size=window.size(), server=dedicated.process.poll(), client=client.process.poll(),
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
    if client is not None:
        if client.process.poll() is None:
            try:
                matrix.close_owned_window(client)
                client.finish()
            finally:
                client.abort()
        summary['processes'].append(dict(kind='client', exit_code=client.process.returncode,
                                        native_logs=matrix.archive_native_logs(game, EVIDENCE, 'client')))
    if dedicated is not None:
        if dedicated.process.poll() is None:
            try:
                matrix.stop_server(dedicated)
            finally:
                dedicated.abort()
        summary['processes'].append(dict(kind='server', exit_code=dedicated.process.returncode,
                                        native_logs=matrix.archive_native_logs(server_game, EVIDENCE, 'server')))
    summary['completed_at'] = matrix.now()
    save()
    runtime.write_json(EVIDENCE / 'summary.json', summary)
