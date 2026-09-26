# Mihon Windows 1.0 release-candidate notes (draft)

This is a local release-candidate draft, not a published or validated stable release.

Windows supports Desktop extension packages, a bundled local Suwayomi engine for Keiyoushi extensions, source browsing and search, library and categories, online/offline reading, downloads, selected tracker integrations, persistent settings, and import of Android `.tachibk` backups. The Desktop package is per-user and includes its Java runtime. Android remains a separate application and continues to use APK-based releases.

The intended local artifacts are `Mihon-1.0.3.msi` and `Mihon-1.0.3.exe`, each with a SHA-256 checksum. This version increments the earlier 1.0.2 installer so an existing installation can upgrade. Prefer the MSI unless the EXE is needed by the target environment. Verify the release checksum before opening either installer. Future versions use a stable Windows upgrade identifier; app data resides in the user's profile and must not be deleted during upgrade.

Keiyoushi extensions run through the bundled Suwayomi process, not directly through Mihon's Desktop `.mihonext` loader. See `SUWAYOMI_ENGINE.md` for setup and security notes. Android-specific preferences and tracker credentials are not migrated from backups. Browser-challenge compatibility varies by source; tracker integrations have local fixture coverage, but full live-service validation is outstanding. Install, upgrade, uninstall, accessibility, and endurance checks on a clean Windows profile are outstanding. The current local packages are unsigned and must not be represented as trusted public releases.

Final release notes must list the exact commit, artifact SHA-256 values, Authenticode publisher and timestamp details, tested Windows versions, migration support boundary, and third-party notices before publication.
