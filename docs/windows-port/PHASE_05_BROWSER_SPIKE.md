# Phase 5 Desktop Browser Challenge Spike

## Decision

Use Playwright with the installed Edge channel when available, falling back to Chrome and then Playwright Chromium. This keeps the challenge browser separate from the OkHttp client while allowing its cookies to be copied back into the persistent Desktop cookie store.

## Evidence

`BrowserChallengeIntegrationTest` starts a representative `HttpSource` fixture which returns a JavaScript challenge until `cf_clearance` is present. The selected browser executes the script, exposes the resulting cookie to `PlaywrightChallengeSolver`, and the retried OkHttp request succeeds. The test also verifies that the clearance cookie is persisted in `DesktopCookieStore`.

The same `:core:network-desktop:test` gate covers normal HTTP redirects and compression, HTTPS, custom DNS, proxy routing, rate limiting, cookie persistence, and the browser cookie handoff.

## Trade-offs

The Edge channel is preferred because it is normally present on supported Windows installations and avoids bundling a second browser. Playwright Chromium is retained as a fallback for machines without an accessible Edge or Chrome channel; it adds a browser download/runtime footprint, but provides a maintained Chromium/JavaScript implementation and deterministic fixture coverage.
