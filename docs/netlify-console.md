# Public ScanPlus POS Security console on Netlify

Console: https://scanplus-pos-security.netlify.app

The production site is public. Visitors see the SCANPLUS administrator sign-in
page; device information and management operations require a valid session.
Deploy Previews remain Private. No Netlify account is needed to use production.

## Current deployment

- Netlify project: `scanplus-pos-security`, team `myplanetguard`, Free plan
- Site ID: `96cdb776-c113-443f-a1f9-5b1c2cda0464`
- Production deploy: `6ad5e56cc6cd13e9229fc775`, 19 October 2026
- Frontend: React static assets plus two Netlify Edge Functions
- Backend: existing Windows backend through the Cloudflare gateway
- Android: existing headless agent; no device policy changes made

The frontend title, navigation, sidebar and sign-in identify the product as
ScanPlus POS Security. The Netlify project and production address use
`scanplus-pos-security`. The exact sign-in origin was updated with the rename;
existing administrator credentials remain valid, but the new domain requires
signing in again. All 36 console tests and the production build passed. Live API
checks confirmed administrator sign-in and authenticated device access, and
confirmed that anonymous device access remains blocked.

The Wi-Fi page has independent Full Wi-Fi protection and Block open Wi-Fi
connections switches. The latter stays unavailable until the updated backend
and headless agent 3.4.0 report support. On the Android 7 A920 it uses a local
disconnect-on-detection guard and explicitly describes the possible brief
association before rejection. Open networks remain visible in scan results.

## SIM-change alert release

The SIM Change Alerts page (`/#sim`) was published with the 19 October release.
It shows per-slot status, baseline time, masked identities and alert history,
with administrator acknowledgement and an unacknowledged-alert fleet banner.
Headless agent 3.5.0 is installed on the A920. Its managed phone-state permission
and both empty-slot baselines were verified without generating false alerts.
The prior open Wi-Fi guard remains enabled and other policy state was preserved.

Cloud SIM reports require the cumulative Windows backend and Cloudflare route
update. These steps remain pending. The latest public health probe returned
HTTP 503, so this release does not claim end-to-end cloud alert verification.
No physical SIM swap was tested, at the owner's request. See
[SIM monitoring](sim-change-alerts.md) and the update package's README.

## Administrator sign-in

The initial administrator access key is saved privately on the administrator Mac
in `.scanplus-local/SCANPLUS-Console-Access.txt`, with owner-only file permissions.
It is a randomly generated 256-bit key. The browser sends it over HTTPS during
sign-in; Netlify stores its SHA-256 verifier rather than the original key. This
scheme requires the generated random key, rather than a short chosen password.

Sign-in sets a host-only, Secure, HttpOnly, SameSite=Strict cookie that expires
after eight hours. The cookie is signed with HMAC-SHA-256 and bound to the exact
production origin. Device data is requested only after authentication. Expired
sessions return to sign-in and stop the console's data polling. The profile menu
provides Sign out, which clears that browser's session cookie.

The session endpoint permits 20 requests per minute per IP/domain. The management
API permits 180. The API also requires the console header and same-origin writes,
validates its existing route/payload allowlist, blocks redirects and keeps the
backend bearer credential out of the browser. Anonymous requests, forged/expired
sessions and other origins cannot reach management operations.

## Private configuration

Existing production variables in the Netlify project:

| Variable | Purpose |
| --- | --- |
| `SCANPLUS_OWNER_ID` | Verified Netlify owner fallback for private deployments |
| `SCANPLUS_CONSOLE_ORIGIN` | Exact production HTTPS origin |
| `SCANPLUS_BACKEND_URL` | Existing Cloudflare HTTPS backend origin |
| `SCANPLUS_ADMIN_TOKEN` | Existing backend administrator token, flagged secret |

The deploy helper supplies these two additional secret variables to the deployed
Functions scope using Netlify CLI's `--secret-env` support:

| Variable | Purpose |
| --- | --- |
| `SCANPLUS_LOGIN_KEY_HASH` | SHA-256 verifier of the random administrator key |
| `SCANPLUS_SESSION_SECRET` | HMAC signing key for browser sessions |

Their source configuration is `.scanplus-local/netlify-console-auth.json`, which
has owner-only permissions and is ignored by Git. It contains the verifier and
session secret, not the original access key. It is never part of `dist`.
Deployment runtime variables continue working when this Mac is offline.

Free accounts use default scopes for project environment variables. The backend
token is excluded from post processing by Netlify's secret flag. No private
values use `VITE_` prefixes, are copied to public assets, or appear in source or
`netlify.toml`.

## Deploy future changes

Use Node 24. From the repository root:

```sh
npm --prefix scanplus-console ci
npm --prefix scanplus-console test
node scanplus-tools/deploy_netlify_console.mjs
```

The helper uses official Netlify CLI 27.13.0, builds the frontend, packages both
Edge Functions and applies the private authentication configuration for each
production deployment. It does not print secrets. It requires the existing
Netlify CLI login and private configuration. For an already-installed CLI script,
`SCANPLUS_NETLIFY_CLI` can point to its `bin/run.js`.

Use this helper for subsequent deployments. A plain deployment without its
secret environment overrides will not carry the public sign-in configuration.
Latest deployment metadata and logs are saved in the ignored `.scanplus-local/`
directory. Automatic Git deployments are not configured.

The local `node scanplus-console/server.mjs` console stays loopback-only and opens
directly without this public sign-in. Both management gateways share the same
route and command-payload validation policy.

## Verification completed

- All 36 console tests and the production build passed.
- An anonymous request to the production homepage returned 200.
- Anonymous device API access and an invalid access key returned 401.
- A valid sign-in returned a secure cookie and authenticated API access returned
  the real A920.
- Browser sign-in displayed the terminal and its 87-app inventory; browser sign-out
  returned to the public sign-in page.
- Published files were checked against the actual private values; none contained
  the backend token, access key, verifier or signing secret.
- Protection state and the headless Android agent were preserved.

No live APK upload or device policy change was used for this publication. The
streaming upload path retains its 64 MiB application limit and is covered by a
7 MiB unit test. Uploads also remain subject to the Edge response timeout.

## Operation and rotation

Keep the Windows PC awake and online, with its backend and Cloudflare tunnel
running. Netlify Free has a monthly usage allowance; no paid plan or domain was
purchased. The Mac is not needed for ongoing device connectivity.

Treat the access key as a full administrator credential. To replace it, generate
another random 32-byte base64url key, update the private verifier and access file,
and deploy with the helper. Replacing the session secret and redeploying
invalidates existing sessions, including those in other browsers. Signing out
alone clears the current browser's cookie. Keep private config backups secure.

Official references: [visibility](https://docs.netlify.com/manage/security/secure-access-to-sites/project-visibility/),
[Edge environment](https://docs.netlify.com/build/edge-functions/environment-variables/),
[Edge limits](https://docs.netlify.com/build/edge-functions/limits/).
