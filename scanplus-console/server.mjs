import http from 'node:http';
import https from 'node:https';
import { readFile, stat } from 'node:fs/promises';
import { resolve, dirname, extname, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const uuid = '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}';
const protectionRoute = new RegExp(`^/api/admin/devices/${uuid}/protection$`, 'i');
const commandsRoute = new RegExp(`^/api/admin/devices/${uuid}/commands$`, 'i');
const appInventoryRoute = new RegExp(`^/api/admin/devices/${uuid}/apps$`, 'i');
const appCommandsRoute = new RegExp(`^/api/admin/devices/${uuid}/app-commands$`, 'i');
const appActions = new Set(['REFRESH', 'LAUNCH', 'FORCE_STOP', 'SUSPEND', 'RESUME', 'HIDE', 'SHOW', 'BLOCK_UNINSTALL', 'ALLOW_UNINSTALL', 'GRANT_PERMISSION', 'DENY_PERMISSION', 'RESET_PERMISSION', 'INSTALL', 'UNINSTALL']);
const apkLimit = 64 * 1024 * 1024;
const contentTypes = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.svg': 'image/svg+xml', '.png': 'image/png', '.ico': 'image/x-icon' };
const csp = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'";
class HttpError extends Error { constructor(status, message) { super(message); this.status = status; } }
function json(res, status, body) { res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' }); res.end(JSON.stringify(body)); }
function body(req, limit = 16384) {
  return new Promise((resolveBody, reject) => {
    let size = 0; const chunks = []; let failed = false;
    req.on('data', chunk => {
      size += chunk.length;
      if (size > limit) { if (!failed) reject(new HttpError(413, 'Request is too large.')); failed = true; return; }
      if (!failed) chunks.push(chunk);
    });
    req.on('end', () => { if (!failed) resolveBody(Buffer.concat(chunks)); });
    req.on('error', () => reject(new HttpError(400, 'Request was interrupted.')));
  });
}
export function backendReader({ backendUrl, adminToken, ca }) {
  const origin = new URL(backendUrl);
  if (origin.protocol !== 'https:' || origin.username || origin.password || origin.search || origin.hash || !['', '/'].includes(origin.pathname)) throw new Error('Configure a dedicated HTTPS backend origin.');
  if (typeof adminToken !== 'string' || adminToken.length < 32) throw new Error('The private administrator configuration is incomplete.');
  return ({ method, path, payload, contentType, filename }) => new Promise((resolveReply, reject) => {
    const upstream = https.request(new URL(path, origin), {
      method, ca, rejectUnauthorized: true, minVersion: 'TLSv1.2',
      headers: { Authorization: `Bearer ${adminToken}`, Accept: 'application/json', ...(payload ? { 'Content-Type': contentType || 'application/json', 'Content-Length': Buffer.byteLength(payload) } : {}), ...(filename ? { 'X-APK-Name': filename } : {}) },
    }, response => {
      const chunks = []; let size = 0;
      response.on('data', chunk => {
        size += chunk.length;
        if (size > 4 * 1024 * 1024) { upstream.destroy(); reject(new HttpError(502, 'The backend returned an unexpectedly large response.')); }
        else chunks.push(chunk);
      });
      response.on('end', () => {
        const status = response.statusCode;
        if (status === 401 || status === 403) { reject(new HttpError(502, 'Backend authentication needs attention. Check the private server configuration.')); return; }
        if (status >= 500) { reject(new HttpError(502, 'The SCANPLUS backend is unavailable. Try refreshing shortly.')); return; }
        let value;
        try { value = JSON.parse(Buffer.concat(chunks).toString('utf8')); }
        catch { reject(new HttpError(502, 'The backend returned an unreadable response.')); return; }
        resolveReply({ status, value });
      });
      response.on('error', () => reject(new HttpError(502, 'The backend connection was interrupted.')));
    });
    upstream.setTimeout(contentType ? 120000 : 12000, () => upstream.destroy(new Error('timeout')));
    upstream.on('error', () => reject(new HttpError(502, 'Cannot reach the dedicated backend. Check that it is running and reachable.')));
    if (payload) upstream.write(payload);
    upstream.end();
  });
}

export function createConsoleServer({ backendUrl, adminToken, ca, distDirectory = resolve(here, 'dist'), readBackend = null }) {
  const upstream = readBackend || backendReader({ backendUrl, adminToken, ca });
  const server = http.createServer(async (req, res) => {
    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('Content-Security-Policy', csp);
    res.setHeader('Referrer-Policy', 'no-referrer');
    res.setHeader('X-Frame-Options', 'DENY');
    res.setHeader('Cross-Origin-Resource-Policy', 'same-origin');
    try {
      // This is a local administrator console, bound exclusively to loopback.
      // Host checks also reject DNS rebinding. Cross-origin API requests are never accepted.
      const host = req.headers.host || '';
      if (!/^(127\.0\.0\.1|localhost):\d+$/.test(host)) throw new HttpError(403, 'Open this console through its local address.');
      if (!req.url?.startsWith('/') || req.url.startsWith('//')) throw new HttpError(400, 'Invalid request path.');
      const url = new URL(req.url, `http://${host}`);
      const path = url.pathname;
      if (path.startsWith('/api/')) {
        if (req.headers['x-scanplus-console'] !== '1') throw new HttpError(403, 'Console request required.');
        if (req.headers.origin && req.headers.origin !== `http://${host}`) throw new HttpError(403, 'Cross-origin management requests are blocked.');
        if (req.headers['sec-fetch-site'] && !['same-origin', 'none'].includes(req.headers['sec-fetch-site'])) throw new HttpError(403, 'Cross-origin management requests are blocked.');
        if (url.search) throw new HttpError(400, 'Unexpected query parameters.');
        if (req.method === 'GET' && path === '/api/console/info') {
          json(res, 200, { backend: new URL(backendUrl).origin, mode: 'Local administrator', pollSeconds: 3 }); return;
        }
        const readAllowed = req.method === 'GET' && (['/api/admin/devices', '/api/admin/audit', '/api/health'].includes(path) || commandsRoute.test(path) || appInventoryRoute.test(path) || appCommandsRoute.test(path));
        const upload = req.method === 'POST' && path === '/api/admin/apks';
        const writeAllowed = req.method === 'POST' && (protectionRoute.test(path) || appCommandsRoute.test(path) || upload);
        if (!readAllowed && !writeAllowed) throw new HttpError(404, 'This operation is not available in the console.');
        let payload, contentType, filename;
        if (writeAllowed) {
          if (req.headers.origin !== `http://${host}`) throw new HttpError(403, 'A same-origin management request is required.');
          if (upload) {
            if (req.headers['content-type'] !== 'application/vnd.android.package-archive') throw new HttpError(415, 'Choose an APK file.');
            if (Number(req.headers['content-length']) > apkLimit) throw new HttpError(413, 'APK limit is 64 MiB.');
            payload = await body(req, apkLimit);
            if (!payload.length || payload[0] !== 0x50 || payload[1] !== 0x4b) throw new HttpError(400, 'Choose a valid APK archive.');
            contentType = 'application/vnd.android.package-archive';
            filename = String(req.headers['x-apk-name'] || 'application.apk').replace(/[^A-Za-z0-9._ -]/g, '_').slice(-150);
          } else {
          if (!req.headers['content-type']?.startsWith('application/json')) throw new HttpError(415, 'Use a JSON protection request.');
          if (Number(req.headers['content-length']) > 16384) throw new HttpError(413, 'Request is too large.');
          let input;
          try { input = JSON.parse((await body(req)).toString('utf8')); }
          catch (error) { if (error instanceof HttpError) throw error; throw new HttpError(400, 'Invalid JSON protection request.'); }
          if (appCommandsRoute.test(path)) {
            const permitted = ['requestId', 'action', 'packageName', 'permission', 'artifactId', 'ttlSeconds'];
            if (!input || Array.isArray(input) || Object.keys(input).some(key => !permitted.includes(key)) || !appActions.has(input.action) || typeof input.requestId !== 'string' || !new RegExp(`^${uuid}$`, 'i').test(input.requestId) || !Number.isInteger(input.ttlSeconds) || input.ttlSeconds < 15 || input.ttlSeconds > 600) throw new HttpError(400, 'Invalid app control request.');
            const withoutPackage = ['REFRESH', 'INSTALL'].includes(input.action);
            if (withoutPackage ? input.packageName != null : typeof input.packageName !== 'string' || input.packageName.length > 180 || !/^(android|[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+)$/.test(input.packageName)) throw new HttpError(400, 'Invalid app package.');
            if (input.action === 'INSTALL' ? typeof input.artifactId !== 'string' || !new RegExp(`^${uuid}$`, 'i').test(input.artifactId) : input.artifactId != null) throw new HttpError(400, 'Invalid APK selection.');
            if (input.action.endsWith('_PERMISSION') ? typeof input.permission !== 'string' || !/^android\.permission\.[A-Z0-9_]+$/.test(input.permission) : input.permission != null) throw new HttpError(400, 'Invalid permission selection.');
          } else if (!input || typeof input !== 'object' || Array.isArray(input) || Object.keys(input).some(key => !['requestId', 'enabled', 'ttlSeconds'].includes(key)) || typeof input.enabled !== 'boolean' || typeof input.requestId !== 'string' || !new RegExp(`^${uuid}$`, 'i').test(input.requestId) || !Number.isInteger(input.ttlSeconds) || input.ttlSeconds < 15 || input.ttlSeconds > 300) throw new HttpError(400, 'Invalid protection request.');
          payload = JSON.stringify(input);
          }
        }
        const reply = await upstream({ method: req.method, path, payload, contentType, filename });
        if (reply.status >= 400) {
          const reason = typeof reply.value?.error === 'string' ? reply.value.error.slice(0, 300) : null;
          const message = reply.status === 404 ? 'This device is no longer available.' : [400, 409, 413].includes(reply.status) && reason ? reason : reply.status === 409 ? 'This request conflicts with an earlier command. Refresh and try again.' : 'The backend could not accept this request.';
          json(res, reply.status, { error: message }); return;
        }
        json(res, reply.status, reply.value); return;
      }
      if (!['GET', 'HEAD'].includes(req.method)) throw new HttpError(405, 'Method not allowed.');
      const relative = decodeURIComponent(path === '/' ? '/index.html' : path);
      const file = resolve(distDirectory, `.${relative}`);
      if (!file.startsWith(resolve(distDirectory) + sep)) throw new HttpError(404, 'Page not found.');
      let data;
      try { if (!(await stat(file)).isFile()) throw new Error(); data = await readFile(file); }
      catch { throw new HttpError(404, 'Page not found.'); }
      res.writeHead(200, { 'Content-Type': contentTypes[extname(file)] || 'application/octet-stream' });
      res.end(req.method === 'HEAD' ? undefined : data);
    } catch (error) {
      if (!res.headersSent) json(res, error instanceof HttpError ? error.status : 500, { error: error instanceof HttpError ? error.message : 'The local console needs attention.' });
      else res.end();
    }
  });
  server.requestTimeout = 120000;
  server.headersTimeout = 10000;
  return server;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const adminFile = process.env.SCANPLUS_ADMIN_FILE || resolve(here, '../.scanplus-local/admin.json');
    const caFile = process.env.SCANPLUS_CA_FILE || resolve(here, '../.scanplus-local/server.pem');
    let settings;
    try { settings = JSON.parse(await readFile(adminFile, 'utf8')); }
    catch { throw new Error('Cannot read the private SCANPLUS administrator configuration. See README.md.'); }
    const ca = await readFile(caFile);
    const port = Number(process.env.SCANPLUS_CONSOLE_PORT || 5173);
    if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('Choose a console port between 1024 and 65535.');
    const server = createConsoleServer({ backendUrl: settings.adminBaseUrl || settings.baseUrl, adminToken: settings.adminToken, ca });
    server.on('error', error => { console.error(error.code === 'EADDRINUSE' ? 'The console port is already in use.' : 'The console server could not start.'); process.exitCode = 1; });
    server.listen(port, '127.0.0.1', () => console.log(`SCANPLUS console ready: http://127.0.0.1:${port}`));
    for (const signal of ['SIGTERM', 'SIGINT']) process.on(signal, () => server.close(() => process.exit(0)));
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}
