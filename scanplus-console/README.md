# SCANPLUS React console · Figma edition

This copy adapts the [Figma dashboard](https://www.figma.com/design/g5Tru5PI83ZEtbwtmA75Jh/Dashboard?node-id=8565-18) for the existing ScanPlus console. Application Control is the first dashboard feature. The charts show reported fleet and terminal data, including the distribution of last check-in ages.

Run this copy on its own port alongside the original. Point `SCANPLUS_ADMIN_FILE` at the existing private configuration and, for dedicated TLS, set `SCANPLUS_CA_FILE` to the existing PEM file.

```sh
npm ci
npm run build
SCANPLUS_CONSOLE_PORT=5181 SCANPLUS_ADMIN_FILE=/path/to/.scanplus-local/admin.json npm start
```

The rest of this README documents the console shared by both editions.

A React interface for ScanPlus POS Security using the supplied dashboard design, connected to the existing Windows/Cloudflare backend. Open
**http://127.0.0.1:5173/** on the administrator's Mac.

The dashboard provides terminal search, last check-in and connection status,
reported protection state, device-owner and USB-helper status, protection and
maintenance controls, command history and the management audit log. The **App
control** tab adds searchable application inventory, launch/force-stop,
suspend/resume, hide/show, uninstall protection, runtime permission controls,
APK upload/install/update and uninstall, with per-command result history.
All device-side management runs in the headless Android agent. It has no launcher
icon or app screens; Android's management notification remains visible.
A command remains pending until the terminal reports its result. Unknown status
is not treated as confirmed maintenance. A request with an uncertain delivery
result can be retried with the same idempotency key.

The console opens directly to the Security Dashboard using the existing local
administrator access. The supplied branding, top navigation, collapsible sidebar,
light/dark themes, status cards and tables are adapted to live payment-terminal
data. Device management, app control, APK deployments, protection, command
history, audit reports and connection settings remain connected to the existing
API. See [CMS integration](../docs/frontend-cms-integration.md) for the page mapping.

## Run

Start the existing SCANPLUS HTTPS backend first. From this directory:

```sh
npm ci
npm run build
npm start
```

`npm run dev` builds and starts the same connected local console. After editing
React sources, rebuild and reload the page. This setup serves the built React
bundle rather than a separate unconnected development preview.

The console requires Node.js 22.12 or newer. Stop it with Ctrl+C. The console
and backend must remain running while you use remote controls.

## Connection

The Node server reads the existing private administrator configuration from
`../.scanplus-local/admin.json` and verifies the backend certificate with
`../.scanplus-local/server.pem`. It sends the API credential only from its server
process to that configured HTTPS origin. It is never embedded in React or saved
in browser storage. TLS verification remains enabled.

For a public HTTPS backend, run `python3 scanplus-tools/configure_cloud_console.py`
from the repository root. Enter the administrator token in its hidden local
prompt. `trustMode: public` uses Node's maintained public CA store and does not
need a local PEM file. The configuration omits the old `adminBaseUrl` override.
The console still binds only to loopback; the public backend stays on Windows.

Reads refresh every ten seconds while the page is visible. Hidden tabs pause,
failed requests back off up to a minute and audit reads refresh every thirty
seconds. Requests run sequentially so slow responses do not accumulate.

For another dedicated backend, set server-side environment variables:

| Variable | Purpose |
|---|---|
| `SCANPLUS_ADMIN_FILE` | Absolute path to a private JSON file containing `baseUrl` and `adminToken` |
| `SCANPLUS_CA_FILE` | Absolute path to the trusted backend certificate or CA in PEM format |
| `SCANPLUS_CONSOLE_PORT` | Local console port, default 5173 |

Keep credential files private and outside the frontend directory. Never put an
administrator token in a `VITE_` environment variable or source file.

## Access model

This console binds to **127.0.0.1 only** and treats processes on the administrator's
Mac as local administration. It is not a public dashboard with individual user
accounts. Do not expose or tunnel this local service to other users or networks
without adding an authenticated deployment layer.

The gateway allows only the existing device list, command history, audit and
health reads, app inventory and jobs, typed protection/app requests and bounded
APK uploads. It checks Host, Origin and fetch
metadata, requires a custom request header and rejects cross-origin management
requests. It does not proxy arbitrary device routes, URLs or shell commands.
The command UUID and expiry are preserved when forwarding requests.

Enabling protection disconnects USB and blocks new installation. Entering
maintenance requests restoration of prior policies and USB settings. Both actions
require a confirmation in the interface. The browser shows the device's reported
state rather than changing the protection label optimistically.

The existing backend and agent provisioning requirements still apply. See
[management setup](../scanplus-tools/README.md), including the USB helper's
reprovisioning requirement after a terminal reboot.

## Verification

```sh
npm test
npm run build
```

The Node tests exercise cross-origin and Host rejection, the API allowlist,
protection payload validation, command identity forwarding and stale-report
handling. The browser was checked against the actual backend for live A920 status,
search, confirmation and cancel, history, audit and connection information.
App deployment was also checked through the actual dashboard, backend and A920
using a compatible vendor-signed APK. Firmware-rejected APKs remain failed jobs.

Install/uninstall require maintenance mode; system and core apps have restricted
controls. Permission details distinguish an Android grant from an app-operation
block on firmware that retains install-time grants. The UI waits for terminal
confirmation before showing success. See the management setup for API details
and device compatibility limits.

The backend also expires pending commands when history is read, so an offline
terminal's command can appear as expired without waiting for its next check-in.

Build references: [React in an existing project](https://react.dev/learn/add-react-to-an-existing-project)
and [Vite](https://vite.dev/guide/).

The downloadable console bundle includes `dist/`. With that prebuilt bundle you
can run `node server.mjs` directly, without installing frontend dependencies.
Place the folder next to `.scanplus-local/`, or supply the two private configuration
paths above. Use `npm ci` and `npm run build` when changing React sources.
## Hosted console

The public [Netlify console](https://scanplus-pos-security.netlify.app) uses SCANPLUS
administrator sign-in and a server-side Edge gateway. Setup,
environment scope, deployment and verification are documented in
[the Netlify deployment guide](../docs/netlify-console.md). The local console
described above remains available independently.
