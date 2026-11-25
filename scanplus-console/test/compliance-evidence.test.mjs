import test from 'node:test';
import assert from 'node:assert/strict';
import { complianceEvidence } from '../src/cms/compliance-evidence.mjs';

const now = Date.parse('2026-11-17T08:00:00Z');
const recent = '2026-11-17T07:59:50Z';
const stale = '2026-11-13T11:06:22Z';
const device = { last_seen: recent, enabled: true, bridge_available: true, usb_sealed: true };
const wifi = {
  reportedAt: recent,
  snapshot: {
    policy: { localSettingsLocked: false },
    openGuard: { enabled: true, applied: true },
    connection: { security: 'WPA2' },
  },
};

test('current agent reports can confirm observed controls', () => {
  const evidence = complianceEvidence({ device, wifi, now, ready: true });
  assert.deepEqual(evidence.protection, { label: 'Full protection on', tone: 'green' });
  assert.equal(evidence.usb.label, 'USB data sealed');
  assert.equal(evidence.wifiLock.label, 'Settings unlocked');
  assert.equal(evidence.wifiGuard.label, 'Guard on');
  assert.equal(evidence.wifiSecurity.label, 'WPA2');
});

test('stale agent and Wi-Fi status only show historical evidence', () => {
  const lastDevice = { ...device, last_seen: stale, enabled: false, detail: 'Maintenance; protection policies are not requested', bridge_available: false, usb_sealed: false };
  const evidence = complianceEvidence({ device: lastDevice, wifi: { ...wifi, reportedAt: stale }, now, ready: true });
  assert.deepEqual(evidence.protection, { label: 'Last report · Maintenance', tone: 'muted' });
  assert.equal(evidence.usb.label, 'Last report · USB state unavailable');
  assert.equal(evidence.wifiGuard.label, 'Last report · Guard on');
});

test('missing, revoked, and failed reports cannot appear current', () => {
  const revoked = complianceEvidence({ device: { ...device, revoked: true }, wifi, now, ready: true });
  assert.equal(revoked.protection.label, 'Not reported');
  assert.equal(revoked.wifiGuard.label, 'Not reported');
  const failed = complianceEvidence({ device, now, ready: false, wifiError: true });
  assert.equal(failed.usb.label, 'Last report · USB data sealed');
  assert.equal(failed.wifiGuard.label, 'Wi-Fi report unavailable');
});

test('an enabled Wi-Fi guard without observed enforcement needs attention', () => {
  const unapplied = { ...wifi, snapshot: { ...wifi.snapshot, openGuard: { enabled: true, applied: false } } };
  const evidence = complianceEvidence({ device, wifi: unapplied, now, ready: true });
  assert.deepEqual(evidence.wifiGuard, { label: 'Guard needs attention', tone: 'amber' });
});
