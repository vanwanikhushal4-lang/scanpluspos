# Velox Hardware Security for PAX A920

Native Android app adapted from rohanagarwal94/Hardware-testing, commit
`0358d9d876200c3e33da118fef55082f098ca2d7`. MIT attribution is retained.

## Build and signing

Use Java 17, Android SDK 35, and the included Gradle 8.10.2 wrapper.
The A920 build has minimum/target API 25 (Android 7.1), package
`dev.velox.hardwaresecurity`, version `2.0.2-a920` (5).

```sh
./gradlew :app:assembleRelease :app:lintRelease
```

Output: `app/build/outputs/apk/release/Velox-Hardware-Security.apk`.
The release is not debuggable and contains no instrumentation runner.
A separate administrator signing key is stored privately on this Mac under
`~/.codex/keys/velox-hardware-security/`. Local `signing.properties` is ignored
by Git. Preserve that key to sign future updates; do not distribute it.
The sole excluded lint rule is Google Play's target-SDK publishing policy.

## Navigation

Protection is the first tab; Hardware retains the original 20 diagnostics.
The user asked to skip further vibration testing. The vibration page remains
available, with explicit physical-result controls instead of an automatic pass.

## Protection controls

The Protection switch is OFF on delivery. The administrator enters their own
8–32 digit PIN on the terminal. PINs are stored only as salted PBKDF2 verifiers;
failed attempts incur a monotonic-clock delay that is renewed after reboot.
PIN entry is hidden from screenshots. Both switch directions require the PIN.

Protection applies persistent Android device-owner restrictions against new
installations (including PAXSTORE updates), unknown sources, debugging, USB file
transfer, app controls, safe boot, and factory reset from Settings. It requires
Android verification and protects the management app against uninstall.
A private snapshot preserves the administrator's prior restrictions for maintenance.
Existing hardware tests, Wi-Fi, and payment networking remain available.

An administrator-provisioned root USB helper supplies the API missing from this
Android version: disabling the entire USB data gadget and configuring charge-only
USB persistently. It disables configured ADB TCP ports and stops adbd. The helper
accepts only fixed operations over a local socket, verifies the application's Linux
UID, and accepts no arbitrary commands or paths. It has no network listener.
The APK contains no root exploit or automatic privilege escalation. Its helper is
provisioned separately through the existing owner-authorized workspace tools.

The original working USB state is saved BEFORE disabling debugging. Turning the
switch OFF enters PIN-protected maintenance, restores the saved restrictions and
USB configuration, and requests debugging through device-owner APIs. After a
reboot the root helper must be reprovisioned for another USB-seal cycle; the
persistent USB configuration and Android restrictions remain separate mechanisms.
The app checks the actual reported state and shows incomplete controls as attention items.

## Firmware boundary

The app does not patch the vulnerable PAX vendor-command parser, contain an
already-root process, enforce SELinux policy, replace recovery, or secure every
processor boot mode. Protection lists these as firmware findings. Vendor-signed
firmware, bootloader/verified-boot enforcement and vendor fixes are required for
those boundaries. No payment data, secure-element keys or firmware partitions are
modified by this app.

Platform references: [Android user restrictions](https://developer.android.com/reference/android/os/UserManager)
and [device-owner policy APIs](https://developer.android.com/reference/android/app/admin/DevicePolicyManager).

## Deployment and acceptance

The surrounding workspace provides `deploy_velox_security.py` (verified USB
transfer, owner-authorized PAX installation and restoration) and
`provision_velox_security.py` (device-owner enrollment and local helper startup).
Provisioning does not enable protection or change USB/debugging configuration.

Build, lint, APK signature, installation and launch are checked. At the user's
explicit request, no blocking action, USB lockout, policy-enforcement trial or
protection toggle is exercised by the agent. The user will set the PIN and test
protection on the terminal. Enforcement is not represented as verified.

Hardware integration checks remain in `app/src/androidTest`, separate from the
release APK. The new security build is not automatically exercised on the device.
