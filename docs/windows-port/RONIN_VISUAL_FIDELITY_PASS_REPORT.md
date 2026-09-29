# Ronin visual fidelity implementation pass — 2026-09-28

Branch: `chatgpt/ronin-p10-visual-fidelity`

Starting HEAD: `81b7204d3f45b7b80e912bc7ed2af1ea4d4e1ea9`

Implementation and automated checks are complete. Rendered visual approval remains with the user. Native window inspection was explicitly waived for this session. The five actual WEBP mockups were opened in the preceding turn, alongside the visual specification; this pass compares those images with the Compose implementation, not with captured runtime output.

## Discrepancies and changes

| Area | Structural gap | Implemented change |
| --- | --- | --- |
| Foundation | Page titles used the same 20sp style as section headings; interactive chips changed size on selection; panel borders competed with covers. | Explicit page-heading variant uses the existing 36sp serif display style. Stable interactive chip geometry, solid coral selection, selected semantics, quieter panel borders, neutral secondary buttons, explicit field surfaces/focus borders, shared theme shapes. |
| Shell | Expanded sidebar consumed 220dp; Library and Search used different inspector widths and thresholds; branding did not use the existing serif brand style. | 196dp expanded sidebar, serif RONIN wordmark, unified 280dp inspectors at 1080dp available content width, restrained lower-sidebar tonal gradient. Compact navigation remains at the existing 900dp window breakpoint. |
| Library | Tall boxed filter groups, horizontal Continue Reading cards, no separate recently updated shelf, and a fixed shelf competing with the collection for window height. | Compact wrapping filter rows and a sort dropdown retaining every existing option; 164dp portrait Continue Reading cards; Recently Updated shelf from positive existing `last_update` timestamps; shelves scroll with the collection in grid and list modes. Shared 152dp minimum grid cells. Scrollable inspector with cover-first hierarchy. |
| Search | Source selector and boxed toolbar dominated the search area; grid/inspector dimensions differed from Library. | Prominent full-width query row with source selector below, simpler page subtitle, shared grid metrics and inspector dimensions, scrollable cover-first inspector, constrained result-heading width for long queries. Existing single-source search and pagination remain. |
| Extensions | Different inspector dimensions and rows stacking their actions even at ordinary desktop content widths. | Shared operations-panel dimensions, scrollable engine context, quieter row borders, reduced vertical padding, actions alongside identity above 560dp row width. Existing repository/trust data and actions remain. |
| Settings | Weak page identity, broad sub-navigation, stock text-button geometry, sub-navigation without scrolling. | Serif Settings heading retaining current section context, 176dp regular sub-navigation, scrollable navigation, shared control shapes and text-button height, wrapping backup controls. Password transformations and all handlers retained. |
| Downloads | Stock row action buttons and non-wrapping toolbar. | Shared compact actions, page-heading hierarchy, wrapping queue toolbar. Actual status, progress, error and action conditions retained. |
| Updates / History | Small page titles, inconsistent summary width, tall summaries competing with feeds in short windows. | Serif page headings, shared inspector width/breakpoint, bounded scrollable summaries on narrower windows. |
| Sources / Categories | Page hierarchy weaker than reference screens. | Serif page headings; inherit shared field, panel, chip, button and theme treatment. Source results also inherit the shared manga grid. |
| Reader | Functional title typography and abrupt text clipping. | Serif manga title in expanded top chrome; manga/chapter ellipsis. Shared panels/buttons inherit the common presentation refinements. No reader input, timing, navigation, loading, fit, zoom or session logic changed. |

Recently Updated uses the same stored timestamp field as the existing Library sort. Entries without positive timestamps do not appear; this is not a fabricated chapter-update feed. The full collection and original sort defaults remain available.

## Responsive reasoning and limitations

- At 800×560 logical size, navigation remains compact, inspectors collapse, controls wrap, Library filter groups have a bounded scroll region, and shelves scroll inside the collection instead of reserving its height.
- Library, Search and Extensions use the same inspector threshold measured after sidebar/gutters. At 1080p and wider, cover grids expand adaptively while inspectors retain a fixed width.
- Settings navigation and tall context panels scroll. Narrow feed summaries are capped at 180dp, leaving space for the feed.
- Button labels and Reader titles use ellipsis where appropriate. Source result headings receive a constrained share of their header row.
- These are code-level layout checks, not observed window-size or DPI tests.

Atmosphere uses a subtle sidebar surface gradient plus the pre-existing shell surface treatment. No raster artwork, manga sample content, fake extensions, statistics or progress were added.

## Protected functionality

No backend, repository, database, source engine, extension engine, tracker implementation or Reader session code was modified. Changes to DesktopShell replace presentation wrappers and spacing only; existing callbacks remain intact. The added Library shelf is a presentation projection of existing state. No branch switch, merge, rebase, reset, force push, Git configuration change or fetch-refspec change was performed.

## Changed files

All Kotlin paths below are relative to `desktopApp/src/desktopMain/kotlin/mihon/desktop/`:

- `design/RoninDesignTokens.kt`
- `design/RoninTheme.kt`
- `RoninComponents.kt`
- `DesktopNavigation.kt`
- `LibraryScreen.kt`
- `UpdatesHistoryScreens.kt`
- `SourcesScreen.kt`
- `SearchScreen.kt`
- `ExtensionsScreen.kt`
- `CategoriesScreen.kt`
- `DesktopSettingsNavigation.kt`
- `DesktopShell.kt`
- `DesktopCompileCompat.kt`
- `DesktopDownloadCard.kt`
- `DesktopReader.kt`

Documentation: `docs/windows-port/RONIN_VISUAL_FIDELITY_PASS_REPORT.md` (this file).

## Validation

Commands executed during this continuation:

```powershell
git status --short
git branch --show-current
git rev-parse HEAD
.\gradlew.bat :desktopApp:compileKotlinDesktop :desktopApp:spotlessKotlinCheck
.\gradlew.bat :desktopApp:spotlessKotlinApply
.\gradlew.bat :desktopApp:compileKotlinDesktop :desktopApp:spotlessKotlinCheck :desktopApp:desktopTest
.\gradlew.bat :desktopApp:run
git diff --check
```

The first combined compile/format check compiled successfully but failed formatting. The first formatting application reported one overlong line; that line was fixed and formatting then succeeded. Following the last Kotlin edits, compile, formatting check and Desktop tests all returned **BUILD SUCCESSFUL**. The run task also returned **BUILD SUCCESSFUL**. Diff whitespace checks passed.

Desktop test XML totals: 23 suites, 45 tests, 0 failures, 0 errors, 2 skipped (43 passed). The skipped tests are the existing Suwayomi owned-process startup and installed Keiyoushi readable-image integration tests. Existing Gradle deprecation and Kotlin warnings remain.

The run task exited quickly. Main supports forwarding to an existing instance, so this task result does not establish that a fresh window rendered the new code. No native UI review or visual-regression certification is claimed.

The initial commit attempt reported missing author identity. The user supplied angelgarosario@gmail.com; the commit uses angelgarosario as the author name (derived from the email) through command-scoped Git options. Repository and global Git configuration remain unchanged. Final commit/status information is recorded in the accompanying chat handoff.

## Manual visual validation still required

1. Close any existing Ronin instance and launch the rebuilt application; compare Library, Search, Extensions, Settings and Reader against the five approved images.
2. Check 800×560, normal 1080p, wide desktop and Windows DPI scaling: shelves, filter scrolling, sort menu, inspector collapse, long titles, backup controls and queue toolbar.
3. Check Continue Reading and collection actions with real data, including empty Library, missing covers, many categories and long source names.
4. Smoke-test Reader modes, RTL/LTR, double page, zoom, keyboard/wheel navigation, auto-hide, chapter transitions and persisted progress. These behaviors were not intentionally changed; automated tests are not a substitute for the previously approved manual Reader checks.
5. Validate real extension install/update/trust operations and tracker/download controls as appropriate; the two live-engine integration tests were skipped.

Known deviations: no bespoke moon/shrine/mountain artwork; current R monogram remains; inspectors contain only existing available metadata/actions and start below screen controls; no illustrative mockup filters, ratings or fabricated data; Settings retains its existing section-based content and controls rather than reproducing unsupported illustrative preferences. Font rendering, final density, clipping and exact visual proportions still require manual comparison.
