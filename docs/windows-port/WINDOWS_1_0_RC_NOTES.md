# Mihon Windows 1.0 release-candidate notes (draft)

This is a local release-candidate draft, not a published or validated stable release.

Windows supports Desktop extension packages, source browsing and search, library and categories, online/offline reading, downloads, selected tracker integrations, persistent settings, and import of Android `.tachibk` backups. The Desktop package is per-user and includes its Java runtime. Android remains a separate application and continues to use APK-based releases.

The intended artifacts are `Mihon-1.0.0.msi` and `Mihon-1.0.0.exe`, each with a SHA-256 checksum. Prefer the MSI unless the EXE is needed by the target environment. Verify the release checksum before opening either installer. Future versions use a stable Windows upgrade identifier; app data resides in the user's profile and must not be deleted during upgrade.

Known RC limitations: Android APK extensions cannot run on Windows; install a Desktop `.mihonext` package and explicitly trust its signer. Android-specific preferences and tracker credentials are not migrated from backups. Browser challenge and tracker integrations have local fixture coverage, but live-service validation is outstanding. Install, upgrade, uninstall, accessibility, and endurance checks on a clean Windows profile are outstanding. The current local packages are unsigned and must not be represented as trusted public releases.

Final release notes must list the exact commit, artifact SHA-256 values, Authenticode publisher and timestamp details, tested Windows versions, migration support boundary, and third-party notices before publication.
