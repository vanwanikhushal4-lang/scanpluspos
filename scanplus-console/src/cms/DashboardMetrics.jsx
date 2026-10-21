import React from 'react';
import { Activity, AppWindow, ArrowUpRight, BellRing, Cable, Download, HardDrive, Monitor, ShieldCheck, Wifi } from 'lucide-react';
import { simAlertCount } from './sim-model.mjs';
import { Card } from './ui.jsx';

function Metric({ title, icon: Icon, category, tone = 'blue', number, caption, note, children }) {
  return <li><Card className="cms-overview-card">
    <div className="cms-overview-head">
      <span className={`cms-metric-icon ${tone}`}><Icon size={18}/></span>
      <div><span className="cms-overview-category">{category}</span><h2>{title}</h2></div>
      <span className="cms-overview-number" aria-hidden="true">{String(number).padStart(2, '0')}</span>
    </div>
    <div className="cms-overview-body"><p className="cms-kpi-caption">{caption}</p><div className="cms-kpi-links">{children}</div></div>
    <p className="cms-overview-note">{note}</p>
  </Card></li>;
}

export default function DashboardMetrics({ model: m, fleet, apps, navigate }) {
  const selectedCaption = m.selected?.label || m.selected?.model || 'Select a terminal';
  const hasInventory = Boolean(m.inventory?.reportedAt);
  const simReporting = m.devices.filter(device => !device.revoked && Number.isSafeInteger(device.sim_alert_count));
  const storage = !m.selected || m.selected.enabled == null ? 'Not reported' : m.selected.enabled ? 'Restricted' : 'Saved policy';
  return <ol className="cms-overview-grid" aria-label="Security overview">
    <Metric number={1} title="Application Control" icon={AppWindow} category="Applications" tone="amber" caption={selectedCaption} note="Installed apps, permissions and restrictions.">
      <button onClick={() => navigate('apps')}>Installed: <b className="text-blue-500">{hasInventory ? apps.total : '—'}</b></button>
      <button onClick={() => navigate('apps', { appFilter: 'restricted' })}>Restricted: <b className="text-amber-500">{hasInventory ? apps.restricted : '—'}</b></button>
    </Metric>
    <Metric number={2} title="Port Control" icon={Cable} category="Ports" tone="green" caption={`Total terminals: ${fleet.total}`} note={`Reported seal rate: ${fleet.total ? (fleet.sealed / fleet.total * 100).toFixed(1) : '0.0'}%`}>
      <button onClick={() => navigate('devices', { filter: 'sealed' })}>Sealed: <b className="text-emerald-500">{fleet.sealed}</b></button>
      <button onClick={() => navigate('devices', { filter: 'unsealed' })}>Not sealed: <b className="text-rose-500">{fleet.unsealed}</b></button>
    </Metric>
    <Metric number={3} title="Storage Control" icon={HardDrive} category="Storage" tone="violet" caption={selectedCaption} note="File transfer restrictions follow terminal protection.">
      <button onClick={() => navigate('protection')}>File transfers: <b className={m.selected?.enabled ? 'text-emerald-500' : ''}>{storage}</b></button>
    </Metric>
    <Metric number={4} title="SIM Protection" icon={BellRing} category="SIM" tone="amber" caption={`Total terminals: ${fleet.total}`} note={simReporting.length ? `${simReporting.length} terminals reporting SIM alerts.` : 'Waiting for terminal SIM reports.'}>
      <span>Unreviewed alerts: <b className={simAlertCount(m.devices) ? 'text-rose-500' : ''}>{simReporting.length ? simAlertCount(m.devices) : '—'}</b></span>
      <button className="text-blue-500" onClick={() => { const device = m.devices.find(item => !item.revoked && item.sim_alert_count > 0); if (device) m.setSelectedId(device.id); navigate('sim'); }}>Review <ArrowUpRight size={12}/></button>
    </Metric>
    <Metric number={5} title="WiFi & Bluetooth Controls" icon={Wifi} category="Wireless" caption="Network access" note="Nearby networks and open-network protection.">
      <button className="text-blue-500" onClick={() => navigate('wifi')}>Wi-Fi controls <ArrowUpRight size={12}/></button>
      <span>Bluetooth: <b>Not available</b></span>
    </Metric>
    <Metric number={6} title="Remote Patch Management" icon={Download} category="Updates" tone="violet" caption="Application updates" note="Deploy application updates and track their results.">
      <button className="text-blue-500" onClick={() => navigate('deployments')}>Manage updates <ArrowUpRight size={12}/></button>
      <span>OS patches: <b>Not available</b></span>
    </Metric>
    <Metric number={7} title="Device and OS Hardening" icon={ShieldCheck} category="Hardening" tone="green" caption={`Total terminals: ${fleet.total}`} note="Settings, factory reset and debugging restrictions.">
      <button onClick={() => navigate('devices', { filter: 'protected' })}>Protected: <b className="text-emerald-500">{fleet.protected}</b></button>
      <button onClick={() => navigate('devices', { filter: 'maintenance' })}>Maintenance: <b className="text-amber-500">{fleet.maintenance}</b></button>
    </Metric>
    <Metric number={8} title="Device Management" icon={Monitor} category="Enrollment" tone="violet" caption="Android device owner" note={`Management helper: ${fleet.helpers} ready`}>
      <button onClick={() => navigate('devices')}>Managed: <b className="text-emerald-500">{fleet.managed}</b></button>
      <span>Not confirmed: <b>{fleet.total - fleet.managed}</b></span>
    </Metric>
    <Metric number={9} title="Up / Down" icon={Activity} category="Connectivity" caption={`Total terminals: ${fleet.total}`} note="Latest terminal check-in status.">
      <button className="text-emerald-500" onClick={() => navigate('devices', { filter: 'online' })}>↑ {fleet.online} Up</button>
      <button className="text-rose-500" onClick={() => navigate('devices', { filter: 'offline' })}>↓ {fleet.offline} Down</button>
    </Metric>
  </ol>;
}
