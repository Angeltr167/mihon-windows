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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import mihon.core.reader.ReadingMode
import mihon.core.reader.nextPageToPreload
import mihon.platform.desktop.DesktopPlatformGraph

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
    val loader = remember(session) { DesktopPageLoader(session.localPages) }
    val focusRequester = remember(target) { FocusRequester() }
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
    var chapterIndex by remember(target) {
        mutableIntStateOf(chapters.indexOfFirst { it.url == target.selectedChapterUrl }.coerceAtLeast(0))
    }
    var pageIndex by remember(target) { mutableIntStateOf(0) }
    var pages by remember(target) { mutableStateOf<List<DesktopPage>>(emptyList()) }
    var chapterId by remember(target) { mutableStateOf<Long?>(null) }
    var error by remember(target) { mutableStateOf<String?>(null) }
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
    var requestedScroll by remember(target) { mutableStateOf<Int?>(null) }

    LaunchedEffect(target, chapterIndex, retry) {
        pages = emptyList()
        chapterId = null
        error = null
        runCatching {
            val loaded = loader.pages(target.source, chapters[chapterIndex])
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
            openAtLastPage = false
        }.onFailure {
            if (it is CancellationException) throw it
            error = it.message ?: "Chapter could not be loaded"
        }
    }

    LaunchedEffect(chapterId, pageIndex, pages.size, mode) {
        val id = chapterId ?: return@LaunchedEffect
        if (pages.isEmpty()) return@LaunchedEffect
        val viewedIndex = if (mode == ReadingMode.DOUBLE_LTR || mode == ReadingMode.DOUBLE_RTL) {
            (pageIndex + 1).coerceAtMost(pages.lastIndex)
        } else {
            pageIndex
        }
        withContext(Dispatchers.IO) { session.library.saveProgress(id, viewedIndex, pages.size) }
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
            TextButton(onClick = onToggleFullscreen) { Text("Fullscreen") }
        }
        when {
            error != null -> Column {
                Text(requireNotNull(error), color = MaterialTheme.colorScheme.error)
                Button(onClick = { retry++ }) { Text("Retry") }
            }
            pages.isEmpty() -> Text("Loading pages…")
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
                            ReaderImage(loader, page, fit, zoom, Modifier.fillMaxWidth(), index)
                        }
                    }
                }
            }
            else -> {
                val pair = if (mode == ReadingMode.DOUBLE_LTR || mode == ReadingMode.DOUBLE_RTL) {
                    pages.drop(pageIndex).take(2)
                } else {
                    listOf(pages[pageIndex])
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
                        (if (rightToLeft) pair.reversed() else pair).forEachIndexed { index, page ->
                            ReaderImage(
                                loader,
                                page,
                                fit,
                                zoom,
                                Modifier.weight(1f).fillMaxHeight(),
                                pageIndex + index,
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
