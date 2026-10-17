# SCANPLUS POS

Source for the SCANPLUS terminal-management system: a headless Android agent,
Java backend, React administrator console and Windows/Cloudflare deployment.

## Project layout

| Directory | Purpose |
| --- | --- |
| `scanplus-agent/` | Headless Android 7.1-compatible management agent, version 3.1.2-scanplus |
| `scanplus-backend/` | Java 17 / Spring MVC API, persistent H2 database, APK storage and audit history |
| `scanplus-console/` | React console with a local Node server that keeps administrator credentials out of the browser |
| `deploy/windows/` | Windows launch/startup scripts and the Cloudflare Workers VPC gateway |
| `scanplus-tools/` | Local backend setup, administrator CLI and administrator-controlled test tools |
| `scanplus-app-fixture/` | Disposable Android application used for integration checks |
| `app/` | Earlier visible hardware-diagnostic and PIN-based protection application |
| `tools/` | Synthetic PIN rules/crypto checks |
| `docs/` | Migration plan and upstream attribution |

The production Android agent has no launcher activity. Its foreground management
notification remains visible. The earlier `app` module is retained as historical
source and is not the headless agent.

## Build and run

Use Java 17 and the included Gradle wrapper. The standalone backend builds without
an Android SDK:

```sh
./gradlew -p scanplus-backend test installDist
```

On Windows, use `gradlew.bat` instead. Backend distributions are generated under
`scanplus-backend/build/install/scanplus-security-backend/`.

The Android modules use Android SDK 35 and support the target Android 7.1 device:

```sh
./gradlew :scanplus-agent:assembleRelease :scanplus-agent:lintRelease
```

Release signing requires your private, ignored `signing.properties` and keystore.
Updating an already managed terminal requires its existing signing identity and
an accepted version code; installing the source does not automatically grant
device-owner or privileged-helper access.

For the console, use Node.js 22.12 or newer:

```sh
cd scanplus-console
npm ci
npm test
npm run build
npm start
```

The console needs its private backend administrator/CA configuration before it
can start. It binds to loopback and is not a public multi-user dashboard.

## Deployment and current migration status

See [Windows hosting](deploy/windows/README.md) for the Windows launchers and
Cloudflare setup, and the [cutover plan](docs/windows-cutover-plan.md) for the
ordered enrollment, certificate-trust and console migration.

The gateway is deployed at
`https://scanplus-backend-gateway.secureitpos.workers.dev`; its Windows VPC Service
identifier is configured in `deploy/windows/cloudflare/wrangler.jsonc`. These
public routing identifiers are not credentials. Another deployment needs its own
Cloudflare account, tunnel and service binding.

The Windows endpoint has passed public health and invalid-credential checks.
At this source snapshot, migration of the existing terminal enrollment remains
planned. The CA-bundle and reduced-polling changes described in the plan are not
implemented in this snapshot. Workers VPC is currently free during beta and
Workers Free request quotas apply; this is not an uptime guarantee.

The privileged USB helper must be explicitly provisioned. Its terminal-reboot
limitation is independent of backend hosting. See [management setup and API](scanplus-tools/README.md)
and the [console README](scanplus-console/README.md) for the current boundaries.

## Private configuration

Credentials, signing keys, local database files, APK uploads, device reports,
generated builds and machine-specific SDK paths are intentionally excluded.
Configure them privately on the deployment host. Never put administrator or
device tokens into React source, a `VITE_` variable or a Cloudflare Worker.

The device-specific fixture harness requires explicit `SCANPLUS_TEST_DEVICE_ID`
and `SCANPLUS_TEST_DEVICE_SERIAL` values. It also uses the separately provisioned
local `pax_shell` workspace helper for foreground verification; it is not a
general-purpose test for arbitrary terminals. Do not run it against a production
terminal.

## Attribution

The legacy hardware application derives from
[rohanagarwal94/Hardware-testing](https://github.com/rohanagarwal94/Hardware-testing),
upstream commit `0358d9d876200c3e33da118fef55082f098ca2d7`.
Its original [README](docs/upstream-hardware-testing.md) and [MIT license](LICENSE)
are retained.
