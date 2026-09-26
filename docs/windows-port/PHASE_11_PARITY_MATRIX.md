# P11 Windows feature-parity and migration audit

Status: Desktop implementation and local-fixture gates complete; clean-profile, assistive-technology, and release packaging validation is tracked for P12/P13.

Classification key: **shared/desktop complete**, **Desktop equivalent**, **Android-only**, or **deferred with reason**.

| Android screen or service group | Desktop disposition | Notes / remaining release validation |
| --- | --- | --- |
| Library, manga details, categories | Desktop equivalent | Existing Mihon schema; browse/add/remove, categories, manga metadata, chapter list, source lookup, and settings-backed library update interval. |
| Updates and history | Desktop equivalent | Local repository views and manual/scheduled-while-open updates. No hidden closed-app worker by design (P9). |
| Browse, source search, extensions | Desktop equivalent | Native JVM `.mihonext` install, repository index, explicit signing trust, filters/search, source API. Android APK/Dex packages are not loaded on Desktop. |
| Reader and chapter actions | Desktop equivalent | P8 parity audit covers main modes and input. Current chapter bookmark persists to the shared schema. Page save/share and WebView/cookie actions are Desktop-specific or not exposed. |
| Downloads / offline reading | Desktop equivalent | Queue, restart recovery, offline page loading, and visible in-app/tray notifications (P9). No Android foreground service or cellular-only rule. |
| Tracking | Desktop equivalent | All 11 Android tracker IDs have local HTTP/account fixtures; live services remain P13 validation. Credentials use Windows DPAPI where applicable. |
| Links, browser, clipboard, protocol registration | Desktop equivalent | Explicit per-user `mihon://` registration; user may opt in/out. File associations are not registered. |
| Android backup and restore | Desktop equivalent | `.tachibk` protobuf import merges manga, categories, chapters, progress/bookmarks, history, and tracker entries by Android source ID + URL into Mihon's schema. It is size-limited and transactional. Android preferences/source secrets are deliberately not applied. |
| Appearance and language | Desktop equivalent / deferred | Window sizing and OS locale are used; reader display controls persist. Android edge-to-edge/system-bar/theme-picker behavior is inapplicable. The current Desktop controls still use English literals in portions of the shell; full localized UI is a known usability follow-up, not silently classified as translated. |
| Library preferences | Desktop equivalent / deferred | Update interval/manual update and categories exist. Android phone-grid columns, tab/pager configuration, Android widgets, and mobile gestures do not apply; advanced Android sort/filter preferences are not migrated. |
| Reader preferences | Desktop equivalent / deferred | Desktop mode, direction, fit, color filter, brightness, keyboard shortcuts, and fullscreen are supported. Android volume keys, orientation lock, screen-on, cutout, touch-only controls, 3D transition effects, crop/rotation presets, and HDR pipeline are intentionally excluded (see P8 audit). |
| Download preferences | Desktop equivalent / Android-only | Desktop queue/retry/concurrency and in-app scheduler are supported. Android battery-optimization, notification permission, foreground-service, and Wi-Fi/cellular restrictions have no direct Windows equivalent. |
| Browse / extension settings | Desktop equivalent / Android-only | Desktop repository index and trust are provided. Android extension-store APK installation, package-manager settings, and Android cookie/WebView controls are not exposed. |
| Tracking settings | Desktop equivalent | Desktop login/status and per-provider link flows are available; live provider accounts and external-browser custom-protocol delivery remain unverified. |
| Data and storage settings | Desktop equivalent / deferred | Per-user data roots and Android backup import exist. Android SAF permissions, Android auto-backup schedule and Android-only source preferences are not imported. |
| Security settings | Android-only / deferred | Biometric app lock and Android app-integrity controls are not copied. Windows account ACLs and DPAPI protect profile data/secrets; extension trust is explicit but Java extensions execute with the user's process privileges. |
| Advanced, diagnostics, worker info, statistics | Android-only / deferred | Android WorkManager inspection, Android log/worker screens, crash reporting, and handset usage statistics are not applicable. Desktop errors remain visible in the UI; richer diagnostics are not a release requirement. |
| About, licenses, updater | Desktop equivalent / deferred | Desktop version metadata is available. Native release package, license bundle audit, and installer-driven upgrade are P12 tasks. |
| Onboarding, crash screen, notification controls, Android widgets | Android-only | Permission requests, Activities, shortcuts/widgets, crash restart flow, and Android notification channels belong to the Android app and remain unchanged. |

## Settings migration policy

Import is additive/update-by-identity, not a raw SQLite copy. A match is `(source_id, manga_url)`; chapters match `(manga_id, chapter_url)`. Categories match by name. Existing matching tracker rows are replaced with the backup values. Re-importing the same backup is idempotent for chapter history durations and does not duplicate manga, chapters, categories, or trackers.

App/source preferences and tracker credentials are not applied. Their backup representation includes Android-specific or sensitive values whose Windows meaning is not guaranteed; users should review Desktop settings and sign into trackers again. This avoids silently importing Android secrets into a different OS credential store.

## Local verification and release checks

- Android-model-to-Desktop-model protobuf compatibility is tested from the Android unit-test target.
- Desktop fixture imports the gzip container into an in-memory copy of the Mihon schema, checks library/category/chapter/history/tracker state, and imports twice to verify idempotence.
- Gzip decompression has a 256 MiB ceiling to reject expansion bombs before unbounded allocation.
- A 10,000-entry repository fixture checks the large-library query target on the current Windows reference host.
- Keyboard navigation and labeled text controls are present; Settings now scrolls as a single accessible focus sequence. Screen-reader and mixed-DPI manual checks remain a P13 release checklist item because a clean test profile/device is unavailable.

## Security review summary

- Extension packages are signature/digest verified and require explicit signer trust; downgrade and silent signing-key replacement are rejected. The isolated classloader prevents dependency collisions but is not an OS sandbox. Extensions are executable local code and must be treated like installed applications.
- Backup input is size bounded before parse/decompression; SQL application is one transaction and uses bound parameters. URLs are stored as data and never used as filesystem paths.
- Archive extraction path validation, loopback OAuth state checks, DPAPI secret storage, URL scheme allow-lists, and local IPC loopback binding are covered by their phase fixtures and notes. The installer/update trust boundary belongs to P12 and is not yet verified.
- No new P11 release blocker was found in the inspected local code. Unsigned artifacts remain explicitly development-only until Authenticode signing material is supplied in P12.
