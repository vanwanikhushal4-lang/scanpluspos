import React, { useMemo, useState } from 'react';
import { Activity, AppWindow, ArrowUpRight, Cable, ChevronRight, Clock3, Monitor, Shield, ShieldCheck, Wifi } from 'lucide-react';
import { online, protectionLabel, relativeTime, prettyAction, needsAttention } from '../model.mjs';
import { applicationSummary, fleetSummary } from './summary.mjs';
import { Badge, Card, CardHead, Empty, SearchField, StateBadge, dateTime } from './ui.jsx';
import DashboardMetrics from './DashboardMetrics.jsx';
import { createDashboardDemo, demoInventory } from './dashboard-demo.mjs';

function arc(from, to) { const point = value => [110 + 84 * Math.cos(Math.PI + value * Math.PI), 112 + 84 * Math.sin(Math.PI + value * Math.PI)]; const a = point(from), b = point(to); return `M${a.join(' ')} A84 84 0 0 1 ${b.join(' ')}`; }
function SemiDonut({ segments, total, label }) { let start = 0; return <svg className="cms-semidonut" viewBox="0 0 220 145" role="img" aria-label={`${label}: ${segments.map(s => `${s.label} ${s.count}`).join(', ')}`}><path d={arc(0, 1)} stroke="var(--chart-track)" fill="none" strokeWidth="23"/>{segments.filter(s => s.count > 0).map(segment => { const from = start; start += segment.count / total; return <path key={segment.label} d={arc(from, Math.min(1, start))} stroke={segment.color} fill="none" strokeWidth="23"/>; })}<text x="110" y="94" textAnchor="middle" className="cms-chart-number">{total}</text><text x="110" y="115" textAnchor="middle" className="cms-chart-label">{label}</text></svg>; }
export default function SecurityDashboard({ model, navigate, demo, toggleDemo }) {
  return <div className="security-dashboard">
    <div className="cms-dashboard-title">
      <div>
        <h1>POS Security Dashboard</h1>
        <span>{demo ? 'Sample fleet, application activity and protection status' : 'Live overview of your managed payment terminals'}</span>
      </div>
      <div className="cms-dashboard-actions">
        {demo && <Badge tone="blue">Demo data</Badge>}
        {toggleDemo && <button className="button white cms-dashboard-mode" onClick={toggleDemo}>{demo ? 'Show live data' : 'Show demo data'}</button>}
        {!demo && <span className="cms-dashboard-updated"><Clock3 size={12}/>{model.lastRefresh ? `Updated ${relativeTime(new Date(model.lastRefresh).toISOString(), model.now).toLowerCase()}` : model.loading ? 'Connecting' : 'Waiting for a backend refresh'}</span>}
      </div>
    </div>
    {demo ? <DemoDashboard/> : <DashboardContent model={model} navigate={navigate}/>}
  </div>;
}

function DemoDashboard() {
  const [snapshot] = useState(() => createDashboardDemo());
  const [selected, setSelected] = useState(snapshot.devices[0]);
  const inventory = useMemo(() => demoInventory(snapshot.now, selected.demoIndex), [snapshot, selected]);
  return <DashboardContent
    demo
    model={{ ...snapshot, selected, inventory }}
    inspectDevice={setSelected}
  />;
}

function DashboardContent({ model: m, navigate, demo = false, inspectDevice }) {
  const [terminalSearch, setTerminalSearch] = useState('');
  const fleet = fleetSummary(m.devices, m.now), apps = applicationSummary(m.inventory);
  const segments = [{ label: 'Protected', count: fleet.protected, color: '#22c55e' }, { label: 'Maintenance', count: fleet.maintenance, color: '#fbbf24' }, { label: 'Needs attention', count: fleet.attention, color: '#f87171' }, { label: 'Awaiting status', count: fleet.unknown, color: '#9ca3af' }];
  const appSegments = [{ label: 'User apps', count: apps.user, key: 'user', color: '#7094ff' }, { label: 'System apps', count: apps.system, key: 'system', color: '#9db0ff' }, { label: 'Management', count: apps.management, key: 'management', color: '#5b9cf6' }, { label: 'Restricted', count: apps.restricted, key: 'restricted', color: '#f59e0b' }];
  const query = terminalSearch.trim().toLowerCase();
  const matches = m.devices.filter(d => `${d.label} ${d.serial} ${d.model}`.toLowerCase().includes(query));
  const terminals = demo ? matches.slice(0, 8) : matches;
  const openDevice = device => {
    if (demo) inspectDevice(device);
    else {
      m.setSelectedId(device.id);
      navigate('devices');
    }
  };
  const LegendRow = demo ? 'div' : 'button';
  const AppSegment = demo ? 'div' : 'button';
  const coverage = fleet.total ? Math.round(fleet.managed / fleet.total * 100) : 0;
  return <>
    <DashboardMetrics model={m} fleet={fleet} apps={apps} navigate={navigate}/>
    <div className="dashboard-details grid grid-cols-12 gap-3">
      <Card className="col-span-12 xl:col-span-5 min-h-[305px]"><CardHead title="Protection Status"><Badge tone="blue">Total terminals: {fleet.total}</Badge></CardHead><div className="flex flex-wrap gap-x-4 gap-y-1 text-[10px] text-gray-500 dark:text-slate-400">{segments.map(segment => <span key={segment.label}>{segment.label}: <b style={{ color: segment.color }}>{segment.count}</b></span>)}</div><div className="cms-protection-chart"><div className="space-y-2 flex-1 min-w-0">{segments.map(segment => <LegendRow key={segment.label} className="cms-legend-row" onClick={navigate && (() => navigate('devices', { filter: ({ Protected: 'protected', Maintenance: 'maintenance', 'Needs attention': 'attention', 'Awaiting status': 'unknown' })[segment.label] }))}><span className="flex items-center gap-2"><i style={{ background: segment.color }}/>{segment.label}</span><b style={{ color: segment.color }}>{segment.count}</b></LegendRow>)}<div className="cms-attention-note"><b>{fleet.attention + fleet.unknown}</b> terminals require status attention.</div></div><SemiDonut segments={segments} total={fleet.total} label="Total terminals"/></div></Card>
      <Card className="col-span-12 xl:col-span-7 min-h-[305px]"><CardHead title="Application Status" subtitle={`Installed-app distribution · ${m.selected?.label || 'Select a terminal'}`}><Badge tone="blue">Total apps: {m.inventory?.reportedAt ? apps.total : '—'}</Badge></CardHead>{m.inventoryError ? <div className="inline-error" role="alert">{m.inventoryError}</div> : !m.inventory?.reportedAt ? <Empty title="Waiting for inventory" detail="The agent reports the installed applications automatically." icon={AppWindow}/> : <><div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5 mt-7">{appSegments.map(segment => <AppSegment key={segment.key} className="cms-app-segment" style={{ '--segment': segment.color }} onClick={navigate && (() => navigate('apps', { appFilter: segment.key }))}><span>{segment.label}</span><strong>{segment.count}</strong></AppSegment>)}</div><div className="mt-6"><div className="flex items-center justify-between mb-2 text-[10px] text-gray-400"><span className="uppercase tracking-wider text-[9px] font-semibold">Distribution</span><span>{apps.total} total</span></div><div className="flex h-3 overflow-hidden rounded-full bg-gray-100 dark:bg-slate-800" role="img" aria-label={appSegments.map(s => `${s.label} ${s.count}`).join(', ')}>{appSegments.map(segment => <div key={segment.key} style={{ background: segment.color, width: `${apps.total ? segment.count / apps.total * 100 : 0}%` }}/>)}</div></div><p className="mt-4 text-[10px] text-gray-400">Last inventory: {dateTime(m.inventory.reportedAt)}</p></>}</Card>
    </div>
    <div className="grid grid-cols-12 gap-3 items-stretch">
      <Card className="col-span-12 xl:col-span-3"><CardHead title="Recent Activity">{navigate && <button className="text-button" onClick={() => navigate('reports')} aria-label="View all activity"><ArrowUpRight size={15}/></button>}</CardHead>{m.auditError ? <p className="text-xs text-rose-500">{m.auditError}</p> : !m.audit.length ? <Empty title="No activity yet"/> : <div className="cms-activity-list">{m.audit.slice(0, 5).map(event => <div key={event.id}><span className={`cms-activity-dot ${event.action === 'FAILED' ? 'red' : 'blue'}`}><Activity size={12}/></span><div><strong>{prettyAction(event.action)}</strong><p>{event.detail || event.actor}</p><small>{relativeTime(event.event_time, m.now)}</small></div></div>)}</div>}</Card>
      <Card className="col-span-12 xl:col-span-6"><CardHead title="Managed Terminals"><SearchField value={terminalSearch} onChange={setTerminalSearch} label="Search dashboard terminals"/></CardHead><div className="table-scroll"><table className="data-table"><thead><tr><th>Device</th><th>Connectivity</th><th>Protection</th><th>Last Seen</th><th/></tr></thead><tbody>{terminals.map(device => <tr key={device.id} className={demo && device.id === m.selected.id ? 'cms-selected-row' : undefined}><td><button className="cms-table-link" aria-pressed={demo ? device.id === m.selected.id : undefined} onClick={() => openDevice(device)}>{device.label || device.model}<small>{device.serial}</small></button></td><td><Badge tone={online(device, m.now) ? 'green' : 'muted'} dot>{online(device, m.now) ? 'Online' : 'Offline'}</Badge></td><td><Badge tone={device.enabled ? 'green' : needsAttention(device) ? 'red' : 'amber'}>{protectionLabel(device)}</Badge></td><td className="date-cell">{relativeTime(device.last_seen, m.now)}</td><td><button className="icon-button" aria-label={`${demo ? 'Inspect' : 'Manage'} ${device.label || device.model}`} onClick={() => openDevice(device)}><ChevronRight size={15}/></button></td></tr>)}</tbody></table>{demo && matches.length > terminals.length && <p className="cms-demo-table-count">Showing {terminals.length} of {matches.length} sample terminals</p>}{!terminals.length && <Empty title={m.loading ? 'Loading terminals' : 'No matching terminals'} icon={Monitor}/>}</div></Card>
      <Card className="col-span-12 xl:col-span-3"><CardHead title="Management Coverage"/><SemiDonut segments={[{ label: 'Device owner active', count: fleet.managed, color: '#7094ff' }, { label: 'Enrollment required', count: fleet.total - fleet.managed, color: '#e5e7eb' }]} total={fleet.total} label="Device-owner coverage"/><div className="text-center"><strong className="text-[22px] font-bold text-gray-900 dark:text-white">{coverage}%</strong><p className="mt-1 text-[10px] text-gray-400">{fleet.managed} of {fleet.total} terminals managed</p></div></Card>
    </div>
    <div className="grid grid-cols-1 xl:grid-cols-2 gap-3"><Card><CardHead title="Recent App Activity" subtitle={m.selected?.label || 'Selected terminal'}>{navigate && <button className="text-button" onClick={() => navigate('deployments')}>View all <ArrowUpRight size={13}/></button>}</CardHead><div className="table-scroll"><table className="data-table"><thead><tr><th>Action</th><th>Application</th><th>Result</th><th>Requested</th></tr></thead><tbody>{m.appJobs.slice(0, 4).map(job => <tr key={job.id}><td>{job.action.replaceAll('_', ' ')}</td><td>{job.package_name || (job.action === 'REFRESH' ? 'App inventory' : 'Uploaded APK')}</td><td><StateBadge state={job.state}/></td><td className="date-cell">{dateTime(job.created_at)}</td></tr>)}</tbody></table>{!m.appJobs.length && <Empty title="No app commands yet" detail="Completed and pending app operations appear here." icon={AppWindow}/>}</div></Card><Card><CardHead title="Terminal Status" subtitle={m.selected?.label || 'Selected terminal'}>{navigate && <button className="text-button" onClick={() => navigate('protection')}>Manage <ArrowUpRight size={13}/></button>}</CardHead>{m.selected ? <div className="grid grid-cols-2 gap-2.5">{[{ icon: ShieldCheck, title: 'Device Owner', value: m.selected.owner_active == null ? 'Not reported' : m.selected.owner_active ? 'Enrolled' : 'Required', ok: m.selected.owner_active }, { icon: Cable, title: 'Port Control', value: m.selected.usb_sealed == null ? 'Not reported' : m.selected.usb_sealed ? 'Sealed' : 'Not sealed', ok: m.selected.usb_sealed }, { icon: Shield, title: 'Management Helper', value: m.selected.bridge_available == null ? 'Not reported' : m.selected.bridge_available ? 'Ready' : 'Unavailable', ok: m.selected.bridge_available }, { icon: Wifi, title: 'Connection', value: online(m.selected, m.now) ? 'Online' : 'Offline', ok: online(m.selected, m.now) }].map(item => <div className="cms-status-tile" key={item.title}><item.icon size={16}/><span>{item.title}</span><Badge tone={item.ok ? 'green' : 'amber'}>{item.value}</Badge></div>)}</div> : <Empty title="Select a terminal" icon={Monitor}/>}</Card></div>
  </>;
}
