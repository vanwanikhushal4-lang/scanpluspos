import React, { useEffect, useRef, useState } from 'react';
import { LogOut, Activity, AppWindow, BellRing, Cable, ChevronDown, ChevronsLeft, ChevronsRight, CircleHelp, Download, FileBarChart2, HardDrive, History, LayoutDashboard, Monitor, Moon, Server, Settings, ShieldCheck, Sun, User, Wifi } from 'lucide-react';
import { online, protectionLabel, relativeTime } from '../model.mjs';
import { Badge, dateTime } from './ui.jsx';
import { simAlertCount } from './sim-model.mjs';

export const navigation = [
  { id: 'dashboard', title: 'Security Dashboard', icon: LayoutDashboard },
  { id: 'apps', title: 'Application Control', icon: AppWindow },
  { id: 'protection', title: 'Port Control', icon: Cable },
  { id: 'storage', title: 'Storage Control', icon: HardDrive },
  { id: 'sim', title: 'SIM Protection', icon: BellRing },
  { id: 'wifi', title: 'WiFi & Bluetooth Controls', icon: Wifi },
  { id: 'deployments', title: 'Remote Patch Management', icon: Download },
  { id: 'hardening', title: 'Device and OS Hardening', icon: ShieldCheck },
  { id: 'devices', title: 'Device Management', icon: Monitor },
  { id: 'connectivity', title: 'Up / Down', icon: Activity },
  { id: 'commands', title: 'Command History', icon: History },
  { id: 'reports', title: 'Reports & Audit', icon: FileBarChart2 },
];
function initialTheme() { try { const value = localStorage.getItem('scanplus-theme'); if (['light', 'dark'].includes(value)) return value; } catch {} return 'dark'; }
export default function ConsoleShell({ model: m, session, view, navigate, demoDashboard, children }) {
  const [theme, setTheme] = useState(initialTheme), [expanded, setExpanded] = useState(false), [profile, setProfile] = useState(false);
  const profileRef = useRef(null);
  const sidebarRef = useRef(null);
  const sidebarToggleRef = useRef(null);
  useEffect(() => { document.documentElement.classList.toggle('dark', theme === 'dark'); try { localStorage.setItem('scanplus-theme', theme); } catch {} }, [theme]);
  useEffect(() => {
    if (!profile) return;
    const close = event => { if (event.key === 'Escape' || event.type === 'pointerdown' && !profileRef.current?.contains(event.target)) setProfile(false); };
    document.addEventListener('pointerdown', close); document.addEventListener('keydown', close);
    return () => { document.removeEventListener('pointerdown', close); document.removeEventListener('keydown', close); };
  }, [profile]);
  useEffect(() => {
    if (!expanded) return;
    const close = event => {
      if (event.key === 'Escape') {
        setExpanded(false);
        sidebarToggleRef.current?.focus();
      } else if (event.type === 'pointerdown' && !sidebarRef.current?.contains(event.target)) {
        setExpanded(false);
      }
    };
    document.addEventListener('pointerdown', close);
    document.addEventListener('keydown', close);
    return () => {
      document.removeEventListener('pointerdown', close);
      document.removeEventListener('keydown', close);
    };
  }, [expanded]);
  const openFromSidebar = page => {
    navigate(page);
    if (expanded) sidebarToggleRef.current?.focus();
    setExpanded(false);
  };
  const owner = session?.mode === 'Netlify administrator';
  const hosted = session?.mode === 'SCANPLUS administrator';
  const tabs = [
    { id: 'dashboard', title: 'POS Security' },
    { id: 'compliance', title: 'Compliance' },
    { id: 'monitoring', title: 'Monitoring' },
  ];
  const activeTab = view === 'compliance' ? 'compliance' : ['monitoring', 'apps', 'deployments'].includes(view) ? 'monitoring' : 'dashboard';
  const simAlerts = demoDashboard ? 0 : simAlertCount(m.devices);
  const reviewSimAlerts = () => { const device = m.devices.find(item => !item.revoked && item.sim_alert_count > 0); if (device) m.setSelectedId(device.id); navigate('sim'); };
  const up = m.selected && online(m.selected, m.now), state = m.selected ? protectionLabel(m.selected) : 'Awaiting device';
  return <div className="cms-shell">
    <header className="cms-header">
      <button className="cms-logo" onClick={() => navigate('dashboard')} aria-label="ScanPlus POS Security dashboard"><img className="dark:hidden" src="/img/darklogo.svg" alt="ScanPlus POS Security"/><img className="hidden dark:block" src="/img/lightlogo.svg" alt="ScanPlus POS Security"/></button>
      <nav className="cms-topnav" aria-label="Workspaces">{tabs.map(tab => <button key={tab.id} aria-current={activeTab === tab.id ? 'page' : undefined} className={activeTab === tab.id ? 'active' : ''} onClick={() => navigate(tab.id)}>{tab.title}</button>)}</nav>
      <div className="cms-header-actions">
        <button className="cms-theme" aria-label={theme === 'light' ? 'Switch to dark theme' : 'Switch to light theme'} onClick={() => setTheme(theme === 'light' ? 'dark' : 'light')}>{theme === 'light' ? <Moon size={17}/> : <Sun size={17}/>}</button>
        <span className={`cms-system ${m.ready ? '' : 'offline'}`} role="status"><i aria-hidden="true"/>{m.ready ? 'Connected' : m.loading ? 'Connecting' : 'Disconnected'}</span>
        <div className="relative" ref={profileRef}><button className="cms-profile" aria-label="Administrator details" aria-expanded={profile} onClick={() => setProfile(!profile)}><span className="cms-avatar">AD</span><span className="cms-profile-copy"><small>{hosted ? 'ADMIN ACCESS' : owner ? 'NETLIFY OWNER' : 'LOCAL ACCESS'}</small><strong>Administrator</strong></span><ChevronDown size={13}/></button>{profile && <div className="cms-profile-menu"><div className="flex items-center gap-3"><span className="cms-avatar"><User size={18}/></span><div><strong className="text-xs">Administrator</strong><p className="text-[10px] text-gray-500 mt-1 dark:text-slate-400">{hosted ? 'Secure administrator session' : owner ? 'Verified Netlify owner access' : 'Current Mac workspace'}</p></div></div><div className="border-t border-gray-100 mt-4 pt-3 dark:border-slate-700"><p className="text-[10px] text-gray-500 mb-1 dark:text-slate-400">Latest backend refresh</p><span className="text-xs">{dateTime(m.lastRefresh)}</span></div><button className="button white mt-4 w-full" onClick={() => { navigate('settings'); setProfile(false); }}><Server size={13}/>Backend connection</button>{hosted && <button className="button white mt-2 w-full" onClick={session.signOut}><LogOut size={13}/>Sign out</button>}</div>}</div>
      </div>
    </header>
    <div className="cms-workspace">
      <aside className={`cms-sidebar ${expanded ? 'expanded' : 'collapsed'}`} ref={sidebarRef}>
        <div className="cms-sidebar-head">
          <button ref={sidebarToggleRef} aria-label={expanded ? 'Collapse sidebar' : 'Expand sidebar'} aria-expanded={expanded} aria-controls="console-navigation" className="cms-menu-toggle" onClick={() => setExpanded(value => !value)}>
            {expanded ? <ChevronsLeft size={17}/> : <ChevronsRight size={17}/>}
          </button>
          <div><strong>ScanPlus POS Security</strong></div>
        </div>
        <nav className="cms-nav" id="console-navigation" aria-label="Main navigation">
          {navigation.map(item => (
            <button key={item.id} onClick={() => openFromSidebar(item.id)} aria-label={item.title}
              aria-current={view === item.id ? 'page' : undefined} title={!expanded ? item.title : undefined}
              className={view === item.id ? 'active' : ''}>
              <item.icon size={17}/><span>{item.title}</span>
            </button>
          ))}
        </nav>
        <div className="cms-sidebar-bottom"><div className="cms-sidebar-divider"/><button onClick={() => openFromSidebar('settings')} className={view === 'settings' ? 'active' : ''} aria-current={view === 'settings' ? 'page' : undefined} aria-label="Settings" title={!expanded ? 'Settings' : undefined}><Settings size={18}/><span>Settings</span></button><button onClick={() => { setExpanded(false); m.setDialog({ type: 'connection' }); }} aria-label="Connection details" title={!expanded ? 'Connection details' : undefined}><CircleHelp size={18}/><span>Connection Details</span></button>{expanded && <div className="cms-sidebar-status"><span className={up ? 'text-emerald-500' : 'text-gray-400'}><Wifi size={14}/></span><div><strong>{m.selected?.label || 'No terminal selected'}</strong><small>{up ? 'Connected over HTTPS' : 'Waiting for terminal'}</small></div></div>}</div>
      </aside>
      <div className="cms-content-column"><main className="cms-content" id="main-content" tabIndex={-1}>{!demoDashboard && m.error && <div className="error-banner" role="alert"><Server size={18}/><div><strong>Backend connection needs attention</strong><span>{m.error} Displayed information may be out of date.</span></div><button onClick={m.refreshAll}>Retry</button></div>}{simAlerts > 0 && <div className="wifi-notice flex flex-wrap items-center gap-3 mb-3" role="status"><BellRing size={16}/><span>{simAlerts} unacknowledged SIM {simAlerts === 1 ? 'alert' : 'alerts'}{!m.ready && ' · last reported count'}</span><button className="text-button ml-auto" onClick={reviewSimAlerts}>Review alerts</button></div>}<div key={view} className={`console-page console-page-${view}`}>{children}</div></main>
        <footer className="cms-footer"><img className="dark:hidden" src="/img/footerdark.png" alt="Powered by Velox"/><img className="hidden dark:block" src="/img/footerlight.png" alt="Powered by Velox"/><div className="cms-footer-sync"><ShieldCheck size={13}/><span>{demoDashboard ? 'Demo snapshot' : state}</span>{!demoDashboard && <><b/><Wifi size={13}/><span>{m.ready && up ? 'Live Sync' : 'Awaiting Sync'}</span></>}</div>{!demoDashboard && <span className="cms-footer-note"><Activity size={13}/>{m.lastRefresh ? `Updated ${relativeTime(new Date(m.lastRefresh).toISOString(), m.now).toLowerCase()}` : 'Connecting to backend'}</span>}{!demoDashboard && <Badge tone="blue">Headless Agent</Badge>}</footer>
      </div>
    </div>
  </div>;
}
