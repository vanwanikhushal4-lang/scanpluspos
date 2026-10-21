export const normalize = row => Object.fromEntries(Object.entries(row).map(([key, value]) => [key.toLowerCase(), value]));
export class ApiError extends Error {
  constructor(message, status = 0) { super(message); this.status = status; }
}
function authenticationRequired(response, path) {
  if (response.status === 401 && path !== '/api/session') window.dispatchEvent(new Event('scanplus-auth-required'));
  if (response.status === 429) throw new ApiError('Too many requests. Please wait a minute and try again.', 429);
}
export async function api(path, { signal, method = 'GET', body } = {}) {
  let response;
  try {
    response = await fetch(path, {
      method, signal, credentials: 'same-origin',
      headers: { 'X-Scanplus-Console': '1', ...(body ? { 'Content-Type': 'application/json' } : {}) },
      ...(body ? { body: JSON.stringify(body) } : {}),
    });
  } catch (error) {
    if (error.name === 'AbortError') throw error;
    throw new ApiError('The console connection was interrupted. Check the connection and refresh.');
  }
  authenticationRequired(response, path);
  let result;
  try { result = await response.json(); }
  catch { throw new ApiError('The console returned an unreadable response.', response.status); }
  if (!response.ok) throw new ApiError(result.error || 'This request could not be completed.', response.status);
  return result;
}
export async function getDevices(signal) { return (await api('/api/admin/devices', { signal })).map(normalize); }
export async function getCommands(id, signal) { return (await api(`/api/admin/devices/${id}/commands`, { signal })).map(normalize); }
export async function getAudit(signal) { return (await api('/api/admin/audit', { signal })).map(normalize); }
export async function setProtection(id, request) { return normalize(await api(`/api/admin/devices/${id}/protection`, { method: 'POST', body: request })); }
export async function getApps(id, signal) { return api(`/api/admin/devices/${id}/apps`, { signal }); }
export async function getAppCommands(id, signal) { return (await api(`/api/admin/devices/${id}/app-commands`, { signal })).map(normalize); }
export async function appCommand(id, request) { return normalize(await api(`/api/admin/devices/${id}/app-commands`, { method: 'POST', body: request })); }
export async function getWifi(id, signal) {
  const report = await api(`/api/admin/devices/${id}/wifi`, { signal });
  return { ...report, commands: (report.commands || []).map(normalize) };
}
export async function getSim(id, signal) {
  const report = await api(`/api/admin/devices/${id}/sim`, { signal });
  return { ...report, alerts: (report.alerts || []).map(normalize) };
}
export async function acknowledgeSimAlert(id, eventId) {
  return normalize(await api(`/api/admin/devices/${id}/sim/alerts/${eventId}/ack`, { method: 'POST', body: {} }));
}
export async function wifiCommand(id, action, request) {
  if (!['scan', 'policy', 'guard'].includes(action)) throw new ApiError('Invalid Wi-Fi operation.');
  return normalize(await api(`/api/admin/devices/${id}/wifi/${action}`, { method: 'POST', body: request }));
}
export async function uploadApk(file) {
  let response;
  try { response = await fetch('/api/admin/apks', { method: 'POST', credentials: 'same-origin', headers: { 'X-Scanplus-Console': '1', 'Content-Type': 'application/vnd.android.package-archive', 'X-APK-Name': file.name.replace(/[^A-Za-z0-9._ -]/g, '_') }, body: file }); }
  catch { throw new ApiError('APK upload was interrupted.'); }
  authenticationRequired(response, '/api/admin/apks');
  let result; try { result = await response.json(); } catch { throw new ApiError('Upload returned an unreadable response.', response.status); }
  if (!response.ok) throw new ApiError(result.error || 'APK upload was not accepted.', response.status);
  return normalize(result);
}
