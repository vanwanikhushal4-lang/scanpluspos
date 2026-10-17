import { test } from 'node:test';
import assert from 'node:assert/strict';
import { online, protectionLabel } from '../src/model.mjs';

test('missing or stale device reports cannot appear online', () => {
  const now = Date.parse('2026-10-16T10:00:00Z');
  for (const last_seen of [null, 'invalid', '2026-10-16T09:59:14Z', '2026-10-16T10:02:00Z']) assert.equal(online({ last_seen }, now), false);
  assert.equal(online({ last_seen: '2026-10-16T09:59:50Z', revoked: true }, now), false);
  assert.equal(online({ last_seen: '2026-10-16T09:59:50Z' }, now), true);
});
test('an unknown report is not shown as confirmed maintenance', () => {
  assert.equal(protectionLabel({ enabled: null }), 'Awaiting status');
  assert.equal(protectionLabel({ enabled: false, detail: 'Protection requested; inspect helper and policy status' }), 'Needs attention');
  assert.equal(protectionLabel({ enabled: true, revoked: true }), 'Revoked');
});
