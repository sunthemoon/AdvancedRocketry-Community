"""Launch through the native owner menu and disconnect the passenger in ascent."""
from pathlib import Path
import importlib.util
import json
import re
import struct
import time
import uuid

TEMP=Path(r'C:\Users\Administrator\AppData\Local\Temp')
ROOT=Path(r'D:\GitHub\AdvancedRocketry-Community')
WORK=TEMP/'arce-passenger-fixed-ui'
OUT=ROOT/'docs/work/v1.0.0-passenger-duo/verification/fixed-native'
spec=importlib.util.spec_from_file_location('control',TEMP/'arce-console-ui-control.py')
control=importlib.util.module_from_spec(spec); spec.loader.exec_module(control)
summary=json.loads(sorted(OUT.glob('checkpoint-*.json'))[-1].read_text())
owner=control.Window(summary['client_pid']); passenger=control.Window(summary['second_client_pid'])
records=[]
def text(): return (OUT/'server-full.txt').read_text(encoding='utf-8')
def wait(pattern,start,seconds=15):
    deadline=time.monotonic()+seconds
    while time.monotonic()<deadline:
        match=re.search(pattern,text()[start:])
        if match: return match
        time.sleep(.04)
    raise RuntimeError('No server receipt: '+pattern)
def request(number,command,pattern=None):
    value=dict(id=number,op='server',command=command)
    if pattern: value['wait']=pattern
    path=WORK/'requests'/f'{number:03}.json'
    with path.open('x') as stream: json.dump(value,stream)
    records.append(value)
def shot(window,game,name):
    records.append(window.screenshot(WORK/game,OUT/(name+'.png')))
def mounted(player,entity,label):
    bits=','.join(str(n) for n in struct.unpack('>iiii',uuid.UUID(entity).bytes))
    return f'execute as {player} on vehicle if entity @s[nbt={{UUID:[I;{bits}]}}] run say {label}'

try:
    source=summary['entity']
    start=len(text())
    request(1,'arce rocket release-test report '+source,'ARCE_RELEASE_TEST_FLIGHT_REPORT entity=')
    request(2,mounted('V100Visual1',source,'OWNER_MOUNTED_SOURCE'),'OWNER_MOUNTED_SOURCE')
    request(3,mounted('V100Visual2',source,'PASSENGER_MOUNTED_SOURCE'),'PASSENGER_MOUNTED_SOURCE')
    request(4,f'data get entity {source} RocketEntityData.flight_data.passengers','has the following entity data:')
    request(5,'data get entity V100Visual1 Dimension','has the following entity data:')
    request(6,'data get entity V100Visual2 Dimension','has the following entity data:')
    request(7,'say PASSENGER_PREFLIGHT_COMPLETE','PASSENGER_PREFLIGHT_COMPLETE')
    wait('PASSENGER_PREFLIGHT_COMPLETE',start)
    start=len(text()); owner.click(640,520)
    launch=wait('ARCE_FLIGHT_INTENT[^\n]*action=LAUNCH[^\n]*code=SUCCESS[^\n]*',start)
    records.append(dict(action='owner launch pointer',receipt=launch.group(0)))
    shot(owner,'client','04-owner-countdown')
    ascent=wait('ARCE_TRANSFER_PHASE[^\n]*event=countdown_complete[^\n]*',start)
    records.append(dict(action='ascent observed before disconnect',receipt=ascent.group(0)))
    request(20,'kick V100Visual2 Passenger ascent reconnect probe','V100Visual2 left the game')
    wait('V100Visual2 left the game',start)
    shot(passenger,'client-2','05-passenger-disconnected'); shot(owner,'client','05-owner-ascent')
    landed=wait('ARCE_TRANSFER_PHASE[^\n]*event=landing_complete entity=([0-9a-f-]+)[^\n]*',start,35)
    destination=landed.group(1)
    records.append(dict(action='landed while passenger offline',entity=destination,receipt=landed.group(0)))
    command='execute in advancedrocketrycommunity:moon run '
    request(30,command+'arce rocket release-test report '+destination,'ARCE_RELEASE_TEST_FLIGHT_REPORT entity=')
    request(31,mounted('V100Visual1',destination,'OWNER_MOUNTED_MOON'),'OWNER_MOUNTED_MOON')
    request(32,command+f'data get entity {destination} RocketEntityData.flight_data.passengers','has the following entity data:')
    request(33,'data get entity V100Visual1 Dimension','has the following entity data:')
    request(34,'data get entity V100Visual1 Pos','has the following entity data:')
    request(35,'say PASSENGER_OFFLINE_LANDING_REPORTED','PASSENGER_OFFLINE_LANDING_REPORTED')
    wait('PASSENGER_OFFLINE_LANDING_REPORTED',start)
    time.sleep(1); shot(owner,'client','06-owner-moon-offline-passenger')
    owner.key(114); time.sleep(.5); shot(owner,'client','06b-owner-moon-coordinates')
finally:
    (OUT/'flight-records.json').write_text(json.dumps(records,indent=2)+'\n')
print(json.dumps(records))
