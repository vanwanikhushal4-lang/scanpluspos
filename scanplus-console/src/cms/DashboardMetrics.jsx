import React, { useId } from 'react';
import { Activity, AppWindow, ArrowUpRight, BellRing, Bluetooth, Cable, Download, HardDrive, Monitor, ShieldCheck, Wifi } from 'lucide-react';
import { online, protectionLabel } from '../model.mjs';
import { simAlertCount } from './sim-model.mjs';

const palette = ['cyan', 'violet', 'indigo', 'rose'];
const number = value => value == null ? '—' : value.toLocaleString('en-IN');
const share = (value, total) => total ? value / total : 0;
const percent = (value, total) => total ? `${(share(value, total) * 100).toFixed(1).replace(/\.0$/, '')}%` : '—';

function Metric({ title, icon: Icon, onOpen, variant, index, children }) {
  const heading = <><Icon size={15} aria-hidden="true"/><span>{title}</span></>;
  return (
    <li className={`bento-tile bento-${variant}`} style={{ '--tile-index': index }}>
      <section className="bento-card">
        <h2 className="bento-heading">
          {onOpen
            ? <button className="bento-heading-content" type="button" onClick={onOpen}>{heading}<ArrowUpRight className="bento-arrow" size={14} aria-hidden="true"/></button>
            : <span className="bento-heading-content">{heading}</span>}
        </h2>
        {children}
      </section>
    </li>
  );
}

function Figure({ value, label, tone }) {
  return <div className={`bento-figure ${tone || ''}`}><strong>{value}</strong><small>{label}</small></div>;
}

function Stat({ value, label, tone, onClick }) {
  const content = <><strong>{number(value)}</strong><small>{label}</small></>;
  const className = `bento-stat ${tone || ''}`;
  return onClick
    ? <button type="button" className={className} onClick={onClick}>{content}</button>
    : <div className={className}>{content}</div>;
}

function Rings({ values, children, className = '' }) {
  return (
    <div className={`bento-rings ${className}`}>
      <svg viewBox="0 0 100 100" aria-hidden="true">
        {values.map(({ value, tone }, index) => {
          const radius = 42 - index * 10;
          return (
            <g key={tone} className={tone}>
              <circle className="bento-ring-track" cx="50" cy="50" r={radius}/>
              {value > 0 && <circle className="bento-ring-value" cx="50" cy="50" r={radius} pathLength="100" strokeDasharray="100 100" style={{ '--dash': 100 - value * 100 }}/>}
            </g>
          );
        })}
      </svg>
      <div className="bento-ring-center">{children}</div>
    </div>
  );
}

function Bars({ rows, loading = false }) {
  const max = Math.max(1, ...rows.map(row => row.count));
  return (
    <div className="bento-bars" style={{ '--bar-count': rows.length }}>
      {rows.map((row, index) => {
        const content = (
          <>
            <span className="bento-bar-track" aria-hidden="true">
              <span style={{ '--fill': row.count / max, '--bar-index': index }}/>
            </span>
            <small>{row.label}</small>
          </>
        );
        const label = `${row.fullLabel || row.label}: ${loading ? 'not reported' : number(row.count)}`;
        return row.onClick
          ? <button type="button" key={row.label} className={palette[index]} aria-label={label} title={label} onClick={row.onClick}>{content}</button>
          : <div key={row.label} className={palette[index]} aria-label={label} role="img" title={label}>{content}</div>;
      })}
    </div>
  );
}

function ProgressRows({ rows, total, loading = false }) {
  return (
    <ul className="bento-progress">
      {rows.map(({ label, count, tone }, index) => (
        <li key={label} className={tone}>
          <div><span>{label}</span><strong>{number(loading ? null : count)}</strong></div>
          <div className="bento-progress-track" aria-hidden="true">
            <span style={{ '--fill': share(count, total), '--bar-index': index }}/>
          </div>
        </li>
      ))}
    </ul>
  );
}

function StorageGauge({ rows, total }) {
  let end = 0;
  return (
    <div className="bento-gauge">
      <svg viewBox="0 0 176 106" aria-hidden="true">
        <path className="bento-gauge-ticks" d="M 8 90 A 80 80 0 0 1 168 90" pathLength="100"/>
        <path className="bento-ring-track" d="M 17 90 A 71 71 0 0 1 159 90"/>
        {rows.filter(row => row.count > 0).map(({ label, count, tone }) => {
          const start = end;
          const length = share(count, total) * 100;
          end += length;
          return <path key={label} className={`bento-gauge-value ${tone}`} d="M 17 90 A 71 71 0 0 1 159 90" pathLength="100" strokeDasharray={`${length} 100`} strokeDashoffset={-start}/>;
        })}
      </svg>
      <div className="bento-gauge-center"><strong>{number(total)}</strong><small>Terminals</small></div>
    </div>
  );
}

const checkInLabels = ['Online', '45s–5m', '5m–1h', '1h–24h', '24h+'];
function CheckInChart({ devices, now }) {
  const gradient = useId();
  const counts = [0, 0, 0, 0, 0];
  for (const device of devices) {
    if (device.revoked || !device.last_seen) continue;
    const age = now - Date.parse(device.last_seen);
    if (!Number.isFinite(age) || age < -60000) continue;
    const bucket = online(device, now) ? 0 : age < 300000 ? 1 : age < 3600000 ? 2 : age < 86400000 ? 3 : 4;
    counts[bucket]++;
  }
  const peak = Math.max(1, ...counts);
  const points = counts.map((value, index) => [8 + index * 71, 65 - value / peak * 54]);
  const line = points.reduce((path, [x, y], index) => {
    if (!index) return `M ${x} ${y}`;
    const [priorX, priorY] = points[index - 1];
    const middle = (x + priorX) / 2;
    return `${path} C ${middle} ${priorY}, ${middle} ${y}, ${x} ${y}`;
  }, '');
  const hasCheckIns = counts.some(Boolean);
  return (
    <figure className="bento-checkins">
      <svg viewBox="0 0 300 70" preserveAspectRatio="none" role="img" aria-label={hasCheckIns ? checkInLabels.map((label, index) => `${label}: ${counts[index]}`).join(', ') : 'No check-ins reported'}>
        <defs><linearGradient id={gradient} x2="0" y2="1" gradientUnits="objectBoundingBox"><stop stopColor="var(--chart-indigo)" stopOpacity=".46"/><stop offset="1" stopColor="var(--chart-indigo)" stopOpacity=".02"/></linearGradient></defs>
        {[13, 39, 65].map(y => <path key={y} d={`M 0 ${y} H 300`} className="bento-chart-gridline"/>)}
        {hasCheckIns && <g className="bento-checkin-series">
          <path d={`${line} L 292 70 H 8 Z`} fill={`url(#${gradient})`}/>
          <path className="bento-checkin-line" d={line} pathLength="100" vectorEffect="non-scaling-stroke"/>
          {points.map(([x, y], index) => <circle key={x} cx={x} cy={y} r="2.4" className="bento-checkin-point"><title>{`${checkInLabels[index]}: ${counts[index]}`}</title></circle>)}
        </g>}
      </svg>
      <figcaption>
        {hasCheckIns ? checkInLabels.map(label => <span key={label}>{label}</span>) : <span>No check-ins reported</span>}
      </figcaption>
    </figure>
  );
}

export default function DashboardMetrics({ model: m, fleet, apps, navigate }) {
  const hasInventory = Boolean(m.inventory?.reportedAt);
  const hasJobs = m.inventory != null || m.appJobs.length > 0;
  const simReporting = m.devices.filter(device => !device.revoked && Number.isSafeInteger(device.sim_alert_count)).length;
  const selected = m.selected?.revoked ? null : m.selected;
  const storageRows = [
    { label: 'File transfer restricted', count: fleet.protected, tone: 'cyan' },
    { label: 'In maintenance', count: fleet.maintenance, tone: 'rose' },
    { label: 'Unconfirmed', count: fleet.attention + fleet.unknown, tone: 'violet' },
  ];
  const installJobs = m.appJobs.filter(job => job.action === 'INSTALL');
  const completed = installJobs.filter(job => job.state === 'SUCCEEDED').length;
  const active = installJobs.filter(job => ['PENDING', 'RUNNING'].includes(job.state)).length;
  const patchRows = [
    { label: 'Completed', count: completed, tone: 'cyan' },
    { label: 'Queued or running', count: active, tone: 'violet' },
    { label: 'Other results', count: installJobs.length - completed - active, tone: 'indigo' },
  ];
  const openPage = (page, options) => navigate && (() => navigate(page, options));
  const openSim = navigate && (() => {
    const device = m.devices.find(item => !item.revoked && item.sim_alert_count > 0);
    if (device) m.setSelectedId(device.id);
    navigate('sim');
  });
  const sealRate = percent(fleet.sealed, fleet.total);
  const knownFleet = m.lastRefresh != null;
  const visibleCount = value => knownFleet ? value : null;
  const appGroups = [
    ['User', 'User apps', 'user'],
    ['System', 'System apps', 'system'],
    ['Managed', 'Management apps', 'management'],
    ['Restricted', 'Restricted apps', 'restricted'],
  ].map(([label, fullLabel, key]) => ({ label, fullLabel, count: apps[key], onClick: openPage('apps', { appFilter: key }) }));

  const WirelessControl = navigate ? 'button' : 'div';
  const ConnectivityStat = navigate ? 'button' : 'span';
  return (
    <ol className="bento-grid" aria-label="Security overview">
      <Metric index={0} variant="apps" title="Application Control" icon={AppWindow} onOpen={openPage('apps', { appFilter: 'all' })}>
        <div className="bento-split bento-app-detail">
          <Figure value={number(hasInventory ? apps.total : null)} label="Installed applications"/>
          <span className="bento-context" title={selected?.label}>{selected?.label || selected?.model || 'No terminal selected'}</span>
        </div>
        <Bars rows={appGroups} loading={!hasInventory}/>
        <div className="bento-stat-strip">
          <Stat value={visibleCount(fleet.total)} label="Enrolled terminals" onClick={openPage('devices')}/>
          <Stat value={hasInventory ? apps.restricted : null} label="Restricted apps" tone="violet" onClick={openPage('apps', { appFilter: 'restricted' })}/>
        </div>
      </Metric>

      <Metric index={1} variant="ports" title="Port Control" icon={Cable} onOpen={openPage('protection')}>
        <div className="bento-ring-row">
          <Rings className="bento-port-ring" values={[{ value: share(fleet.sealed, fleet.total), tone: 'cyan' }]}>
            <strong>{sealRate}</strong><small>Sealed</small>
          </Rings>
          <Figure value={number(visibleCount(fleet.total))} label="Total terminals"/>
        </div>
        <div className="bento-stat-strip">
          <Stat value={visibleCount(fleet.sealed)} label="Sealed" onClick={openPage('devices', { filter: 'sealed' })}/>
          <Stat value={visibleCount(fleet.unsealed)} label="Not sealed" tone="rose" onClick={openPage('devices', { filter: 'unsealed' })}/>
        </div>
      </Metric>

      <Metric index={2} variant="storage" title="Storage Control" icon={HardDrive} onOpen={openPage('storage')}>
        <div className="bento-wide-chart">
          <StorageGauge rows={storageRows} total={visibleCount(fleet.total)}/>
          <ProgressRows rows={storageRows} total={fleet.total} loading={!knownFleet}/>
        </div>
        <p className="bento-note">File transfer policy follows terminal protection.</p>
      </Metric>

      <Metric index={3} variant="sim" title="SIM Protection" icon={BellRing} onOpen={openSim}>
        <div className="bento-ring-row">
          <Rings className="bento-sim-ring" values={[{ value: share(simReporting, fleet.total), tone: 'cyan' }]}>
            <ShieldCheck size={21} aria-hidden="true"/>
          </Rings>
          <Figure value={number(simReporting ? simAlertCount(m.devices) : null)} label="Unreviewed alerts"/>
        </div>
        <div className="bento-summary-line"><span>Terminals reporting</span><strong className="cyan">{number(visibleCount(simReporting))} / {number(visibleCount(fleet.total))}</strong></div>
      </Metric>

      <Metric index={4} variant="wireless" title="WiFi & Bluetooth Controls" icon={Wifi} onOpen={openPage('wifi')}>
        <div className="bento-wireless-grid">
          <WirelessControl type={navigate ? 'button' : undefined} onClick={openPage('wifi')}>
            <span className="bento-radio cyan"><Wifi size={18}/></span>
            <strong>Wi-Fi</strong><small>{m.wireless ? `${number(m.wireless.wifi)} secured` : 'Network policy'}</small>
          </WirelessControl>
          <div>
            <span className="bento-radio violet"><Bluetooth size={18}/></span>
            <strong>Bluetooth</strong><small>{m.wireless ? `${number(m.wireless.bluetooth)} restricted` : 'Not available'}</small>
          </div>
        </div>
      </Metric>

      <Metric index={5} variant="patch" title="Remote Patch Management" icon={Download} onOpen={openPage('deployments')}>
        <div className="bento-wide-chart">
          <Rings className="bento-patch-rings" values={patchRows.map(({ count, tone }) => ({ value: share(count, installJobs.length), tone }))}>
            <strong>{number(hasJobs ? installJobs.length : null)}</strong><small>APK jobs</small>
          </Rings>
          <ProgressRows rows={patchRows} total={installJobs.length} loading={!hasJobs}/>
        </div>
        <p className="bento-note">Application updates · OS patches unavailable</p>
      </Metric>

      <Metric index={6} variant="hardening" title="Device and OS Hardening" icon={ShieldCheck} onOpen={openPage('hardening')}>
        <Figure value={percent(fleet.protected, fleet.total)} label="Terminals protected" tone="cyan"/>
        <div className="bento-meter" aria-hidden="true"><span style={{ '--fill': share(fleet.protected, fleet.total) }}/></div>
        <div className="bento-stat-strip bento-three-stats">
          <Stat value={visibleCount(fleet.total)} label="Total"/>
          <Stat value={visibleCount(fleet.protected)} label="Protected" tone="cyan" onClick={openPage('devices', { filter: 'protected' })}/>
          <Stat value={visibleCount(fleet.maintenance)} label="Maintenance" onClick={openPage('devices', { filter: 'maintenance' })}/>
        </div>
      </Metric>

      <Metric index={7} variant="devices" title="Device Management" icon={Monitor} onOpen={openPage('devices')}>
        <div className="bento-device-figures">
          <Figure value={number(visibleCount(fleet.managed))} label="Managed"/>
          <Figure value={number(visibleCount(fleet.total - fleet.managed))} label="Not confirmed" tone="violet"/>
        </div>
        <Bars loading={!knownFleet} rows={[
          { label: 'Managed', count: fleet.managed },
          { label: 'Unconfirmed', count: fleet.total - fleet.managed },
          { label: 'Helper ready', count: fleet.helpers },
        ]}/>
      </Metric>

      <Metric index={8} variant="connectivity" title="Up / Down" icon={Activity} onOpen={openPage('connectivity')}>
        <div className="bento-connectivity-chart">
          <Figure value={number(visibleCount(fleet.total))} label="Total terminals"/>
          <CheckInChart devices={m.devices} now={m.now}/>
        </div>
        <div className="bento-summary-line">
          <span>{selected ? `Selected: ${protectionLabel(selected)}` : 'Latest check-ins'}</span>
          <div className="bento-connectivity-links">
            <ConnectivityStat className="cyan" type={navigate ? 'button' : undefined} onClick={openPage('connectivity', { filter: 'online' })}><i/>{number(visibleCount(fleet.online))} Up</ConnectivityStat>
            <ConnectivityStat className="rose" type={navigate ? 'button' : undefined} onClick={openPage('connectivity', { filter: 'offline' })}><i/>{number(visibleCount(fleet.offline))} Down</ConnectivityStat>
          </div>
        </div>
      </Metric>
    </ol>
  );
}
