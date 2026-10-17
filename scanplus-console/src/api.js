export const normalize = row => Object.fromEntries(Object.entries(row).map(([key, value]) => [key.toLowerCase(), value]));
export class ApiError extends Error {
  constructor(message, status = 0) { super(message); this.status = status; }
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
export async function uploadApk(file) {
  let response;
  try { response = await fetch('/api/admin/apks', { method: 'POST', credentials: 'same-origin', headers: { 'X-Scanplus-Console': '1', 'Content-Type': 'application/vnd.android.package-archive', 'X-APK-Name': file.name.replace(/[^A-Za-z0-9._ -]/g, '_') }, body: file }); }
  catch { throw new ApiError('APK upload was interrupted.'); }
  let result; try { result = await response.json(); } catch { throw new ApiError('Upload returned an unreadable response.', response.status); }
  if (!response.ok) throw new ApiError(result.error || 'APK upload was not accepted.', response.status);
  return normalize(result);
}
