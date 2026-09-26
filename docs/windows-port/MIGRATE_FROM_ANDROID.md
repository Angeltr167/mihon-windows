# Move a Mihon library from Android to Windows

1. In Mihon on Android, open **Settings → Data and storage → Create backup**. Include library entries, categories, chapters, history, and tracking when prompted. Save the `.tachibk` file.
2. Copy the file to the Windows computer. Install Desktop `.mihonext` extensions for the sources in that library; Android APK extensions cannot run on Windows.
3. In Mihon Windows, open **Settings → Migration → Choose…**, select the `.tachibk`, then choose **Import backup**. The app reports how many manga, chapters, categories, and tracker entries it imported.
4. Reopen **Library** and verify representative titles, chapters, bookmarks, and reading position. Restore tracker sign-ins in Settings before expecting new remote progress syncs.
5. To move a local library, copy the actual manga folders/archives separately into the **Library** directory shown in Windows Settings. The backup contains references and metadata, not the image/archive files.

Import merges entries by Android source ID and manga URL. The same backup can be imported again without duplicate entries or doubled history duration. Existing matching records are updated from the backup. The import runs in one database transaction, so malformed or oversized input does not leave a partly applied backup.

Android app and source preferences, account tokens, and APK extensions are not imported. Their Desktop equivalents have different platform semantics or credential storage. Review settings and sign in again after import. Back up the existing Windows profile before importing into a library with different changes if you need to preserve both versions.
