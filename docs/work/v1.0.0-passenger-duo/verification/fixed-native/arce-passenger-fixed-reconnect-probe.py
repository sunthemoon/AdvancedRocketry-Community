"""Reconnect the same native passenger profile using Direct Connection."""
from pathlib import Path
import importlib.util
import json
import re
import time
import struct
import uuid

TEMP=Path(r'C:\Users\Administrator\AppData\Local\Temp')
ROOT=Path(r'D:\GitHub\AdvancedRocketry-Community')
WORK=TEMP/'arce-passenger-fixed-ui'
OUT=ROOT/'docs/work/v1.0.0-passenger-duo/verification/fixed-native'
spec=importlib.util.spec_from_file_location('control',TEMP/'arce-console-ui-control.py')
control=importlib.util.module_from_spec(spec); spec.loader.exec_module(control)
summary=json.loads(sorted(OUT.glob('checkpoint-*.json'))[-1].read_text())
window=control.Window(summary['second_client_pid'])
address='127.0.0.1:'+str(summary['port'])
records=[dict(pid=window.pid,address=address,action='WM_CHAR address then native Join',process_restarted=False)]
def text(): return (OUT/'server-full.txt').read_text(encoding='utf-8')
def wait(pattern,start,seconds=40):
    deadline=time.monotonic()+seconds
    while time.monotonic()<deadline:
        match=re.search(pattern,text()[start:])
        if match: return match.group(0)
        time.sleep(.05)
    raise RuntimeError('No reconnect receipt: '+pattern)
def request(number,command,pattern):
    value=dict(id=number,op='server',command=command,wait=pattern)
    with (WORK/'requests'/f'{number:03}.json').open('x') as stream: json.dump(value,stream)
    records.append(value)
try:
    start=len(text())
    for char in address:
        window.u.PostMessageW(window.handle,0x0102,ord(char),0); time.sleep(.04)
    records.append(window.screenshot(WORK/'client-2',OUT/'08-passenger-address.png'))
    window.click(640,415)
    records.append(dict(receipt=wait('V100Visual2 joined the game',start)))
    flights=json.loads((OUT/'flight-records.json').read_text())
    entity=next(row['entity'] for row in flights if row.get('action')=='landed while passenger offline')
    bits=','.join(str(n) for n in struct.unpack('>iiii',uuid.UUID(entity).bytes))
    for number,name in ((50,'V100Visual1'),(51,'V100Visual2')):
        marker=name+'_MOUNTED_RECONNECTED_AUTHORITY'
        request(number,f'execute as {name} on vehicle if entity @s[nbt={{UUID:[I;{bits}]}}] run say {marker}',marker)
    request(52,'data get entity V100Visual2 Dimension','has the following entity data:')
    request(53,'data get entity V100Visual2 Pos','has the following entity data:')
    request(54,'execute in advancedrocketrycommunity:moon run arce rocket release-test report '+entity,'ARCE_RELEASE_TEST_FLIGHT_REPORT entity=')
    request(55,'say PASSENGER_RECONNECT_REPORTED','PASSENGER_RECONNECT_REPORTED')
    records.append(dict(receipt=wait('PASSENGER_RECONNECT_REPORTED',start)))
    time.sleep(1)
    records.append(window.screenshot(WORK/'client-2',OUT/'09-passenger-reconnected.png'))
    window.key(114); time.sleep(.5)
    records.append(window.screenshot(WORK/'client-2',OUT/'09b-passenger-coordinates.png'))
    owner=control.Window(summary['client_pid'])
    records.append(owner.screenshot(WORK/'client',OUT/'09c-owner-coordinates.png'))
finally:
    (OUT/'reconnect-records.json').write_text(json.dumps(records,indent=2)+'\n')
print(json.dumps(records))
