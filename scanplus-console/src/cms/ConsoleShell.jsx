import React, { useEffect, useRef, useState } from 'react';
import { LogOut, Activity, AppWindow, BellRing, ChevronDown, CircleHelp, Clock3, Download, FileBarChart2, History, LayoutDashboard, Menu, Monitor, Moon, Package, Server, Settings, ShieldCheck, Sun, Usb, User, Wifi, X } from 'lucide-react';
import { online, protectionLabel, relativeTime } from '../model.mjs';
import { Badge, dateTime } from './ui.jsx';
import { simAlertCount } from './sim-model.mjs';

export const navigation = [
  { id: 'dashboard', title: 'Security Dashboard', icon: LayoutDashboard },
  { id: 'devices', title: 'Device Management', icon: Monitor },
  { id: 'apps', title: 'Application Control', icon: AppWindow },
  { id: 'protection', title: 'Port & Device Protection', icon: Usb },
  { id: 'wifi', title: 'Wi-Fi Security', icon: Wifi },
  { id: 'sim', title: 'SIM Change Alerts', icon: BellRing },
  { id: 'deployments', title: 'App Deployment', icon: Download },
  { id: 'commands', title: 'Command History', icon: History },
  { id: 'reports', title: 'Reports & Audit', icon: FileBarChart2 },
];
function initialTheme() { try { const value = localStorage.getItem('scanplus-theme'); if (['light', 'dark'].includes(value)) return value; } catch {} return 'light'; }
export default function ConsoleShell({ model: m, session, view, navigate, children }) {
  const [theme, setTheme] = useState(initialTheme), [expanded, setExpanded] = useState(true), [profile, setProfile] = useState(false);
  const profileRef = useRef(null);
  useEffect(() => { document.documentElement.classList.toggle('dark', theme === 'dark'); try { localStorage.setItem('scanplus-theme', theme); } catch {} }, [theme]);
  useEffect(() => {
    if (!profile) return;
    const close = event => { if (event.key === 'Escape' || event.type === 'pointerdown' && !profileRef.current?.contains(event.target)) setProfile(false); };
    document.addEventListener('pointerdown', close); document.addEventListener('keydown', close);
    return () => { document.removeEventListener('pointerdown', close); document.removeEventListener('keydown', close); };
  }, [profile]);
  const owner = session?.mode === 'Netlify administrator';
  const hosted = session?.mode === 'SCANPLUS administrator';
  const tabs = [{ id: 'dashboard', title: 'POS Security' }, { id: 'devices', title: 'Device Management' }, { id: 'apps', title: 'Application Control' }];
  const activeTab = ['apps', 'deployments'].includes(view) ? 'apps' : ['devices', 'protection', 'wifi', 'sim'].includes(view) ? 'devices' : 'dashboard';
  const simAlerts = simAlertCount(m.devices);
  const reviewSimAlerts = () => { const device = m.devices.find(item => !item.revoked && item.sim_alert_count > 0); if (device) m.setSelectedId(device.id); navigate('sim'); };
  const up = m.selected && online(m.selected, m.now), state = m.selected ? protectionLabel(m.selected) : 'Awaiting device';
  return <div className="cms-shell">
    <header className="cms-header">
      <button className="cms-logo" onClick={() => navigate('dashboard')} aria-label="ScanPlus POS Security dashboard"><img className="dark:hidden" src="/img/darklogo.svg" alt="ScanPlus POS Security"/><img className="hidden dark:block" src="/img/lightlogo.svg" alt="ScanPlus POS Security"/></button>
      <nav className="cms-topnav" aria-label="Workspaces">{tabs.map(tab => <button key={tab.id} aria-current={activeTab === tab.id ? 'page' : undefined} className={activeTab === tab.id ? 'active' : ''} onClick={() => navigate(tab.id)}>{tab.title}</button>)}</nav>
      <div className="ml-auto flex items-center gap-2 shrink-0">
        <button className="cms-theme" aria-label={theme === 'light' ? 'Switch to dark theme' : 'Switch to light theme'} onClick={() => setTheme(theme === 'light' ? 'dark' : 'light')}>{theme === 'light' ? <Moon size={17}/> : <Sun size={17}/>}</button>
        <span className={`cms-system ${m.ready ? '' : 'offline'}`}><i/>{m.ready ? 'Backend Connected' : m.loading ? 'Connecting' : 'Backend Disconnected'}</span>
        <div className="relative" ref={profileRef}><button className="cms-profile" aria-label="Administrator details" aria-expanded={profile} onClick={() => setProfile(!profile)}><span className="cms-avatar">AD</span><span className="cms-profile-copy"><small>{hosted ? 'ADMIN ACCESS' : owner ? 'NETLIFY OWNER' : 'LOCAL ACCESS'}</small><strong>Administrator</strong></span><ChevronDown size={13}/></button>{profile && <div className="cms-profile-menu"><div className="flex items-center gap-3"><span className="cms-avatar"><User size={18}/></span><div><strong className="text-xs">Administrator</strong><p className="text-[10px] text-gray-500 mt-1 dark:text-slate-400">{hosted ? 'Secure administrator session' : owner ? 'Verified Netlify owner access' : 'Current Mac workspace'}</p></div></div><div className="border-t border-gray-100 mt-4 pt-3 dark:border-slate-700"><p className="text-[10px] text-gray-500 mb-1 dark:text-slate-400">Latest backend refresh</p><span className="text-xs">{dateTime(m.lastRefresh)}</span></div><button className="button white mt-4 w-full" onClick={() => { navigate('settings'); setProfile(false); }}><Server size={13}/>Backend connection</button>{hosted && <button className="button white mt-2 w-full" onClick={session.signOut}><LogOut size={13}/>Sign out</button>}</div>}</div>
      </div>
    </header>
    <div className="cms-workspace">
      <aside className={`cms-sidebar ${expanded ? 'expanded' : 'collapsed'}`}>
        <div className="cms-sidebar-head">{expanded && <div><strong>ScanPlus POS Security</strong><small>Admin Console</small></div>}<button aria-label={expanded ? 'Collapse sidebar' : 'Expand sidebar'} className="cms-menu-toggle" onClick={() => setExpanded(!expanded)}>{expanded ? <X size={19}/> : <Menu size={20}/>}</button></div>
        <nav className="cms-nav" aria-label="Main navigation">{navigation.map(item => <button key={item.id} onClick={() => navigate(item.id)} aria-label={item.title} aria-current={view === item.id ? 'page' : undefined} title={!expanded ? item.title : undefined} className={view === item.id ? 'active' : ''}><item.icon size={18}/><span>{item.title}</span>{item.id === 'devices' && expanded && <small>{m.devices.length}</small>}</button>)}</nav>
        <div className="cms-sidebar-bottom"><div className="cms-sidebar-divider"/><button onClick={() => navigate('settings')} className={view === 'settings' ? 'active' : ''} aria-current={view === 'settings' ? 'page' : undefined} aria-label="Settings" title={!expanded ? 'Settings' : undefined}><Settings size={18}/><span>Settings</span></button><button onClick={() => m.setDialog({ type: 'connection' })} aria-label="Connection details" title={!expanded ? 'Connection details' : undefined}><CircleHelp size={18}/><span>Connection Details</span></button>{expanded && <div className="cms-sidebar-status"><span className={up ? 'text-emerald-500' : 'text-gray-400'}><Wifi size={14}/></span><div><strong>{m.selected?.label || 'No terminal selected'}</strong><small>{up ? 'Connected over HTTPS' : 'Waiting for terminal'}</small></div></div>}</div>
      </aside>
      <div className="cms-content-column"><main className="cms-content" id="main-content" tabIndex={-1}>{m.error && <div className="error-banner" role="alert"><Server size={18}/><div><strong>Backend connection needs attention</strong><span>{m.error} Displayed information may be out of date.</span></div><button onClick={m.refreshAll}>Retry</button></div>}{simAlerts > 0 && <div className="wifi-notice flex flex-wrap items-center gap-3 mb-3" role="status"><BellRing size={16}/><span>{simAlerts} unacknowledged SIM {simAlerts === 1 ? 'alert' : 'alerts'}{!m.ready && ' · last reported count'}</span><button className="text-button ml-auto" onClick={reviewSimAlerts}>Review alerts</button></div>}{children}</main>
        <footer className="cms-footer"><img className="dark:hidden" src="/img/footerdark.png" alt="Powered by Velox"/><img className="hidden dark:block" src="/img/footerlight.png" alt="Powered by Velox"/><div className="cms-footer-sync"><ShieldCheck size={13}/><span>{state}</span><b/><Wifi size={13}/><span>{m.ready && up ? 'Live Sync' : 'Awaiting Sync'}</span></div><span className="cms-footer-note"><Activity size={13}/>{m.lastRefresh ? `Updated ${relativeTime(new Date(m.lastRefresh).toISOString(), m.now).toLowerCase()}` : 'Connecting to backend'}</span><Badge tone="blue">Headless Agent</Badge></footer>
      </div>
    </div>
  </div>;
}
