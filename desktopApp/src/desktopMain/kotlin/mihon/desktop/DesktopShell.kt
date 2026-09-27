package mihon.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.core.extension.desktop.DesktopRepositoryEntry
import mihon.platform.api.OpenFileRequest
import mihon.platform.desktop.DesktopPlatformGraph
import mihon.platform.desktop.WindowsProtocolRegistrar
import tachiyomi.data.Chapters
import tachiyomi.data.GetCategories
import tachiyomi.data.Manga_sync
import tachiyomi.data.Mangas
import tachiyomi.source.local.desktop.DesktopLocalSource
import tachiyomi.view.History
import tachiyomi.view.UpdatesView
import java.net.URI
import java.nio.file.Path
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import androidx.compose.foundation.lazy.grid.items as gridItems

private fun Source.displayName(): String =
    if (this is DesktopLocalSource || lang == "localsourcelang") name else "$name (${lang.uppercase()})"

private const val KEIYOUSHI_STORE_URL = "https://github.com/keiyoushi/extensions/raw/repo/index.pb"

private data class DetailSnapshot(
    val sourceId: Long,
    val mangaUrl: String,
    val stored: Mangas?,
    val tracks: Map<Long, Manga_sync>,
)

private data class LibrarySnapshot(
    val manga: List<Mangas>,
    val memberships: Map<Long, Set<Long>>,
    val history: List<History>,
    val updates: List<UpdatesView>,
    val categories: List<GetCategories>,
)

private class DesktopNoticeState : ReadWriteProperty<Any?, String> {
    private var text by mutableStateOf("")
    var isError by mutableStateOf(false)
        private set

    override fun getValue(thisRef: Any?, property: KProperty<*>): String = text

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
        text = value
        isError = false
    }

    fun error(value: String) {
        text = value
        isError = true
    }
}

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
    var settingsSection by remember { mutableStateOf(DesktopSettingsSection.GENERAL) }
    var sources by remember { mutableStateOf(session.sources()) }
    var library by remember { mutableStateOf(emptyList<Mangas>()) }
    var libraryMembership by remember { mutableStateOf<Map<Long, Set<Long>>>(emptyMap()) }
    var historyEntries by remember { mutableStateOf(emptyList<History>()) }
    var historyChapters by remember { mutableStateOf<Map<Long, Chapters?>>(emptyMap()) }
    var updateEntries by remember { mutableStateOf(emptyList<UpdatesView>()) }
    var categories by remember { mutableStateOf(emptyList<GetCategories>()) }
    var libraryLoading by remember { mutableStateOf(true) }
    var libraryJob by remember { mutableStateOf<Job?>(null) }
    var libraryRequestId by remember { mutableIntStateOf(0) }
    var selectedCategory by remember { mutableStateOf<Long?>(null) }
    var librarySearch by remember { mutableStateOf("") }
    var historySearch by remember { mutableStateOf("") }
    var sourceSearch by remember { mutableStateOf("") }
    var categorySearch by remember { mutableStateOf("") }
    var source by remember { mutableStateOf<Source?>(null) }
    var browseItems by remember { mutableStateOf(emptyList<SManga>()) }
    var browsePage by remember { mutableIntStateOf(0) }
    var browseHasNext by remember { mutableStateOf(false) }
    var browseLoading by remember { mutableStateOf(false) }
    var browseError by remember { mutableStateOf<String?>(null) }
    var browseQuery by remember { mutableStateOf("") }
    var browseRequestId by remember { mutableIntStateOf(0) }
    var browseJob by remember { mutableStateOf<Job?>(null) }
    var detailRequestId by remember { mutableIntStateOf(0) }
    var detailJob by remember { mutableStateOf<Job?>(null) }
    var selectedManga by remember { mutableStateOf<SManga?>(null) }
    var detailSnapshot by remember { mutableStateOf<DetailSnapshot?>(null) }
    var detailsLoading by remember { mutableStateOf(false) }
    var detailsError by remember { mutableStateOf<String?>(null) }
    var selectedMangaCategories by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var chapters by remember { mutableStateOf(emptyList<SChapter>()) }
    var readerTarget by remember { mutableStateOf<ReaderTarget?>(null) }
    var query by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var showLinkTools by remember { mutableStateOf(false) }
    var packagePath by remember { mutableStateOf("") }
    var indexUrl by remember { mutableStateOf("") }
    var availableExtensions by remember { mutableStateOf(emptyList<DesktopRepositoryEntry>()) }
    var installedDesktopExtensions by remember { mutableStateOf(session.extensions.installedExtensions()) }
    var suwayomiExtensions by remember { mutableStateOf(emptyList<SuwayomiExtension>()) }
    var suwayomiSearch by remember { mutableStateOf("") }
    var suwayomiStatus by remember { mutableStateOf("Starting local extension engine…") }
    var extensionSection by remember { mutableIntStateOf(0) }
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
    val notice = remember { DesktopNoticeState() }
    var message by notice
    var mihonProtocolRegistered by remember { mutableStateOf(protocolRegistrar.isRegistered()) }
    val downloads by session.downloads.queue.collectAsState()
    var downloadMangas by remember { mutableStateOf<Map<String, Mangas?>>(emptyMap()) }
    val trackerSyncQueue by session.trackerSync.pending.collectAsState()
    val incomingLink by incomingLinks.collectAsState()
    val libraryUpdate by session.libraryUpdates.state.collectAsState()
    var updateInterval by remember { mutableLongStateOf(session.libraryUpdates.intervalHours()) }

    fun refreshLibrary() {
        libraryJob?.cancel()
        libraryRequestId++
        val requestId = libraryRequestId
        libraryLoading = true
        libraryJob = scope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    val manga = session.library.library()
                    LibrarySnapshot(
                        manga,
                        manga.associate { it._id to session.library.mangaCategories(it._id) },
                        session.library.history(),
                        session.library.updates(),
                        session.library.categories(),
                    )
                }
                if (requestId == libraryRequestId) {
                    library = snapshot.manga
                    libraryMembership = snapshot.memberships
                    historyEntries = snapshot.history
                    updateEntries = snapshot.updates
                    categories = snapshot.categories
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (requestId == libraryRequestId) notice.error(error.message ?: "Could not load library")
            } finally {
                if (requestId == libraryRequestId) libraryLoading = false
            }
        }
    }
    fun navigateToScreen(destination: Screen) {
        screen = destination
        readerTarget = null
        selectedManga = null
        chapters = emptyList()
        message = ""
        if (destination == Screen.SOURCES || destination == Screen.SEARCH) {
            browseJob?.cancel()
            browseRequestId++
            source = null
            browseItems = emptyList()
            browseError = null
            browseLoading = false
        }
        if (destination == Screen.LIBRARY) refreshLibrary()
    }
    fun fetchBrowsePage(selectedSource: Source, search: String, page: Int, requestId: Int) {
        browseJob?.cancel()
        browseLoading = true
        browseError = null
        browseJob = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    if (search.isBlank()) {
                        selectedSource.getPopularManga(page)
                    } else {
                        selectedSource.getSearchManga(page, search, FilterList())
                    }
                }
                if (requestId != browseRequestId || source?.id != selectedSource.id) return@launch
                browseItems = (if (page == 1) result.mangas else browseItems + result.mangas).distinctBy(SManga::url)
                browsePage = page
                browseHasNext = result.hasNextPage
                message = ""
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (requestId == browseRequestId) browseError = failure.message ?: "Source failed"
            } finally {
                if (requestId == browseRequestId) browseLoading = false
            }
        }
    }
    fun browse(selectedSource: Source, search: String = "") {
        source = selectedSource
        selectedManga = null
        chapters = emptyList()
        browseItems = emptyList()
        browsePage = 0
        browseHasNext = false
        browseQuery = search
        browseRequestId++
        screen = if (screen == Screen.SEARCH) Screen.SEARCH else Screen.SOURCES
        fetchBrowsePage(selectedSource, search, 1, browseRequestId)
    }
    fun loadMangaDetails(selectedSource: Source, manga: SManga, chapterUrl: String? = null) {
        detailJob?.cancel()
        detailRequestId++
        val requestId = detailRequestId
        source = selectedSource
        selectedManga = manga
        selectedMangaCategories = emptySet()
        chapters = emptyList()
        detailsLoading = true
        detailsError = null
        message = ""
        detailJob = scope.launch {
            try {
                val update = withContext(Dispatchers.IO) {
                    selectedSource.getMangaUpdate(manga, emptyList(), true, true)
                }
                if (
                    requestId != detailRequestId || source?.id != selectedSource.id ||
                    selectedManga?.url != manga.url
                ) {
                    return@launch
                }
                selectedManga = update.manga
                chapters = update.chapters
                message = ""
                if (chapterUrl != null) {
                    if (update.chapters.none { it.url == chapterUrl }) {
                        notice.error("The saved chapter is no longer available from this source")
                    } else {
                        readerTarget = ReaderTarget(selectedSource, update.manga, update.chapters, chapterUrl)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (requestId == detailRequestId) {
                    detailsError = failure.message ?: "Details failed"
                }
            } finally {
                if (requestId == detailRequestId) detailsLoading = false
            }
        }
    }
    fun openStoredManga(mangaId: Long, chapterUrl: String? = null) {
        val stored = session.library.manga(mangaId) ?: run {
            notice.error("This manga is no longer in the library")
            return
        }
        val installedSource = sources.firstOrNull { it.id == stored.source }
        val manga = SManga.create().apply {
            url = stored.url
            title = stored.title
            description = stored.description
            thumbnail_url = stored.thumbnail_url
            artist = stored.artist
            author = stored.author
            genre = stored.genre?.joinToString(", ")
        }
        if (
            chapterUrl != null &&
            session.downloads.store.completedPages(stored.source, stored.url, chapterUrl) != null
        ) {
            val savedChapters = session.library.chapters(mangaId).map { chapter ->
                SChapter.create().apply {
                    url = chapter.url
                    name = chapter.name
                    chapter_number = chapter.chapter_number.toFloat()
                    date_upload = chapter.date_upload
                    scanlator = chapter.scanlator
                    memo = chapter.memo
                }
            }
            if (savedChapters.any { it.url == chapterUrl }) {
                val readingSource = installedSource ?: DownloadedSource(stored.source)
                source = readingSource
                readerTarget = ReaderTarget(readingSource, manga, savedChapters, chapterUrl)
                return
            }
        }
        if (installedSource == null) {
            notice.error("Install this manga’s source to browse its details or read undownloaded chapters")
            return
        }
        loadMangaDetails(installedSource, manga, chapterUrl)
    }
    fun queueStoredChapter(mangaId: Long, chapterUrl: String) {
        val stored = session.library.manga(mangaId) ?: run {
            notice.error("This manga is no longer in the library")
            return
        }
        val installedSource = sources.firstOrNull { it.id == stored.source } ?: run {
            notice.error("Install this manga’s source before downloading chapters")
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
            }.onFailure { notice.error(it.message ?: "Could not queue chapter") }
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
                            }.onFailure { notice.error(it.message ?: "Could not load chapters") }
                        }
                    }
                    null -> notice.error("No installed source recognizes this link")
                }
            }.onFailure { notice.error(it.message ?: "Could not open link") }
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
            }.onFailure { notice.error(it.message ?: "$tracker sign-in failed") }
        }
    }

    LaunchedEffect(session) { refreshLibrary() }

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
    LaunchedEffect(historyEntries) {
        historyChapters = withContext(Dispatchers.IO) {
            historyEntries.map { it.chapterId }.distinct().associateWith(session.library::chapter)
        }
    }
    LaunchedEffect(selectedManga?.url, source?.id, message) {
        val activeManga = selectedManga
        val activeSource = source
        if (activeManga == null || activeSource == null) {
            detailSnapshot = null
        } else {
            val (snapshot, memberships) = withContext(Dispatchers.IO) {
                val stored = session.library.find(activeSource.id, activeManga.url)
                val tracks = stored?.let { session.library.tracks(it._id).associateBy(Manga_sync::sync_id) }
                    ?: emptyMap()
                DetailSnapshot(activeSource.id, activeManga.url, stored, tracks) to
                    (stored?.let { session.library.mangaCategories(it._id) } ?: emptySet())
            }
            detailSnapshot = snapshot
            selectedMangaCategories = memberships
        }
    }
    LaunchedEffect(downloads.map(DesktopDownload::key)) {
        downloadMangas = withContext(Dispatchers.IO) {
            downloads.associate { item -> item.key to session.library.find(item.sourceId, item.mangaUrl) }
        }
    }

    val density = LocalDensity.current
    var compactNavigation by remember { mutableStateOf(false) }
    MihonDesktopTheme {
        Surface(
            modifier = Modifier.fillMaxSize().onSizeChanged { size ->
                compactNavigation = with(density) { size.width.toDp() < 900.dp }
            },
            color = MihonPalette.graphite,
        ) {
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
                    navigateToScreen(Screen.entries[index])
                    true
                }.focusRequester(focusRequester).focusable(),
            ) {
                if (readerTarget == null) {
                    DesktopNavigation(screen, compactNavigation, ::navigateToScreen)
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight()
                        .padding(
                            start = if (readerTarget != null) {
                                8.dp
                            } else if (compactNavigation) {
                                12.dp
                            } else {
                                24.dp
                            },
                            top = if (readerTarget != null) {
                                8.dp
                            } else if (compactNavigation) {
                                12.dp
                            } else {
                                24.dp
                            },
                            end = if (readerTarget != null) {
                                8.dp
                            } else if (compactNavigation) {
                                24.dp
                            } else {
                                48.dp
                            },
                            bottom = if (readerTarget != null) {
                                8.dp
                            } else if (compactNavigation) {
                                12.dp
                            } else {
                                24.dp
                            },
                        ),
                ) {
                    if (message.isNotBlank()) {
                        MihonPanel(Modifier.fillMaxWidth()) {
                            Text(
                                message,
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                                color = if (notice.isError) MaterialTheme.colorScheme.error else MihonPalette.muted,
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
                            val snapshot = detailSnapshot?.takeIf {
                                it.sourceId == selectedSource?.id && it.mangaUrl == item.url
                            }
                            val storedManga = snapshot?.stored
                            LazyColumn(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                item {
                                    Column(
                                        modifier = Modifier.widthIn(max = 1280.dp).fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        val stored = snapshot?.stored
                                        val tracks = snapshot?.tracks.orEmpty()
                                        val komgaTrack = tracks[DesktopKomgaTracker.TRACKER_ID]
                                        val aniListTrack = tracks[DesktopAniListTracker.TRACKER_ID]
                                        val kavitaTrack = tracks[DesktopKavitaTracker.TRACKER_ID]
                                        val mangaUpdatesTrack = tracks[DesktopMangaUpdatesTracker.TRACKER_ID]
                                        val kitsuTrack = tracks[DesktopKitsuTracker.TRACKER_ID]
                                        val malTrack = tracks[DesktopMyAnimeListTracker.TRACKER_ID]
                                        val shikimoriTrack = tracks[DesktopShikimoriTracker.TRACKER_ID]
                                        val hikkaTrack = tracks[DesktopHikkaTracker.TRACKER_ID]
                                        val bangumiTrack = tracks[DesktopBangumiTracker.TRACKER_ID]
                                        val mangaBakaTrack = tracks[DesktopMangaBakaTracker.TRACKER_ID]
                                        val suwayomiTrack = tracks[DesktopSuwayomiTracker.TRACKER_ID]
                                        var trackingExpanded by remember(item.url) { mutableStateOf(false) }
                                        val linkedTrackers = buildList {
                                            if (komgaTrack != null) add("Komga")
                                            if (kavitaTrack != null) add("Kavita")
                                            if (aniListTrack != null) add("AniList")
                                            if (mangaUpdatesTrack != null) add("MangaUpdates")
                                            if (kitsuTrack != null) add("Kitsu")
                                            if (malTrack != null) add("MyAnimeList")
                                            if (shikimoriTrack != null) add("Shikimori")
                                            if (hikkaTrack != null) add("Hikka")
                                            if (bangumiTrack != null) add("Bangumi")
                                            if (mangaBakaTrack != null) add("MangaBaka")
                                            if (suwayomiTrack != null) add("Suwayomi")
                                        }
                                        val recentChapter = stored?.let { manga ->
                                            historyEntries.firstOrNull { it.mangaId == manga._id }
                                                ?.let { historyChapters[it.chapterId] }
                                        }
                                        val continueChapter = recentChapter?.let { recent ->
                                            chapters.firstOrNull { it.url == recent.url }
                                        } ?: chapters.firstOrNull()
                                        TextButton(onClick = { selectedManga = null }) {
                                            Text("← Back")
                                        }
                                        BoxWithConstraints(
                                            Modifier.fillMaxWidth().padding(vertical = MihonSpacing.sm),
                                        ) {
                                                val compactDetails = maxWidth < 820.dp
                                                val identity: @Composable () -> Unit = {
                                                    Row(
                                                        Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(MihonSpacing.lg),
                                                        verticalAlignment = Alignment.Top,
                                                    ) {
                                                        DesktopCover(
                                                            item.thumbnail_url,
                                                            selectedSource,
                                                            Modifier.width(208.dp).height(300.dp),
                                                        )
                                                        Column(
                                                            Modifier.weight(1f),
                                                            verticalArrangement = Arrangement.spacedBy(MihonSpacing.sm),
                                                        ) {
                                                            Text(
                                                                item.title,
                                                                style = MaterialTheme.typography.headlineLarge,
                                                            )
                                                            listOfNotNull(item.author, item.artist)
                                                                .filter(String::isNotBlank)
                                                                .distinct()
                                                                .takeIf { it.isNotEmpty() }
                                                                ?.let {
                                                                    Text(
                                                                        it.joinToString(" · "),
                                                                        color = MihonPalette.muted,
                                                                    )
                                                                }
                                                            Text(
                                                                item.description.orEmpty().ifBlank {
                                                                    "No description available."
                                                                },
                                                                color = MihonPalette.muted,
                                                                maxLines = 7,
                                                            )
                                                            Button(
                                                                onClick = {
                                                                    if (
                                                                        selectedSource != null &&
                                                                        continueChapter != null
                                                                    ) {
                                                                        readerTarget = ReaderTarget(
                                                                            selectedSource,
                                                                            item,
                                                                            chapters,
                                                                            continueChapter.url,
                                                                        )
                                                                    }
                                                                },
                                                                enabled =
                                                                selectedSource != null &&
                                                                    continueChapter != null,
                                                                modifier = Modifier.widthIn(min = 188.dp),
                                                            ) {
                                                                Text(
                                                                    if (recentChapter != null) {
                                                                        "▶ Continue reading"
                                                                    } else {
                                                                        "▶ Start reading"
                                                                    },
                                                                )
                                                            }
                                                            if (recentChapter != null) {
                                                                Text(
                                                                    if (recentChapter.read) {
                                                                        "Last chapter finished"
                                                                    } else {
                                                                        "Continue from page " +
                                                                            "${recentChapter.last_page_read + 1}"
                                                                    },
                                                                    color = MihonPalette.sage,
                                                                    style = MaterialTheme.typography.labelMedium,
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                val statusPanel: @Composable () -> Unit = {
                                                    Surface(
                                                        color = MihonPalette.raised.copy(alpha = 0.44f),
                                                        shape = RoundedCornerShape(MihonRadius.card),
                                                    ) {
                                                        Column(
                                                            Modifier.fillMaxWidth().padding(MihonSpacing.md),
                                                            verticalArrangement =
                                                            Arrangement.spacedBy(MihonSpacing.md),
                                                        ) {
                                                            Row(
                                                                Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically,
                                                            ) {
                                                                MihonCompactChip(
                                                                    label = if (stored?.favorite == true) {
                                                                        "In library"
                                                                    } else {
                                                                        "Not in library"
                                                                    },
                                                                    accent = stored?.favorite == true,
                                                                )
                                                                TextButton(
                                                                    onClick = {
                                                                        if (
                                                                            stored == null &&
                                                                            selectedSource != null
                                                                        ) {
                                                                            session.library.addToLibrary(
                                                                                selectedSource.id,
                                                                                item,
                                                                            )
                                                                        } else if (stored != null) {
                                                                            session.library.setFavorite(
                                                                                stored._id,
                                                                                !stored.favorite,
                                                                            )
                                                                        }
                                                                        refreshLibrary()
                                                                        message = "Library updated"
                                                                    },
                                                                    enabled = snapshot != null,
                                                                ) {
                                                                    Text(
                                                                        if (stored?.favorite == true) {
                                                                            "Remove"
                                                                        } else {
                                                                            "Add"
                                                                        },
                                                                    )
                                                                }
                                                            }
                                                            Column(
                                                                verticalArrangement =
                                                                Arrangement.spacedBy(MihonSpacing.xs),
                                                            ) {
                                                                Text(
                                                                    "SOURCE",
                                                                    color = MihonPalette.muted,
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                )
                                                                Text(
                                                                    selectedSource?.displayName() ?: "Unknown",
                                                                    style = MaterialTheme.typography.bodyMedium,
                                                                )
                                                            }
                                                            Column(
                                                                verticalArrangement =
                                                                Arrangement.spacedBy(MihonSpacing.xs),
                                                            ) {
                                                                Text(
                                                                    "TRACKING",
                                                                    color = MihonPalette.muted,
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                )
                                                                Text(
                                                                    if (linkedTrackers.isEmpty()) {
                                                                        "No trackers linked"
                                                                    } else {
                                                                        linkedTrackers.joinToString(" · ")
                                                                    },
                                                                    color = if (linkedTrackers.isEmpty()) {
                                                                        MihonPalette.muted
                                                                    } else {
                                                                        MihonPalette.ivory
                                                                    },
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                )
                                                                TextButton(
                                                                    onClick = {
                                                                        trackingExpanded = !trackingExpanded
                                                                    },
                                                                    enabled = stored != null,
                                                                ) {
                                                                    Text(
                                                                        if (trackingExpanded) {
                                                                            "Close tracking"
                                                                        } else {
                                                                            "Manage tracking"
                                                                        },
                                                                    )
                                                                }
                                                            }
                                                            Column(
                                                                verticalArrangement =
                                                                Arrangement.spacedBy(MihonSpacing.xs),
                                                            ) {
                                                                Text(
                                                                    "CATEGORIES",
                                                                    color = MihonPalette.muted,
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                )
                                                                Text(
                                                                    if (selectedMangaCategories.isEmpty()) {
                                                                        "Uncategorized"
                                                                    } else {
                                                                        "${selectedMangaCategories.size} assigned"
                                                                    },
                                                                    style = MaterialTheme.typography.bodyMedium,
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                if (compactDetails) {
                                                    Column(
                                                        verticalArrangement = Arrangement.spacedBy(MihonSpacing.md),
                                                    ) {
                                                        identity()
                                                        statusPanel()
                                                    }
                                                } else {
                                                    Row(
                                                        Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(MihonSpacing.lg),
                                                        verticalAlignment = Alignment.Top,
                                                    ) {
                                                        Box(Modifier.weight(1f)) { identity() }
                                                        Box(Modifier.width(288.dp)) { statusPanel() }
                                                    }
                                                }
                                            }
                                        FlowRow(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
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
                                                    }.onFailure { notice.error(it.message ?: "Could not open browser") }
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
                                                        .onFailure { notice.error(it.message ?: "Could not copy link") }
                                                }) { Text("Copy link") }
                                            }
                                            if (
                                                trackingExpanded && stored != null &&
                                                item.url.contains("/api/v1/series/")
                                            ) {
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
                                                        }.onFailure {
                                                            notice.error(it.message ?: "Komga tracking failed")
                                                        }
                                                    }
                                                }) { Text(if (komgaTrack == null) "Link Komga" else "Sync Komga") }
                                                trackerSyncQueue.firstOrNull { it.mangaId == stored._id }?.error?.let {
                                                    Text(it, color = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                        if (trackingExpanded && stored != null && item.url.contains("/api/Series/")) {
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
                                                                    notice.error(it.message ?: "Kavita link failed")
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
                                        if (trackingExpanded && stored != null && aniListLoggedIn) {
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
                                                                notice.error(it.message ?: "AniList link failed")
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
                                        if (trackingExpanded && stored != null && mangaUpdatesLoggedIn) {
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
                                                                notice.error(it.message ?: "MangaUpdates link failed")
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
                                        if (trackingExpanded && stored != null && kitsuLoggedIn) {
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
                                                            }.onFailure {
                                                                notice.error(it.message ?: "Kitsu link failed")
                                                            }
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
                                        if (trackingExpanded && stored != null && malLoggedIn) {
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
                                                                notice.error(it.message ?: "MyAnimeList link failed")
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
                                        if (trackingExpanded && stored != null && shikimoriLoggedIn) {
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
                                                                notice.error(it.message ?: "Shikimori link failed")
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
                                        if (trackingExpanded && stored != null && hikkaLoggedIn) {
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
                                                                    notice.error(it.message ?: "Hikka link failed")
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
                                        if (trackingExpanded && stored != null && bangumiLoggedIn) {
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
                                                                notice.error(it.message ?: "Bangumi link failed")
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
                                        if (trackingExpanded && stored != null && mangaBakaLoggedIn) {
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
                                                                notice.error(it.message ?: "MangaBaka link failed")
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
                                        if (trackingExpanded && stored != null && selectedSource != null &&
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
                                                    }.onFailure {
                                                        notice.error(it.message ?: "Suwayomi tracking failed")
                                                    }
                                                }
                                            }) { Text(if (suwayomiTrack == null) "Link Suwayomi" else "Sync Suwayomi") }
                                        }
                                    }
                                }
                                item {
                                    Box(
                                        modifier = Modifier.widthIn(max = 1280.dp).fillMaxWidth(),
                                    ) {
                                        MihonSectionHeader(
                                            "Chapters",
                                            "${chapters.size} chapters available",
                                        )
                                    }
                                }
                                if (chapters.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier.widthIn(max = 1280.dp).fillMaxWidth(),
                                        ) {
                                            Column {
                                                MihonEmptyState(
                                                    when {
                                                        detailsLoading -> "Loading chapters…"
                                                        detailsError != null -> "Could not load chapters"
                                                        else -> "No chapters available"
                                                    },
                                                    detailsError ?: if (detailsLoading) {
                                                        null
                                                    } else {
                                                        "Try refreshing details from the source."
                                                    },
                                                )
                                                if (detailsError != null && selectedSource != null) {
                                                    Button(
                                                        onClick = {
                                                            loadMangaDetails(selectedSource, item)
                                                        },
                                                    ) {
                                                        Text("Retry")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                items(chapters, key = SChapter::url) { chapter ->
                                    val queued = downloads.firstOrNull {
                                        it.sourceId == selectedSource?.id &&
                                            it.mangaUrl == item.url &&
                                            it.chapterUrl == chapter.url
                                    }
                                    Column(
                                        modifier = Modifier.widthIn(max = 1280.dp).fillMaxWidth(),
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth()
                                                .padding(horizontal = MihonSpacing.xs),
                                            horizontalArrangement =
                                            Arrangement.spacedBy(MihonSpacing.md),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(
                                                modifier = Modifier.weight(1f)
                                                    .clickable {
                                                        if (selectedSource != null) {
                                                            readerTarget = ReaderTarget(
                                                                selectedSource,
                                                                item,
                                                                chapters,
                                                                chapter.url,
                                                            )
                                                        }
                                                    }
                                                    .padding(
                                                        horizontal = MihonSpacing.sm,
                                                        vertical = MihonSpacing.md,
                                                    ),
                                                verticalArrangement =
                                                Arrangement.spacedBy(MihonSpacing.xs),
                                            ) {
                                                Text(
                                                    chapter.name,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                )
                                                chapter.scanlator
                                                    ?.takeIf(String::isNotBlank)
                                                    ?.let { scanlator ->
                                                        Text(
                                                            scanlator,
                                                            color = MihonPalette.muted,
                                                            style =
                                                            MaterialTheme.typography.bodySmall,
                                                        )
                                                    }
                                            }
                                            if (
                                                selectedSource != null &&
                                                selectedSource !is DesktopLocalSource
                                            ) {
                                                when (queued?.status) {
                                                    DesktopDownloadStatus.COMPLETED ->
                                                        MihonCompactChip(
                                                            "Downloaded",
                                                            accent = true,
                                                        )
                                                    DesktopDownloadStatus.RUNNING ->
                                                        MihonCompactChip(
                                                            "${queued.pagesDone}/${queued.pageCount}",
                                                            accent = true,
                                                        )
                                                    DesktopDownloadStatus.PENDING ->
                                                        MihonCompactChip("Queued")
                                                    DesktopDownloadStatus.PAUSED,
                                                    DesktopDownloadStatus.FAILED,
                                                    null,
                                                    -> TextButton(
                                                        onClick = {
                                                            when (queued?.status) {
                                                                DesktopDownloadStatus.PAUSED,
                                                                DesktopDownloadStatus.FAILED,
                                                                ->
                                                                    session.downloads.resume(queued.key)
                                                                null -> session.downloads.enqueue(
                                                                    selectedSource,
                                                                    item,
                                                                    chapter,
                                                                )
                                                                else -> Unit
                                                            }
                                                        },
                                                    ) {
                                                        Text(
                                                            when (queued?.status) {
                                                                DesktopDownloadStatus.PAUSED ->
                                                                    "Resume"
                                                                DesktopDownloadStatus.FAILED ->
                                                                    "Retry"
                                                                else -> "Download"
                                                            },
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        HorizontalDivider(color = MihonPalette.outlineSoft)
                                    }
                                }
                                if (storedManga?.favorite == true) {
                                    item {
                                        MihonPanel(
                                            Modifier.widthIn(max = 1280.dp).fillMaxWidth(),
                                        ) {
                                            Column(
                                                Modifier.fillMaxWidth().padding(MihonSpacing.md),
                                                verticalArrangement = Arrangement.spacedBy(MihonSpacing.xs),
                                            ) {
                                                Text("Categories", style = MaterialTheme.typography.titleMedium)
                                                categories.forEach { category ->
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Checkbox(
                                                            checked = category.id in selectedMangaCategories,
                                                            onCheckedChange = { checked ->
                                                                val updated = if (checked) {
                                                                    selectedMangaCategories + category.id
                                                                } else {
                                                                    selectedMangaCategories - category.id
                                                                }
                                                                session.library.setMangaCategories(
                                                                    storedManga._id,
                                                                    updated,
                                                                )
                                                                selectedMangaCategories = updated
                                                                message = "Categories updated"
                                                            },
                                                        )
                                                        Text(category.name.ifBlank { "Uncategorized" })
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        screen == Screen.LIBRARY -> {
                            LibraryScreen(
                                library = library,
                                membership = libraryMembership,
                                historyEntries = historyEntries,
                                historyChapters = historyChapters,
                                sources = sources,
                                selectedCategory = selectedCategory,
                                onClearCategory = { selectedCategory = null },
                                search = librarySearch,
                                onSearchChange = { librarySearch = it },
                                loading = libraryLoading,
                                onOpenManga = ::openStoredManga,
                            )
                        }
                        screen == Screen.SEARCH -> {
                            SearchScreen(
                                sources = sources,
                                selectedSource = source,
                                query = query,
                                onQueryChange = { query = it },
                                results = browseItems,
                                loading = browseLoading,
                                error = browseError,
                                hasNext = browseHasNext,
                                link = link,
                                showLinkTools = showLinkTools,
                                onLinkChange = { link = it },
                                onToggleLinkTools = { showLinkTools = !showLinkTools },
                                onOpenLink = { openLink(link) },
                                onPasteLink = {
                                    graph.clipboardService.readText()?.let { pasted ->
                                        link = pasted
                                        openLink(pasted)
                                    }
                                },
                                onSelectSource = { selected -> browse(selected) },
                                onSearch = { source?.let { browse(it, query) } },
                                onOpenManga = { manga ->
                                    source?.let { selected -> loadMangaDetails(selected, manga) }
                                },
                                onLoadMore = {
                                    source?.let { selected ->
                                        fetchBrowsePage(
                                            selected,
                                            browseQuery,
                                            browsePage + 1,
                                            browseRequestId,
                                        )
                                    }
                                },
                            )
                        }
                        screen == Screen.SOURCES -> {
                            if (source == null) {
                                MihonSectionHeader(
                                    "Sources",
                                    "Choose an installed source and language to browse",
                                    trailing = {
                                        OutlinedTextField(
                                            sourceSearch,
                                            { sourceSearch = it },
                                            label = { Text("Search sources…") },
                                            singleLine = true,
                                            modifier = Modifier.width(280.dp),
                                        )
                                    },
                                )
                                val visibleSources = sources.filter {
                                    it.name.contains(sourceSearch, ignoreCase = true) ||
                                        it.lang.contains(sourceSearch, ignoreCase = true)
                                }
                                val groupedSources = visibleSources
                                    .groupBy { available ->
                                        if (available is DesktopLocalSource) {
                                            "local:${available.id}"
                                        } else {
                                            available.name.lowercase()
                                        }
                                    }
                                    .values
                                    .toList()
                                when {
                                    sources.isEmpty() -> MihonEmptyState(
                                        "No sources available",
                                        "Install an extension or use the local source.",
                                    )
                                    visibleSources.isEmpty() -> MihonEmptyState(
                                        "No sources found",
                                        "Try another source name or language.",
                                    )
                                    else -> Box(
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        contentAlignment = Alignment.TopCenter,
                                    ) {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxHeight().widthIn(max = 1180.dp),
                                            verticalArrangement = Arrangement.spacedBy(MihonSpacing.sm),
                                        ) {
                                            items(
                                                groupedSources,
                                                key = { variants ->
                                                    variants.joinToString("|") { it.id.toString() }
                                                },
                                            ) { variants ->
                                                val primary = variants.first()
                                                Surface(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(MihonRadius.card),
                                                    color = MihonPalette.panel,
                                                    border = BorderStroke(1.dp, MihonPalette.outlineSoft),
                                                ) {
                                                    Row(
                                                        Modifier.fillMaxWidth().padding(
                                                            horizontal = MihonSpacing.md,
                                                            vertical = MihonSpacing.sm,
                                                        ),
                                                        horizontalArrangement =
                                                        Arrangement.spacedBy(MihonSpacing.md),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Surface(
                                                            shape = RoundedCornerShape(999.dp),
                                                            color = MihonPalette.raised,
                                                            border = BorderStroke(
                                                                1.dp,
                                                                MihonPalette.outlineSoft,
                                                            ),
                                                        ) {
                                                            Box(
                                                                Modifier.width(38.dp).height(38.dp),
                                                                contentAlignment = Alignment.Center,
                                                            ) {
                                                                Text(
                                                                    primary.name.firstOrNull()?.uppercase() ?: "?",
                                                                    color = MihonPalette.sage,
                                                                    style = MaterialTheme.typography.titleMedium,
                                                                )
                                                            }
                                                        }
                                                        Column(
                                                            Modifier.weight(1f),
                                                            verticalArrangement =
                                                            Arrangement.spacedBy(MihonSpacing.xs),
                                                        ) {
                                                            Text(
                                                                primary.name,
                                                                style = MaterialTheme.typography.titleMedium,
                                                            )
                                                            Text(
                                                                when {
                                                                    primary is DesktopLocalSource ->
                                                                        "Local files · On this device"
                                                                    variants.size == 1 ->
                                                                        "${primary.lang.uppercase()} · Installed"
                                                                    else ->
                                                                        "${variants.size} language variants installed"
                                                                },
                                                                color = MihonPalette.muted,
                                                                style = MaterialTheme.typography.bodySmall,
                                                            )
                                                            if (
                                                                variants.size > 1 &&
                                                                primary !is DesktopLocalSource
                                                            ) {
                                                                FlowRow(
                                                                    horizontalArrangement =
                                                                    Arrangement.spacedBy(MihonSpacing.xs),
                                                                    verticalArrangement =
                                                                    Arrangement.spacedBy(MihonSpacing.xs),
                                                                ) {
                                                                    variants
                                                                        .sortedBy { it.lang }
                                                                        .forEach { variant ->
                                                                            MihonCompactChip(
                                                                                label =
                                                                                variant.lang.uppercase(),
                                                                                onClick = {
                                                                                    browse(variant)
                                                                                },
                                                                            )
                                                                        }
                                                                }
                                                            }
                                                        }
                                                        if (
                                                            variants.size == 1 ||
                                                            primary is DesktopLocalSource
                                                        ) {
                                                            TextButton(onClick = { browse(primary) }) {
                                                                Text("Browse →")
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                val activeSource = requireNotNull(source)
                                MihonSectionHeader(
                                    activeSource.name,
                                    if (activeSource is DesktopLocalSource) {
                                        "Local · Manga stored on this device"
                                    } else {
                                        "${activeSource.lang.uppercase()} · Installed source"
                                    },
                                    trailing = {
                                        TextButton(onClick = {
                                            source = null
                                            browseItems = emptyList()
                                        }) { Text("← All sources") }
                                    },
                                )
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().widthIn(max = 1180.dp),
                                        horizontalArrangement = Arrangement.spacedBy(MihonSpacing.sm),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        OutlinedTextField(
                                            query,
                                            { query = it },
                                            label = { Text("Search ${activeSource.name}") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Button(onClick = { browse(activeSource, query) }) {
                                            Text("Search")
                                        }
                                    }
                                }
                                when {
                                    browseLoading && browseItems.isEmpty() ->
                                        MihonEmptyState("Loading manga…", activeSource.name)
                                    browseItems.isEmpty() && browseError == null ->
                                        MihonEmptyState(
                                            "No manga to show",
                                            "Search this source or return to the source list.",
                                        )
                                    else -> LazyVerticalGrid(
                                        columns = GridCells.Adaptive(390.dp),
                                        modifier = Modifier.weight(1f).padding(top = MihonSpacing.md),
                                        horizontalArrangement = Arrangement.spacedBy(MihonSpacing.md),
                                        verticalArrangement = Arrangement.spacedBy(MihonSpacing.md),
                                    ) {
                                        gridItems(browseItems, key = SManga::url) { item ->
                                            MihonMangaResultCard(
                                                manga = item,
                                                source = activeSource,
                                                onClick = {
                                                    loadMangaDetails(activeSource, item)
                                                },
                                            )
                                        }
                                        if (browseLoading || browseError != null || browseHasNext) {
                                            item(span = { GridItemSpan(maxLineSpan) }) {
                                                Row(
                                                    Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    browseError?.let {
                                                        Text(
                                                            it,
                                                            color = MaterialTheme.colorScheme.error,
                                                        )
                                                    }
                                                    if (browseLoading) {
                                                        Text("Loading…", color = MihonPalette.muted)
                                                    } else {
                                                        TextButton(onClick = {
                                                            fetchBrowsePage(
                                                                activeSource,
                                                                browseQuery,
                                                                browsePage + 1,
                                                                browseRequestId,
                                                            )
                                                        }) {
                                                            Text(
                                                                if (browseError == null) {
                                                                    "Load more"
                                                                } else {
                                                                    "Retry"
                                                                },
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        screen == Screen.EXTENSIONS -> {
                            Column(Modifier.fillMaxSize()) {
                                MihonSectionHeader(
                                    "Extensions",
                                    "Discover, install, and manage manga sources",
                                )
                                MihonTabStrip(
                                    labels = listOf("Installed", "Browse", "Advanced"),
                                    selectedIndex = extensionSection,
                                    onSelect = { extensionSection = it },
                                    modifier = Modifier.widthIn(max = 1120.dp)
                                        .align(Alignment.CenterHorizontally)
                                        .padding(bottom = MihonSpacing.md),
                                )
                                Column(
                                    Modifier.weight(1f)
                                        .widthIn(max = 1120.dp)
                                        .align(Alignment.CenterHorizontally)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(MihonSpacing.md),
                                ) {
                                    when (extensionSection) {
                                        0 -> {
                                            val installedKeiyoushi = suwayomiExtensions
                                                .filter { it.installed && !it.obsolete }
                                                .sortedBy { it.name }
                                            MihonSectionHeader(
                                                "Installed sources",
                                                "${installedKeiyoushi.size + installedDesktopExtensions.size} " +
                                                    "extensions installed",
                                            )
                                            if (installedKeiyoushi.isEmpty() && installedDesktopExtensions.isEmpty()) {
                                                MihonEmptyState(
                                                    "No extensions installed",
                                                    "Open Browse to install a source from Keiyoushi.",
                                                )
                                            }
                                            installedKeiyoushi.forEach { entry ->
                                                Surface(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(MihonRadius.card),
                                                    color = MihonPalette.panel,
                                                    border = BorderStroke(1.dp, MihonPalette.outlineSoft),
                                                ) {
                                                    Row(
                                                        Modifier.fillMaxWidth().padding(
                                                            horizontal = MihonSpacing.md,
                                                            vertical = MihonSpacing.sm,
                                                        ),
                                                        horizontalArrangement = Arrangement.spacedBy(MihonSpacing.md),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Surface(
                                                            shape = RoundedCornerShape(999.dp),
                                                            color = MihonPalette.raised,
                                                        ) {
                                                            Box(
                                                                Modifier.width(38.dp).height(38.dp),
                                                                contentAlignment = Alignment.Center,
                                                            ) {
                                                                Text(
                                                                    entry.name.firstOrNull()?.uppercase() ?: "?",
                                                                    color = MihonPalette.sage,
                                                                )
                                                            }
                                                        }
                                                        Column(Modifier.weight(1f)) {
                                                            Text(
                                                                entry.name,
                                                                style = MaterialTheme.typography.titleMedium,
                                                            )
                                                            Text(
                                                                "${entry.versionName} · Keiyoushi",
                                                                color = MihonPalette.muted,
                                                                style = MaterialTheme.typography.bodySmall,
                                                            )
                                                        }
                                                        if (entry.hasUpdate) {
                                                            MihonCompactChip(
                                                                "Update available",
                                                                accent = true,
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
                                                                            if (entry.hasUpdate) {
                                                                                "update"
                                                                            } else {
                                                                                "uninstall"
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
                                                                    suwayomiStatus =
                                                                        it.message ?: "Extension action failed"
                                                                }
                                                            }
                                                        }) {
                                                            Text(if (entry.hasUpdate) "Update" else "Remove")
                                                        }
                                                    }
                                                }
                                            }
                                            installedDesktopExtensions.forEach { installed ->
                                                Surface(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(MihonRadius.card),
                                                    color = MihonPalette.panel,
                                                    border = BorderStroke(1.dp, MihonPalette.outlineSoft),
                                                ) {
                                                    Row(
                                                        Modifier.fillMaxWidth().padding(
                                                            horizontal = MihonSpacing.md,
                                                            vertical = MihonSpacing.sm,
                                                        ),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Column(Modifier.weight(1f)) {
                                                            Text(
                                                                installed.id,
                                                                style = MaterialTheme.typography.titleMedium,
                                                            )
                                                            Text(
                                                                "Version ${installed.versionCode} · Desktop .mihonext",
                                                                color = MihonPalette.muted,
                                                                style = MaterialTheme.typography.bodySmall,
                                                            )
                                                        }
                                                        MihonCompactChip(
                                                            "Locally trusted",
                                                            accent = true,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        1 -> {
                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(MihonRadius.card),
                                                color = MihonPalette.panel,
                                                border = BorderStroke(1.dp, MihonPalette.outlineSoft),
                                            ) {
                                                Row(
                                                    Modifier.fillMaxWidth().padding(
                                                        horizontal = MihonSpacing.md,
                                                        vertical = MihonSpacing.sm,
                                                    ),
                                                    horizontalArrangement = Arrangement.spacedBy(MihonSpacing.md),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text(
                                                            "Keiyoushi catalog",
                                                            style = MaterialTheme.typography.titleMedium,
                                                        )
                                                        Text(
                                                            "Community sources through the isolated Suwayomi engine.",
                                                            color = MihonPalette.muted,
                                                        )
                                                        Text(
                                                            suwayomiStatus,
                                                            color = if (session.suwayomiEngine.isRunning) {
                                                                MihonPalette.sage
                                                            } else {
                                                                MaterialTheme.colorScheme.error
                                                            },
                                                            style = MaterialTheme.typography.bodySmall,
                                                        )
                                                    }
                                                    Button(
                                                        onClick = {
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
                                                                    suwayomiStatus =
                                                                        it.message ?: "Could not refresh extensions"
                                                                }
                                                            }
                                                        },
                                                        enabled = session.suwayomiEngine.isRunning,
                                                    ) { Text("Refresh catalog") }
                                                }
                                            }
                                            OutlinedTextField(
                                                suwayomiSearch,
                                                { suwayomiSearch = it },
                                                label = { Text("Search extensions…") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth(),
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
                                                .take(80)
                                            if (suwayomiExtensions.isEmpty()) {
                                                MihonEmptyState(
                                                    "Catalog is not loaded",
                                                    "Refresh the catalog to browse compatible sources.",
                                                )
                                            } else if (visible.isEmpty()) {
                                                MihonEmptyState("No matching extensions", "Try a different name.")
                                            }
                                            visible.forEach { entry ->
                                                Surface(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(MihonRadius.card),
                                                    color = MihonPalette.panel,
                                                    border = BorderStroke(1.dp, MihonPalette.outlineSoft),
                                                ) {
                                                    Row(
                                                        Modifier.fillMaxWidth().padding(
                                                            horizontal = MihonSpacing.md,
                                                            vertical = MihonSpacing.sm,
                                                        ),
                                                        horizontalArrangement = Arrangement.spacedBy(MihonSpacing.md),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Surface(
                                                            shape = RoundedCornerShape(999.dp),
                                                            color = MihonPalette.raised,
                                                        ) {
                                                            Box(
                                                                Modifier.width(38.dp).height(38.dp),
                                                                contentAlignment = Alignment.Center,
                                                            ) {
                                                                Text(
                                                                    entry.name.firstOrNull()?.uppercase() ?: "?",
                                                                    color = MihonPalette.sage,
                                                                )
                                                            }
                                                        }
                                                        Column(Modifier.weight(1f)) {
                                                            Text(
                                                                entry.name,
                                                                style = MaterialTheme.typography.titleMedium,
                                                            )
                                                            Text(
                                                                "${entry.versionName} · ${entry.contentWarning}",
                                                                color = MihonPalette.muted,
                                                                style = MaterialTheme.typography.bodySmall,
                                                            )
                                                        }
                                                        MihonCompactChip(
                                                            label = when {
                                                                entry.installed && entry.hasUpdate ->
                                                                    "Update available"
                                                                entry.installed -> "Installed"
                                                                else -> "Available"
                                                            },
                                                            accent = entry.installed || entry.hasUpdate,
                                                        )
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
                                                                    suwayomiStatus =
                                                                        it.message ?: "Extension action failed"
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
                                        else -> {
                                            MihonSectionHeader(
                                                "Advanced extension management",
                                                "Signed Desktop packages, repository indexes and local " +
                                                    "fingerprint trust",
                                            )
                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(MihonRadius.card),
                                                color = MihonPalette.panel,
                                                border = BorderStroke(1.dp, MihonPalette.outlineSoft),
                                            ) {
                                                Column(
                                                    Modifier.fillMaxWidth().padding(MihonSpacing.lg),
                                                    verticalArrangement = Arrangement.spacedBy(MihonSpacing.sm),
                                                ) {
                                                    Text(
                                                        "Local trust",
                                                        style = MaterialTheme.typography.titleMedium,
                                                    )
                                                    Text(
                                                        "Trusting a fingerprint is a local decision and does not " +
                                                            "verify publisher identity.",
                                                        color = MihonPalette.muted,
                                                    )
                                                    OutlinedTextField(
                                                        fingerprint,
                                                        { fingerprint = it },
                                                        label = { Text("Signing fingerprint") },
                                                        modifier = Modifier.fillMaxWidth(),
                                                    )
                                                    Button(onClick = {
                                                        runCatching {
                                                            session.extensions.trust(fingerprint.trim())
                                                        }.onSuccess {
                                                            message = "Fingerprint trusted"
                                                        }.onFailure {
                                                            notice.error(it.message ?: "Trust failed")
                                                        }
                                                    }, enabled = fingerprint.isNotBlank()) {
                                                        Text("Trust fingerprint")
                                                    }
                                                }
                                            }
                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(MihonRadius.card),
                                                color = MihonPalette.panel,
                                                border = BorderStroke(1.dp, MihonPalette.outlineSoft),
                                            ) {
                                                Column(
                                                    Modifier.fillMaxWidth().padding(MihonSpacing.lg),
                                                    verticalArrangement = Arrangement.spacedBy(MihonSpacing.sm),
                                                ) {
                                                    Text(
                                                        "Install .mihonext package",
                                                        style = MaterialTheme.typography.titleMedium,
                                                    )
                                                    OutlinedTextField(
                                                        packagePath,
                                                        { packagePath = it },
                                                        label = { Text(".mihonext path") },
                                                        modifier = Modifier.fillMaxWidth(),
                                                    )
                                                    Row(horizontalArrangement = Arrangement.spacedBy(MihonSpacing.sm)) {
                                                        TextButton(onClick = {
                                                            graph.fileDialogService.chooseOpenFile(
                                                                OpenFileRequest(
                                                                    "Install Desktop extension",
                                                                    extensions = setOf("mihonext"),
                                                                ),
                                                            )?.let { packagePath = it }
                                                        }) { Text("Choose package…") }
                                                        Button(onClick = {
                                                            scope.launch {
                                                                message = "Installing Desktop extension…"
                                                                runCatching {
                                                                    withContext(Dispatchers.IO) {
                                                                        val result = session.extensions.install(
                                                                            Path.of(packagePath.trim()),
                                                                        )
                                                                        Triple(
                                                                            result,
                                                                            session.refreshSources(),
                                                                            session.extensions.installedExtensions(),
                                                                        )
                                                                    }
                                                                }.onSuccess { installed ->
                                                                    val (
                                                                        result,
                                                                        installedSources,
                                                                        installedPackages,
                                                                    ) = installed
                                                                    message = result.toString()
                                                                    sources = installedSources
                                                                    installedDesktopExtensions = installedPackages
                                                                }.onFailure {
                                                                    notice.error(it.message ?: "Install failed")
                                                                }
                                                            }
                                                        }, enabled = packagePath.isNotBlank()) {
                                                            Text("Install")
                                                        }
                                                    }
                                                }
                                            }
                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(MihonRadius.card),
                                                color = MihonPalette.panel,
                                                border = BorderStroke(1.dp, MihonPalette.outlineSoft),
                                            ) {
                                                Column(
                                                    Modifier.fillMaxWidth().padding(MihonSpacing.lg),
                                                    verticalArrangement = Arrangement.spacedBy(MihonSpacing.sm),
                                                ) {
                                                    Text(
                                                        "HTTPS repository",
                                                        style = MaterialTheme.typography.titleMedium,
                                                    )
                                                    OutlinedTextField(
                                                        indexUrl,
                                                        { indexUrl = it },
                                                        label = { Text("Repository index URL") },
                                                        modifier = Modifier.fillMaxWidth(),
                                                    )
                                                    Button(onClick = {
                                                        scope.launch {
                                                            runCatching {
                                                                withContext(Dispatchers.IO) {
                                                                    session.extensionRepository.discover(
                                                                        URI(indexUrl.trim()),
                                                                    )
                                                                }
                                                            }.onSuccess {
                                                                availableExtensions = it
                                                                message = "Repository loaded"
                                                            }.onFailure {
                                                                notice.error(it.message ?: "Repository failed")
                                                            }
                                                        }
                                                    }, enabled = indexUrl.isNotBlank()) { Text("Discover") }
                                                    availableExtensions.forEach { entry ->
                                                        Row(
                                                            Modifier.fillMaxWidth(),
                                                            horizontalArrangement =
                                                            Arrangement.spacedBy(MihonSpacing.sm),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            Text(
                                                                "${entry.name} · ${entry.versionCode}",
                                                                modifier = Modifier.weight(1f),
                                                            )
                                                            TextButton(onClick = {
                                                                fingerprint = entry.fingerprint
                                                            }) { Text("Fingerprint") }
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
                                                                        sources = withContext(Dispatchers.IO) {
                                                                            session.refreshSources()
                                                                        }
                                                                        installedDesktopExtensions =
                                                                            withContext(Dispatchers.IO) {
                                                                                session.extensions
                                                                                    .installedExtensions()
                                                                            }
                                                                    }.onFailure {
                                                                        notice.error(
                                                                            it.message ?: "Update failed",
                                                                        )
                                                                    }
                                                                }
                                                            }) { Text("Install / update") }
                                                        }
                                                    }
                                                }
                                            }
                                        }
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
                                        val name = categoryName
                                        scope.launch {
                                            runCatching {
                                                withContext(Dispatchers.IO) { session.library.addCategory(name) }
                                            }.onSuccess {
                                                if (categoryName == name) categoryName = ""
                                                refreshLibrary()
                                                message = "Category added"
                                            }.onFailure { notice.error(it.message ?: "Category failed") }
                                        }
                                    },
                                    enabled = categoryName.isNotBlank(),
                                ) { Text("Add") }
                            }
                            val visibleCategories = categories.filter {
                                (it.name.ifBlank { "Uncategorized" }).contains(categorySearch, ignoreCase = true)
                            }
                            if (libraryLoading) {
                                MihonEmptyState("Loading categories…")
                            } else if (visibleCategories.isEmpty()) {
                                MihonEmptyState("No categories found", "Create a category or change the search.")
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    items(visibleCategories, key = { it.id }) { category ->
                                        val categoryManga = library.filter { manga ->
                                            val membership = libraryMembership[manga._id].orEmpty()
                                            if (category.id == 0L) membership.isEmpty() else category.id in membership
                                        }
                                        MihonPanel(
                                            Modifier.fillMaxWidth().clickable {
                                                selectedCategory = category.id
                                                screen = Screen.LIBRARY
                                                librarySearch = ""
                                            },
                                        ) {
                                            BoxWithConstraints(Modifier.fillMaxWidth().padding(14.dp)) {
                                                val label: @Composable () -> Unit = {
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        DesktopNavigationIcon(6, MihonPalette.sage)
                                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                                    }
                                                }
                                                val previews: @Composable () -> Unit = {
                                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                                if (maxWidth < 520.dp) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                        label()
                                                        previews()
                                                    }
                                                } else {
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Box(Modifier.width(190.dp)) { label() }
                                                        Box(Modifier.weight(1f)) { previews() }
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
                            if (libraryLoading) {
                                MihonEmptyState("Loading history…")
                            } else if (visibleHistory.isEmpty()) {
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
                                    visibleHistory.groupBy { desktopDateGroup(it.readAt?.time ?: 0L) }
                                        .forEach { (day, entries) ->
                                            item {
                                                Text(
                                                    day,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MihonPalette.sage,
                                                )
                                            }
                                            items(entries, key = { it.id }) { entry ->
                                                val storedChapter = historyChapters[entry.chapterId]
                                                DesktopHistoryCard(
                                                    entry,
                                                    storedChapter,
                                                    sources.firstOrNull { it.id == entry.source },
                                                ) {
                                                    if (storedChapter != null) {
                                                        openStoredManga(entry.mangaId, storedChapter.url)
                                                    } else {
                                                        notice.error("The saved chapter is no longer available")
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
                            if (libraryLoading) {
                                MihonEmptyState("Loading updates…")
                            } else if (updateEntries.isEmpty()) {
                                MihonEmptyState(
                                    "No updates yet",
                                    "Check your library when sources are available to look for new chapters.",
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    updateEntries.groupBy { desktopDateGroup(it.dateUpload) }
                                        .forEach { (day, entries) ->
                                            item {
                                                Text(
                                                    day,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MihonPalette.sage,
                                                )
                                            }
                                            items(entries, key = { it.chapterId }) { entry ->
                                                DesktopUpdateCard(
                                                    entry,
                                                    sources.firstOrNull { it.id == entry.source },
                                                    onRead = { openStoredManga(entry.mangaId, entry.chapterUrl) },
                                                    onDownload = {
                                                        queueStoredChapter(entry.mangaId, entry.chapterUrl)
                                                    },
                                                )
                                            }
                                        }
                                }
                            }
                        }
                        screen == Screen.SETTINGS -> {
                            Row(
                                Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(MihonSpacing.md),
                            ) {
                                DesktopSettingsNavigation(
                                    selected = settingsSection,
                                    onSelect = { settingsSection = it },
                                    modifier = Modifier.width(196.dp).fillMaxHeight()
                                        .background(MihonPalette.panel, RoundedCornerShape(MihonRadius.panel)),
                                )
                                Column(
                                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(MihonSpacing.md),
                                ) {
                                    MihonSectionHeader(settingsSection.title, settingsSection.description)
                                    when (settingsSection) {
                                        DesktopSettingsSection.GENERAL -> MihonPanel(Modifier.fillMaxWidth()) {
                                            Column(
                                                Modifier.fillMaxWidth().padding(MihonSpacing.lg),
                                                verticalArrangement = Arrangement.spacedBy(MihonSpacing.sm),
                                            ) {
                                                Text("Mihon for Windows", style = MaterialTheme.typography.titleMedium)
                                                Text(
                                                    "Language follows Windows. " +
                                                        "Library and reader preferences are saved on this device.",
                                                    color = MihonPalette.muted,
                                                )
                                                Text(
                                                    "Language: ${graph.localeService.currentLanguageTag()}",
                                                    color = MihonPalette.muted,
                                                )
                                            }
                                        }
                                        DesktopSettingsSection.STORAGE -> {
                                            MihonPanel(Modifier.fillMaxWidth()) {
                                                Column(
                                                    Modifier.fillMaxWidth().padding(14.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                                ) {
                                                    Text("Migration", style = MaterialTheme.typography.titleMedium)
                                                    Text(
                                                        "Import a Mihon Android .tachibk backup. " +
                                                            "Matching manga records are updated.",
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
                                                                        DesktopBackupImporter.import(
                                                                            Path.of(path),
                                                                            session.library,
                                                                        )
                                                                    }
                                                                }.onSuccess { result ->
                                                                    refreshLibrary()
                                                                    message = buildString {
                                                                        append("Imported ${result.manga} manga")
                                                                        append(", ${result.chapters} chapters")
                                                                        append(
                                                                            ", ${result.categories} categories",
                                                                        )
                                                                        append(", ")
                                                                        append(result.trackerEntries)
                                                                        append(" tracker entries")
                                                                    }
                                                                }.onFailure {
                                                                    notice.error(it.message ?: "Backup import failed")
                                                                }
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
                                                    Text(
                                                        "Application and storage",
                                                        style = MaterialTheme.typography.titleMedium,
                                                    )
                                                    Text(
                                                        "Local library: ${graph.appDirectories.localLibrary}",
                                                        color = MihonPalette.muted,
                                                    )
                                                    Text(
                                                        "Database: ${graph.appDirectories.database}",
                                                        color = MihonPalette.muted,
                                                    )
                                                }
                                            }
                                        }
                                        DesktopSettingsSection.TRACKING -> {
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
                                                        notice.error("Could not open AniList in the browser")
                                                    }
                                                }) { Text("Sign in to AniList in browser") }
                                                Text("If the redirect fails, paste its URL below.")
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
                                                        runCatching {
                                                            session.aniListTracker.loginFromCallback(callback)
                                                        }
                                                            .onSuccess { name ->
                                                                aniListLoggedIn = true
                                                                message = "Signed in to AniList as $name"
                                                            }
                                                            .onFailure {
                                                                notice.error(it.message ?: "AniList sign-in failed")
                                                            }
                                                    }
                                                }) { Text("Complete AniList sign-in") }
                                            }
                                            HorizontalDivider(Modifier.padding(vertical = 6.dp))
                                            Text(
                                                "MangaUpdates: " +
                                                    if (mangaUpdatesLoggedIn) "signed in" else "not signed in",
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
                                                        runCatching {
                                                            session.mangaUpdatesTracker.login(username, password)
                                                        }
                                                            .onSuccess { name ->
                                                                mangaUpdatesLoggedIn = true
                                                                message = "Signed in to MangaUpdates as $name"
                                                            }.onFailure {
                                                                notice.error(
                                                                    it.message ?: "MangaUpdates sign-in failed",
                                                                )
                                                            }
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
                                                            }.onFailure {
                                                                notice.error(it.message ?: "Kitsu sign-in failed")
                                                            }
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
                                                    }.onFailure {
                                                        notice.error(it.message ?: "Could not open MyAnimeList")
                                                    }
                                                }) { Text("Sign in to MyAnimeList in browser") }
                                                Text("If the redirect fails, paste its URL below.")
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
                                                        runCatching {
                                                            session.myAnimeListTracker.loginFromCallback(callback)
                                                        }
                                                            .onSuccess { name ->
                                                                malLoggedIn = true
                                                                message = "Signed in to MyAnimeList as $name"
                                                            }.onFailure {
                                                                notice.error(it.message ?: "MyAnimeList sign-in failed")
                                                            }
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
                                                        val authorizationUrl = session.shikimoriTracker.beginLogin()
                                                        check(graph.browserService.open(authorizationUrl))
                                                    }.onFailure {
                                                        notice.error(it.message ?: "Could not open Shikimori")
                                                    }
                                                }) { Text("Sign in to Shikimori in browser") }
                                                Text("If the redirect fails, paste its URL below.")
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
                                                        runCatching {
                                                            session.shikimoriTracker.loginFromCallback(callback)
                                                        }
                                                            .onSuccess { name ->
                                                                shikimoriLoggedIn = true
                                                                message = "Signed in to Shikimori as $name"
                                                            }.onFailure {
                                                                notice.error(it.message ?: "Shikimori sign-in failed")
                                                            }
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
                                                        val authorizationUrl = session.hikkaTracker.beginLogin()
                                                        check(graph.browserService.open(authorizationUrl))
                                                    }.onFailure { notice.error(it.message ?: "Could not open Hikka") }
                                                }) { Text("Sign in to Hikka in browser") }
                                                Text("If the redirect fails, paste its URL below.")
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
                                                            }.onFailure {
                                                                notice.error(it.message ?: "Hikka sign-in failed")
                                                            }
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
                                                        val authorizationUrl = session.bangumiTracker.beginLogin()
                                                        check(graph.browserService.open(authorizationUrl))
                                                    }.onFailure { notice.error(it.message ?: "Could not open Bangumi") }
                                                }) { Text("Sign in to Bangumi in browser") }
                                                Text("If the redirect fails, paste its URL below.")
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
                                                        runCatching {
                                                            session.bangumiTracker.loginFromCallback(callback)
                                                        }
                                                            .onSuccess { name ->
                                                                bangumiLoggedIn = true
                                                                message = "Signed in to Bangumi as $name"
                                                            }.onFailure {
                                                                notice.error(it.message ?: "Bangumi sign-in failed")
                                                            }
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
                                                        val authorizationUrl = session.mangaBakaTracker.beginLogin()
                                                        check(graph.browserService.open(authorizationUrl))
                                                    }.onFailure {
                                                        notice.error(it.message ?: "Could not open MangaBaka")
                                                    }
                                                }) { Text("Sign in to MangaBaka in browser") }
                                                Text("If the redirect fails, paste its URL below.")
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
                                                        runCatching {
                                                            session.mangaBakaTracker.loginFromCallback(callback)
                                                        }
                                                            .onSuccess { name ->
                                                                mangaBakaLoggedIn = true
                                                                message = "Signed in to MangaBaka as $name"
                                                            }.onFailure {
                                                                notice.error(it.message ?: "MangaBaka sign-in failed")
                                                            }
                                                    }
                                                }) { Text("Complete MangaBaka sign-in") }
                                            }
                                        }
                                        DesktopSettingsSection.LIBRARY -> MihonPanel(Modifier.fillMaxWidth()) {
                                            Column(
                                                Modifier.fillMaxWidth().padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Text(
                                                    "Library and downloads",
                                                    style = MaterialTheme.typography.titleMedium,
                                                )
                                                TextButton(onClick = {
                                                    val intervals = DesktopLibraryUpdateScheduler.INTERVALS
                                                    val nextIndex =
                                                        (intervals.indexOf(updateInterval) + 1) % intervals.size
                                                    updateInterval = intervals[nextIndex]
                                                    session.libraryUpdates.setIntervalHours(updateInterval)
                                                }) {
                                                    Text(
                                                        if (updateInterval == 0L) {
                                                            "Scheduled library updates: off"
                                                        } else {
                                                            "Scheduled updates: every $updateInterval hours " +
                                                                "while Mihon is open"
                                                        },
                                                    )
                                                }
                                                Text(
                                                    "Downloads wait for an active network connection.",
                                                    color = MihonPalette.muted,
                                                )
                                            }
                                        }
                                        DesktopSettingsSection.READER -> DesktopReaderDefaultsSettings(graph)
                                        DesktopSettingsSection.DOWNLOADS -> MihonPanel(Modifier.fillMaxWidth()) {
                                            Column(
                                                Modifier.fillMaxWidth().padding(MihonSpacing.lg),
                                                verticalArrangement = Arrangement.spacedBy(MihonSpacing.sm),
                                            ) {
                                                Text("Download queue", style = MaterialTheme.typography.titleMedium)
                                                Text(
                                                    "Downloads pause when the network is unavailable " +
                                                        "and resume from the queue.",
                                                    color = MihonPalette.muted,
                                                )
                                                TextButton(onClick = { navigateToScreen(Screen.DOWNLOADS) }) {
                                                    Text("Open downloads")
                                                }
                                            }
                                        }
                                        DesktopSettingsSection.ADVANCED -> MihonPanel(Modifier.fillMaxWidth()) {
                                            Column(
                                                Modifier.fillMaxWidth().padding(MihonSpacing.lg),
                                                verticalArrangement = Arrangement.spacedBy(MihonSpacing.sm),
                                            ) {
                                                Text("Windows links", style = MaterialTheme.typography.titleMedium)
                                                Text(
                                                    "Mihon can handle manga and tracker callback links " +
                                                        "for this Windows account.",
                                                    color = MihonPalette.muted,
                                                )
                                                Text(
                                                    "Status: " +
                                                        if (mihonProtocolRegistered) "registered" else "not registered",
                                                    color = MihonPalette.muted,
                                                )
                                                if (
                                                    System.getProperty("os.name")
                                                        .startsWith("Windows", ignoreCase = true)
                                                ) {
                                                    TextButton(onClick = {
                                                        runCatching {
                                                            if (mihonProtocolRegistered) {
                                                                protocolRegistrar.unregisterMihonProtocol()
                                                            } else {
                                                                protocolRegistrar.registerMihonProtocol()
                                                            }
                                                        }.onSuccess {
                                                            mihonProtocolRegistered = !mihonProtocolRegistered
                                                            message = if (mihonProtocolRegistered) {
                                                                "Mihon browser links registered for this Windows user"
                                                            } else {
                                                                "Mihon browser links unregistered"
                                                            }
                                                        }.onFailure {
                                                            notice.error(it.message ?: "Could not update Mihon links")
                                                        }
                                                    }) {
                                                        Text(
                                                            if (mihonProtocolRegistered) {
                                                                "Unregister Mihon links"
                                                            } else {
                                                                "Register Mihon links"
                                                            },
                                                        )
                                                    }
                                                }
                                                HorizontalDivider()
                                                Text("Desktop extensions", style = MaterialTheme.typography.titleMedium)
                                                Text(
                                                    "A locally trusted signing fingerprint records a local trust " +
                                                        "decision; it does not verify a publisher identity.",
                                                    color = MihonPalette.muted,
                                                )
                                                TextButton(onClick = { navigateToScreen(Screen.EXTENSIONS) }) {
                                                    Text("Manage extensions")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        screen == Screen.DOWNLOADS -> {
                            MihonSectionHeader(
                                "Downloads",
                                "${downloads.size} chapters in the persisted queue",
                                trailing = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(
                                            onClick = { session.downloads.pause() },
                                            enabled = downloads.any {
                                                it.status == DesktopDownloadStatus.RUNNING ||
                                                    it.status == DesktopDownloadStatus.PENDING
                                            },
                                        ) { Text("Pause all") }
                                        Button(
                                            onClick = { session.downloads.resume() },
                                            enabled = downloads.any {
                                                it.status == DesktopDownloadStatus.PAUSED ||
                                                    it.status == DesktopDownloadStatus.FAILED
                                            },
                                        ) { Text("Resume all") }
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
                                    listOf(
                                        "In progress" to setOf(
                                            DesktopDownloadStatus.RUNNING,
                                            DesktopDownloadStatus.PENDING,
                                        ),
                                        "Paused" to setOf(DesktopDownloadStatus.PAUSED),
                                        "Failed" to setOf(DesktopDownloadStatus.FAILED),
                                        "Completed" to setOf(DesktopDownloadStatus.COMPLETED),
                                    ).forEach { (heading, statuses) ->
                                        val grouped = downloads.filter { it.status in statuses }
                                        if (grouped.isNotEmpty()) {
                                            item {
                                                Text(
                                                    heading,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MihonPalette.sage,
                                                )
                                            }
                                        }
                                        items(grouped, key = DesktopDownload::key) { download ->
                                            val savedManga = downloadMangas[download.key]
                                            val downloadSource = sources.firstOrNull { it.id == download.sourceId }
                                            DesktopDownloadCard(
                                                download = download,
                                                coverUrl = savedManga?.thumbnail_url,
                                                source = downloadSource,
                                                onPause = { session.downloads.pause(download.key) },
                                                onResume = { session.downloads.resume(download.key) },
                                                onCancel = { session.downloads.cancel(download.key) },
                                                onRead = {
                                                    if (savedManga != null) {
                                                        openStoredManga(savedManga._id, download.chapterUrl)
                                                    } else {
                                                        val readingSource = downloadSource
                                                            ?: DownloadedSource(download.sourceId)
                                                        val readingManga = SManga.create().apply {
                                                            url = download.mangaUrl
                                                            title = download.mangaTitle
                                                        }
                                                        val readingChapter = SChapter.create().apply {
                                                            url = download.chapterUrl
                                                            name = download.chapterName
                                                        }
                                                        readerTarget = ReaderTarget(
                                                            readingSource,
                                                            readingManga,
                                                            listOf(readingChapter),
                                                            readingChapter.url,
                                                        )
                                                    }
                                                },
                                            )
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
