// Deploy from any working directory without placing private values in shell
// history, source files, browser assets or deployment output.
import { readFile, writeFile } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
let auth;
try { auth = JSON.parse(await readFile(resolve(root, '.scanplus-local/netlify-console-auth.json'), 'utf8')); }
catch { throw new Error('The private Netlify console authentication configuration is missing.'); }
if (!/^[a-f0-9]{64}$/.test(auth.keyHash || '') || !/^[a-f0-9]{64}$/.test(auth.sessionSecret || '')) throw new Error('The private console authentication configuration is invalid.');
const cli = process.env.SCANPLUS_NETLIFY_CLI;
const executable = cli ? process.execPath : 'npm';
const prefix = cli ? [cli] : ['exec', '--registry=https://registry.npmjs.org', '--yes', '--package', 'netlify-cli@27.13.0', '--', 'netlify'];
const args = [...prefix, 'deploy', '--prod', '--context', 'production', '--site', '96cdb776-c113-443f-a1f9-5b1c2cda0464', '--message', 'ScanPlus POS Security console', '--json', '--secret-env', `SCANPLUS_LOGIN_KEY_HASH=${auth.keyHash}`, '--secret-env', `SCANPLUS_SESSION_SECRET=${auth.sessionSecret}`];
console.log('Building and deploying the authenticated ScanPlus POS Security console…');
const result = await new Promise((done, reject) => {
  const child = spawn(executable, args, { cwd: resolve(root, 'scanplus-console'), stdio: ['ignore', 'pipe', 'pipe'] });
  let output = '', errors = '';
  child.stdout.on('data', chunk => output += chunk); child.stderr.on('data', chunk => errors += chunk);
  child.on('error', reject); child.on('close', code => done({ code, output, errors }));
});
await writeFile(resolve(root, '.scanplus-local/netlify-deploy.log'), result.output + result.errors, { mode: 0o600 });
if (result.code) throw new Error('Netlify deployment failed. Details are in the private netlify-deploy.log file.');
let deploy; try { deploy = JSON.parse(result.output); } catch { throw new Error('The CLI did not return a deployment record. Check the private log.'); }
const record = Object.fromEntries(['site_id', 'site_name', 'deploy_id', 'deploy_url', 'url', 'logs'].map(key => [key, deploy[key]]));
if (!record.deploy_id || record.url !== 'https://scanplus-pos-security.netlify.app') throw new Error('The CLI did not confirm the expected production site.');
await writeFile(resolve(root, '.scanplus-local/latest-netlify-deploy.json'), JSON.stringify(record, null, 2) + '\n', { mode: 0o600 });
console.log(JSON.stringify(record, null, 2));
