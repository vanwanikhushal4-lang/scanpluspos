export const simEventName = type => ({ SIM_INSERTED: 'SIM inserted', SIM_REMOVED: 'SIM removed', SIM_REPLACED: 'SIM replaced', MONITORING_GAP: 'Monitoring gap' })[type] || 'SIM event';
export const simSlotName = slot => Number.isInteger(slot) && slot >= 0 ? `SIM ${slot + 1}` : 'All slots';
export const simStateName = state => ({ PRESENT: 'Present', ABSENT: 'No SIM detected', LOCKED: 'SIM locked', NOT_READY: 'Initializing', UNKNOWN: 'Unknown' })[state] || 'Unknown';
export const maskedSim = suffix => typeof suffix === 'string' && /^\d{4}$/.test(suffix) ? `•••• ${suffix}` : 'Unavailable';
export function simAlertCount(devices) {
  return devices.filter(device => !device.revoked).reduce((count, device) => count + (Number.isSafeInteger(device.sim_alert_count) && device.sim_alert_count > 0 ? device.sim_alert_count : 0), 0);
}
export function simReportFresh(reportedAt, now = Date.now()) {
  const at = Date.parse(reportedAt);
  return Number.isFinite(at) && now >= at && now - at < 90000;
}
