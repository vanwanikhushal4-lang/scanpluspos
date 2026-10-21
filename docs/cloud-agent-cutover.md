# Headless agent and public backend

Agent 3.2.0 (version code 10) supports an explicit public HTTPS trust mode.
It keeps the existing application ID, administrator receiver and signing identity.
Use a signed in-place update; uninstalling a device-owner app or clearing its
data is not a migration step. The manifest contains no activities or launcher.
The ongoing Android management notification remains available.

## Prepare

1. Verify the Windows backend and encrypted Cloudflare tunnel are running.
   Check the public `/api/health` and confirm unauthenticated administrator
   requests return 401.
2. Back up the terminal's private bootstrap and protection preferences securely.
   Preserve the old signed APK, backend database, administrator configuration
   and dedicated CA for rollback. Never commit them or include them in a public ZIP.
3. Build with the existing private signing key:

   ```sh
   ./gradlew :scanplus-agent:testReleaseUnitTest :scanplus-agent:assembleRelease :scanplus-agent:lintRelease
   ```

4. Verify the APK signer matches the installed APK and the manifest remains
   headless before installation. Preserve the current protection state. Do not
   automatically toggle protection or reboot the terminal during cutover.

## HTTPS trust

The private bootstrap remains limited to 16 KiB. For public hosting it contains:

```json
{
  "baseUrl": "https://YOUR-GATEWAY.workers.dev",
  "trustMode": "public",
  "serial": "YOUR-TERMINAL-SERIAL",
  "enrollmentCode": "SHORT-LIVED-ONE-TIME-CODE"
}
```

Public CA roots ship in `scanplus-agent/src/main/assets/public-roots.crt`.
Its notice records the source, license, date and checksum. This supplies current
public roots on old Android firmware. Certificate-chain and hostname validation
remain enabled, TLS 1.2 is required and redirects are refused. No short-lived
Cloudflare leaf certificate is pinned. Review and update the bundled roots and
the public trust cache key together during maintenance.

Existing dedicated backends remain supported: omit `trustMode` (or set it to
`dedicated`) and provide `caPem`. Unknown modes, empty bundles and non-CA trust
certificates fail closed. Trust factories are cached without caching tokens or
enrollment identity.

## Enroll and connect the console

1. Update the installed agent without uninstalling it. Retain private app data,
   Android UID, device-owner assignment and protection preferences.
2. For a fresh Windows database, create a short-lived enrollment bound to the
   terminal's exact serial immediately before provisioning. Deliver only that
   code to the terminal's private bootstrap, with app ownership and mode 0600.
   The agent exchanges it for its own device credential and removes the code.
   This creates a new server-side identity; it does not import old command or
   audit history. Preserve the old database separately. An existing-database
   migration instead retains the existing device ID and token.
3. Configure the Mac console privately:

   ```sh
   python3 scanplus-tools/configure_cloud_console.py
   ```

   Enter the Windows administrator token in its hidden local prompt, never in
   chat or React source. It creates an ignored mode-0600 `.scanplus-local/admin.json`
   with `trustMode: public`, no old loopback override and no private CA requirement.
4. Run the console with a supported Node runtime:

   ```sh
   cd scanplus-console
   npm ci
   npm test
   npm run build
   npm start
   ```

   Open `http://127.0.0.1:5173`. The console server binds only to loopback and
   keeps the bearer credential server-side. Node validates the public server
   with its maintained CA store. Read-only CLI checks are available through
   `node scanplus-tools/cloud_admin.mjs devices|audit|health` from the repository root.

## Acceptance

Require fresh authenticated check-ins from the real terminal, the expected agent
version, owner status, inventory and unchanged protection preferences. Exercise
the command round trip with an app-inventory refresh. Check the console API and
compiled React page against the public backend. Do not call an issued command
successful until the terminal acknowledges it.

Console reads run sequentially every ten seconds, pause in hidden tabs and back
off after failures. Audit refreshes every thirty seconds. Four active console
reads per ten seconds plus one agent are approximately 44,640 requests/day
before extra activity, rather than over 100,000. Multiple tabs, more devices,
manual refreshes and jobs consume additional quota.

The Windows host must remain awake, online and running both backend and tunnel.
This changes the connection address and trust; it does not add redundancy or fix
the existing privileged helper's terminal-reboot limitation.
