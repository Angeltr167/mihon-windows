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
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.core.extension.desktop.DesktopRepositoryEntry
import mihon.platform.api.OpenFileRequest
import mihon.platform.desktop.DesktopPlatformGraph
import tachiyomi.data.Mangas
import tachiyomi.source.local.desktop.DesktopLocalSource
import java.net.URI
import java.nio.file.Path

private enum class Screen(val title: String) {
    LIBRARY("Library"),
    UPDATES("Updates"),
    HISTORY("History"),
    SOURCES("Browse / Sources"),
    SEARCH("Search"),
    EXTENSIONS("Extensions"),
    CATEGORIES("Categories"),
    SETTINGS("Settings"),
    DOWNLOADS("Downloads"),
}

@Composable
fun DesktopShell(
    graph: DesktopPlatformGraph,
    initialLink: String? = null,
    onToggleFullscreen: () -> Unit = {},
) {
    val session = remember { DesktopSession(graph) }
    DisposableEffect(session) { onDispose(session::close) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
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
    var fingerprint by remember { mutableStateOf("") }
    var categoryName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val downloads by session.downloads.queue.collectAsState()
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

    LaunchedEffect(initialLink) {
        if (!initialLink.isNullOrBlank()) openLink(initialLink)
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
                            ) { Text("${index + 1}  ${item.title}") }
                        }
                    }
                }
                Column(Modifier.fillMaxSize().padding(if (readerTarget == null) 20.dp else 8.dp)) {
                    if (readerTarget == null) {
                        Text(
                            selectedManga?.title ?: screen.title,
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
                                Text("Desktop .mihonext packages only. Trust authors before installing their code.")
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
                                            sources = session.sources()
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
                                                        session.sources()
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
                            Text("Language: ${graph.localeService.currentLanguageTag()}")
                            Text("Library: ${graph.appDirectories.localLibrary}")
                            Text("Database: ${graph.appDirectories.database}")
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
