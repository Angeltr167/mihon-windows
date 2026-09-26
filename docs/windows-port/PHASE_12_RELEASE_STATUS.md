# P12 Windows release engineering status

The Desktop build now produces per-user MSI and EXE installers with a bundled JVM runtime. Both use the same stable upgrade UUID so a newer installer can replace an older version without moving the per-user library, settings, downloads, or extension directories. Android's APK updater is not reused.

Build on Windows with JDK 21:

```powershell
.\gradlew.bat :desktopApp:desktopTest :desktopApp:packageMsi :desktopApp:packageExe
```

The packages are written under `desktopApp/build/compose/binaries/main/{msi,exe}`. The Windows CI matrix builds each format independently after Android and Desktop tests, then publishes the installer and its SHA-256 file. The release operator must copy those exact checksums into the release notes and verify each uploaded file again before publication. Do not treat a checksum posted alongside an unsigned download as proof of publisher identity.

Local packaging fixture (2026-09-25; unsigned, not a published release):

| Artifact | SHA-256 |
| --- | --- |
| `Mihon-1.0.0.msi` | `23f7bca3271f1d2be18934f95a7bfbc40daf0965741633db7da7e7661d159472` |
| `Mihon-1.0.0.exe` | `4e0b5de9f9a8a4879231b37cac0a4010ed66b4c8b84a6c12fc22bbb57e33127e` |

For installer-driven upgrades, download the new installer from the project's official HTTPS release location, compare its SHA-256 with the published release record, close Mihon, then run the newer installer. The installer must be launched only after the digest matches. Back up the profile before an RC upgrade. The stable upgrade UUID is `c764cc56-8996-49ef-b813-1ee3815d9da2`; do not change it between Windows releases. The version supplied through `-PmihonWindowsVersion` must increase for upgrades and use the numeric `major.minor.patch` format required by Windows packaging.

The Apache-2.0 project `LICENSE` is supplied as installer license metadata. The bundled JDK runtime includes its own `legal` directory. Before any public release, audit and ship notices for all packaged third-party libraries, including Compose/Skiko, Kotlin, SQLDelight, OkHttp, coroutines, serialization, and extension-runtime dependencies. This audit is not complete yet.

Authenticode credentials are not present in this repository. Signing must use a release certificate and timestamp service held outside the repository; verify the signature on both installers before publishing. Unsigned local and CI packages are test artifacts, not trusted public updates.

## Remaining exit-gate evidence

- Clean supported Windows profile: install each package and launch with no developer JDK or Gradle.
- Upgrade from the preceding RC: verify library, settings, downloads, and extensions stay intact.
- Uninstall: verify application files are removed and confirm the intended treatment of user data.
- Run the published Windows and Android CI jobs; local success does not establish CI status.
- Complete the third-party notice and signature audit before a stable release.

These tests require the separate clean Windows environment that is deferred by the user. P12 and P13 must not be marked passed until this evidence exists.
