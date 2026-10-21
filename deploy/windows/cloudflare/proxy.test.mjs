import test from 'node:test';
import assert from 'node:assert/strict';
import proxy from './proxy.mjs';

const token = 'A'.repeat(54);
const publicOrigin = 'https://scanplus-backend-gateway.example.workers.dev';
const device = '11111111-1111-4111-8111-111111111111';
const req = (path, init = {}) => new Request(publicOrigin + path, init);
const forbidden = { SCANPLUS_BACKEND: { fetch() { throw new Error('Must not reach backend'); } } };

test('anonymous management requests never reach the backend', async () => {
  assert.equal((await proxy.fetch(req('/api/admin/devices'), forbidden)).status, 401);
});
test('Wi-Fi and SIM reports and typed management routes preserve device identity and require authentication', async () => {
  const paths = [
    ['GET', `/api/admin/devices/${device}/wifi`],
    ['POST', `/api/admin/devices/${device}/wifi/scan`],
    ['POST', `/api/admin/devices/${device}/wifi/policy`],
    ['POST', `/api/admin/devices/${device}/wifi/guard`],
    ['POST', '/api/device/wifi/report'],
    ['POST', `/api/device/wifi/commands/${device}/ack`],
    ['GET', `/api/admin/devices/${device}/sim`],
    ['POST', `/api/admin/devices/${device}/sim/alerts/${device}/ack`],
    ['POST', '/api/device/sim/report'],
  ];
  for (const [method, path] of paths) {
    assert.equal((await proxy.fetch(req(path, { method }), forbidden)).status, 401);
    let forwarded;
    const env = { SCANPLUS_BACKEND: { async fetch(request) { forwarded = request; return Response.json({ accepted: true }); } } };
    assert.equal((await proxy.fetch(req(path, { method, headers: { Authorization: `Bearer ${token}`, 'X-Device-Id': device } }), env)).status, 200);
    assert.equal(forwarded.url, `http://localhost:9443${path}`);
    assert.equal(forwarded.headers.get('X-Device-Id'), device);
  }
  assert.equal((await proxy.fetch(req(`/api/admin/devices/${device}/wifi/connect`, { method: 'POST', headers: { Authorization: `Bearer ${token}` } }), forbidden)).status, 404);
});
test('HTTP and arbitrary URLs cannot become a backend proxy', async () => {
  assert.equal((await proxy.fetch(new Request('http://example.workers.dev/api/health'), forbidden)).status, 426);
  for (const path of ['/http://169.254.169.254/', '/api/health?url=http://other/', '/api/admin/devices/%2e%2e/', '/api/shell']) {
    const status = (await proxy.fetch(req(path), forbidden)).status;
    assert.ok([400, 404].includes(status));
  }
});
test('authenticated request identity and body survive; spoofed forwarding headers do not', async () => {
  let forwarded;
  const env = { SCANPLUS_BACKEND: { async fetch(request) { forwarded = request; return Response.json({ state: 'PENDING' }); } } };
  const body = JSON.stringify({ requestId: device, enabled: true });
  const result = await proxy.fetch(req(`/api/admin/devices/${device}/protection`, {
    method: 'POST', body,
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', 'X-Forwarded-Proto': 'http', 'X-Forwarded-For': 'spoofed', 'CF-Connecting-IP': '203.0.113.12', Cookie: 'secret', 'X-Device-Id': device },
  }), env);
  assert.equal(result.status, 200);
  assert.equal(forwarded.url, `http://localhost:9443/api/admin/devices/${device}/protection`);
  assert.equal(forwarded.headers.get('Authorization'), `Bearer ${token}`);
  assert.equal(forwarded.headers.get('X-Device-Id'), device);
  assert.equal(forwarded.headers.get('X-Forwarded-Proto'), 'https');
  assert.equal(forwarded.headers.get('X-Forwarded-For'), '203.0.113.12');
  assert.equal(forwarded.headers.get('X-Forwarded-Host'), new URL(publicOrigin).host);
  assert.equal(forwarded.headers.get('Cookie'), null);
  assert.equal(forwarded.redirect, 'manual');
  assert.equal(await forwarded.text(), body);
});
test('oversized APK is rejected before reaching the backend', async () => {
  const request = req('/api/admin/apks', { method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Length': String(64 * 1024 * 1024 + 1) } });
  assert.equal((await proxy.fetch(request, forbidden)).status, 413);
});
test('backend authentication failures are preserved', async () => {
  const env = { SCANPLUS_BACKEND: { async fetch() { return new Response('Denied', { status: 401 }); } } };
  assert.equal((await proxy.fetch(req('/api/admin/devices', { headers: { Authorization: `Bearer ${token}` } }), env)).status, 401);
});
test('backend redirects cannot forward a credential to another host', async () => {
  const env = { SCANPLUS_BACKEND: { async fetch() { return new Response(null, { status: 302, headers: { Location: 'https://other.example/' } }); } } };
  assert.equal((await proxy.fetch(req('/api/health'), env)).status, 502);
});
test('unconfigured and disconnected tunnels fail closed', async () => {
  assert.equal((await proxy.fetch(req('/api/health'), {})).status, 503);
  assert.equal((await proxy.fetch(req('/api/health'), forbidden)).status, 503);
});
test('APK responses stream without cookies or cache directives from upstream', async () => {
  const bytes = new Uint8Array([0, 1, 2, 255]);
  const env = { SCANPLUS_BACKEND: { async fetch() { return new Response(bytes, { headers: { 'Content-Type': 'application/vnd.android.package-archive', 'Set-Cookie': 'secret', 'Cache-Control': 'public' } }); } } };
  const response = await proxy.fetch(req(`/api/device/apks/${device}`, { headers: { Authorization: `Bearer ${token}`, 'X-Device-Id': device } }), env);
  assert.deepEqual(new Uint8Array(await response.arrayBuffer()), bytes);
  assert.equal(response.headers.get('Set-Cookie'), null);
  assert.equal(response.headers.get('Cache-Control'), 'no-store');
});
