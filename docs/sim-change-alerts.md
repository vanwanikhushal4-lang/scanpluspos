# SIM-change alerts

ScanPlus POS Security agent 3.5.0 monitors each SIM slot without adding a launcher
or screen to the terminal. Alerts appear in the website's **SIM Change Alerts**
page and in a fleet-wide banner. This release adds monitoring, not an automatic
lock, SMS message or email notification. Existing protection and Wi-Fi controls
are independent and are not changed by the update.

## Detection and offline operation

- The first stable reading of each slot establishes its baseline without an alert.
- Insertion, removal and replacement generate separate events. Reinserting the
  same known SIM is an insertion; inserting a different known identity is a
  replacement. Carrier-name changes alone do not generate replacement alerts.
- The headless service samples every five seconds and listens for radio-state
  broadcasts. A change needs observations spanning at least 15 seconds. The first
  30 seconds after device boot are excluded from comparison.
- Airplane mode, locked SIMs, unavailable metadata and permission failures pause
  comparison and retain the previous baseline. An empty subscription list alone
  is never treated as proof of SIM removal.
- Confirmed changes and the new baseline are saved together on the device before
  delivery. Restarting the service or losing Internet access retains the outbox.
- Delivery uses the existing authenticated HTTPS management connection. The
  backend acknowledges individual event IDs; retries do not duplicate alerts.
- The outbox holds 128 entries, including a reserved monitoring-gap warning when
  capacity is reached. It does not silently advance the baseline past changes
  that cannot be recorded. It cannot retain an unlimited offline history.
- With a compatible backend, status is reported about once per minute and queued
  events are attempted during management cycles. The website marks stale reports.

## Data and permissions

The managed agent declares READ_PHONE_STATE. Its existing device-owner role can
grant this declared permission unless an administrator has explicitly denied it.
No Activity, permission dialog, SMS permission or phone-number access is added.

The ICCID is read transiently to calculate an HMAC-SHA-256 fingerprint using a
random installation-specific key. The raw ICCID is not saved or sent. Reports
contain the fingerprint, last four digits, carrier and slot state. The private
key stays in the application's private preferences. The interface shows only
masked last-four-digit identifiers; alert records retain masked metadata and an
event-content hash. These are management metadata and still require admin access.

The Android 7 A920 exposes two slots through its read-only modem state property;
newer Android versions use the public per-slot state API. SIM identity access can
still depend on vendor firmware. A present but unreadable SIM is shown as such;
the agent does not claim replacement detection when identity is unavailable.

## Website

Open https://scanplus-pos-security.netlify.app/#sim after administrator sign-in.
Select a terminal to see its baseline, latest report, slot status and alert
history. The latest 200 alerts are returned, with unacknowledged entries first.
Acknowledgement retains the event and adds an audit entry; repeating the action
does not create duplicate audit entries. The global banner counts unacknowledged
alerts for active devices. Received time uses the server clock; detected time
uses the terminal clock and can differ.

## Protocol

| Route | Authentication and purpose |
| --- | --- |
| `POST /api/device/sim/report` | Device credential; bounded snapshot and up to eight events |
| `GET /api/admin/devices/{id}/sim` | Administrator; latest snapshot, alerts and open count |
| `POST /api/admin/devices/{id}/sim/alerts/{eventId}/ack` | Administrator; exact empty JSON object |

The check-in response advertises `simChangeAlertsSupported`. Agent 3.5.0 continues
local monitoring with an older backend but does not call its missing SIM route.
The pending outbox is delivered once the backend and Cloudflare gateway support
the feature. Device revocation, per-device isolation, UUID replay checks and
existing request-size limits apply. A reused event ID with altered content is
rejected. Backend database additions retain existing enrollment and management
data. The Netlify/local gateways expose only typed administrator routes and keep
the backend credential out of browser assets.

## Rollout

The cumulative `SCANPLUS-SIM-Alerts-Update.zip` includes the new backend, Cloudflare
routes, signed 3.5.0 APK and the preceding Wi-Fi guard changes. Use its README to
update the existing Windows installation and deploy its Cloudflare folder. Keep
the private configuration, database, APK store and tunnel configuration. Never
uninstall or clear the enrolled agent to perform an update. The same signing
identity and in-place installation retain its enrollment and policy state.

The Windows step must run on that PC. Publishing the website alone does not add
the server routes. Until the backend is updated, the SIM page explicitly reports
the required update rather than presenting monitoring as active.

## Validation and physical test scope

Automated checks cover stable baselines, boot/airplane/permission noise,
per-slot replacement, same-SIM reinsertion, restart and offline receipts, durable
save failures, bounded-outbox gaps, authenticated API isolation, replay rejection,
acknowledgement, malformed reports and gateway routes. The release build and
Android lint pass; the APK retains the existing signature and has no Activity.

The A920 currently has two empty slots. At the owner's request, this release is
prepared without a physical SIM insertion/removal test. Empty-slot baseline,
permission and management-connection checks cannot validate a real card's ICCID
visibility or the vendor radio's behaviour during a swap. That physical check
can be performed later without changing the monitoring design.
