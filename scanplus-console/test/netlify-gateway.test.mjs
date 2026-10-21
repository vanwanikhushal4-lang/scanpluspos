import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createGateway } from '../netlify/edge-functions/console-api.js';
import { apkLimit } from '../gateway-policy.mjs';

const origin = 'https://scanplus-test.netlify.app';
const device = '11111111-1111-4111-8111-111111111111';
const requestId = 'f3b027ba-a8af-40e4-8212-f121abc65432';
const secret = 'server-only-admin-token-for-testing-12345678';
const env = { SCANPLUS_OWNER_ID: 'owner', SCANPLUS_CONSOLE_ORIGIN: origin, SCANPLUS_BACKEND_URL: 'https://backend.example', SCANPLUS_ADMIN_TOKEN: secret };
const context = { user: { id: 'owner', expiresAt: new Date(Date.now() + 60000) } };
const req = (path, { method = 'GET', headers = {}, body } = {}) => new Request(origin + path, { method, headers: { 'X-Scanplus-Console': '1', ...headers }, ...(body ? { body, duplex: 'half' } : {}) });
const gateway = (fetchBackend, settings = env) => createGateway({ getEnv: name => settings[name], fetchBackend });
const ok = value => new Response(JSON.stringify(value));
const post = input => ({ method: 'POST', headers: { Origin: origin, 'Content-Type': 'application/json' }, body: JSON.stringify(input) });

test('hosted management fails closed for missing config, anonymous, wrong and expired Netlify users', async () => {
  let calls = 0; const proxy = gateway(async () => { calls++; return ok([]); });
  for (const denied of [{}, { user: { id: 'other', expiresAt: context.user.expiresAt } }, { user: { id: 'owner', expiresAt: new Date(0) } }, { user: { id: 'owner' } }]) {
    const response = await proxy(req('/api/admin/devices', { headers: { Authorization: `Bearer ${secret}`, 'X-Netlify-User': 'owner', Cookie: 'user=owner' } }), denied);
    assert.equal(response.status, 401);
  }
  assert.equal((await gateway(async () => { calls++; }, {})(req('/api/admin/devices'), context)).status, 503);
  assert.equal(calls, 0);
});
test('only the production origin, same-origin requests and the typed route allowlist are accepted', async () => {
  let calls = 0; const proxy = gateway(async () => { calls++; return ok([]); });
  assert.equal((await proxy(new Request('https://preview.netlify.app/api/admin/devices'), context)).status, 403);
  assert.equal((await proxy(new Request(origin + '/api/admin/devices'), context)).status, 403);
  assert.equal((await proxy(req('/api/admin/devices', { headers: { Origin: 'https://foreign.example' } }), context)).status, 403);
  assert.equal((await proxy(req('/api/admin/devices', { headers: { 'Sec-Fetch-Site': 'cross-site' } }), context)).status, 403);
  assert.equal((await proxy(req('/api/admin/devices?url=https://foreign.example'), context)).status, 400);
  assert.equal((await proxy(req('/api/admin/enrollments', post({})), context)).status, 404);
  assert.equal((await proxy(req(`/api/admin/devices/${device}/protection`, { ...post({ requestId, enabled: true, ttlSeconds: 120 }), headers: { 'Content-Type': 'application/json' } }), context)).status, 403);
  assert.equal(calls, 0);
});
test('the gateway supplies its own backend authorization and never forwards browser cookies or headers', async () => {
  const proxy = gateway(async (url, options) => {
    assert.equal(url.href, 'https://backend.example/api/admin/devices');
    assert.equal(options.headers.get('Authorization'), `Bearer ${secret}`);
    assert.equal(options.headers.get('Cookie'), null);
    assert.equal(options.headers.get('X-Netlify-User'), null);
    assert.equal(options.redirect, 'error');
    return ok([{ ID: device }]);
  });
  const response = await proxy(req('/api/admin/devices', { headers: { Cookie: 'private-netlify-login=secret', Authorization: 'Bearer foreign', 'X-Netlify-User': 'not-trusted' } }), context);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), [{ ID: device }]);
  assert.equal(response.headers.get('Cache-Control'), 'no-store');
  assert.equal(response.headers.get('Access-Control-Allow-Origin'), null);
  const info = await (await proxy(req('/api/console/info'), context)).json();
  assert.deepEqual(Object.keys(info).sort(), ['backend', 'mode', 'pollSeconds']);
  assert.equal(JSON.stringify(info).includes(secret), false);
});
test('idempotent typed writes work; shell commands, extra fields and oversized JSON never reach the backend', async () => {
  const sent = [];
  const proxy = gateway(async (url, options) => { sent.push({ url, options }); return ok({ STATE: 'PENDING' }); });
  const route = `/api/admin/devices/${device}/app-commands`;
  const good = { requestId, action: 'REFRESH', ttlSeconds: 180 };
  assert.equal((await proxy(req(route, post(good)), context)).status, 200);
  assert.deepEqual(JSON.parse(sent[0].options.body), good);
  for (const bad of [{ ...good, action: 'SHELL' }, { ...good, command: 'id' }, { ...good, packageName: 'x;id' }, { ...good, ttlSeconds: 1000 }]) assert.equal((await proxy(req(route, post(bad)), context)).status, 400);
  assert.equal((await proxy(req(route, post({ ...good, extra: 'x'.repeat(17000) })), context)).status, 413);
  assert.equal(sent.length, 1);
});
test('APK uploads stream above serverless binary limits, validate the archive and enforce 64 MiB', async () => {
  let calls = 0, bytes = 0;
  const proxy = gateway(async (_url, options) => {
    calls++;
    assert.equal(options.headers.get('X-APK-Name'), 'example.apk');
    assert.ok(options.body instanceof ReadableStream);
    for await (const chunk of options.body) bytes += chunk.byteLength;
    return ok({ ID: 'artifact' });
  });
  const upload = body => req('/api/admin/apks', { method: 'POST', headers: { Origin: origin, 'Content-Type': 'application/vnd.android.package-archive', 'X-APK-Name': 'example.apk' }, body });
  let remaining = 7 * 1024 * 1024;
  const body = new ReadableStream({ pull(controller) { if (remaining === 7 * 1024 * 1024) { controller.enqueue(new Uint8Array([0x50])); remaining--; } else if (remaining === 7 * 1024 * 1024 - 1) { controller.enqueue(new Uint8Array([0x4b])); remaining--; } else if (remaining) { const n = Math.min(65536, remaining); controller.enqueue(new Uint8Array(n)); remaining -= n; } else controller.close(); } });
  assert.equal((await proxy(upload(body), context)).status, 200);
  assert.equal(bytes, 7 * 1024 * 1024);
  assert.equal((await proxy(upload('not-an-archive'), context)).status, 400);
  const oversized = upload('PK'); oversized.headers.set('Content-Length', String(apkLimit + 1));
  assert.equal((await proxy(oversized, context)).status, 413);
  assert.equal(calls, 1);
});
test('upstream auth failures, redirects and failures cannot expose credentials or backend bodies', async () => {
  for (const status of [401, 403, 500, 302]) {
    const response = await gateway(async () => new Response(secret, { status }))(req('/api/admin/devices'), context);
    assert.equal(response.status, 502);
    assert.equal((await response.text()).includes(secret), false);
  }
  const response = await gateway(async () => { throw new Error(secret); })(req('/api/admin/devices'), context);
  assert.equal(response.status, 502);
  assert.equal((await response.text()).includes(secret), false);
});
test('Wi-Fi management uses the same authenticated gateway and rejects arbitrary Wi-Fi configuration', async () => {
  const sent = [];
  const proxy = gateway(async (url, options) => { sent.push({ url, options }); return ok({ STATE: 'PENDING' }); });
  const route = `/api/admin/devices/${device}/wifi/policy`;
  assert.equal((await proxy(req(route, post({ requestId, blocked: true, ttlSeconds: 120 })), {})).status, 401);
  assert.equal((await proxy(req(route, post({ requestId, blocked: true, ttlSeconds: 120 })), context)).status, 200);
  assert.equal(sent[0].options.headers.get('Authorization'), `Bearer ${secret}`);
  assert.equal((await proxy(req(route, post({ requestId, blocked: true, ttlSeconds: 120, password: 'unexpected' })), context)).status, 400);
  assert.equal(sent.length, 1);
});
