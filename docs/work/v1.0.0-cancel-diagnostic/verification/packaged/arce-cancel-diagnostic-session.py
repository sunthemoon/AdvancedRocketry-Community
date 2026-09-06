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
WORK = Path(r'C:\Users\Administrator\AppData\Local\Temp\arce-cancel-diagnostic')
EVIDENCE = ROOT / 'docs/work/v1.0.0-cancel-diagnostic/verification/packaged'
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
server_label = 'server'

def archive(process, directory, label):
    process.abort()
    log = EVIDENCE / (label + '-full.txt')
    summary['processes'].append(dict(kind=label, exit_code=process.process.returncode,
        full_log_file=log.name, full_log_sha256=server.digest_file(log),
        native_logs=matrix.archive_native_logs(directory, EVIDENCE, label)))
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
    dedicated.command('tp ' + USER + ' 384.5 100 381.5 facing 384.5 102 384.5')
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

    # The existing source floor is a precondition, not an interaction outcome.
    start = len(dedicated.lines)
    dedicated.command('execute if block 384 99 381 minecraft:stone run say GUI_SUPPORT_PRESENT')
    dedicated.wait_for(re.compile('GUI_SUPPORT_PRESENT'), 3, start_at=start)
    window.focus()
    window.resize(1280, 720)
    time.sleep(0.4)
    summary['screenshots'].append(window.screenshot(game, EVIDENCE / '01-world.png'))
    window.click(640, 360, right=True)
    time.sleep(0.4)
    summary['screenshots'].append(window.screenshot(game, EVIDENCE / '02-console.png'))
    start = len(dedicated.lines)
    window.click(640, 520)
    launch_index = dedicated.wait_for(re.compile(r'ARCE_FLIGHT_INTENT .*action=LAUNCH .*code=SUCCESS'), 3, start_at=start)
    summary['launch_receipt'] = dedicated.lines[launch_index].strip()
    time.sleep(0.2)
    summary['screenshots'].append(window.screenshot(game, EVIDENCE / '03-countdown.png'))
    window.key(9)
    time.sleep(0.15)
    window.key(13)
    cancel_index = dedicated.wait_for(re.compile(r'ARCE_FLIGHT_INTENT .*action=CANCEL .*code=SUCCESS'), 3, start_at=launch_index)
    summary['cancel_receipt'] = dedicated.lines[cancel_index].strip()
    time.sleep(0.3)
    summary['screenshots'].append(window.screenshot(game, EVIDENCE / '04-cancelled.png'))
    returns = [line for line in dedicated.lines[start:] if 'ARCE_TRANSFER_RETURNED_TO_SOURCE ' in line]
    if len(returns) != 1 or '[Server thread/INFO]' not in returns[0] or 'reason=countdown_cancelled fuel=1000' not in returns[0]:
        raise AssertionError('Unexpected return diagnostic: ' + repr(returns))
    summary['return_diagnostic'] = returns[0].strip()
    start = len(dedicated.lines)
    dedicated.command('arce rocket release-test report ' + entity)
    index = dedicated.wait_for(re.compile('ARCE_RELEASE_TEST_FLIGHT_REPORT entity=' + re.escape(entity)), 10, start_at=start)
    summary['report_before_restart'] = dedicated.lines[index].strip()
    if 'state=FUELED fuel=1000 capacity=1000 passengers=0 transfer=none' not in dedicated.lines[index]:
        raise AssertionError('Unexpected post-cancel state')
    matrix.close_owned_window(client)
    if client.finish() != 0 or server.scan_log(client.lines):
        raise AssertionError('Native client exit/audit failed')
    archive(client, game, 'client')
    client = None
    matrix.stop_server(dedicated)
    archive(dedicated, server_game, 'server')
    dedicated = None
    server_label = 'restart'
    dedicated = server.CapturedProcess([JAVA, '-Xms512M', '-Xmx2G',
        '-Dadvancedrocketrycommunity.releaseTestHooks=true', '@' + str(args), 'nogui'],
        server_game, EVIDENCE / 'restart-full.txt')
    dedicated.wait_for(server.READY_MARKER, 240)
    dedicated.wait_for(re.compile('ARCE_ROCKET_ENTITY_ACTIVE entity=' + re.escape(entity)), 30)
    start = len(dedicated.lines)
    dedicated.command('arce rocket release-test report ' + entity)
    index = dedicated.wait_for(re.compile('ARCE_RELEASE_TEST_FLIGHT_REPORT entity=' + re.escape(entity)), 10, start_at=start)
    summary['report_after_restart'] = dedicated.lines[index].strip()
    if 'state=FUELED fuel=1000 capacity=1000 passengers=0 transfer=none' not in dedicated.lines[index]:
        raise AssertionError('Cancelled rocket did not survive restart unchanged')
    matrix.stop_server(dedicated)
    archive(dedicated, server_game, 'restart')
    dedicated = None
    if server.digest_file(ARTIFACT) != summary['artifact_sha256']:
        raise AssertionError('Artifact changed during native test')
    summary['status'] = 'PASS'
    print('PASS: native launch/cancel, strict audit and dedicated restart', flush=True)
except BaseException as exc:
    summary['status'] = 'FAIL'
    summary['failure'] = type(exc).__name__ + ': ' + str(exc)
    raise
finally:
    cleanup_errors = []
    if client is not None:
        try:
            if client.process.poll() is None:
                matrix.close_owned_window(client)
                client.finish()
        except Exception as exc:
            cleanup_errors.append('client: ' + repr(exc))
        finally:
            archive(client, game, 'client')
    if dedicated is not None:
        try:
            if dedicated.process.poll() is None:
                matrix.stop_server(dedicated)
        except Exception as exc:
            cleanup_errors.append('server: ' + repr(exc))
        finally:
            archive(dedicated, server_game, server_label)
    summary['cleanup_errors'] = cleanup_errors
    if cleanup_errors:
        summary['status'] = 'FAIL'
    summary['completed_at'] = matrix.now()
    save()
    runtime.write_json(EVIDENCE / 'summary.json', summary)
    if cleanup_errors:
        print('FAILED_CLEANUP_AUDIT ' + json.dumps(cleanup_errors), flush=True)
        sys.exit(1)

