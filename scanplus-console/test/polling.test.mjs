import { test } from 'node:test';
import assert from 'node:assert/strict';
import { startPolling } from '../src/polling.mjs';

function fixture() {
  const jobs = new Map(), listeners = new Set(); let next = 0;
  const timers = { setTimeout(fn, delay) { const id = ++next; jobs.set(id, { fn, delay }); return id; }, clearTimeout(id) { jobs.delete(id); } };
  const visibility = { hidden: false, addEventListener(_, fn) { listeners.add(fn); }, removeEventListener(_, fn) { listeners.delete(fn); } };
  return { jobs, listeners, timers, visibility, change(hidden) { visibility.hidden = hidden; for (const fn of listeners) fn(); }, tick() { const [id, job] = jobs.entries().next().value; jobs.delete(id); return job.fn(); } };
}
const settle = () => new Promise(resolve => setImmediate(resolve));
test('polling waits for slow requests and stops without scheduling another', async () => {
  const f = fixture(); let release, calls = 0;
  const poll = startPolling(() => { calls++; return new Promise(resolve => { release = resolve; }); }, f);
  await settle(); assert.equal(calls, 1); assert.equal(f.jobs.size, 0);
  poll.refresh(); await settle(); assert.equal(calls, 1);
  poll.stop(); release(); await settle(); assert.equal(f.jobs.size, 0); assert.equal(f.listeners.size, 0);
});
test('hidden tabs pause requests and resume immediately when visible', async () => {
  const f = fixture(); let calls = 0;
  const poll = startPolling(() => { calls++; }, f);
  await settle(); assert.equal(f.jobs.values().next().value.delay, 10000);
  f.change(true); assert.equal(f.jobs.size, 0); await poll.refresh(); assert.equal(calls, 1);
  f.change(false); await settle(); assert.equal(calls, 2);
  poll.stop();
});
test('failures back off to a minute and success restores normal cadence', async () => {
  const f = fixture(); let ok = false;
  const poll = startPolling(() => ok, f); await settle();
  for (const delay of [20000, 40000, 60000, 60000]) { assert.equal(f.jobs.values().next().value.delay, delay); await f.tick(); }
  ok = true; await f.tick(); assert.equal(f.jobs.values().next().value.delay, 10000);
  poll.stop();
});
