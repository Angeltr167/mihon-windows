# P12 Windows release engineering status

The Desktop build now produces per-user MSI and EXE installers with a bundled JVM runtime. Both use the same stable upgrade UUID so a newer installer can replace an older version without moving the per-user library, settings, downloads, or extension directories. Android's APK updater is not reused.

Build on Windows with JDK 21:

```powershell
.\gradlew.bat :desktopApp:desktopTest :desktopApp:packageMsi :desktopApp:packageExe
```

The packages are written under `desktopApp/build/compose/binaries/main/{msi,exe}`. The Windows CI matrix builds each format independently after Android and Desktop tests, then publishes the installer and its SHA-256 file. The release operator must copy those exact checksums into the release notes and verify each uploaded file again before publication. Do not treat a checksum posted alongside an unsigned download as proof of publisher identity.

Local packaging fixtures (2026-09-26; unsigned, not published releases). The latest local application distribution and installers are version 1.0.4; the 1.0.3 rows are retained as the preceding upgrade fixture. The portable `Mihon.exe` is the application executable, distinct from the EXE installer.

| Version | Artifact | SHA-256 |
| --- | --- | --- |
| `1.0.4` | `Mihon.exe` (application executable) | `9b23510b8b26c60f2ee96c96730cb6eb26c9973f5e93f6c4ad47b79fd0dbdd1b` |
| `1.0.4` | `Mihon-1.0.4.msi` | `e301df25b6bae6ff89ae16e5364a3f5d3e3d5fd9e733b3f6356739e8555914b9` |
| `1.0.4` | `Mihon-1.0.4.exe` (installer) | `5e2486da9b52c83561cf388976d4683f0590f5d8eb05654161f56c0a438bf2c9` |
| `1.0.3` | `Mihon-1.0.3.msi` | `5b364758fb03a84ebd4c6655c2ba629b899016ccc166082dbfbdccf0ed768a51` |
| `1.0.3` | `Mihon-1.0.3.exe` | `6746c5f2397d3eb56569c8284827109d35ab66faf219061c2247a823b88e3e9b` |

The 1.0.4 portable application executable was launched directly from the distribution with the isolated `build/ui-test-profile`; no installer was run against the personal profile. The distribution contains the pinned `suwayomi-server.bin` and bundled `runtime/bin/javaw.exe`. The engine starts as a separate JVM process and is not listed in `Mihon.cfg`'s application classpath.

For installer-driven upgrades, download the new installer from the project's official HTTPS release location, compare its SHA-256 with the published release record, close Mihon, then run the newer installer. The installer must be launched only after the digest matches. Back up the profile before an RC upgrade. The stable upgrade UUID is `c764cc56-8996-49ef-b813-1ee3815d9da2`; do not change it between Windows releases. The version supplied through `-PmihonWindowsVersion` must increase for upgrades and use the numeric `major.minor.patch` format required by Windows packaging.

The Apache-2.0 project `LICENSE` is supplied as installer license metadata. The bundled JDK runtime includes its own `legal` directory. The optional local extension compatibility engine is packaged from a pinned Suwayomi-Server release with its MPL-2.0 license and bundled dependency notices; see `SUWAYOMI_ENGINE.md`. Before any public release, audit and ship notices for all packaged third-party libraries, including Compose/Skiko, Kotlin, SQLDelight, OkHttp, coroutines, serialization, and extension-runtime dependencies. This audit is not complete yet.

Authenticode credentials are not present in this repository. Signing must use a release certificate and timestamp service held outside the repository; verify the signature on both installers before publishing. Unsigned local and CI packages are test artifacts, not trusted public updates.

## Remaining exit-gate evidence

- Clean supported Windows profile: install each package and launch with no developer JDK or Gradle.
- Upgrade from the preceding RC: verify library, settings, downloads, and extensions stay intact.
- Uninstall: verify application files are removed and confirm the intended treatment of user data.
- Run the published Windows and Android CI jobs; local success does not establish CI status.
- Complete the third-party notice and signature audit before a stable release.

These tests require the separate clean Windows environment that is deferred by the user. P12 and P13 must not be marked passed until this evidence exists.
