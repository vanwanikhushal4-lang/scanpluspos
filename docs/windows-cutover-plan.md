# SCANPLUS: Windows deployment review and cutover plan

Reviewed: 17 October 2026. Status: implementation plan; no cutover performed.

## Deployment being reviewed

| Item | Value |
| --- | --- |
| Public HTTPS origin | `https://scanplus-backend-gateway.secureitpos.workers.dev` |
| Tunnel | `scanplus-windows` |
| Tunnel ID | `afdd81d2-cbff-4d42-8032-40f38c73783a` |
| Workers VPC Service | `scanplus-backend` |
| Service ID | `01a14946-a2fe-7622-8caa-915e448d82fb` |
| Private Windows target | HTTP, `127.0.0.1:9443` |
| Existing A920 serial | `<TERMINAL_SERIAL>` |
| Existing enrolled device ID | `<ENROLLED_DEVICE_ID>` |
| Existing Android package | `dev.velox.hardwaresecurity` |
| Current Mac backend origin | `https://<MAC_LAN_IP>:9443` |

The intended route is A920/console gateway → public HTTPS Worker → encrypted
Cloudflare Tunnel → Windows loopback HTTP → Java backend → local database/APKs.
Cloudflare documents that the tunnel remains encrypted when its final local hop
uses HTTP. The loopback target is therefore appropriate; it must remain bound to
`127.0.0.1`, with no router forwarding or LAN listener.
[VPC transport documentation](https://developers.cloudflare.com/workers-vpc/configuration/vpc-services/)

## Review results

| Check | Result and evidence |
| --- | --- |
| Live public health, Node HTTPS | Independently returned 200 and `status: up`. |
| Live public health, Java HTTPS | Independently returned 200 with certificate and hostname verification enabled. |
| TLS 1.2 | Independently negotiated successfully; observed cipher `ECDHE-ECDSA-AES128-GCM-SHA256`. This is a desktop check, not proof of Android compatibility. |
| Anonymous administrator read | Independently returned 401 at the gateway. |
| Deliberately invalid, correctly shaped administrator token | Independently returned 401 from the backend, establishing that token shape alone does not authorize access. No real credential was sent. |
| Unknown `/api/shell` route | Independently returned 404. |
| Authenticated Windows administrator read | User reports 200 with zero devices. No Windows credential was requested or independently inspected. |
| Existing Mac enrollment | Read-only administrator lookup still found the original device. Its observed state was maintenance, owner active, USB helper available. |
| Python HTTPS client | Received Cloudflare 403/error 1010, while Node and Java succeeded. This is a client compatibility issue to investigate, not evidence that every client is blocked. |
| Actual Windows startup/service configuration | User reports backend and tunnel running. The new `Start-Tunnel.ps1`, installed tasks, private ACLs and deployed Worker source are not available on this Mac for direct inspection. Check them locally before cutover. |

The existing packaged gateway preserves bearer authentication and typed API
routes, overwrites forwarding headers, uses one fixed VPC binding, disables
caching and refuses redirects. The Windows configuration enables trusted
loopback proxy handling while retaining the application's HTTPS requirement.

The Mac copy of `wrangler.jsonc` is still a starter template with an all-zero
service ID. The user reports updating the deployed Windows copy. Do not deploy
the old Mac template over the working deployment.

The observed public certificate issuer was Let's Encrypt YE2, with a leaf
expiration of 15 January 2027. Renewals should not require changing the agent:
trust supported root CAs, not that short-lived leaf certificate.

## Changes needed before cutover

1. **Enrollment data:** the Windows database is empty. The agent's existing
   device ID/token will not authenticate there until its enrollment record and
   stored token hash are migrated. Re-enrollment is unnecessary.
2. **Certificate trust:** `BackendClient.java` builds a private trust store from
   one configured certificate. It will not automatically trust Cloudflare merely
   because desktop browsers do. The endpoint and trust configuration must move
   together, with hostname verification retained.
3. **A local provisioning route:** the existing management API has no backend-URL
   change command. It also intentionally refuses to replace the management app
   through ordinary app control. Confirm an administrator-controlled local/USB
   route before scheduling agent updates or editing its private configuration.
4. **Free-plan traffic:** App control currently leaves four reads running every
   three seconds: device list, protection history, inventory and app-job history.
   A continuously open, responsive tab can generate about 115,200 requests/day,
   before agent traffic. Workers Free permits 100,000 requests/day across the
   account. Polling must change before a continuously open dashboard is used.
   [Workers limits](https://developers.cloudflare.com/workers/platform/limits/)
5. **Unattended operation:** a healthy process launched in a terminal does not
   establish that both processes will recover after Windows restarts or logout.

## Step-by-step implementation

### 1. Capture the working baseline and rollback material

- Record the existing device ID, serial, app version, signing-certificate
  fingerprint, owner component, current protection/USB-helper status and fresh
  Mac check-in time.
- Preserve the original private agent configuration and console configuration
  in access-controlled local backups. Do not print credentials or put them into
  this plan, a Worker, React code, source control or chat.
- Confirm the local provisioning connection works before changing anything.
  If USB is unplugged, reconnect it for this one-time work if needed. Do not
  automatically change protection to obtain access.
- Verify the Windows backend JARs match the supplied distribution, particularly
  H2. The local source distribution uses `h2-2.3.232.jar`.
- Inspect the actual Windows launchers, service/task settings, listener binding
  and private-directory ACLs locally. Preserve the working deployment IDs.

**Proceed when:** rollback files and an authorized local recovery route are
available, the two backend builds match, and the terminal's current state is
recorded. No protection test or terminal reboot is part of this step.

### 2. Prepare public TLS trust without abandoning the current server

- Implement an explicit public-CA trust option in the headless agent. Load a
  maintained, verified public root bundle as an app asset; the provisioning JSON
  currently has a 16 KiB limit and should not carry a complete public CA catalog.
- Preserve the existing dedicated Mac CA for rollback. Keep TLS 1.2 support,
  normal hostname verification, credential-free URLs and redirect rejection.
- Test trusted public chains, rejected untrusted/mismatched certificates and the
  legacy dedicated-CA connection. Obtain public roots from maintained official
  sources and check the actual served chain; do not assume Android 7 already
  contains newer roots.
- Prepare an in-place agent update with the same package, signer and device-owner
  component and an accepted higher version code. Keep zero launcher activities.
  Prepare a compatible rollback build before installation; an older version-code
  APK is not a reliable Android rollback.
- Use the approved administrator provisioning route for this management-agent
  update. Its ordinary app-control installer intentionally rejects self-update.
  Do not uninstall the agent, clear its data or reset device-owner enrollment.
- Initially retain the old backend origin. Confirm fresh old-server check-ins,
  owner status, helper availability and unchanged protection after the update.

**Proceed when:** the staged agent still works with the Mac and an on-device
HTTPS probe using the proposed public trust configuration receives the gateway's
JSON health response. Desktop Java success alone does not satisfy this gate.

A configuration-only move using a single validated public root is technically
possible with the current APK. The CA-bundle approach is recommended for the
stated reliability goal because the current single-certificate loader cannot
handle multiple trust anchors across certificate-provider changes. If a safe
in-place update is unavailable, stop at this gate and review that tradeoff.

### 3. Prepare the console and free-plan request budget separately

- Stage console changes without switching its running backend yet.
- Poll device state approximately every 10 seconds; refresh app inventory and
  audit approximately every 30 seconds. Poll command results faster only while
  a command is pending. Pause unnecessary polling in hidden tabs and back off
  after network errors; keep immediate user-triggered refresh.
- Update the displayed refresh intervals and connection text to match actual
  behavior. Keep the current layout and branding.
- Keep the agent's normal 10-second check-ins initially. One agent contributes
  roughly 8,640 check-ins/day plus inventory and command traffic. The agent
  currently does not use the reply's `pollSeconds`, so changing that server field
  alone would not change its cadence.
- Create a separate private cloud-console configuration. Set its effective
  administrator origin to the public HTTPS origin above, without port 9443.
  Account for `adminBaseUrl` taking precedence over `baseUrl`; leaving the old
  loopback override would keep directing the console to the Mac.
- Use the Windows administrator credential only in the console server's private
  configuration. Supply verified public roots for this connection rather than
  the old dedicated Mac certificate. Keep the original Mac files for rollback.
- Keep the React console bound to loopback. Publishing a multi-user web dashboard
  with login/session management is separate work.

**Proceed when:** staged console tests pass, secrets remain server-side, and the
estimated total traffic leaves headroom under the account's free quota.

### 4. Freeze new jobs and produce a consistent migration backup

- Temporarily stop issuing controls, installations and new enrollments. Drain
  pending/running commands. Do not replay outstanding app jobs during migration.
- Gracefully stop the Mac backend for the final consistent copy, verifying the
  Java process has exited. The agent will retry; do not reboot it or alter its
  protection policies.
- Back up the closed H2 database and APK directory, preserving all device/token
  hashes, command IDs, inventories, APK metadata and audit history. Include a
  manifest with file sizes and SHA-256 checksums.
- Transfer the backup and required database credential privately in an encrypted
  package. A database login password by itself is not file-at-rest encryption.
- Keep the Mac's original data intact. Do not run the H2 backup tool or copy its
  live database file while that file is open. If the backend versions differ,
  use a separately rehearsed logical export/import instead of raw file copying.

**Proceed when:** backup integrity is verified and a restore path is available.
[H2 backup guidance](https://h2database.com/html/tutorial.html#upgrade_backup_restore)

### 5. Restore the existing enrollment on Windows

- Stop the Windows backend, retaining its currently empty database as a backup.
  Recheck that it has no newly enrolled devices before replacing it.
- Restore the verified Mac database under the Windows private database path and
  copy its matching APK files under the Windows private APK path.
- Set the Windows database password to the credential required by the restored
  database. Retain the Windows administrator token independently; it is not the
  device token and does not have to be replaced with the Mac administrator token.
- Retain the working Cloudflare/loopback configuration. Do not copy the old Mac
  bind address, TLS keystore or private CA key into Windows server settings.
- Resolve any remaining pending/running jobs in the imported copy explicitly
  before the terminal joins; preserve history and request IDs, avoiding replay.
- Restart Windows backend and verify health plus an authenticated device read.
  Confirm the exact original device ID/serial, stored state and imported records.
  Last-seen time will remain stale until the agent actually connects.

**Proceed when:** Windows shows the existing enrollment and APK checksums match;
missing/invalid credentials still receive 401. Keep the old Mac backend stopped
during cutover so the two database copies do not accumulate conflicting updates.

### 6. Atomically switch the A920's endpoint and trust mode

- Through the confirmed local provisioning route, atomically update its private
  bootstrap origin to
  `https://scanplus-backend-gateway.secureitpos.workers.dev` and select the tested
  public trust configuration.
- Preserve the existing device ID, device token, serial, private preferences,
  ownership and command-result history. Keep correct app ownership and private
  file permissions. Preserve a restorable old configuration.
- Let the existing service reload configuration on its next cycle. Do not
  reinstall, reset or reboot the terminal for this address change.
- Observe several fresh check-ins in Windows, plus a fresh inventory report.
  Verify owner/helper/protection status against the baseline. Allow the existing
  retry delay and network timeouts; use approximately two minutes as the initial
  observation window before diagnosing failure.

**Proceed when:** the same enrolled device is updating Windows records and its
local policies remain consistent with the baseline. Any failure triggers the
rollback procedure below rather than further blind configuration changes.

### 7. Switch the console's server gateway

- Start the prepared console using its separate cloud administrator/CA files.
  Preserve the original local-backend launch configuration.
- Confirm that the reported backend is the workers.dev origin, that the original
  device is displayed, and that last-seen/history/inventory match Windows.
- Confirm the browser contains no administrator token and management requests
  still go through the local console server.
- Use an inventory-refresh command to verify request → backend → agent → result
  without changing protection. Do not automatically enable protection, seal USB,
  uninstall applications or cycle maintenance as a migration test.

**Proceed when:** the console displays fresh Windows-backed status and a
non-policy-changing management round trip completes successfully.

### 8. Verify unattended recovery and finish the handover

- Ensure the backend startup task and cloudflared service start without an open
  terminal or an interactive login, restart after failure, and reference the
  intended private configuration. Check the new tunnel launcher for safe token
  handling locally.
- In a controlled window, restart **Windows only**, and observe tunnel recovery,
  backend health and the A920's subsequent check-in. Leave the A920 powered on;
  its privileged helper has a separate terminal-reboot limitation.
- Verify connectivity from another internet connection and observe continued
  device check-ins with the Mac backend stopped and USB disconnected.
- Address the Python/error-1010 incompatibility before relying on that CLI.
  Compare the actual approved client behavior with Cloudflare security events.
  Use supported, narrowly scoped owner controls where available, retaining API
  authentication; do not globally remove protections or assume workers.dev has
  every custom-zone WAF control. Re-test the actual Android agent as well.
- Set up consistent database/APK backups and a restore drill, and monitor host
  disk space, certificate/agent errors, last check-in and free-plan usage.
  These are planned operational tasks, not newly created automations.
- Preserve the old Mac backup for an agreed retention period. Record final
  deployment versions, startup instructions and rollback steps.

**Complete when:** the same device remains managed through Windows without the
Mac backend or USB, normal outage recovery works, policy state is preserved,
and backup/restore and quota handling are documented.

## Rollback

Before the agent switches, retain the Mac as the authoritative database and stop
the cutover if a gate fails. After the final copy, keep control activity frozen
until the migration is accepted or rolled back.

If the agent cannot connect, restore its original private endpoint/trust
configuration through the retained local route, restart the original Mac backend
with its original files, and confirm fresh check-ins. Restore the original
console configuration. Do not clear agent data, revoke its credential or create
a new enrollment to recover connectivity.

If controls have already executed on Windows, freeze further activity and
reconcile or migrate the newer Windows records before returning to the older Mac
snapshot. Reverting the database alone does not reverse device-side operations.

## Availability boundary

This removes dependence on the Mac's local Wi-Fi address and uptime. Windows
power, disk, sleep/restart behavior and internet access remain dependencies.
Workers VPC is currently free in beta and its pricing/API can change; Workers
Free quotas still apply. This design is suitable for the requested zero-cost
initial company deployment, with those limits explicitly tracked.
[Workers VPC pricing](https://developers.cloudflare.com/workers-vpc/reference/pricing/)

Cloudflare documents error 1010 as a browser-signature block. Its BIC documentation
also discusses non-browser clients, which explains why actual client testing is
needed rather than treating a successful browser visit as sufficient.
[Error 1010](https://developers.cloudflare.com/support/troubleshooting/http-status-codes/cloudflare-1xxx-errors/error-1010/)
[Browser Integrity Check](https://developers.cloudflare.com/waf/tools/browser-integrity-check/)

Modern public CA chains can differ from an older Android installation's trust
store. Use maintained roots and verified chain behavior during implementation.
[Let's Encrypt certificate chains](https://letsencrypt.org/ca/certificates/)
