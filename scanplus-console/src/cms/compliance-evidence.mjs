import { needsAttention, online } from '../model.mjs';
import { securityName } from './wifi-model.mjs';

const unknown = { label: 'Not reported', tone: 'muted' };

function observed(label, tone, fresh) {
  return fresh ? { label, tone } : { label: `Last report · ${label}`, tone: 'muted' };
}

export function complianceEvidence({ device, ready, now, wifi, wifiError, wifiLoading }) {
  const terminalReported = !device?.revoked && Number.isFinite(Date.parse(device?.last_seen));
  const fresh = ready && online(device, now);
  const wifiStamp = Date.parse(wifi?.reportedAt);
  const wifiFresh = fresh && !wifiError && now >= wifiStamp && now - wifiStamp < 90000;
  const unavailable = wifiError ? { label: 'Wi-Fi report unavailable', tone: 'muted' }
    : wifiLoading ? { label: 'Loading Wi-Fi report', tone: 'muted' } : unknown;
  const wifiSnapshot = Number.isFinite(wifiStamp) && !device?.revoked ? wifi?.snapshot : null;
  const { policy, openGuard, connection } = wifiSnapshot || {};

  let protection = unknown;
  if (terminalReported && device.enabled === true) protection = observed('Full protection on', 'green', fresh);
  if (terminalReported && device.enabled === false) {
    const label = needsAttention(device) ? 'Protection needs attention'
      : /^Maintenance\b/i.test(device.detail || '') ? 'Maintenance' : 'Full protection unconfirmed';
    protection = observed(label, 'amber', fresh);
  }

  let usb = unknown;
  if (terminalReported && device.bridge_available === false) usb = observed('USB state unavailable', 'amber', fresh);
  if (terminalReported && device.bridge_available === true && typeof device.usb_sealed === 'boolean') {
    usb = observed(device.usb_sealed ? 'USB data sealed' : 'USB data unsealed', device.usb_sealed ? 'green' : 'amber', fresh);
  }

  return {
    protection,
    usb,
    wifiLock: typeof policy?.localSettingsLocked === 'boolean'
      ? observed(policy.localSettingsLocked ? 'Settings locked' : 'Settings unlocked', policy.localSettingsLocked ? 'green' : 'muted', wifiFresh)
      : unavailable,
    wifiGuard: typeof openGuard?.enabled === 'boolean'
      ? observed(openGuard.enabled ? openGuard.applied === true ? 'Guard on' : 'Guard needs attention' : 'Guard off',
        openGuard.enabled ? openGuard.applied === true ? 'green' : 'amber' : 'muted', wifiFresh)
      : unavailable,
    wifiSecurity: connection
      ? observed(securityName(connection.security), 'blue', wifiFresh)
      : wifiSnapshot ? observed('No connection reported', 'muted', wifiFresh) : unavailable,
  };
}
