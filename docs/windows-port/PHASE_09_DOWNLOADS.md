# P9 Desktop downloads and scheduled work

Desktop downloads use the source API and a transfer engine independent of the Desktop scheduler. Android `DownloadJob`/WorkManager is unchanged.

- Queue state is stored atomically in `Downloads/Mihon/queue.properties`; chapter files are under `Downloads/Mihon/chapters/<SHA-256 of source ID, manga URL, chapter URL>`. Extension-provided names never become filesystem paths.
- A chapter is visible to the offline reader only after every page is fetched, checked as a decodable image, and the staging directory is committed with a `complete` page-count marker. A crash during a transfer leaves `<key>.partial`, which is discarded and restarted from page one on retry/startup. Incomplete/corrupt committed folders are moved aside before replacement. Completed chapters survive queue-record removal.
- On restart, `RUNNING` becomes `PENDING`; a committed chapter is recognized as `COMPLETED` even if the queue record was not saved before exit. Paused and failed entries remain paused/failed until the user resumes them. Invalid queue metadata is moved to a recoverable `queue.corrupt-*.properties` file.
- Add, pause, resume/retry, and cancel are available in the Downloads screen. Transfers retry up to three times before a visible failure. Pause/cancel interrupt the active coroutine; a blocking source request may finish before cancellation is observed, but no incomplete chapter is committed.
- The scheduler waits when Windows reports no active non-loopback interface. Windows cannot reliably map Android's Wi-Fi-only/cellular distinction, so no cellular-specific policy is copied; individual network failures still retry/fail visibly.
- The optional system tray shows completion/error notifications where supported; the in-app queue is always the status fallback. No hidden process remains after the app closes.
- Library updates are off by default. Settings can schedule them every 6, 12, or 24 hours while Mihon is open; the Updates screen also offers a manual check. Closing the UI stops scheduled work. No closed-app startup/background task is installed.

P9 validation: the local HTTP fixture tests authenticated page transfer and offline reading; queue tests cover pause, resume, cancel, persistence, and crash recovery; a virtual-time scheduler test verifies in-app library updates. The Desktop and Android builds/tests are required before the phase is closed.
