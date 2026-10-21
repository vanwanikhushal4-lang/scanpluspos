import { test } from 'node:test';
import assert from 'node:assert/strict';
import { routeKind, validateCommand } from '../gateway-policy.mjs';
import { maskedSim, simAlertCount, simEventName, simReportFresh, simSlotName } from '../src/cms/sim-model.mjs';

const id='11111111-1111-4111-8111-111111111111',event='22222222-2222-4222-8222-222222222222';
test('SIM alerts use only authenticated read and idempotent acknowledgment routes',()=>{
  const base=`/api/admin/devices/${id}/sim`,ack=`${base}/alerts/${event}/ack`;
  assert.equal(routeKind('GET',base),'read');assert.equal(routeKind('POST',ack),'command');assert.equal(validateCommand(ack,{}),'{}');
  for(const body of [null,[],{enabled:true},{command:'shell'},{iccid:'89000000000000001234'}])assert.throws(()=>validateCommand(ack,body));
  assert.throws(()=>routeKind('POST',`${base}/reset`));assert.throws(()=>routeKind('GET','/api/device/sim/report'));
});
test('revoked or unreported devices cannot create fictitious SIM alert badges',()=>{
  assert.equal(simAlertCount([{sim_alert_count:2},{sim_alert_count:1},{sim_alert_count:99,revoked:true},{},{sim_alert_count:'8'},{sim_alert_count:-1}]),3);
  assert.equal(simAlertCount([]),0);
});
test('SIM identities remain masked and monitoring cannot appear fresh from an absent or stale report',()=>{
  assert.equal(maskedSim('1234'),'•••• 1234');assert.equal(maskedSim('89000000000000001234'),'Unavailable');
  const now=Date.now();assert.equal(simReportFresh(new Date(now-10000).toISOString(),now),true);
  assert.equal(simReportFresh(null,now),false);assert.equal(simReportFresh(new Date(now-100000).toISOString(),now),false);assert.equal(simReportFresh(new Date(now+10000).toISOString(),now),false);
  assert.equal(simSlotName(0),'SIM 1');assert.equal(simSlotName(-1),'All slots');assert.equal(simEventName('SIM_REPLACED'),'SIM replaced');
});
