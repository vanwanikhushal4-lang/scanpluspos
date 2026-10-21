import { useCallback, useEffect, useRef, useState } from 'react';
import { api, getDevices, getCommands, getAudit, getApps, getAppCommands, setProtection } from '../api.js';
import { startPolling } from '../polling.mjs';

const finished = new Set(['SUCCEEDED', 'FAILED', 'EXPIRED', 'CANCELLED']);
export function useConsole(view) {
  const [devices, setDevices] = useState([]), [selectedId, setSelectedId] = useState(null);
  const [commands, setCommands] = useState([]), [audit, setAudit] = useState([]);
  const [inventory, setInventory] = useState(null), [appJobs, setAppJobs] = useState([]);
  const [info, setInfo] = useState(null), [ready, setReady] = useState(false), [loading, setLoading] = useState(true);
  const [error, setError] = useState(''), [commandError, setCommandError] = useState(''), [auditError, setAuditError] = useState(''), [inventoryError, setInventoryError] = useState('');
  const [now, setNow] = useState(Date.now()), [lastRefresh, setLastRefresh] = useState(null);
  const [refreshing, setRefreshing] = useState(false), [dialog, setDialog] = useState(null), [sending, setSending] = useState(false);
  const [pending, setPending] = useState({}), [uncertain, setUncertain] = useState(null), [toast, setToast] = useState(null);
  const reading = useRef(false), issued = useRef(false), selection = useRef(null), epoch = useRef(0), notified = useRef(new Set());
  selection.current = selectedId;
  const selected = devices.find(device => device.id === selectedId) || null;

  const refreshDevices = useCallback(async signal => {
    if (reading.current) return;
    reading.current = true;
    try {
      const rows = await getDevices(signal);
      if (signal?.aborted) return;
      setDevices(rows); setReady(true); setError(''); setLastRefresh(Date.now());
      setSelectedId(id => rows.some(device => device.id === id) ? id : rows.find(device => !device.revoked)?.id || rows[0]?.id || null);
    } catch (failure) { if (failure.name !== 'AbortError') { setReady(false); setError(failure.message); } return false; }
    finally { reading.current = false; if (!signal?.aborted) setLoading(false); }
  }, []);
  const refreshCommands = useCallback(async (id, signal) => {
    if (!id) return;
    const version = epoch.current;
    try {
      const rows = await getCommands(id, signal);
      if (signal?.aborted) return;
      if (selection.current === id && version === epoch.current) { setCommands(rows); setCommandError(''); }
      setPending(current => {
        const prior = current[id]; if (!prior) return current;
        const found = rows.find(item => item.id === prior.id || item.request_id === prior.request_id);
        return found ? { ...current, [id]: found } : current;
      });
      setUncertain(current => current?.deviceId === id && rows.some(item => item.request_id === current.requestId) ? null : current);
    } catch (failure) { if (failure.name !== 'AbortError' && selection.current === id) setCommandError(failure.message); return false; }
  }, []);
  const refreshAudit = useCallback(async signal => {
    try { const rows = await getAudit(signal); if (!signal?.aborted) { setAudit(rows); setAuditError(''); } }
    catch (failure) { if (failure.name !== 'AbortError') setAuditError(failure.message); return false; }
  }, []);
  const refreshInventory = useCallback(async (id, signal) => {
    if (!id) return;
    try {
      const [apps, jobs] = await Promise.all([getApps(id, signal), getAppCommands(id, signal)]);
      if (!signal?.aborted && selection.current === id) { setInventory(apps); setAppJobs(jobs); setInventoryError(''); }
    } catch (failure) { if (failure.name !== 'AbortError' && selection.current === id) setInventoryError(failure.message); return false; }
  }, []);
  useEffect(() => {
    const controller = new AbortController(), poll = startPolling(() => refreshDevices(controller.signal));
    api('/api/console/info', { signal: controller.signal }).then(setInfo).catch(() => {});
    return () => { controller.abort(); poll.stop(); };
  }, [refreshDevices]);
  useEffect(() => { const clock = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(clock); }, []);
  useEffect(() => {
    setCommands([]); setCommandError(''); setInventory(null); setAppJobs([]); setInventoryError('');
    if (!selectedId || selected?.revoked) return;
    const controller = new AbortController(), poll = startPolling(() => refreshCommands(selectedId, controller.signal));
    return () => { controller.abort(); poll.stop(); };
  }, [selectedId, selected?.revoked, refreshCommands]);
  useEffect(() => {
    const controller = new AbortController(), poll = startPolling(() => refreshAudit(controller.signal), { interval: 30000 });
    return () => { controller.abort(); poll.stop(); };
  }, [refreshAudit]);
  useEffect(() => {
    if (!selectedId || selected?.revoked || !['dashboard', 'reports', 'devices', 'settings'].includes(view)) return;
    const controller = new AbortController(), poll = startPolling(() => refreshInventory(selectedId, controller.signal), { interval: 30000 });
    return () => { controller.abort(); poll.stop(); };
  }, [selectedId, selected?.revoked, view, refreshInventory]);
  useEffect(() => {
    for (const command of Object.values(pending)) {
      if (!finished.has(command.state) || notified.current.has(command.id)) continue;
      notified.current.add(command.id);
      setToast({ tone: command.state === 'SUCCEEDED' ? 'success' : 'error', message: command.state === 'SUCCEEDED' ? `${command.enabled ? 'Protection' : 'Maintenance'} confirmed by the terminal.` : command.detail || `Command ${command.state.toLowerCase()}.` });
    }
  }, [pending]);
  useEffect(() => { if (!toast) return; const timer = setTimeout(() => setToast(null), 7000); return () => clearTimeout(timer); }, [toast]);
  async function refreshAll() {
    setRefreshing(true);
    try { await Promise.all([refreshDevices(), refreshCommands(selectedId), refreshAudit(), ...(['dashboard', 'reports', 'devices', 'settings'].includes(view) ? [refreshInventory(selectedId)] : [])]); }
    finally { setRefreshing(false); }
  }
  async function submit(change, retry = null) {
    if (issued.current) return;
    issued.current = true; setSending(true);
    const request = retry || { deviceId: change.device.id, requestId: crypto.randomUUID(), enabled: change.enabled, ttlSeconds: 120 };
    try {
      const command = await setProtection(request.deviceId, { requestId: request.requestId, enabled: request.enabled, ttlSeconds: request.ttlSeconds });
      epoch.current++; setPending(current => ({ ...current, [request.deviceId]: command }));
      if (selection.current === request.deviceId) setCommands(current => [command, ...current.filter(item => item.id !== command.id)]);
      setUncertain(null); setDialog(null); setToast({ tone: 'info', message: 'Request sent. Waiting for the terminal to acknowledge.' });
      refreshDevices(); refreshCommands(request.deviceId);
    } catch (failure) {
      setDialog(null);
      if (!failure.status || failure.status >= 500) setUncertain({ ...request, message: failure.message });
      else setToast({ tone: 'error', message: failure.message });
    } finally { issued.current = false; setSending(false); }
  }
  const selectedPending = pending[selectedId]?.state === 'PENDING' ? pending[selectedId] : commands.find(command => command.state === 'PENDING');
  const actionBusy = sending || Boolean(selectedPending) || uncertain?.deviceId === selectedId;
  const canControl = Boolean(selected && !selected.revoked && selected.owner_active && selected.bridge_available && ready);
  return { devices, selected, selectedId, setSelectedId, commands, audit, inventory, appJobs, info, ready, loading, error, commandError, auditError, inventoryError, now, lastRefresh, refreshing, refreshAll, refreshCommands, refreshAudit, dialog, setDialog, sending, submit, selectedPending, actionBusy, canControl, uncertain, toast, setToast };
}
