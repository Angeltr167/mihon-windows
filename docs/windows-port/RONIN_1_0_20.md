# Ronin 1.0.20 for Windows

This release includes the charcoal desktop redesign and the selected travelling
ronin icon with its black background. The same icon is used inside the app, in
the title bar, and by Windows shortcuts and installers.

## Updating while keeping your library

Close Ronin, then run `Ronin-1.0.20.msi` (or the EXE installer). Install over the
existing installation. The upgrade UUID remains
`c764cc56-8996-49ef-b813-1ee3815d9da2`, including compatibility with earlier Mihon
packages using that UUID. The installer updates the application and its bundled
runtime. It does not package or replace your user profile.

The historical profile paths remain stable across branding and version changes:

| Content | Location |
| --- | --- |
| Library, reading history, progress, categories and tracking | `%LOCALAPPDATA%\Mihon\database\tachiyomi.db` |
| Settings | `%APPDATA%\Mihon` |
| Extensions and other application data | `%LOCALAPPDATA%\Mihon` |
| Downloaded chapters | `%USERPROFILE%\Downloads\Mihon` |
| Local manga | `%USERPROFILE%\Documents\Mihon` |

Database schema upgrades create a consistent SQLite snapshot in
`%LOCALAPPDATA%\Mihon\database\upgrade-backups` before applying migrations.
Committed WAL data is included. If snapshot creation fails, migration does not
start. SQLDelight applies migrations in a transaction; a failed migration rolls
back its changes. Opening a newer schema with an older app, or an existing
unrecognized unversioned database, stops with an error instead of resetting it.
Opening the same schema again does not create redundant snapshots. Backups are
kept for recovery rather than deleted automatically.

## Validation and scope

Regression fixtures cover the real schema 14 → 15 migration, including a live
WAL, retained reading history and progress, preserved favorite/bookmark flags,
same-version reopening, rejected downgrades, an unversioned existing database,
and rollback after a deliberately failed migration. Installer upgrades on a
separate Windows account were not executed against the personal installation.

The packaged Windows launcher passed the platform, database and session smoke
checks with its bundled runtime and an isolated profile. The MSI reports version
1.0.20, retains the historical upgrade code, and includes upgrade detection for
earlier releases. The packaged PNG matches the new icon; the ICO includes sizes
16, 24, 32, 48, 64, 128 and 256. All 10 shared-data tests and 43 desktop tests
passed; 2 desktop tests were skipped. Formatting and whitespace checks passed.

The icon was extracted into a standalone bitmap with the built-in image tool,
using the selected reference: keep the large black tile and its coral kasa hat,
ivory face/scarf silhouette, remove the surrounding board and all text. The PNG
and multi-resolution ICO are in `desktopApp/src/desktopMain/resources/ronin`.

These are local unsigned installers. This work does not publish an online update
feed or introduce an automatic downloader.
