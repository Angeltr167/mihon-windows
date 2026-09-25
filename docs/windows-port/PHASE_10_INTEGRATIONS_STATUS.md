# P10 integrations status (in progress)

The Desktop source-link router accepts HTTPS manga/chapter URLs handled by an installed `ResolvableSource`, plus `mihon://extension-store?url=...` and `tachiyomi://add-repo?url=...` repository links. It rejects arbitrary schemes and unsafe repository URLs. Links can be pasted into Search or passed as a single launch argument. The repository URL is shown for review before discovery or installation. Registering the custom protocol with Windows and forwarding links to an already running instance remain P10/P12 work.

The Android tracker manager exposes 11 providers (MyAnimeList, AniList, Kitsu, Shikimori, Bangumi, Komga, MangaUpdates, Kavita, Suwayomi, Hikka, MangaBaka). Their Android implementations live in `:app`; reusing `:app` directly would violate the Desktop Android-dependency boundary.

Komga is the first Desktop tracker adapter. It preserves Android's tracker ID 6 and existing `manga_sync` schema. A Komga series can be linked from manga details when its source supplies a `/api/v1/series/<id>` URL. Linking validates the existing authenticated source HTTP session against Komga's [Mihon progress endpoint](https://komga.org/docs/openapi/get-mihon-read-progress-by-series-id/). Finishing a chapter queues progress through the same session and reads it back; the details screen also offers manual retry. The queue persists in `config/tracker-sync.properties`, retries on launch, and shows errors without blocking local reading. This follows Komga's [API authentication](https://komga.org/docs/openapi/komga-api/) and [progress-update](https://komga.org/docs/openapi/update-mihon-read-progress-by-series-id/) contracts. Local authenticated HTTP and restart fixtures verify database binding and progress sync. A real Komga server/extension account has not yet been tested, so this is not the P10 exit gate.

Three providers have not been ported. Their login/token handling, resources, preferences, and callback Activities are Android-bound; provider-specific Desktop authentication, UI binding, and reader sync remain P10 work. They are implementation gaps, not claimed provider-specific blockers. P10 cannot be marked complete yet.

Windows-user DPAPI credential storage now protects the AniList token. The Desktop Settings screen opens AniList's existing Mihon OAuth client in the system browser and accepts the `mihon://anilist-auth#access_token=...` redirect URL by explicit paste; the token is checked against AniList's Viewer query before storage. A linked AniList manga uses Android tracker ID 2, the existing `manga_sync` table, and the same persistent post-reading retry queue as Komga. A local GraphQL fixture verifies login, protected storage, binding, and progress mutations. This is not a live AniList authentication test, and the redirect is not yet handed back automatically from an external browser.

Suwayomi uses the installed Tachidesk source HTTP client and headers, preserving Android tracker ID 9. A manga from that source can be linked from details, and finishing a chapter marks matching unread chapters and invokes Suwayomi's progress mutation. The Desktop session now retains loaded source instances until extension installation explicitly refreshes them, so a tracker sync does not close the reader's extension loader. A local authenticated GraphQL fixture verifies binding, chapter filtering, and post-reading sync; no real Suwayomi server/extension has been exercised.

Kavita uses the existing Android plugin-authentication and Tachiyomi progress endpoints with tracker ID 8. Because `ConfigurableSource` preferences remain Android-only, Desktop asks for a Kavita API key when linking a series, verifies it against the server, and protects it with Windows-user DPAPI. The key is never written to `manga_sync`; post-reading sync reauthenticates using the protected key. A local server fixture verifies auth, binding, reauthentication, and progress. No real Kavita server or extension has been tested, and plain-HTTP server configurations would expose the key in transit.

MangaUpdates now supports its existing credential exchange and REST list/progress contract with tracker ID 7. Desktop checks the returned session against the profile endpoint and stores only the token with DPAPI; a local fixture covers adding a series to the wish list and moving it to reading after a completed chapter. No real MangaUpdates account has been tested.

Kitsu now supports its Android password-grant and GraphQL library-entry path with tracker ID 3. Desktop validates the account, protects access/refresh tokens with DPAPI, refreshes expiring sessions, binds a Kitsu manga ID, and updates progress after reading. A local fixture covers login, entry creation, refresh, and status transitions. This remains unverified against a live Kitsu account.

MyAnimeList now has a browser PKCE sign-in path with tracker ID 1. The verifier and callback state are generated per attempt and held in DPAPI for ten minutes; Desktop accepts the custom redirect by explicit paste, exchanges the code, checks the account, and protects refreshable tokens. A local fixture covers callback-state rejection, token refresh, manga binding, and progress/status updates. Automatic Windows callback delivery and a live account remain untested.

Shikimori now uses the existing Mihon OAuth client and user-rate API with tracker ID 4. Desktop accepts its custom redirect by explicit paste, verifies the current user before protecting tokens with DPAPI, binds or creates a manga user rate, refreshes tokens, and syncs progress after reading. A local fixture verifies login, binding, refresh, and status transitions. Automatic Windows callback delivery and a live account remain untested.

| Android provider | Existing login path | Desktop status |
| --- | --- | --- |
| Komga | Source-authenticated session | Fixture-backed adapter and retry queue; live server pending |
| Suwayomi | Source-authenticated session | Fixture-backed source session, link and progress sync; live server/extension pending |
| Kavita | Source API-key authentication | Fixture-backed API key, link and progress sync; live server/extension pending |
| MangaUpdates | Credential exchange | Fixture-backed login, DPAPI session, link and progress sync; live account pending |
| Kitsu | Credential exchange and refresh | Fixture-backed login, DPAPI session, link and progress sync; live account pending |
| MyAnimeList | PKCE OAuth/custom callback | Fixture-backed browser + paste callback, DPAPI tokens, link and progress sync; live account and automatic callback pending |
| Shikimori | OAuth/custom callback | Fixture-backed browser + paste callback, DPAPI tokens, link and progress sync; live account and automatic callback pending |
| AniList | OAuth/custom callback | Fixture-backed browser + paste callback, DPAPI token, manga binding and progress sync; live account and automatic callback pending |
| Bangumi, Hikka, MangaBaka | Provider login/callback | Not ported; Windows callback registration and provider adapters needed |

No unported provider is being labeled a provider-specific blocker merely because it has not yet been implemented. The callback approach must also be tested against actual provider redirect registrations before it is declared supported.

The Desktop platform layer provides browser opening, clipboard, file opening, and share-as-copy behavior. Manga details now expose browser/copy-link actions for HTTP sources. Actual Windows protocol registration and file associations are packaging work; none should be claimed as tested from an external browser yet.
