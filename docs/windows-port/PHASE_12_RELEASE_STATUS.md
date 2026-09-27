# P12 Windows release engineering status

The Desktop build now produces per-user MSI and EXE installers with a bundled JVM runtime. Both use the same stable upgrade UUID so a newer installer can replace an older version without moving the per-user library, settings, downloads, or extension directories. Android's APK updater is not reused.

Build on Windows with the existing JDK 21 and JDK 17 toolchain (replace the paths if the checked-out toolchain subdirectories differ):

```powershell
$env:JAVA_HOME = 'C:\Projects\mihon-windows\build\workspace\toolchains\jdk21\jdk-21.0.12.1+1'
.\gradlew.bat '-Dorg.gradle.java.installations.paths=C:\Projects\mihon-windows\build\workspace\toolchains\jdk17\jdk-17.0.20.1+1' :desktopApp:desktopTest :desktopApp:spotlessCheck :desktopApp:createDistributable :desktopApp:packageMsi :desktopApp:packageExe
```

The packages are written under `desktopApp/build/compose/binaries/main/{msi,exe}`. The Windows CI matrix builds each format independently after Android and Desktop tests, then publishes the installer and its SHA-256 file. The release operator must copy those exact checksums into the release notes and verify each uploaded file again before publication. Do not treat a checksum posted alongside an unsigned download as proof of publisher identity.

Local packaging fixtures (2026-09-27; unsigned, not published releases). The latest local application distribution and installers are version 1.0.5; the 1.0.4 and 1.0.3 rows are retained as preceding upgrade fixtures. The portable `Mihon.exe` is the application executable, distinct from the EXE installer. `desktopApp/build.gradle.kts` defaults to 1.0.5; `-PmihonWindowsVersion` can override it for a later numeric version.

| Version | Artifact | SHA-256 |
| --- | --- | --- |
| `1.0.5` | `Mihon.exe` (application executable) | `2593766007d55ccc5698789237c9b65ee3ab378614e414c1cd931d174e4d7dd6` |
| `1.0.5` | `Mihon-1.0.5.msi` | `daa362ede77ba0dd1c324f652f3835c14a230ea9c1f9473110a8390f74b4884b` |
| `1.0.5` | `Mihon-1.0.5.exe` (installer) | `f2e9dabc7ad33c1b1eef4b1dc79d1d2aea00c75123b785b29f487519a3c9e807` |
| `1.0.4` | `Mihon.exe` (application executable) | `9b23510b8b26c60f2ee96c96730cb6eb26c9973f5e93f6c4ad47b79fd0dbdd1b` |
| `1.0.4` | `Mihon-1.0.4.msi` | `e301df25b6bae6ff89ae16e5364a3f5d3e3d5fd9e733b3f6356739e8555914b9` |
| `1.0.4` | `Mihon-1.0.4.exe` (installer) | `5e2486da9b52c83561cf388976d4683f0590f5d8eb05654161f56c0a438bf2c9` |
| `1.0.3` | `Mihon-1.0.3.msi` | `5b364758fb03a84ebd4c6655c2ba629b899016ccc166082dbfbdccf0ed768a51` |
| `1.0.3` | `Mihon-1.0.3.exe` | `6746c5f2397d3eb56569c8284827109d35ab66faf219061c2247a823b88e3e9b` |

The 1.0.5 portable application executable was launched directly from the distribution with the isolated `build/ui-test-profile`; no installer was run against the personal profile. The distribution contains the pinned `suwayomi-server.bin` and bundled `runtime/bin/javaw.exe`. The engine started as a separate child `javaw.exe` process from this bundled runtime, with `suwayomi-server.bin` as an argument, and is not listed in `Mihon.cfg`'s application classpath. The packaged UI showed the current design and source language labels; MangaDex (EN) browsing and reading worked in the isolated profile.

The complete 510-file SHA-256 list for the 1.0.5 application distribution is [Mihon-1.0.5-distribution.sha256](release/Mihon-1.0.5-distribution.sha256) (manifest SHA-256 `b83f54b385bffeb256d93b0d2b563345522b76d9b033f4073832be2b17fea71f`). Its code JAR `desktopApp-desktop-a37c4786955ca11ca395c4f67fb3f3f4.jar` has SHA-256 `e4c306531395efdef3393dd7a15319c240b481e125b5e64dbf0a1575e13a62a5`. The executable hash alone does not identify the Kotlin code.

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
