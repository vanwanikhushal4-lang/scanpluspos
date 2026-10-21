import { csp, HttpError } from '../../gateway-policy.mjs';
import { clearSession, issueSession, publicLoginConfigured, validAccessKey, validSession, verifiedOwner } from '../lib/session.mjs';

const reply = (status, value, cookie) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff', 'X-Frame-Options': 'DENY', 'Content-Security-Policy': csp, ...(cookie ? { 'Set-Cookie': cookie } : {}) } });
export function createSessionEndpoint({ getEnv, now = Date.now }) {
  return async (request, context) => {
    try {
      const url = new URL(request.url), origin = getEnv('SCANPLUS_CONSOLE_ORIGIN');
      if (!origin || url.origin !== origin || url.protocol !== 'https:') throw new HttpError(403, 'Open the production console address.');
      if (url.pathname !== '/api/session' || url.search) throw new HttpError(404, 'Not found.');
      if (request.headers.get('X-Scanplus-Console') !== '1') throw new HttpError(403, 'Console request required.');
      const from = request.headers.get('Origin'), site = request.headers.get('Sec-Fetch-Site');
      if (from && from !== origin || site && !['same-origin', 'none'].includes(site) || request.method !== 'GET' && from !== origin) throw new HttpError(403, 'A same-origin sign-in request is required.');
      const owner = verifiedOwner(context, getEnv('SCANPLUS_OWNER_ID'), now());
      const active = publicLoginConfigured(getEnv);
      if (request.method === 'GET') {
        const authenticated = owner || active && await validSession(request, origin, getEnv('SCANPLUS_SESSION_SECRET'), now());
        return reply(200, { authenticated, mode: owner ? 'Netlify administrator' : 'SCANPLUS administrator' });
      }
      if (request.method === 'DELETE') return reply(200, { authenticated: false }, clearSession());
      if (request.method !== 'POST') throw new HttpError(405, 'Method not allowed.');
      if (!active) throw new HttpError(503, 'Administrator sign-in is being configured.');
      if (!request.headers.get('Content-Type')?.startsWith('application/json')) throw new HttpError(415, 'Use a JSON sign-in request.');
      if (Number(request.headers.get('Content-Length')) > 2048) throw new HttpError(413, 'Request is too large.');
      const reader = request.body?.getReader(), chunks = []; let size = 0;
      if (!reader) throw new HttpError(400, 'Enter your administrator access key.');
      try { while (true) { const { done, value } = await reader.read(); if (done) break; size += value.byteLength; if (size > 2048) { await reader.cancel(); throw new HttpError(413, 'Request is too large.'); } chunks.push(value); } } finally { reader.releaseLock(); }
      const bytes = new Uint8Array(size); let offset = 0; for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
      let input; try { input = JSON.parse(new TextDecoder().decode(bytes)); } catch { throw new HttpError(400, 'Invalid sign-in request.'); }
      if (!input || typeof input !== 'object' || Array.isArray(input) || Object.keys(input).length !== 1 || !Object.hasOwn(input, 'accessKey') || !await validAccessKey(input.accessKey, getEnv('SCANPLUS_LOGIN_KEY_HASH'))) throw new HttpError(401, 'The administrator access key was not accepted.');
      return reply(200, { authenticated: true, mode: 'SCANPLUS administrator' }, await issueSession(origin, getEnv('SCANPLUS_SESSION_SECRET'), now()));
    } catch (error) { return reply(error instanceof HttpError ? error.status : 500, { error: error instanceof HttpError ? error.message : 'Sign-in is temporarily unavailable.' }); }
  };
}
export default async (request, context) => createSessionEndpoint({ getEnv: name => Netlify.env.get(name) })(request, context);
export const config = { path: '/api/session', rateLimit: { windowLimit: 20, windowSize: 60, aggregateBy: ['ip', 'domain'] } };
