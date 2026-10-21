import { online, needsAttention } from '../model.mjs';
export function fleetSummary(devices, now) {
  const current = devices.filter(device => !device.revoked);
  const result = { total: current.length, online: 0, offline: 0, protected: 0, maintenance: 0, attention: 0, unknown: 0, managed: 0, helpers: 0, sealed: 0, unsealed: 0 };
  for (const device of current) {
    result[online(device, now) ? 'online' : 'offline']++;
    result[needsAttention(device) ? 'attention' : device.enabled === true ? 'protected' : device.enabled === false ? 'maintenance' : 'unknown']++;
    if (device.owner_active) result.managed++;
    if (device.bridge_available) result.helpers++;
    if (device.usb_sealed) result.sealed++;
    if (device.usb_sealed === false) result.unsealed++;
  }
  return result;
}
export function applicationSummary(inventory) {
  const apps = inventory?.apps || [];
  const result = { total: apps.length, system: 0, user: 0, restricted: 0, management: 0, permissions: 0 };
  for (const app of apps) {
    result[applicationGroup(app)]++;
    result.permissions += app.permissions?.length || 0;
  }
  return result;
}
export function applicationGroup(app) {
  return app.systemApp ? 'system' : app.critical ? 'management' : app.hidden || app.suspended ? 'restricted' : 'user';
}
export function matchesAppFilter(app, filter) { return filter === 'all' || applicationGroup(app) === filter; }
export function auditDate(value) {
  const parts = new Intl.DateTimeFormat('en', { timeZone: 'Asia/Kolkata', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date(value));
  const part = type => parts.find(item => item.type === type).value;
  return `${part('year')}-${part('month')}-${part('day')}`;
}
export function csvText(headers, rows) {
  // Spreadsheet formula prefixes must remain text in exported reports.
  const cell = value => { let text = String(value ?? ''); if (/^[\s]*[=+@-]/.test(text)) text = "'" + text; return '"' + text.replaceAll('"', '""') + '"'; };
  return '\uFEFF' + [headers, ...rows].map(row => row.map(cell).join(',')).join('\r\n');
}
export function downloadCsv(name, headers, rows) {
  const url = URL.createObjectURL(new Blob([csvText(headers, rows)], { type: 'text/csv;charset=utf-8' }));
  const link = document.createElement('a'); link.href = url; link.download = name; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
}
