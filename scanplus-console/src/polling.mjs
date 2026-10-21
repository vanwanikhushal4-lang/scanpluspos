/** Sequential polling: pause hidden tabs and back off when the backend is down. */
export function startPolling(read, { interval = 10000, visibility = globalThis.document, timers = globalThis } = {}) {
  let stopped = false, timer, running = null, failures = 0;
  const hidden = () => visibility?.hidden === true;
  const clear = () => { if (timer !== undefined) timers.clearTimeout(timer); timer = undefined; };
  const refresh = () => {
    clear();
    if (stopped || hidden()) return Promise.resolve();
    if (running) return running;
    running = Promise.resolve().then(read).then(ok => { failures = ok === false ? failures + 1 : 0; }, () => { failures++; }).finally(() => {
      running = null;
      if (!stopped && !hidden()) timer = timers.setTimeout(refresh, Math.min(60000, interval * 2 ** Math.min(failures, 6)));
    });
    return running;
  };
  const changed = () => { clear(); if (!hidden()) refresh(); };
  visibility?.addEventListener('visibilitychange', changed);
  refresh();
  return { refresh, stop() { stopped = true; clear(); visibility?.removeEventListener('visibilitychange', changed); } };
}
