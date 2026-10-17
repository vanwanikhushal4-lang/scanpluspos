import { test } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { createConsoleServer, backendReader } from '../server.mjs';

const device = '11111111-1111-4111-8111-111111111111';
const requestId = 'f3b027ba-a8af-40e4-8212-f121abc65432';
async function fixture(t, readBackend = async () => ({ status: 200, value: [] })) {
  const server = createConsoleServer({ backendUrl: 'https://192.168.0.30:9443', readBackend });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`;
  t.after(() => new Promise(resolve => { server.closeAllConnections(); server.close(resolve); }));
  const call = (path, options = {}) => fetch(origin + path, { ...options, headers: { 'X-Scanplus-Console': '1', ...options.headers } });
  return { origin, call };
}
test('cross-origin pages and DNS rebinding cannot use the management proxy', async t => {
  let forwarded = 0;
  const { origin, call } = await fixture(t, async () => { forwarded++; return { status: 200, value: [] }; });
  assert.equal((await fetch(origin + '/api/admin/devices')).status, 403);
  assert.equal((await call('/api/admin/devices', { headers: { Origin: 'https://other.example' } })).status, 403);
  assert.equal((await call('/api/admin/devices', { headers: { 'Sec-Fetch-Site': 'cross-site' } })).status, 403);
  const forgedHostStatus = await new Promise((resolve, reject) => { const req = http.get(origin + '/api/admin/devices', { headers: { Host: 'evil.example:5173', 'X-Scanplus-Console': '1' } }, res => { res.resume(); resolve(res.statusCode); }); req.on('error', reject); });
  assert.equal(forgedHostStatus, 403);
  assert.equal((await call(`/api/admin/devices/${device}/protection`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ requestId, enabled: true, ttlSeconds: 120 }) })).status, 403);
  assert.equal(forwarded, 0);
});
test('a same-origin protection request preserves its idempotency key and target', async t => {
  const forwarded = [];
  const { origin, call } = await fixture(t, async input => { forwarded.push(input); return { status: 200, value: { ID: 'command', STATE: 'PENDING' } }; });
  const input = { requestId, enabled: true, ttlSeconds: 120 };
  const response = await call(`/api/admin/devices/${device}/protection`, { method: 'POST', headers: { Origin: origin, 'Content-Type': 'application/json' }, body: JSON.stringify(input) });
  assert.equal(response.status, 200);
  assert.equal((await response.json()).STATE, 'PENDING');
  assert.equal(forwarded[0].method, 'POST');
  assert.equal(forwarded[0].path, `/api/admin/devices/${device}/protection`);
  assert.deepEqual(JSON.parse(forwarded[0].payload), input);
});
test('only the console API allowlist can be forwarded', async t => {
  let count = 0;
  const { origin, call } = await fixture(t, async () => { count++; return { status: 200, value: {} }; });
  assert.equal((await call('/api/device/check-in')).status, 404);
  assert.equal((await call('/api/admin/enrollments', { method: 'POST', headers: { Origin: origin } })).status, 404);
  assert.equal((await call(`/api/admin/devices/${device}`, { method: 'DELETE', headers: { Origin: origin } })).status, 404);
  assert.equal((await call('/api/admin/devices?target=elsewhere')).status, 400);
  assert.equal((await call('/api/admin/devices', { method: 'OPTIONS' })).status, 404);
  assert.equal(count, 0);
});
test('invalid protection payloads never reach the backend', async t => {
  let count = 0;
  const { origin, call } = await fixture(t, async () => { count++; return { status: 200, value: {} }; });
  for (const input of [{ requestId, enabled: 'true', ttlSeconds: 120 }, { requestId, enabled: true, ttlSeconds: 999 }, { requestId, enabled: true, ttlSeconds: 120, command: 'unexpected' }, { requestId: 'wrong', enabled: false, ttlSeconds: 120 }]) {
    const response = await call(`/api/admin/devices/${device}/protection`, { method: 'POST', headers: { Origin: origin, 'Content-Type': 'application/json' }, body: JSON.stringify(input) });
    assert.equal(response.status, 400);
  }
  assert.equal((await call(`/api/admin/devices/${device}/protection`, { method: 'POST', headers: { Origin: origin, 'Content-Type': 'text/plain' }, body: 'bad' })).status, 415);
  assert.equal((await call(`/api/admin/devices/${device}/protection`, { method: 'POST', headers: { Origin: origin, 'Content-Type': 'application/json' }, body: 'x'.repeat(17000) })).status, 413);
  assert.equal(count, 0);
});
test('private configuration and upstream authentication errors are not exposed', async t => {
  const { call } = await fixture(t, async () => ({ status: 401, value: { secret: 'must-not-leak' } }));
  const info = await (await call('/api/console/info')).json();
  assert.deepEqual(Object.keys(info).sort(), ['backend', 'mode', 'pollSeconds']);
  const response = await call('/api/admin/devices');
  assert.equal(response.status, 401);
  assert.equal((await response.text()).includes('must-not-leak'), false);
  assert.equal(response.headers.get('access-control-allow-origin'), null);
  assert.match(response.headers.get('content-security-policy'), /frame-ancestors 'none'/);
});
test('backend origins cannot redirect credentials to HTTP or a path', () => {
  for (const backendUrl of ['http://localhost:9443', 'https://a.example/some-path', 'https://user:secret@a.example', 'https://a.example?redirect=x']) {
    assert.throws(() => backendReader({ backendUrl, adminToken: 'a'.repeat(40), ca: '' }));
  }
});
test('app management forwards typed requests and rejects shell strings and foreign uploads', async t => {
  const forwarded = [];
  const { origin, call } = await fixture(t, async input => { forwarded.push(input); return { status: 200, value: { STATE: 'PENDING' } }; });
  const route = `/api/admin/devices/${device}/app-commands`;
  const good = { requestId, action: 'SUSPEND', packageName: 'dev.scanplus.test', ttlSeconds: 180 };
  assert.equal((await call(route, { method: 'POST', headers: { Origin: origin, 'Content-Type': 'application/json' }, body: JSON.stringify(good) })).status, 200);
  assert.deepEqual(JSON.parse(forwarded[0].payload), good);
  for (const bad of [{ ...good, action: 'SHELL' }, { ...good, packageName: 'dev.test;id' }, { ...good, command: 'id' }, { ...good, permission: 'android.permission.CAMERA' }]) {
    assert.equal((await call(route, { method: 'POST', headers: { Origin: origin, 'Content-Type': 'application/json' }, body: JSON.stringify(bad) })).status, 400);
  }
  assert.equal((await call('/api/admin/apks', { method: 'POST', headers: { Origin: 'https://foreign.example', 'Content-Type': 'application/vnd.android.package-archive' }, body: 'PKtest' })).status, 403);
  assert.equal((await call('/api/admin/apks', { method: 'POST', headers: { Origin: origin, 'Content-Type': 'application/vnd.android.package-archive' }, body: 'PKtest' })).status, 200);
  assert.equal(forwarded.at(-1).contentType, 'application/vnd.android.package-archive');
  assert.ok(Buffer.isBuffer(forwarded.at(-1).payload));
  assert.equal((await call(`/api/admin/devices/${device}/apps`)).status, 200);
});
