#!/usr/bin/env node
import { readFile } from 'node:fs/promises';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { backendReader } from '../scanplus-console/server.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
export async function request(path, body, method = body === undefined ? 'GET' : 'POST') {
  const config = JSON.parse(await readFile(resolve(root, '.scanplus-local/admin.json'), 'utf8'));
  if (config.trustMode !== 'public') throw new Error('This helper requires the public HTTPS configuration.');
  const read = backendReader({ backendUrl: config.baseUrl, adminToken: config.adminToken });
  const reply = await read({ method, path, payload: body === undefined ? undefined : JSON.stringify(body) });
  if (reply.status < 200 || reply.status >= 300) throw new Error(`Backend returned HTTP ${reply.status}.`);
  return reply.value;
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const routes = { devices: '/api/admin/devices', audit: '/api/admin/audit', health: '/api/health' };
  const path = routes[process.argv[2]];
  if (!path) { console.error('Usage: node scanplus-tools/cloud_admin.mjs devices|audit|health'); process.exitCode = 1; }
  else try { console.log(JSON.stringify(await request(path), null, 2)); }
  catch (error) { console.error(error.message); process.exitCode = 1; }
}
