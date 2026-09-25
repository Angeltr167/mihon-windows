# P8 reader parity audit

Status: P8 exit checks passed on the local Windows reference machine. This audit compares the current Android reader with the Desktop reader; it does not waive the P11 parity review or P13 release-candidate validation.

| Android capability | Desktop disposition |
| --- | --- |
| Horizontal right-to-left/left-to-right, vertical, and continuous/webtoon viewers | Compose Desktop equivalents implemented. Single and double spreads are selectable independently of direction. |
| Online and local chapter page loading, authenticated requests | Source `getPageList` and `HttpSource.getImage` paths are used; local directories/archives/EPUB are supported. Online page/request-header tests and local GUI smoke pass. A real installed extension GUI journey remains for P13. |
| Adjacent chapter transitions, page number, history/read state, retry | Functional Desktop controls and shared database persistence implemented. GUI restart test for local progress passed. |
| Tap zones, wheel, keyboard, zoom/pan, fit modes, fullscreen | Desktop equivalents implemented. Configurable extra next/previous keys persist. GUI smoke verified custom keys and fullscreen toggle. |
| Grayscale, inverted colors and brightness | Desktop GPU color-matrix filters and adjustable brightness implemented with persisted selections. |
| Android WebGPU viewer's basic page/continuous rendering | Formally replaced by Compose Desktop + Skia image rendering, bounded decode and lazy webtoon composition. |
| WebGPU 3D/cube/flip/stack/sphere transitions, gainmap/HDR rendering | Explicit Desktop parity exception: presentation effects, not required to navigate/read pages. No Android-only WebGPU dependency is included. Reconsider at P11 if user research identifies a material reading need. |
| Android display cutout, orientation lock, volume-key navigation, screen-on and touch long-press controls | Android-only hardware/window semantics. Desktop has window/fullscreen and keyboard/mouse controls instead. |
| Automatic wide-page spread detection/splitting/rotation, border crop, custom color overlays, zoom-start presets, background themes and flash effects | Explicit Desktop parity exception. These are optional presentation aids; supported pages remain readable with manual fit/zoom/pan and double-page mode. Copying Android's crop/split algorithms and WebGPU-specific effects would add substantial decode/memory complexity. Reassess accessibility and user demand at P11 before release. |
| Reader page save/share, chapter bookmark, browser/WebView actions, tracker integration | Not yet equivalent. Include in P10/P11 parity review; they cannot be counted as complete by this audit. |

## Reader memory target

The decoded-page LRU retains at most two images of at most 20 million pixels each (at most 160 MB of decoded RGBA pixel data in the cache). Each encoded page is capped at 32 MiB, and at most two page decodes run concurrently. The 1,000-page chapter stress test verifies that decoded cache occupancy never exceeds two pages; a second test decodes three actual 4,000 × 5,000 images and verifies eviction. Larger dimensions are rejected before decode.

In a Windows GUI smoke run, a 1,003-page webtoon (three 4,000 × 5,000 pages followed by 1,000 regular pages) reached the last page. Sampled process working set was 337 MB before opening, 946 MB after the large pages, and 677 MB at page 1,003; the P8 checkpoint target is below 1 GiB at these sampled points. This is not a continuous peak measurement or a replacement for the P11/P13 endurance tests.
