import { test } from 'node:test';
import assert from 'node:assert/strict';
import { routeKind, validateCommand } from '../gateway-policy.mjs';
import { canEnableOpenWifiBlock, canEnableOpenWifiGuard, wifiActionLabel, wifiAge } from '../src/cms/wifi-model.mjs';

const device = '11111111-1111-4111-8111-111111111111';
const requestId = 'f3b027ba-a8af-40e4-8212-f121abc65432';
const base = `/api/admin/devices/${device}/wifi`;
test('Wi-Fi gateway accepts only typed scan and policy requests', () => {
  assert.equal(routeKind('GET', base), 'read');
  for (const action of ['scan', 'policy', 'guard']) assert.equal(routeKind('POST', `${base}/${action}`), 'command');
  const request = { requestId, blocked: true, ttlSeconds: 120 };
  assert.deepEqual(JSON.parse(validateCommand(`${base}/policy`, request)), request);
  assert.deepEqual(JSON.parse(validateCommand(`${base}/guard`, request)), request);
  assert.doesNotThrow(() => validateCommand(`${base}/scan`, { requestId, ttlSeconds: 120 }));
  for (const bad of [{ ...request, blocked: 'true' }, { ...request, ssid: 'other' }, { ...request, command: 'shell' }, { ...request, ttlSeconds: 600 }]) for(const action of ['policy','guard']) assert.throws(() => validateCommand(`${base}/${action}`, bad));
  assert.throws(() => validateCommand(`${base}/scan`, request));
  assert.throws(() => routeKind('POST', `${base}/connect`));
  assert.throws(() => routeKind('GET', '/api/device/wifi/report'));
});
test('the toggle cannot activate with an open, unknown, stale or unenrolled connection', () => {
  const now = Date.now(), enrolled = { owner_active: true, revoked: false };
  const report = security => ({ reportedAt: new Date(now - 20000).toISOString(), snapshot: { connection: { security } } });
  assert.equal(canEnableOpenWifiBlock(enrolled, report('WPA2'), now), true);
  for (const security of ['OPEN', 'UNKNOWN', 'ENHANCED_OPEN']) assert.equal(canEnableOpenWifiBlock(enrolled, report(security), now), false);
  assert.equal(canEnableOpenWifiBlock(enrolled, { ...report('WPA2'), reportedAt: new Date(now - 100000).toISOString() }, now), false);
  assert.equal(canEnableOpenWifiBlock({ ...enrolled, revoked: true }, report('WPA2'), now), false);
  assert.equal(canEnableOpenWifiBlock({ owner_active: false }, report('WPA2'), now), false);
  assert.equal(canEnableOpenWifiBlock(enrolled, null, now), false);
});
test('cached scan ages keep growing after the report instead of appearing fresh', () => {
  const time = '2026-10-18T12:00:00Z';
  assert.equal(wifiAge(100, time, Date.parse(time) + 60000), '2m ago');
  assert.equal(wifiAge(null, time), 'Unknown');
});
test('the independent guard requires an agent capability report and a secured connection', () => {
  const now=Date.now(), enrolled={owner_active:true,revoked:false};
  const report={reportedAt:new Date(now).toISOString(),snapshot:{connection:{security:'WPA2'},policy:{blocked:false},openGuard:{enabled:false,applied:true,enforcement:'OFF'}}};
  assert.equal(canEnableOpenWifiGuard(enrolled,report,now),true);
  assert.equal(canEnableOpenWifiGuard(enrolled,{...report,snapshot:{...report.snapshot,policy:{blocked:true}}},now),true);
  assert.equal(canEnableOpenWifiGuard(enrolled,{...report,snapshot:{...report.snapshot,openGuard:null}},now),false);
  assert.equal(canEnableOpenWifiGuard(enrolled,{...report,snapshot:{...report.snapshot,connection:{security:'OPEN'}}},now),false);
  assert.equal(canEnableOpenWifiGuard(enrolled,{...report,reportedAt:new Date(now-100000).toISOString()},now),false);
  assert.equal(wifiActionLabel({action:'SET_OPEN_POLICY',blocked:false}),'Disable full Wi-Fi protection');
  assert.equal(wifiActionLabel({action:'SET_OPEN_GUARD',blocked:true}),'Enable open-network guard');
});
