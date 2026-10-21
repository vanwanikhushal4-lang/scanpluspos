// Shared by the loopback console and the hosted gateway. No platform or secrets.
export const uuid = '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}';
const idPattern = new RegExp(`^${uuid}$`, 'i');
export const protectionRoute = new RegExp(`^/api/admin/devices/${uuid}/protection$`, 'i');
const commandsRoute = new RegExp(`^/api/admin/devices/${uuid}/commands$`, 'i');
const inventoryRoute = new RegExp(`^/api/admin/devices/${uuid}/apps$`, 'i');
const wifiRoute = new RegExp(`^/api/admin/devices/${uuid}/wifi$`, 'i');
const simRoute = new RegExp(`^/api/admin/devices/${uuid}/sim$`, 'i');
export const simAckRoute = new RegExp(`^/api/admin/devices/${uuid}/sim/alerts/${uuid}/ack$`, 'i');
export const wifiCommandRoute = new RegExp(`^/api/admin/devices/${uuid}/wifi/(scan|policy|guard)$`, 'i');
export const appCommandsRoute = new RegExp(`^/api/admin/devices/${uuid}/app-commands$`, 'i');
export const apkLimit = 64 * 1024 * 1024;
export const csp = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'";
const actions = new Set(['REFRESH', 'LAUNCH', 'FORCE_STOP', 'SUSPEND', 'RESUME', 'HIDE', 'SHOW', 'BLOCK_UNINSTALL', 'ALLOW_UNINSTALL', 'GRANT_PERMISSION', 'DENY_PERMISSION', 'RESET_PERMISSION', 'INSTALL', 'UNINSTALL']);
export class HttpError extends Error { constructor(status, message) { super(message); this.status = status; } }
export function routeKind(method, path) {
  if (method === 'GET' && (['/api/admin/devices', '/api/admin/audit', '/api/health'].includes(path) || commandsRoute.test(path) || inventoryRoute.test(path) || appCommandsRoute.test(path) || wifiRoute.test(path) || simRoute.test(path))) return 'read';
  if (method === 'POST' && path === '/api/admin/apks') return 'upload';
  if (method === 'POST' && (protectionRoute.test(path) || appCommandsRoute.test(path) || wifiCommandRoute.test(path) || simAckRoute.test(path))) return 'command';
  throw new HttpError(404, 'This operation is not available in the console.');
}
export function validateCommand(path, input) {
  if (simAckRoute.test(path)) {
    if (!input || typeof input !== 'object' || Array.isArray(input) || Object.keys(input).length) throw new HttpError(400, 'Invalid SIM alert acknowledgment.');
  } else if (wifiCommandRoute.test(path)) {
    const policy = /\/(policy|guard)$/i.test(path);
    const permitted = policy ? ['requestId', 'blocked', 'ttlSeconds'] : ['requestId', 'ttlSeconds'];
    if (!input || typeof input !== 'object' || Array.isArray(input) || Object.keys(input).some(key => !permitted.includes(key)) || typeof input.requestId !== 'string' || !idPattern.test(input.requestId) || !Number.isInteger(input.ttlSeconds) || input.ttlSeconds < 15 || input.ttlSeconds > 300 || policy && typeof input.blocked !== 'boolean') throw new HttpError(400, 'Invalid Wi-Fi request.');
  } else if (appCommandsRoute.test(path)) {
    const permitted = ['requestId', 'action', 'packageName', 'permission', 'artifactId', 'ttlSeconds'];
    if (!input || typeof input !== 'object' || Array.isArray(input) || Object.keys(input).some(key => !permitted.includes(key)) || !actions.has(input.action) || typeof input.requestId !== 'string' || !idPattern.test(input.requestId) || !Number.isInteger(input.ttlSeconds) || input.ttlSeconds < 15 || input.ttlSeconds > 600) throw new HttpError(400, 'Invalid app control request.');
    const withoutPackage = ['REFRESH', 'INSTALL'].includes(input.action);
    if (withoutPackage ? input.packageName != null : typeof input.packageName !== 'string' || input.packageName.length > 180 || !/^(android|[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+)$/.test(input.packageName)) throw new HttpError(400, 'Invalid app package.');
    if (input.action === 'INSTALL' ? typeof input.artifactId !== 'string' || !idPattern.test(input.artifactId) : input.artifactId != null) throw new HttpError(400, 'Invalid APK selection.');
    if (input.action.endsWith('_PERMISSION') ? typeof input.permission !== 'string' || !/^android\.permission\.[A-Z0-9_]+$/.test(input.permission) : input.permission != null) throw new HttpError(400, 'Invalid permission selection.');
  } else if (!input || typeof input !== 'object' || Array.isArray(input) || Object.keys(input).some(key => !['requestId', 'enabled', 'ttlSeconds'].includes(key)) || typeof input.enabled !== 'boolean' || typeof input.requestId !== 'string' || !idPattern.test(input.requestId) || !Number.isInteger(input.ttlSeconds) || input.ttlSeconds < 15 || input.ttlSeconds > 300) throw new HttpError(400, 'Invalid protection request.');
  return JSON.stringify(input);
}
export const apkName = value => String(value || 'application.apk').replace(/[^A-Za-z0-9._ -]/g, '_').slice(-150);
