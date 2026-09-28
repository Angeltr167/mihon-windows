package mihon.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.source.Source
import mihon.desktop.design.RoninBorders
import mihon.desktop.design.RoninColors
import mihon.desktop.design.RoninLayout
import mihon.desktop.design.RoninMangaMetrics
import mihon.desktop.design.RoninRadius
import mihon.desktop.design.RoninSpacing
import tachiyomi.data.Chapters
import tachiyomi.data.GetCategories
import tachiyomi.data.Mangas
import tachiyomi.view.History
import androidx.compose.foundation.lazy.grid.items as gridItems

private enum class LibraryShelfFilter(val label: String) {
    ALL("All"),
    READING("In progress"),
    NOT_STARTED("Not started"),
    LAST_CHAPTER_FINISHED("Finished"),
}

private enum class LibrarySortMode(val label: String) {
    RECENTLY_ADDED("Recently added"),
    RECENTLY_UPDATED("Recently updated"),
    TITLE("A–Z"),
}

private enum class LibraryViewMode(val label: String) {
    GRID("Grid"),
    LIST("List"),
}

private data class LibraryContinueItem(
    val manga: Mangas,
    val chapter: Chapters,
)

@Composable
internal fun LibraryScreen(
    library: List<Mangas>,
    membership: Map<Long, Set<Long>>,
    historyEntries: List<History>,
    historyChapters: Map<Long, Chapters?>,
    sources: List<Source>,
    categories: List<GetCategories>,
    selectedCategory: Long?,
    onSelectCategory: (Long?) -> Unit,
    search: String,
    onSearchChange: (String) -> Unit,
    loading: Boolean,
    error: String?,
    onOpenManga: (Long, String?) -> Unit,
) {
    var shelfFilter by remember { mutableStateOf(LibraryShelfFilter.ALL) }
    var sortMode by remember { mutableStateOf(LibrarySortMode.RECENTLY_ADDED) }
    var viewMode by remember { mutableStateOf(LibraryViewMode.GRID) }
    var inspectedMangaId by remember { mutableStateOf<Long?>(null) }

    val sourceById = remember(sources) { sources.associateBy(Source::id) }
    val libraryById = remember(library) { library.associateBy(Mangas::_id) }
    val recentChapters = remember(historyEntries, historyChapters) {
        historyEntries.mapNotNull { entry ->
            historyChapters[entry.chapterId]?.let { chapter -> entry.mangaId to chapter }
        }.toMap()
    }
    val continueReading = remember(historyEntries, historyChapters, libraryById) {
        historyEntries.mapNotNull { entry ->
            val manga = libraryById[entry.mangaId] ?: return@mapNotNull null
            val chapter = historyChapters[entry.chapterId] ?: return@mapNotNull null
            if (chapter.read) null else LibraryContinueItem(manga, chapter)
        }
    }

    val visibleLibrary = remember(
        library,
        membership,
        selectedCategory,
        search,
        shelfFilter,
        recentChapters,
        sortMode,
    ) {
        val filtered = library.asSequence()
            .filter { it.title.contains(search, ignoreCase = true) }
            .filter { manga ->
                selectedCategory == null || run {
                    val assigned = membership[manga._id].orEmpty()
                    if (selectedCategory == 0L) assigned.isEmpty() else selectedCategory in assigned
                }
            }
            .filter { manga ->
                val chapter = recentChapters[manga._id]
                when (shelfFilter) {
                    LibraryShelfFilter.ALL -> true
                    LibraryShelfFilter.READING -> chapter != null && !chapter.read
                    LibraryShelfFilter.NOT_STARTED -> chapter == null
                    LibraryShelfFilter.LAST_CHAPTER_FINISHED -> chapter?.read == true
                }
            }
            .toList()

        when (sortMode) {
            LibrarySortMode.RECENTLY_ADDED -> filtered.sortedByDescending(Mangas::date_added)
            LibrarySortMode.RECENTLY_UPDATED -> filtered.sortedByDescending { it.last_update ?: 0L }
            LibrarySortMode.TITLE -> filtered.sortedBy { it.title.lowercase() }
        }
    }
    val visibleIds = remember(visibleLibrary) { visibleLibrary.mapTo(mutableSetOf(), Mangas::_id) }
    val visibleContinueReading = remember(continueReading, visibleIds, shelfFilter) {
        if (shelfFilter == LibraryShelfFilter.NOT_STARTED || shelfFilter == LibraryShelfFilter.LAST_CHAPTER_FINISHED) {
            emptyList()
        } else {
            continueReading.filter { it.manga._id in visibleIds }.take(8)
        }
    }
    val inspectedManga = remember(visibleLibrary, inspectedMangaId) {
        visibleLibrary.firstOrNull { it._id == inspectedMangaId } ?: visibleLibrary.firstOrNull()
    }
    val selectedCategoryName = remember(categories, selectedCategory) {
        categories.firstOrNull { it.id == selectedCategory }?.name?.ifBlank { "Uncategorized" }
    }

    Column(Modifier.fillMaxSize()) {
        RoninSectionHeader(
            title = "Library",
            subtitle = when {
                selectedCategoryName != null -> "${visibleLibrary.size} manga in $selectedCategoryName"
                search.isNotBlank() -> "${visibleLibrary.size} matching manga"
                else -> "${library.size} manga on this device"
            },
            trailing = {
                RoninSearchField(
                    value = search,
                    onValueChange = onSearchChange,
                    placeholder = "Search your library…",
                    modifier = Modifier.widthIn(min = 280.dp, max = 420.dp),
                )
            },
        )

        LibraryControlPanel(
            categories = categories,
            selectedCategory = selectedCategory,
            onSelectCategory = onSelectCategory,
            shelfFilter = shelfFilter,
            onShelfFilterChange = { shelfFilter = it },
            sortMode = sortMode,
            onSortModeChange = { sortMode = it },
            viewMode = viewMode,
            onViewModeChange = { viewMode = it },
        )

        if (error != null && !loading && library.isNotEmpty()) {
            RoninErrorState(
                title = "Library refresh failed",
                detail = error,
                modifier = Modifier.padding(bottom = RoninSpacing.large),
            )
        }

        when {
            loading -> RoninLoadingState(
                title = "Loading library…",
                detail = "Reading your saved manga and progress.",
                modifier = Modifier.padding(top = RoninSpacing.large),
            )

            error != null && library.isEmpty() -> RoninErrorState(
                title = "Could not load library",
                detail = error,
                modifier = Modifier.padding(top = RoninSpacing.large),
            )

            library.isEmpty() -> RoninEmptyState(
                title = "Your library is empty",
                detail = "Browse an installed source and add a manga to start reading.",
                modifier = Modifier.padding(top = RoninSpacing.large),
            )

            visibleLibrary.isEmpty() -> RoninEmptyState(
                title = "No manga found",
                detail = "Change the search, shelf, category, or sort controls.",
                modifier = Modifier.padding(top = RoninSpacing.large),
            )

            else -> {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val showInspector = maxWidth >= RoninLayout.rightPanelBreakpoint
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(RoninSpacing.large),
                    ) {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                if (visibleContinueReading.isNotEmpty()) {
                    LibrarySectionLabel(
                        title = "Continue reading",
                        detail = "${visibleContinueReading.size} recent",
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(bottom = RoninSpacing.large),
                        horizontalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
                    ) {
                        items(visibleContinueReading, key = { it.manga._id }) { item ->
                            ContinueReadingCard(
                                item = item,
                                source = sourceById[item.manga.source],
                                onOpenManga = { id, url -> inspectedMangaId = id; onOpenManga(id, url) },
                            )
                        }
                    }
                }

                LibrarySectionLabel(
                    title = if (selectedCategoryName == null) "Collection" else selectedCategoryName,
                    detail = "${visibleLibrary.size} titles · ${sortMode.label}",
                )

                when (viewMode) {
                    LibraryViewMode.GRID -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 174.dp),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(RoninLayout.gridGap),
                        verticalArrangement = Arrangement.spacedBy(RoninSpacing.large),
                    ) {
                        gridItems(visibleLibrary, key = Mangas::_id) { manga ->
                            LibraryGridCard(
                                manga = manga,
                                source = sourceById[manga.source],
                                chapter = recentChapters[manga._id],
                                onOpenManga = { id, url -> inspectedMangaId = id; onOpenManga(id, url) },
                            )
                        }
                    }

                    LibraryViewMode.LIST -> LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                    ) {
                        items(visibleLibrary, key = Mangas::_id) { manga ->
                            LibraryListCard(
                                manga = manga,
                                source = sourceById[manga.source],
                                chapter = recentChapters[manga._id],
                                onOpenManga = { id, url -> inspectedMangaId = id; onOpenManga(id, url) },
                            )
                        }
                    }
                }
                    }
                    if (showInspector && inspectedManga != null) {
                        LibraryInspector(
                            manga = inspectedManga,
                            source = sourceById[inspectedManga.source],
                            chapter = recentChapters[inspectedManga._id],
                            onOpenManga = onOpenManga,
                            modifier = Modifier.width(RoninLayout.rightPanelWidth),
                        )
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryInspector(
    manga: Mangas,
    source: Source?,
    chapter: Chapters?,
    onOpenManga: (Long, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    RoninPanel(modifier) {
        Column(
            Modifier.fillMaxWidth().padding(RoninSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
        ) {
            Text("Inspector", style = MaterialTheme.typography.titleLarge)
            RoninCover(Modifier.fillMaxWidth()) {
                DesktopCover(
                    manga.thumbnail_url,
                    source,
                    Modifier.fillMaxWidth().aspectRatio(RoninMangaMetrics.COVER_ASPECT_RATIO),
                    contentScale = ContentScale.Crop,
                )
            }
            Text(manga.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
            source?.let { RoninBadge(it.roninSourceDisplayName()) }
            Text(chapterProgressLabel(chapter), color = RoninColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
            RoninButton(
                label = if (chapter?.read == false) "Continue reading" else "Open manga",
                onClick = { onOpenManga(manga._id, manga.url) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LibraryControlPanel(
    categories: List<GetCategories>,
    selectedCategory: Long?,
    onSelectCategory: (Long?) -> Unit,
    shelfFilter: LibraryShelfFilter,
    onShelfFilterChange: (LibraryShelfFilter) -> Unit,
    sortMode: LibrarySortMode,
    onSortModeChange: (LibrarySortMode) -> Unit,
    viewMode: LibraryViewMode,
    onViewModeChange: (LibraryViewMode) -> Unit,
) {
    RoninPanel(Modifier.fillMaxWidth().padding(bottom = RoninSpacing.large)) {
        Column(
            Modifier.fillMaxWidth().padding(RoninSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(RoninSpacing.small)) {
                Text(
                    "Shelves",
                    color = RoninColors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                    verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                ) {
                    RoninFilterPill(
                        label = "All",
                        selected = selectedCategory == null,
                        onClick = { onSelectCategory(null) },
                    )
                    categories.forEach { category ->
                        RoninFilterPill(
                            label = category.name.ifBlank { "Uncategorized" },
                            selected = selectedCategory == category.id,
                            onClick = { onSelectCategory(category.id) },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(RoninSpacing.small)) {
                Text(
                    "Reading state",
                    color = RoninColors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                    verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                ) {
                    LibraryShelfFilter.entries.forEach { filter ->
                        RoninFilterPill(
                            label = filter.label,
                            selected = shelfFilter == filter,
                            onClick = { onShelfFilterChange(filter) },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                ) {
                    Text(
                        "Sort",
                        color = RoninColors.textMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                        verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                    ) {
                        LibrarySortMode.entries.forEach { mode ->
                            RoninFilterPill(
                                label = mode.label,
                                selected = sortMode == mode,
                                onClick = { onSortModeChange(mode) },
                            )
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                ) {
                    Text(
                        "View",
                        color = RoninColors.textMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall)) {
                        LibraryViewMode.entries.forEach { mode ->
                            RoninFilterPill(
                                label = mode.label,
                                selected = viewMode == mode,
                                onClick = { onViewModeChange(mode) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrarySectionLabel(
    title: String,
    detail: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = RoninSpacing.small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            detail,
            color = RoninColors.textMuted,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun ContinueReadingCard(
    item: LibraryContinueItem,
    source: Source?,
    onOpenManga: (Long, String?) -> Unit,
) {
    val manga = item.manga
    val chapter = item.chapter
    LibraryInteractiveSurface(
        modifier = Modifier.width(342.dp),
        onClick = { onOpenManga(manga._id, null) },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(RoninSpacing.medium),
            horizontalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoninCover(
                Modifier.width(82.dp).aspectRatio(RoninMangaMetrics.COVER_ASPECT_RATIO),
            ) {
                DesktopCover(
                    manga.thumbnail_url,
                    source,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
            ) {
                Text(
                    manga.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    chapter.name,
                    color = RoninColors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    chapterProgressLabel(chapter),
                    color = RoninColors.accentSage,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
                RoninTextButton(
                    label = "Continue",
                    onClick = { onOpenManga(manga._id, chapter.url) },
                )
            }
        }
    }
}

@Composable
private fun LibraryGridCard(
    manga: Mangas,
    source: Source?,
    chapter: Chapters?,
    onOpenManga: (Long, String?) -> Unit,
) {
    LibraryInteractiveSurface(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onOpenManga(manga._id, null) },
    ) {
        Column(Modifier.fillMaxWidth().padding(RoninSpacing.small)) {
            Box {
                RoninCover(
                    Modifier.fillMaxWidth().aspectRatio(RoninMangaMetrics.COVER_ASPECT_RATIO),
                ) {
                    DesktopCover(
                        manga.thumbnail_url,
                        source,
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                RoninBadge(
                    label = chapterBadge(chapter),
                    accent = chapter?.read == false,
                    modifier = Modifier.align(Alignment.TopEnd).padding(RoninSpacing.small),
                )
            }

            Text(
                manga.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = RoninSpacing.small),
            )
            Text(
                chapter?.name ?: source?.name ?: "Not started",
                color = RoninColors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                chapterProgressLabel(chapter),
                color = if (chapter?.read == false) RoninColors.accentSage else RoninColors.textMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = RoninSpacing.xSmall),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = RoninSpacing.xSmall),
                horizontalArrangement = Arrangement.End,
            ) {
                if (chapter != null && !chapter.read) {
                    RoninTextButton(
                        label = "Continue",
                        onClick = { onOpenManga(manga._id, chapter.url) },
                    )
                } else {
                    RoninTextButton(
                        label = "Details",
                        onClick = { onOpenManga(manga._id, null) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryListCard(
    manga: Mangas,
    source: Source?,
    chapter: Chapters?,
    onOpenManga: (Long, String?) -> Unit,
) {
    LibraryInteractiveSurface(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onOpenManga(manga._id, null) },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(RoninSpacing.medium),
            horizontalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoninCover(
                Modifier.width(RoninMangaMetrics.coverCompactWidth)
                    .aspectRatio(RoninMangaMetrics.COVER_ASPECT_RATIO),
            ) {
                DesktopCover(
                    manga.thumbnail_url,
                    source,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }

            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
            ) {
                Text(
                    manga.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    chapter?.name ?: "Not started",
                    color = RoninColors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                source?.let {
                    Text(
                        it.name,
                        color = RoninColors.textMuted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
            ) {
                RoninBadge(
                    label = chapterBadge(chapter),
                    accent = chapter?.read == false,
                )
                Text(
                    chapterProgressLabel(chapter),
                    color = if (chapter?.read == false) RoninColors.accentSage else RoninColors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
                if (chapter != null && !chapter.read) {
                    RoninTextButton(
                        label = "Continue",
                        onClick = { onOpenManga(manga._id, chapter.url) },
                    )
                } else {
                    RoninTextButton(
                        label = "Details",
                        onClick = { onOpenManga(manga._id, null) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryInteractiveSurface(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    Surface(
        modifier = modifier
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(RoninRadius.card),
        color = if (hovered) RoninColors.hoverSurface else RoninColors.elevatedSurface,
        border = BorderStroke(
            RoninBorders.hairline,
            if (hovered) RoninColors.accentCoral.copy(alpha = 0.42f) else RoninColors.border,
        ),
        content = content,
    )
}

private fun chapterBadge(chapter: Chapters?): String = when {
    chapter == null -> "NEW"
    chapter.read -> "DONE"
    chapter.last_page_read > 0L -> "P${chapter.last_page_read + 1}"
    else -> "READ"
}

private fun chapterProgressLabel(chapter: Chapters?): String = when {
    chapter == null -> "Not started"
    chapter.read -> "Last chapter finished"
    chapter.last_page_read > 0L -> "Continue from page ${chapter.last_page_read + 1}"
    else -> "Continue chapter"
}
