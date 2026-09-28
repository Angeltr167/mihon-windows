package mihon.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.compose.stringResource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.core.reader.FitMode
import mihon.core.reader.ReaderPageLoadState
import mihon.core.reader.ReadingMode
import mihon.core.reader.nextPageToPreload
import mihon.desktop.design.RoninReaderMetrics
import mihon.platform.desktop.DesktopPlatformGraph
import tachiyomi.i18n.MR
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.roundToInt

/** First page stands alone; following pages are paired without dropping an odd final page. */
internal fun doublePageSpread(pageIndex: Int, pageCount: Int): List<Int> {
    require(pageCount > 0 && pageIndex in 0 until pageCount)
    if (pageIndex == 0) return listOf(0)
    val first = 1 + ((pageIndex - 1) / 2) * 2
    return (first..minOf(first + 1, pageCount - 1)).toList()
}

internal fun normalizeReaderPageIndex(mode: ReadingMode, pageIndex: Int, pageCount: Int): Int {
    require(pageCount > 0 && pageIndex in 0 until pageCount)
    return if (mode == ReadingMode.DOUBLE_LTR || mode == ReadingMode.DOUBLE_RTL) {
        doublePageSpread(pageIndex, pageCount).first()
    } else {
        pageIndex
    }
}

internal fun viewedReaderPageIndex(mode: ReadingMode, pageIndex: Int, pageCount: Int): Int =
    if (mode == ReadingMode.DOUBLE_LTR || mode == ReadingMode.DOUBLE_RTL) {
        doublePageSpread(pageIndex, pageCount).last()
    } else {
        pageIndex
    }

internal fun nextReaderPageIndex(mode: ReadingMode, pageIndex: Int, pageCount: Int): Int? {
    val next = if (mode == ReadingMode.DOUBLE_LTR || mode == ReadingMode.DOUBLE_RTL) {
        doublePageSpread(pageIndex, pageCount).last() + 1
    } else {
        pageIndex + 1
    }
    return next.takeIf { it < pageCount }?.let { normalizeReaderPageIndex(mode, it, pageCount) }
}

internal fun previousReaderPageIndex(mode: ReadingMode, pageIndex: Int, pageCount: Int): Int? {
    if (pageIndex == 0) return null
    val step = if (mode == ReadingMode.DOUBLE_LTR || mode == ReadingMode.DOUBLE_RTL) 2 else 1
    return normalizeReaderPageIndex(mode, (pageIndex - step).coerceAtLeast(0), pageCount)
}

internal data class ReaderSpreadGeometry(val height: Float, val widths: List<Float>)

internal data class ReaderWheelResult(val remainder: Float, val direction: Int?)

internal enum class ReaderChapterOpenIntent {
    RESTORE,
    FIRST,
    LAST,
}

internal fun readerStartPage(
    intent: ReaderChapterOpenIntent,
    storedPage: Int,
    pageCount: Int,
): Int {
    require(pageCount > 0)
    return when (intent) {
        ReaderChapterOpenIntent.RESTORE -> storedPage.coerceIn(0, pageCount - 1)
        ReaderChapterOpenIntent.FIRST -> 0
        ReaderChapterOpenIntent.LAST -> pageCount - 1
    }
}

internal fun readingModeLabel(mode: ReadingMode): String = when (mode) {
    ReadingMode.SINGLE_LTR -> "Single · LTR"
    ReadingMode.SINGLE_RTL -> "Single · RTL"
    ReadingMode.DOUBLE_LTR -> "Double · LTR"
    ReadingMode.DOUBLE_RTL -> "Double · RTL"
    ReadingMode.VERTICAL -> "Vertical"
    ReadingMode.WEBTOON -> "Webtoon"
}

internal fun fitModeLabel(fit: FitMode): String = when (fit) {
    FitMode.WIDTH -> "Fit width"
    FitMode.HEIGHT -> "Fit height"
    FitMode.ORIGINAL -> "Original size"
}

internal fun shouldAutoHideReaderControls(
    showAppearance: Boolean,
    showShortcuts: Boolean,
    showMore: Boolean,
    showModeMenu: Boolean,
    showFitMenu: Boolean,
    sliderDragging: Boolean,
    controlsHovered: Boolean,
): Boolean = !showAppearance &&
    !showShortcuts &&
    !showMore &&
    !showModeMenu &&
    !showFitMenu &&
    !sliderDragging &&
    !controlsHovered

/** Accumulate touchpad-sized deltas and emit at most one page direction per threshold crossing. */
internal fun accumulateReaderWheelDelta(
    accumulated: Float,
    delta: Float,
    threshold: Float = 1f,
): ReaderWheelResult {
    require(threshold > 0f)
    val total = accumulated + delta
    return when {
        total >= threshold -> ReaderWheelResult(0f, 1)
        total <= -threshold -> ReaderWheelResult(0f, -1)
        else -> ReaderWheelResult(total, null)
    }
}

/** A previous spread may remain decoded briefly while Compose switches page sets. */
internal fun safeReaderRatios(decodedRatios: List<Float>?, pageCount: Int): List<Float> =
    decodedRatios?.takeIf { ratios ->
        ratios.size == pageCount && ratios.all { it.isFinite() && it > 0f }
    } ?: List(pageCount) { 0.7f }

/** Keep every page complete while joining their inside edges at a shared height. */
internal fun fitReaderSpread(ratios: List<Float>, width: Float, height: Float): ReaderSpreadGeometry {
    require(ratios.isNotEmpty() && ratios.all { it.isFinite() && it > 0f })
    require(width > 0f && height > 0f)
    val fittedHeight = minOf(height, width / ratios.sum())
    return ReaderSpreadGeometry(fittedHeight, ratios.map { it * fittedHeight })
}

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
    val scope = rememberCoroutineScope()
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
    var chapterIndex by remember(target) {
        mutableIntStateOf(chapters.indexOfFirst { it.url == target.selectedChapterUrl }.coerceAtLeast(0))
    }
    var pageIndex by remember(target) { mutableIntStateOf(0) }
    var pages by remember(target) { mutableStateOf<List<DesktopPage>>(emptyList()) }
    var chapterId by remember(target) { mutableStateOf<Long?>(null) }
    var chapterBookmarked by remember(target) { mutableStateOf(false) }
    var mangaId by remember(target) { mutableStateOf<Long?>(null) }
    var trackerSyncAttempted by remember(target) { mutableStateOf(false) }
    var trackerError by remember(target) { mutableStateOf<String?>(null) }
    var pageActionMessage by remember(target) { mutableStateOf<String?>(null) }
    var savedPage by remember(target) { mutableStateOf<Path?>(null) }
    val pendingTrackerSync by session.trackerSync.pending.collectAsState()
    var loadedChapterIndex by remember(target) { mutableIntStateOf(-1) }
    var loadState by remember(target) { mutableStateOf<ReaderPageLoadState>(ReaderPageLoadState.Loading) }
    var retry by remember(target) { mutableIntStateOf(0) }
    var chapterOpenIntent by remember(target) { mutableStateOf(ReaderChapterOpenIntent.RESTORE) }
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
                    graph.keyValueStore.getString("desktop.reader.fit", FitMode.HEIGHT.name) ?: FitMode.HEIGHT.name,
                )
            }
                .getOrDefault(FitMode.HEIGHT),
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
    var controlsVisible by remember(target) { mutableStateOf(true) }
    var showMore by remember(target) { mutableStateOf(false) }
    var showModeMenu by remember(target) { mutableStateOf(false) }
    var showFitMenu by remember(target) { mutableStateOf(false) }
    var sliderDragging by remember(target) { mutableStateOf(false) }
    var controlsHovered by remember(target) { mutableStateOf(false) }
    var sliderTargetPage by remember(target) { mutableStateOf<Int?>(null) }
    var interactionVersion by remember(target) { mutableLongStateOf(0L) }
    var wheelAccumulator by remember(target) { mutableStateOf(0f) }
    var wheelInteractionVersion by remember(target) { mutableLongStateOf(0L) }
    var wheelGestureActive by remember(target) { mutableStateOf(false) }
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
        savedPage = null
        pageActionMessage = null
        chapterId = null
        chapterBookmarked = false
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
            val restored = readerStartPage(
                chapterOpenIntent,
                stored.last_page_read.toInt(),
                loaded.size,
            )
            pageIndex = normalizeReaderPageIndex(mode, restored, loaded.size)
            pages = loaded
            chapterId = stored._id
            chapterBookmarked = stored.bookmark
            mangaId = stored.manga_id
            trackerSyncAttempted = stored.read
            loadedChapterIndex = chapterIndex
            chapterOpenIntent = ReaderChapterOpenIntent.RESTORE
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
        val viewedIndex = viewedReaderPageIndex(mode, pageIndex, pages.size)
        withContext(Dispatchers.IO) { session.library.saveProgress(id, viewedIndex, pages.size) }
        if (viewedIndex == pages.lastIndex && !trackerSyncAttempted) {
            trackerSyncAttempted = true
            mangaId?.let { mangaId ->
                runCatching {
                    val number = chapters[chapterIndex].chapter_number.toDouble()
                    if (number.isFinite() && number > 0 && withContext(Dispatchers.IO) {
                            session.library.track(mangaId, DesktopKomgaTracker.TRACKER_ID) != null ||
                                session.library.track(mangaId, DesktopAniListTracker.TRACKER_ID) != null ||
                                session.library.track(mangaId, DesktopKavitaTracker.TRACKER_ID) != null ||
                                session.library.track(mangaId, DesktopMangaUpdatesTracker.TRACKER_ID) != null ||
                                session.library.track(mangaId, DesktopKitsuTracker.TRACKER_ID) != null ||
                                session.library.track(mangaId, DesktopMyAnimeListTracker.TRACKER_ID) != null ||
                                session.library.track(mangaId, DesktopShikimoriTracker.TRACKER_ID) != null ||
                                session.library.track(mangaId, DesktopHikkaTracker.TRACKER_ID) != null ||
                                session.library.track(mangaId, DesktopBangumiTracker.TRACKER_ID) != null ||
                                session.library.track(mangaId, DesktopMangaBakaTracker.TRACKER_ID) != null ||
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
        if (pages.isEmpty()) return@LaunchedEffect
        val next = if (mode == ReadingMode.DOUBLE_LTR || mode == ReadingMode.DOUBLE_RTL) {
            nextReaderPageIndex(mode, pageIndex, pages.size)
        } else {
            nextPageToPreload(pageIndex, pages.size, mode)
        }
        next?.let {
            try {
                loader.prefetch(pages[it])
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Visible page loading reports its own retryable error.
            }
        }
    }

    fun navigateTo(destination: Int) {
        if (mode == ReadingMode.VERTICAL || mode == ReadingMode.WEBTOON) {
            requestedScroll = destination
        } else {
            pageIndex = destination
        }
    }

    fun next() {
        if (pages.isEmpty()) return
        val destination = nextReaderPageIndex(mode, pageIndex, pages.size)
        if (destination != null) {
            navigateTo(destination)
        } else if (chapterIndex < chapters.lastIndex) {
            chapterOpenIntent = ReaderChapterOpenIntent.FIRST
            chapterIndex++
        }
    }

    fun previous() {
        if (pages.isEmpty()) return
        val destination = previousReaderPageIndex(mode, pageIndex, pages.size)
        if (destination != null) {
            navigateTo(destination)
        } else if (chapterIndex > 0) {
            chapterOpenIntent = ReaderChapterOpenIntent.LAST
            chapterIndex--
        }
    }

    fun navigateByWheel(delta: Float) {
        if (delta == 0f) return
        val result = accumulateReaderWheelDelta(wheelAccumulator, delta)
        wheelAccumulator = result.remainder
        wheelInteractionVersion++
        if (wheelGestureActive) return
        result.direction?.let { direction ->
            wheelGestureActive = true
            if (direction > 0) next() else previous()
        }
    }

    fun saveCurrentPage() {
        if (pages.isEmpty()) return
        val currentIndex = viewedReaderPageIndex(mode, pageIndex, pages.size)
        val current = pages.getOrNull(currentIndex) ?: return
        val folder = graph.fileDialogService.chooseDirectory("Save reader page") ?: return
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = DesktopPageFetcher(session.localPages).bytes(current)
                    val prefix = "mihon-page-${currentIndex + 1}-"
                    val suffix = ".${imageExtension(bytes)}"
                    val output = Files.createTempFile(Path.of(folder), prefix, suffix)
                    Files.write(output, bytes)
                    output
                }
            }.onSuccess {
                savedPage = it
                pageActionMessage = "Saved page: $it"
            }.onFailure { pageActionMessage = it.message ?: "Could not save page" }
        }
    }

    fun toggleChapterBookmark() {
        val id = chapterId ?: return
        val bookmarked = !chapterBookmarked
        chapterBookmarked = bookmarked
        scope.launch {
            withContext(Dispatchers.IO) {
                session.library.setChapterBookmark(id, bookmarked)
            }
        }
    }

    fun previousChapter() {
        if (chapterIndex > 0) {
            chapterOpenIntent = ReaderChapterOpenIntent.FIRST
            chapterIndex--
        }
    }

    fun nextChapter() {
        if (chapterIndex < chapters.lastIndex) {
            chapterOpenIntent = ReaderChapterOpenIntent.FIRST
            chapterIndex++
        }
    }

    val rightToLeft = mode == ReadingMode.SINGLE_RTL || mode == ReadingMode.DOUBLE_RTL
    val bookmarkLabel = stringResource(
        if (chapterBookmarked) MR.strings.action_remove_bookmark else MR.strings.action_bookmark,
    )
    LaunchedEffect(
        controlsVisible,
        interactionVersion,
        showAppearance,
        showShortcuts,
        showMore,
        showModeMenu,
        showFitMenu,
        sliderDragging,
        controlsHovered,
    ) {
        if (
            controlsVisible &&
            shouldAutoHideReaderControls(
                showAppearance = showAppearance,
                showShortcuts = showShortcuts,
                showMore = showMore,
                showModeMenu = showModeMenu,
                showFitMenu = showFitMenu,
                sliderDragging = sliderDragging,
                controlsHovered = controlsHovered,
            )
        ) {
            delay(RoninReaderMetrics.AUTO_HIDE_DELAY_MILLIS)
            controlsVisible = false
        }
    }
    LaunchedEffect(wheelInteractionVersion) {
        if (wheelInteractionVersion == 0L) return@LaunchedEffect
        delay(RoninReaderMetrics.WHEEL_GESTURE_RESET_MILLIS)
        wheelAccumulator = 0f
        wheelGestureActive = false
    }
    Box(
        Modifier.fillMaxSize()
            .background(MihonPalette.graphite)
            .onPointerEvent(PointerEventType.Move) {
                controlsVisible = true
                interactionVersion++
            }
            .onPointerEvent(PointerEventType.Press) {
                controlsVisible = true
                interactionVersion++
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                controlsVisible = true
                interactionVersion++
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
        when {
            loadState is ReaderPageLoadState.Failed -> Box(
                Modifier.fillMaxSize().padding(RoninReaderMetrics.chromeHorizontalMargin),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier.widthIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(MihonSpacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RoninErrorState(
                        title = "Could not load chapter",
                        detail = (loadState as ReaderPageLoadState.Failed).reason,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(MihonSpacing.sm, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(MihonSpacing.sm),
                    ) {
                        RoninSecondaryButton(
                            label = "Previous chapter",
                            onClick = ::previousChapter,
                            enabled = chapterIndex > 0,
                        )
                        RoninButton(
                            label = "Retry",
                            onClick = {
                                retry++
                                controlsVisible = true
                            },
                        )
                        RoninSecondaryButton(
                            label = "Next chapter",
                            onClick = ::nextChapter,
                            enabled = chapterIndex < chapters.lastIndex,
                        )
                    }
                }
            }
            loadState is ReaderPageLoadState.Loading -> Box(
                Modifier.fillMaxSize().padding(RoninReaderMetrics.chromeHorizontalMargin),
                contentAlignment = Alignment.Center,
            ) {
                RoninLoadingState(
                    title = "Loading chapter",
                    detail = chapters[chapterIndex].name,
                    modifier = Modifier.widthIn(max = 520.dp),
                )
            }
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
                        pageIndex = listState.firstVisibleItemIndex.coerceIn(pages.indices)
                        sliderTargetPage = null
                    }
                }
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        contentPadding = PaddingValues(bottom = maxHeight),
                        verticalArrangement = Arrangement.spacedBy(if (mode == ReadingMode.VERTICAL) 16.dp else 0.dp),
                    ) {
                        itemsIndexed(pages) { index, page ->
                            ReaderImage(loader, page, fit, zoom, colorFilter, Modifier.fillMaxWidth(), index, maxHeight)
                        }
                    }
                }
            }
            else -> {
                val pair = if (mode == ReadingMode.DOUBLE_LTR || mode == ReadingMode.DOUBLE_RTL) {
                    doublePageSpread(pageIndex, pages.size).map { it to pages[it] }
                } else {
                    listOf(pageIndex to pages[pageIndex])
                }
                var decodedPages by remember(pair) { mutableStateOf<List<ImageBitmap>?>(null) }
                LaunchedEffect(pair) {
                    decodedPages = try {
                        pair.map { (_, page) -> loader.image(page) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                }
                BoxWithConstraints(
                    Modifier.fillMaxSize()
                        .background(MihonPalette.graphite)
                        .pointerInput(pageIndex, mode) {
                            detectTapGestures { offset ->
                                if (offset.x < size.width / 3) {
                                    if (rightToLeft) next() else previous()
                                } else if (offset.x > size.width * 2 / 3) {
                                    if (rightToLeft) previous() else next()
                                } else {
                                    controlsVisible = !controlsVisible
                                    interactionVersion++
                                }
                            }
                        }
                        .onPointerEvent(PointerEventType.Scroll) { event ->
                            controlsVisible = true
                            interactionVersion++
                            val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            if (!(pair.size == 1 && fit == FitMode.WIDTH)) {
                                navigateByWheel(delta)
                            }
                        },
                ) {
                    val availableWidth = maxWidth
                    val availableHeight = maxHeight
                    Box(Modifier.fillMaxSize().background(MihonPalette.graphite)) {
                        val isSpread = pair.size == 2
                        val pageRatios = safeReaderRatios(
                            decodedPages?.map { it.width.toFloat() / it.height },
                            pair.size,
                        )
                        val geometry = fitReaderSpread(
                            pageRatios,
                            (availableWidth - RoninReaderMetrics.pageHorizontalMargin * 2).coerceAtLeast(1.dp).value,
                            (availableHeight - RoninReaderMetrics.pageVerticalMargin * 2).coerceAtLeast(1.dp).value,
                        )
                        val spreadHeight = geometry.height.dp
                        if (pair.size == 1 && fit == FitMode.WIDTH) {
                            val width =
                                (availableWidth - RoninReaderMetrics.pageHorizontalMargin * 2).coerceAtLeast(1.dp)
                            val height = width / pageRatios.single()
                            Column(
                                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(Modifier.width(width).height(height), contentAlignment = Alignment.Center) {
                                    ReaderImage(
                                        loader,
                                        pair.single().second,
                                        fit,
                                        zoom,
                                        colorFilter,
                                        Modifier.fillMaxSize(),
                                        pair.single().first,
                                    )
                                }
                            }
                        } else {
                            Row(
                                Modifier.align(Alignment.Center).padding(
                                    horizontal = RoninReaderMetrics.pageHorizontalMargin,
                                    vertical = RoninReaderMetrics.pageVerticalMargin,
                                ),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                (if (rightToLeft) pair.reversed() else pair).forEachIndexed { position, (index, page) ->
                                    val originalIndex = if (rightToLeft) pair.lastIndex - position else position
                                    val displayedHeight = if (fit == FitMode.ORIGINAL && !isSpread) {
                                        val original = decodedPages?.getOrNull(originalIndex)
                                        if (original == null) {
                                            spreadHeight
                                        } else {
                                            minOf(
                                                spreadHeight,
                                                with(LocalDensity.current) { original.height.toDp() },
                                            )
                                        }
                                    } else {
                                        spreadHeight
                                    }
                                    val displayedWidth = if (displayedHeight == spreadHeight) {
                                        geometry.widths[originalIndex].dp
                                    } else {
                                        displayedHeight * pageRatios[originalIndex]
                                    }
                                    Box(
                                        Modifier.width(displayedWidth).height(displayedHeight)
                                            .shadow(if (isSpread) 8.dp else 12.dp, RoundedCornerShape(MihonRadius.control))
                                            .background(MihonPalette.graphite)
                                            .border(
                                                1.dp,
                                                MihonPalette.outlineSoft,
                                                RoundedCornerShape(MihonRadius.control),
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        ReaderImage(loader, page, fit, zoom, colorFilter, Modifier.fillMaxSize(), index)
                                    }
                                }
                            }
                            if (isSpread) {
                                Box(
                                    Modifier.align(Alignment.Center).height(spreadHeight)
                                        .width(RoninReaderMetrics.pageSeamWidth)
                                        .background(MihonPalette.graphite.copy(alpha = 0.92f)),
                                )
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.TopCenter)
                .widthIn(max = 1180.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
        ) {
            MihonPanel(
                Modifier.fillMaxWidth()
                    .onPointerEvent(PointerEventType.Enter) {
                        controlsHovered = true
                        controlsVisible = true
                    }
                    .onPointerEvent(PointerEventType.Exit) {
                        controlsHovered = false
                        interactionVersion++
                    },
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(MihonSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onClose) { Text("←") }
                    Column(Modifier.weight(1f)) {
                        Text(
                            target.manga.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                        )
                        Text(
                            chapters[chapterIndex].name,
                            color = MihonPalette.muted,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                        )
                    }
                    Text(
                        if (pages.isEmpty()) {
                            "— / —"
                        } else {
                            "${viewedReaderPageIndex(mode, pageIndex, pages.size) + 1} / ${pages.size}"
                        },
                        color = MihonPalette.muted,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    TextButton(onClick = onToggleFullscreen) { Text("Fullscreen") }
                }
            }
        }
        val readerNotice = trackerError ?: pendingTrackerSync.firstOrNull { it.mangaId == mangaId }?.error
        if (readerNotice != null || pageActionMessage != null) {
            MihonPanel(
                Modifier.align(Alignment.TopCenter)
                    .widthIn(max = 920.dp)
                    .fillMaxWidth()
                    .padding(top = 72.dp, start = 16.dp, end = 16.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(MihonSpacing.xs),
                ) {
                    readerNotice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    pageActionMessage?.let { Text(it, color = MihonPalette.muted) }
                }
            }
        }
        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.BottomCenter)
                .widthIn(max = 1040.dp)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
        ) {
            Column(
                modifier = Modifier
                    .onPointerEvent(PointerEventType.Enter) {
                        controlsHovered = true
                        controlsVisible = true
                    }
                    .onPointerEvent(PointerEventType.Exit) {
                        controlsHovered = false
                        interactionVersion++
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Slider(
                    value = (sliderTargetPage ?: pageIndex).toFloat(),
                    onValueChange = { value ->
                        if (pages.isNotEmpty()) {
                            sliderDragging = true
                            val destination = normalizeReaderPageIndex(
                                mode,
                                value.roundToInt().coerceIn(pages.indices),
                                pages.size,
                            )
                            sliderTargetPage = destination
                            navigateTo(destination)
                        }
                    },
                    onValueChangeFinished = {
                        if (mode != ReadingMode.VERTICAL && mode != ReadingMode.WEBTOON) {
                            sliderTargetPage = null
                        }
                        sliderDragging = false
                    },
                    enabled = pages.isNotEmpty(),
                    valueRange = 0f..pages.lastIndex.coerceAtLeast(1).toFloat(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = MihonPalette.sage,
                        activeTrackColor = MihonPalette.sage,
                        inactiveTrackColor = MihonPalette.outline,
                    ),
                )
                MihonPanel(Modifier.fillMaxWidth()) {
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    ) {
                        TextButton(onClick = {
                            if (chapterIndex > 0) {
                                chapterOpenIntent = ReaderChapterOpenIntent.FIRST
                                chapterIndex--
                            }
                        }, enabled = chapterIndex > 0) { Text("‹ Chapter") }
                        TextButton(onClick = ::previous, enabled = pages.isNotEmpty()) { Text("‹ Page") }
                        TextButton(onClick = ::next, enabled = pages.isNotEmpty()) { Text("Page ›") }
                        TextButton(onClick = {
                            if (pages.isNotEmpty()) {
                                val currentPage = viewedReaderPageIndex(mode, pageIndex, pages.size)
                                mode = ReadingMode.entries[(mode.ordinal + 1) % ReadingMode.entries.size]
                                pageIndex = normalizeReaderPageIndex(mode, currentPage, pages.size)
                            }
                            graph.keyValueStore.putString("desktop.reader.mode", mode.name)
                        }) { Text(mode.name.replace('_', ' ')) }
                        TextButton(onClick = {
                            fit = FitMode.entries[(fit.ordinal + 1) % FitMode.entries.size]
                            graph.keyValueStore.putString("desktop.reader.fit", fit.name)
                        }) { Text("Fit ${fit.name.lowercase()}") }
                        Box {
                            TextButton(onClick = { showMore = true }) { Text("More…") }
                            DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                                ReaderMoreActions(
                                    onClose = onClose,
                                    onZoomOut = { zoom = (zoom - 0.25f).coerceAtLeast(0.5f) },
                                    onZoomIn = { zoom = (zoom + 0.25f).coerceAtMost(4f) },
                                    onAppearance = { showAppearance = true },
                                    onFullscreen = onToggleFullscreen,
                                    onShortcuts = { showShortcuts = true },
                                    onSave = ::saveCurrentPage,
                                    canSave = pages.isNotEmpty(),
                                    onOpenSaved = savedPage?.let { path ->
                                        {
                                            graph.externalOpenService.openPath(path.toString())
                                        }
                                    },
                                    onBookmark = ::toggleChapterBookmark,
                                    canBookmark = chapterId != null,
                                    bookmarkLabel = bookmarkLabel,
                                    onNextChapter = ::nextChapter,
                                    canNextChapter = chapterIndex < chapters.lastIndex,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderMoreActions(
    onClose: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onAppearance: () -> Unit,
    onFullscreen: () -> Unit,
    onShortcuts: () -> Unit,
    onSave: () -> Unit,
    canSave: Boolean,
    onOpenSaved: (() -> Unit)?,
    onBookmark: () -> Unit,
    canBookmark: Boolean,
    bookmarkLabel: String,
    onNextChapter: () -> Unit,
    canNextChapter: Boolean,
) {
    Column {
        TextButton(onClick = onClose) { Text("Back to manga") }
        TextButton(onClick = onZoomOut) { Text("Zoom −") }
        TextButton(onClick = onZoomIn) { Text("Zoom +") }
        TextButton(onClick = onAppearance) { Text("Appearance") }
        TextButton(onClick = onFullscreen) { Text("Fullscreen") }
        TextButton(onClick = onShortcuts) { Text("Keys") }
        TextButton(onClick = onSave, enabled = canSave) { Text("Save page…") }
        onOpenSaved?.let { TextButton(onClick = it) { Text("Open saved page") } }
        TextButton(onClick = onBookmark, enabled = canBookmark) { Text(bookmarkLabel) }
        TextButton(onClick = onNextChapter, enabled = canNextChapter) { Text("Next chapter") }
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
    continuousViewportHeight: Dp? = null,
) {
    var retry by remember(page) { mutableIntStateOf(0) }
    val density = LocalDensity.current
    var result by remember(page) { mutableStateOf<Result<ImageBitmap>?>(null) }
    LaunchedEffect(page, retry) {
        result = null
        val loaded = runCatching { loader.image(page) }
        loaded.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        result = loaded
    }
    var pan by remember(page) { mutableStateOf(Offset.Zero) }
    LaunchedEffect(zoom) {
        if (zoom <= 1f) pan = Offset.Zero
    }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        result?.fold(
            onSuccess = { image ->
                val ratio = image.width.toFloat() / image.height
                val naturalWidth = with(density) { image.width.toDp() }
                val availableHeight = continuousViewportHeight ?: maxHeight
                val completeWidth = minOf(maxWidth, availableHeight * ratio)
                val width = when (fit) {
                    FitMode.WIDTH -> if (continuousViewportHeight != null) maxWidth else completeWidth
                    FitMode.HEIGHT -> completeWidth
                    FitMode.ORIGINAL -> minOf(naturalWidth, completeWidth)
                }
                Image(
                    bitmap = image,
                    contentDescription = "Page ${index + 1}",
                    contentScale = ContentScale.Fit,
                    colorFilter = colorFilter,
                    modifier = Modifier.width(width).height(width / ratio).graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = pan.x
                        translationY = pan.y
                    }.then(
                        if (continuousViewportHeight == null && zoom > 1f) {
                            Modifier.pointerInput(page, zoom, width) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    val maxX = (zoom - 1f) * width.toPx() / 2f
                                    val maxY = (zoom - 1f) * (width / ratio).toPx() / 2f
                                    pan = Offset(
                                        (pan.x + drag.x).coerceIn(-maxX, maxX),
                                        (pan.y + drag.y).coerceIn(-maxY, maxY),
                                    )
                                }
                            }
                        } else {
                            Modifier
                        },
                    ),
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
