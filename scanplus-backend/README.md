# SCANPLUS Spring MVC backend

Java 17, Spring Boot 3.5.16, Spring MVC, Bean Validation and transactional JDBC.
The embedded H2 database persists enrollment, hashed device credentials, device
reports, protection commands, application inventories, app jobs, APK metadata and
audit events. APK bytes are stored privately on disk with SHA-256 checksums.

```sh
./gradlew test installDist
```

See [management setup and API](../scanplus-tools/README.md) for HTTPS setup,
configuration, device provisioning, administrator API and the local CLI.
The bundled launcher is `build/install/scanplus-security-backend/bin/scanplus-security-backend`.
TLS is mandatory by default; a random administrator credential of at least
32 characters is required at startup. No development credential is embedded.

## React device console

The connected React GUI is in [scanplus-console](../scanplus-console/README.md).
It serves on `http://127.0.0.1:5173/` and uses a local server gateway to this HTTPS
backend. The gateway keeps the existing administrator credential out of browser
assets and verifies the dedicated backend certificate.

Application jobs are typed, device-scoped, idempotent and expiring. The service
validates package and permission fields, bounds uploads and report sizes, and
does not accept shell commands. The agent independently protects critical apps
and enforces firmware installer rules. See the management API for supported
actions, lifecycle and the permission compatibility behavior on this A920.
