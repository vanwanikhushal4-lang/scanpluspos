import { apkLimit, apkName, csp, HttpError, routeKind, validateCommand } from '../../gateway-policy.mjs';
import { publicLoginConfigured, validSession, verifiedOwner } from '../lib/session.mjs';

const securityHeaders = {
  'Cache-Control': 'no-store',
  'X-Content-Type-Options': 'nosniff',
  'Content-Security-Policy': csp,
  'Referrer-Policy': 'no-referrer',
  'X-Frame-Options': 'DENY',
  'Cross-Origin-Resource-Policy': 'same-origin',
};
function json(status, value) {
  return new Response(JSON.stringify(value), { status, headers: { ...securityHeaders, 'Content-Type': 'application/json; charset=utf-8' } });
}
async function limitedBytes(stream, limit) {
  if (!stream) return new Uint8Array();
  const reader = stream.getReader(), chunks = []; let size = 0;
  try {
    while (true) {
      const { done, value } = await reader.read(); if (done) break;
      size += value.byteLength;
      if (size > limit) { await reader.cancel(); throw new HttpError(413, 'Request is too large.'); }
      chunks.push(value);
    }
  } finally { reader.releaseLock(); }
  const result = new Uint8Array(size); let offset = 0;
  for (const chunk of chunks) { result.set(chunk, offset); offset += chunk.byteLength; }
  return result;
}
// Validate the archive header before contacting the backend, then stream without
// base64 encoding or buffering an entire 64 MiB APK in a serverless function.
async function apkStream(stream) {
  if (!stream) throw new HttpError(400, 'Choose a valid APK archive.');
  const reader = stream.getReader(), initial = []; let size = 0;
  while (size < 2) {
    const { done, value } = await reader.read();
    if (done) { await reader.cancel(); throw new HttpError(400, 'Choose a valid APK archive.'); }
    initial.push(value); size += value.byteLength;
  }
  const bytes = initial.flatMap(chunk => Array.from(chunk.subarray(0, 2))).slice(0, 2);
  if (bytes[0] !== 0x50 || bytes[1] !== 0x4b || size > apkLimit) { await reader.cancel(); throw new HttpError(size > apkLimit ? 413 : 400, size > apkLimit ? 'APK limit is 64 MiB.' : 'Choose a valid APK archive.'); }
  return new ReadableStream({
    start(controller) { for (const chunk of initial) controller.enqueue(chunk); },
    async pull(controller) {
      try {
        const { done, value } = await reader.read();
        if (done) { controller.close(); reader.releaseLock(); return; }
        size += value.byteLength;
        if (size > apkLimit) { await reader.cancel(); controller.error(new HttpError(413, 'APK limit is 64 MiB.')); return; }
        controller.enqueue(value);
      } catch (error) { controller.error(error); }
    },
    cancel(reason) { return reader.cancel(reason); },
  });
}

export function createGateway({ getEnv, fetchBackend = fetch, now = Date.now }) {
  return async (request, context) => {
    try {
      const ownerId = getEnv('SCANPLUS_OWNER_ID');
      const consoleOrigin = getEnv('SCANPLUS_CONSOLE_ORIGIN');
      const backendUrl = getEnv('SCANPLUS_BACKEND_URL');
      const adminToken = getEnv('SCANPLUS_ADMIN_TOKEN');
      if (!ownerId || !consoleOrigin || !backendUrl || !adminToken || adminToken.length < 32) throw new HttpError(503, 'The hosted console is not configured.');
      const url = new URL(request.url), target = new URL(backendUrl);
      if (url.origin !== consoleOrigin || url.protocol !== 'https:') throw new HttpError(403, 'Open the production console address.');
      // Private deployments can use Netlify's verified owner context. Public
      // deployments require a server-signed session; client headers are not auth.
      const owner = verifiedOwner(context, ownerId, now());
      const session = publicLoginConfigured(getEnv) && await validSession(request, consoleOrigin, getEnv('SCANPLUS_SESSION_SECRET'), now());
      if (!owner && !session) throw new HttpError(401, 'Sign in to the SCANPLUS administrator console.');
      if (target.protocol !== 'https:' || target.username || target.password || target.search || target.hash || target.pathname !== '/') throw new HttpError(503, 'The backend configuration needs attention.');
      if (request.headers.get('X-Scanplus-Console') !== '1') throw new HttpError(403, 'Console request required.');
      const origin = request.headers.get('Origin'), site = request.headers.get('Sec-Fetch-Site');
      if (origin && origin !== consoleOrigin || site && !['same-origin', 'none'].includes(site)) throw new HttpError(403, 'Cross-origin management requests are blocked.');
      if (url.search) throw new HttpError(400, 'Unexpected query parameters.');
      if (request.method === 'GET' && url.pathname === '/api/console/info') return json(200, { backend: target.origin, mode: owner ? 'Netlify administrator' : 'SCANPLUS administrator', pollSeconds: 10 });
      const kind = routeKind(request.method, url.pathname);
      let body;
      const headers = new Headers({ Authorization: `Bearer ${adminToken}`, Accept: 'application/json' });
      if (kind !== 'read') {
        if (origin !== consoleOrigin) throw new HttpError(403, 'A same-origin management request is required.');
        if (kind === 'upload') {
          if (request.headers.get('Content-Type') !== 'application/vnd.android.package-archive') throw new HttpError(415, 'Choose an APK file.');
          if (Number(request.headers.get('Content-Length')) > apkLimit) throw new HttpError(413, 'APK limit is 64 MiB.');
          body = await apkStream(request.body);
          headers.set('Content-Type', 'application/vnd.android.package-archive');
          headers.set('X-APK-Name', apkName(request.headers.get('X-APK-Name')));
        } else {
          if (!request.headers.get('Content-Type')?.startsWith('application/json')) throw new HttpError(415, 'Use a JSON management request.');
          const bytes = await limitedBytes(request.body, 16384);
          let input; try { input = JSON.parse(new TextDecoder().decode(bytes)); } catch { throw new HttpError(400, 'Invalid JSON management request.'); }
          body = validateCommand(url.pathname, input);
          headers.set('Content-Type', 'application/json');
        }
      }
      // Fresh headers prevent forwarding browser cookies, supplied Authorization,
      // Host, proxy headers or Netlify access credentials to Cloudflare.
      let reply;
      try {
        reply = await fetchBackend(new URL(url.pathname, target), { method: request.method, headers, body, redirect: 'error', signal: AbortSignal.timeout(kind === 'upload' ? 35000 : 12000), ...(kind === 'upload' ? { duplex: 'half' } : {}) });
      } catch (error) {
        if (error instanceof HttpError) throw error;
        throw new HttpError(502, 'Cannot reach the SCANPLUS backend. Check that the Windows host and tunnel are running.');
      }
      if ([401, 403].includes(reply.status)) throw new HttpError(502, 'Backend authentication needs attention.');
      if (reply.status >= 500) throw new HttpError(502, 'The SCANPLUS backend is unavailable.');
      if (reply.status >= 300 && reply.status < 400) throw new HttpError(502, 'Backend redirects are blocked.');
      let value;
      try { value = JSON.parse(new TextDecoder().decode(await limitedBytes(reply.body, 4 * 1024 * 1024))); }
      catch { throw new HttpError(502, 'The backend returned an unreadable response.'); }
      if (reply.status >= 400) {
        const reason = typeof value?.error === 'string' ? value.error.slice(0, 300).split(adminToken).join('[redacted]') : null;
        return json(reply.status, { error: reply.status === 404 ? 'This device is no longer available.' : [400, 409, 413].includes(reply.status) && reason ? reason : 'The backend could not accept this request.' });
      }
      return json(reply.status, value);
    } catch (error) { return json(error instanceof HttpError ? error.status : 500, { error: error instanceof HttpError ? error.message : 'The hosted console needs attention.' }); }
  };
}

export default async (request, context) => createGateway({ getEnv: name => Netlify.env.get(name) })(request, context);
export const config = { path: '/api/*', excludedPath: '/api/session', rateLimit: { windowLimit: 180, windowSize: 60, aggregateBy: ['ip', 'domain'] } };
