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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import mihon.core.extension.desktop.DesktopExtensionInstallResult
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

private fun Source.displayName(): String =
    if (this is DesktopLocalSource || lang == "localsourcelang") name else "$name (${lang.uppercase()})"

private const val KEIYOUSHI_STORE_URL = "https://github.com/keiyoushi/extensions/raw/repo/index.pb"

private data class DetailSnapshot(
    val sourceId: Long,
    val mangaUrl: String,
    val stored: Mangas?,
    val tracks: Map<Long, Manga_sync>,
    val chapterStates: Map<String, Chapters>,
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
    var libraryError by remember { mutableStateOf<String?>(null) }
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
    var detailRevision by remember { mutableIntStateOf(0) }
    var detailsLoading by remember { mutableStateOf(false) }
    var detailsError by remember { mutableStateOf<String?>(null) }
    var selectedMangaCategories by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var chapters by remember { mutableStateOf(emptyList<SChapter>()) }
    var readerTarget by remember { mutableStateOf<ReaderTarget?>(null) }
    var readerReturnScreen by remember { mutableStateOf<Screen?>(null) }
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
    var extensionLoading by remember { mutableStateOf(false) }
    var extensionError by remember { mutableStateOf<String?>(null) }
    var extensionBusyPackage by remember { mutableStateOf<String?>(null) }
    var extensionBusyLabel by remember { mutableStateOf<String?>(null) }
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
    var downloadQuery by remember { mutableStateOf("") }
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
        libraryError = null
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
                if (requestId == libraryRequestId) {
                    val detail = error.message ?: "Could not load library"
                    libraryError = detail
                    notice.error(detail)
                }
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
    fun loadMangaDetails(
        selectedSource: Source,
        manga: SManga,
        chapterUrl: String? = null,
        readerReturnTo: Screen? = null,
    ) {
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
                        readerReturnScreen = readerReturnTo
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
    fun openStoredManga(
        mangaId: Long,
        chapterUrl: String? = null,
        readerReturnTo: Screen? = null,
    ) {
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
                readerReturnScreen = readerReturnTo
                readerTarget = ReaderTarget(readingSource, manga, savedChapters, chapterUrl)
                return
            }
        }
        if (installedSource == null) {
            notice.error("Install this manga’s source to browse its details or read undownloaded chapters")
            return
        }
        loadMangaDetails(
            selectedSource = installedSource,
            manga = manga,
            chapterUrl = chapterUrl,
            readerReturnTo = readerReturnTo,
        )
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
                        extensionSection = 3
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
            extensionError = null
        }.onFailure {
            suwayomiStatus = it.message ?: "Local extension engine failed"
            extensionError = suwayomiStatus
        }
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
    LaunchedEffect(selectedManga?.url, source?.id, detailRevision, message) {
        val activeManga = selectedManga
        val activeSource = source
        if (activeManga == null || activeSource == null) {
            detailSnapshot = null
        } else {
            val (snapshot, memberships) = withContext(Dispatchers.IO) {
                val stored = session.library.find(activeSource.id, activeManga.url)
                val tracks = stored?.let { session.library.tracks(it._id).associateBy(Manga_sync::sync_id) }
                    ?: emptyMap()
                DetailSnapshot(
                    sourceId = activeSource.id,
                    mangaUrl = activeManga.url,
                    stored = stored,
                    tracks = tracks,
                    chapterStates = stored?.let { manga ->
                        session.library.chapters(manga._id).associateBy { it.url }
                    } ?: emptyMap(),
                ) to (stored?.let { session.library.mangaCategories(it._id) } ?: emptySet())
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
                compactNavigation = with(density) { size.width.toDp() < MihonSizes.navigationCompactBreakpoint }
            },
            color = RoninColors.appBackground,
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
                RoninMainContent(
                    compactNavigation = compactNavigation,
                    readerMode = readerTarget != null,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    if (message.isNotBlank()) {
                        RoninPanel(Modifier.fillMaxWidth()) {
                            Text(
                                message,
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                                color = if (notice.isError) MaterialTheme.colorScheme.error else RoninColors.textMuted,
                            )
                        }
                    }
                    when {
                        readerTarget != null -> DesktopReader(
                            session = session,
                            graph = graph,
                            target = requireNotNull(readerTarget),
                            onClose = {
                                val returnScreen = readerReturnScreen
                                readerTarget = null
                                readerReturnScreen = null
                                if (returnScreen != null) {
                                    screen = returnScreen
                                    selectedManga = null
                                    chapters = emptyList()
                                }
                                detailRevision++
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
                            val persistedChapterStates = snapshot?.chapterStates.orEmpty()
                            LazyColumn(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                item {
                                    Column(
                                        modifier = Modifier.widthIn(max = 1440.dp).fillMaxWidth(),
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
                                        val openBrowserAction: (() -> Unit)? =
                                            if (selectedSource is HttpSource) {
                                                {
                                                    runCatching {
                                                        val url = selectedSource.getMangaUrl(item)
                                                        val uri = URI(url)
                                                        require(
                                                            uri.scheme in setOf("http", "https") &&
                                                                uri.userInfo == null,
                                                        )
                                                        check(graph.browserService.open(url))
                                                    }.onFailure {
                                                        notice.error(it.message ?: "Could not open browser")
                                                    }
                                                }
                                            } else {
                                                null
                                            }
                                        val copyLinkAction: (() -> Unit)? =
                                            if (selectedSource is HttpSource) {
                                                {
                                                    runCatching {
                                                        val url = selectedSource.getMangaUrl(item)
                                                        val uri = URI(url)
                                                        require(
                                                            uri.scheme in setOf("http", "https") &&
                                                                uri.userInfo == null,
                                                        )
                                                        check(graph.externalOpenService.shareText(url))
                                                    }.onSuccess {
                                                        message = "Link copied"
                                                    }.onFailure {
                                                        notice.error(it.message ?: "Could not copy link")
                                                    }
                                                }
                                            } else {
                                                null
                                            }
                                        RoninMangaDetailsHero(
                                            manga = item,
                                            source = selectedSource,
                                            sourceName = selectedSource?.displayName() ?: "Unknown source",
                                            inLibrary = stored?.favorite == true,
                                            categoryIds = selectedMangaCategories,
                                            categories = categories,
                                            linkedTrackers = linkedTrackers,
                                            continueLabel = if (recentChapter != null) {
                                                "Continue reading"
                                            } else {
                                                "Start reading"
                                            },
                                            progressLabel = recentChapter?.let {
                                                if (it.read) {
                                                    "Last chapter finished"
                                                } else {
                                                    "Continue from page ${it.last_page_read + 1}"
                                                }
                                            },
                                            detailsLoading = detailsLoading,
                                            detailsError = detailsError,
                                            canRead = selectedSource != null && continueChapter != null,
                                            canToggleLibrary = snapshot != null,
                                            canManageTracking = stored != null,
                                            trackingExpanded = trackingExpanded,
                                            onBack = { selectedManga = null },
                                            onRead = {
                                                if (selectedSource != null && continueChapter != null) {
                                                    readerReturnScreen = null
                                                    readerTarget = ReaderTarget(
                                                        selectedSource,
                                                        item,
                                                        chapters,
                                                        continueChapter.url,
                                                    )
                                                }
                                            },
                                            onToggleLibrary = {
                                                if (snapshot != null) {
                                                    if (stored == null && selectedSource != null) {
                                                        session.library.addToLibrary(selectedSource.id, item)
                                                    } else if (stored != null) {
                                                        session.library.setFavorite(
                                                            stored._id,
                                                            !stored.favorite,
                                                        )
                                                    }
                                                    detailRevision++
                                                    refreshLibrary()
                                                    message = "Library updated"
                                                }
                                            },
                                            onToggleTracking = {
                                                trackingExpanded = !trackingExpanded
                                            },
                                            onRetry = {
                                                if (selectedSource != null) {
                                                    loadMangaDetails(selectedSource, item)
                                                }
                                            },
                                            onOpenBrowser = openBrowserAction,
                                            onCopyLink = copyLinkAction,
                                        )
                                        if (
                                            trackingExpanded && stored != null &&
                                            item.url.contains("/api/v1/series/")
                                        ) {
                                            RoninTextButton(
                                                label = if (komgaTrack == null) "Link Komga" else "Sync Komga",
                                                onClick = {
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
                                                                        )?.read == true
                                                                    }.maxOfOrNull {
                                                                        it.chapter_number.toDouble()
                                                                    }
                                                                }
                                                                if (
                                                                    lastRead != null &&
                                                                    lastRead.isFinite() &&
                                                                    lastRead > 0
                                                                ) {
                                                                    session.trackerSync.enqueue(
                                                                        stored._id,
                                                                        lastRead,
                                                                    )
                                                                } else {
                                                                    session.trackerSync.retry(stored._id)
                                                                }
                                                            }
                                                        }.onSuccess {
                                                            message = if (komgaTrack == null) {
                                                                "Komga tracking linked"
                                                            } else {
                                                                "Komga sync queued"
                                                            }
                                                        }.onFailure {
                                                            notice.error(
                                                                it.message ?: "Komga tracking failed",
                                                            )
                                                        }
                                                    }
                                                },
                                            )
                                            trackerSyncQueue.firstOrNull {
                                                it.mangaId == stored._id
                                            }?.error?.let {
                                                RoninErrorState(
                                                    title = "Tracker sync issue",
                                                    detail = it,
                                                )
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
                                    val persisted = persistedChapterStates.values
                                    val unreadCount = persisted.count { !it.read }
                                    val bookmarkedCount = persisted.count { it.bookmark }
                                    Box(
                                        modifier = Modifier.widthIn(max = 1440.dp).fillMaxWidth(),
                                    ) {
                                        RoninSectionHeader(
                                            title = "Chapters",
                                            subtitle = if (storedManga != null && persisted.isNotEmpty()) {
                                                buildString {
                                                    append(chapters.size)
                                                    append(" chapters · ")
                                                    append(unreadCount)
                                                    append(" unread")
                                                    if (bookmarkedCount > 0) {
                                                        append(" · ")
                                                        append(bookmarkedCount)
                                                        append(" bookmarked")
                                                    }
                                                }
                                            } else {
                                                "${chapters.size} chapters available"
                                            },
                                        )
                                    }
                                }
                                if (chapters.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier.widthIn(max = 1440.dp).fillMaxWidth(),
                                        ) {
                                            when {
                                                detailsLoading -> RoninLoadingState(
                                                    title = "Loading chapters",
                                                    detail = "Fetching the latest chapter list from the source.",
                                                )
                                                detailsError != null -> Column(
                                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                                ) {
                                                    RoninErrorState(
                                                        title = "Could not load chapters",
                                                        detail = detailsError,
                                                    )
                                                    if (selectedSource != null) {
                                                        RoninSecondaryButton(
                                                            label = "Retry",
                                                            onClick = {
                                                                loadMangaDetails(selectedSource, item)
                                                            },
                                                        )
                                                    }
                                                }
                                                else -> RoninEmptyState(
                                                    title = "No chapters available",
                                                    detail = "This source did not return any chapters for this manga.",
                                                )
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
                                    val storedChapter = persistedChapterStates[chapter.url]
                                    val subtitle = buildList {
                                        chapter.scanlator
                                            ?.takeIf(String::isNotBlank)
                                            ?.let { add(it) }
                                        if (
                                            storedChapter != null &&
                                            !storedChapter.read &&
                                            storedChapter.last_page_read > 0
                                        ) {
                                            add("Page ${storedChapter.last_page_read + 1}")
                                        }
                                    }.joinToString(" · ").takeIf(String::isNotBlank)
                                    Column(
                                        modifier = Modifier.widthIn(max = 1440.dp).fillMaxWidth(),
                                    ) {
                                        RoninChapterRow(
                                            title = chapter.name,
                                            subtitle = subtitle,
                                            read = if (storedManga != null) {
                                                storedChapter?.read ?: false
                                            } else {
                                                null
                                            },
                                            bookmarked = storedChapter?.bookmark == true,
                                            onClick = {
                                                if (selectedSource != null) {
                                                    readerTarget = ReaderTarget(
                                                        selectedSource,
                                                        item,
                                                        chapters,
                                                        chapter.url,
                                                    )
                                                }
                                            },
                                            trailing = {
                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                                ) {
                                                    if (storedChapter != null) {
                                                        RoninInlineAction(
                                                            label = if (storedChapter.bookmark) {
                                                                "Unbookmark"
                                                            } else {
                                                                "Bookmark"
                                                            },
                                                            onClick = {
                                                                session.library.setChapterBookmark(
                                                                    storedChapter._id,
                                                                    !storedChapter.bookmark,
                                                                )
                                                                detailRevision++
                                                                message = if (storedChapter.bookmark) {
                                                                    "Bookmark removed"
                                                                } else {
                                                                    "Chapter bookmarked"
                                                                }
                                                            },
                                                        )
                                                    }
                                                    if (
                                                        selectedSource != null &&
                                                        selectedSource !is DesktopLocalSource
                                                    ) {
                                                        when (queued?.status) {
                                                            DesktopDownloadStatus.COMPLETED -> RoninBadge(
                                                                label = "Downloaded",
                                                                accent = true,
                                                            )
                                                            DesktopDownloadStatus.RUNNING -> RoninBadge(
                                                                label = "${queued.pagesDone}/${queued.pageCount}",
                                                                accent = true,
                                                            )
                                                            DesktopDownloadStatus.PENDING -> RoninBadge(
                                                                label = "Queued",
                                                            )
                                                            DesktopDownloadStatus.PAUSED,
                                                            DesktopDownloadStatus.FAILED,
                                                            null,
                                                            -> RoninInlineAction(
                                                                label = when (queued?.status) {
                                                                    DesktopDownloadStatus.PAUSED -> "Resume"
                                                                    DesktopDownloadStatus.FAILED -> "Retry"
                                                                    else -> "Download"
                                                                },
                                                                onClick = {
                                                                    when (queued?.status) {
                                                                        DesktopDownloadStatus.PAUSED,
                                                                        DesktopDownloadStatus.FAILED,
                                                                        -> session.downloads.resume(queued.key)
                                                                        null -> session.downloads.enqueue(
                                                                            selectedSource,
                                                                            item,
                                                                            chapter,
                                                                        )
                                                                        else -> Unit
                                                                    }
                                                                },
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                        )
                                        HorizontalDivider(color = mihon.desktop.design.RoninColors.borderSubtle)
                                    }
                                }
                                if (storedManga?.favorite == true) {
                                    item {
                                        RoninPanel(
                                            Modifier.widthIn(max = 1440.dp).fillMaxWidth(),
                                        ) {
                                            Column(
                                                Modifier.fillMaxWidth().padding(24.dp),
                                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                            ) {
                                                RoninSectionHeader(
                                                    title = "Categories",
                                                    subtitle = "Organize this manga without leaving its details.",
                                                )
                                                if (categories.isEmpty()) {
                                                    Text(
                                                        "No categories created yet.",
                                                        color = mihon.desktop.design.RoninColors.textMuted,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                    )
                                                } else {
                                                    FlowRow(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                                    ) {
                                                        categories.forEach { category ->
                                                            val selected =
                                                                category.id in selectedMangaCategories
                                                            RoninChip(
                                                                label = category.name.ifBlank {
                                                                    "Uncategorized"
                                                                },
                                                                selected = selected,
                                                                onClick = {
                                                                    val updated = if (selected) {
                                                                        selectedMangaCategories - category.id
                                                                    } else {
                                                                        selectedMangaCategories + category.id
                                                                    }
                                                                    session.library.setMangaCategories(
                                                                        storedManga._id,
                                                                        updated,
                                                                    )
                                                                    selectedMangaCategories = updated
                                                                    detailRevision++
                                                                    message = "Categories updated"
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
                        screen == Screen.LIBRARY -> {
                            LibraryScreen(
                                library = library,
                                membership = libraryMembership,
                                historyEntries = historyEntries,
                                historyChapters = historyChapters,
                                sources = sources,
                                categories = categories,
                                selectedCategory = selectedCategory,
                                onSelectCategory = { selectedCategory = it },
                                search = librarySearch,
                                onSearchChange = { librarySearch = it },
                                loading = libraryLoading,
                                error = libraryError,
                                onOpenManga = ::openStoredManga,
                            )
                        }
                        screen == Screen.SEARCH -> {
                            SearchScreen(
                                sources = sources,
                                selectedSource = source,
                                query = query,
                                activeQuery = browseQuery,
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
                            SourcesScreen(
                                sources = sources,
                                sourceSearch = sourceSearch,
                                onSourceSearchChange = { sourceSearch = it },
                                selectedSource = source,
                                query = query,
                                activeQuery = browseQuery,
                                onQueryChange = { query = it },
                                results = browseItems,
                                loading = browseLoading,
                                error = browseError,
                                hasNext = browseHasNext,
                                onSelectSource = { selected ->
                                    query = ""
                                    browse(selected)
                                },
                                onClearSource = {
                                    browseJob?.cancel()
                                    browseRequestId++
                                    source = null
                                    query = ""
                                    browseQuery = ""
                                    browseItems = emptyList()
                                    browsePage = 0
                                    browseHasNext = false
                                    browseLoading = false
                                    browseError = null
                                },
                                onBrowsePopular = {
                                    source?.let { selected ->
                                        query = ""
                                        browse(selected)
                                    }
                                },
                                onSearch = {
                                    source?.let { selected ->
                                        browse(selected, query)
                                    }
                                },
                                onOpenManga = { manga ->
                                    source?.let { selected ->
                                        loadMangaDetails(selected, manga)
                                    }
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
                        screen == Screen.EXTENSIONS -> {
                            ExtensionsScreen(
                                selectedSection = extensionSection,
                                onSelectSection = {
                                    extensionSection = it
                                    extensionError = null
                                },
                                suwayomiExtensions = suwayomiExtensions,
                                installedDesktopExtensions = installedDesktopExtensions,
                                availableDesktopExtensions = availableExtensions,
                                search = suwayomiSearch,
                                onSearchChange = { suwayomiSearch = it },
                                status = suwayomiStatus,
                                engineRunning = session.suwayomiEngine.isRunning,
                                loading = extensionLoading,
                                error = extensionError,
                                busyPackage = extensionBusyPackage,
                                busyLabel = extensionBusyLabel,
                                repositoryUrl = indexUrl,
                                onRepositoryUrlChange = {
                                    indexUrl = it
                                    availableExtensions = emptyList()
                                },
                                packagePath = packagePath,
                                onPackagePathChange = { packagePath = it },
                                fingerprint = fingerprint,
                                onFingerprintChange = { fingerprint = it },
                                onRefreshCatalog = {
                                    extensionLoading = true
                                    extensionError = null
                                    scope.launch {
                                        try {
                                            val refreshed = withContext(Dispatchers.IO) {
                                                val client = session.suwayomiEngine.client()
                                                client.addStore(KEIYOUSHI_STORE_URL)
                                                client.refreshExtensions()
                                            }
                                            suwayomiExtensions = refreshed
                                            suwayomiStatus = "${refreshed.size} extensions available"
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (failure: Exception) {
                                            extensionError = failure.message ?: "Could not refresh extensions"
                                            suwayomiStatus = extensionError ?: "Could not refresh extensions"
                                        } finally {
                                            extensionLoading = false
                                        }
                                    }
                                },
                                onSuwayomiAction = { entry, action ->
                                    val engineAction = when (action) {
                                        ExtensionAction.INSTALL -> "install"
                                        ExtensionAction.UPDATE -> "update"
                                        ExtensionAction.REMOVE -> "uninstall"
                                    }
                                    extensionBusyPackage = entry.pkgName
                                    extensionBusyLabel = when (action) {
                                        ExtensionAction.INSTALL -> "Installing…"
                                        ExtensionAction.UPDATE -> "Updating…"
                                        ExtensionAction.REMOVE -> "Removing…"
                                    }
                                    extensionError = null
                                    scope.launch {
                                        try {
                                            val (extensions, installedSources) = withContext(Dispatchers.IO) {
                                                val client = session.suwayomiEngine.client()
                                                client.setInstalled(entry.pkgName, engineAction)
                                                session.suwayomiEngine.refreshSources()
                                                client.extensions() to session.refreshSources()
                                            }
                                            suwayomiExtensions = extensions
                                            sources = installedSources
                                            suwayomiStatus = "${entry.name} changed"
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (failure: Exception) {
                                            extensionError = failure.message ?: "Extension action failed"
                                            suwayomiStatus = extensionError ?: "Extension action failed"
                                        } finally {
                                            if (extensionBusyPackage == entry.pkgName) {
                                                extensionBusyPackage = null
                                                extensionBusyLabel = null
                                            }
                                        }
                                    }
                                },
                                onDiscoverRepository = {
                                    extensionLoading = true
                                    extensionError = null
                                    scope.launch {
                                        try {
                                            availableExtensions = withContext(Dispatchers.IO) {
                                                session.extensionRepository.discover(URI(indexUrl.trim()))
                                            }
                                            message = "Repository loaded"
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (failure: Exception) {
                                            extensionError = failure.message ?: "Repository failed"
                                        } finally {
                                            extensionLoading = false
                                        }
                                    }
                                },
                                onInstallRepositoryEntry = { entry ->
                                    extensionBusyPackage = entry.id
                                    extensionBusyLabel = if (
                                        installedDesktopExtensions.any {
                                            it.id == entry.id && it.versionCode < entry.versionCode
                                        }
                                    ) {
                                        "Updating…"
                                    } else {
                                        "Installing…"
                                    }
                                    extensionError = null
                                    scope.launch {
                                        try {
                                            val result = withContext(Dispatchers.IO) {
                                                session.extensionRepository.install(
                                                    URI(indexUrl.trim()),
                                                    entry,
                                                    session.extensions,
                                                )
                                            }
                                            when (result) {
                                                is DesktopExtensionInstallResult.Installed -> {
                                                    message = "Installed ${result.manifest.name}"
                                                    sources = withContext(Dispatchers.IO) {
                                                        session.refreshSources()
                                                    }
                                                    installedDesktopExtensions = withContext(Dispatchers.IO) {
                                                        session.extensions.installedExtensions()
                                                    }
                                                }
                                                is DesktopExtensionInstallResult.Rejected -> {
                                                    extensionError = "Install rejected: " +
                                                        result.reason.name.lowercase().replace('_', ' ')
                                                }
                                            }
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (failure: Exception) {
                                            extensionError = failure.message ?: "Desktop extension install failed"
                                        } finally {
                                            if (extensionBusyPackage == entry.id) {
                                                extensionBusyPackage = null
                                                extensionBusyLabel = null
                                            }
                                        }
                                    }
                                },
                                onTrustFingerprint = {
                                    extensionBusyPackage = "fingerprint"
                                    extensionBusyLabel = "Trusting…"
                                    extensionError = null
                                    scope.launch {
                                        try {
                                            withContext(Dispatchers.IO) {
                                                session.extensions.trust(fingerprint.trim())
                                            }
                                            message = "Fingerprint trusted"
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (failure: Exception) {
                                            extensionError = failure.message ?: "Trust failed"
                                        } finally {
                                            extensionBusyPackage = null
                                            extensionBusyLabel = null
                                        }
                                    }
                                },
                                onChoosePackage = {
                                    graph.fileDialogService.chooseOpenFile(
                                        OpenFileRequest(
                                            "Install Desktop extension",
                                            extensions = setOf("mihonext"),
                                        ),
                                    )?.let { packagePath = it }
                                },
                                onInstallPackage = {
                                    extensionBusyPackage = "local-package"
                                    extensionBusyLabel = "Installing…"
                                    extensionError = null
                                    scope.launch {
                                        try {
                                            val (result, installedSources, installedPackages) =
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
                                            when (result) {
                                                is DesktopExtensionInstallResult.Installed -> {
                                                    message = "Installed ${result.manifest.name}"
                                                    sources = installedSources
                                                    installedDesktopExtensions = installedPackages
                                                }
                                                is DesktopExtensionInstallResult.Rejected -> {
                                                    extensionError = "Install rejected: " +
                                                        result.reason.name.lowercase().replace('_', ' ')
                                                }
                                            }
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (failure: Exception) {
                                            extensionError = failure.message ?: "Install failed"
                                        } finally {
                                            extensionBusyPackage = null
                                            extensionBusyLabel = null
                                        }
                                    }
                                },
                            )
                        }
                        screen == Screen.CATEGORIES -> {
                            CategoriesScreen(
                                categories = categories,
                                library = library,
                                membership = libraryMembership,
                                sources = sources,
                                search = categorySearch,
                                onSearchChange = { categorySearch = it },
                                newCategoryName = categoryName,
                                onNewCategoryNameChange = { categoryName = it },
                                loading = libraryLoading,
                                error = libraryError,
                                onCreateCategory = {
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
                                onRenameCategory = { categoryId, name ->
                                    scope.launch {
                                        runCatching {
                                            withContext(Dispatchers.IO) {
                                                session.library.renameCategory(categoryId, name)
                                            }
                                        }.onSuccess {
                                            refreshLibrary()
                                            message = "Category renamed"
                                        }.onFailure {
                                            notice.error(it.message ?: "Could not rename category")
                                        }
                                    }
                                },
                                onDeleteCategory = { categoryId ->
                                    scope.launch {
                                        runCatching {
                                            withContext(Dispatchers.IO) {
                                                session.library.deleteCategory(categoryId)
                                            }
                                        }.onSuccess {
                                            if (selectedCategory == categoryId) selectedCategory = null
                                            refreshLibrary()
                                            message = "Category deleted"
                                        }.onFailure {
                                            notice.error(it.message ?: "Could not delete category")
                                        }
                                    }
                                },
                                onMoveCategory = { categoryId, offset ->
                                    scope.launch {
                                        runCatching {
                                            withContext(Dispatchers.IO) {
                                                session.library.moveCategory(categoryId, offset)
                                            }
                                        }.onSuccess {
                                            refreshLibrary()
                                            message = "Category order updated"
                                        }.onFailure {
                                            notice.error(it.message ?: "Could not reorder category")
                                        }
                                    }
                                },
                                onOpenCategory = { categoryId ->
                                    selectedCategory = categoryId
                                    librarySearch = ""
                                    navigateToScreen(Screen.LIBRARY)
                                },
                            )
                        }
                        screen == Screen.HISTORY -> {
                            RoninHistoryScreen(
                                entries = historyEntries,
                                chapters = historyChapters,
                                library = library,
                                sources = sources,
                                downloads = downloads,
                                search = historySearch,
                                onSearchChange = { historySearch = it },
                                loading = libraryLoading,
                                error = libraryError,
                                onRetry = ::refreshLibrary,
                                onResume = { entry, chapter ->
                                    if (chapter != null) {
                                        openStoredManga(
                                            mangaId = entry.mangaId,
                                            chapterUrl = chapter.url,
                                            readerReturnTo = Screen.HISTORY,
                                        )
                                    } else {
                                        notice.error("The saved chapter is no longer available")
                                    }
                                },
                            )
                        }
                        screen == Screen.UPDATES -> {
                            RoninUpdatesScreen(
                                entries = updateEntries,
                                library = library,
                                sources = sources,
                                downloads = downloads,
                                loading = libraryLoading,
                                error = libraryError,
                                updateRunning = libraryUpdate.running,
                                updateMessage = libraryUpdate.message,
                                updateFailures = libraryUpdate.failures,
                                onCheckUpdates = session.libraryUpdates::updateNow,
                                onRetry = ::refreshLibrary,
                                onRead = { entry ->
                                    openStoredManga(
                                        mangaId = entry.mangaId,
                                        chapterUrl = entry.chapterUrl,
                                        readerReturnTo = Screen.UPDATES,
                                    )
                                },
                                onDownload = { entry ->
                                    queueStoredChapter(entry.mangaId, entry.chapterUrl)
                                },
                                onResumeDownload = { item ->
                                    session.downloads.resume(item.key)
                                },
                            )
                        }
                        screen == Screen.SETTINGS -> {
                            Row(
                                Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
                            ) {
                                DesktopSettingsNavigation(
                                    selected = settingsSection,
                                    onSelect = { settingsSection = it },
                                    modifier = Modifier.width(if (compactNavigation) 164.dp else 196.dp).fillMaxHeight()
                                        .background(RoninColors.elevatedSurface, RoundedCornerShape(RoninRadius.panel)),
                                )
                                Column(
                                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
                                ) {
                                    MihonSectionHeader(settingsSection.title, settingsSection.description)
                                    when (settingsSection) {
                                        DesktopSettingsSection.GENERAL -> RoninPanel(Modifier.fillMaxWidth()) {
                                            Column(
                                                Modifier.fillMaxWidth().padding(RoninSpacing.large),
                                                verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                                            ) {
                                                Text("Ronin for Windows", style = MaterialTheme.typography.titleMedium)
                                                Text(
                                                    "Language follows Windows. " +
                                                        "Library and reader preferences are saved on this device.",
                                                    color = RoninColors.textMuted,
                                                )
                                                Text(
                                                    "Language: ${graph.localeService.currentLanguageTag()}",
                                                    color = RoninColors.textMuted,
                                                )
                                            }
                                        }
                                        DesktopSettingsSection.STORAGE -> {
                                            RoninPanel(Modifier.fillMaxWidth()) {
                                                Column(
                                                    Modifier.fillMaxWidth().padding(14.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                                ) {
                                                    Text("Migration", style = MaterialTheme.typography.titleMedium)
                                                    Text(
                                                        "Import a Mihon Android .tachibk backup. " +
                                                            "Matching manga records are updated.",
                                                        color = RoninColors.textMuted,
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
                                            RoninPanel(Modifier.fillMaxWidth()) {
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
                                                        color = RoninColors.textMuted,
                                                    )
                                                    Text(
                                                        "Database: ${graph.appDirectories.database}",
                                                        color = RoninColors.textMuted,
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
                                        DesktopSettingsSection.LIBRARY -> RoninPanel(Modifier.fillMaxWidth()) {
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
                                                                "while Ronin is open"
                                                        },
                                                    )
                                                }
                                                Text(
                                                    "Downloads wait for an active network connection.",
                                                    color = RoninColors.textMuted,
                                                )
                                            }
                                        }
                                        DesktopSettingsSection.READER -> DesktopReaderDefaultsSettings(graph)
                                        DesktopSettingsSection.DOWNLOADS -> RoninPanel(Modifier.fillMaxWidth()) {
                                            Column(
                                                Modifier.fillMaxWidth().padding(RoninSpacing.large),
                                                verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                                            ) {
                                                Text("Download queue", style = MaterialTheme.typography.titleMedium)
                                                Text(
                                                    "Downloads pause when the network is unavailable " +
                                                        "and resume from the queue.",
                                                    color = RoninColors.textMuted,
                                                )
                                                TextButton(onClick = { navigateToScreen(Screen.DOWNLOADS) }) {
                                                    Text("Open downloads")
                                                }
                                            }
                                        }
                                        DesktopSettingsSection.ADVANCED -> RoninPanel(Modifier.fillMaxWidth()) {
                                            Column(
                                                Modifier.fillMaxWidth().padding(RoninSpacing.large),
                                                verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                                            ) {
                                                Text("Windows links", style = MaterialTheme.typography.titleMedium)
                                                Text(
                                                    "Ronin keeps compatibility with existing mihon:// manga and " +
                                                        "tracker callback links for this Windows account.",
                                                    color = RoninColors.textMuted,
                                                )
                                                Text(
                                                    "Status: " +
                                                        if (mihonProtocolRegistered) "registered" else "not registered",
                                                    color = RoninColors.textMuted,
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
                                                                "Ronin browser-link compatibility registered for this Windows user"
                                                            } else {
                                                                "Ronin browser-link compatibility unregistered"
                                                            }
                                                        }.onFailure {
                                                            notice.error(
                                                                it.message
                                                                    ?: "Could not update Ronin link compatibility",
                                                            )
                                                        }
                                                    }) {
                                                        Text(
                                                            if (mihonProtocolRegistered) {
                                                                "Unregister browser links"
                                                            } else {
                                                                "Register browser links"
                                                            },
                                                        )
                                                    }
                                                }
                                                HorizontalDivider()
                                                Text("Desktop extensions", style = MaterialTheme.typography.titleMedium)
                                                Text(
                                                    "A locally trusted signing fingerprint records a local trust " +
                                                        "decision; it does not verify a publisher identity.",
                                                    color = RoninColors.textMuted,
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
                                            onClick = { session.downloads.clearFinished() },
                                            enabled = downloads.any { it.status == DesktopDownloadStatus.COMPLETED },
                                        ) { Text("Clear finished") }
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
                            val visibleDownloads = if (downloadQuery.isBlank()) {
                                downloads
                            } else {
                                downloads.filter { item ->
                                    item.mangaTitle.contains(downloadQuery, ignoreCase = true) ||
                                        item.chapterName.contains(downloadQuery, ignoreCase = true)
                                }
                            }
                            if (downloads.isNotEmpty()) {
                                OutlinedTextField(
                                    value = downloadQuery,
                                    onValueChange = { downloadQuery = it },
                                    label = { Text("Search downloads") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            if (downloads.isEmpty()) {
                                MihonEmptyState(
                                    "No downloads",
                                    "Queue a chapter from its manga details or the Updates screen.",
                                )
                            } else if (visibleDownloads.isEmpty()) {
                                MihonEmptyState(
                                    "No matching downloads",
                                    "Try a different manga or chapter name.",
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
                                        val grouped = visibleDownloads.filter { it.status in statuses }
                                        if (grouped.isNotEmpty()) {
                                            item {
                                                Text(
                                                    heading,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = RoninColors.accentSage,
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
