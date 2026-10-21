# Wi-Fi visibility and open-network policy

The Wi-Fi Security page shows access points scanned by the selected A920. It
shows SSID, BSSID, signal in dBm, security, frequency band, per-result age and the
reported current connection. It never reads Wi-Fi passwords or GPS coordinates.
Up to 40 access points are reported, strongest first. A blank SSID appears as
Hidden network. Unknown security is displayed as Unknown rather than assumed safe.

## Scanning

The headless 3.3.0 agent requests a scan about once per minute while connected to
management. Scan now queues an authenticated, expiring request for that terminal.
Scans run on a separate worker so they do not block protection heartbeats or APK
installation. Scan failure, throttling, missing location permission, Location off,
Wi-Fi off and cached results are reported explicitly. Row ages grow on the website.

The manifest declares Wi-Fi state/change and fine location permissions. The
enrolled device owner can grant the agent its declared location permission.
Location services are never turned on automatically; if the firmware requires
Location and it is off, the page explains that requirement. There is no Activity
or launcher added to the agent.

## Full Wi-Fi protection and the independent connection guard

Both controls default off. Enabling either requires a recent report of a secured current
Wi-Fi connection and device-owner enrollment. The backend checks the report and
the agent repeats the current-connection check before any policy change. An open,
unknown or disconnected current network is refused without changing the policy.

Full Wi-Fi protection retains the original strict Android 7 enforcement:

1. Saves the device owner's previous local Wi-Fi settings restriction.
2. Locks local Wi-Fi changes using DISALLOW_CONFIG_WIFI.
3. Saves private recovery copies of saved open-network configurations before
   removing those configurations from Android's usable saved-network list.
4. Leaves saved secured networks in place. The agent verifies there are no usable
   saved open profiles and that its settings restriction is active.
5. Reapplies the policy during service cycles, including while the backend is
   unreachable. Turning full protection off restores the previous settings
   restriction. Saved open profiles are restored only when both controls are off.
   Failed restoration keeps the recovery checkpoint for retry.

The separate **Block open Wi-Fi connections** toggle was added in agent 3.4.0.
It does not request DISALLOW_CONFIG_WIFI. With full Wi-Fi protection off, the
user can select and join new secured networks in the A920's normal Wi-Fi settings.
Scans continue to report open and secured networks; no SSID is hidden from the
website's results. Existing restrictions imposed earlier or by other admins are
preserved, so those restrictions can still lock local settings.

On Android 7 this is a reactive connection guard, not pre-association prevention.
The headless foreground service listens for Wi-Fi connection/supplicant/radio
broadcasts and has a separate one-second local watchdog. It checkpoints and removes
open saved profiles, disconnecting a matching active open profile when detected.
A brief association or traffic window is possible before detection. It does not
guarantee a zero-packet window. Broadcast events expedite checks; the watchdog
continues while the backend is unreachable. The website describes this as
**Disconnect on detection** and only confirms the guard when the monitor is
running and the current state can be observed. There is still no launcher or Activity.

The two intents share one recovery checkpoint. Disabling full Wi-Fi protection
while the guard remains on unlocks our settings restriction while retaining the
open-profile guard. Disabling the guard while full protection remains on leaves
the stricter lock intact. Disabling both restores the saved open profiles and
previous settings restriction. USB/device protection is independent of both.

On supported newer Android releases, the agent uses the native minimum Wi-Fi
security policy and preserves its previous level. Existing stronger policy is
preserved. Unknown saved-profile security causes legacy activation to fail and
roll back instead of guessing. WEP and WPA are identified as weak/legacy in the
UI; this particular toggle blocks open networks rather than all weak encryption.

While strict blocking is on, a user cannot add a new secured network through the
terminal's Wi-Fi settings either. An administrator must disable this policy while
the terminal still has a working management connection, make the network change,
then enable it again. Saved secured profiles remain available for normal roaming.
Firmware enforcement still needs a controlled device check; automated tests cannot
prove a vendor's Settings app obeys the Android restriction.

## Protocol and authorization

- GET /api/admin/devices/{id}/wifi returns the last snapshot and Wi-Fi history.
- POST /api/admin/devices/{id}/wifi/scan accepts requestId and ttlSeconds.
- POST /api/admin/devices/{id}/wifi/policy accepts the blocked boolean for full protection.
- POST /api/admin/devices/{id}/wifi/guard accepts the blocked boolean for the independent guard.
- POST /api/device/wifi/report accepts a bounded, validated snapshot.
- The existing check-in response optionally includes wifiCommand and advertises openWifiGuardSupported.
- POST /api/device/wifi/commands/{id}/ack records the device's confirmed result.

Commands use UUID idempotency keys, a two-minute default expiry and terminal-bound
acknowledgments. A successful scan acknowledgment requires fresh scan status; a
successful policy acknowledgment requires consistent observed policy state.
Guard requests use SET_OPEN_GUARD and require their own observed openGuard
snapshot rather than accepting the strict policy state as proof. An older agent
without that capability cannot be sent a guard request. Agent 3.4.0 omits the new
snapshot field when an older backend does not advertise support, preserving the
existing scan/protection protocol until Windows is updated. Older stored strict
policy preferences keep their original meaning; they are not silently relaxed.
Only one Wi-Fi operation may be pending per device. Device revocation cancels
pending Wi-Fi requests. Older agents ignore the new optional check-in field.

The local and Netlify gateways expose only the typed scan/policy routes. The
Cloudflare allowlist includes the new reporting routes. Existing administrator
and per-device credentials remain separate; no backend token enters browser code.
Recovery profiles remain on the device and are not uploaded to the backend.

## Rollout order

1. Stop the Windows backend, back up its database and existing backend directory,
   and install the new backend distribution. Retain private/backend.json, the
   database, APK directory and existing tunnel configuration. The new schema adds
   Wi-Fi tables without deleting existing data. Restart and verify health.
2. Deploy the updated Cloudflare proxy with the existing VPC binding.
3. Deploy the console build and its updated Netlify gateway together. Preserve
   existing authentication and backend environment variables.
4. Update the existing agent to version 3.4.0 using the same signing key and an
   authorized management/USB installation route. Keep application data and
   device-owner enrollment. Do not uninstall or re-enroll it.
5. Keep both Wi-Fi controls off. Check current connection and Scan now first. Review any
   Location/permission message on the terminal. The installer does not enable
   open-network blocking automatically.
6. Enable the selected policy manually only from a known secured connection. Check the
   terminal's result, settings restriction and saved secured connectivity before
   relying on vendor enforcement. Then verify the off/restore path in a controlled
   maintenance session with an independent recovery route available.

Automated coverage includes API authorization, terminal isolation, idempotency,
expiry/revocation, stale and open-connection refusal, observed acknowledgments,
policy checkpoint/rollback/restart/recovery, independent guard/full-policy
transitions, adding secured networks while guarded, and gateway payload restrictions.
