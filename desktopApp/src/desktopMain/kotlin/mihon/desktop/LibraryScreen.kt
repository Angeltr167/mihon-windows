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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
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
    LAST_CHAPTER_FINISHED("Last chapter finished"),
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
    val uiLanguage = LocalRoninLanguage.current
    var shelfFilter by remember { mutableStateOf(LibraryShelfFilter.ALL) }
    var sortMode by remember { mutableStateOf(LibrarySortMode.RECENTLY_UPDATED) }
    var viewMode by remember { mutableStateOf(LibraryViewMode.GRID) }

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
    val selectedCategoryName = remember(categories, selectedCategory, uiLanguage) {
        categories.firstOrNull {
            it.id == selectedCategory
        }?.name?.ifBlank { roninCopy("Uncategorized", "Sin categoría", uiLanguage) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val continueWidth = if (maxWidth >= 1100.dp) {
            (maxWidth - RoninSpacing.medium * 2) / 3
        } else {
            RoninMangaMetrics.continueCardWidth
        }
        Column(Modifier.fillMaxSize()) {
            RoninSectionHeader(
                title = roninText("Library", "Biblioteca"),
                pageHeading = true,
                subtitle = when {
                    selectedCategoryName != null -> roninCopy(
                        "${visibleLibrary.size} manga in $selectedCategoryName",
                        "${visibleLibrary.size} manga en $selectedCategoryName",
                        uiLanguage,
                    )
                    search.isNotBlank() -> roninCopy(
                        "${visibleLibrary.size} matching manga",
                        "${visibleLibrary.size} manga encontrados",
                        uiLanguage,
                    )
                    else -> roninText(
                        "${library.size} titles · Your collection",
                        "${library.size} títulos · Tu colección",
                    )
                },
                trailing = {
                    RoninSearchField(
                        value = search,
                        onValueChange = onSearchChange,
                        placeholder = roninText("Search your library…", "Buscar en tu biblioteca…"),
                        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
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
                    title = roninCopy("Library refresh failed", "Falló la actualización de biblioteca", uiLanguage),
                    detail = error,
                    modifier = Modifier.padding(bottom = RoninSpacing.large),
                )
            }

            when {
                loading && library.isEmpty() -> RoninLoadingState(
                    title = roninCopy("Loading library…", "Cargando biblioteca…", uiLanguage),
                    detail = roninCopy(
                        "Reading your saved manga and progress.",
                        "Cargando tus manga y el progreso guardado.",
                        uiLanguage,
                    ),
                    modifier = Modifier.padding(top = RoninSpacing.large),
                )

                error != null && library.isEmpty() -> RoninErrorState(
                    title = roninCopy("Could not load library", "No se pudo cargar la biblioteca", uiLanguage),
                    detail = error,
                    modifier = Modifier.padding(top = RoninSpacing.large),
                )

                library.isEmpty() -> RoninEmptyState(
                    title = roninCopy("Your library is empty", "Tu biblioteca está vacía", uiLanguage),
                    detail = roninCopy(
                        "Browse an installed source and add a manga to start reading.",
                        "Explora una fuente y añade un manga para empezar a leer.",
                        uiLanguage,
                    ),
                    modifier = Modifier.padding(top = RoninSpacing.large),
                )

                visibleLibrary.isEmpty() -> RoninEmptyState(
                    title = roninCopy("No manga found", "No se encontraron manga", uiLanguage),
                    detail = roninCopy(
                        "Change the search, shelf, category, or sort controls.",
                        "Cambia la búsqueda, el filtro, la categoría o el orden.",
                        uiLanguage,
                    ),
                    modifier = Modifier.padding(top = RoninSpacing.large),
                )

                else -> {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        Box(Modifier.fillMaxSize()) {
                            val shelves: @Composable () -> Unit = {
                                if (visibleContinueReading.isNotEmpty()) {
                                    LibrarySectionLabel(
                                        title = roninText("Continue reading", "Continuar leyendo"),
                                        detail = "",
                                    )
                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = RoninSpacing.large),
                                        horizontalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
                                    ) {
                                        items(visibleContinueReading, key = { it.manga._id }) { item ->
                                            ContinueReadingCard(
                                                item = item,
                                                width = continueWidth,
                                                source = sourceById[item.manga.source],
                                                onOpenManga = { id, url ->
                                                    onOpenManga(id, url)
                                                },
                                            )
                                        }
                                    }
                                }

                                LibrarySectionLabel(
                                    title = selectedCategoryName ?: roninText("Your collection", "Tu colección"),
                                    detail = "${visibleLibrary.size}",
                                )
                            }
                            when (viewMode) {
                                LibraryViewMode.GRID -> LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = RoninMangaMetrics.gridCellMinWidth),
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.spacedBy(RoninLayout.gridGap),
                                    verticalArrangement = Arrangement.spacedBy(RoninSpacing.large),
                                ) {
                                    item(key = "library-shelves", span = { GridItemSpan(maxLineSpan) }) {
                                        Column { shelves() }
                                    }
                                    gridItems(visibleLibrary, key = Mangas::_id) { manga ->
                                        LibraryGridCard(
                                            manga = manga,
                                            source = sourceById[manga.source],
                                            chapter = recentChapters[manga._id],
                                            onOpenManga = { id, url ->
                                                onOpenManga(id, url)
                                            },
                                        )
                                    }
                                }

                                LibraryViewMode.LIST -> LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                                ) {
                                    item(key = "library-shelves") { Column { shelves() } }
                                    items(visibleLibrary, key = Mangas::_id) { manga ->
                                        LibraryListCard(
                                            manga = manga,
                                            source = sourceById[manga.source],
                                            chapter = recentChapters[manga._id],
                                            onOpenManga = { id, url ->
                                                onOpenManga(id, url)
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
    val uiLanguage = LocalRoninLanguage.current
    var sortExpanded by remember { mutableStateOf(false) }
    var filtersExpanded by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = RoninSpacing.medium)) {
        val narrow = maxWidth < 720.dp
        val categoryTabs: @Composable (Modifier) -> Unit = { tabsModifier ->
            LazyRow(
                tabsModifier,
                horizontalArrangement = Arrangement.spacedBy(RoninSpacing.small),
            ) {
                item {
                    RoninFilterPill(roninText("All", "Todos"), selectedCategory == null, { onSelectCategory(null) })
                }
                items(categories, key = { it.id }) { category ->
                    RoninFilterPill(
                        category.name.ifBlank { roninCopy("Uncategorized", "Sin categoría", uiLanguage) },
                        selectedCategory == category.id,
                        { onSelectCategory(category.id) },
                    )
                }
            }
        }
        val actions: @Composable () -> Unit = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
            ) {
                Box {
                    RoninSecondaryButton(
                        roninText("Filter", "Filtrar") + if (shelfFilter != LibraryShelfFilter.ALL) " · 1" else "",
                        { filtersExpanded = true },
                    )
                    DropdownMenu(filtersExpanded, { filtersExpanded = false }) {
                        LibraryShelfFilter.entries.forEach { filter ->
                            DropdownMenuItem(
                                text = { Text(roninUiText(filter.label)) },
                                onClick = {
                                    onShelfFilterChange(filter)
                                    filtersExpanded = false
                                },
                            )
                        }
                    }
                }
                Box {
                    RoninSecondaryButton(roninText("Sort", "Ordenar"), { sortExpanded = true })
                    DropdownMenu(sortExpanded, { sortExpanded = false }) {
                        LibrarySortMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(roninUiText(mode.label)) },
                                onClick = {
                                    onSortModeChange(mode)
                                    sortExpanded = false
                                },
                            )
                        }
                    }
                }
                RoninFilterPill("▦", viewMode == LibraryViewMode.GRID, { onViewModeChange(LibraryViewMode.GRID) })
                RoninFilterPill("☰", viewMode == LibraryViewMode.LIST, { onViewModeChange(LibraryViewMode.LIST) })
            }
        }
        if (narrow) {
            Column(verticalArrangement = Arrangement.spacedBy(RoninSpacing.small)) {
                categoryTabs(Modifier.fillMaxWidth())
                actions()
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
            ) {
                categoryTabs(Modifier.weight(1f))
                actions()
            }
        }
    }
}

@Composable
private fun LibrarySectionLabel(
    title: String,
    detail: String,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = RoninSpacing.small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
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
    width: androidx.compose.ui.unit.Dp,
    source: Source?,
    onOpenManga: (Long, String?) -> Unit,
) {
    val manga = item.manga
    val chapter = item.chapter
    LibraryInteractiveSurface(
        modifier = Modifier.width(width),
        onClick = { onOpenManga(manga._id, chapter.url) },
    ) {
        Row(Modifier.fillMaxWidth().height(148.dp)) {
            DesktopCover(
                manga.thumbnail_url,
                source,
                Modifier.width(if (width >= 390.dp) 168.dp else 116.dp).fillMaxHeight(),
                contentScale = ContentScale.Crop,
                mangaUrl = manga.url,
                sourceId = manga.source,
            )
            Column(
                Modifier.weight(1f).padding(RoninSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
            ) {
                Text(
                    manga.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
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
                    overflow = TextOverflow.Ellipsis,
                )
                RoninButton(
                    roninText("▶ Continue", "▶ Continuar"),
                    { onOpenManga(manga._id, chapter.url) },
                    Modifier.height(32.dp),
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
        framed = false,
    ) {
        Column(Modifier.fillMaxWidth()) {
            RoninCover(Modifier.fillMaxWidth().aspectRatio(1.35f)) {
                DesktopCover(
                    manga.thumbnail_url,
                    source,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    mangaUrl = manga.url,
                    sourceId = manga.source,
                )
            }
            Text(
                manga.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = RoninSpacing.small),
            )
            Text(
                chapter?.name ?: source?.let { localizedSourceName(it, LocalRoninLanguage.current) }
                    ?: roninText("Not started", "Sin comenzar"),
                color = RoninColors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (chapter != null) {
                if (chapter.read) RoninProgressBar(1f, Modifier.fillMaxWidth(0.78f).padding(top = RoninSpacing.small))
                RoninInlineAction(
                    roninText("Continue", "Continuar"),
                    { onOpenManga(manga._id, chapter.url) },
                )
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
    val uiLanguage = LocalRoninLanguage.current
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
                    mangaUrl = manga.url,
                    sourceId = manga.source,
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
                    chapter?.name ?: roninCopy("Not started", "Sin empezar", uiLanguage),
                    color = RoninColors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                source?.let {
                    Text(
                        localizedSourceName(it, LocalRoninLanguage.current),
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
                    label = roninUiText(chapterBadge(chapter)),
                    accent = chapter?.read == false,
                )
                Text(
                    chapterProgressLabel(chapter),
                    color = if (chapter?.read == false) RoninColors.accentSage else RoninColors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
                if (chapter != null && !chapter.read) {
                    RoninTextButton(
                        label = roninCopy("Continue", "Continuar", uiLanguage),
                        onClick = { onOpenManga(manga._id, chapter.url) },
                    )
                } else {
                    RoninTextButton(
                        label = roninCopy("Details", "Detalles", uiLanguage),
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
    framed: Boolean = true,
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
        color = if (hovered) {
            RoninColors.hoverSurface
        } else if (framed) {
            RoninColors.elevatedSurface
        } else {
            Color.Transparent
        },
        border = if (framed || hovered) {
            BorderStroke(
                RoninBorders.hairline,
                if (hovered) RoninColors.accentCoral.copy(alpha = 0.42f) else RoninColors.borderSubtle,
            )
        } else {
            null
        },
        content = content,
    )
}

private fun chapterBadge(chapter: Chapters?): String = when {
    chapter == null -> "NEW"
    chapter.read -> "DONE"
    chapter.last_page_read > 0L -> "P${chapter.last_page_read + 1}"
    else -> "READ"
}

@Composable
private fun chapterProgressLabel(chapter: Chapters?): String = when {
    chapter == null -> roninText("Not started", "Sin empezar")
    chapter.read -> roninText("Last chapter finished", "Último capítulo terminado")
    chapter.last_page_read > 0L -> roninText(
        "Continue from page ${chapter.last_page_read + 1}",
        "Continuar desde la página ${chapter.last_page_read + 1}",
    )
    else -> roninText("Continue chapter", "Continuar capítulo")
}
