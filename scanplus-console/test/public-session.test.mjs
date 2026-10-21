import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { createSessionEndpoint } from '../netlify/edge-functions/session-api.js';
import { createGateway } from '../netlify/edge-functions/console-api.js';
import { issueSession, validSession, sessionSeconds } from '../netlify/lib/session.mjs';

const origin = 'https://scanplus-test.netlify.app', accessKey = 'A'.repeat(43), now = 1792240000000;
const env = { SCANPLUS_CONSOLE_ORIGIN: origin, SCANPLUS_OWNER_ID: 'owner', SCANPLUS_BACKEND_URL: 'https://backend.example', SCANPLUS_ADMIN_TOKEN: 'private-backend-token-1234567890123456789', SCANPLUS_LOGIN_KEY_HASH: createHash('sha256').update(accessKey).digest('hex'), SCANPLUS_SESSION_SECRET: '1'.repeat(64) };
const getEnv = key => env[key];
const endpoint = createSessionEndpoint({ getEnv, now: () => now });
const req = (method = 'GET', body, cookie, from = origin) => new Request(origin + '/api/session', { method, headers: { 'X-Scanplus-Console': '1', ...(method !== 'GET' ? { Origin: from, 'Content-Type': 'application/json' } : {}), ...(cookie ? { Cookie: cookie } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}) });

test('anonymous session status exposes no device or secret configuration', async () => {
  const response = await endpoint(req(), {});
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { authenticated: false, mode: 'SCANPLUS administrator' });
  assert.equal(response.headers.get('Cache-Control'), 'no-store');
  assert.equal(response.headers.get('Access-Control-Allow-Origin'), null);
});
test('only a valid access key creates a secure, host-only expiring session', async () => {
  for (const input of [{ accessKey: 'B'.repeat(43) }, { accessKey, command: 'extra' }, { password: accessKey }, { accessKey: '' }]) assert.equal((await endpoint(req('POST', input), {})).status, 401);
  const response = await endpoint(req('POST', { accessKey }), {});
  assert.equal(response.status, 200);
  const header = response.headers.get('Set-Cookie');
  assert.match(header, /^__Host-scanplus_session=/);
  for (const flag of ['Path=/', 'Secure', 'HttpOnly', 'SameSite=Strict', `Max-Age=${sessionSeconds}`]) assert.ok(header.includes(flag));
  assert.equal(header.includes(accessKey), false);
  assert.equal(header.includes(env.SCANPLUS_ADMIN_TOKEN), false);
  const cookie = header.split(';')[0];
  assert.equal((await (await endpoint(req('GET', null, cookie), {})).json()).authenticated, true);
  const logout = await endpoint(req('DELETE', null, cookie), {});
  assert.match(logout.headers.get('Set-Cookie'), /Max-Age=0/);
});
test('cross-origin sign-in, previews, missing headers and oversized requests are rejected', async () => {
  assert.equal((await endpoint(req('POST', { accessKey }, null, 'https://foreign.example'), {})).status, 403);
  assert.equal((await endpoint(new Request(origin + '/api/session'), {})).status, 403);
  assert.equal((await endpoint(new Request('https://preview.netlify.app/api/session'), {})).status, 403);
  assert.equal((await endpoint(req('POST', { accessKey: 'x'.repeat(3000) }), {})).status, 413);
  assert.equal((await endpoint(req('PUT', { accessKey }), {})).status, 405);
});
test('session forgery, expiry, a different audience and key rotation deny access', async () => {
  const header = await issueSession(origin, env.SCANPLUS_SESSION_SECRET, now);
  const cookie = header.split(';')[0], request = req('GET', null, cookie);
  assert.equal(await validSession(request, origin, env.SCANPLUS_SESSION_SECRET, now), true);
  assert.equal(await validSession(request, origin, env.SCANPLUS_SESSION_SECRET, now + sessionSeconds * 1000), false);
  assert.equal(await validSession(request, 'https://another.netlify.app', env.SCANPLUS_SESSION_SECRET, now), false);
  assert.equal(await validSession(request, origin, '2'.repeat(64), now), false);
  assert.equal(await validSession(req('GET', null, cookie.replace('=', '=forged')), origin, env.SCANPLUS_SESSION_SECRET, now), false);
});
test('a signed public session reaches typed management; guests and forged cookies never reach the backend', async () => {
  let forwarded = 0;
  const gateway = createGateway({ getEnv, now: () => now, fetchBackend: async (_url, options) => { forwarded++; assert.equal(options.headers.get('Cookie'), null); assert.equal(options.headers.get('Authorization'), `Bearer ${env.SCANPLUS_ADMIN_TOKEN}`); return Response.json([]); } });
  const apiReq = cookie => new Request(origin + '/api/admin/devices', { headers: { 'X-Scanplus-Console': '1', ...(cookie ? { Cookie: cookie } : {}) } });
  assert.equal((await gateway(apiReq(), {})).status, 401);
  assert.equal((await gateway(apiReq('__Host-scanplus_session=forged.fake'), {})).status, 401);
  assert.equal(forwarded, 0);
  const cookie = (await issueSession(origin, env.SCANPLUS_SESSION_SECRET, now)).split(';')[0];
  assert.equal((await gateway(apiReq(cookie), {})).status, 200);
  assert.equal(forwarded, 1);
});
