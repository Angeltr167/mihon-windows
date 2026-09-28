# Ronin Phase 9 — Reader

Status: implementation branch under QA. This document records the real Desktop reader capabilities used by the Ronin Phase 9 UI and the limitations that remain intentionally outside this phase.

## Baseline

- Base branch: `chatgpt/ronin-p08-extensions-categories`
- Exact base commit: `55f812cb2e13e1b66284f83311b35dd6c42e0063`
- Phase 9 branch: `chatgpt/ronin-p09-reader`

The Phase 9 branch was created directly from the exact Phase 8 commit. No previous pull request was merged to create it.

## Real reader capabilities confirmed before redesign

The Desktop reader already had real implementations for:

- remote source page lists and authenticated `HttpSource` image requests;
- local directory/archive/EPUB pages through the Desktop local source;
- completed downloaded chapters through `DesktopDownloadStore`;
- persisted chapter progress and read state;
- chapter bookmark changes and tracker completion sync;
- single-page LTR/RTL, double-page LTR/RTL, vertical, and webtoon modes;
- fit-width, fit-height, and original-size presentation;
- zoom and drag-to-pan for paged reading;
- mouse wheel/touchpad navigation, page tap zones, configurable keys, arrows, Page Up/Down, Space, Escape, and F11;
- fullscreen through the Desktop window placement;
- decoded-image LRU caching and adjacent-page preloading;
- chapter-level loading/retry and page-level decode/request retry;
- page save/open actions and appearance filters.

Phase 9 preserves those real paths instead of adding simulated controls.

## Phase 9 behavior changes

- Reader chrome now follows the shared Ronin graphite/ivory/sage design system.
- Header shows manga/chapter context, current page position, current mode, back action, and fullscreen.
- Footer uses real previous/next page and chapter actions, a real page scrubber, explicit mode and fit menus, zoom state, and a compact overflow menu.
- Controls auto-hide only while the user is not interacting with the header, footer, scrubber, menus, or reader dialogs.
- Pointer movement restores controls; clicking the center reading zone toggles them.
- Compact breakpoints reduce control labels instead of hiding essential actions.
- Chapter-edge navigation is deterministic:
  - natural next-chapter transition opens the first page;
  - natural previous-chapter transition opens the last page;
  - direct chapter opens restore saved progress.
- Previous/next page controls reflect the global first/last readable boundary instead of remaining enabled at a dead end.
- Chapter and individual page loading/error states use visible Ronin states with real retry actions.
- Existing double-page geometry continues to keep every page complete and respects LTR/RTL order.

## Deliberate limitations

Phase 9 does not invent capabilities that the Desktop architecture does not currently provide:

- no Android volume-key, orientation-lock, display-cutout, keep-screen-on, or touch long-press semantics;
- no WebGPU 3D/cube/flip/stack/sphere effects or HDR/gainmap pipeline;
- no automatic wide-page split/rotation, border crop, flash effects, or other Android-only presentation algorithms;
- no fake per-page download telemetry or simulated remote retry backend beyond reissuing the real page request/decode;
- no automatic two-page spread inference from image content; double mode remains a user-selected real reading mode.

These can be reconsidered separately if a future parity phase establishes a concrete Desktop requirement.

## Validation gate

Phase 9 is not complete until formatting, Desktop compilation/tests, smoke checks, Windows guardrails, MSI/EXE packaging, GitHub CI, and the user's local visual/behavior QA are all green, followed by the user's explicit `approved`.
