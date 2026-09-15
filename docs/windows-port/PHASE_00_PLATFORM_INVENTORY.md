# Phase 0 — Platform and Module Inventory

Baseline: `mihonapp/mihon@b2cda03e3af23035b7d71629703b8ebc89f14eec`

This inventory is a guardrail for the Windows port. It records where Android framework dependencies currently live and which modules should be shared, adapted, retained as Android-only, or replaced on Desktop.

## Classification

| Module / area | Current role | Windows disposition | Phase |
| --- | --- | --- | --- |
| `:app` | Android application, Activities, jobs, extension loader, reader host, notifications | Keep as Android entrypoint; extract reusable logic and add a separate `:desktopApp` | P1–P11 |
| `:baseline-profile` | Android baseline profiles | Android-only | none |
| `:core-metadata` | Metadata parsing/utilities | Share where framework-free | P3 |
| `:core:archive` | Archive access with Android/native assumptions | Shared interface + Android and Desktop implementations | P4 |
| `:core:common` | Common utilities, preferences/network helpers and Android-adjacent services | Split portable code from platform bindings | P2–P5 |
| `:core:metro` | Metro DI helpers, currently includes Android-context graph access | Preserve Metro; add explicit Desktop composition root | P2 |
| `:data` | SQLDelight DB, repositories, serialization | High-value shared target; Desktop JVM driver | P3 |
| `:domain` | Models/interactors/repository contracts | Primarily shared; move framework adapters outward | P3 |
| `:i18n` | KMP resource module | Extend to Desktop resources | P1 |
| `:icons:material-symbols` | Icons | Share with Compose Desktop where compatible | P1/P7 |
| `:icons:simple-icons` | Icons | Share with Compose Desktop where compatible | P1/P7 |
| `:presentation-core` | Compose presentation primitives | Share portable Compose UI; isolate Android-only pieces | P7 |
| `:presentation-widget` | Android Glance/AppWidget | Android-only | none |
| `:source-api` | `Source`, `HttpSource`, source models/contracts | Critical shared compatibility boundary | P3/P5/P6 |
| `:source-local` | Local manga source | Shared behavior + native filesystem backend | P4 |
| `:telemetry` | Android/app telemetry integration | Optional for personal Windows build; no P1 dependency | later/optional |
| new `:desktopApp` | Windows/JVM entrypoint | Desktop-only | P1 |
| new platform contracts/impls | Files, browser, notifications, clipboard, lifecycle, paths | Shared API + Android/Desktop implementations | P2 |

## Confirmed Android framework islands

### Application lifecycle and UI

- `MainActivity`, `ReaderActivity`, other Activities and Intents.
- Android window/insets/activity-result APIs.
- Android notifications and foreground-service behavior.
- `:presentation-widget` / Glance widgets.

### Reader

`ReaderActivity` hosts reader state and Android-specific rendering/input. Pager and webtoon implementations rely on Android Views (`ViewPager`/`RecyclerView`) while overlays already use Compose in several places. Reader state/loading should be extracted before Desktop renderers are added.

### Networking

`NetworkHelper` currently requires `android.content.Context`, uses `context.cacheDir`, `AndroidCookieJar`, and constructs the Android WebView-based `CloudflareInterceptor`. OkHttp behavior, DoH selection, timeouts and portable interceptors are reusable; cache location, cookie/browser bridge and challenge solving are platform implementations.

### Extensions

`ExtensionLoader` is explicitly APK/Dex/PackageManager based. It reads Android package metadata/signatures and loads extension classes through an Android classloader. Desktop must retain policy semantics while replacing package discovery/loading with a JVM-native extension artifact and classloader.

### Database

`Database`, SQLDelight schema/queries/adapters and repositories are reusable. `AppBindings.providesSqlDriver()` is the platform seam: Android currently binds `AndroidxSqliteDriver` + `BundledSQLiteDriver`; Desktop will bind a JVM SQLite driver.

### Downloads/background work

`DownloadJob` is a WorkManager `CoroutineWorker`. The downloader/orchestration is a candidate for sharing, while scheduling, network-state integration, foreground notifications and process lifecycle need Desktop implementations.

### Files/backups/local source

Backup models and protobuf serialization are portable. Current backup read/write boundaries use Android `Context`, `Uri`, content resolver, UniFile and WorkManager. Desktop must provide normal filesystem paths and native file pickers while preserving the serialized backup format.

## Contracts that must not silently change

1. SQLDelight schema and migration history.
2. Backup protobuf field numbers and semantics.
3. `Source`/`HttpSource`/`SourceFactory` observable contract.
4. Extension version/trust/content-warning behavior.
5. Manga/chapter identity and repository semantics.
6. Reader progress/bookmark/history semantics.
7. Android build and behavior while shared modules move.

## Phase 0 verification assets

- `windows-port-fixtures/database/baseline.sql` — representative manga/chapter rows against the current SQL schema.
- `BackupCompatibilityFixtureTest` — executable protobuf round-trip fixture using current backup models.
- `.github/workflows/windows-port-guardrails.yml` — explicit Windows-port regression gate for formatting, Android unit tests and SQLDelight migrations.

This document must be updated when a subsystem is reclassified. Reclassification is an architectural change and belongs in the phase PR that caused it.
