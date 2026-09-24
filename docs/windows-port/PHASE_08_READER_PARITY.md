# P8 reader parity audit

Status: P8 is still open. This audit compares the current Android reader with the Desktop reader; it does not waive the P8 or Windows 1.0 exit gates.

| Android capability | Desktop disposition |
| --- | --- |
| Horizontal right-to-left/left-to-right, vertical, and continuous/webtoon viewers | Compose Desktop equivalents implemented. Single and double spreads are selectable independently of direction. |
| Online and local chapter page loading, authenticated requests | Source `getPageList` and `HttpSource.getImage` paths are used; local directories/archives/EPUB are supported. Online request-header test and local GUI smoke pass. A real installed extension GUI journey is still needed. |
| Adjacent chapter transitions, page number, history/read state, retry | Functional Desktop controls and shared database persistence implemented. GUI restart test for local progress passed. |
| Tap zones, wheel, keyboard, zoom/pan, fit modes, fullscreen | Desktop equivalents implemented. Configurable extra next/previous keys persist. GUI smoke verified custom keys and fullscreen toggle. |
| Grayscale and inverted colors | Desktop GPU color-matrix filters implemented with a persisted selection. |
| Android WebGPU viewer's basic page/continuous rendering | Formally replaced by Compose Desktop + Skia image rendering, bounded decode and lazy webtoon composition. |
| WebGPU 3D/cube/flip/stack/sphere transitions, gainmap/HDR rendering | Explicit Desktop parity exception: presentation effects, not required to navigate/read pages. No Android-only WebGPU dependency is included. Reconsider at P11 if user research identifies a material reading need. |
| Android display cutout, orientation lock, volume-key navigation, screen-on and touch long-press controls | Android-only hardware/window semantics. Desktop has window/fullscreen and keyboard/mouse controls instead. |
| Automatic wide-page spread detection/splitting/rotation, border crop, custom brightness/color overlays, zoom-start presets, background themes and flash effects | Not yet equivalent on Desktop. These remain P8 feature gaps to resolve or justify explicitly before the P8 gate. |
| Reader page save/share, chapter bookmark, browser/WebView actions, tracker integration | Not yet equivalent. Include in P10/P11 parity review; they cannot be counted as complete by this audit. |

## Reader memory target

The decoded-page LRU retains at most two images of at most 20 million pixels each (at most 160 MB of decoded RGBA pixel data in the cache). Each encoded page is capped at 32 MiB, and at most two page decodes run concurrently. The 1,000-page chapter stress test verifies that decoded cache occupancy never exceeds two pages; high-resolution dimension rejection is tested before decode. A process-level RSS endurance run with representative high-resolution webtoon pages is still required for the P8 exit gate.
