import React, { useEffect, useState } from 'react';
import { Check, CheckCheck, CircleHelp, LoaderCircle, LockKeyhole, Monitor, Server, ShieldCheck, TerminalSquare, X } from 'lucide-react';
import AppControl from './AppControl.jsx';
import ConsoleShell from './cms/ConsoleShell.jsx';
import SecurityDashboard from './cms/SecurityDashboard.jsx';
import { CommandHistory, ConnectionSettings, DeviceManagement, ProtectionPanel } from './cms/DevicePages.jsx';
import Reports from './cms/Reports.jsx';
import WifiPage from './cms/WifiPage.jsx';
import SimPage from './cms/SimPage.jsx';
import { useConsole } from './cms/useConsole.js';
import { Modal, PageHead } from './cms/ui.jsx';
import { online } from './model.mjs';

const routes = new Set(['dashboard', 'devices', 'apps', 'wifi', 'sim', 'protection', 'deployments', 'commands', 'reports', 'settings']);
function route() { const value = window.location.hash.slice(1); return routes.has(value) ? value : 'dashboard'; }
export default function App({ session }) {
  const [view, setView] = useState(route), [options, setOptions] = useState({});
  const m = useConsole(view);
  const navigate = (page, next = {}) => { if (!routes.has(page)) return; setOptions(next); setView(page); window.history.replaceState(null, '', `${window.location.pathname}${window.location.search}#${page}`); document.querySelector('.cms-content')?.scrollTo({ top: 0 }); };
  useEffect(() => { const changed = () => { setView(route()); setOptions({}); }; window.addEventListener('hashchange', changed); return () => window.removeEventListener('hashchange', changed); }, []);
  return <><a href="#main-content" className="cms-skip-link" onClick={event => { event.preventDefault(); document.getElementById('main-content')?.focus(); }}>Skip to main content</a><ConsoleShell model={m} session={session} view={view} navigate={navigate}>
    {view === 'dashboard' && <SecurityDashboard model={m} navigate={navigate}/>}
    {view === 'devices' && <DeviceManagement key={options.filter || 'all'} model={m} navigate={navigate} initialFilter={options.filter}/>}
    {view === 'wifi' && <WifiPage key={m.selectedId || 'none'} model={m}/>}
    {view === 'sim' && <SimPage key={m.selectedId || 'none'} model={m}/>}
    {['apps', 'deployments'].includes(view) && <><PageHead title={view === 'apps' ? 'Application Control' : 'App Deployment'} description={view === 'apps' ? 'Manage installed apps, visibility, runtime permissions and policies.' : 'Upload and install APKs, then track the terminal’s confirmed result.'}/><AppControl key={`${m.selectedId}-${view}-${options.appFilter || ''}`} devices={m.devices} selectedId={m.selectedId} onSelect={m.setSelectedId} onManageDevice={() => navigate('protection')} now={m.now} initialFilter={options.appFilter} focus={view === 'deployments' ? 'deployments' : 'all'}/></>}
    {view === 'protection' && <ProtectionPanel model={m} navigate={navigate}/>}
    {view === 'commands' && <CommandHistory model={m}/>}
    {view === 'reports' && <Reports model={m}/>}
    {view === 'settings' && <ConnectionSettings model={m}/>}
  </ConsoleShell>
  {m.dialog?.type === 'protection' && <Modal title={m.dialog.enabled ? 'Enable terminal protection?' : 'Enter maintenance mode?'} busy={m.sending} onClose={() => m.setDialog(null)}><div className={`dialog-symbol ${m.dialog.enabled ? 'blue' : 'amber'}`}>{m.dialog.enabled ? <ShieldCheck size={27}/> : <LockKeyhole size={27}/>}</div><p className="dialog-summary">{m.dialog.enabled ? 'Enable protection on' : 'Restore the previous service settings on'} <strong>{m.dialog.device.label || m.dialog.device.model}</strong>.</p><div className="dialog-device"><TerminalSquare size={18}/><span>{m.dialog.device.model}</span><code>{m.dialog.device.serial}</code></div><ul className="effect-list">{(m.dialog.enabled ? ['USB data and debugging will disconnect.', 'New app installations and updates will be blocked.', 'The headless agent will stay connected over Wi-Fi.'] : ['Previous installation and management restrictions will be restored.', 'The helper will request restoration of the saved USB settings.', 'Enable protection again after servicing is complete.']).map(effect => <li key={effect}><Check size={14}/>{effect}</li>)}</ul>{!online(m.devices.find(d => d.id === m.dialog.device.id) || m.dialog.device, m.now) && <p className="dialog-offline">This terminal is offline. The request expires after two minutes if it does not reconnect.</p>}<div className="modal-actions"><button className="button white" disabled={m.sending} onClick={() => m.setDialog(null)}>Cancel</button><button className="button primary" disabled={m.sending} onClick={() => m.submit(m.dialog)}>{m.sending ? <LoaderCircle size={14} className="spin"/> : <ShieldCheck size={14}/>} {m.sending ? 'Sending request' : m.dialog.enabled ? 'Confirm protection' : 'Confirm maintenance'}</button></div></Modal>}
  {m.dialog?.type === 'connection' && <Modal title="Backend Connection" onClose={() => m.setDialog(null)}><div className="dialog-symbol blue"><Server size={26}/></div><p className="dialog-summary">{m.ready ? 'Connected securely to your cloud backend.' : 'The backend connection needs attention.'}</p><dl className="cms-settings-list mt-4"><div><dt>Backend</dt><dd>{m.info?.backend || 'Loading…'}</dd></div><div><dt>Access</dt><dd>{m.info?.mode === 'Netlify administrator' ? 'Netlify owner sign-in' : m.info?.mode === 'SCANPLUS administrator' ? 'SCANPLUS administrator sign-in' : 'This Mac only'}</dd></div><div><dt>Device connection</dt><dd>Outbound HTTPS over Wi-Fi</dd></div><div><dt>Refresh</dt><dd>10 seconds while visible</dd></div></dl><p className="text-xs leading-6 text-gray-500 dark:text-slate-400 my-5">The Windows host and Cloudflare tunnel must stay running. Administrator credentials remain in the console server.</p><div className="modal-actions"><button className="button primary" onClick={() => m.setDialog(null)}>Done</button></div></Modal>}
  {m.toast && <div className={`toast ${m.toast.tone}`} role={m.toast.tone === 'error' ? 'alert' : 'status'}>{m.toast.tone === 'success' ? <CheckCheck size={19}/> : m.toast.tone === 'error' ? <CircleHelp size={19}/> : <Monitor size={19}/>}<span>{m.toast.message}</span><button className="icon-button" aria-label="Dismiss notification" onClick={() => m.setToast(null)}><X size={16}/></button></div>}
  </>;
}
