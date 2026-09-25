package mihon.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import mihon.core.reader.FitMode
import mihon.core.reader.ReaderPageLoadState
import mihon.core.reader.ReadingMode
import mihon.core.reader.nextPageToPreload
import mihon.core.reader.viewedPageIndex
import mihon.platform.desktop.DesktopPlatformGraph
import kotlin.math.roundToInt

internal data class ReaderTarget(
    val source: Source,
    val manga: SManga,
    val chapters: List<SChapter>,
    val selectedChapterUrl: String,
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun DesktopReader(
    session: DesktopSession,
    graph: DesktopPlatformGraph,
    target: ReaderTarget,
    onClose: () -> Unit,
    onToggleFullscreen: () -> Unit,
) {
    val chapters = remember(target) { target.chapters.asReversed() }
    val loader = remember(session) { DesktopPageLoader(session.localPages, session.downloads.store) }
    val focusRequester = remember(target) { FocusRequester() }
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
    var chapterIndex by remember(target) {
        mutableIntStateOf(chapters.indexOfFirst { it.url == target.selectedChapterUrl }.coerceAtLeast(0))
    }
    var pageIndex by remember(target) { mutableIntStateOf(0) }
    var pages by remember(target) { mutableStateOf<List<DesktopPage>>(emptyList()) }
    var chapterId by remember(target) { mutableStateOf<Long?>(null) }
    var mangaId by remember(target) { mutableStateOf<Long?>(null) }
    var trackerSyncAttempted by remember(target) { mutableStateOf(false) }
    var trackerError by remember(target) { mutableStateOf<String?>(null) }
    val pendingTrackerSync by session.trackerSync.pending.collectAsState()
    var loadedChapterIndex by remember(target) { mutableIntStateOf(-1) }
    var loadState by remember(target) { mutableStateOf<ReaderPageLoadState>(ReaderPageLoadState.Loading) }
    var retry by remember(target) { mutableIntStateOf(0) }
    var openAtLastPage by remember(target) { mutableStateOf(false) }
    var mode by remember(target) {
        mutableStateOf(
            runCatching {
                ReadingMode.valueOf(
                    graph.keyValueStore.getString("desktop.reader.mode", ReadingMode.SINGLE_LTR.name)
                        ?: ReadingMode.SINGLE_LTR.name,
                )
            }
                .getOrDefault(ReadingMode.SINGLE_LTR),
        )
    }
    var fit by remember(target) {
        mutableStateOf(
            runCatching {
                FitMode.valueOf(
                    graph.keyValueStore.getString("desktop.reader.fit", FitMode.WIDTH.name) ?: FitMode.WIDTH.name,
                )
            }
                .getOrDefault(FitMode.WIDTH),
        )
    }
    var zoom by remember(target) { mutableStateOf(1f) }
    var imageFilter by remember(target) {
        mutableStateOf(
            ReaderImageFilter.entries.firstOrNull {
                it.name == graph.keyValueStore.getString("desktop.reader.imageFilter", ReaderImageFilter.NORMAL.name)
            } ?: ReaderImageFilter.NORMAL,
        )
    }
    var brightness by remember(target) {
        mutableIntStateOf(graph.keyValueStore.getLong("desktop.reader.brightness", 0).toInt().coerceIn(-100, 100))
    }
    val colorFilter = remember(imageFilter, brightness) { imageFilter.colorFilter(brightness) }
    var requestedScroll by remember(target) { mutableStateOf<Int?>(null) }
    var showAppearance by remember(target) { mutableStateOf(false) }
    var showShortcuts by remember(target) { mutableStateOf(false) }
    var shortcuts by remember(target) {
        val next = ReaderShortcutKey.restore(
            graph.keyValueStore.getString("desktop.reader.shortcut.next", null),
            ReaderShortcutKey.J,
        )
        val previous = ReaderShortcutKey.restore(
            graph.keyValueStore.getString("desktop.reader.shortcut.previous", null),
            ReaderShortcutKey.K,
        )
        mutableStateOf(
            ReaderShortcuts(
                next,
                previous.takeUnless {
                    it == next
                } ?: ReaderShortcutKey.K.nextExcept(next),
            ),
        )
    }

    if (showShortcuts) {
        AlertDialog(
            onDismissRequest = { showShortcuts = false },
            title = { Text("Reader shortcuts") },
            text = {
                Column {
                    Text("Arrow keys, Page Up/Down, Space and F11 always work.")
                    TextButton(onClick = {
                        shortcuts = shortcuts.copy(next = shortcuts.next.nextExcept(shortcuts.previous))
                        graph.keyValueStore.putString("desktop.reader.shortcut.next", shortcuts.next.name)
                    }) { Text("Next page: ${shortcuts.next.name}") }
                    TextButton(onClick = {
                        shortcuts = shortcuts.copy(previous = shortcuts.previous.nextExcept(shortcuts.next))
                        graph.keyValueStore.putString("desktop.reader.shortcut.previous", shortcuts.previous.name)
                    }) { Text("Previous page: ${shortcuts.previous.name}") }
                }
            },
            confirmButton = { TextButton(onClick = { showShortcuts = false }) { Text("Done") } },
        )
    }
    if (showAppearance) {
        AlertDialog(
            onDismissRequest = { showAppearance = false },
            title = { Text("Reader appearance") },
            text = {
                Column {
                    TextButton(onClick = {
                        imageFilter =
                            ReaderImageFilter.entries[(imageFilter.ordinal + 1) % ReaderImageFilter.entries.size]
                        graph.keyValueStore.putString("desktop.reader.imageFilter", imageFilter.name)
                    }) { Text("Color: ${imageFilter.name.lowercase()}") }
                    Text("Brightness: $brightness")
                    Slider(
                        value = brightness.toFloat(),
                        onValueChange = { brightness = it.roundToInt() },
                        onValueChangeFinished = {
                            graph.keyValueStore.putLong("desktop.reader.brightness", brightness.toLong())
                        },
                        valueRange = -100f..100f,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showAppearance = false }) { Text("Done") } },
        )
    }

    LaunchedEffect(target, chapterIndex, retry) {
        pages = emptyList()
        chapterId = null
        mangaId = null
        trackerError = null
        loadedChapterIndex = -1
        loadState = ReaderPageLoadState.Loading
        runCatching {
            val loaded = loader.pages(target.source, chapters[chapterIndex], target.manga.url)
            val stored = withContext(Dispatchers.IO) {
                val manga = session.library.ensureManga(target.source.id, target.manga)
                session.library.syncChapters(manga._id, chapters)
                requireNotNull(session.library.chapter(manga._id, chapters[chapterIndex].url))
            }
            loaded to stored
        }.onSuccess { (loaded, stored) ->
            val restored =
                if (openAtLastPage) loaded.lastIndex else stored.last_page_read.toInt().coerceIn(0, loaded.lastIndex)
            pageIndex =
                if (mode == ReadingMode.DOUBLE_LTR || mode == ReadingMode.DOUBLE_RTL) restored / 2 * 2 else restored
            pages = loaded
            chapterId = stored._id
            mangaId = stored.manga_id
            trackerSyncAttempted = stored.read
            loadedChapterIndex = chapterIndex
            openAtLastPage = false
            loadState = ReaderPageLoadState.Ready(loaded.size)
        }.onFailure {
            if (it is CancellationException) throw it
            loadState = ReaderPageLoadState.Failed(it.message ?: "Chapter could not be loaded", retry)
        }
    }

    LaunchedEffect(chapterId, chapterIndex, loadedChapterIndex, pageIndex, pages.size, mode) {
        val id = chapterId ?: return@LaunchedEffect
        if (loadedChapterIndex != chapterIndex) return@LaunchedEffect
        if (pages.isEmpty()) return@LaunchedEffect
        val viewedIndex = viewedPageIndex(pageIndex, pages.size, mode)
        withContext(Dispatchers.IO) { session.library.saveProgress(id, viewedIndex, pages.size) }
        if (viewedIndex == pages.lastIndex && !trackerSyncAttempted) {
            trackerSyncAttempted = true
            mangaId?.let { mangaId ->
                runCatching {
                    val number = chapters[chapterIndex].chapter_number.toDouble()
                    if (number.isFinite() && number > 0 && withContext(Dispatchers.IO) {
                            session.library.track(mangaId, DesktopKomgaTracker.TRACKER_ID) != null ||
                                session.library.track(mangaId, DesktopAniListTracker.TRACKER_ID) != null ||
                                session.library.track(mangaId, DesktopSuwayomiTracker.TRACKER_ID) != null
                        }
                    ) {
                        session.trackerSync.enqueue(mangaId, number)
                    }
                }.onFailure {
                    if (it is CancellationException) throw it
                    trackerError = it.message ?: "Tracker sync failed"
                }
            }
        }
    }

    LaunchedEffect(chapterIndex, pageIndex, pages, mode) {
        nextPageToPreload(pageIndex, pages.size, mode)?.let { next ->
            try {
                loader.prefetch(pages[next])
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Visible page loading reports its own retryable error.
            }
        }
    }

    fun next() {
        val step = if (mode == ReadingMode.DOUBLE_LTR || mode == ReadingMode.DOUBLE_RTL) 2 else 1
        if (pageIndex + step < pages.size) {
            pageIndex += step
            if (mode == ReadingMode.VERTICAL || mode == ReadingMode.WEBTOON) {
                requestedScroll = pageIndex
            }
        } else if (chapterIndex < chapters.lastIndex) {
            pageIndex = 0
            chapterIndex++
        }
    }

    fun previous() {
        val step = if (mode == ReadingMode.DOUBLE_LTR || mode == ReadingMode.DOUBLE_RTL) 2 else 1
        if (pageIndex > 0) {
            pageIndex = (pageIndex - step).coerceAtLeast(0)
            if (mode == ReadingMode.VERTICAL || mode == ReadingMode.WEBTOON) {
                requestedScroll = pageIndex
            }
        } else if (chapterIndex > 0) {
            openAtLastPage = true
            chapterIndex--
        }
    }

    val rightToLeft = mode == ReadingMode.SINGLE_RTL || mode == ReadingMode.DOUBLE_RTL
    Column(
        Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (shortcuts.action(event.key)) {
                ReaderShortcuts.Action.NEXT -> {
                    next()
                    return@onPreviewKeyEvent true
                }
                ReaderShortcuts.Action.PREVIOUS -> {
                    previous()
                    return@onPreviewKeyEvent true
                }
                null -> Unit
            }
            when (event.key) {
                Key.DirectionRight -> if (rightToLeft) previous() else next()
                Key.DirectionLeft -> if (rightToLeft) next() else previous()
                Key.PageDown, Key.Spacebar -> next()
                Key.PageUp -> previous()
                Key.F11 -> onToggleFullscreen()
                Key.Escape -> onClose()
                else -> return@onPreviewKeyEvent false
            }
            true
        }.focusRequester(focusRequester).focusable(),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onClose) { Text("Back") }
            Text(chapters[chapterIndex].name, modifier = Modifier.weight(1f).padding(top = 12.dp))
            TextButton(onClick = {
                mode = ReadingMode.entries[(mode.ordinal + 1) % ReadingMode.entries.size]
                if (mode == ReadingMode.DOUBLE_LTR || mode == ReadingMode.DOUBLE_RTL) pageIndex = pageIndex / 2 * 2
                graph.keyValueStore.putString("desktop.reader.mode", mode.name)
            }) { Text(mode.name.replace('_', ' ')) }
            TextButton(onClick = {
                fit = FitMode.entries[(fit.ordinal + 1) % FitMode.entries.size]
                graph.keyValueStore.putString("desktop.reader.fit", fit.name)
            }) { Text("Fit ${fit.name.lowercase()}") }
            TextButton(onClick = { zoom = (zoom - 0.25f).coerceAtLeast(0.5f) }) { Text("−") }
            TextButton(onClick = { zoom = (zoom + 0.25f).coerceAtMost(4f) }) { Text("+") }
            TextButton(onClick = { showAppearance = true }) { Text("Appearance") }
            TextButton(onClick = onToggleFullscreen) { Text("Fullscreen") }
            TextButton(onClick = { showShortcuts = true }) { Text("Keys") }
        }
        (trackerError ?: pendingTrackerSync.firstOrNull { it.mangaId == mangaId }?.error)
            ?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        when {
            loadState is ReaderPageLoadState.Failed -> Column {
                Text((loadState as ReaderPageLoadState.Failed).reason, color = MaterialTheme.colorScheme.error)
                Button(onClick = { retry++ }) { Text("Retry") }
            }
            loadState is ReaderPageLoadState.Loading -> Text("Loading pages…")
            mode == ReadingMode.VERTICAL || mode == ReadingMode.WEBTOON -> {
                val listState = rememberLazyListState()
                LaunchedEffect(chapterIndex, pages) {
                    listState.scrollToItem(pageIndex)
                    snapshotFlow { listState.firstVisibleItemIndex }.collectLatest { visible ->
                        if (requestedScroll == null) {
                            pageIndex = visible.coerceIn(pages.indices)
                        }
                    }
                }
                LaunchedEffect(requestedScroll, pages) {
                    requestedScroll?.let { destination ->
                        listState.animateScrollToItem(destination.coerceIn(pages.indices))
                        requestedScroll = null
                    }
                }
                BoxWithConstraints(Modifier.weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = maxHeight),
                    ) {
                        itemsIndexed(pages) { index, page ->
                            ReaderImage(loader, page, fit, zoom, colorFilter, Modifier.fillMaxWidth(), index)
                        }
                    }
                }
            }
            else -> {
                val pair = if (mode == ReadingMode.DOUBLE_LTR || mode == ReadingMode.DOUBLE_RTL) {
                    pages.drop(pageIndex).take(2).mapIndexed { offset, page -> (pageIndex + offset) to page }
                } else {
                    listOf(pageIndex to pages[pageIndex])
                }
                BoxWithConstraints(
                    Modifier.weight(1f).fillMaxWidth()
                        .pointerInput(pageIndex, mode) {
                            detectTapGestures { offset ->
                                if (offset.x < size.width / 3) {
                                    if (rightToLeft) next() else previous()
                                } else if (offset.x > size.width * 2 / 3) {
                                    if (rightToLeft) previous() else next()
                                }
                            }
                        }
                        .onPointerEvent(PointerEventType.Scroll) { event ->
                            val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            if (delta > 0) {
                                next()
                            } else if (delta < 0) {
                                previous()
                            }
                        },
                ) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) {
                        (if (rightToLeft) pair.reversed() else pair).forEach { (index, page) ->
                            ReaderImage(
                                loader,
                                page,
                                fit,
                                zoom,
                                colorFilter,
                                Modifier.weight(1f).fillMaxHeight(),
                                index,
                            )
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                if (chapterIndex >
                    0
                ) {
                    openAtLastPage = false
                    chapterIndex--
                }
            }, enabled = chapterIndex > 0) {
                Text("Previous chapter")
            }
            TextButton(onClick = ::previous) { Text("Previous page") }
            Text("${pageIndex + 1} / ${pages.size}", modifier = Modifier.padding(top = 12.dp))
            TextButton(onClick = ::next) { Text("Next page") }
            TextButton(
                onClick = {
                    if (chapterIndex <
                        chapters.lastIndex
                    ) {
                        openAtLastPage = false
                        chapterIndex++
                    }
                },
                enabled =
                chapterIndex < chapters.lastIndex,
            ) {
                Text("Next chapter")
            }
        }
    }
}

@Composable
private fun ReaderImage(
    loader: DesktopPageLoader,
    page: DesktopPage,
    fit: FitMode,
    zoom: Float,
    colorFilter: ColorFilter?,
    modifier: Modifier,
    index: Int,
) {
    var retry by remember(page) { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val result by produceState<Result<ImageBitmap>?>(null, page, retry) {
        val loaded = runCatching { loader.image(page) }
        loaded.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        value = loaded
    }
    var pan by remember(page) { mutableStateOf(Offset.Zero) }
    Box(modifier, contentAlignment = Alignment.Center) {
        result?.fold(
            onSuccess = { image ->
                val imageModifier = when (fit) {
                    FitMode.WIDTH -> Modifier.fillMaxWidth()
                    FitMode.HEIGHT -> Modifier.fillMaxHeight()
                    FitMode.ORIGINAL -> Modifier.width(with(density) { image.width.toDp() })
                }
                Image(
                    bitmap = image,
                    contentDescription = "Page ${index + 1}",
                    contentScale = ContentScale.Fit,
                    colorFilter = colorFilter,
                    modifier = imageModifier.graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = pan.x
                        translationY = pan.y
                    }.pointerInput(page, zoom) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            pan += drag
                        }
                    },
                )
            },
            onFailure = { failure ->
                Text("Page ${index + 1}: ${failure.message ?: "failed"} — retry", Modifier.clickable { retry++ })
            },
        ) ?: Text("Loading page ${index + 1}…")
    }
}

private enum class ReaderImageFilter {
    NORMAL,
    GRAYSCALE,
    INVERT,
    ;

    fun colorFilter(brightness: Int): ColorFilter? {
        if (this == NORMAL && brightness == 0) return null
        val offset = brightness * 255f / 100f
        val matrix = when (this) {
            NORMAL -> floatArrayOf(
                1f, 0f, 0f, 0f, offset,
                0f, 1f, 0f, 0f, offset,
                0f, 0f, 1f, 0f, offset,
                0f, 0f, 0f, 1f, 0f,
            )
            GRAYSCALE -> floatArrayOf(
                0.213f, 0.715f, 0.072f, 0f, offset,
                0.213f, 0.715f, 0.072f, 0f, offset,
                0.213f, 0.715f, 0.072f, 0f, offset,
                0f, 0f, 0f, 1f, 0f,
            )
            INVERT -> floatArrayOf(
                -1f, 0f, 0f, 0f, 255f + offset,
                0f, -1f, 0f, 0f, 255f + offset,
                0f, 0f, -1f, 0f, 255f + offset,
                0f, 0f, 0f, 1f, 0f,
            )
        }
        return ColorFilter.colorMatrix(ColorMatrix(matrix))
    }
}
