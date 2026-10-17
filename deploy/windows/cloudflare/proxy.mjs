const uuid = '[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}';
const routes = [
  ['GET', '^/api/health$', 0, false],
  ['POST', '^/api/enroll$', 16384, false],
  ['GET', '^/api/admin/(devices|audit)$', 0, true],
  ['POST', '^/api/admin/enrollments$', 16384, true],
  ['POST', '^/api/admin/apks$', 64 * 1024 * 1024, true],
  ['DELETE', `^/api/admin/devices/${uuid}$`, 0, true],
  ['GET', `^/api/admin/devices/${uuid}/(commands|apps|app-commands)$`, 0, true],
  ['POST', `^/api/admin/devices/${uuid}/(protection|app-commands)$`, 16384, true],
  ['POST', '^/api/device/check-in$', 16384, true],
  ['POST', '^/api/device/apps/inventory$', 2 * 1024 * 1024, true],
  ['POST', `^/api/device/commands/${uuid}/ack$`, 16384, true],
  ['POST', `^/api/device/app-commands/${uuid}/start$`, 16384, true],
  ['POST', `^/api/device/app-commands/${uuid}/ack$`, 65536, true],
  ['GET', `^/api/device/apks/${uuid}$`, 0, true],
].map(([method, path, limit, auth]) => ({ method, path: new RegExp(path), limit, auth }));

function failure(status, message) {
  return Response.json({ error: message }, { status, headers: { 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' } });
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.protocol !== 'https:') return failure(426, 'HTTPS required');
    if (url.search || url.pathname.includes('%')) return failure(400, 'Invalid API URL');
    const route = routes.find(r => r.method === request.method && r.path.test(url.pathname));
    if (!route) return failure(404, 'Not found');
    if (route.auth && !/^Bearer [A-Za-z0-9_-]{32,128}$/.test(request.headers.get('Authorization') || '')) return failure(401, 'Authentication required');
    const length = request.headers.get('Content-Length');
    if (length !== null && (!/^\d+$/.test(length) || !Number.isSafeInteger(Number(length)))) return failure(400, 'Invalid content length');
    if (length !== null && Number(length) > route.limit) return failure(413, 'Request too large');
    if (!env.SCANPLUS_BACKEND?.fetch) return failure(503, 'Backend connection is not configured');

    const headers = new Headers();
    for (const name of ['Accept', 'Content-Type', 'Content-Length', 'Authorization', 'X-Device-Id', 'X-APK-Name']) {
      const value = request.headers.get(name);
      if (value !== null) headers.set(name, value);
    }
    headers.set('X-Forwarded-Proto', 'https');
    headers.set('X-Forwarded-Host', url.host);
    headers.set('X-Forwarded-Port', '443');
    const ip = request.headers.get('CF-Connecting-IP');
    if (ip && /^[a-fA-F0-9:.]{2,64}$/.test(ip)) headers.set('X-Forwarded-For', ip);

    // Only this VPC binding and its fixed loopback origin are reachable.
    // Authorization remains end-to-end to the backend; no admin secret lives here.
    const upstream = new Request('http://localhost:9443' + url.pathname, {
      method: request.method, headers,
      body: request.method === 'POST' ? request.body : undefined,
      redirect: 'manual', duplex: 'half',
    });
    try {
      const result = await env.SCANPLUS_BACKEND.fetch(upstream);
      if (result.status >= 300 && result.status < 400) { await result.body?.cancel(); return failure(502, 'Unexpected backend redirect'); }
      const responseHeaders = new Headers();
      for (const name of ['Content-Type', 'Content-Length', 'Retry-After']) {
        const value = result.headers.get(name);
        if (value !== null) responseHeaders.set(name, value);
      }
      responseHeaders.set('Cache-Control', 'no-store');
      responseHeaders.set('X-Content-Type-Options', 'nosniff');
      return new Response(result.body, { status: result.status, headers: responseHeaders });
    } catch {
      return failure(503, 'Windows backend is unavailable');
    }
  },
};
