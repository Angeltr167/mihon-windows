package mihon.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.compose.foundation.lazy.grid.items as gridItems

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

private fun Source.displayName(): String =
    if (lang == "localsourcelang") name else "$name (${lang.uppercase()})"

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
    var historyEntries by remember { mutableStateOf(session.library.history()) }
    var updateEntries by remember { mutableStateOf(session.library.updates()) }
    var categories by remember { mutableStateOf(session.library.categories()) }
    var selectedCategory by remember { mutableStateOf<Long?>(null) }
    var librarySearch by remember { mutableStateOf("") }
    var historySearch by remember { mutableStateOf("") }
    var categorySearch by remember { mutableStateOf("") }
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
        historyEntries = session.library.history()
        updateEntries = session.library.updates()
        categories = session.library.categories()
    }
    fun browse(selectedSource: Source, search: String = "") {
        source = selectedSource
        selectedManga = null
        chapters = emptyList()
        screen = if (screen == Screen.SEARCH) Screen.SEARCH else Screen.SOURCES
        scope.launch {
            message = "Loading ${selectedSource.displayName()}…"
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
    fun loadMangaDetails(selectedSource: Source, manga: SManga, chapterUrl: String? = null) {
        source = selectedSource
        selectedManga = manga
        chapters = emptyList()
        message = "Loading ${manga.title}…"
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    selectedSource.getMangaUpdate(manga, emptyList(), true, true)
                }
            }.onSuccess { update ->
                selectedManga = update.manga
                chapters = update.chapters
                message = ""
                if (chapterUrl != null) {
                    if (update.chapters.none { it.url == chapterUrl }) {
                        message = "The saved chapter is no longer available from this source"
                    } else {
                        readerTarget = ReaderTarget(selectedSource, update.manga, update.chapters, chapterUrl)
                    }
                }
            }.onFailure { message = it.message ?: "Details failed" }
        }
    }
    fun openStoredManga(mangaId: Long, chapterUrl: String? = null) {
        val stored = session.library.manga(mangaId) ?: run {
            message = "This manga is no longer in the library"
            return
        }
        val installedSource = sources.firstOrNull { it.id == stored.source } ?: run {
            message = "Install this manga’s source to browse its details and continue reading"
            return
        }
        val manga = SManga.create().apply {
            url = stored.url
            title = stored.title
            description = stored.description
            thumbnail_url = stored.thumbnail_url
            artist = stored.artist
            author = stored.author
            genre = stored.genre?.joinToString(", ")
        }
        loadMangaDetails(installedSource, manga, chapterUrl)
    }
    fun queueStoredChapter(mangaId: Long, chapterUrl: String) {
        val stored = session.library.manga(mangaId) ?: run {
            message = "This manga is no longer in the library"
            return
        }
        val installedSource = sources.firstOrNull { it.id == stored.source } ?: run {
            message = "Install this manga’s source before downloading chapters"
            return
        }
        val manga = SManga.create().apply {
            url = stored.url
            title = stored.title
            description = stored.description
            thumbnail_url = stored.thumbnail_url
        }
        scope.launch {
            message = "Loading chapter…"
            runCatching {
                withContext(Dispatchers.IO) {
                    val update = installedSource.getMangaUpdate(manga, emptyList(), true, true)
                    val chapter = update.chapters.firstOrNull { it.url == chapterUrl }
                        ?: error("This chapter is no longer available")
                    Triple(update.manga, chapter, installedSource)
                }
            }.onSuccess { (updatedManga, chapter, updatedSource) ->
                session.downloads.enqueue(updatedSource, updatedManga, chapter)
                message = "Added ${chapter.name} to downloads"
            }.onFailure { message = it.message ?: "Could not queue chapter" }
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

    LaunchedEffect(libraryUpdate.running, libraryUpdate.message) {
        if (!libraryUpdate.running && libraryUpdate.message.isNotBlank()) refreshLibrary()
    }

    MihonDesktopTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MihonPalette.graphite) {
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
                    Column(
                        Modifier.width(190.dp).fillMaxSize().background(MihonPalette.panel).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text("Mihon", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(8.dp))
                        Text(
                            "WINDOWS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MihonPalette.muted,
                            modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
                        )
                        Screen.entries.forEachIndexed { index, item ->
                            val selected = screen == item
                            Surface(
                                modifier = Modifier.fillMaxWidth()
                                    .semantics { this.selected = selected }
                                    .clickable(role = Role.Tab) {
                                        screen = item
                                        readerTarget = null
                                        selectedManga = null
                                        chapters = emptyList()
                                        message = ""
                                        if (item == Screen.LIBRARY) refreshLibrary()
                                    },
                                shape = RoundedCornerShape(9.dp),
                                color = if (selected) MihonPalette.raised else Color.Transparent,
                                border = if (selected) BorderStroke(1.dp, MihonPalette.outline) else null,
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier.width(3.dp).background(
                                            if (selected) MihonPalette.sage else Color.Transparent,
                                            RoundedCornerShape(4.dp),
                                        ),
                                    )
                                    Text(
                                        "${index + 1}",
                                        color = if (selected) MihonPalette.sage else MihonPalette.muted,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                    Text(
                                        stringResource(item.title),
                                        color = if (selected) MihonPalette.ivory else MihonPalette.muted,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }
                    }
                }
                Column(
                    Modifier.fillMaxSize()
                        .padding(if (readerTarget == null) 24.dp else 8.dp)
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
                    if (message.isNotBlank()) {
                        val isError = listOf("failed", "could not", "error", "not available", "no installed")
                            .any { message.contains(it, ignoreCase = true) }
                        MihonPanel(Modifier.fillMaxWidth()) {
                            Text(
                                message,
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                                color = if (isError) MaterialTheme.colorScheme.error else MihonPalette.muted,
                            )
                        }
                    }
                    when {
                        readerTarget != null -> DesktopReader(
                            session = session,
                            graph = graph,
                            target = requireNotNull(readerTarget),
                            onClose = {
                                readerTarget = null
                                refreshLibrary()
                            },
                            onToggleFullscreen = onToggleFullscreen,
                        )
                        selectedManga != null -> {
                            val item = requireNotNull(selectedManga)
                            val selectedSource = source
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                        MihonPanel(Modifier.fillMaxWidth()) {
                                            Row(
                                                Modifier.fillMaxWidth().padding(14.dp),
                                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                                                verticalAlignment = Alignment.Top,
                                            ) {
                                                DesktopCover(
                                                    item.thumbnail_url,
                                                    selectedSource,
                                                    Modifier.width(170.dp).height(245.dp),
                                                )
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text(item.title, style = MaterialTheme.typography.headlineSmall)
                                                    selectedSource?.let {
                                                        Text(it.displayName(), color = MihonPalette.muted)
                                                    }
                                                    Text(
                                                        item.description.orEmpty().ifBlank {
                                                            "No description available."
                                                        },
                                                        color = MihonPalette.muted,
                                                        maxLines = 8,
                                                    )
                                                }
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(onClick = {
                                                if (stored == null && selectedSource != null) {
                                                    session.library.addToLibrary(selectedSource.id, item)
                                                } else if (stored != null) {
                                                    session.library.setFavorite(stored._id, !stored.favorite)
                                                }
                                                refreshLibrary()
                                                message = "Library updated"
                                            }) {
                                                Text(
                                                    if (stored?.favorite ==
                                                        true
                                                    ) {
                                                        "Remove from library"
                                                    } else {
                                                        "Add to library"
                                                    },
                                                )
                                            }
                                            TextButton(onClick = { selectedManga = null }) { Text("Back") }
                                            if (selectedSource is HttpSource) {
                                                TextButton(onClick = {
                                                    runCatching {
                                                        val url = selectedSource.getMangaUrl(item)
                                                        val uri = URI(url)
                                                        require(
                                                            uri.scheme in setOf(
                                                                "http",
                                                                "https",
                                                            ) && uri.userInfo == null,
                                                        )
                                                        check(graph.browserService.open(url))
                                                    }.onFailure { message = it.message ?: "Could not open browser" }
                                                }) { Text("Open in browser") }
                                                TextButton(onClick = {
                                                    runCatching {
                                                        val url = selectedSource.getMangaUrl(item)
                                                        val uri = URI(url)
                                                        require(
                                                            uri.scheme in setOf(
                                                                "http",
                                                                "https",
                                                            ) && uri.userInfo == null,
                                                        )
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
                                                                session.komgaTracker.bind(
                                                                    stored._id,
                                                                    item.title,
                                                                    item.url,
                                                                )
                                                            } else {
                                                                val lastRead = withContext(Dispatchers.IO) {
                                                                    chapters.filter {
                                                                        session.library.chapter(
                                                                            stored._id,
                                                                            it.url,
                                                                        )?.read ==
                                                                            true
                                                                    }.maxOfOrNull { it.chapter_number.toDouble() }
                                                                }
                                                                if (lastRead != null && lastRead.isFinite() &&
                                                                    lastRead > 0
                                                                ) {
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
                                                                .onFailure {
                                                                    message =
                                                                        it.message ?: "Kavita link failed"
                                                                }
                                                        }
                                                    }) { Text("Link Kavita") }
                                                }
                                            } else {
                                                TextButton(onClick = {
                                                    scope.launch {
                                                        val lastRead = withContext(Dispatchers.IO) {
                                                            chapters.filter {
                                                                session.library.chapter(stored._id, it.url)?.read ==
                                                                    true
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
                                                            }.onFailure {
                                                                message = it.message ?: "AniList link failed"
                                                            }
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
                                                            }.onFailure {
                                                                message =
                                                                    it.message ?: "MangaUpdates link failed"
                                                            }
                                                        }
                                                    }) { Text("Link MangaUpdates") }
                                                }
                                            } else {
                                                TextButton(onClick = {
                                                    scope.launch {
                                                        val lastRead = withContext(Dispatchers.IO) {
                                                            chapters.filter {
                                                                session.library.chapter(stored._id, it.url)?.read ==
                                                                    true
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
                                                                session.library.chapter(stored._id, it.url)?.read ==
                                                                    true
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
                                                            }.onFailure {
                                                                message =
                                                                    it.message ?: "MyAnimeList link failed"
                                                            }
                                                        }
                                                    }) { Text("Link MyAnimeList") }
                                                }
                                            } else {
                                                TextButton(onClick = {
                                                    scope.launch {
                                                        val lastRead = withContext(Dispatchers.IO) {
                                                            chapters.filter {
                                                                session.library.chapter(stored._id, it.url)?.read ==
                                                                    true
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
                                                            }.onFailure {
                                                                message =
                                                                    it.message ?: "Shikimori link failed"
                                                            }
                                                        }
                                                    }) { Text("Link Shikimori") }
                                                }
                                            } else {
                                                TextButton(onClick = {
                                                    scope.launch {
                                                        val lastRead = withContext(Dispatchers.IO) {
                                                            chapters.filter {
                                                                session.library.chapter(stored._id, it.url)?.read ==
                                                                    true
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
                                                            runCatching {
                                                                session.hikkaTracker.bind(stored._id, hikkaSlug.trim())
                                                            }
                                                                .onSuccess {
                                                                    hikkaSlug = ""
                                                                    message = "Hikka tracking linked"
                                                                }.onFailure {
                                                                    message =
                                                                        it.message ?: "Hikka link failed"
                                                                }
                                                        }
                                                    }) { Text("Link Hikka") }
                                                }
                                            } else {
                                                TextButton(onClick = {
                                                    scope.launch {
                                                        val lastRead = withContext(Dispatchers.IO) {
                                                            chapters.filter {
                                                                session.library.chapter(stored._id, it.url)?.read ==
                                                                    true
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
                                                            }.onFailure {
                                                                message = it.message ?: "Bangumi link failed"
                                                            }
                                                        }
                                                    }) { Text("Link Bangumi") }
                                                }
                                            } else {
                                                TextButton(onClick = {
                                                    scope.launch {
                                                        val lastRead = withContext(Dispatchers.IO) {
                                                            chapters.filter {
                                                                session.library.chapter(stored._id, it.url)?.read ==
                                                                    true
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
                                                            }.onFailure {
                                                                message =
                                                                    it.message ?: "MangaBaka link failed"
                                                            }
                                                        }
                                                    }) { Text("Link MangaBaka") }
                                                }
                                            } else {
                                                TextButton(onClick = {
                                                    scope.launch {
                                                        val lastRead = withContext(Dispatchers.IO) {
                                                            chapters.filter {
                                                                session.library.chapter(stored._id, it.url)?.read ==
                                                                    true
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
                                                            session.suwayomiTracker.bind(
                                                                stored._id,
                                                                selectedSource.id,
                                                                item.url,
                                                            )
                                                        } else {
                                                            val lastRead = withContext(Dispatchers.IO) {
                                                                chapters.filter {
                                                                    session.library.chapter(stored._id, it.url)?.read ==
                                                                        true
                                                                }.maxOfOrNull { it.chapter_number.toDouble() }
                                                            }
                                                            if (lastRead != null && lastRead.isFinite() &&
                                                                lastRead > 0
                                                            ) {
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
                                                                if (checked) {
                                                                    selected + category.id
                                                                } else {
                                                                    selected -
                                                                        category.id
                                                                },
                                                            )
                                                            message = "Categories updated"
                                                        },
                                                    )
                                                    Text(
                                                        category.name.ifBlank { "Uncategorized" },
                                                        modifier = Modifier.padding(top = 12.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                item {
                                    MihonSectionHeader("Chapters", "${chapters.size} chapters available")
                                }
                                if (chapters.isEmpty()) {
                                    item {
                                        MihonEmptyState(
                                            "No chapters available",
                                            "Try refreshing details from the source.",
                                        )
                                    }
                                }
                                items(chapters, key = SChapter::url) { chapter ->
                                    MihonPanel(Modifier.fillMaxWidth()) {
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
                        }
                        screen == Screen.LIBRARY -> {
                            val visibleLibrary = library.asSequence()
                                .filter { it.title.contains(librarySearch, ignoreCase = true) }
                                .filter {
                                    selectedCategory == null || run {
                                        val membership = session.library.mangaCategories(it._id)
                                        if (selectedCategory ==
                                            0L
                                        ) {
                                            membership.isEmpty()
                                        } else {
                                            selectedCategory in membership
                                        }
                                    }
                                }
                                .toList()
                            MihonSectionHeader(
                                "Your library",
                                if (selectedCategory == null) {
                                    "${library.size} manga saved on this device"
                                } else {
                                    "${visibleLibrary.size} manga in this category"
                                },
                                trailing = {
                                    OutlinedTextField(
                                        librarySearch,
                                        { librarySearch = it },
                                        label = { Text("Search library") },
                                        singleLine = true,
                                        modifier = Modifier.width(250.dp),
                                    )
                                },
                            )
                            if (selectedCategory != null) {
                                TextButton(onClick = { selectedCategory = null }) { Text("All library") }
                            }
                            if (library.isEmpty()) {
                                MihonEmptyState(
                                    "Your library is empty",
                                    "Browse an installed source and add a manga to start reading.",
                                )
                            } else if (visibleLibrary.isEmpty()) {
                                MihonEmptyState("No manga found", "Try a different search or category.")
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(160.dp),
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    gridItems(visibleLibrary, key = Mangas::_id) { item ->
                                        val itemSource = sources.firstOrNull { it.id == item.source }
                                        val lastRead = historyEntries.firstOrNull { it.mangaId == item._id }
                                        val chapter = lastRead?.let { session.library.chapter(it.chapterId) }
                                        MihonPanel {
                                            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                                DesktopCover(
                                                    item.thumbnail_url,
                                                    itemSource,
                                                    Modifier.fillMaxWidth().aspectRatio(0.69f)
                                                        .clickable { openStoredManga(item._id) },
                                                )
                                                Text(
                                                    item.title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    maxLines = 2,
                                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                                )
                                                Text(
                                                    chapter?.name ?: "Not read yet",
                                                    color = MihonPalette.muted,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1,
                                                )
                                                TextButton(
                                                    onClick = {
                                                        if (chapter != null) {
                                                            openStoredManga(item._id, chapter.url)
                                                        } else {
                                                            openStoredManga(item._id)
                                                        }
                                                    },
                                                ) { Text(if (chapter != null) "Continue reading" else "Details") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        screen == Screen.SOURCES || screen == Screen.SEARCH -> {
                            if (screen == Screen.SEARCH) {
                                MihonPanel(Modifier.fillMaxWidth()) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        OutlinedTextField(
                                            link,
                                            { link = it },
                                            label = { Text("Manga or Mihon link") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Button(onClick = { openLink(link) }) { Text("Open link") }
                                        TextButton(onClick = {
                                            graph.clipboardService.readText()?.let { pasted ->
                                                link = pasted
                                                openLink(pasted)
                                            }
                                        }) { Text("Paste link") }
                                    }
                                }
                            }
                            if (source == null) {
                                MihonSectionHeader(
                                    if (screen == Screen.SEARCH) "Search manga" else "Sources",
                                    "Local source and ${sources.count { it !is DesktopLocalSource }} installed sources",
                                )
                                if (sources.isEmpty()) {
                                    MihonEmptyState(
                                        "No sources available",
                                        "Check the Extensions screen or browse your local library.",
                                    )
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(sources, key = Source::id) { available ->
                                            MihonPanel(Modifier.fillMaxWidth().clickable { browse(available) }) {
                                                Row(
                                                    Modifier.fillMaxWidth().padding(16.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Column {
                                                        Text(
                                                            available.displayName(),
                                                            style = MaterialTheme.typography.titleMedium,
                                                        )
                                                        Text(
                                                            if (available is DesktopLocalSource) {
                                                                "On this device"
                                                            } else {
                                                                "Installed source · ${available.lang.uppercase()}"
                                                            },
                                                            color = MihonPalette.muted,
                                                            style = MaterialTheme.typography.bodySmall,
                                                        )
                                                    }
                                                    Text("Browse", color = MihonPalette.sage)
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                val activeSource = requireNotNull(source)
                                MihonSectionHeader(
                                    activeSource.displayName(),
                                    if (activeSource is DesktopLocalSource) {
                                        "Manga stored on this device"
                                    } else {
                                        "Installed source · ${activeSource.lang.uppercase()}"
                                    },
                                    trailing = {
                                        TextButton(onClick = {
                                            source = null
                                            browseItems = emptyList()
                                        }) { Text("All sources") }
                                    },
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    OutlinedTextField(
                                        query,
                                        { query = it },
                                        label = { Text("Search ${activeSource.displayName()}") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Button(onClick = { source?.let { browse(it, query) } }) { Text("Search") }
                                }
                                if (browseItems.isEmpty() && message.isBlank()) {
                                    MihonEmptyState(
                                        "No manga to show",
                                        "Search this source or select another installed source.",
                                    )
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(browseItems, key = SManga::url) { item ->
                                            MihonPanel(
                                                Modifier.fillMaxWidth().clickable {
                                                    val selectedSource = source
                                                    if (selectedSource != null) loadMangaDetails(selectedSource, item)
                                                },
                                            ) {
                                                Row(
                                                    Modifier.fillMaxWidth().padding(10.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    DesktopCover(item.thumbnail_url, activeSource)
                                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                                                        Text(activeSource.displayName(), color = MihonPalette.muted)
                                                        Text(
                                                            "Open manga details",
                                                            color = MihonPalette.sage,
                                                            style = MaterialTheme.typography.labelMedium,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        screen == Screen.EXTENSIONS -> {
                            Column(Modifier.verticalScroll(rememberScrollState())) {
                                MihonSectionHeader(
                                    "Extensions",
                                    "Keiyoushi sources through Suwayomi, plus signed Desktop .mihonext packages",
                                )
                                MihonPanel(Modifier.fillMaxWidth()) {
                                    Column(
                                        Modifier.fillMaxWidth().padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text("Keiyoushi catalog", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "Only install code from publishers you trust. " +
                                                "Fingerprint trust does not verify publisher identity.",
                                            color = MihonPalette.muted,
                                        )
                                        Text(
                                            suwayomiStatus,
                                            color = if (session.suwayomiEngine.isRunning) {
                                                MihonPalette.sage
                                            } else {
                                                MaterialTheme.colorScheme.error
                                            },
                                        )
                                        if (!session.suwayomiEngine.isRunning) {
                                            Text(
                                                "The engine runs separately from Mihon.",
                                                color = MihonPalette.muted,
                                            )
                                        }
                                    }
                                }
                                if (session.suwayomiEngine.isRunning) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
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
                                            singleLine = true,
                                        )
                                    }
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
                                        "Search among ${visible.size} of ${suwayomiExtensions.size} sources",
                                        color = MihonPalette.muted,
                                    )
                                    if (suwayomiExtensions.isEmpty()) {
                                        MihonEmptyState(
                                            "Keiyoushi catalog is not loaded",
                                            "Load the catalog to browse and manage compatible sources.",
                                        )
                                    } else if (visible.isEmpty()) {
                                        MihonEmptyState(
                                            "No matching extensions",
                                            "Change the search to see other extensions.",
                                        )
                                    }
                                    visible.forEach { entry ->
                                        MihonPanel(Modifier.fillMaxWidth()) {
                                            Row(
                                                Modifier.fillMaxWidth().padding(10.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Column(Modifier.weight(1f)) {
                                                    Text(entry.name, style = MaterialTheme.typography.titleMedium)
                                                    Text(
                                                        "${entry.versionName} · ${entry.contentWarning}",
                                                        color = MihonPalette.muted,
                                                        style = MaterialTheme.typography.bodySmall,
                                                    )
                                                }
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
                                }
                                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                                MihonSectionHeader(
                                    "Desktop .mihonext packages",
                                    "Signed JVM extensions use explicit, local fingerprint trust.",
                                )
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
                                val installedDesktopExtensions = session.extensions.installedExtensions()
                                if (installedDesktopExtensions.isEmpty()) {
                                    MihonEmptyState(
                                        "No Desktop extensions installed",
                                        "Install a signed .mihonext package or discover a trusted HTTPS index.",
                                    )
                                }
                                installedDesktopExtensions.forEach { installed ->
                                    MihonPanel(Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text(installed.id, style = MaterialTheme.typography.titleMedium)
                                            Text("Version ${installed.versionCode}", color = MihonPalette.muted)
                                            Text(
                                                "Signing fingerprint accepted locally",
                                                color = MihonPalette.muted,
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    }
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
                            MihonSectionHeader(
                                "Categories",
                                "${categories.size} categories · organize and open your library by category",
                            )
                            OutlinedTextField(
                                categorySearch,
                                { categorySearch = it },
                                label = { Text("Find category") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedTextField(
                                    categoryName,
                                    { categoryName = it },
                                    label = { Text("New category") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                Button(
                                    onClick = {
                                        runCatching { session.library.addCategory(categoryName) }
                                            .onSuccess {
                                                categoryName = ""
                                                categories = session.library.categories()
                                                message = "Category added"
                                            }
                                            .onFailure { message = it.message ?: "Category failed" }
                                    },
                                    enabled = categoryName.isNotBlank(),
                                ) { Text("Add") }
                            }
                            val visibleCategories = categories.filter {
                                (it.name.ifBlank { "Uncategorized" }).contains(categorySearch, ignoreCase = true)
                            }
                            if (visibleCategories.isEmpty()) {
                                MihonEmptyState("No categories found", "Create a category or change the search.")
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    items(visibleCategories, key = { it.id }) { category ->
                                        val categoryManga = library.filter { manga ->
                                            val membership = session.library.mangaCategories(manga._id)
                                            if (category.id == 0L) membership.isEmpty() else category.id in membership
                                        }
                                        MihonPanel(
                                            Modifier.fillMaxWidth().clickable {
                                                selectedCategory = category.id
                                                screen = Screen.LIBRARY
                                                librarySearch = ""
                                            },
                                        ) {
                                            Row(
                                                Modifier.fillMaxWidth().padding(14.dp),
                                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Column(
                                                    Modifier.width(170.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                                ) {
                                                    Text(
                                                        category.name.ifBlank { "Uncategorized" },
                                                        style = MaterialTheme.typography.titleLarge,
                                                    )
                                                    Text(
                                                        "${categoryManga.size} manga",
                                                        color = MihonPalette.muted,
                                                    )
                                                    Text(
                                                        "Open library",
                                                        color = MihonPalette.sage,
                                                        style = MaterialTheme.typography.labelMedium,
                                                    )
                                                }
                                                Row(
                                                    Modifier.weight(1f),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                ) {
                                                    categoryManga.take(5).forEach { manga ->
                                                        DesktopCover(
                                                            manga.thumbnail_url,
                                                            sources.firstOrNull { it.id == manga.source },
                                                            Modifier.width(52.dp).height(74.dp),
                                                        )
                                                    }
                                                    if (categoryManga.isEmpty()) {
                                                        Text("No manga assigned", color = MihonPalette.muted)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        screen == Screen.HISTORY -> {
                            MihonSectionHeader(
                                "Reading history",
                                "Recent chapters and saved reading positions",
                                trailing = {
                                    OutlinedTextField(
                                        historySearch,
                                        { historySearch = it },
                                        label = { Text("Search history") },
                                        singleLine = true,
                                        modifier = Modifier.width(250.dp),
                                    )
                                },
                            )
                            val visibleHistory = historyEntries.filter {
                                it.title.contains(
                                    historySearch,
                                    ignoreCase = true,
                                )
                            }
                            if (visibleHistory.isEmpty()) {
                                MihonEmptyState(
                                    if (historySearch.isBlank()) "Nothing read yet" else "No history found",
                                    if (historySearch.isBlank()) {
                                        "Your reading progress will appear here."
                                    } else {
                                        "Try a different title."
                                    },
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(visibleHistory, key = { it.id }) { entry ->
                                        val storedChapter = session.library.chapter(entry.chapterId)
                                        val manga = session.library.manga(entry.mangaId)
                                        MihonPanel {
                                            Row(
                                                Modifier.fillMaxWidth().padding(10.dp),
                                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                DesktopCover(
                                                    entry.thumbnailUrl,
                                                    sources.firstOrNull { it.id == entry.source },
                                                    Modifier.width(70.dp).height(100.dp),
                                                )
                                                Column(
                                                    Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                                ) {
                                                    Text(entry.title, style = MaterialTheme.typography.titleMedium)
                                                    Text(
                                                        storedChapter?.name ?: "Chapter ${entry.chapterNumber}",
                                                        color = MihonPalette.muted,
                                                    )
                                                    Text(
                                                        if (storedChapter?.read == true) {
                                                            "Chapter finished"
                                                        } else {
                                                            "Page ${(storedChapter?.last_page_read ?: 0) + 1}"
                                                        },
                                                        color = MihonPalette.muted,
                                                        style = MaterialTheme.typography.bodySmall,
                                                    )
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(
                                                        java.time.Instant.ofEpochMilli(entry.readAt?.time ?: 0L)
                                                            .atZone(java.time.ZoneId.systemDefault())
                                                            .format(
                                                                java.time.format.DateTimeFormatter.ofPattern(
                                                                    "MMM d · HH:mm",
                                                                ),
                                                            ),
                                                        color = MihonPalette.muted,
                                                        style = MaterialTheme.typography.bodySmall,
                                                    )
                                                    TextButton(
                                                        onClick = {
                                                            if (manga != null && storedChapter != null) {
                                                                openStoredManga(entry.mangaId, storedChapter.url)
                                                            } else {
                                                                message = "The saved chapter is no longer available"
                                                            }
                                                        },
                                                    ) { Text("Continue") }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        screen == Screen.UPDATES -> {
                            MihonSectionHeader(
                                "Updates",
                                "New chapters from manga in your library",
                                trailing = {
                                    Button(
                                        onClick = {
                                            message = "Checking library for updates…"
                                            session.libraryUpdates.updateNow()
                                        },
                                        enabled = !libraryUpdate.running,
                                    ) { Text(if (libraryUpdate.running) "Checking…" else "Check library now") }
                                },
                            )
                            if (libraryUpdate.message.isNotBlank()) {
                                MihonPanel(Modifier.fillMaxWidth()) {
                                    Text(
                                        libraryUpdate.message,
                                        Modifier.padding(12.dp),
                                        color = if (libraryUpdate.failures > 0) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MihonPalette.muted
                                        },
                                    )
                                }
                            }
                            if (updateEntries.isEmpty()) {
                                MihonEmptyState(
                                    "No updates yet",
                                    "Check your library when sources are available to look for new chapters.",
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(updateEntries, key = { it.chapterId }) { entry ->
                                        MihonPanel {
                                            Row(
                                                Modifier.fillMaxWidth().padding(10.dp),
                                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                DesktopCover(
                                                    entry.thumbnailUrl,
                                                    sources.firstOrNull { it.id == entry.source },
                                                    Modifier.width(70.dp).height(100.dp),
                                                )
                                                Column(Modifier.weight(1f)) {
                                                    Text(entry.mangaTitle, style = MaterialTheme.typography.titleMedium)
                                                    Text(entry.chapterName, color = MihonPalette.muted)
                                                    Text(
                                                        if (entry.read) "Read" else "Unread",
                                                        color = if (entry.read) {
                                                            MihonPalette.muted
                                                        } else {
                                                            MihonPalette.sage
                                                        },
                                                        style = MaterialTheme.typography.labelMedium,
                                                    )
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(
                                                        java.time.Instant.ofEpochMilli(entry.dateUpload)
                                                            .atZone(java.time.ZoneId.systemDefault())
                                                            .format(
                                                                java.time.format.DateTimeFormatter.ofPattern("MMM d"),
                                                            ),
                                                        color = MihonPalette.muted,
                                                        style = MaterialTheme.typography.bodySmall,
                                                    )
                                                    TextButton(onClick = {
                                                        openStoredManga(
                                                            entry.mangaId,
                                                            entry.chapterUrl,
                                                        )
                                                    }) {
                                                        Text("Read")
                                                    }
                                                    TextButton(onClick = {
                                                        queueStoredChapter(
                                                            entry.mangaId,
                                                            entry.chapterUrl,
                                                        )
                                                    }) {
                                                        Text("Download")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        screen == Screen.SETTINGS -> {
                            MihonSectionHeader(
                                "Settings",
                                "Storage, migration, links, reading integrations and update scheduling",
                            )
                            MihonPanel(Modifier.fillMaxWidth()) {
                                Column(
                                    Modifier.fillMaxWidth().padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text("Migration", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Import a Mihon Android .tachibk backup. Matching manga records are updated.",
                                        color = MihonPalette.muted,
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
                                                OpenFileRequest(
                                                    "Import Mihon Android backup",
                                                    extensions = setOf("tachibk"),
                                                ),
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
                                                    message = buildString {
                                                        append("Imported ${result.manga} manga")
                                                        append(", ${result.chapters} chapters")
                                                        append(", ${result.categories} categories")
                                                        append(", ${result.trackerEntries} tracker entries")
                                                    }
                                                }.onFailure { message = it.message ?: "Backup import failed" }
                                            }
                                        }, enabled = backupPath.isNotBlank()) { Text("Import backup") }
                                    }
                                }
                            }
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            MihonPanel(Modifier.fillMaxWidth()) {
                                Column(
                                    Modifier.fillMaxWidth().padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text("Application and storage", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Language: ${graph.localeService.currentLanguageTag()}",
                                        color = MihonPalette.muted,
                                    )
                                    Text(
                                        "Local library: ${graph.appDirectories.localLibrary}",
                                        color = MihonPalette.muted,
                                    )
                                    Text("Database: ${graph.appDirectories.database}", color = MihonPalette.muted)
                                    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
                                        Text(
                                            "Browser links: " +
                                                if (mihonProtocolRegistered) "registered" else "not registered",
                                            color = MihonPalette.muted,
                                        )
                                        if (mihonProtocolRegistered) {
                                            TextButton(onClick = {
                                                runCatching { protocolRegistrar.unregisterMihonProtocol() }
                                                    .onSuccess {
                                                        mihonProtocolRegistered = false
                                                        message = "Mihon browser links unregistered"
                                                    }.onFailure {
                                                        message = it.message ?: "Could not unregister Mihon links"
                                                    }
                                            }) { Text("Unregister Mihon browser links") }
                                        } else {
                                            TextButton(onClick = {
                                                runCatching { protocolRegistrar.registerMihonProtocol() }
                                                    .onSuccess {
                                                        mihonProtocolRegistered = true
                                                        message = "Mihon browser links registered for this Windows user"
                                                    }.onFailure {
                                                        message = it.message ?: "Could not register Mihon links"
                                                    }
                                            }) { Text("Register Mihon browser links") }
                                        }
                                    }
                                }
                            }
                            Text(
                                "Tracker integrations",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            Text(
                                "AniList: ${if (aniListLoggedIn) "signed in" else "not signed in"}",
                                style = MaterialTheme.typography.titleMedium,
                            )
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
                            HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            Text(
                                "MangaUpdates: ${if (mangaUpdatesLoggedIn) "signed in" else "not signed in"}",
                                style = MaterialTheme.typography.titleMedium,
                            )
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
                            HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            Text(
                                "Kitsu: ${if (kitsuLoggedIn) "signed in" else "not signed in"}",
                                style = MaterialTheme.typography.titleMedium,
                            )
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
                            HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            Text(
                                "MyAnimeList: ${if (malLoggedIn) "signed in" else "not signed in"}",
                                style = MaterialTheme.typography.titleMedium,
                            )
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
                            HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            Text(
                                "Shikimori: ${if (shikimoriLoggedIn) "signed in" else "not signed in"}",
                                style = MaterialTheme.typography.titleMedium,
                            )
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
                            HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            Text(
                                "Hikka: ${if (hikkaLoggedIn) "signed in" else "not signed in"}",
                                style = MaterialTheme.typography.titleMedium,
                            )
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
                            HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            Text(
                                "Bangumi: ${if (bangumiLoggedIn) "signed in" else "not signed in"}",
                                style = MaterialTheme.typography.titleMedium,
                            )
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
                            HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            Text(
                                "MangaBaka: ${if (mangaBakaLoggedIn) "signed in" else "not signed in"}",
                                style = MaterialTheme.typography.titleMedium,
                            )
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
                            MihonPanel(Modifier.fillMaxWidth()) {
                                Column(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text("Library and downloads", style = MaterialTheme.typography.titleMedium)
                                    TextButton(onClick = {
                                        val intervals = DesktopLibraryUpdateScheduler.INTERVALS
                                        updateInterval =
                                            intervals[(intervals.indexOf(updateInterval) + 1) % intervals.size]
                                        session.libraryUpdates.setIntervalHours(updateInterval)
                                    }) {
                                        Text(
                                            if (updateInterval == 0L) {
                                                "Scheduled library updates: off"
                                            } else {
                                                "Scheduled updates: every $updateInterval hours (while app is open)"
                                            },
                                        )
                                    }
                                    Text(
                                        "Downloads wait for an active network connection.",
                                        color = MihonPalette.muted,
                                    )
                                }
                            }
                        }
                        screen == Screen.DOWNLOADS -> {
                            MihonSectionHeader(
                                "Downloads",
                                "${downloads.size} chapters in the persisted queue",
                                trailing = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = { session.downloads.pause() }) { Text("Pause all") }
                                        Button(onClick = { session.downloads.resume() }) { Text("Resume all") }
                                    }
                                },
                            )
                            if (downloads.isEmpty()) {
                                MihonEmptyState(
                                    "No downloads",
                                    "Queue a chapter from its manga details or the Updates screen.",
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(downloads, key = DesktopDownload::key) { download ->
                                        val savedManga = session.library.find(download.sourceId, download.mangaUrl)
                                        val downloadSource = sources.firstOrNull { it.id == download.sourceId }
                                        MihonPanel {
                                            Row(
                                                Modifier.fillMaxWidth().padding(12.dp),
                                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                DesktopCover(
                                                    savedManga?.thumbnail_url,
                                                    downloadSource,
                                                    Modifier.width(68.dp).height(96.dp),
                                                )
                                                Column(
                                                    Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                                ) {
                                                    Text(
                                                        download.mangaTitle,
                                                        style = MaterialTheme.typography.titleMedium,
                                                    )
                                                    Text(download.chapterName, color = MihonPalette.muted)
                                                    Text(
                                                        when (download.status) {
                                                            DesktopDownloadStatus.PENDING -> "Queued"
                                                            DesktopDownloadStatus.RUNNING -> "Downloading"
                                                            DesktopDownloadStatus.PAUSED -> "Paused"
                                                            DesktopDownloadStatus.FAILED -> "Failed"
                                                            DesktopDownloadStatus.COMPLETED -> "Completed"
                                                        },
                                                        color = if (download.status == DesktopDownloadStatus.FAILED) {
                                                            MaterialTheme.colorScheme.error
                                                        } else {
                                                            MihonPalette.sage
                                                        },
                                                        style = MaterialTheme.typography.labelMedium,
                                                    )
                                                    if (
                                                        download.pageCount > 0 &&
                                                        download.status != DesktopDownloadStatus.FAILED
                                                    ) {
                                                        LinearProgressIndicator(
                                                            progress = {
                                                                download.pagesDone.toFloat()
                                                                    .div(download.pageCount)
                                                                    .coerceIn(0f, 1f)
                                                            },
                                                            modifier = Modifier.fillMaxWidth(),
                                                            color = MihonPalette.sage,
                                                            trackColor = MihonPalette.raised,
                                                        )
                                                        Text(
                                                            "${download.pagesDone} / ${download.pageCount} pages",
                                                            color = MihonPalette.muted,
                                                            style = MaterialTheme.typography.bodySmall,
                                                        )
                                                    }
                                                    download.error?.let {
                                                        Text(
                                                            it,
                                                            color = MaterialTheme.colorScheme.error,
                                                            style = MaterialTheme.typography.bodySmall,
                                                        )
                                                    }
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    val retry = download.status == DesktopDownloadStatus.FAILED
                                                    when (download.status) {
                                                        DesktopDownloadStatus.PENDING, DesktopDownloadStatus.RUNNING ->
                                                            TextButton(
                                                                onClick = { session.downloads.pause(download.key) },
                                                            ) {
                                                                Text("Pause")
                                                            }
                                                        DesktopDownloadStatus.PAUSED, DesktopDownloadStatus.FAILED ->
                                                            TextButton(
                                                                onClick = { session.downloads.resume(download.key) },
                                                            ) {
                                                                Text(
                                                                    if (retry) "Retry" else "Resume",
                                                                )
                                                            }
                                                        DesktopDownloadStatus.COMPLETED -> Unit
                                                    }
                                                    if (download.status != DesktopDownloadStatus.COMPLETED) {
                                                        TextButton(
                                                            onClick = { session.downloads.cancel(download.key) },
                                                        ) {
                                                            Text("Cancel")
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
                }
            }
        }
    }
}
