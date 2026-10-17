# SCANPLUS UNIFIED POS SECURITY

The Spring MVC backend is in `../scanplus-backend`. The Android release agent is
in `../scanplus-agent`. It has no activities or launcher icon and no hardware
check screens. Android's persistent management notification remains visible.

The agent exposes **protection, status, enrollment and application management**.
The API accepts typed requests. It does not
accept shell commands, perform password guessing, or distribute firmware exploits.

## Trust and provisioning

Each device must be explicitly enrolled by its owner. Installation alone does
not grant Android device-owner privileges or provision the privileged USB helper.
The A920 migration preserves the existing application ID, administrator component
and signing certificate so its device-owner enrollment and private data survive.
The legacy app source and APK are retained for an administrator-controlled rollback.
An Android rollback must be signed with the same key and use an accepted higher
version code; do not uninstall the device owner or clear its data to downgrade.

A fresh managed terminal needs approved APK installation, device-owner enrollment
and a vendor/administrator-provisioned USB helper where full USB sealing requires
privilege. Hardware/firmware compatibility must be checked per device model.
A helper is not automatically given root after reboot: without approved platform
startup integration, re-provision it before another full USB seal cycle. The agent
reports an incomplete state if the helper or required policies are unavailable.

Provisioning supplies a private `files/scanplus-bootstrap.json` in the app's data
directory, owned by the application UID with mode 0600. Fields:

- `baseUrl`: dedicated HTTPS origin, with no path, user information, query or fragment.
- `caPem`: the dedicated server's trusted X.509 certificate or CA in PEM format.
- `serial`: the device serial expected by the administrator's enrollment record.
- `enrollmentCode`: a one-time 10-minute code issued by the admin enrollment API.

The agent stores its returned device ID and random device token atomically and
removes the enrollment code. No shared administrator token is stored in the APK
or given to devices. Private credential storage is excluded from Android backup.

## Run locally

Use Java 17. From the Android repository:

```sh
./gradlew -p scanplus-backend test installDist
./gradlew :scanplus-agent:assembleRelease :scanplus-agent:lintRelease
python3 scanplus-tools/local_backend.py YOUR_MAC_LAN_IP
```

The local launcher generates a private TLS certificate, administrator token and
database password under `.scanplus-local/` (ignored by Git). It serves HTTPS on
9443 and persists H2 data under `scanplus-backend/var/`. Keep this directory
private, back it up, and preserve the TLS trust material for enrolled devices.
A certificate generated for one IP cannot silently be reused with a different IP.
This development server depends on this Mac staying awake and reachable.

For a dedicated production host, configure the datasource, administrator credential,
TLS keystore and bind address through the environment. Restrict administrative
network access, use controlled administrator credentials and backups, and arrange
service supervision and approved device provisioning. Do not publish the private
local configuration or use `SCANPLUS_REQUIRE_TLS=false` on a network service.

## Administrator API

All admin calls require `Authorization: Bearer <administrator credential>`.
The service requires HTTPS and does not trust forwarded headers by default.

| Method and path | Purpose |
|---|---|
| POST `/api/admin/enrollments` | Body `label`, `expectedSerial`; returns a short-lived single-use code |
| GET `/api/admin/devices` | Last contact and observed protection, owner, helper and USB status |
| POST `/api/admin/devices/{id}/protection` | Body `requestId` UUID, `enabled` boolean, optional `ttlSeconds` from 15 to 300 |
| GET `/api/admin/devices/{id}/commands` | Latest command states and result detail |
| GET `/api/admin/audit` | Latest 200 management audit events |
| DELETE `/api/admin/devices/{id}` | Revoke the device credential; leaves its local policy unchanged |

Use the private local administrator CLI without putting credentials in shell history:

```sh
python3 scanplus-tools/admin.py devices
python3 scanplus-tools/admin.py enable --device DEVICE_UUID
python3 scanplus-tools/admin.py commands --device DEVICE_UUID
python3 scanplus-tools/admin.py disable --device DEVICE_UUID
python3 scanplus-tools/admin.py audit
```

Queued commands remain PENDING until acknowledged. They expire, can be superseded,
and use request IDs for idempotency. Device result states are SUCCEEDED or FAILED.
A successful result includes an observed state consistent with the requested policy.
Device reports are authenticated observations, not hardware attestation.

## Device protocol and operation

`POST /api/enroll` consumes the serial-bound one-time code. Subsequent
`POST /api/device/check-in` and `/api/device/commands/{id}/ack` calls require both
`X-Device-Id` and that device's bearer credential. A device cannot acknowledge
another device's command. The database retains hashes of high-entropy tokens,
not raw device tokens or enrollment codes.

The agent polls over outbound HTTPS every 10 seconds when online. It retries
connection failures with backoff up to 60 seconds. Android power management may
delay background connectivity. It checks the pinned trust anchor and hostname,
rejects HTTP, and does not follow redirects with credentials. No inbound device
port is opened. It keeps bounded local command results before acknowledging to
handle delivery retries. Loss of Wi-Fi does not automatically disable protection.

Enable saves usable USB recovery state, applies the device-owner restrictions,
seals the USB gadget and stops ADB. Disable restores the saved policy and USB
configuration through the limited helper. The management link uses Wi-Fi, so it
can survive the intended USB disconnect. Root helper requests are fixed STATUS,
PREPARE, SEAL and RESTORE operations over a UID-checked local socket, plus
validated force-stop, maintenance-only removal and runtime-permission app-operation
controls for user apps. The helper accepts no APK installation or arbitrary shell
request. It must already have been explicitly provisioned by the administrator.

The earlier local PIN authorizes the legacy UI. The headless agent instead accepts
commands authorized by the enrolled backend. Protect the backend administrator
credential accordingly. This migration does not erase or disclose the legacy PIN.

## App control

The React console's **App control** tab lists installed applications and their
observed state. It supports search, user/system/restricted filters, launch,
force-stop, suspend/resume, hide/show, uninstall protection, runtime permission
policy, APK install/update and uninstall. These controls run in the existing
headless service: the release agent has no Activity, settings screen or launcher
icon. Android's foreground management notification remains visible.

| Method and path | Purpose |
|---|---|
| GET `/api/admin/devices/{id}/apps` | Latest inventory and force-stop capability |
| GET `/api/admin/devices/{id}/app-commands` | Recent app jobs and acknowledged results |
| POST `/api/admin/devices/{id}/app-commands` | Queue one typed action |
| POST `/api/admin/apks` | Upload a base APK as raw `application/vnd.android.package-archive`, `X-APK-Name` header |
| POST `/api/device/apps/inventory` | Authenticated inventory report |
| POST `/api/device/app-commands/{id}/start` | Claim a job before execution |
| POST `/api/device/app-commands/{id}/ack` | Report its actual result |
| GET `/api/device/apks/{id}` | Download only the artifact assigned to that device's running job |

App jobs use a UUID `requestId`, an `action`, optional `ttlSeconds` (15–600),
and action-specific fields: `packageName`, `permission` or `artifactId`.
Actions are `REFRESH`, `LAUNCH`, `FORCE_STOP`, `SUSPEND`, `RESUME`, `HIDE`,
`SHOW`, `BLOCK_UNINSTALL`, `ALLOW_UNINSTALL`, `GRANT_PERMISSION`,
`DENY_PERMISSION`, `RESET_PERMISSION`, `INSTALL` and `UNINSTALL`.
Never substitute shell text for these fields. Expired jobs and failed operations
are reported as such. Requests with uncertain delivery can reuse their exact
request ID and payload; running jobs are not blindly replayed after a restart.

Inventory refreshes approximately every minute and after each app command.
The agent protects itself, active administrators, launchers and core packages;
system apps cannot be modified. Install and uninstall require confirmed
maintenance mode. Uploaded APKs are capped at 64 MiB, downloaded over verified
HTTPS, and checked against their stored SHA-256, size, package and Android version.
Android/PAX signing and verification rules still apply. A compatible vendor-signed
APK can install; an unsigned or incompatible APK can be rejected by the firmware.
Split APK bundles are not supported by the single-base-APK uploader.

If this firmware requests local approval for uninstall despite device-owner
enrollment, agent 3.1.2 can use the existing administrator-provisioned helper's
fixed user-app removal operation. The agent rechecks maintenance, critical-app
and uninstall-block policies first; the helper independently rejects system
packages and requires saved maintenance state. Completion requires the package
to be absent. No removal or installation screen is launched by the agent.

On this A920 firmware, an Android permission can remain granted despite a device
policy denial. When needed, the pre-provisioned local helper denies the mapped
app operation instead. Inventory reports `operationBlocked` and `enforcement`
separately from Android's `granted` flag so the dashboard says **Operation blocked**
instead of claiming the grant was revoked. Unsupported operations fail explicitly.
Reset clears the management override; it does not promise to revoke a prior grant.

The disposable `scanplus-app-fixture` module is for controlled verification only;
its visible test Activity is not included in the production agent. Remove the
fixture after tests. `test_app_control.py` is deliberately bound to the named test
terminal and fixture; it does not target arbitrary installed applications.

## Development host address changes

Use a DHCP reservation or stable DNS name for an enduring deployment. Local
administration can use the separately configured `adminBaseUrl` loopback origin.
If the Mac's LAN address changes, `rotate_server_address.py` can issue a new
server leaf under the existing private CA and update the local server settings.
The enrolled device's origin must also be explicitly updated by its administrator;
the script does not silently re-enroll or contact arbitrary devices. Preserve the
original CA, device identity, private configuration and encrypted database.

## Build references

[Spring Boot 3.5 requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
confirm Java 17 and Gradle 8.4 or later for this backend.
[Spring transaction management](https://docs.spring.io/spring-framework/reference/data-access/transaction.html)
describes the JDBC transaction model used for enrollment and command changes.
