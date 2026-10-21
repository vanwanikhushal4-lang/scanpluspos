import { test } from 'node:test';
import assert from 'node:assert/strict';
import { fleetSummary, applicationSummary, matchesAppFilter, auditDate, csvText } from '../src/cms/summary.mjs';
const now = Date.parse('2026-10-17T12:00:00Z');
test('new and stale terminals cannot silently become online or confirmed maintenance', () => {
  const data = [
    { enabled: null, owner_active: null, usb_sealed: null, last_seen: null },
    { enabled: false, usb_sealed: false, last_seen: '2026-10-16T12:00:00Z', detail: 'Protection requested; inspect helper and policy status' },
    { enabled: true, usb_sealed: true, owner_active: true, bridge_available: true, last_seen: '2026-10-17T11:59:55Z' },
    { enabled: true, revoked: true, last_seen: '2026-10-17T11:59:55Z' },
  ];
  const result = fleetSummary(data, now);
  assert.equal(result.total, 3); assert.equal(result.online, 1); assert.equal(result.offline, 2);
  assert.equal(result.maintenance, 0); assert.equal(result.attention, 1); assert.equal(result.unknown, 1);
  assert.equal(result.protected, 1); assert.equal(result.sealed, 1); assert.equal(result.unsealed, 1);
  assert.equal(result.managed, 1);
});
test('application chart categories are disjoint even for restricted system and management apps', () => {
  const summary = applicationSummary({ apps: [
    { systemApp: true, critical: true, hidden: true },
    { systemApp: false, critical: true, suspended: true },
    { systemApp: false, critical: false, suspended: true },
    { systemApp: false, critical: false, hidden: false },
  ] });
  assert.deepEqual(summary, { total: 4, system: 1, management: 1, restricted: 1, user: 1, permissions: 0 });
  assert.equal(summary.total, summary.system + summary.management + summary.restricted + summary.user);
});
test('CSV export escapes quotes and neutralizes spreadsheet formulas without losing multiline details', () => {
  const csv = csvText(['Details', 'Count'], [['=HYPERLINK("https://example.test")', 2], ['line one\nline two', '-3'], ['  +CMD', '@value']]);
  assert.match(csv, /"'=HYPERLINK\(""https:\/\/example.test""\)"/);
  assert.match(csv, /"line one\nline two","'-3"/);
  assert.match(csv, /"'  \+CMD","'@value"/);
});

test('dashboard category links select exactly the matching application list', () => {
  const inventory = { apps: [{ systemApp: true, hidden: true }, { critical: true }, { suspended: true }, {}] };
  const summary = applicationSummary(inventory);
  for (const group of ['system', 'management', 'restricted', 'user']) {
    assert.equal(inventory.apps.filter(app => matchesAppFilter(app, group)).length, summary[group]);
  }
  assert.equal(inventory.apps.filter(app => matchesAppFilter(app, 'all')).length, 4);
});

test('audit date filters use the same IST calendar day as displayed timestamps', () => {
  assert.equal(auditDate('2026-10-17T18:29:59Z'), '2026-10-17');
  assert.equal(auditDate('2026-10-17T18:30:00Z'), '2026-10-18');
});
