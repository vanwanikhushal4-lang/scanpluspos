import React, { useEffect, useRef, useState } from 'react';
import { startPolling } from './polling.mjs';
import { matchesAppFilter } from './cms/summary.mjs';
import { AppWindow, Check, Clock3, Eye, EyeOff, FileUp, LoaderCircle, LockKeyhole, Package, Play, RefreshCw, Search, Shield, Square, Trash2, UnlockKeyhole, X } from 'lucide-react';
import { appCommand, getApps, getAppCommands, uploadApk } from './api.js';
import { online, relativeTime } from './model.mjs';

const actionNames = { REFRESH: 'Refresh inventory', LAUNCH: 'Open app', FORCE_STOP: 'Force stop', SUSPEND: 'Suspend app', RESUME: 'Resume app', HIDE: 'Hide app', SHOW: 'Show app', BLOCK_UNINSTALL: 'Block uninstall', ALLOW_UNINSTALL: 'Allow uninstall', GRANT_PERMISSION: 'Allow permission', DENY_PERMISSION: 'Deny permission', RESET_PERMISSION: 'Reset permission', INSTALL: 'Install / update APK', UNINSTALL: 'Uninstall app' };
const date = value => value ? new Date(value).toLocaleString('en-IN', { timeZone: 'Asia/Kolkata', dateStyle: 'medium', timeStyle: 'short' }) : 'Not reported';
const terminal = new Set(['SUCCEEDED', 'FAILED', 'EXPIRED', 'CANCELLED']);
const bytes = value => value >= 1048576 ? `${(value / 1048576).toFixed(1)} MiB` : `${Math.ceil(value / 1024)} KiB`;

function Confirm({ operation, busy, onClose, onConfirm }) {
  const ref = useRef(); const [accept, setAccept] = useState(false);
  useEffect(() => { ref.current.showModal(); const node = ref.current; return () => node.close(); }, []);
  return <dialog ref={ref} className="modal app-confirm" aria-labelledby="app-confirm-title" onCancel={event => { event.preventDefault(); if (!busy) onClose(); }}>
    <div className="modal-head"><h2 id="app-confirm-title">{actionNames[operation.action]}?</h2><button className="icon-button" aria-label="Close app action" disabled={busy} onClick={onClose}><X size={18}/></button></div>
    <p className="dialog-summary">{operation.action === 'INSTALL' ? `Install the uploaded APK on ${operation.device.label}. The existing application is updated when its package and signing certificate match.` : <>{actionNames[operation.action]} for <strong>{operation.app.label}</strong> on {operation.device.label}.</>}</p>
    {operation.action === 'INSTALL' && <div className="app-install-review"><strong>{operation.artifactName}</strong><code>{operation.sha256}</code></div>}
    {operation.app && <div className="dialog-device"><Package size={18}/><code>{operation.app.packageName}</code></div>}
    {operation.permission && <p className="app-permission-name">{operation.permission}</p>}
    {operation.action === 'UNINSTALL' && <label className="app-delete-confirm"><input type="checkbox" checked={accept} onChange={event => setAccept(event.target.checked)}/>Remove this application and its local data.</label>}
    {operation.action === 'SUSPEND' && <p className="app-action-hint">The app cannot run until you resume it. Its data stays on the terminal.</p>}
    {operation.action === 'HIDE' && <p className="app-action-hint">The app is hidden from the terminal’s user. You can show it again here.</p>}
    <div className="modal-actions"><button className="button white" disabled={busy} onClick={onClose}>Cancel</button><button className="button primary" disabled={busy || operation.action === 'UNINSTALL' && !accept} onClick={onConfirm}>{busy ? <LoaderCircle size={15} className="spin"/> : <Check size={15}/>} {busy ? 'Sending' : 'Confirm'}</button></div>
  </dialog>;
}

export default function AppControl({ devices, selectedId, onSelect, onManageDevice, now, initialFilter = 'user', focus = 'all' }) {
  const [inventory, setInventory] = useState(null); const [history, setHistory] = useState([]);
  const [packageName, setPackageName] = useState(null); const [query, setQuery] = useState(''); const [filter, setFilter] = useState(initialFilter || 'user');
  const [error, setError] = useState(''); const [notice, setNotice] = useState(''); const [noticeTone, setNoticeTone] = useState('success'); const [dialog, setDialog] = useState(null);
  const [lastRequest, setLastRequest] = useState(null); const [busy, setBusy] = useState(false); const [uncertain, setUncertain] = useState(null); const [file, setFile] = useState(null); const [artifact, setArtifact] = useState(null); const [uploading, setUploading] = useState(false);
  const sending = useRef(false); const refresh = useRef(null);
  const device = devices.find(item => item.id === selectedId); const apps = inventory?.apps || [];
  const selected = apps.find(item => item.packageName === packageName);
  const version = /^(\d+)\.(\d+)\./.exec(device?.version || '');
  const supported = Boolean(version && (+version[1] > 3 || +version[1] === 3 && +version[2] >= 1));
  const connected = online(device, now); const available = supported && connected && !device.revoked && device.owner_active;
  const mutating = available && selected && !selected.critical && !selected.systemApp;
  const activeJobs = history.filter(job => !terminal.has(job.state));
  const matching = apps.filter(app => (!query || `${app.label} ${app.packageName}`.toLowerCase().includes(query.toLowerCase())) && matchesAppFilter(app, filter));

  useEffect(() => {
    setPackageName(current => matching.some(app => app.packageName === current) ? current : matching[0]?.packageName || null);
  }, [inventory, query, filter]);

  useEffect(() => {
    const controller = new AbortController(); let reading = false;
    const load = async () => {
      if (!selectedId || reading) return; reading = true;
      try {
        const [data, commands] = await Promise.all([getApps(selectedId, controller.signal), getAppCommands(selectedId, controller.signal)]);
        if (controller.signal.aborted) return;
        setInventory(data); setHistory(commands); setError('');
        setPackageName(current => data.apps.some(app => app.packageName === current) ? current : data.apps.find(app => !app.systemApp && !app.critical)?.packageName || data.apps[0]?.packageName || null);
        setUncertain(current => current && commands.some(job => job.request_id === current.requestId) ? null : current);
      } catch (failure) { if (failure.name !== 'AbortError' && !controller.signal.aborted) setError(failure.message); return false; }
      finally { reading = false; }
    };
    const poll = startPolling(load);
    refresh.current = poll.refresh;
    return () => { controller.abort(); poll.stop(); };
  }, [selectedId]);

  useEffect(() => { const job = history.find(item => item.request_id === lastRequest); if (job && terminal.has(job.state)) { setNotice(`${actionNames[job.action]} ${job.state.toLowerCase()}. ${job.detail || ''}`); setNoticeTone(job.state === 'SUCCEEDED' ? 'success' : 'error'); setLastRequest(null); } }, [history, lastRequest]);

  async function send(operation, retry = null) {
    if (sending.current) return; sending.current = true; setBusy(true); setError('');
    const request = retry || { requestId: crypto.randomUUID(), action: operation.action, ttlSeconds: operation.action === 'INSTALL' ? 600 : 180, ...(operation.app ? { packageName: operation.app.packageName } : {}), ...(operation.permission ? { permission: operation.permission } : {}), ...(operation.artifactId ? { artifactId: operation.artifactId } : {}) };
    try { const command = await appCommand(selectedId, request); setHistory(current => [command, ...current.filter(item => item.id !== command.id)]); setUncertain(null); setLastRequest(request.requestId); setNoticeTone('info'); setNotice(`${actionNames[request.action]} queued. Waiting for the terminal’s result.`); setDialog(null); refresh.current?.(); }
    catch (failure) { setDialog(null); setError(failure.message); if (!failure.status || failure.status >= 500) setUncertain(request); }
    finally { sending.current = false; setBusy(false); }
  }
  function confirm(action, permission = null) { setDialog({ action, app: selected, permission, device }); }
  async function upload() {
    if (!file) return; setUploading(true); setError('');
    try { if (!file.name.toLowerCase().endsWith('.apk') || file.size < 1 || file.size > 64 * 1048576) throw new Error('Choose a base APK of up to 64 MiB.'); setArtifact(await uploadApk(file)); setNoticeTone('success'); setNotice('APK uploaded and checksummed. Choose Install on terminal to deploy it.'); }
    catch (failure) { setError(failure.message); }
    finally { setUploading(false); }
  }

  return <div className="app-control">
    <div className="app-toolbar card"><div className="app-device-picker"><label htmlFor="app-terminal">MANAGED TERMINAL</label><select id="app-terminal" value={selectedId || ''} onChange={event => onSelect(event.target.value)}>{!devices.length && <option value="">No enrolled terminals</option>}{devices.filter(item => !item.revoked).map(item => <option key={item.id} value={item.id}>{item.label} · {item.serial}</option>)}</select></div><div className="app-inventory-time"><span>{apps.length} installed apps</span><small>{inventory?.reportedAt ? `Reported ${relativeTime(inventory.reportedAt, now).toLowerCase()}` : 'Waiting for agent inventory'}</small></div><button className="button white" disabled={!available || busy || Boolean(uncertain) || activeJobs.some(job => job.action === 'REFRESH')} onClick={() => send({ action: 'REFRESH' })}><RefreshCw size={14}/>Refresh from terminal</button></div>
    {!supported && device && <div className="app-note">App control requires SCANPLUS agent 3.1 or later. This terminal reports {device.version}.</div>}
    {supported && !connected && <div className="app-note">The terminal is offline. Its last reported inventory remains visible.</div>}
    {error && <div className="inline-error app-message" role="alert">{error}<button onClick={() => refresh.current?.()}>Refresh</button></div>}
    {notice && <div className={`app-notice ${noticeTone}`} role={noticeTone === 'error' ? 'alert' : 'status'}>{noticeTone === 'error' ? <X size={14}/> : <Check size={14}/>}{notice}<button className="icon-button" aria-label="Dismiss app notice" onClick={() => setNotice('')}><X size={13}/></button></div>}
    {uncertain && <div className="app-note">Delivery is not confirmed. <button className="text-button" disabled={busy} onClick={() => send(null, uncertain)}>Retry the same request</button></div>}
    <div className={`app-control-grid ${focus === 'deployments' ? 'deployment-only' : ''}`}>
      {focus !== 'deployments' && <section className="card app-list-panel"><div className="card-heading"><div><h2>Applications <span>{apps.length}</span></h2><p>Inventory reported by the terminal.</p></div><AppWindow size={19}/></div><div className="search-field"><Search size={15}/><input aria-label="Search applications" placeholder="App name or package…" value={query} onChange={event => setQuery(event.target.value)}/></div><div className="filter-tabs" role="group" aria-label="Filter applications">{[['user', 'User'], ['system', 'System'], ['restricted', 'Restricted'], ['management', 'Managed'], ['all', 'All']].map(([id, label]) => <button key={id} className={filter === id ? 'selected' : ''} aria-pressed={filter === id} onClick={() => setFilter(id)}>{label}</button>)}</div><div className="application-list">{matching.map(app => <button key={app.packageName} className={`application-row ${packageName === app.packageName ? 'selected' : ''}`} aria-pressed={packageName === app.packageName} onClick={() => setPackageName(app.packageName)}><span className={`app-monogram ${app.systemApp ? 'system' : ''}`}>{app.label.slice(0, 1).toUpperCase()}</span><span><strong>{app.label}</strong><small>{app.packageName}</small><em>{app.critical ? 'Protected core app' : app.hidden ? 'Hidden' : app.suspended ? 'Suspended' : app.systemApp ? 'System app' : `v${app.versionName || app.versionCode}`}</em></span>{app.uninstallBlocked && <LockKeyhole size={13}/>}</button>)}{!matching.length && <div className="device-empty"><Package size={26}/><strong>{inventory?.reportedAt ? 'No matching apps' : 'Waiting for inventory'}</strong><p>{inventory?.reportedAt ? 'Try another search or filter.' : 'The upgraded agent reports installed apps automatically.'}</p></div>}</div></section>}
      <div className="app-detail-column">{focus !== 'deployments' && (selected ? <>
        <section className="card app-detail-card"><div className="app-detail-heading"><span className="app-monogram large">{selected.label.slice(0, 1).toUpperCase()}</span><div><h2>{selected.label}</h2><code>{selected.packageName}</code><div className="app-state-tags">{[selected.systemApp ? 'System app' : 'User app', selected.hidden && 'Hidden', selected.suspended && 'Suspended', selected.uninstallBlocked && 'Uninstall blocked', !selected.enabled && 'Disabled'].filter(Boolean).map(label => <span key={label}>{label}</span>)}</div></div></div><dl className="app-details"><div><dt>Version</dt><dd>{selected.versionName || '—'} <small>({selected.versionCode})</small></dd></div><div><dt>Installed</dt><dd>{date(selected.firstInstallTime)}</dd></div><div><dt>Last updated</dt><dd>{date(selected.lastUpdateTime)}</dd></div><div><dt>Process</dt><dd>{selected.stopped ? 'Stopped' : 'May run in background'}</dd></div></dl>{selected.critical || selected.systemApp ? <p className="app-note">Core and system apps have restricted controls to keep the terminal operational.</p> : null}<div className="app-action-grid">
          <button className="button white" disabled={!available || selected.critical || !selected.launchable || selected.hidden || selected.suspended || busy || Boolean(uncertain)} onClick={() => confirm('LAUNCH')}><Play size={14}/>Open app</button>
          <button className="button white" disabled={!mutating || !inventory?.canForceStop || busy || Boolean(uncertain)} onClick={() => confirm('FORCE_STOP')}><Square size={13}/>Force stop</button>
          <button className="button white" disabled={!mutating || busy || Boolean(uncertain)} onClick={() => confirm(selected.suspended ? 'RESUME' : 'SUSPEND')}><Shield size={14}/>{selected.suspended ? 'Resume app' : 'Suspend app'}</button>
          <button className="button white" disabled={!mutating || busy || Boolean(uncertain)} onClick={() => confirm(selected.hidden ? 'SHOW' : 'HIDE')}>{selected.hidden ? <Eye size={14}/> : <EyeOff size={14}/>}{selected.hidden ? 'Show app' : 'Hide app'}</button>
          <button className="button white" disabled={!mutating || busy || Boolean(uncertain)} onClick={() => confirm(selected.uninstallBlocked ? 'ALLOW_UNINSTALL' : 'BLOCK_UNINSTALL')}>{selected.uninstallBlocked ? <UnlockKeyhole size={14}/> : <LockKeyhole size={14}/>}{selected.uninstallBlocked ? 'Allow uninstall' : 'Block uninstall'}</button>
          <button className="button white danger" disabled={!mutating || device.enabled !== false || selected.uninstallBlocked || busy || Boolean(uncertain)} onClick={() => confirm('UNINSTALL')}><Trash2 size={14}/>Uninstall app</button>
        </div>{device?.enabled && <p className="app-action-hint">Installation and removal require <button className="text-button" onClick={onManageDevice}>maintenance mode</button>. Other app policies remain available.</p>}{!inventory?.canForceStop && <p className="app-action-hint">Force stop needs the updated local management helper.</p>}</section>
        <section className="card app-permissions"><div className="card-heading"><div><h2>Runtime permissions</h2><p>Manage the permissions requested by this application.</p></div></div>{selected.permissions?.length ? selected.permissions.map(permission => <div className="app-permission-row" key={permission.name}><div><strong>{permission.name.replace('android.permission.', '').replaceAll('_', ' ').toLowerCase()}</strong><small>{permission.operationBlocked ? 'Operation blocked' : ['UNVERIFIED', 'NOT_ENFORCED'].includes(permission.enforcement) ? 'Policy needs attention' : permission.granted ? 'Granted' : 'Not granted'} · {permission.policy === 1 ? 'Allowed by policy' : permission.policy === 2 ? 'Denied by policy' : 'Default policy'}</small></div><select aria-label={`Policy for ${permission.name}`} value={permission.policy} disabled={!mutating || busy || Boolean(uncertain)} onChange={event => confirm(({ 0: 'RESET_PERMISSION', 1: 'GRANT_PERMISSION', 2: 'DENY_PERMISSION' })[event.target.value], permission.name)}><option value="0">Default</option><option value="1">Allow</option><option value="2">Deny</option></select></div>) : <div className="empty-inline">No manageable runtime permissions.</div>}</section>
      </> : <section className="card no-selection"><AppWindow size={36}/><h2>Select an application</h2><p>Inspect its version, visibility, restrictions and permissions.</p></section>)}
      <section className="card app-upload"><div className="card-heading"><div><h2>Deploy an APK</h2><p>Install a base APK or update a matching installed app.</p></div><FileUp size={18}/></div><div className="app-upload-body"><label className="app-file-picker"><input aria-label="Choose APK file" type="file" accept=".apk,application/vnd.android.package-archive" disabled={uploading || busy} onChange={event => { setFile(event.target.files[0] || null); setArtifact(null); }}/><span>{file ? `${file.name} · ${bytes(file.size)}` : 'Choose a base .apk · up to 64 MiB'}</span></label><div className="app-upload-actions"><button className="button white" disabled={!file || uploading || Boolean(artifact)} onClick={upload}>{uploading ? <LoaderCircle size={14} className="spin"/> : <FileUp size={14}/>} {uploading ? 'Uploading APK' : artifact ? 'Uploaded' : 'Upload APK'}</button><button className="button primary" disabled={!artifact || !available || device?.enabled !== false || busy || Boolean(uncertain) || activeJobs.some(job => job.action === 'INSTALL')} onClick={() => setDialog({ action: 'INSTALL', device, artifactId: artifact.id, artifactName: artifact.filename, sha256: artifact.sha256 })}><Package size={14}/>Install on terminal</button></div>{artifact && <div className="app-checksum"><strong>SHA-256</strong><code>{artifact.sha256}</code></div>}<p className="app-action-hint">The agent verifies the download checksum and Android compatibility. Firmware signing rules still apply. For split APKs, upload a compatible standalone APK.</p>{device?.enabled !== false && <p className="app-action-hint">Enter maintenance mode before deploying an APK.</p>}</div></section>
      </div>
    </div>
    <section className="card app-jobs"><div className="card-heading"><div><h2>App activity {activeJobs.length > 0 && <span>{activeJobs.length} active</span>}</h2><p>Success appears after the terminal confirms the operation.</p></div><Clock3 size={17}/></div><div className="table-scroll"><table className="data-table"><thead><tr><th>Action</th><th>Application</th><th>Result</th><th>Requested</th><th>Details</th></tr></thead><tbody>{history.map(job => <tr key={job.id}><td>{actionNames[job.action] || job.action}</td><td><code>{job.package_name || (job.action === 'INSTALL' ? 'Uploaded APK' : 'All apps')}</code></td><td><span className={`badge ${job.state === 'SUCCEEDED' ? 'green' : job.state === 'FAILED' ? 'red' : !terminal.has(job.state) ? 'blue' : 'muted'}`}>{!terminal.has(job.state) && <LoaderCircle size={12} className="spin"/>}{job.state.toLowerCase()}</span></td><td className="date-cell">{date(job.created_at)}</td><td className="detail-cell">{job.detail}</td></tr>)}</tbody></table>{!history.length && <div className="empty-inline">No app commands yet.</div>}</div></section>
    {dialog && <Confirm key={`${dialog.action}-${dialog.app?.packageName || ''}`} operation={dialog} busy={busy} onClose={() => setDialog(null)} onConfirm={() => send(dialog)}/>}
  </div>;
}
