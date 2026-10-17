export function online(device, now = Date.now()) {
  if (!device || device.revoked || !device.last_seen) return false;
  const stamp = Date.parse(device.last_seen);
  return Number.isFinite(stamp) && now - stamp >= -60000 && now - stamp < 45000;
}
export function relativeTime(value, now = Date.now()) {
  if (!value || !Number.isFinite(Date.parse(value))) return 'Never';
  const seconds = Math.max(0, Math.floor((now - Date.parse(value)) / 1000));
  if (seconds < 5) return 'Just now';
  if (seconds < 60) return `${seconds}s ago`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
  return `${Math.floor(seconds / 86400)}d ago`;
}
export function needsAttention(device) {
  return device.enabled === false && /^Protection requested|USB restoration needs attention/i.test(device.detail || '');
}
export function protectionLabel(device) {
  if (device.revoked) return 'Revoked';
  if (needsAttention(device)) return 'Needs attention';
  if (device.enabled === true) return 'Protected';
  if (device.enabled === false) return 'Maintenance';
  return 'Awaiting status';
}
export function commandLabel(command) {
  return command.enabled ? 'Enable protection' : 'Enter maintenance';
}
export function prettyAction(action) {
  return ({ PROTECTION_ENABLE_REQUESTED: 'Protection enable requested', PROTECTION_DISABLE_REQUESTED: 'Maintenance requested', SUCCEEDED: 'Command completed', FAILED: 'Command failed', ENROLLED: 'Device enrolled', ENROLLMENT_CREATED: 'Enrollment created', DEVICE_REVOKED: 'Device access revoked' })[action] || action.replaceAll('_', ' ').toLowerCase();
}
export function matchesDevice(device, query) {
  const text = [device.label, device.serial, device.model, device.id].join(' ').toLowerCase();
  return text.includes(query.trim().toLowerCase());
}
