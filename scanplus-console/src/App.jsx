import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Activity, ArrowDownLeft, ArrowUpRight, Check, CheckCheck, ChevronRight, CircleHelp, Clock3, Command, Copy, Cpu, ExternalLink, Fingerprint, History, LayoutGrid, LoaderCircle, LockKeyhole, Monitor, MoreHorizontal, RefreshCw, Search, Server, Shield, ShieldCheck, ShieldOff, Signal, TerminalSquare, Usb, Wifi, X, Zap } from 'lucide-react';
import { api, getDevices, getCommands, getAudit, setProtection } from './api.js';
import AppControl from './AppControl.jsx';
import { online, relativeTime, protectionLabel, commandLabel, prettyAction, matchesDevice, needsAttention } from './model.mjs';

const fullDate = value => value ? new Intl.DateTimeFormat('en-IN', { timeZone: 'Asia/Kolkata', dateStyle: 'medium', timeStyle: 'medium' }).format(new Date(value)) : 'Not reported';
const shortDate = value => value ? new Intl.DateTimeFormat('en-IN', { timeZone: 'Asia/Kolkata', day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }).format(new Date(value)) : '—';
const terminalStates = new Set(['SUCCEEDED', 'FAILED', 'EXPIRED', 'CANCELLED']);
const initials = label => (label || 'Terminal').split(' ').slice(0, 2).map(s => s[0]).join('').toUpperCase();

function Brand() {
  return <div className="brand">
    <span className="brand-lockup"><span className="brand-logo-crop">
      <img className="brand-logo-base" src="/brand/scanplus-logo.png" alt="ScanPlus — Unified Terminal Security & Management" width="2170" height="725" />
      <img className="brand-logo-tagline" src="/brand/scanplus-logo.png" alt="" aria-hidden="true" width="2170" height="725" />
    </span></span>
    <span className="brand-emblem" role="img" aria-label="ScanPlus"><img src="/brand/scanplus-logo.png" alt="" width="2170" height="725" /></span>
  </div>;
}
function Badge({ children, tone = 'muted', dot = false }) {
  return <span className={`badge ${tone}`}>{dot && <i />}{children}</span>;
}
function StatusIcon({ state }) {
  return state === 'SUCCEEDED' ? <CheckCheck size={16} /> : state === 'PENDING' ? <LoaderCircle size={15} className="spin" /> : state === 'FAILED' ? <X size={16} /> : <Clock3 size={15} />;
}
function TerminalArt() {
  return <svg className="terminal-art" viewBox="0 0 110 145" aria-hidden="true"><defs><linearGradient id="body" x2="1" y2="1"><stop stopColor="#dfe8ed"/><stop offset="1" stopColor="#a5b7c3"/></linearGradient></defs><ellipse cx="53" cy="133" rx="33" ry="7" fill="#dce5e9"/><g transform="rotate(-8 55 72)"><rect x="25" y="7" width="60" height="126" rx="14" fill="url(#body)"/><rect x="29" y="11" width="52" height="118" rx="11" fill="#f7fafc"/><path d="M37 20h36v13H37z" fill="#d9e5eb"/><path d="M41 23h28m-28 4h28m-28 4h28" stroke="#a8bdc9" strokeWidth="1.4"/><rect x="34" y="39" width="42" height="65" rx="5" fill="#173b43"/><path d="M50 56h10v11c0 7-5 11-5 11s-5-4-5-11z" fill="#73decb"/><path d="m51 66 3 3 5-6" stroke="#173b43" fill="none" strokeWidth="2"/><rect x="42" y="86" width="26" height="3" rx="1.5" fill="#3e6267"/><rect x="46" y="93" width="18" height="2" rx="1" fill="#3e6267"/><circle cx="55" cy="116" r="5" fill="#d6e3e9"/><path d="M52 116h6" stroke="#7895a3" strokeWidth="1.5"/><path d="M82 57v23" stroke="#97adba" strokeWidth="3" strokeLinecap="round"/></g></svg>;
}
function Modal({ title, children, onClose, busy = false, wide = false }) {
  const ref = useRef();
  useEffect(() => { ref.current.showModal(); const element = ref.current; return () => element.close(); }, []);
  return <dialog ref={ref} className={`modal ${wide ? 'wide' : ''}`} onCancel={event => { event.preventDefault(); if (!busy) onClose(); }} onClick={event => { if (event.target === ref.current && !busy) onClose(); }} aria-labelledby="modal-title"><div className="modal-head"><h2 id="modal-title">{title}</h2><button className="icon-button" aria-label="Close dialog" onClick={onClose} disabled={busy}><X size={19}/></button></div>{children}</dialog>;
}
function Metric({ label, value, icon: Icon, color }) {
  return <div className="metric"><div className={`metric-icon ${color}`}><Icon size={20}/></div><div><span>{label}</span><strong>{value}</strong></div></div>;
}
function CommandTable({ commands, busy, empty = 'No commands yet. Protection changes will appear here.' }) {
  return <div className="table-scroll"><table className="data-table command-table"><thead><tr><th>Command</th><th>Result</th><th>Requested</th><th>Details</th></tr></thead><tbody>{commands.map(command => <tr key={command.id}><td><span className={`table-command-icon ${command.enabled ? 'teal' : 'amber'}`}>{command.enabled ? <ShieldCheck size={16}/> : <ShieldOff size={16}/>}</span><span className="table-command-label">{commandLabel(command)}</span></td><td><Badge tone={command.state === 'SUCCEEDED' ? 'green' : command.state === 'PENDING' ? 'blue' : command.state === 'FAILED' ? 'red' : 'muted'}><StatusIcon state={command.state}/>{({ SUCCEEDED: 'Completed', PENDING: 'Pending', FAILED: 'Failed', EXPIRED: 'Expired', CANCELLED: 'Cancelled' })[command.state] || command.state}</Badge></td><td title={fullDate(command.created_at)} className="date-cell">{shortDate(command.created_at)}</td><td className="detail-cell">{command.detail || (command.state === 'PENDING' ? 'Waiting for the terminal to acknowledge.' : '—')}</td></tr>)}</tbody></table>{!commands.length && <div className="empty-inline">{busy ? <><LoaderCircle size={18} className="spin"/>Loading command history…</> : <><History size={21}/>{empty}</>}</div>}</div>;
}

export default function App() {
  const [view, setView] = useState('devices');
  const [devices, setDevices] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [commands, setCommands] = useState([]);
  const [audit, setAudit] = useState([]);
  const [info, setInfo] = useState(null);
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState('all');
  const [ready, setReady] = useState(false);
  const [loading, setLoading] = useState(true);
  const [commandsLoading, setCommandsLoading] = useState(false);
  const [error, setError] = useState('');
  const [commandError, setCommandError] = useState('');
  const [auditError, setAuditError] = useState('');
  const [now, setNow] = useState(Date.now());
  const [lastRefresh, setLastRefresh] = useState(null);
  const [refreshing, setRefreshing] = useState(false);
  const [dialog, setDialog] = useState(null);
  const [sending, setSending] = useState(false);
  const [pending, setPending] = useState({});
  const [uncertain, setUncertain] = useState(null);
  const [toast, setToast] = useState(null);
  const reads = useRef(false);
  const issued = useRef(false);
  const commandEpoch = useRef(0);
  const selectedRef = useRef(null);
  const lastNotified = useRef(new Set());
  const selected = devices.find(device => device.id === selectedId) || null;
  selectedRef.current = selectedId;

  const refreshDevices = useCallback(async signal => {
    if (reads.current) return;
    reads.current = true;
    try {
      const rows = await getDevices(signal);
      setDevices(rows); setReady(true); setError(''); setLastRefresh(Date.now());
      setSelectedId(id => rows.some(device => device.id === id) ? id : rows.find(device => !device.revoked)?.id || rows[0]?.id || null);
    } catch (failure) {
      if (failure.name !== 'AbortError') { setReady(false); setError(failure.message); }
    } finally { reads.current = false; if (!signal?.aborted) setLoading(false); }
  }, []);
  const refreshCommands = useCallback(async id => {
    if (!id) return;
    const epoch = commandEpoch.current;
    try {
      const rows = await getCommands(id);
      if (selectedRef.current === id && epoch === commandEpoch.current) { setCommands(rows); setCommandError(''); }
      setPending(current => {
        const updated = { ...current };
        const previous = updated[id];
        if (previous) {
          const found = rows.find(command => command.id === previous.id || command.request_id === previous.request_id);
          if (found) updated[id] = found;
        }
        return updated;
      });
      setUncertain(current => current?.deviceId === id && rows.some(command => command.request_id === current.requestId) ? null : current);
    } catch (failure) { if (selectedRef.current === id) setCommandError(failure.message); }
    finally { if (selectedRef.current === id) setCommandsLoading(false); }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    refreshDevices(controller.signal);
    api('/api/console/info', { signal: controller.signal }).then(setInfo).catch(() => {});
    const timer = setInterval(() => refreshDevices(controller.signal), 3000);
    return () => { controller.abort(); clearInterval(timer); };
  }, [refreshDevices]);
  useEffect(() => { const timer = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(timer); }, []);
  useEffect(() => {
    setCommands([]); setCommandError('');
    if (!selectedId || selected?.revoked) return;
    setCommandsLoading(true); refreshCommands(selectedId);
    const timer = setInterval(() => refreshCommands(selectedId), 3000);
    return () => clearInterval(timer);
  }, [selectedId, selected?.revoked, refreshCommands]);
  const refreshAudit = useCallback(async () => {
    try { setAudit(await getAudit()); setAuditError(''); }
    catch (failure) { setAuditError(failure.message); }
  }, []);
  useEffect(() => {
    if (view !== 'audit') return;
    refreshAudit();
    const timer = setInterval(refreshAudit, 5000);
    return () => clearInterval(timer);
  }, [view, refreshAudit]);
  useEffect(() => {
    Object.values(pending).forEach(command => {
      if (terminalStates.has(command.state) && !lastNotified.current.has(command.id)) {
        lastNotified.current.add(command.id);
        setToast({ tone: command.state === 'SUCCEEDED' ? 'success' : 'error', message: command.state === 'SUCCEEDED' ? (command.enabled ? 'Protection enabled. The terminal confirmed the change.' : 'Maintenance enabled. The terminal confirmed the change.') : command.detail || `Command ${command.state.toLowerCase()}.` });
      }
    });
  }, [pending]);
  useEffect(() => { if (!toast) return; const timer = setTimeout(() => setToast(null), 7000); return () => clearTimeout(timer); }, [toast]);

  async function refreshAll() {
    setRefreshing(true);
    try { await Promise.all([refreshDevices(), refreshCommands(selectedId), ...(view === 'audit' ? [refreshAudit()] : [])]); }
    finally { setRefreshing(false); }
  }
  async function submit(change, retry = null) {
    if (issued.current) return;
    issued.current = true; setSending(true);
    const request = retry || { deviceId: change.device.id, requestId: crypto.randomUUID(), enabled: change.enabled, ttlSeconds: 120 };
    try {
      const command = await setProtection(request.deviceId, { requestId: request.requestId, enabled: request.enabled, ttlSeconds: request.ttlSeconds });
      commandEpoch.current++;
      setPending(current => ({ ...current, [request.deviceId]: command }));
      if (selectedRef.current === request.deviceId) setCommands(current => [command, ...current.filter(item => item.id !== command.id)]);
      setUncertain(null); setDialog(null);
      setToast({ tone: 'info', message: 'Command sent. Waiting for the terminal to confirm.' });
      refreshDevices(); refreshCommands(request.deviceId);
    } catch (failure) {
      setDialog(null);
      if (!failure.status || failure.status >= 500) setUncertain({ ...request, message: failure.message });
      else setToast({ tone: 'error', message: failure.message });
    } finally { issued.current = false; setSending(false); }
  }
  const selectedPending = pending[selectedId]?.state === 'PENDING' ? pending[selectedId] : commands.find(command => command.state === 'PENDING');
  const actionBusy = sending || Boolean(selectedPending) || uncertain?.deviceId === selectedId;
  const canControl = selected && !selected.revoked && selected.owner_active && selected.bridge_available && ready;
  const visibleDevices = devices.filter(device => matchesDevice(device, search) && (filter === 'all' || filter === 'online' && online(device, now) || filter === 'maintenance' && device.enabled === false && !device.revoked && !needsAttention(device)));
  const commandRows = view === 'commands' ? commands : commands.slice(0, 4);
  const notice = selectedPending ? Math.max(0, Math.ceil((Date.parse(selectedPending.expires_at) - now) / 1000)) : 0;

  return <div className="app-shell">
    <aside className="sidebar">
      <Brand />
      <div className="workspace"><span className="workspace-icon"><LayoutGrid size={16}/></span><div><strong>Device workspace</strong><span>Administrator console</span></div><span className="workspace-dot"/></div>
      <div className="nav-label">MANAGEMENT</div>
      <nav aria-label="Main navigation">{[{ id: 'devices', label: 'Devices', icon: Monitor }, { id: 'apps', label: 'App control', icon: LayoutGrid }, { id: 'commands', label: 'Command history', icon: History }, { id: 'audit', label: 'Audit log', icon: Activity }].map(item => <button key={item.id} className={`nav-item ${view === item.id ? 'active' : ''}`} aria-label={item.label} onClick={() => setView(item.id)} aria-current={view === item.id ? 'page' : undefined}><item.icon size={18}/><span>{item.label}</span>{item.id === 'devices' && <small>{devices.length}</small>}</button>)}</nav>
      <div className="sidebar-bottom"><div className="sidebar-note"><span className="small-shield"><ShieldCheck size={18}/></span><strong>Local administration</strong><p>Protection managed through your dedicated SCANPLUS backend.</p></div><button className="nav-item connection-link" onClick={() => setDialog({ type: 'connection' })}><Server size={17}/><span>Backend connection</span><ChevronRight size={15}/></button><div className="profile"><span>AD</span><div><strong>Administrator</strong><small>Local workspace</small></div><LockKeyhole size={14}/></div></div>
    </aside>
    <main className="main">
      <header className="topbar"><div className="breadcrumb">Workspace <ChevronRight size={13}/> <span>{view === 'devices' ? 'Device management' : view === 'apps' ? 'App control' : view === 'commands' ? 'Command history' : 'Audit log'}</span></div><div className={`connection-state ${ready ? '' : 'disconnected'}`}><i/>{ready ? 'Backend connected' : loading ? 'Connecting to backend' : 'Backend disconnected'}</div></header>
      <div className="page-content">
        <div className="page-heading"><div><div className="eyebrow">SCANPLUS CONTROL CENTER</div><h1>{view === 'devices' ? 'Devices' : view === 'apps' ? 'App control' : view === 'commands' ? 'Command history' : 'Audit log'}</h1><p>{view === 'devices' ? 'Monitor your terminals and manage protection remotely.' : view === 'apps' ? 'Manage applications, permissions and deployments on your terminal.' : view === 'commands' ? 'Track every request through to the terminal’s response.' : 'A record of enrollment, administrator actions and device results.'}</p></div><button className="button white" onClick={refreshAll} disabled={refreshing}><RefreshCw size={15} className={refreshing ? 'spin' : ''}/>{refreshing ? 'Refreshing' : 'Refresh'}</button></div>
        {error && <div className="error-banner" role="alert"><Server size={19}/><div><strong>Backend connection needs attention</strong><span>{error} Displayed device information may be out of date.</span></div><button onClick={refreshAll}>Retry</button></div>}
        <section className="metrics" aria-label="Fleet summary"><Metric label="Enrolled devices" value={devices.length} icon={Monitor} color="blue"/><Metric label="Online now" value={devices.filter(device => online(device, now)).length} icon={Signal} color="green"/><Metric label="Protected" value={devices.filter(device => device.enabled === true && !device.revoked).length} icon={ShieldCheck} color="teal"/><Metric label="In maintenance" value={devices.filter(device => device.enabled === false && !device.revoked && !needsAttention(device)).length} icon={ShieldOff} color="amber"/></section>
        {view === 'apps' ? <AppControl key={selectedId || 'none'} devices={devices} selectedId={selectedId} onSelect={setSelectedId} onManageDevice={() => setView('devices')} now={now}/> : view !== 'audit' ? <>
          <div className="device-layout">
            <section className="card fleet-card"><div className="card-heading"><div><h2>Your terminals <span>{devices.length}</span></h2><p>Select a device to manage.</p></div><Monitor size={18} className="muted-icon"/></div><div className="search-field"><Search size={16}/><input aria-label="Search devices" value={search} onChange={event => setSearch(event.target.value)} placeholder="Search name or serial…"/>{search && <button aria-label="Clear search" onClick={() => setSearch('')}><X size={14}/></button>}</div><div className="filter-tabs" role="group" aria-label="Filter devices">{[['all', 'All devices'], ['online', 'Online'], ['maintenance', 'Maintenance']].map(([value, label]) => <button key={value} onClick={() => setFilter(value)} className={filter === value ? 'selected' : ''} aria-pressed={filter === value}>{label}</button>)}</div><div className="device-list">{visibleDevices.map(device => <button key={device.id} className={`device-row ${selectedId === device.id ? 'selected' : ''}`} onClick={() => setSelectedId(device.id)} aria-pressed={selectedId === device.id}><span className="device-symbol"><TerminalSquare size={23}/><i className={online(device, now) ? 'online' : ''}/></span><div className="device-row-main"><strong>{device.label || device.model}</strong><span>{device.model} <b>·</b> {device.serial}</span><Badge tone={device.enabled === true ? 'green' : device.revoked ? 'muted' : 'amber'}>{protectionLabel(device)}</Badge></div><ChevronRight size={16}/></button>)}{!visibleDevices.length && <div className="device-empty">{loading ? <LoaderCircle size={24} className="spin"/> : <Monitor size={28}/>}<strong>{loading ? 'Loading terminals' : search || filter !== 'all' ? 'No matching terminals' : 'No terminals enrolled'}</strong><p>{loading ? 'Connecting to your backend…' : search || filter !== 'all' ? 'Try a different search or filter.' : 'Enroll a terminal with the backend, then refresh this page.'}</p>{(search || filter !== 'all') && <button className="text-button" onClick={() => { setSearch(''); setFilter('all'); }}>Clear filters</button>}</div>}</div><div className="fleet-footer"><span><i className={ready ? 'online' : ''}/>{ready ? 'Automatic refresh is on' : 'Waiting for backend'}</span><small>Every 3 seconds</small></div></section>
            {selected ? <section className="device-detail" aria-label="Selected device">
              <div className="card terminal-card"><div className="terminal-top"><div><div className="section-eyebrow">SELECTED TERMINAL</div><div className="terminal-name"><h2>{selected.label || selected.model}</h2><Badge tone={online(selected, now) ? 'green' : 'muted'} dot>{selected.revoked ? 'Revoked' : online(selected, now) ? 'Online' : 'Offline'}</Badge></div><p>{selected.model} <span>Serial {selected.serial}</span></p><div className="terminal-meta"><span><Cpu size={13}/>{selected.version}</span><span title={fullDate(selected.last_seen)}><Clock3 size={13}/>Seen {relativeTime(selected.last_seen, now).toLowerCase()}</span></div></div><TerminalArt/></div><div className="health-grid">{[{ icon: Fingerprint, label: 'Device management', value: selected.owner_active == null ? 'Not reported' : selected.owner_active ? 'Enrolled' : 'Not enrolled', good: selected.owner_active }, { icon: Usb, label: 'USB data', value: selected.usb_sealed == null ? 'Not reported' : selected.usb_sealed ? 'Blocked' : 'Not sealed', good: selected.usb_sealed }, { icon: Zap, label: 'USB helper', value: selected.bridge_available == null ? 'Not reported' : selected.bridge_available ? 'Ready' : 'Unavailable', good: selected.bridge_available }].map(item => <div key={item.label}><span><item.icon size={14}/>{item.label}</span><strong><i className={item.good ? 'good' : 'neutral'}/>{item.value}</strong></div>)}</div></div>
              <div className={`card protection-card ${selected.enabled === true ? 'is-protected' : ''}`}><div className="protection-head"><span className={`protection-icon ${selected.enabled === true ? 'teal' : 'amber'}`}>{selected.enabled === true ? <ShieldCheck size={25}/> : <ShieldOff size={25}/>}</span><div><div className="section-eyebrow">TERMINAL PROTECTION</div><h2>{selected.enabled === true ? 'Protection is enabled' : needsAttention(selected) ? 'Protection needs attention' : selected.enabled === false ? 'Maintenance mode' : 'Waiting for device status'}</h2></div><Badge tone={selected.enabled === true ? 'green' : 'amber'}>{selected.enabled === true ? 'Protected' : needsAttention(selected) ? 'Attention' : selected.enabled === false ? 'Disabled' : 'Unknown'}</Badge></div><p>{selected.enabled === true ? 'USB access and new app installations are blocked. The agent stays connected over Wi-Fi.' : needsAttention(selected) ? 'Review the latest status below. You can retry protection or enter maintenance to restore service access.' : selected.enabled === false ? 'Enable protection to block USB access and new app installations on this terminal.' : 'The terminal will report its protection state after its first check-in.'}</p><div className="protection-actions"><button className="button primary" disabled={!canControl || actionBusy || selected.enabled === true} onClick={() => setDialog({ type: 'protection', device: selected, enabled: true })}><ShieldCheck size={16}/>Enable protection</button><button className="button white" disabled={!canControl || actionBusy || selected.enabled === false && !needsAttention(selected)} onClick={() => setDialog({ type: 'protection', device: selected, enabled: false })}><LockKeyhole size={15}/>Enter maintenance</button></div>{!canControl && <div className="action-note">{!ready ? 'Reconnect the backend to send a command.' : selected.revoked ? 'This device’s remote access has been revoked.' : !selected.owner_active ? 'Administrator enrollment is required before protection can be changed.' : 'The USB helper must be available before protection can be changed.'}</div>}<div className="reported-status"><Check size={13}/><span>{selected.detail || 'Waiting for the first status report.'}</span></div></div>
              {selectedPending && <div className="pending-panel" role="status"><LoaderCircle size={20} className="spin"/><div><strong>{selectedPending.enabled ? 'Enabling protection' : 'Entering maintenance'}</strong><span>{notice > 0 ? `Waiting for the terminal · expires in ${Math.floor(notice / 60)}:${String(notice % 60).padStart(2, '0')}` : 'Command deadline reached · refreshing the result'}</span></div><Badge tone="blue">Pending</Badge></div>}
              {uncertain?.deviceId === selectedId && <div className="uncertain-panel" role="alert"><strong>Delivery is not yet confirmed</strong><p>{uncertain.message} The terminal’s current protection state is unchanged in this view until confirmed.</p><button className="button white" disabled={sending || !ready} onClick={() => submit(null, uncertain)}><RefreshCw size={14}/>Retry the same request</button></div>}
            </section> : <section className="card no-selection"><Shield size={44}/><h2>Your device controls will appear here</h2><p>Select an enrolled terminal to see its status and manage protection.</p></section>}
          </div>
          <section className="card history-card"><div className="card-heading"><div><h2>{view === 'commands' ? 'Command history' : 'Recent commands'}</h2><p>{selected ? `Requests for ${selected.label || selected.model}. Times shown in IST.` : 'Select a terminal to view command history.'}</p></div>{view === 'devices' && <button className="text-button" onClick={() => setView('commands')}>View all <ArrowUpRight size={14}/></button>}</div>{commandError && <div className="inline-error" role="alert">{commandError}<button onClick={() => refreshCommands(selectedId)}>Retry</button></div>}<CommandTable commands={commandRows} busy={commandsLoading}/></section>
        </> : <section className="card audit-card"><div className="card-heading"><div><h2>Activity log</h2><p>Latest 200 events from your backend. Times shown in IST.</p></div><Badge tone="muted"><Activity size={13}/>Administrator & devices</Badge></div>{auditError && <div className="inline-error" role="alert">{auditError}</div>}<div className="table-scroll"><table className="data-table audit-table"><thead><tr><th>Event</th><th>Initiated by</th><th>Device</th><th>Time</th><th>Details</th></tr></thead><tbody>{audit.map(event => <tr key={event.id}><td><span className={`audit-icon ${event.action === 'SUCCEEDED' ? 'teal' : event.action === 'FAILED' ? 'red' : 'blue'}`}><Activity size={15}/></span>{prettyAction(event.action)}</td><td><Badge tone="muted">{event.actor === 'administrator' ? 'Administrator' : event.actor === 'device' ? 'Device' : event.actor}</Badge></td><td>{devices.find(device => device.id === event.device_id)?.label || (event.device_id ? event.device_id.slice(0, 8) : 'Workspace')}</td><td className="date-cell" title={fullDate(event.event_time)}>{shortDate(event.event_time)}</td><td className="detail-cell">{event.detail}</td></tr>)}</tbody></table>{!audit.length && <div className="empty-inline"><Activity size={20}/>{auditError ? 'The activity log is unavailable.' : 'No activity recorded yet.'}</div>}</div></section>}
        <footer className="page-footer"><span><ShieldCheck size={13}/>SCANPLUS UNIFIED POS SECURITY</span><span>{lastRefresh ? `Updated ${relativeTime(new Date(lastRefresh).toISOString(), now).toLowerCase()}` : 'Waiting for backend'} <b>·</b> Local administrator</span></footer>
      </div>
    </main>
    {dialog?.type === 'protection' && <Modal title={dialog.enabled ? 'Enable terminal protection?' : 'Enter maintenance mode?'} busy={sending} onClose={() => setDialog(null)}><div className={`dialog-symbol ${dialog.enabled ? 'teal' : 'amber'}`}>{dialog.enabled ? <ShieldCheck size={28}/> : <ShieldOff size={28}/>}</div><p className="dialog-summary">{dialog.enabled ? 'This will enable protection on' : 'This will restore the previous service settings on'} <strong>{dialog.device.label || dialog.device.model}</strong>.</p><div className="dialog-device"><TerminalSquare size={18}/><span>{dialog.device.model}</span><code>{dialog.device.serial}</code></div><ul className="effect-list">{(dialog.enabled ? ['USB data and debugging will disconnect.', 'New app installations and updates will be blocked.', 'The agent will remain connected over Wi-Fi.'] : ['Previous installation and management restrictions will be restored.', 'The USB helper will request restoration of the saved USB configuration.', 'Enable protection again after servicing is complete.']).map(effect => <li key={effect}><Check size={14}/>{effect}</li>)}</ul>{!online(devices.find(device => device.id === dialog.device.id) || dialog.device, now) && <p className="dialog-offline">This terminal is offline. The request will expire after two minutes if it does not reconnect.</p>}<div className="modal-actions"><button className="button white" onClick={() => setDialog(null)} disabled={sending}>Cancel</button><button className="button primary" disabled={sending} onClick={() => submit(dialog)}>{sending ? <LoaderCircle size={16} className="spin"/> : dialog.enabled ? <ShieldCheck size={16}/> : <LockKeyhole size={16}/>} {sending ? 'Sending command' : dialog.enabled ? 'Confirm protection' : 'Confirm maintenance'}</button></div></Modal>}
    {dialog?.type === 'connection' && <Modal title="Backend connection" onClose={() => setDialog(null)}><div className="connection-overview"><span className="dialog-symbol blue"><Server size={26}/></span><div><strong>{ready ? 'Connected to SCANPLUS' : 'Backend needs attention'}</strong><p>Your console uses the dedicated backend configured on this Mac.</p></div></div><dl className="connection-details"><div><dt>Backend address</dt><dd>{info?.backend || 'Loading connection information…'}</dd></div><div><dt>Console access</dt><dd>This Mac only</dd></div><div><dt>Status refresh</dt><dd>Every 3 seconds</dd></div><div><dt>Device connection</dt><dd>Outbound HTTPS over Wi-Fi</dd></div></dl><p className="connection-help">Keep this Mac and the backend running to manage devices. Administrator credentials stay in the console server and are not stored in your browser.</p><div className="modal-actions"><button className="button primary" onClick={() => { refreshAll(); setDialog(null); }}>Done</button></div></Modal>}
    {toast && <div className={`toast ${toast.tone}`} role={toast.tone === 'error' ? 'alert' : 'status'}>{toast.tone === 'success' ? <CheckCheck size={20}/> : toast.tone === 'error' ? <CircleHelp size={20}/> : <Command size={20}/>}<span>{toast.message}</span><button aria-label="Dismiss notification" onClick={() => setToast(null)}><X size={16}/></button></div>}
  </div>;
}
