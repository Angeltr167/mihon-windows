# P10 integrations status (in progress)

The Desktop source-link router accepts HTTPS manga/chapter URLs handled by an installed `ResolvableSource`, plus `mihon://extension-store?url=...` and `tachiyomi://add-repo?url=...` repository links. It rejects arbitrary schemes and unsafe repository URLs. Links can be pasted into Search or passed as a single launch argument. The repository URL is shown for review before discovery or installation. Registering the custom protocol with Windows and forwarding links to an already running instance remain P10/P12 work.

The Android tracker manager exposes 11 providers (MyAnimeList, AniList, Kitsu, Shikimori, Bangumi, Komga, MangaUpdates, Kavita, Suwayomi, Hikka, MangaBaka). Their Android implementations live in `:app`; reusing `:app` directly would violate the Desktop Android-dependency boundary.

Komga is the first Desktop tracker adapter. It preserves Android's tracker ID 6 and existing `manga_sync` schema. A Komga series can be linked from manga details when its source supplies a `/api/v1/series/<id>` URL. Linking validates the existing authenticated source HTTP session against Komga's [Mihon progress endpoint](https://komga.org/docs/openapi/get-mihon-read-progress-by-series-id/). Finishing a chapter queues progress through the same session and reads it back; the details screen also offers manual retry. The queue persists in `config/tracker-sync.properties`, retries on launch, and shows errors without blocking local reading. This follows Komga's [API authentication](https://komga.org/docs/openapi/komga-api/) and [progress-update](https://komga.org/docs/openapi/update-mihon-read-progress-by-series-id/) contracts. Local authenticated HTTP and restart fixtures verify database binding and progress sync. A real Komga server/extension account has not yet been tested, so this is not the P10 exit gate.

The other ten providers remain incomplete. Their login/token handling, resources, preferences, and callback Activities are Android-bound; provider-specific Desktop authentication, secure credential storage, UI binding, and reader sync remain P10 work. They are implementation gaps, not claimed provider-specific blockers. P10 cannot be marked complete yet.

Windows-user DPAPI credential storage is available for future Desktop tracker tokens. It is not yet wired to any provider; storing a token alone does not satisfy the OAuth or tracker-sync gate.

| Android provider | Existing login path | Desktop status |
| --- | --- | --- |
| Komga | Source-authenticated session | Fixture-backed adapter and retry queue; live server pending |
| Kavita, Suwayomi | Source-authenticated session | Not ported |
| Kitsu, MangaUpdates | Credential exchange | Not ported |
| AniList, Bangumi, Hikka, MangaBaka, MyAnimeList, Shikimori | OAuth/custom callback | Not ported; Windows callback registration and provider adapters needed |

No provider in the last three rows is being labeled a provider-specific blocker merely because it has not yet been implemented. The callback approach must also be tested against actual provider redirect registrations before it is declared supported.

The Desktop platform layer provides browser opening, clipboard, file opening, and share-as-copy behavior. Manga details now expose browser/copy-link actions for HTTP sources. Actual Windows protocol registration and file associations are packaging work; none should be claimed as tested from an external browser yet.
