# Ronin 1.0.21 for Windows

## Local backups

Settings → Storage & Backup now creates local `.tachibk` backups. Choose a folder
with the directory picker or enter a path, then select Create backup. The folder
is remembered. Each backup has a timestamp and unique filename; previous copies
are retained. A completed backup is published only after its temporary file has
been fully written.

Backups include library records (including reading data outside favorites),
categories, chapter metadata, progress, bookmarks, history, tracker links and
portable basic desktop preferences. They do not contain downloaded chapter
images, local books, extensions, authentication secrets or machine-specific paths.
There is no automatic backup schedule in this release.

Restore accepts Ronin and Mihon `.tachibk` files. Library records merge by source
ID and URL; matching chapter data is updated and unrelated records remain.
Included desktop preferences are validated against an explicit allow-list.
Credentials and paths cannot be applied through backup preferences. Android
preferences are not imported. An empty Ronin library can still back up its basic
preferences.

## Basic preferences

Settings → General includes Spanish, English and system-language selection;
Library, History or Updates as the start page; starting maximized; remembering
window size; and download completion/error notifications. Preferences save
immediately. Language and notification changes apply during the current session;
start page and window options apply on the next launch. Navigation, the new
settings and reader mode/fit menus follow the selected language. Some inherited
screens and service messages still contain English text.

Existing library update intervals and reader defaults remain available.

## Packaging and validation

The release keeps the existing Windows upgrade UUID and historical Mihon profile
paths. Close Ronin before installing `Ronin-1.0.21.exe` over the previous version.
The EXE is an installer; the application executable is bundled with its runtime
in the portable distribution directory.

Validation: 68 tests passed across Desktop, shared data, backup and platform
modules, with two existing Desktop tests skipped. This includes new backup file
round trips, preserved reading data and tracker links, repeated exports,
preference persistence, empty-library backups and rejected untrusted preferences.
Formatting checks passed. The local installer is not published online.
