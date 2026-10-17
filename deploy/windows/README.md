# SCANPLUS backend on an existing Windows PC

Prepared 17 October 2026. This source directory contains Windows launch scripts
and a Cloudflare Worker gateway. Build the Java backend before using it. It contains
no live database, enrolled-device credential, administrator token, certificate
private key, or Android APK. It does not modify the working Mac or A920.

## Connection

```
A920 headless agent --HTTPS--> stable workers.dev address
                                      |
                               Cloudflare Worker
                                      |
                            Workers VPC + encrypted tunnel
                                      |
                            Windows PC, loopback:9443
                                      |
                         Java backend + local database/APKs

React console's server gateway --HTTPS--> same workers.dev address
```

Only the API gateway is published. The existing React console remains local and
keeps its administrator credential on its server. This package does not publish
an unauthenticated dashboard or open Windows/router inbound ports.

The Windows PC must stay powered, awake and connected. Its disk retains the H2
database and APK files, so no Render or Supabase account is needed. Arrange local
backups; an always-on PC alone does not provide redundancy.

## Cost and availability

Use the **Workers Free** plan. The workers.dev address and its public HTTPS
certificate do not require buying a domain. Workers Free currently allows 100,000
requests/day. One agent's 10-second check-ins are about 8,640/day, before other
management requests; monitor combined usage.

**Workers VPC is currently free during its open beta. Its pricing and API can
change.** This is a zero-hosting-fee deployment under today's free limits, not a
promise of permanently free business hosting or guaranteed uptime. Do not enable
Workers Paid, paid upgrades or paid add-ons for the requested zero-cost setup.

## 1. Start the Windows backend locally

1. Install Java 17 or newer for Windows from a maintained distribution, such as
   [Eclipse Temurin](https://adoptium.net/temurin/releases/?version=17&os=windows).
2. Clone the repository to an ordinary local directory outside OneDrive,
   EFS-encrypted folders and network drives. From its root, build and copy the
   distribution once into this deployment directory:

   ```powershell
   .\gradlew.bat -p scanplus-backend test installDist
   Copy-Item .\scanplus-backend\build\install\scanplus-security-backend .\deploy\windows\backend -Recurse
   Set-Location .\deploy\windows
   ```

   The generated `backend` directory is ignored by Git. Keep private runtime
   data on the Windows host. If a distribution already exists, stop its backend
   and back it up before deliberately replacing it.
3. Open PowerShell in `deploy\windows`. Run:

   ```powershell
   powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Initialize-Backend.ps1
   powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Start-Backend.ps1
   ```

4. Leave that window running. In another PowerShell window in the same folder:

   ```powershell
   powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Check-Backend.ps1
   ```

The initializer generates fresh credentials inside the ACL-restricted `private`
directory without printing them. It refuses to overwrite an existing config.
The backend starts with an **empty** database. A920 migration is a later step.
Logs are in `private\logs`. Do not send the private directory in chat or include
it in a shared ZIP. A plain HTTP health request without trusted proxy headers is
expected to be rejected; the supplied checker simulates the local tunnel hop.

## 2. Set up a stable Cloudflare endpoint

The account owner signs into a free [Cloudflare account](https://dash.cloudflare.com/).
Cloudflare setup is not performed by extracting this ZIP.

1. In **Workers VPC**, create a tunnel named `scanplus-windows`.
2. Choose Windows and the PC's architecture. Follow the dashboard's download and
   service-install command locally in administrator PowerShell. Keep the tunnel
   token private. Confirm the tunnel is connected.
3. Create an **HTTP VPC Service** on that tunnel. Set its target to `127.0.0.1`
   and its **HTTP port to 9443**. The HTTP connection is only over Windows
   loopback; the preceding tunnel is encrypted. Record its service ID.
4. In `cloudflare\wrangler.jsonc`, configure that service's actual ID. The
   checked-in value identifies the owner's existing deployment; another account
   needs its own service ID. The Worker binding must remain
   `SCANPLUS_BACKEND`. The Worker always targets `http://localhost:9443`; the VPC
   Service controls the actual target and must match the configuration above.
5. Install a currently supported Node.js release and open PowerShell in the
   `cloudflare` folder. Run:

   ```powershell
   npx wrangler@latest login
   npx wrangler@latest deploy
   ```

   Wrangler opens a browser for account-owner sign-in. Use Workers Free. The
   gateway needs no administrator credential or device token in its configuration.
6. Record the resulting address, typically
   `https://scanplus-backend-gateway.YOUR-SUBDOMAIN.workers.dev`.
   Keep the Worker name and account subdomain stable.
7. From another internet connection, verify `/api/health` returns `status: up`.
   `/api/admin/devices` without a credential must return 401. Only then move on
   to migration. There is no login page or dashboard served at the root URL.

The Worker allows only the existing typed SCANPLUS API routes. It strips
caller-provided forwarding headers, forwards through one fixed VPC binding,
preserves backend authentication, streams APK bodies, disables caching and
refuses backend redirects. The backend still verifies each device/admin token.

## 3. Move the existing enrollment after the endpoint works

This starter package does **not** perform these steps automatically:

- Take a consistent database backup/export from the current Mac backend and
  preserve the complete device/enrollment/command/audit records and APK files.
  Do not copy a live, open H2 database file or run two writers against it.
- Stop the empty Windows backend, import the backup and matching configuration,
  and verify its device record. Keep the source backup for rollback.
- Verify the public endpoint's TLS chain with the actual Android 7 terminal's
  supported TLS/cipher capabilities. Update the private agent endpoint and trust
  anchor while retaining its existing device ID/token, app identity and owner
  privileges. The old dedicated Mac CA alone will not validate Cloudflare's
  public certificate. Do not pin a short-lived leaf certificate or disable TLS
  hostname/certificate verification.
- Point the React console's **server-side** gateway at the new endpoint and
  its trusted CA. Keep credentials out of browser source and storage.
- Confirm fresh agent check-ins at the Windows server before finishing cutover.
  Preserve the terminal's current protection state; no automatic protection
  toggle, device reboot, app reinstall or USB sealing test is included here.

The existing privileged USB helper's terminal-reboot limitation remains separate
from backend connectivity. Moving the backend does not change that helper.

## 4. Enable automatic Windows startup

After the backend has been checked locally, run administrator PowerShell as the
same Windows user that initialized it:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Install-StartupTask.ps1
```

The task runs under that account with limited privileges using S4U, without
storing its password. Company policy must allow batch logon/S4U tasks. The task
refuses to replace an existing task of the same name. If registration is blocked,
the manual launcher remains available; ask your Windows administrator about the
policy rather than changing it blindly.

Stop the manually running backend with Ctrl+C, then run:

```powershell
Start-ScheduledTask -TaskName 'SCANPLUS-Backend'
Get-ScheduledTaskInfo -TaskName 'SCANPLUS-Backend'
```

The task starts on boot and retries failed exits. Ensure system JAVA_HOME points
to the installed JDK/JRE, and configure Windows not to sleep during hosting.
The cloudflared service is configured separately by its installation command.
Do not reboot the A920 to test Windows startup.

## Verification and sources

Gateway unit checks can be run without third-party dependencies:

```powershell
node --test .\cloudflare\proxy.test.mjs
```

Gateway unit checks and an isolated Java backend check passed during preparation.
Deployment reports and live device data are not included in this source repository.
The administrator has also reported a working Windows endpoint. Terminal cutover
remains separate; see the migration plan for current status and acceptance gates.

- [Workers VPC setup](https://developers.cloudflare.com/workers-vpc/get-started/)
- [Workers VPC pricing: free during beta](https://developers.cloudflare.com/workers-vpc/reference/pricing/)
- [Workers Free pricing and quotas](https://developers.cloudflare.com/workers/platform/pricing/)
- [workers.dev routing](https://developers.cloudflare.com/workers/configuration/routing/workers-dev/)
- [Windows scheduled-task principals](https://learn.microsoft.com/en-us/powershell/module/scheduledtasks/new-scheduledtaskprincipal)
