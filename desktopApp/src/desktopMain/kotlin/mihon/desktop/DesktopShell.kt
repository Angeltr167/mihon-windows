package mihon.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import dev.icerock.moko.resources.compose.stringResource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.core.extension.desktop.DesktopRepositoryEntry
import mihon.platform.api.OpenFileRequest
import mihon.platform.desktop.DesktopPlatformGraph
import mihon.platform.desktop.WindowsProtocolRegistrar
import tachiyomi.data.Mangas
import tachiyomi.i18n.MR
import tachiyomi.source.local.desktop.DesktopLocalSource
import java.net.URI
import java.nio.file.Path

private enum class Screen(val title: StringResource) {
    LIBRARY(MR.strings.label_library),
    UPDATES(MR.strings.label_recent_updates),
    HISTORY(MR.strings.label_recent_manga),
    SOURCES(MR.strings.label_sources),
    SEARCH(MR.strings.action_search),
    EXTENSIONS(MR.strings.label_extensions),
    CATEGORIES(MR.strings.categories),
    SETTINGS(MR.strings.label_settings),
    DOWNLOADS(MR.strings.label_download_queue),
}

private const val KEIYOUSHI_STORE_URL = "https://github.com/keiyoushi/extensions/raw/repo/index.pb"

@Composable
fun DesktopShell(
    graph: DesktopPlatformGraph,
    initialLink: String? = null,
    incomingLinks: StateFlow<String?>,
    onToggleFullscreen: () -> Unit = {},
) {
    val session = remember { DesktopSession(graph) }
    DisposableEffect(session) { onDispose(session::close) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val protocolRegistrar = remember { WindowsProtocolRegistrar() }
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
    var screen by remember { mutableStateOf(Screen.LIBRARY) }
    var sources by remember { mutableStateOf(session.sources()) }
    var library by remember { mutableStateOf(session.library.library()) }
    var source by remember { mutableStateOf<Source?>(null) }
    var browseItems by remember { mutableStateOf(emptyList<SManga>()) }
    var selectedManga by remember { mutableStateOf<SManga?>(null) }
    var chapters by remember { mutableStateOf(emptyList<SChapter>()) }
    var readerTarget by remember { mutableStateOf<ReaderTarget?>(null) }
    var query by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var packagePath by remember { mutableStateOf("") }
    var indexUrl by remember { mutableStateOf("") }
    var availableExtensions by remember { mutableStateOf(emptyList<DesktopRepositoryEntry>()) }
    var suwayomiExtensions by remember { mutableStateOf(emptyList<SuwayomiExtension>()) }
    var suwayomiSearch by remember { mutableStateOf("") }
    var suwayomiStatus by remember { mutableStateOf("Starting local extension engine…") }
    var fingerprint by remember { mutableStateOf("") }
    var categoryName by remember { mutableStateOf("") }
    var backupPath by remember { mutableStateOf("") }
    var aniListCallback by remember { mutableStateOf("") }
    var aniListMediaId by remember { mutableStateOf("") }
    var kavitaApiKey by remember { mutableStateOf("") }
    var mangaUpdatesUsername by remember { mutableStateOf("") }
    var mangaUpdatesPassword by remember { mutableStateOf("") }
    var mangaUpdatesSeriesId by remember { mutableStateOf("") }
    var mangaUpdatesLoggedIn by remember { mutableStateOf(session.mangaUpdatesTracker.isLoggedIn) }
    var kitsuUsername by remember { mutableStateOf("") }
    var kitsuPassword by remember { mutableStateOf("") }
    var kitsuMangaId by remember { mutableStateOf("") }
    var kitsuLoggedIn by remember { mutableStateOf(session.kitsuTracker.isLoggedIn) }
    var malCallback by remember { mutableStateOf("") }
    var malMangaId by remember { mutableStateOf("") }
    var malLoggedIn by remember { mutableStateOf(session.myAnimeListTracker.isLoggedIn) }
    var shikimoriCallback by remember { mutableStateOf("") }
    var shikimoriMangaId by remember { mutableStateOf("") }
    var shikimoriLoggedIn by remember { mutableStateOf(session.shikimoriTracker.isLoggedIn) }
    var hikkaCallback by remember { mutableStateOf("") }
    var hikkaSlug by remember { mutableStateOf("") }
    var hikkaLoggedIn by remember { mutableStateOf(session.hikkaTracker.isLoggedIn) }
    var bangumiCallback by remember { mutableStateOf("") }
    var bangumiMangaId by remember { mutableStateOf("") }
    var bangumiLoggedIn by remember { mutableStateOf(session.bangumiTracker.isLoggedIn) }
    var mangaBakaCallback by remember { mutableStateOf("") }
    var mangaBakaSeriesId by remember { mutableStateOf("") }
    var mangaBakaLoggedIn by remember { mutableStateOf(session.mangaBakaTracker.isLoggedIn) }
    var aniListLoggedIn by remember { mutableStateOf(session.aniListTracker.isLoggedIn) }
    var message by remember { mutableStateOf("") }
    var mihonProtocolRegistered by remember { mutableStateOf(protocolRegistrar.isRegistered()) }
    val downloads by session.downloads.queue.collectAsState()
    val trackerSyncQueue by session.trackerSync.pending.collectAsState()
    val incomingLink by incomingLinks.collectAsState()
    val libraryUpdate by session.libraryUpdates.state.collectAsState()
    var updateInterval by remember { mutableLongStateOf(session.libraryUpdates.intervalHours()) }

    fun refreshLibrary() {
        library = session.library.library()
    }
    fun browse(selectedSource: Source, search: String = "") {
        source = selectedSource
        selectedManga = null
        chapters = emptyList()
        screen = if (screen == Screen.SEARCH) Screen.SEARCH else Screen.SOURCES
        scope.launch {
            message = "Loading ${selectedSource.name}…"
            runCatching {
                withContext(Dispatchers.IO) {
                    if (search.isBlank()) {
                        selectedSource.getPopularManga(1).mangas
                    } else {
                        selectedSource.getSearchManga(1, search, FilterList()).mangas
                    }
                }
            }.onSuccess {
                browseItems = it
                message = ""
            }
                .onFailure { message = it.message ?: "Source failed" }
        }
    }

    fun openLink(raw: String) {
        scope.launch {
            message = "Opening link…"
            runCatching {
                withContext(Dispatchers.IO) { DesktopLinkRouter().resolve(raw, sources) }
            }.onSuccess { target ->
                when (target) {
                    is DesktopLinkTarget.ExtensionRepository -> {
                        indexUrl = target.url.toString()
                        screen = Screen.EXTENSIONS
                        message = "Repository link ready. Review it before loading."
                    }
                    is DesktopLinkTarget.Manga -> {
                        source = target.source
                        selectedManga = target.manga
                        chapters = emptyList()
                        screen = Screen.SOURCES
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    target.source.getMangaUpdate(target.manga, emptyList(), true, true)
                                }
                            }.onSuccess { update ->
                                selectedManga = update.manga
                                chapters = update.chapters
                                val chapterUrl = target.chapter?.url
                                if (chapterUrl != null && update.chapters.any { it.url == chapterUrl }) {
                                    readerTarget =
                                        ReaderTarget(target.source, update.manga, update.chapters, chapterUrl)
                                }
                                message = ""
                            }.onFailure { message = it.message ?: "Could not load chapters" }
                        }
                    }
                    null -> message = "No installed source recognizes this link"
                }
            }.onFailure { message = it.message ?: "Could not open link" }
        }
    }

    fun handleIncomingLink(raw: String) {
        val host = runCatching { URI(raw).takeIf { it.scheme == "mihon" }?.host }.getOrNull()
        val tracker = when (host) {
            "anilist-auth" -> "AniList"
            "myanimelist-auth" -> "MyAnimeList"
            "shikimori-auth" -> "Shikimori"
            "bangumi-auth" -> "Bangumi"
            "hikka-auth" -> "Hikka"
            "mangabaka-auth" -> "MangaBaka"
            else -> null
        }
        if (tracker == null) {
            openLink(raw)
            return
        }
        scope.launch {
            runCatching {
                when (tracker) {
                    "AniList" -> session.aniListTracker.loginFromCallback(raw)
                    "MyAnimeList" -> session.myAnimeListTracker.loginFromCallback(raw)
                    "Shikimori" -> session.shikimoriTracker.loginFromCallback(raw)
                    "Bangumi" -> session.bangumiTracker.loginFromCallback(raw)
                    "Hikka" -> session.hikkaTracker.loginFromCallback(raw)
                    "MangaBaka" -> session.mangaBakaTracker.loginFromCallback(raw)
                    else -> error("Unsupported tracker callback")
                }
            }.onSuccess { name ->
                when (tracker) {
                    "AniList" -> aniListLoggedIn = true
                    "MyAnimeList" -> malLoggedIn = true
                    "Shikimori" -> shikimoriLoggedIn = true
                    "Bangumi" -> bangumiLoggedIn = true
                    "Hikka" -> hikkaLoggedIn = true
                    "MangaBaka" -> mangaBakaLoggedIn = true
                }
                message = "Signed in to $tracker as $name"
            }.onFailure { message = it.message ?: "$tracker sign-in failed" }
        }
    }

    LaunchedEffect(session) {
        runCatching {
            withContext(Dispatchers.IO) {
                session.suwayomiEngine.start()
                val extensions = session.suwayomiEngine.client().extensions()
                extensions to session.refreshSources()
            }
        }.onSuccess { (extensions, installedSources) ->
            suwayomiExtensions = extensions
            sources = installedSources
            suwayomiStatus = "Local extension engine ready"
        }.onFailure { suwayomiStatus = it.message ?: "Local extension engine failed" }
    }

    LaunchedEffect(initialLink) {
        if (!initialLink.isNullOrBlank()) handleIncomingLink(initialLink)
    }

    LaunchedEffect(incomingLink) {
        val link = incomingLink
        if (!link.isNullOrBlank() && link != initialLink) handleIncomingLink(link)
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed) return@onPreviewKeyEvent false
                    val index = when (event.key) {
                        Key.One -> 0
                        Key.Two -> 1
                        Key.Three -> 2
                        Key.Four -> 3
                        Key.Five -> 4
                        Key.Six -> 5
                        Key.Seven -> 6
                        Key.Eight -> 7
                        Key.Nine -> 8
                        else -> return@onPreviewKeyEvent false
                    }
                    screen = Screen.entries[index]
                    readerTarget = null
                    selectedManga = null
                    chapters = emptyList()
                    message = ""
                    if (screen == Screen.LIBRARY) refreshLibrary()
                    true
                }.focusRequester(focusRequester).focusable(),
            ) {
                if (readerTarget == null) {
                    Column(Modifier.width(190.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Mihon", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(8.dp))
                        Screen.entries.forEachIndexed { index, item ->
                            TextButton(
                                onClick = {
                                    screen = item
                                    readerTarget = null
                                    selectedManga = null
                                    chapters = emptyList()
                                    message = ""
                                    if (item == Screen.LIBRARY) refreshLibrary()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("${index + 1}  ${stringResource(item.title)}") }
                        }
                    }
                }
                Column(
                    Modifier.fillMaxSize()
                        .padding(if (readerTarget == null) 20.dp else 8.dp)
                        .then(
                            if (screen ==
                                Screen.SETTINGS
                            ) {
                                Modifier.verticalScroll(rememberScrollState())
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    if (readerTarget == null) {
                        Text(
                            selectedManga?.title ?: stringResource(screen.title),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    }
                    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    when {
                        readerTarget != null -> DesktopReader(
                            session = session,
                            graph = graph,
                            target = requireNotNull(readerTarget),
                            onClose = { readerTarget = null },
                            onToggleFullscreen = onToggleFullscreen,
                        )
                        selectedManga != null -> {
                            val item = requireNotNull(selectedManga)
                            val selectedSource = source
                            val stored = selectedSource?.let { session.library.find(it.id, item.url) }
                            val komgaTrack = stored?.let {
                                session.library.track(it._id, DesktopKomgaTracker.TRACKER_ID)
                            }
                            val aniListTrack = stored?.let {
                                session.library.track(it._id, DesktopAniListTracker.TRACKER_ID)
                            }
                            val kavitaTrack = stored?.let {
                                session.library.track(it._id, DesktopKavitaTracker.TRACKER_ID)
                            }
                            val mangaUpdatesTrack = stored?.let {
                                session.library.track(it._id, DesktopMangaUpdatesTracker.TRACKER_ID)
                            }
                            val kitsuTrack = stored?.let {
                                session.library.track(it._id, DesktopKitsuTracker.TRACKER_ID)
                            }
                            val malTrack = stored?.let {
                                session.library.track(it._id, DesktopMyAnimeListTracker.TRACKER_ID)
                            }
                            val shikimoriTrack = stored?.let {
                                session.library.track(it._id, DesktopShikimoriTracker.TRACKER_ID)
                            }
                            val hikkaTrack = stored?.let {
                                session.library.track(it._id, DesktopHikkaTracker.TRACKER_ID)
                            }
                            val bangumiTrack = stored?.let {
                                session.library.track(it._id, DesktopBangumiTracker.TRACKER_ID)
                            }
                            val mangaBakaTrack = stored?.let {
                                session.library.track(it._id, DesktopMangaBakaTracker.TRACKER_ID)
                            }
                            val suwayomiTrack = stored?.let {
                                session.library.track(it._id, DesktopSuwayomiTracker.TRACKER_ID)
                            }
                            DesktopCover(item.thumbnail_url, selectedSource)
                            Text(item.description.orEmpty())
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    if (stored == null && selectedSource != null) {
                                        session.library.addToLibrary(selectedSource.id, item)
                                    } else if (stored != null) {
                                        session.library.setFavorite(stored._id, !stored.favorite)
                                    }
                                    refreshLibrary()
                                    message = "Library updated"
                                }) { Text(if (stored?.favorite == true) "Remove from library" else "Add to library") }
                                TextButton(onClick = { selectedManga = null }) { Text("Back") }
                                if (selectedSource is HttpSource) {
                                    TextButton(onClick = {
                                        runCatching {
                                            val url = selectedSource.getMangaUrl(item)
                                            val uri = URI(url)
                                            require(uri.scheme in setOf("http", "https") && uri.userInfo == null)
                                            check(graph.browserService.open(url))
                                        }.onFailure { message = it.message ?: "Could not open browser" }
                                    }) { Text("Open in browser") }
                                    TextButton(onClick = {
                                        runCatching {
                                            val url = selectedSource.getMangaUrl(item)
                                            val uri = URI(url)
                                            require(uri.scheme in setOf("http", "https") && uri.userInfo == null)
                                            check(graph.externalOpenService.shareText(url))
                                        }.onSuccess { message = "Link copied" }
                                            .onFailure { message = it.message ?: "Could not copy link" }
                                    }) { Text("Copy link") }
                                }
                                if (stored != null && item.url.contains("/api/v1/series/")) {
                                    TextButton(onClick = {
                                        scope.launch {
                                            runCatching {
                                                if (komgaTrack == null) {
                                                    session.komgaTracker.bind(stored._id, item.title, item.url)
                                                } else {
                                                    val lastRead = withContext(Dispatchers.IO) {
                                                        chapters.filter {
                                                            session.library.chapter(stored._id, it.url)?.read == true
                                                        }.maxOfOrNull { it.chapter_number.toDouble() }
                                                    }
                                                    if (lastRead != null && lastRead.isFinite() && lastRead > 0) {
                                                        session.trackerSync.enqueue(stored._id, lastRead)
                                                    } else {
                                                        session.trackerSync.retry(stored._id)
                                                    }
                                                }
                                            }.onSuccess {
                                                message =
                                                    if (komgaTrack ==
                                                        null
                                                    ) {
                                                        "Komga tracking linked"
                                                    } else {
                                                        "Komga sync queued"
                                                    }
                                            }.onFailure { message = it.message ?: "Komga tracking failed" }
                                        }
                                    }) { Text(if (komgaTrack == null) "Link Komga" else "Sync Komga") }
                                    trackerSyncQueue.firstOrNull { it.mangaId == stored._id }?.error?.let {
                                        Text(it, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            if (stored != null && item.url.contains("/api/Series/")) {
                                if (kavitaTrack == null) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            kavitaApiKey,
                                            { kavitaApiKey = it },
                                            label = { Text("Kavita API key") },
                                            visualTransformation = PasswordVisualTransformation(),
                                        )
                                        TextButton(onClick = {
                                            val key = kavitaApiKey
                                            kavitaApiKey = ""
                                            scope.launch {
                                                runCatching {
                                                    session.kavitaTracker.bind(stored._id, item.url, key)
                                                }.onSuccess { message = "Kavita tracking linked" }
                                                    .onFailure { message = it.message ?: "Kavita link failed" }
                                            }
                                        }) { Text("Link Kavita") }
                                    }
                                } else {
                                    TextButton(onClick = {
                                        scope.launch {
                                            val lastRead = withContext(Dispatchers.IO) {
                                                chapters.filter {
                                                    session.library.chapter(stored._id, it.url)?.read == true
                                                }.maxOfOrNull { it.chapter_number.toDouble() }
                                            }
                                            if (lastRead != null && lastRead.isFinite() && lastRead > 0) {
                                                session.trackerSync.enqueue(stored._id, lastRead)
                                            } else {
                                                session.trackerSync.retry(stored._id)
                                            }
                                            message = "Kavita sync queued"
                                        }
                                    }) { Text("Sync Kavita") }
                                }
                            }
                            if (stored != null && aniListLoggedIn) {
                                if (aniListTrack == null) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            aniListMediaId,
                                            { aniListMediaId = it },
                                            label = { Text("AniList manga ID") },
                                        )
                                        TextButton(onClick = {
                                            scope.launch {
                                                runCatching {
                                                    val id = aniListMediaId.trim().toIntOrNull()
                                                        ?: error("Enter a numeric AniList manga ID")
                                                    session.aniListTracker.bind(stored._id, id)
                                                }.onSuccess {
                                                    aniListMediaId = ""
                                                    message = "AniList tracking linked"
                                                }.onFailure { message = it.message ?: "AniList link failed" }
                                            }
                                        }) { Text("Link AniList") }
                                    }
                                } else {
                                    TextButton(onClick = {
                                        val lastRead = chapters.filter {
                                            session.library.chapter(stored._id, it.url)?.read == true
                                        }.maxOfOrNull { it.chapter_number.toDouble() }
                                        if (lastRead != null && lastRead.isFinite() && lastRead > 0) {
                                            session.trackerSync.enqueue(stored._id, lastRead)
                                            message = "AniList sync queued"
                                        } else {
                                            session.trackerSync.retry(stored._id)
                                            message = "AniList sync retry queued"
                                        }
                                    }) { Text("Sync AniList") }
                                }
                            }
                            if (stored != null && mangaUpdatesLoggedIn) {
                                if (mangaUpdatesTrack == null) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            mangaUpdatesSeriesId,
                                            { mangaUpdatesSeriesId = it },
                                            label = { Text("MangaUpdates series ID") },
                                        )
                                        TextButton(onClick = {
                                            scope.launch {
                                                runCatching {
                                                    val id = mangaUpdatesSeriesId.trim().toLongOrNull()
                                                        ?: error("Enter a numeric MangaUpdates series ID")
                                                    session.mangaUpdatesTracker.bind(stored._id, id)
                                                }.onSuccess {
                                                    mangaUpdatesSeriesId = ""
                                                    message = "MangaUpdates tracking linked"
                                                }.onFailure { message = it.message ?: "MangaUpdates link failed" }
                                            }
                                        }) { Text("Link MangaUpdates") }
                                    }
                                } else {
                                    TextButton(onClick = {
                                        scope.launch {
                                            val lastRead = withContext(Dispatchers.IO) {
                                                chapters.filter {
                                                    session.library.chapter(stored._id, it.url)?.read == true
                                                }.maxOfOrNull { it.chapter_number.toDouble() }
                                            }
                                            if (lastRead != null && lastRead.isFinite() && lastRead > 0) {
                                                session.trackerSync.enqueue(stored._id, lastRead)
                                            } else {
                                                session.trackerSync.retry(stored._id)
                                            }
                                            message = "MangaUpdates sync queued"
                                        }
                                    }) { Text("Sync MangaUpdates") }
                                }
                            }
                            if (stored != null && kitsuLoggedIn) {
                                if (kitsuTrack == null) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            kitsuMangaId,
                                            { kitsuMangaId = it },
                                            label = { Text("Kitsu manga ID") },
                                        )
                                        TextButton(onClick = {
                                            scope.launch {
                                                runCatching {
                                                    val id = kitsuMangaId.trim().toLongOrNull()
                                                        ?: error("Enter a numeric Kitsu manga ID")
                                                    session.kitsuTracker.bind(stored._id, id)
                                                }.onSuccess {
                                                    kitsuMangaId = ""
                                                    message = "Kitsu tracking linked"
                                                }.onFailure { message = it.message ?: "Kitsu link failed" }
                                            }
                                        }) { Text("Link Kitsu") }
                                    }
                                } else {
                                    TextButton(onClick = {
                                        scope.launch {
                                            val lastRead = withContext(Dispatchers.IO) {
                                                chapters.filter {
                                                    session.library.chapter(stored._id, it.url)?.read == true
                                                }.maxOfOrNull { it.chapter_number.toDouble() }
                                            }
                                            if (lastRead != null && lastRead.isFinite() && lastRead > 0) {
                                                session.trackerSync.enqueue(stored._id, lastRead)
                                            } else {
                                                session.trackerSync.retry(stored._id)
                                            }
                                            message = "Kitsu sync queued"
                                        }
                                    }) { Text("Sync Kitsu") }
                                }
                            }
                            if (stored != null && malLoggedIn) {
                                if (malTrack == null) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            malMangaId,
                                            { malMangaId = it },
                                            label = { Text("MyAnimeList manga ID") },
                                        )
                                        TextButton(onClick = {
                                            scope.launch {
                                                runCatching {
                                                    val id = malMangaId.trim().toLongOrNull()
                                                        ?: error("Enter a numeric MyAnimeList manga ID")
                                                    session.myAnimeListTracker.bind(stored._id, id)
                                                }.onSuccess {
                                                    malMangaId = ""
                                                    message = "MyAnimeList tracking linked"
                                                }.onFailure { message = it.message ?: "MyAnimeList link failed" }
                                            }
                                        }) { Text("Link MyAnimeList") }
                                    }
                                } else {
                                    TextButton(onClick = {
                                        scope.launch {
                                            val lastRead = withContext(Dispatchers.IO) {
                                                chapters.filter {
                                                    session.library.chapter(stored._id, it.url)?.read == true
                                                }.maxOfOrNull { it.chapter_number.toDouble() }
                                            }
                                            if (lastRead != null && lastRead.isFinite() && lastRead > 0) {
                                                session.trackerSync.enqueue(stored._id, lastRead)
                                            } else {
                                                session.trackerSync.retry(stored._id)
                                            }
                                            message = "MyAnimeList sync queued"
                                        }
                                    }) { Text("Sync MyAnimeList") }
                                }
                            }
                            if (stored != null && shikimoriLoggedIn) {
                                if (shikimoriTrack == null) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            shikimoriMangaId,
                                            { shikimoriMangaId = it },
                                            label = { Text("Shikimori manga ID") },
                                        )
                                        TextButton(onClick = {
                                            scope.launch {
                                                runCatching {
                                                    val id = shikimoriMangaId.trim().toLongOrNull()
                                                        ?: error("Enter a numeric Shikimori manga ID")
                                                    session.shikimoriTracker.bind(stored._id, id)
                                                }.onSuccess {
                                                    shikimoriMangaId = ""
                                                    message = "Shikimori tracking linked"
                                                }.onFailure { message = it.message ?: "Shikimori link failed" }
                                            }
                                        }) { Text("Link Shikimori") }
                                    }
                                } else {
                                    TextButton(onClick = {
                                        scope.launch {
                                            val lastRead = withContext(Dispatchers.IO) {
                                                chapters.filter {
                                                    session.library.chapter(stored._id, it.url)?.read == true
                                                }.maxOfOrNull { it.chapter_number.toDouble() }
                                            }
                                            if (lastRead != null && lastRead.isFinite() && lastRead > 0) {
                                                session.trackerSync.enqueue(stored._id, lastRead)
                                            } else {
                                                session.trackerSync.retry(stored._id)
                                            }
                                            message = "Shikimori sync queued"
                                        }
                                    }) { Text("Sync Shikimori") }
                                }
                            }
                            if (stored != null && hikkaLoggedIn) {
                                if (hikkaTrack == null) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            hikkaSlug,
                                            { hikkaSlug = it },
                                            label = { Text("Hikka manga slug") },
                                        )
                                        TextButton(onClick = {
                                            scope.launch {
                                                runCatching { session.hikkaTracker.bind(stored._id, hikkaSlug.trim()) }
                                                    .onSuccess {
                                                        hikkaSlug = ""
                                                        message = "Hikka tracking linked"
                                                    }.onFailure { message = it.message ?: "Hikka link failed" }
                                            }
                                        }) { Text("Link Hikka") }
                                    }
                                } else {
                                    TextButton(onClick = {
                                        scope.launch {
                                            val lastRead = withContext(Dispatchers.IO) {
                                                chapters.filter {
                                                    session.library.chapter(stored._id, it.url)?.read == true
                                                }.maxOfOrNull { it.chapter_number.toDouble() }
                                            }
                                            if (lastRead != null && lastRead.isFinite() && lastRead > 0) {
                                                session.trackerSync.enqueue(stored._id, lastRead)
                                            } else {
                                                session.trackerSync.retry(stored._id)
                                            }
                                            message = "Hikka sync queued"
                                        }
                                    }) { Text("Sync Hikka") }
                                }
                            }
                            if (stored != null && bangumiLoggedIn) {
                                if (bangumiTrack == null) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            bangumiMangaId,
                                            { bangumiMangaId = it },
                                            label = { Text("Bangumi subject ID") },
                                        )
                                        TextButton(onClick = {
                                            scope.launch {
                                                runCatching {
                                                    val id = bangumiMangaId.trim().toLongOrNull()
                                                        ?: error("Enter a numeric Bangumi subject ID")
                                                    session.bangumiTracker.bind(stored._id, id)
                                                }.onSuccess {
                                                    bangumiMangaId = ""
                                                    message = "Bangumi tracking linked"
                                                }.onFailure { message = it.message ?: "Bangumi link failed" }
                                            }
                                        }) { Text("Link Bangumi") }
                                    }
                                } else {
                                    TextButton(onClick = {
                                        scope.launch {
                                            val lastRead = withContext(Dispatchers.IO) {
                                                chapters.filter {
                                                    session.library.chapter(stored._id, it.url)?.read == true
                                                }.maxOfOrNull { it.chapter_number.toDouble() }
                                            }
                                            if (lastRead != null && lastRead.isFinite() && lastRead > 0) {
                                                session.trackerSync.enqueue(stored._id, lastRead)
                                            } else {
                                                session.trackerSync.retry(stored._id)
                                            }
                                            message = "Bangumi sync queued"
                                        }
                                    }) { Text("Sync Bangumi") }
                                }
                            }
                            if (stored != null && mangaBakaLoggedIn) {
                                if (mangaBakaTrack == null) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            mangaBakaSeriesId,
                                            { mangaBakaSeriesId = it },
                                            label = { Text("MangaBaka series ID") },
                                        )
                                        TextButton(onClick = {
                                            scope.launch {
                                                runCatching {
                                                    val id = mangaBakaSeriesId.trim().toLongOrNull()
                                                        ?: error("Enter a numeric MangaBaka series ID")
                                                    session.mangaBakaTracker.bind(stored._id, id)
                                                }.onSuccess {
                                                    mangaBakaSeriesId = ""
                                                    message = "MangaBaka tracking linked"
                                                }.onFailure { message = it.message ?: "MangaBaka link failed" }
                                            }
                                        }) { Text("Link MangaBaka") }
                                    }
                                } else {
                                    TextButton(onClick = {
                                        scope.launch {
                                            val lastRead = withContext(Dispatchers.IO) {
                                                chapters.filter {
                                                    session.library.chapter(stored._id, it.url)?.read == true
                                                }.maxOfOrNull { it.chapter_number.toDouble() }
                                            }
                                            if (lastRead != null && lastRead.isFinite() && lastRead > 0) {
                                                session.trackerSync.enqueue(stored._id, lastRead)
                                            } else {
                                                session.trackerSync.retry(stored._id)
                                            }
                                            message = "MangaBaka sync queued"
                                        }
                                    }) { Text("Sync MangaBaka") }
                                }
                            }
                            if (stored != null &&
                                selectedSource.javaClass.name == DesktopSuwayomiTracker.SOURCE_CLASS
                            ) {
                                TextButton(onClick = {
                                    scope.launch {
                                        runCatching {
                                            if (suwayomiTrack == null) {
                                                session.suwayomiTracker.bind(stored._id, selectedSource.id, item.url)
                                            } else {
                                                val lastRead = withContext(Dispatchers.IO) {
                                                    chapters.filter {
                                                        session.library.chapter(stored._id, it.url)?.read == true
                                                    }.maxOfOrNull { it.chapter_number.toDouble() }
                                                }
                                                if (lastRead != null && lastRead.isFinite() && lastRead > 0) {
                                                    session.trackerSync.enqueue(stored._id, lastRead)
                                                } else {
                                                    session.trackerSync.retry(stored._id)
                                                }
                                            }
                                        }.onSuccess {
                                            message = if (suwayomiTrack == null) {
                                                "Suwayomi tracking linked"
                                            } else {
                                                "Suwayomi sync queued"
                                            }
                                        }.onFailure { message = it.message ?: "Suwayomi tracking failed" }
                                    }
                                }) { Text(if (suwayomiTrack == null) "Link Suwayomi" else "Sync Suwayomi") }
                            }
                            if (stored?.favorite == true) {
                                val selected = session.library.mangaCategories(stored._id)
                                session.library.categories().forEach { category ->
                                    Row {
                                        Checkbox(
                                            checked = category.id in selected,
                                            onCheckedChange = { checked ->
                                                session.library.setMangaCategories(
                                                    stored._id,
                                                    if (checked) selected + category.id else selected - category.id,
                                                )
                                                message = "Categories updated"
                                            },
                                        )
                                        Text(category.name, modifier = Modifier.padding(top = 12.dp))
                                    }
                                }
                            }
                            Text("Chapters", style = MaterialTheme.typography.titleMedium)
                            LazyColumn {
                                items(chapters) { chapter ->
                                    Row(Modifier.fillMaxWidth()) {
                                        Text(
                                            chapter.name,
                                            modifier = Modifier.weight(1f).clickable {
                                                if (selectedSource != null) {
                                                    readerTarget =
                                                        ReaderTarget(selectedSource, item, chapters, chapter.url)
                                                }
                                            }.padding(8.dp),
                                        )
                                        val queued = downloads.firstOrNull {
                                            it.sourceId == selectedSource?.id && it.mangaUrl == item.url &&
                                                it.chapterUrl == chapter.url
                                        }
                                        if (selectedSource != null && selectedSource !is DesktopLocalSource) {
                                            TextButton(onClick = {
                                                when (queued?.status) {
                                                    DesktopDownloadStatus.PAUSED, DesktopDownloadStatus.FAILED ->
                                                        session.downloads.resume(queued.key)
                                                    null -> session.downloads.enqueue(selectedSource, item, chapter)
                                                    else -> Unit
                                                }
                                            }) {
                                                Text(
                                                    when (queued?.status) {
                                                        DesktopDownloadStatus.COMPLETED -> "Downloaded"
                                                        DesktopDownloadStatus.PAUSED -> "Resume"
                                                        DesktopDownloadStatus.FAILED -> "Retry"
                                                        DesktopDownloadStatus.RUNNING ->
                                                            "${queued.pagesDone}/${queued.pageCount}"
                                                        DesktopDownloadStatus.PENDING -> "Queued"
                                                        null -> "Download"
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        screen == Screen.LIBRARY -> {
                            if (library.isEmpty()) Text("Your library is empty. Browse a source to add manga.")
                            LazyColumn {
                                items(library, key = Mangas::_id) { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            source = sources.firstOrNull { it.id == item.source }
                                            selectedManga = SManga.create().apply {
                                                url = item.url
                                                title = item.title
                                                description = item.description
                                            }
                                            chapters = emptyList()
                                            source?.let { selectedSource ->
                                                val manga = requireNotNull(selectedManga)
                                                scope.launch {
                                                    runCatching {
                                                        withContext(Dispatchers.IO) {
                                                            selectedSource.getMangaUpdate(
                                                                manga,
                                                                emptyList(),
                                                                true,
                                                                true,
                                                            )
                                                        }
                                                    }.onSuccess { update ->
                                                        selectedManga = update.manga
                                                        chapters = update.chapters
                                                    }.onFailure { message = it.message ?: "Details failed" }
                                                }
                                            }
                                        }.padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        DesktopCover(item.thumbnail_url, sources.firstOrNull { it.id == item.source })
                                        Text(item.title)
                                    }
                                }
                            }
                        }
                        screen == Screen.SOURCES || screen == Screen.SEARCH -> {
                            if (screen == Screen.SEARCH) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(link, { link = it }, label = { Text("Manga or Mihon link") })
                                    Button(onClick = { openLink(link) }) { Text("Open link") }
                                    TextButton(onClick = {
                                        graph.clipboardService.readText()?.let { pasted ->
                                            link = pasted
                                            openLink(pasted)
                                        }
                                    }) { Text("Paste link") }
                                }
                            }
                            if (source == null) {
                                Text("Select a source")
                                sources.forEach { available ->
                                    TextButton(onClick = { browse(available) }) { Text(available.name) }
                                }
                            } else {
                                TextButton(onClick = {
                                    source = null
                                    browseItems = emptyList()
                                }) { Text("All sources") }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(query, { query = it }, label = { Text("Search ${source?.name}") })
                                    Button(onClick = { source?.let { browse(it, query) } }) { Text("Search") }
                                }
                                LazyColumn {
                                    items(browseItems, key = SManga::url) { item ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                selectedManga = item
                                                chapters = emptyList()
                                                source?.let { selectedSource ->
                                                    scope.launch {
                                                        runCatching {
                                                            withContext(Dispatchers.IO) {
                                                                selectedSource.getMangaUpdate(
                                                                    item,
                                                                    emptyList(),
                                                                    true,
                                                                    true,
                                                                )
                                                            }
                                                        }.onSuccess { update ->
                                                            selectedManga = update.manga
                                                            chapters = update.chapters
                                                        }
                                                            .onFailure { message = it.message ?: "Details failed" }
                                                    }
                                                }
                                            }.padding(12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            DesktopCover(item.thumbnail_url, source)
                                            Text(item.title)
                                        }
                                    }
                                }
                            }
                        }
                        screen == Screen.EXTENSIONS -> {
                            Column(Modifier.verticalScroll(rememberScrollState())) {
                                Text("Keiyoushi extensions")
                                Text("Only install extensions you trust; they run code in the local engine.")
                                Text(suwayomiStatus)
                                if (session.suwayomiEngine.isRunning) {
                                    Button(onClick = {
                                        scope.launch {
                                            suwayomiStatus = "Refreshing Keiyoushi…"
                                            runCatching {
                                                withContext(Dispatchers.IO) {
                                                    val client = session.suwayomiEngine.client()
                                                    client.addStore(KEIYOUSHI_STORE_URL)
                                                    client.refreshExtensions()
                                                }
                                            }.onSuccess {
                                                suwayomiExtensions = it
                                                suwayomiStatus = "${it.size} extensions available"
                                            }.onFailure {
                                                suwayomiStatus = it.message ?: "Could not refresh extensions"
                                            }
                                        }
                                    }) { Text("Load Keiyoushi catalog") }
                                    OutlinedTextField(
                                        suwayomiSearch,
                                        { suwayomiSearch = it },
                                        label = { Text("Find extension") },
                                    )
                                    val visible = suwayomiExtensions
                                        .filter { !it.obsolete || it.installed }
                                        .filter {
                                            suwayomiSearch.isBlank() ||
                                                it.name.contains(suwayomiSearch, ignoreCase = true) ||
                                                it.pkgName.contains(suwayomiSearch, ignoreCase = true)
                                        }
                                        .sortedWith(
                                            compareByDescending<SuwayomiExtension> {
                                                it.installed
                                            }.thenBy { it.name },
                                        )
                                        .take(50)
                                    Text(
                                        "Showing ${visible.size} of ${suwayomiExtensions.size}; search to narrow the list",
                                    )
                                    visible.forEach { entry ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("${entry.name} · ${entry.versionName} · ${entry.contentWarning}")
                                            TextButton(onClick = {
                                                scope.launch {
                                                    suwayomiStatus = "Updating ${entry.name}…"
                                                    runCatching {
                                                        withContext(Dispatchers.IO) {
                                                            val client = session.suwayomiEngine.client()
                                                            client.setInstalled(
                                                                entry.pkgName,
                                                                when {
                                                                    !entry.installed -> "install"
                                                                    entry.hasUpdate -> "update"
                                                                    else -> "uninstall"
                                                                },
                                                            )
                                                            session.suwayomiEngine.refreshSources()
                                                            client.extensions() to session.refreshSources()
                                                        }
                                                    }.onSuccess { (extensions, installedSources) ->
                                                        suwayomiExtensions = extensions
                                                        sources = installedSources
                                                        suwayomiStatus = "${entry.name} changed"
                                                    }.onFailure {
                                                        suwayomiStatus = it.message ?: "Extension action failed"
                                                    }
                                                }
                                            }) {
                                                Text(
                                                    when {
                                                        !entry.installed -> "Install"
                                                        entry.hasUpdate -> "Update"
                                                        else -> "Remove"
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                                Text("Desktop .mihonext packages · trust authors before installing their code")
                                OutlinedTextField(fingerprint, {
                                    fingerprint = it
                                }, label = { Text("Signing fingerprint") })
                                Button(onClick = {
                                    runCatching { session.extensions.trust(fingerprint.trim()) }
                                        .onSuccess { message = "Fingerprint trusted" }
                                        .onFailure { message = it.message ?: "Trust failed" }
                                }) { Text("Trust fingerprint") }
                                OutlinedTextField(
                                    packagePath,
                                    { packagePath = it },
                                    label = { Text(".mihonext path") },
                                )
                                TextButton(onClick = {
                                    graph.fileDialogService.chooseOpenFile(
                                        OpenFileRequest(
                                            "Install Desktop extension",
                                            extensions = setOf("mihonext"),
                                        ),
                                    )?.let { packagePath = it }
                                }) { Text("Choose package…") }
                                Button(onClick = {
                                    runCatching { session.extensions.install(Path.of(packagePath.trim())) }
                                        .onSuccess { result ->
                                            message = result.toString()
                                            sources = session.refreshSources()
                                        }
                                        .onFailure { message = it.message ?: "Install failed" }
                                }) { Text("Install") }
                                session.extensions.installedExtensions().forEach { installed ->
                                    Text("${installed.id} · ${installed.versionCode}")
                                }
                                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                                OutlinedTextField(
                                    indexUrl,
                                    { indexUrl = it },
                                    label = { Text("HTTPS repository index") },
                                )
                                Button(onClick = {
                                    scope.launch {
                                        runCatching {
                                            withContext(Dispatchers.IO) {
                                                session.extensionRepository.discover(URI(indexUrl.trim()))
                                            }
                                        }.onSuccess {
                                            availableExtensions = it
                                            message = "Repository loaded"
                                        }
                                            .onFailure { message = it.message ?: "Repository failed" }
                                    }
                                }) { Text("Discover") }
                                availableExtensions.forEach { entry ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("${entry.name} · ${entry.versionCode}")
                                        TextButton(onClick = {
                                            fingerprint = entry.fingerprint
                                        }) { Text("Show fingerprint") }
                                        TextButton(onClick = {
                                            scope.launch {
                                                runCatching {
                                                    withContext(Dispatchers.IO) {
                                                        session.extensionRepository.install(
                                                            URI(indexUrl.trim()),
                                                            entry,
                                                            session.extensions,
                                                        )
                                                    }
                                                }.onSuccess { result ->
                                                    message = result.toString()
                                                    sources =
                                                        session.refreshSources()
                                                }
                                                    .onFailure { message = it.message ?: "Update failed" }
                                            }
                                        }) { Text("Install / Update") }
                                    }
                                }
                            }
                        }
                        screen == Screen.CATEGORIES -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(categoryName, {
                                    categoryName = it
                                }, label = { Text("Category name") })
                                Button(onClick = {
                                    runCatching { session.library.addCategory(categoryName) }
                                        .onSuccess {
                                            categoryName = ""
                                            message = "Category added"
                                        }
                                        .onFailure { message = it.message ?: "Category failed" }
                                }) { Text("Add") }
                            }
                            session.library.categories().forEach { category -> Text(category.name) }
                        }
                        screen == Screen.HISTORY -> {
                            session.library.history().forEach { entry -> Text(entry.title) }
                        }
                        screen == Screen.UPDATES -> {
                            TextButton(onClick = session.libraryUpdates::updateNow, enabled = !libraryUpdate.running) {
                                Text("Check library now")
                            }
                            if (libraryUpdate.message.isNotBlank()) Text(libraryUpdate.message)
                            session.library.updates().forEach { entry ->
                                Text("${entry.mangaTitle} · ${entry.chapterName}")
                            }
                        }
                        screen == Screen.SETTINGS -> {
                            Text("Migration")
                            Text(
                                "Import a Mihon Android .tachibk backup. Existing records with matching source and manga URLs are updated.",
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    backupPath,
                                    { backupPath = it },
                                    label = { Text("Android backup (.tachibk)") },
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = {
                                    graph.fileDialogService.chooseOpenFile(
                                        OpenFileRequest("Import Mihon Android backup", extensions = setOf("tachibk")),
                                    )?.let { backupPath = it }
                                }) { Text("Choose…") }
                                Button(onClick = {
                                    val path = backupPath.trim()
                                    scope.launch {
                                        runCatching {
                                            withContext(Dispatchers.IO) {
                                                DesktopBackupImporter.import(Path.of(path), session.library)
                                            }
                                        }.onSuccess { result ->
                                            refreshLibrary()
                                            message =
                                                "Imported ${result.manga} manga, ${result.chapters} chapters, ${result.categories} categories, and ${result.trackerEntries} tracker entries"
                                        }.onFailure { message = it.message ?: "Backup import failed" }
                                    }
                                }, enabled = backupPath.isNotBlank()) { Text("Import backup") }
                            }
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            Text("Language: ${graph.localeService.currentLanguageTag()}")
                            Text("Library: ${graph.appDirectories.localLibrary}")
                            Text("Database: ${graph.appDirectories.database}")
                            if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
                                Text(
                                    "Browser links: ${if (mihonProtocolRegistered) "registered" else "not registered"}",
                                )
                                if (mihonProtocolRegistered) {
                                    TextButton(onClick = {
                                        runCatching { protocolRegistrar.unregisterMihonProtocol() }
                                            .onSuccess {
                                                mihonProtocolRegistered = false
                                                message = "Mihon browser links unregistered"
                                            }.onFailure { message = it.message ?: "Could not unregister Mihon links" }
                                    }) { Text("Unregister Mihon browser links") }
                                } else {
                                    TextButton(onClick = {
                                        runCatching { protocolRegistrar.registerMihonProtocol() }
                                            .onSuccess {
                                                mihonProtocolRegistered = true
                                                message = "Mihon browser links registered for this Windows user"
                                            }.onFailure { message = it.message ?: "Could not register Mihon links" }
                                    }) { Text("Register Mihon browser links") }
                                }
                            }
                            Text("AniList: ${if (aniListLoggedIn) "signed in" else "not signed in"}")
                            if (aniListLoggedIn) {
                                TextButton(onClick = {
                                    session.aniListTracker.logout()
                                    aniListLoggedIn = false
                                    message = "AniList signed out"
                                }) { Text("Sign out of AniList") }
                            } else {
                                TextButton(onClick = {
                                    if (!graph.browserService.open(DesktopAniListTracker.AUTH_URL)) {
                                        message = "Could not open AniList in the browser"
                                    }
                                }) { Text("Sign in to AniList in browser") }
                                Text("If Windows cannot open the Mihon redirect, copy its URL and paste it below.")
                                OutlinedTextField(
                                    aniListCallback,
                                    { aniListCallback = it },
                                    label = { Text("Paste AniList redirect URL") },
                                    visualTransformation = PasswordVisualTransformation(),
                                )
                                TextButton(onClick = {
                                    val callback = aniListCallback
                                    aniListCallback = ""
                                    scope.launch {
                                        runCatching { session.aniListTracker.loginFromCallback(callback) }
                                            .onSuccess { name ->
                                                aniListLoggedIn = true
                                                message = "Signed in to AniList as $name"
                                            }
                                            .onFailure { message = it.message ?: "AniList sign-in failed" }
                                    }
                                }) { Text("Complete AniList sign-in") }
                            }
                            Text("MangaUpdates: ${if (mangaUpdatesLoggedIn) "signed in" else "not signed in"}")
                            if (mangaUpdatesLoggedIn) {
                                TextButton(onClick = {
                                    session.mangaUpdatesTracker.logout()
                                    mangaUpdatesLoggedIn = false
                                    message = "MangaUpdates signed out"
                                }) { Text("Sign out of MangaUpdates") }
                            } else {
                                OutlinedTextField(
                                    mangaUpdatesUsername,
                                    { mangaUpdatesUsername = it },
                                    label = { Text("MangaUpdates username") },
                                )
                                OutlinedTextField(
                                    mangaUpdatesPassword,
                                    { mangaUpdatesPassword = it },
                                    label = { Text("MangaUpdates password") },
                                    visualTransformation = PasswordVisualTransformation(),
                                )
                                TextButton(onClick = {
                                    val username = mangaUpdatesUsername
                                    val password = mangaUpdatesPassword
                                    mangaUpdatesPassword = ""
                                    scope.launch {
                                        runCatching { session.mangaUpdatesTracker.login(username, password) }
                                            .onSuccess { name ->
                                                mangaUpdatesLoggedIn = true
                                                message = "Signed in to MangaUpdates as $name"
                                            }.onFailure { message = it.message ?: "MangaUpdates sign-in failed" }
                                    }
                                }) { Text("Sign in to MangaUpdates") }
                            }
                            Text("Kitsu: ${if (kitsuLoggedIn) "signed in" else "not signed in"}")
                            if (kitsuLoggedIn) {
                                TextButton(onClick = {
                                    session.kitsuTracker.logout()
                                    kitsuLoggedIn = false
                                    message = "Kitsu signed out"
                                }) { Text("Sign out of Kitsu") }
                            } else {
                                OutlinedTextField(
                                    kitsuUsername,
                                    { kitsuUsername = it },
                                    label = { Text("Kitsu username") },
                                )
                                OutlinedTextField(
                                    kitsuPassword,
                                    { kitsuPassword = it },
                                    label = { Text("Kitsu password") },
                                    visualTransformation = PasswordVisualTransformation(),
                                )
                                TextButton(onClick = {
                                    val username = kitsuUsername
                                    val password = kitsuPassword
                                    kitsuPassword = ""
                                    scope.launch {
                                        runCatching { session.kitsuTracker.login(username, password) }
                                            .onSuccess { name ->
                                                kitsuLoggedIn = true
                                                message = "Signed in to Kitsu as $name"
                                            }.onFailure { message = it.message ?: "Kitsu sign-in failed" }
                                    }
                                }) { Text("Sign in to Kitsu") }
                            }
                            Text("MyAnimeList: ${if (malLoggedIn) "signed in" else "not signed in"}")
                            if (malLoggedIn) {
                                TextButton(onClick = {
                                    session.myAnimeListTracker.logout()
                                    malLoggedIn = false
                                    message = "MyAnimeList signed out"
                                }) { Text("Sign out of MyAnimeList") }
                            } else {
                                TextButton(onClick = {
                                    runCatching {
                                        val url = session.myAnimeListTracker.beginLogin()
                                        check(graph.browserService.open(url))
                                    }.onFailure { message = it.message ?: "Could not open MyAnimeList" }
                                }) { Text("Sign in to MyAnimeList in browser") }
                                Text("If Windows cannot open the Mihon redirect, copy its URL and paste it below.")
                                OutlinedTextField(
                                    malCallback,
                                    { malCallback = it },
                                    label = { Text("Paste MyAnimeList redirect URL") },
                                    visualTransformation = PasswordVisualTransformation(),
                                )
                                TextButton(onClick = {
                                    val callback = malCallback
                                    malCallback = ""
                                    scope.launch {
                                        runCatching { session.myAnimeListTracker.loginFromCallback(callback) }
                                            .onSuccess { name ->
                                                malLoggedIn = true
                                                message = "Signed in to MyAnimeList as $name"
                                            }.onFailure { message = it.message ?: "MyAnimeList sign-in failed" }
                                    }
                                }) { Text("Complete MyAnimeList sign-in") }
                            }
                            Text("Shikimori: ${if (shikimoriLoggedIn) "signed in" else "not signed in"}")
                            if (shikimoriLoggedIn) {
                                TextButton(onClick = {
                                    session.shikimoriTracker.logout()
                                    shikimoriLoggedIn = false
                                    message = "Shikimori signed out"
                                }) { Text("Sign out of Shikimori") }
                            } else {
                                TextButton(onClick = {
                                    runCatching {
                                        check(graph.browserService.open(session.shikimoriTracker.beginLogin()))
                                    }.onFailure { message = it.message ?: "Could not open Shikimori" }
                                }) { Text("Sign in to Shikimori in browser") }
                                Text("If Windows cannot open the Mihon redirect, copy its URL and paste it below.")
                                OutlinedTextField(
                                    shikimoriCallback,
                                    { shikimoriCallback = it },
                                    label = { Text("Paste Shikimori redirect URL") },
                                    visualTransformation = PasswordVisualTransformation(),
                                )
                                TextButton(onClick = {
                                    val callback = shikimoriCallback
                                    shikimoriCallback = ""
                                    scope.launch {
                                        runCatching { session.shikimoriTracker.loginFromCallback(callback) }
                                            .onSuccess { name ->
                                                shikimoriLoggedIn = true
                                                message = "Signed in to Shikimori as $name"
                                            }.onFailure { message = it.message ?: "Shikimori sign-in failed" }
                                    }
                                }) { Text("Complete Shikimori sign-in") }
                            }
                            Text("Hikka: ${if (hikkaLoggedIn) "signed in" else "not signed in"}")
                            if (hikkaLoggedIn) {
                                TextButton(onClick = {
                                    session.hikkaTracker.logout()
                                    hikkaLoggedIn = false
                                    message = "Hikka signed out"
                                }) { Text("Sign out of Hikka") }
                            } else {
                                TextButton(onClick = {
                                    runCatching {
                                        check(graph.browserService.open(session.hikkaTracker.beginLogin()))
                                    }.onFailure { message = it.message ?: "Could not open Hikka" }
                                }) { Text("Sign in to Hikka in browser") }
                                Text("If Windows cannot open the Mihon redirect, copy its URL and paste it below.")
                                OutlinedTextField(
                                    hikkaCallback,
                                    { hikkaCallback = it },
                                    label = { Text("Paste Hikka redirect URL") },
                                    visualTransformation = PasswordVisualTransformation(),
                                )
                                TextButton(onClick = {
                                    val callback = hikkaCallback
                                    hikkaCallback = ""
                                    scope.launch {
                                        runCatching { session.hikkaTracker.loginFromCallback(callback) }
                                            .onSuccess { name ->
                                                hikkaLoggedIn = true
                                                message = "Signed in to Hikka as $name"
                                            }.onFailure { message = it.message ?: "Hikka sign-in failed" }
                                    }
                                }) { Text("Complete Hikka sign-in") }
                            }
                            Text("Bangumi: ${if (bangumiLoggedIn) "signed in" else "not signed in"}")
                            if (bangumiLoggedIn) {
                                TextButton(onClick = {
                                    session.bangumiTracker.logout()
                                    bangumiLoggedIn = false
                                    message = "Bangumi signed out"
                                }) { Text("Sign out of Bangumi") }
                            } else {
                                TextButton(onClick = {
                                    runCatching {
                                        check(graph.browserService.open(session.bangumiTracker.beginLogin()))
                                    }.onFailure { message = it.message ?: "Could not open Bangumi" }
                                }) { Text("Sign in to Bangumi in browser") }
                                Text("If Windows cannot open the Mihon redirect, copy its URL and paste it below.")
                                OutlinedTextField(
                                    bangumiCallback,
                                    { bangumiCallback = it },
                                    label = { Text("Paste Bangumi redirect URL") },
                                    visualTransformation = PasswordVisualTransformation(),
                                )
                                TextButton(onClick = {
                                    val callback = bangumiCallback
                                    bangumiCallback = ""
                                    scope.launch {
                                        runCatching { session.bangumiTracker.loginFromCallback(callback) }
                                            .onSuccess { name ->
                                                bangumiLoggedIn = true
                                                message = "Signed in to Bangumi as $name"
                                            }.onFailure { message = it.message ?: "Bangumi sign-in failed" }
                                    }
                                }) { Text("Complete Bangumi sign-in") }
                            }
                            Text("MangaBaka: ${if (mangaBakaLoggedIn) "signed in" else "not signed in"}")
                            if (mangaBakaLoggedIn) {
                                TextButton(onClick = {
                                    session.mangaBakaTracker.logout()
                                    mangaBakaLoggedIn = false
                                    message = "MangaBaka signed out"
                                }) { Text("Sign out of MangaBaka") }
                            } else {
                                TextButton(onClick = {
                                    runCatching {
                                        check(graph.browserService.open(session.mangaBakaTracker.beginLogin()))
                                    }.onFailure { message = it.message ?: "Could not open MangaBaka" }
                                }) { Text("Sign in to MangaBaka in browser") }
                                Text("If Windows cannot open the Mihon redirect, copy its URL and paste it below.")
                                OutlinedTextField(
                                    mangaBakaCallback,
                                    { mangaBakaCallback = it },
                                    label = { Text("Paste MangaBaka redirect URL") },
                                    visualTransformation = PasswordVisualTransformation(),
                                )
                                TextButton(onClick = {
                                    val callback = mangaBakaCallback
                                    mangaBakaCallback = ""
                                    scope.launch {
                                        runCatching { session.mangaBakaTracker.loginFromCallback(callback) }
                                            .onSuccess { name ->
                                                mangaBakaLoggedIn = true
                                                message = "Signed in to MangaBaka as $name"
                                            }.onFailure { message = it.message ?: "MangaBaka sign-in failed" }
                                    }
                                }) { Text("Complete MangaBaka sign-in") }
                            }
                            TextButton(onClick = {
                                val intervals = DesktopLibraryUpdateScheduler.INTERVALS
                                updateInterval = intervals[(intervals.indexOf(updateInterval) + 1) % intervals.size]
                                session.libraryUpdates.setIntervalHours(updateInterval)
                            }) {
                                Text(
                                    if (updateInterval == 0L) {
                                        "Scheduled library updates: off"
                                    } else {
                                        "Scheduled library updates: every $updateInterval hours (while app is open)"
                                    },
                                )
                            }
                            Text(
                                "Downloads wait for an active network. Windows Wi-Fi/cellular distinction is not assumed.",
                            )
                        }
                        screen == Screen.DOWNLOADS -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { session.downloads.pause() }) { Text("Pause all") }
                                TextButton(onClick = { session.downloads.resume() }) { Text("Resume all") }
                            }
                            LazyColumn {
                                items(downloads, key = DesktopDownload::key) { download ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            "${download.mangaTitle} · ${download.chapterName} · " +
                                                "${download.status} ${download.pagesDone}/${download.pageCount}" +
                                                (download.error?.let { " · $it" } ?: ""),
                                            modifier = Modifier.weight(1f).padding(8.dp),
                                        )
                                        when (download.status) {
                                            DesktopDownloadStatus.PENDING, DesktopDownloadStatus.RUNNING ->
                                                TextButton(onClick = { session.downloads.pause(download.key) }) {
                                                    Text("Pause")
                                                }
                                            DesktopDownloadStatus.PAUSED, DesktopDownloadStatus.FAILED ->
                                                TextButton(onClick = { session.downloads.resume(download.key) }) {
                                                    Text("Resume")
                                                }
                                            DesktopDownloadStatus.COMPLETED -> Unit
                                        }
                                        TextButton(onClick = {
                                            session.downloads.cancel(download.key)
                                        }) { Text("Cancel") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
