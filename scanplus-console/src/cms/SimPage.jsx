import React, { useCallback, useEffect, useRef, useState } from 'react';
import { AlertTriangle, BellRing, CheckCheck, LoaderCircle, Smartphone } from 'lucide-react';
import { acknowledgeSimAlert, getSim } from '../api.js';
import { startPolling } from '../polling.mjs';
import { online } from '../model.mjs';
import { DeviceSelect } from './DevicePages.jsx';
import { Badge, Card, CardHead, CheckLine, Empty, PageHead, dateTime } from './ui.jsx';
import { maskedSim, simEventName, simReportFresh, simSlotName, simStateName } from './sim-model.mjs';

export default function SimPage({ model: m }) {
  const [report, setReport] = useState(null), [loading, setLoading] = useState(true), [error, setError] = useState('');
  const [busy, setBusy] = useState(null), [notice, setNotice] = useState(''), [filter, setFilter] = useState('all');
  const alive = useRef(true), issued = useRef(false), id = m.selectedId;
  const refresh = useCallback(async signal => {
    if (!id || m.selected?.revoked) { setLoading(false); return; }
    try {
      const value = await getSim(id, signal);
      if (!alive.current || signal?.aborted) return;
      setReport(value); setError('');
    } catch (failure) {
      if (alive.current && failure.name !== 'AbortError') setError(failure.status === 404 ? 'SIM alerts require the cumulative Windows backend and Cloudflare update.' : failure.message);
    } finally { if (alive.current && !signal?.aborted) setLoading(false); }
  }, [id, m.selected?.revoked]);
  useEffect(() => {
    alive.current = true;
    const controller = new AbortController(), polling = startPolling(() => refresh(controller.signal));
    return () => { alive.current = false; controller.abort(); polling.stop(); };
  }, [refresh]);
  async function acknowledge(eventId) {
    if (issued.current) return;
    issued.current = true; setBusy(eventId); setNotice('');
    try {
      await acknowledgeSimAlert(id, eventId);
      if (!alive.current) return;
      setNotice('Alert acknowledged. Its history is retained.');
      await Promise.all([refresh(), m.refreshAll()]);
    } catch (failure) { if (alive.current) setError(failure.message); }
    finally { issued.current = false; if (alive.current) setBusy(null); }
  }
  const snapshot = report?.snapshot, rows = (report?.alerts || []).filter(alert => filter === 'all' || (filter === 'open' ? !alert.acknowledged_at : Boolean(alert.acknowledged_at)));
  const fresh = simReportFresh(report?.reportedAt, m.now) && m.selected && online(m.selected, m.now);
  const healthy = fresh && snapshot?.readState === 'READY' && snapshot.slots?.length > 0 && snapshot.slots.every(slot => slot.state === 'ABSENT' || slot.state === 'PRESENT' && Boolean(slot.fingerprint));
  return <>
    <PageHead title="SIM Change Alerts" description="SIM removal, insertion and replacement reported by the terminal." refresh={() => refresh()} busy={loading}><DeviceSelect model={m}/></PageHead>
    {error && <div className="error-banner mb-4" role="alert"><AlertTriangle size={17}/><div><strong>SIM monitoring needs attention</strong><span>{error}</span></div><button onClick={() => refresh()}>Retry</button></div>}
    {notice && <p className="wifi-notice" role="status">{notice}</p>}
    <div className="grid grid-cols-1 xl:grid-cols-2 gap-3 mb-3">
      <Card><CardHead title="Monitoring status" subtitle={m.selected?.label || 'Choose a terminal'}><Badge tone={healthy ? 'green' : snapshot ? 'amber' : 'muted'}>{healthy ? 'Monitoring' : snapshot ? fresh ? 'Needs attention' : 'Report out of date' : 'Awaiting report'}</Badge></CardHead>
        {snapshot ? <div className="space-y-2"><CheckLine label="Baseline" value={snapshot.baselineEstablished ? 'Recorded' : 'Waiting for stable SIM state'} positive={snapshot.baselineEstablished}/><CheckLine label="Baseline established" value={dateTime(snapshot.baselineAt)}/><CheckLine label="Latest report" value={dateTime(report.reportedAt)}/><CheckLine label="Unacknowledged alerts" value={report.unacknowledgedCount || 0}/><p className="wifi-policy-help">{snapshot.detail}</p></div> : <Empty icon={Smartphone} title={loading ? 'Loading SIM status' : 'Waiting for the SIM-capable agent'} detail="SIM monitoring requires headless agent 3.5.0 or newer and the cumulative backend update."/>}
      </Card>
      <Card><CardHead title="SIM slots" subtitle="Only the identifier’s last four digits are displayed."/>{snapshot?.slots?.length ? <div className="space-y-3">{snapshot.slots.map(slot => <div key={slot.slot} className="rounded-lg border border-gray-100 p-3 dark:border-slate-700"><div className="flex flex-wrap items-center gap-2 mb-2"><strong className="text-xs">{simSlotName(slot.slot)}</strong><Badge tone={slot.state === 'PRESENT' ? 'green' : slot.state === 'ABSENT' ? 'muted' : 'amber'}>{simStateName(slot.state)}</Badge>{!slot.baselineKnown && <Badge tone="muted">Establishing baseline</Badge>}</div><p className="text-xs text-gray-500 dark:text-slate-400">{slot.state === 'PRESENT' ? `${slot.carrier || 'Carrier unavailable'} · ${maskedSim(slot.suffix)}` : slot.state === 'ABSENT' ? 'No SIM is currently reported in this slot.' : 'Waiting for readable SIM metadata; no replacement is inferred.'}</p>{slot.state === 'PRESENT' && !slot.fingerprint && <p className="wifi-policy-help">SIM identity is unavailable; replacement detection is limited.</p>}</div>)}</div> : <Empty icon={Smartphone} title="SIM slots not available" detail={snapshot?.detail || 'The terminal has not reported its SIM slots yet.'}/>}</Card>
    </div>
    <Card><CardHead title="Alert history" subtitle="Latest 200 alerts, with unacknowledged alerts first. Received times use the server clock."><div className="flex items-center gap-2"><Badge tone={report?.unacknowledgedCount ? 'red' : 'muted'}>{report ? `${report.unacknowledgedCount} unacknowledged` : 'Not reported'}</Badge><select className="cms-select" aria-label="Filter SIM alerts" value={filter} onChange={event => setFilter(event.target.value)}><option value="all">All recent alerts</option><option value="open">Unacknowledged</option><option value="acknowledged">Acknowledged</option></select></div></CardHead>
      {rows.length ? <div className="table-scroll"><table className="data-table"><thead><tr><th>Event</th><th>Slot</th><th>Previous SIM</th><th>Current SIM</th><th>Received (IST)</th><th>Status</th></tr></thead><tbody>{rows.map(alert => <tr key={alert.id}><td><span className="font-medium">{simEventName(alert.event_type)}</span>{alert.event_type === 'MONITORING_GAP' && <small>Offline alert storage reached its limit; some changes may need review.</small>}</td><td>{simSlotName(alert.slot)}</td><td>{alert.previous_suffix ? <>{maskedSim(alert.previous_suffix)}<small>{alert.previous_carrier || 'Carrier unavailable'}</small></> : '—'}</td><td>{alert.current_suffix ? <>{maskedSim(alert.current_suffix)}<small>{alert.current_carrier || 'Carrier unavailable'}</small></> : '—'}</td><td className="date-cell">{dateTime(alert.received_at)}<small title="The terminal clock can differ from the server">Detected: {dateTime(alert.observed_at)}</small></td><td>{alert.acknowledged_at ? <Badge tone="green"><CheckCheck size={12}/>Acknowledged</Badge> : <button className="button white" disabled={Boolean(busy) || !m.ready || m.selected?.revoked} onClick={() => acknowledge(alert.id)}>{busy === alert.id ? <LoaderCircle size={13} className="spin"/> : <CheckCheck size={13}/>}Acknowledge</button>}</td></tr>)}</tbody></table></div> : <Empty icon={BellRing} title={report ? 'No matching alerts' : 'Alert history not available yet'} detail={report ? 'The first stable reading establishes the baseline. Later confirmed changes appear here.' : 'Apply the cumulative backend update to receive the terminal’s queued alerts.'}/>}
      <p className="wifi-policy-help">Changes are confirmed after a short stability check. Airplane mode and unreadable SIM metadata pause comparisons. Offline changes are queued on the terminal and appear here after it reconnects.</p>
    </Card>
  </>;
}
