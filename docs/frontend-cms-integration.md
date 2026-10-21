# ScanPlus CMS frontend integration

The ScanPlus POS Security frontend uses the design supplied in the dashboard archive
(SHA-256 `02f230cf9e7d0660700d1d77bf5d9f308bd8a17ae17ed9689c4610e27a943a20`).
The supplied archive is preserved unchanged. Its generated bundles, nested ZIPs,
private environment file and bundled dependencies are not copied into this app.

## Layout and access

The new console uses the supplied ScanPlus light/dark logos, Velox footer,
56-pixel top header, collapsible sidebar, blue accents, rounded cards, compact
status tables, theme switch and responsive dashboard grid. The content is adapted
to the payment terminal and the management APIs already deployed on Windows.
The console opens directly to the dashboard under the existing local administrator
access model, as requested. There is no separate login or browser-held bearer token.

## Connected pages

| Page | Live data and actions |
| --- | --- |
| Security Dashboard | Online/offline terminals, reported protection, USB seal, device-owner coverage, selected-terminal app distribution, audit events and app job results |
| Device Management | Serial/model/agent version, last check-in, owner/helper status, searchable and filtered device list, details and CSV export |
| Application Control | Installed apps, application details, visibility, suspension, launch/force-stop, uninstall protection and runtime permission policies |
| USB & Device Protection | Existing protection/maintenance requests with explicit confirmation, idempotent retries and terminal acknowledgment |
| App Deployment | Existing APK upload/install API, checksums, compatibility checks and per-job results |
| Command History | Selected-terminal protection requests and results |
| Reports & Audit | Cloud audit records, actor/date/text filters and CSV export |
| Settings | Public backend origin, connection status, terminal version and current local access information |

Legacy ATM-specific API calls are replaced by the existing typed SCANPLUS API.
Protection distribution replaces encryption data; Android app categories replace
legacy Windows whitelist modes; Android device-owner coverage replaces password
frequency. Dashboard values come from actual API responses. Unknown reports stay
unknown, stale reports do not appear online and revoked terminals are excluded
from fleet totals. Application chart categories are disjoint and link to the same
filtered app lists. CSV cells escape spreadsheet formulas and embedded quotes.

## Backend and agent

The browser talks to the existing loopback Node server. The server obtains its
public backend origin and administrator credential from the ignored private
configuration, then calls the Windows/Cloudflare API using verified HTTPS.
Administrator credentials remain outside source assets and browser storage.

This frontend change does not modify the Android agent, its package, owner
assignment, protection state or headless manifest. The foreground management
notification remains. Only an inventory refresh was sent during live UI checks;
protection confirmation was opened and cancelled, without issuing a protection
command.

## Build and verification

```sh
cd scanplus-console
npm ci
npm test
npm run build
npm start
```

The build includes only the frontend, local API gateway and required dependencies.
Tailwind 3 supplies the reference design's layout/theme utilities. The copied
branding assets are served locally. The interface retains the existing hidden-tab
polling pause and failure backoff; audit and dashboard inventory read less often
than terminal status.

Verification covers security-state aggregation, disjoint app filters, CSV safety,
polling, local API access controls and the production bundle. Live browser checks
cover dashboard routing, light/dark themes, device search/filtering, app inventory,
APK deployment UI, protection review/cancel, history, audit filtering and settings.
Desktop, embedded-browser and phone-width layouts are checked for outer overflow.
The live inventory-refresh command was acknowledged successfully by the A920.
