#!/usr/bin/env python3
"""Exercise typed controls only on the owner's disposable, data-free app fixture."""
import json,os,sys,time,uuid,urllib.error
from pathlib import Path
from admin import request

ROOT=Path(__file__).resolve().parents[1]
DEVICE=os.environ.get('SCANPLUS_TEST_DEVICE_ID','')
EXPECTED_SERIAL=os.environ.get('SCANPLUS_TEST_DEVICE_SERIAL','')
PACKAGE='dev.scanplus.appcontroltest'
REPORT=ROOT/'output/scanplus-app-control-device-test.json'
BASE='/api/admin/devices/'+DEVICE
results=[]

def save():REPORT.write_text(json.dumps({'deviceId':DEVICE,'package':PACKAGE,'checks':results},indent=2)+'\n')
def inventory():return request(BASE+'/apps')
def observed():return next((app for app in inventory()['apps']if app['packageName']==PACKAGE),None)
def command(action,expect='SUCCEEDED',**fields):
    payload={'requestId':str(uuid.uuid4()),'action':action,'ttlSeconds':180}
    if action!='REFRESH':payload['packageName']=PACKAGE
    payload.update(fields)
    queued=request(BASE+'/app-commands',payload)
    for attempt in range(90):
        row=next(j for j in request(BASE+'/app-commands')if j['ID']==queued['ID'])
        if row['STATE']not in ('PENDING','RUNNING'):break
        time.sleep(2)
    else:raise RuntimeError('Existing job has not finished: '+queued['ID'])
    results.append({'action':action,'id':queued['ID'],'state':row['STATE'],'detail':row['DETAIL'],'inventoryApp':observed()})
    save();print(json.dumps({'action':action,'state':row['STATE'],'detail':row['DETAIL']}),flush=True)
    assert row['STATE']==expect,row
    return row

def main():
    if not DEVICE or not EXPECTED_SERIAL:
        raise SystemExit('Set SCANPLUS_TEST_DEVICE_ID and SCANPLUS_TEST_DEVICE_SERIAL explicitly for the disposable test terminal.')
    uuid.UUID(DEVICE)
    REPORT.parent.mkdir(parents=True,exist_ok=True)
    assert any(d['ID']==DEVICE and d['SERIAL']==EXPECTED_SERIAL and d['VERSION'].startswith('3.1.') for d in request('/api/admin/devices'))
    command('REFRESH');assert observed()is not None,'Install the disposable fixture through the administrator setup first.'
    assert inventory()['canForceStop']
    command('LAUNCH');assert not observed()['stopped']
    sys.path.insert(0,str(ROOT));from pax_shell import run
    activity=run('dumpsys activity activities',timeout=20)
    resumed=[line.strip()for line in activity.splitlines()if 'mResumedActivity' in line or 'mFocusedActivity'in line]
    assert any(PACKAGE in line for line in resumed),resumed
    results.append({'action':'FOREGROUND_VERIFICATION','matchedFixture':True});save()
    command('FORCE_STOP');assert observed()['stopped']
    command('SUSPEND');assert observed()['suspended']
    command('RESUME');assert not observed()['suspended']
    command('HIDE');assert observed()['hidden']
    command('SHOW');assert not observed()['hidden']
    command('BLOCK_UNINSTALL');assert observed()['uninstallBlocked']
    command('UNINSTALL',expect='FAILED');assert observed()is not None
    command('ALLOW_UNINSTALL');assert not observed()['uninstallBlocked']
    for action,expected in [('GRANT_PERMISSION',1),('DENY_PERMISSION',2),('RESET_PERMISSION',0)]:
        command(action,permission='android.permission.CAMERA');permission=next(p for p in observed()['permissions']if p['name']=='android.permission.CAMERA');assert permission['policy']==expected
        if expected==1:assert permission['granted'] and not permission.get('operationBlocked',False)
        if expected==2:assert not permission['granted'] or permission.get('operationBlocked',False)
    print('All reversible app controls passed; fixture remains installed for protected-mode and removal checks.',flush=True)

if __name__=='__main__':main()
