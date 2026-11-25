const demoSites = ['Orchid Market', 'Juniper Pharmacy', 'Harbour Books', 'Copper Café', 'Cedar Grocers', 'Maple Corner', 'Riverstone Retail', 'Bluebell Express'];
const checkInAges = [15000, 160000, 1260000, 21600000, 112000000];
const appCounts = [
  [24, { systemApp: false }],
  [91, { systemApp: true }],
  [7, { critical: true }],
  [13, { suspended: true }],
];
const activity = [
  ['PROTECTION_ENABLE_REQUESTED', 'Protection policy sent to Orchid Market', 'administrator', 40],
  ['SUCCEEDED', 'App inventory synced at Juniper Pharmacy', 'device', 96],
  ['ENROLLED', 'New checkout terminal at Harbour Books', 'device', 180],
  ['PROTECTION_DISABLE_REQUESTED', 'Scheduled maintenance at Copper Café', 'administrator', 380],
  ['FAILED', 'Update postponed until the terminal reconnects', 'device', 650],
];
const jobs = [
  ['INSTALL', 'com.example.demo.checkout', 'SUCCEEDED', 74],
  ['INSTALL', 'com.example.demo.receipts', 'RUNNING', 132],
  ['REFRESH', null, 'SUCCEEDED', 215],
  ['INSTALL', 'com.example.demo.catalog', 'PENDING', 360],
];

export function createDashboardDemo(now = Date.now()) {
  const timestamp = (secondsAgo = 0) => new Date(now - secondsAgo * 1000).toISOString();
  const devices = Array.from({ length: 248 }, (_, index) => {
    const rank = (index * 67 + 9) % 248;
    const statusRank = (index * 53 + 8) % 248;
    const checkInBucket = rank < 195 ? 0 : rank < 215 ? 1 : rank < 230 ? 2 : rank < 242 ? 3 : 4;
    const enabled = statusRank < 198 ? true : statusRank < 239 ? false : null;
    return {
      id: `demo-terminal-${index + 1}`,
      serial: `DEMO-POS-${String(index + 1).padStart(4, '0')}`,
      label: `${demoSites[index % demoSites.length]} · POS ${String(Math.floor(index / demoSites.length) + 1).padStart(2, '0')}`,
      model: 'PAX A920',
      demoIndex: index,
      last_seen: new Date(now - checkInAges[checkInBucket] - rank * 28).toISOString(),
      enabled,
      detail: statusRank >= 229 && statusRank < 239 ? 'Protection requested' : '',
      owner_active: statusRank < 227,
      bridge_available: statusRank < 211,
      usb_sealed: statusRank < 206 ? true : statusRank < 242 ? false : null,
      sim_alert_count: statusRank < 230 ? Number(statusRank % 21 === 0) : null,
      wifiSecured: rank < 216,
      bluetoothRestricted: statusRank < 182,
    };
  });
  const audit = activity.map(([action, detail, actor, seconds], index) => ({
    id: `demo-event-${index}`, action, detail, actor, event_time: timestamp(seconds),
  }));
  const appJobs = jobs.map(([action, package_name, state, seconds], index) => ({
    id: `demo-job-${index}`, action, package_name, state, created_at: timestamp(seconds),
  }));
  const extraInstalls = Array.from({ length: 29 }, (_, index) => ({
    id: `demo-install-${index}`,
    action: 'INSTALL',
    state: index < 24 ? 'SUCCEEDED' : index < 26 ? 'RUNNING' : 'FAILED',
    created_at: timestamp(450 + index * 75),
  }));
  const wireless = {
    wifi: devices.filter(device => device.wifiSecured).length,
    bluetooth: devices.filter(device => device.bluetoothRestricted).length,
  };
  return {
    now,
    lastRefresh: now,
    loading: false,
    devices,
    audit,
    appJobs: [...appJobs, ...extraInstalls],
    wireless,
  };
}

export function demoInventory(now, index) {
  return {
    reportedAt: new Date(now - (56 + index % 31) * 1000).toISOString(),
    apps: appCounts.flatMap(([count, attributes], group) =>
      Array.from({ length: count + index * (group + 1) % 9 }, () => ({ ...attributes }))),
  };
}
