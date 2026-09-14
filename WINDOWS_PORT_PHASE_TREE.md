# Mihon Windows Port — Authoritative Phase Tree

Status: planning baseline

Target repository: `Angeltr167/mihon-windows`

Upstream baseline: `mihonapp/mihon@b2cda03e3af23035b7d71629703b8ebc89f14eec`

Baseline Git tree: `10fde311025ac7f915357d03f32861f413dc9d7d`

Protected baseline branch: `baseline/mihon-main-2026-09-14`

## Mission

Build a first-class native Windows version of Mihon without replacing the Android application, without embedding an Android runtime, and without losing the behavior and data model already proven in the existing codebase.

The port stays in Kotlin. Shared code moves toward Kotlin Multiplatform/JVM where that materially reduces duplication, while Android-only integrations remain Android implementations and Windows receives explicit desktop implementations. Compose Multiplatform is the preferred UI layer for the Windows application.

The Android app must remain buildable and behaviorally intact throughout the migration. No phase is allowed to trade Android correctness for desktop progress.

## Architectural facts from the current codebase

The current Gradle graph contains `:app`, `:core-metadata`, `:core:archive`, `:core:common`, `:core:metro`, `:data`, `:domain`, `:i18n`, `:presentation-core`, `:presentation-widget`, `:source-api`, `:source-local`, `:telemetry`, icon modules, and the Android baseline-profile module.

The repository already contains Kotlin Multiplatform infrastructure through `PluginKotlinMultiplatform`, but it currently configures only an Android KMP target. `:i18n` already uses this infrastructure. This is the seam used to introduce the Desktop/JVM target instead of inventing a separate language stack.

`data` owns the SQLDelight database and already separates generated database/schema logic from the Android driver binding. This makes the schema, queries, repositories, migrations, adapters, serialization, and most domain data behavior reusable on Desktop once a JVM SQLite driver is bound.

`source-api` contains the source contract used by extensions (`Source`, `HttpSource`, `SourceFactory`, source models and requests). The contract is conceptually portable, but the module and some helpers currently pull Android dependencies. It must become the compatibility boundary shared by Android and Desktop.

`NetworkHelper` currently directly receives Android `Context`, stores cache under `context.cacheDir`, uses `AndroidCookieJar`, and installs an Android WebView-based `CloudflareInterceptor`. HTTP behavior is reusable; cache location, cookies and challenge solving are platform implementations.

`ExtensionLoader` is an Android package/APK/Dex loader. It uses `PackageManager`, package metadata, signatures, Android package installation semantics and `DelegateLastClassLoaderCompat`. This is the largest non-portable subsystem. Windows will need a JVM extension package and loader while preserving the source contract, trust rules, version rules and failure isolation semantics.

The reader is currently hosted by Android `ReaderActivity` and mixes shared state/reader logic with Android windowing, input and viewer implementations. Pager/webtoon viewers are Android View based. Reader state and loading behavior should be extracted; the desktop renderer should be implemented specifically for mouse, keyboard, resizing and desktop display characteristics.

Downloads currently rely on Android WorkManager through `DownloadJob`. Download orchestration can be shared, but scheduling, network state, foreground service behavior and notifications require Desktop implementations.

## Non-negotiable rules

1. `baseline/mihon-main-2026-09-14` is immutable reference material.
2. Every implementation phase happens on its own feature branch and enters `main` through a reviewable PR.
3. Android compile/tests are a gate for every phase touching shared code.
4. Desktop code must not depend on Android framework classes.
5. Shared modules may use `commonMain` where truly portable and a JVM-shared source set where Java APIs are intentionally shared between Android/JVM targets.
6. No APK-to-JAR runtime conversion, Android emulator, WSA dependency, or embedded Android runtime is part of the product architecture.
7. Database schema compatibility and backup compatibility are preserved unless an explicitly versioned migration is introduced.
8. Existing extension trust/version/content-warning semantics are preserved when designing the Desktop extension format.
9. A phase is not complete because it compiles; its exit gate must pass.
10. No destructive refactor is merged together with an unrelated feature port.

---

# Phase Tree

```text
PHASE 0  Baseline, controls and regression guardrails
   |
   v
PHASE 1  Desktop/JVM build system + empty Windows shell
   |
   v
PHASE 2  Platform abstraction layer + Desktop DI/bootstrap
   |
   v
PHASE 3  Shared core/domain/data/source contracts
   |\
   | +--> PHASE 4  Storage, archives, local source, backup I/O
   |
   +----> PHASE 5  Networking, cookies and browser challenge layer
                     |
                     v
               PHASE 6  Desktop extension system
                     |
                     v
               PHASE 7  Real Desktop application shell
                     |
             +-------+--------+
             |                |
             v                v
       PHASE 8 Reader    PHASE 9 Downloads + schedulers
             |                |
             +-------+--------+
                     |
                     v
               PHASE 10 Trackers, OAuth, links and system integrations
                     |
                     v
               PHASE 11 Feature parity, migration and UX hardening
                     |
                     v
               PHASE 12 Windows packaging, updater, CI and release
                     |
                     v
               PHASE 13 Release-candidate validation and stable 1.0
```

---

## PHASE 0 — Baseline, controls and regression guardrails

Branch family: `phase/00-*`

### Goal

Make the Android baseline reproducible and establish controls that prevent the Windows work from silently breaking existing Mihon behavior.

### Work

#### 0.1 Freeze the baseline

Already established:

- upstream commit `b2cda03e3af23035b7d71629703b8ebc89f14eec`
- upstream/current tree `10fde311025ac7f915357d03f32861f413dc9d7d`
- protected reference branch `baseline/mihon-main-2026-09-14`

#### 0.2 Record module boundaries

Generate and maintain a dependency map for all Gradle modules. Mark each dependency as:

- portable now
- portable after abstraction
- Android-only by design
- replace on Desktop

Initial Android-only islands include `:app`, `:presentation-widget`, `:baseline-profile`, Android WorkManager jobs, Android notifications, Activities/Intents, PackageManager extension loading, Android WebView challenge handling and platform storage/file-picker code.

#### 0.3 Establish CI gates

Keep the existing Android build/test/Spotless/SQLDelight migration gates and add a dedicated Windows-port validation group as desktop targets appear.

#### 0.4 Record compatibility fixtures

Capture representative database, backup and source/extension metadata fixtures before refactoring so later phases can prove compatibility instead of assuming it.

### Exit gate P0

- Baseline branch exists and does not move.
- Current Android CI remains green.
- Module/platform inventory is checked into the repo.
- Database/backup compatibility fixtures exist.
- No production behavior changed.

---

## PHASE 1 — Desktop/JVM build system and empty Windows shell

Branch family: `phase/01-desktop-bootstrap`

### Goal

Make the repository capable of building and launching a native Desktop/JVM application on Windows while changing no Mihon functionality yet.

### Code areas

- `settings.gradle.kts`
- `gradle/build-logic`
- version catalogs
- new `:desktopApp`
- `:i18n` resource wiring

### Work

#### 1.1 Add a Desktop/JVM target to build logic

Extend `PluginKotlinMultiplatform` so modules can expose both Android and JVM/Desktop targets. Do not blindly put Java-dependent code in `commonMain`; use a JVM-shared hierarchy when appropriate.

#### 1.2 Add Compose Multiplatform build support

Create a desktop-capable Compose convention instead of mutating the current Android-only Compose convention into an ambiguous hybrid.

#### 1.3 Add `:desktopApp`

Create a minimal JVM entrypoint that opens a Compose Desktop window and can load shared resources. It must not depend on `android.*`.

#### 1.4 Add Desktop CI compile task

At minimum, CI must compile desktop sources. A Windows runner is introduced before native packaging becomes a gate.

### Exit gate P1

- `:desktopApp` launches a window on Windows 11.
- Desktop source sets compile without Android framework classes.
- Android app still builds/tests exactly as before.
- No database/source/reader behavior has been ported yet.

Milestone: **M1 — Windows process launches from the Mihon repository.**

---

## PHASE 2 — Platform abstraction layer and Desktop bootstrap

Branch family: `phase/02-platform-foundation`

### Goal

Create explicit seams around Android services currently leaking into reusable logic.

### Target structure

```text
core/
  common/
platform-api/             # contracts / expect-actual seams where useful
platform-android/         # Android implementations
platform-desktop/         # Windows/JVM implementations
desktopApp/               # Windows composition root / entrypoint
```

Exact module names may change during implementation, but responsibilities may not be collapsed back into `Context` calls in shared code.

### Work

#### 2.1 Application directories

Abstract cache, config, database, downloads, local library, extension and temporary directories.

Desktop default roots should follow Windows conventions, with user-configurable library/download locations.

#### 2.2 Preferences

Separate preference contracts from Android preference storage. Preserve keys/defaults where shared behavior is intended.

#### 2.3 System services

Introduce interfaces for:

- clipboard
- system browser
- file/folder chooser
- notifications
- network state
- locale
- app version/build metadata
- URI/deep-link handling
- process/application lifecycle
- window-independent share/open operations

#### 2.4 Metro DI composition roots

Keep Metro as the object graph mechanism where practical, but create Android and Desktop composition roots instead of resolving application services through Android `Context`.

### Exit gate P2

- Desktop app starts with a real Desktop dependency graph.
- Platform contracts have Android and Desktop implementations or explicit placeholders covered by tests.
- Shareable services no longer need an Android `Context` merely to locate files or platform services.
- Android behavior remains green.

---

## PHASE 3 — Shared core, domain, data and source contracts

Branch family: `phase/03-shared-core-*`

### Goal

Move the business/data/source foundation used by both applications into shared Android+JVM code without destabilizing the schema or extension-facing contracts.

### Order of migration

#### 3.1 `:core:common`

Split portable utilities from Android utilities. Networking is only partially moved here; browser/challenge work waits for Phase 5.

#### 3.2 `:source-api`

Make source models and contracts available to Desktop/JVM. Preserve externally observable source interfaces so extension source implementations do not require two business-logic APIs.

Android preference or UI dependencies currently pulled into the module must be moved behind a boundary or to a platform source set.

#### 3.3 `:domain`

Move models/interactors/repositories that are platform-neutral. Push Android storage, Android paging adapters and other framework integrations outward rather than contaminating the shared domain source set.

#### 3.4 `:data`

Convert SQLDelight-backed data/repositories to shared targets.

Keep the current schema and migration history. Bind:

- Android -> current Android/Bundled SQLite path
- Desktop -> SQLDelight JVM SQLite driver

#### 3.5 JVM unit-test target

Run domain/data/source contract tests directly on JVM, independent of Android instrumentation.

### Exit gate P3

- A Desktop process can open/create the Mihon database through the Desktop driver.
- Core repository/interactor tests run on JVM.
- SQLDelight migration verification still passes.
- Android database files remain compatible.
- `source-api` is consumable by a plain JVM extension test fixture.

Milestone: **M2 — Windows has the real Mihon data/domain foundation, not mocked state.**

---

## PHASE 4 — Storage, archives, local source and backup I/O

Branch family: `phase/04-storage-local`

Depends on: P2 + P3

### Goal

Make local manga, archive reading and backups usable through native Windows file APIs.

### Work

#### 4.1 Desktop filesystem backend

Implement storage with `java.nio.file.Path`, `Files`, streams and atomic/defensive file operations. Android keeps SAF/UniFile behavior.

#### 4.2 `:core:archive`

The current archive implementation contains Android-native assumptions. Define a shared archive interface and a Desktop backend that covers the formats actually supported by Mihon.

Before selecting the Desktop archive library, build compatibility fixtures for ZIP/CBZ and every other format accepted by the Android build.

#### 4.3 `:source-local`

Port local source discovery, metadata, cover management, chapter ordering and file observation to the Desktop filesystem implementation.

#### 4.4 Backup serialization vs backup I/O

Keep backup model/serialization shared. Replace Android `Uri`, Activity result/OpenDocument and WorkManager boundaries with Desktop file choosers and Desktop jobs.

#### 4.5 Round-trip verification

A backup created by Android must be restorable on Desktop and vice versa when both are on compatible schema versions.

### Exit gate P4

- Windows imports a local manga folder.
- Supported archive fixtures open correctly.
- Covers/metadata/local chapters match Android semantics.
- Android -> Windows -> Android backup round-trip passes fixture verification.

---

## PHASE 5 — Networking, cookies and browser challenge layer

Branch family: `phase/05-network-stack`

Depends on: P2 + P3

### Goal

Preserve Mihon's source HTTP behavior on Desktop without carrying Android WebView or Android cookie APIs into shared code.

### Work

#### 5.1 Split `NetworkHelper`

Create a platform-neutral client factory containing:

- OkHttp timeouts
- user agent handling
- HTTP logging
- headers/interceptors that are truly portable
- DNS-over-HTTPS selection
- cache policy

Provide platform bindings for cache directory and cookies.

#### 5.2 Desktop cookie jar

Implement persistent/session cookie behavior compatible with source expectations and browser challenge handoff.

#### 5.3 Challenge solver interface

Replace direct construction of `CloudflareInterceptor(context, ...)` with a `ChallengeSolver`/browser-session boundary.

Android implementation continues using the existing WebView path.

#### 5.4 Desktop browser technical spike

Test at least the viable Windows approaches against real challenge/cookie transfer fixtures. Primary candidates are a WebView2 integration or a Chromium/JCEF integration. Select based on:

- challenge success rate
- JS/browser compatibility
- cookie synchronization with OkHttp
- installer size
- memory cost
- maintenance burden

This decision must be evidence-driven; it is a gate, not a preference.

#### 5.5 Network regression suite

Verify normal HTTP sources, redirects, cookies, rate limiting, DoH, challenge recovery, cancellation and errors.

### Exit gate P5

- `HttpSource` requests work on Desktop through the same high-level API.
- Cookies persist and synchronize with the challenge browser.
- Selected Desktop challenge implementation passes fixtures.
- No Android WebView class is referenced by Desktop code.

Milestone: **M3 — Windows can execute real Mihon HTTP source logic.**

---

## PHASE 6 — Desktop extension system

Branch family: `phase/06-extensions-*`

Depends on: P3 + P5

### Goal

Provide a secure JVM-native equivalent of Mihon's Android extension system.

### Key constraint

Existing Android extensions are APK/Dex packages. The current loader depends on Android `PackageManager`, APK metadata/signatures and a Dalvik/Android classloader. Windows must not pretend those APKs are JVM JARs.

### Work

#### 6.1 Extract platform-neutral extension policy

Move these semantics out of Android package loading:

- extension identity
- version/version-code comparison
- extension-lib compatibility
- content warnings
- trust state
- signature/fingerprint model
- loaded/not-loaded failure model
- source/factory instantiation result

Android continues adapting APK metadata into this policy model.

#### 6.2 Define Desktop package format

Working format:

```text
*.mihonext
  manifest.json
  extension.jar
  icon.*
  signature / signing metadata
```

The exact container may be ZIP-based, but it must be versioned and documented.

#### 6.3 Desktop classloader

Implement an isolated JVM loader with explicit parent/child delegation rules so extension dependencies do not accidentally shadow Mihon internals.

The loader must instantiate the same `Source` / `SourceFactory` contract exposed by `source-api`.

#### 6.4 Signing and trust

Desktop installation must reject malformed/unsafe packages predictably, preserve fingerprint trust decisions and prevent silent downgrade/signature replacement.

#### 6.5 Extension build tooling

Create tooling/conventions that allow an extension codebase to produce Android APK and Desktop `.mihonext` artifacts from shared source logic where dependencies permit.

#### 6.6 Repository/update flow

Implement Desktop extension discovery/update metadata separately from the Android package installer mechanism.

### Exit gate P6

- A reference extension builds for JVM/Desktop.
- It installs into the Desktop extension directory.
- Signature/trust/version checks pass/fail as designed.
- It loads through the Desktop classloader.
- Its sources appear and execute through the normal source API.
- One bad extension cannot crash loading of all extensions.

Milestone: **M4 — Windows can install and run real Desktop Mihon extensions.**

---

## PHASE 7 — Real Desktop application shell

Branch family: `phase/07-desktop-ui-*`

Depends on: P3 + P4 + P5 + P6

### Goal

Turn the bootstrap window into a usable Mihon desktop application backed by real repositories and sources.

### Work

#### 7.1 Navigation architecture

Use desktop-first navigation rather than stretching the phone bottom bar. Recommended shell:

- resizable main window
- navigation rail/sidebar
- title/toolbar area
- content pane
- optional details pane where useful

#### 7.2 Shared Compose presentation

Reuse portable `presentation-core` components and screen state. Move Android-only Compose helpers behind platform APIs.

`:presentation-widget` remains Android-only.

#### 7.3 Primary screens

Port in dependency order:

- Library
- Updates
- History
- Browse/Sources
- Extension management
- Manga details
- Search
- Categories
- Settings

#### 7.4 Desktop interaction layer

Add keyboard navigation, shortcuts, hover states, context menus, multi-selection where appropriate, window-state persistence and responsive minimum sizes.

#### 7.5 Image pipeline

Use a Desktop-compatible image loader/decoder while preserving cache keys, headers and authenticated-source image behavior.

### Exit gate P7

- User can add/browse/search a real source.
- User can open manga details and add/remove library entries.
- Library/history/categories persist across restart.
- Core screens work with keyboard and mouse.
- Android navigation/UI remains unaffected.

Milestone: **M5 — Windows is a usable manga-library client before reader parity.**

---

## PHASE 8 — Desktop reader

Branch family: `phase/08-reader-*`

Depends on: P4 + P5 + P7

### Goal

Implement a desktop-native reader while preserving Mihon's reader state, chapter loading, progress and preference semantics.

### Work

#### 8.1 Extract `reader-core`

Separate reusable logic from `ReaderActivity` and Android viewers:

- reader chapter/page models
- adjacent chapter transitions
- page loading state
- progress/history updates
- preload decisions
- reading mode settings
- error/retry state

#### 8.2 Desktop renderers

Implement Compose Desktop viewers for:

- single page
- double page
- right-to-left manga
- left-to-right
- vertical paging
- continuous/webtoon

#### 8.3 Desktop controls

Support:

- mouse wheel
- click zones
- keyboard next/previous
- configurable shortcuts
- zoom
- pan
- fit width
- fit height
- original size
- fullscreen
- chapter navigation
- page indicator

#### 8.4 Image behavior

Preserve authenticated page requests, image transforms/settings, prefetching and failure retry behavior. Add memory-pressure tests for long webtoon chapters and high-resolution pages.

#### 8.5 Specialized viewer parity

Audit every Android reader mode/feature still present at implementation time, including the current WebGPU viewer. Either implement an equivalent, formally replace it with a desktop implementation, or document an explicit parity exception before release.

### Exit gate P8

- Online and local chapters open.
- All primary reading directions work.
- Reading progress survives restart and chapter changes.
- Long webtoon and high-resolution stress fixtures remain within defined memory targets.
- Keyboard/mouse/fullscreen behavior is stable.

Milestone: **M6 — Windows can browse and read end-to-end.**

---

## PHASE 9 — Downloads, background work and library scheduling

Branch family: `phase/09-downloads-jobs`

Depends on: P4 + P5 + P7

### Goal

Replace Android WorkManager/foreground-service orchestration with Desktop scheduling while keeping download business rules shared.

### Work

#### 9.1 Split download engine from scheduler

Keep queue/state/retry/download logic reusable.

Implement:

- `AndroidDownloadScheduler` -> existing WorkManager path
- `DesktopDownloadScheduler` -> coroutine/process lifecycle scheduler

#### 9.2 Desktop persistence/resume

Downloads must resume safely after app restart. Partial files and queue state require explicit recovery rules.

#### 9.3 Network constraints

Translate Wi-Fi/network preferences into meaningful Desktop behavior; do not copy Android cellular semantics literally where Windows cannot identify them reliably.

#### 9.4 Notifications and tray

Expose progress/errors through desktop notifications and optional system tray state.

#### 9.5 Scheduled library updates

Implement in-app scheduled updates first. If parity requires execution while the UI is closed, add a deliberate Windows background/startup strategy rather than leaving a hidden permanent process by accident.

### Exit gate P9

- Queue add/pause/resume/cancel works.
- Restart recovery works.
- Downloaded chapters are readable offline.
- Scheduled updates operate according to documented Desktop lifecycle rules.
- Android WorkManager behavior is unchanged.

---

## PHASE 10 — Trackers, OAuth, links and Windows integrations

Branch family: `phase/10-integrations`

Depends on: P7 + P8

### Goal

Bring external tracker and operating-system integration to feature parity.

### Work

#### 10.1 Tracker core

Reuse tracker network/business logic where it is already platform-neutral.

#### 10.2 OAuth/login

Replace Android login Activities/callback Intents with Desktop system-browser flows using a secure loopback callback and/or registered custom protocol where required by the provider.

#### 10.3 URI/deep links

Define Desktop routing for Mihon links and supported external URLs.

#### 10.4 Windows shell integration

Implement browser opening, clipboard, share/copy behaviors and optional archive/file associations.

### Exit gate P10

- Every supported tracker can authenticate on Windows or has a documented provider-specific blocker.
- Search/link routing works from an external browser into the app where supported.
- Tracker updates sync correctly after reading.

---

## PHASE 11 — Feature parity, migration and UX hardening

Branch family: `phase/11-parity-*`

Depends on: P4-P10

### Goal

Close remaining behavioral gaps and make Windows feel intentional rather than like an Android port.

### Work

#### 11.1 Feature parity matrix

Compare current Android functionality screen-by-screen and service-by-service. Every item is classified:

- shared and complete
- Desktop equivalent complete
- Android-only by nature
- intentionally deferred with justification

#### 11.2 Settings parity

Audit all preferences. Hide Android-only settings only when they truly have no Desktop meaning; provide Desktop equivalents where possible.

#### 11.3 Import/migration workflow

Support moving an existing Mihon user's backup/local library configuration to Windows without requiring database-file surgery.

#### 11.4 Accessibility and localization

Verify keyboard-only operation, focus order, scaling, high-DPI rendering, text scaling, screen-reader-relevant semantics where supported and all existing i18n resources.

#### 11.5 Performance

Profile startup, large libraries, browse pagination, image cache, reader memory, extension loading and download concurrency.

#### 11.6 Security review

Review:

- extension sandbox/classloader boundaries
- extension signature/trust storage
- archive path traversal
- backup parsing
- URI handlers
- browser OAuth callback
- WebView/JCEF/WebView2 bridge surface
- updater integrity

### Exit gate P11

- No unclassified Android feature remains.
- Backup migration path is documented/tested.
- Critical accessibility and DPI issues are resolved.
- Security review has no unresolved release blocker.
- Performance targets are met on the reference Windows machine class.

---

## PHASE 12 — Windows packaging, updater, CI and release engineering

Branch family: `phase/12-release-engineering`

Depends on: P11

### Goal

Produce installable, updateable and reproducible Windows releases.

### Work

#### 12.1 Native distributions

Configure Compose Desktop/jpackage distributions for Windows. Primary release artifacts:

- `.msi`
- `.exe` installer where supported/desired

Portable ZIP may be produced as an additional artifact, not as the only supported installation path.

#### 12.2 Runtime packaging

Bundle or provision the required JVM/runtime so users do not need to install a development JDK.

#### 12.3 Windows signing

Add Authenticode signing when release credentials are available. Release process must keep signing credentials outside the repository.

#### 12.4 Desktop updater

Do not reuse the Android APK updater. Implement a signed release-manifest/update path suitable for installed Windows packages, or intentionally use installer-driven updates with integrity verification.

#### 12.5 Windows CI

Add a Windows GitHub Actions matrix that builds/tests Desktop and produces release artifacts. Android CI remains independent and mandatory.

#### 12.6 Release metadata

Generate checksums and release notes; preserve Apache-2.0 notices and audit third-party runtime licenses used by the Desktop build.

### Exit gate P12

- Clean Windows machine installs the generated package.
- App launches without developer tools/JDK installation.
- Upgrade preserves library/settings/download state.
- Uninstall behaves predictably.
- Release artifact checksums are reproducible/recorded.
- Windows and Android CI are green.

Milestone: **M7 — Installable Mihon Windows release candidate.**

---

## PHASE 13 — Release-candidate validation and stable 1.0

Branch family: `release/windows-1.0-*`

Depends on: P12

### Goal

Prove the complete system before declaring the Windows port stable.

### Validation matrix

#### 13.1 Fresh install

Empty profile -> extension install -> source browse -> library add -> online read -> download -> offline read -> backup.

#### 13.2 Migrated user

Android backup -> Windows restore -> verify library/categories/history/tracking/preferences where portable.

#### 13.3 Large library

Stress library/search/update behavior with large representative fixtures.

#### 13.4 Extension failure isolation

Malformed, incompatible, unsigned/untrusted and throwing extensions must fail locally without corrupting app startup.

#### 13.5 Network/challenge recovery

Exercise normal HTTP, challenge pages, cookie expiry, offline/reconnect and rate limiting.

#### 13.6 Reader endurance

Long-session and large-chapter tests covering page, double-page and webtoon modes.

#### 13.7 Update path

Install previous RC -> update to current RC -> verify persisted state and extension compatibility.

### Exit gate P13 / Windows 1.0 Definition of Done

A Windows 1.0 release is allowed only when:

- native Windows installer works on a clean supported Windows version
- real Desktop extensions can be installed/updated/loaded securely
- library, browse, search, manga details and categories are functional
- local and online reading are functional
- main reader modes are functional
- downloads and offline reading are functional
- backup/restore is cross-platform compatible within supported versions
- supported trackers work
- settings persist
- Android remains healthy
- release/security/parity matrices have no unresolved blocker

Milestone: **M8 — Mihon Windows 1.0 stable.**

---

# Critical path

The critical engineering sequence is:

`P0 -> P1 -> P2 -> P3 -> P5 -> P6 -> P7 -> P8 -> P11 -> P12 -> P13`

Storage/local/backup work in P4 can proceed in parallel after P3. Downloads P9 can proceed after the storage/network/UI foundations exist. Tracker/system integrations P10 can proceed once the real Desktop UI and reader state are stable.

The two highest-risk technical areas are **Desktop extensions** and **browser challenge handling**. The reader is large but comparatively controllable because its desired behavior is already explicit in the existing application. Extension packaging/classloading and browser challenge/cookie synchronization require early proof before UI polish becomes the focus.

# Branch and merge discipline

Use one parent branch per numbered phase and smaller branches for risky subphases. Example:

```text
phase/05-network-stack
  phase/05a-network-core
  phase/05b-desktop-cookiejar
  phase/05c-browser-spike
  phase/05d-challenge-solver
```

A subphase can merge into its phase branch after focused tests pass. A numbered phase merges to `main` only after its complete exit gate passes.

Never stack multiple unfinished numbered phases directly on `main`.

# Permanent project context to preserve

When development resumes in a later conversation/session, the authoritative recovery order is:

1. Read this file.
2. Read the current phase branch/PR description.
3. Verify `main` and the protected baseline branch.
4. Check the latest completed phase gate.
5. Continue from the first incomplete gate; do not redesign the port from scratch.

If the upstream Android Mihon project advances during the port, upstream synchronization is handled as an explicit maintenance task with tree/diff review. Do not silently replace the baseline or mix an upstream sync into an unrelated Windows feature PR.
