"""Board two actual native clients through their flight menus."""
from pathlib import Path
import importlib.util
import json
import re
import time

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
def wait(pattern,start,seconds=10):
    deadline=time.monotonic()+seconds
    while time.monotonic()<deadline:
        match=re.search(pattern,text()[start:])
        if match: return match.group(0)
        time.sleep(.05)
    raise RuntimeError('No expected server receipt: '+pattern)
def shot(window,game,name):
    result=window.screenshot(WORK/game,OUT/(name+'.png'))
    records.append(result)
def save(): (OUT/'boarding-records.json').write_text(json.dumps(records,indent=2)+'\n')

try:
    for window,game,name in ((owner,'client','owner'),(passenger,'client-2','passenger')):
        window.resize(1280,720); window.click(640,360,True); time.sleep(.8)
        shot(window,game,'01-'+name+'-open')
    for window,game,name,actor in ((owner,'client','owner',summary['owner']),
            (passenger,'client-2','passenger','6482f9f6-ff26-3046-b7ba-86db04b972f8')):
        start=len(text()); window.click(535,455)
        receipt=wait('ARCE_FLIGHT_INTENT[^\n]*player='+actor+'[^\n]*action=BOARD[^\n]*code=SUCCESS[^\n]*',start)
        records.append(dict(action=name+' BOARD pointer',receipt=receipt))
        time.sleep(.4); shot(window,game,'02-'+name+'-boarded')
    time.sleep(.5); shot(owner,'client','03-owner-two-manifested'); shot(passenger,'client-2','03-passenger-two-manifested')
finally: save()
print(json.dumps(records))
