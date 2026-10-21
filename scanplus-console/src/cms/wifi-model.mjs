export const securedWifi = new Set(['WEP', 'WPA', 'WPA2', 'WPA3', 'ENTERPRISE']);
export function canEnableOpenWifiBlock(device, report, now = Date.now()) {
  const at = Date.parse(report?.reportedAt);
  return Boolean(device?.owner_active && !device.revoked && Number.isFinite(at) && now >= at && now - at < 90000 && securedWifi.has(report?.snapshot?.connection?.security));
}
export function canEnableOpenWifiGuard(device, report, now = Date.now()) {
  return Boolean(report?.snapshot?.openGuard && canEnableOpenWifiBlock(device, report, now));
}
export function wifiActionLabel(job) {
  if (job.action === 'SCAN') return 'Scan nearby networks';
  if (job.action === 'SET_OPEN_GUARD') return job.blocked ? 'Enable open-network guard' : 'Disable open-network guard';
  return job.blocked ? 'Enable full Wi-Fi protection' : 'Disable full Wi-Fi protection';
}
export function wifiAge(seconds, reportedAt, now = Date.now()) {
  if (seconds == null || !Number.isFinite(Date.parse(reportedAt))) return 'Unknown';
  const age = Math.max(0, Math.floor(seconds + (now - Date.parse(reportedAt)) / 1000));
  return age < 60 ? `${age}s ago` : age < 3600 ? `${Math.floor(age / 60)}m ago` : `${Math.floor(age / 3600)}h ago`;
}
export const securityName = value => ({ OPEN: 'Open', WEP: 'WEP · weak', WPA: 'WPA · legacy', WPA2: 'WPA2', WPA3: 'WPA3', ENTERPRISE: 'Enterprise', ENHANCED_OPEN: 'Enhanced Open', UNKNOWN: 'Unknown' })[value] || 'Unknown';
