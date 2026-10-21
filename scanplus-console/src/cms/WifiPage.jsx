import React, { useCallback, useEffect, useRef, useState } from 'react';
import { AlertTriangle, Check, LoaderCircle, LockKeyhole, Radio, ShieldCheck, Wifi } from 'lucide-react';
import { getWifi, wifiCommand } from '../api.js';
import { online } from '../model.mjs';
import { startPolling } from '../polling.mjs';
import { DeviceSelect } from './DevicePages.jsx';
import { Badge, Card, CardHead, CheckLine, Empty, Modal, PageHead, StateBadge, dateTime } from './ui.jsx';
import { canEnableOpenWifiBlock, canEnableOpenWifiGuard, securityName, wifiActionLabel, wifiAge } from './wifi-model.mjs';

export default function WifiPage({ model: m }) {
  const [report, setReport] = useState(null), [error, setError] = useState(''), [notice, setNotice] = useState('');
  const [loading, setLoading] = useState(true), [sending, setSending] = useState(false), [dialog, setDialog] = useState(null);
  const [localJob, setLocalJob] = useState(null), [uncertain, setUncertain] = useState(null);
  const issued = useRef(false), alive = useRef(true), lastRequest = useRef(null), notified = useRef(null);
  const id = m.selectedId;
  const refresh = useCallback(async signal => {
    if (!id || m.selected?.revoked) { setLoading(false); return; }
    try {
      const next = await getWifi(id, signal);
      if (!alive.current || signal?.aborted) return;
      setReport(next); setError('');
      const job = next.commands.find(item => item.request_id === lastRequest.current);
      if (job) {
        setLocalJob(job); setUncertain(null);
        if (job.state !== 'PENDING' && notified.current !== job.id) {
          notified.current = job.id;
          setNotice(`${job.action === 'SCAN' ? 'Wi-Fi scan' : 'Wi-Fi policy'} ${job.state.toLowerCase()}. ${job.detail || ''}`);
        }
      }
    } catch (failure) {
      if (alive.current && failure.name !== 'AbortError') setError(failure.status === 404 ? 'Wi-Fi management needs the updated Windows backend and Cloudflare gateway.' : failure.message);
    } finally { if (alive.current && !signal?.aborted) setLoading(false); }
  }, [id, m.selected?.revoked]);
  useEffect(() => {
    alive.current = true;
    const controller = new AbortController(), poll = startPolling(() => refresh(controller.signal));
    return () => { alive.current = false; controller.abort(); poll.stop(); };
  }, [refresh]);
  const send = async (action, blocked, retry) => {
    if (issued.current) return;
    issued.current = true; setSending(true); setNotice('');
    const request = retry || { requestId: crypto.randomUUID(), ttlSeconds: 120, ...(action !== 'scan' ? { blocked } : {}) };
    lastRequest.current = request.requestId;
    try {
      const job = await wifiCommand(id, action, request);
      if (!alive.current) return;
      setLocalJob(job); setUncertain(null); setDialog(null); setNotice('Request queued. Waiting for the terminal’s confirmed result.');
      await refresh();
    } catch (failure) {
      if (!alive.current) return;
      setError(failure.message);
      if (!failure.status || failure.status >= 500) setUncertain({ action, request });
    } finally { issued.current = false; if (alive.current) setSending(false); }
  };
  const snapshot = report?.snapshot, connection = snapshot?.connection, policy = snapshot?.policy, guard = snapshot?.openGuard;
  const commands = report?.commands || [];
  const pending = commands.some(job => job.state === 'PENDING') || localJob?.state === 'PENDING';
  const available = Boolean(m.ready && m.selected && !m.selected.revoked && online(m.selected, m.now) && snapshot);
  const blocked = policy?.blocked === true;
  const guarded = guard?.enabled === true;
  const canEnable = canEnableOpenWifiBlock(m.selected, report, m.now);
  const canEnableGuard = canEnableOpenWifiGuard(m.selected, report, m.now);
  const busy = sending || pending || Boolean(uncertain);
  return <>
    <PageHead title="Wi-Fi Security" description="Networks visible to the selected terminal and its open-network policy." refresh={() => refresh()} busy={loading}>{<DeviceSelect model={m}/>}</PageHead>
    {error && <div className="error-banner mb-4" role="alert"><AlertTriangle size={17}/><div><strong>Wi-Fi management needs attention</strong><span>{error}</span></div><button onClick={() => refresh()}>Refresh</button></div>}
    {notice && <p className="wifi-notice" role="status">{notice}</p>}
    {uncertain && <p className="wifi-notice" role="alert">The request outcome is unknown. Refresh to check its history, or <button className="text-button" disabled={sending} onClick={() => send(uncertain.action, uncertain.request.blocked, uncertain.request)}>retry the same request</button>.</p>}
    <div className="grid grid-cols-1 xl:grid-cols-2 gap-3 mb-3">
      <Card><CardHead title="Current connection" subtitle={m.selected?.label || 'No terminal selected'}><Badge tone={available ? 'green' : 'muted'}>{available ? 'Terminal online' : 'Awaiting terminal'}</Badge></CardHead>
        {connection ? <div className="space-y-2"><CheckLine label="Network" value={connection.ssid || 'Hidden or unavailable'}/><CheckLine label="Security" value={securityName(connection.security)}/><CheckLine label="Signal" value={`${connection.rssi} dBm`}/><CheckLine label="IP address" value={connection.ipAddress || 'Unavailable'}/><CheckLine label="Last report" value={dateTime(report.reportedAt)}/></div> : <Empty icon={Wifi} title={snapshot ? 'No reported Wi-Fi connection' : loading ? 'Loading terminal Wi-Fi' : 'Waiting for Wi-Fi report'} detail={snapshot ? snapshot.detail : 'The terminal needs the Wi-Fi-capable headless agent.'}/>}
      </Card>
      <Card><CardHead title="Full Wi-Fi protection" subtitle="Lock local Wi-Fi changes on the terminal."><ShieldCheck size={18}/></CardHead>
        <div className="wifi-policy-control"><div><strong>{!policy ? 'Not reported' : blocked ? policy.applied ? 'Full protection on' : 'Protection needs attention' : 'Full protection off'}</strong><p>{policy?.detail || 'Update the agent to manage this policy.'}</p></div><button type="button" role="switch" aria-label="Full Wi-Fi protection" aria-checked={blocked} className="wifi-policy-switch" disabled={!available || busy || !blocked && !canEnable} onClick={() => setDialog({ action: 'policy', blocked: !blocked })}><span/></button></div>
        {policy && <div className="flex flex-wrap gap-2 mt-4"><Badge tone={!policy.applied ? 'amber' : blocked ? 'green' : 'muted'}>{policy.applied ? blocked ? 'Confirmed on terminal' : 'Policy off' : 'Enforcement needs attention'}</Badge><Badge tone="muted">{policy.enforcement === 'NATIVE' ? 'Android native policy' : policy.enforcement === 'LEGACY_LOCKDOWN' ? 'Managed network lockdown' : 'Agent policy'}</Badge></div>}
        <p className="wifi-policy-help">On this A920, full Wi-Fi protection locks local network changes. Saved secured networks remain available. This control is separate from USB and device protection.</p>
        {!blocked && !canEnable && snapshot && <p className="wifi-policy-help">A recent secured connection and device-owner enrollment are required before enabling. Use Scan now to refresh the connection report.</p>}
        {policy?.localSettingsLocked && <p className="wifi-policy-help"><LockKeyhole size={12}/>Local Wi-Fi changes are locked on the terminal.</p>}
      </Card>
      <Card className="xl:col-span-2"><CardHead title="Block open Wi-Fi connections" subtitle="Allow secured networks while rejecting password-free, unencrypted networks."><Wifi size={18}/></CardHead>
        <div className="wifi-policy-control"><div><strong>{!guard ? 'Agent update required' : guarded ? guard.applied ? 'Open-network guard on' : 'Guard needs attention' : 'Open-network guard off'}</strong><p>{guard?.detail || 'The separate connection guard requires the updated backend and headless agent 3.4.0 or newer.'}</p></div><button type="button" role="switch" aria-label="Block open Wi-Fi connections" aria-checked={guarded} className="wifi-policy-switch" disabled={!available || busy || !guard || !guarded && !canEnableGuard} onClick={() => setDialog({ action: 'guard', blocked: !guarded })}><span/></button></div>
        <p className="wifi-policy-help">All nearby networks stay visible. With full Wi-Fi protection off, you can choose and join secured networks on the terminal. On the A920, open-network attempts are disconnected when detected; a brief connection is possible before rejection.</p>
        {guarded && <div className="flex flex-wrap gap-2 mt-4"><Badge tone={guard.applied ? 'green' : 'amber'}>{guard.applied ? guard.enforcement === 'NATIVE' ? 'Native connection blocking' : 'Disconnect on detection' : 'Enforcement needs attention'}</Badge></div>}
        {blocked && <p className="wifi-policy-help"><LockKeyhole size={12}/>Full Wi-Fi protection is also on. Turn it off to allow local secured-network changes.</p>}
        {!blocked && policy?.localSettingsLocked && <p className="wifi-policy-help">Another Wi-Fi restriction is keeping local settings locked.</p>}
        {!guarded && guard && !canEnableGuard && <p className="wifi-policy-help">Connect to a secured network and refresh the terminal report before enabling.</p>}
      </Card>
    </div>
    <Card><CardHead title="Nearby networks" subtitle="Scanned by the A920, not by the administrator’s computer."><button className="button primary" disabled={!available || busy} onClick={() => send('scan')}>{sending || pending ? <LoaderCircle size={14} className="spin"/> : <Radio size={14}/>}Scan now</button></CardHead>
      {snapshot && <p className="wifi-policy-help mb-4">{snapshot.detail} {snapshot.scanAgeSeconds != null && <>Newest result: {wifiAge(snapshot.scanAgeSeconds, report.reportedAt, m.now)}.</>}</p>}
      {snapshot?.networks?.length ? <div className="table-scroll"><table className="data-table"><thead><tr><th>Network</th><th>Security</th><th>Signal</th><th>Band</th><th>Last seen</th><th>Policy</th></tr></thead><tbody>{snapshot.networks.map(network => <tr key={network.bssid}><td><strong className="cms-table-link">{network.ssid || 'Hidden network'} {network.connected && <Badge tone="blue">Connected</Badge>}</strong><small className="font-mono">{network.bssid}</small></td><td>{securityName(network.security)}</td><td>{network.rssi} dBm</td><td>{network.frequencyMhz >= 5925 ? '6 GHz' : network.frequencyMhz >= 4900 ? '5 GHz' : network.frequencyMhz ? '2.4 GHz' : 'Unknown'}</td><td>{wifiAge(network.ageSeconds, report.reportedAt, m.now)}</td><td>{network.security === 'OPEN' && blocked && policy.applied ? <Badge tone="red">Blocked by full protection</Badge> : network.security === 'OPEN' && guarded && guard.applied ? <Badge tone="amber">Disconnect on detection</Badge> : network.security === 'OPEN' ? <Badge tone="amber">Open</Badge> : network.security === 'UNKNOWN' ? <Badge>Unknown</Badge> : <Badge tone="green">Encrypted</Badge>}</td></tr>)}</tbody></table></div> : <Empty icon={Radio} title="No nearby networks reported" detail={snapshot?.detail || 'Install the updated headless agent, then request a scan.'}/>}
    </Card>
    <Card className="mt-3"><CardHead title="Wi-Fi activity" subtitle="Requests change state only after the terminal acknowledges them."/>{commands.length ? <div className="table-scroll"><table className="data-table"><thead><tr><th>Action</th><th>Result</th><th>Requested</th><th>Details</th></tr></thead><tbody>{commands.map(job => <tr key={job.id}><td>{wifiActionLabel(job)}</td><td><StateBadge state={job.state}/></td><td className="date-cell">{dateTime(job.created_at)}</td><td>{job.detail}</td></tr>)}</tbody></table></div> : <Empty title="No Wi-Fi requests yet" icon={Wifi}/>}</Card>
    {dialog && <Modal title={`${dialog.blocked ? 'Enable' : 'Disable'} ${dialog.action === 'guard' ? 'open-network guard' : 'full Wi-Fi protection'}?`} busy={sending} onClose={() => setDialog(null)}><p className="dialog-summary">Apply this policy to <strong>{m.selected?.label || m.selected?.model}</strong>.</p><ul className="effect-list">{(dialog.action === 'guard' ? dialog.blocked ? ['The terminal must currently use a secured Wi-Fi network.', 'All nearby networks remain visible; secured-network selection remains available when full Wi-Fi protection is off.', 'On the A920, open attempts are disconnected on detection. A brief connection is possible.', 'Full Wi-Fi protection and USB/device protection will not be changed.'] : ['The open-network guard will stop rejecting connections.', 'Any separate full Wi-Fi protection stays unchanged.', 'Saved open profiles are restored only when both Wi-Fi controls are off.'] : dialog.blocked ? ['The terminal must currently use a secured Wi-Fi network.', 'Local Wi-Fi changes will be locked. Saved secured networks remain available.', 'The separate open-network guard and USB/device protection stay unchanged.'] : ['The previous local Wi-Fi settings restriction will be restored.', 'The separate open-network guard stays unchanged.', 'Saved open profiles are restored only when both Wi-Fi controls are off.']).map(text => <li key={text}><Check size={14}/>{text}</li>)}</ul><div className="modal-actions"><button className="button white" disabled={sending} onClick={() => setDialog(null)}>Cancel</button><button className="button primary" disabled={sending || dialog.blocked && !(dialog.action === 'guard' ? canEnableGuard : canEnable)} onClick={() => send(dialog.action, dialog.blocked)}>{sending ? <LoaderCircle size={14} className="spin"/> : <ShieldCheck size={14}/>}Confirm</button></div></Modal>}
  </>;
}
