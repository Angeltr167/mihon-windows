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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.desktop.design.RoninBorders
import mihon.desktop.design.RoninColors
import mihon.desktop.design.RoninLayout
import mihon.desktop.design.RoninMangaMetrics
import mihon.desktop.design.RoninRadius
import mihon.desktop.design.RoninSpacing
import tachiyomi.source.local.desktop.DesktopLocalSource

@Composable
internal fun Source.roninSourceDisplayName(): String =
    if (this is DesktopLocalSource ||
        lang == "localsourcelang"
    ) {
        localizedSourceName(this, LocalRoninLanguage.current)
    } else {
        "$name · ${lang.uppercase()}"
    }

internal fun Source.roninSourceLanguage(): String =
    if (this is DesktopLocalSource || lang == "localsourcelang") "LOCAL" else lang.uppercase()

@Composable
internal fun SearchScreen(
    sources: List<Source>,
    selectedSource: Source?,
    query: String,
    activeQuery: String,
    onQueryChange: (String) -> Unit,
    results: List<SManga>,
    loading: Boolean,
    error: String?,
    hasNext: Boolean,
    link: String,
    showLinkTools: Boolean,
    onLinkChange: (String) -> Unit,
    onToggleLinkTools: () -> Unit,
    onOpenLink: () -> Unit,
    onPasteLink: () -> Unit,
    onSelectSource: (Source) -> Unit,
    onSearch: () -> Unit,
    onOpenManga: (SManga) -> Unit,
    libraryUrls: Set<String>,
    onAddToLibrary: (SManga) -> Unit,
    onLoadMore: () -> Unit,
) {
    var sourceMenuExpanded by remember { mutableStateOf(false) }
    var inspectorOpen by remember { mutableStateOf(true) }
    var inspectedManga by remember { mutableStateOf<SManga?>(null) }
    var previewManga by remember(selectedSource?.id, inspectedManga?.url) { mutableStateOf<SManga?>(null) }

    LaunchedEffect(results) {
        if (inspectedManga == null || results.none { it.url == inspectedManga?.url }) {
            inspectedManga = results.firstOrNull()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val inspectorFits = maxWidth >= RoninLayout.rightPanelBreakpoint
        val showInspector =
            maxWidth >= RoninLayout.rightPanelBreakpoint && inspectorOpen && selectedSource != null &&
                results.isNotEmpty()
        LaunchedEffect(selectedSource?.id, inspectedManga?.url, showInspector) {
            val selected = inspectedManga
            val previewSource = selectedSource
            if (showInspector && selected != null && previewSource != null) {
                val copy = selected.copy()
                try {
                    previewManga = withContext(Dispatchers.IO) {
                        previewSource.getMangaUpdate(copy, emptyList(), true, false).manga
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A failed metadata preview must never hide usable search results.
                    previewManga = selected
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(
                end = if (showInspector) RoninLayout.rightPanelWidth + RoninSpacing.large else 0.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
        ) {
            RoninSectionHeader(
                title = roninText("Search", "Buscar"),
                pageHeading = true,
                compactBreakpoint = 580.dp,
                subtitle = roninText("Find your next read", "Encuentra tu próxima lectura"),
                trailing = {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(RoninSpacing.small)) {
                        if (!inspectorOpen && results.isNotEmpty() && inspectorFits) {
                            RoninTextButton(roninText("Show details", "Ver detalles"), { inspectorOpen = true })
                        }
                        RoninTextButton(
                            label = if (showLinkTools) {
                                roninText("Hide link tools", "Ocultar enlace")
                            } else {
                                roninText("Open manga link…", "Abrir enlace de manga…")
                            },
                            onClick = onToggleLinkTools,
                        )
                    }
                },
            )

            if (showLinkTools) {
                SearchLinkTools(
                    link = link,
                    onLinkChange = onLinkChange,
                    onOpenLink = onOpenLink,
                    onPasteLink = onPasteLink,
                )
            }

            Column(
                Modifier.fillMaxWidth().padding(bottom = RoninSpacing.small),
                verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoninSearchField(
                        value = query,
                        onValueChange = onQueryChange,
                        placeholder = roninText("Search manga…", "Buscar manga…"),
                        modifier = Modifier.weight(1f),
                        leadingIcon = { DesktopNavigationIcon(Screen.SEARCH.ordinal, RoninColors.textMuted) },
                        onSearch = { if (selectedSource != null && !loading) onSearch() },
                    )
                    RoninButton(
                        label = if (loading) roninText("Searching…", "Buscando…") else roninText("Search", "Buscar"),
                        onClick = onSearch,
                        enabled = selectedSource != null && !loading,
                    )
                }
                SearchSourcePicker(
                    sources = sources,
                    selectedSource = selectedSource,
                    expanded = sourceMenuExpanded,
                    onExpandedChange = { sourceMenuExpanded = it },
                    onSelectSource = onSelectSource,
                    modifier = Modifier.widthIn(max = 320.dp),
                )
            }

            when {
                selectedSource == null -> RoninEmptyState(
                    title = roninText("Choose a source", "Elige una fuente"),
                    detail = roninText(
                        "Select an installed source above. Search stays scoped to that source.",
                        "Selecciona una fuente instalada para buscar en su catálogo.",
                    ),
                    modifier = Modifier.padding(top = RoninSpacing.small),
                )

                loading && results.isEmpty() -> RoninLoadingState(
                    title = if (activeQuery.isBlank()) "Loading source" else "Searching source",
                    detail = selectedSource.roninSourceDisplayName(),
                    modifier = Modifier.padding(top = RoninSpacing.small),
                )

                error != null && results.isEmpty() -> Column(
                    verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                ) {
                    RoninErrorState(
                        title = "Search failed",
                        detail = error,
                    )
                    RoninSecondaryButton(
                        label = "Retry",
                        onClick = onSearch,
                    )
                }

                results.isEmpty() -> RoninEmptyState(
                    title = if (activeQuery.isBlank()) "Nothing to show yet" else "No manga found",
                    detail = if (activeQuery.isBlank()) {
                        "Search this source to find a title."
                    } else {
                        "No results for “$activeQuery” in ${selectedSource.name}."
                    },
                    modifier = Modifier.padding(top = RoninSpacing.small),
                )

                else -> SearchResults(
                    source = selectedSource,
                    results = results,
                    activeQuery = activeQuery,
                    loading = loading,
                    error = error,
                    hasNext = hasNext,
                    onOpenManga = onOpenManga,
                    inspectedUrl = inspectedManga?.url,
                    onInspectManga = { inspectedManga = it },
                    inspectOnClick = showInspector,
                    onLoadMore = onLoadMore,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (showInspector && selectedSource != null) {
            SearchContextPanel(
                onClose = { inspectorOpen = false },
                source = selectedSource,
                manga = previewManga ?: inspectedManga,
                inLibrary = inspectedManga?.url in libraryUrls,
                onAddToLibrary = (previewManga ?: inspectedManga)?.let { manga -> { onAddToLibrary(manga) } },
                onOpenManga = inspectedManga?.let { manga -> { onOpenManga(manga) } },
                modifier = Modifier.align(Alignment.TopEnd).width(RoninLayout.rightPanelWidth).fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SearchLinkTools(
    link: String,
    onLinkChange: (String) -> Unit,
    onOpenLink: () -> Unit,
    onPasteLink: () -> Unit,
) {
    RoninPanel(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(RoninSpacing.medium)) {
            val narrow = maxWidth < 720.dp
            if (narrow) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                ) {
                    RoninSearchField(
                        value = link,
                        onValueChange = onLinkChange,
                        placeholder = "Manga or mihon:// link",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                    ) {
                        RoninButton(
                            label = "Open",
                            onClick = onOpenLink,
                            enabled = link.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        )
                        RoninSecondaryButton(
                            label = "Paste",
                            onClick = onPasteLink,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoninSearchField(
                        value = link,
                        onValueChange = onLinkChange,
                        placeholder = "Manga or mihon:// link",
                        modifier = Modifier.weight(1f),
                    )
                    RoninButton(
                        label = "Open",
                        onClick = onOpenLink,
                        enabled = link.isNotBlank(),
                    )
                    RoninSecondaryButton(
                        label = "Paste",
                        onClick = onPasteLink,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchSourcePicker(
    sources: List<Source>,
    selectedSource: Source?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelectSource: (Source) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        RoninSecondaryButton(
            label = selectedSource?.roninSourceDisplayName() ?: roninText("Choose source", "Elegir fuente"),
            onClick = { onExpandedChange(true) },
            modifier = Modifier.fillMaxWidth(),
            enabled = sources.isNotEmpty(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier
                .widthIn(min = 240.dp, max = 360.dp)
                .heightIn(max = 460.dp),
        ) {
            sources
                .sortedWith(compareBy<Source> { it.name.lowercase() }.thenBy { it.lang })
                .forEach { source ->
                    DropdownMenuItem(
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(RoninSpacing.micro)) {
                                Text(
                                    localizedSourceName(source, LocalRoninLanguage.current),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    source.roninSourceLanguage(),
                                    color = RoninColors.textMuted,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        },
                        onClick = {
                            onExpandedChange(false)
                            onSelectSource(source)
                        },
                    )
                }
        }
    }
}

@Composable
private fun SearchResults(
    source: Source,
    results: List<SManga>,
    activeQuery: String,
    loading: Boolean,
    error: String?,
    hasNext: Boolean,
    onOpenManga: (SManga) -> Unit,
    onInspectManga: (SManga) -> Unit,
    inspectedUrl: String?,
    inspectOnClick: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = RoninSpacing.small),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                source.roninSourceDisplayName(),
                color = RoninColors.textMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                roninText("${results.size} results", "${results.size} resultados"),
                color = RoninColors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        RoninMangaResultsGrid(
            results = results,
            selectedUrl = if (inspectOnClick) inspectedUrl else null,
            source = source,
            loading = loading,
            error = error,
            hasNext = hasNext,
            onOpenManga = { manga ->
                onInspectManga(manga)
                if (!inspectOnClick) onOpenManga(manga)
            },
            onLoadMore = onLoadMore,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun SearchContextPanel(
    onClose: () -> Unit,
    source: Source,
    manga: SManga?,
    inLibrary: Boolean,
    onAddToLibrary: (() -> Unit)?,
    onOpenManga: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    RoninPanel(modifier) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(RoninSpacing.large),
            verticalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                RoninInlineAction("×", onClose)
            }
            if (manga != null) {
                RoninCover(Modifier.fillMaxWidth().aspectRatio(RoninMangaMetrics.COVER_ASPECT_RATIO)) {
                    DesktopCover(
                        manga.thumbnail_url,
                        source,
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        mangaUrl = manga.url,
                    )
                }
                Text(
                    manga.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                manga.author?.takeIf(String::isNotBlank)?.let {
                    Text(it, color = RoninColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                }
                manga.genre?.takeIf(String::isNotBlank)?.let {
                    Text(
                        it,
                        color = RoninColors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                manga.description?.takeIf(String::isNotBlank)?.let {
                    Text(
                        it,
                        color = RoninColors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                onOpenManga?.let { RoninButton(roninText("View details", "Ver detalles"), it, Modifier.fillMaxWidth()) }
                onAddToLibrary?.let {
                    RoninSecondaryButton(
                        if (inLibrary) {
                            roninText("✓ In library", "✓ En biblioteca")
                        } else {
                            roninText("Add to library", "Añadir a biblioteca")
                        },
                        it,
                        Modifier.fillMaxWidth(),
                        enabled = !inLibrary,
                    )
                }
                Text(
                    source.roninSourceDisplayName(),
                    color = RoninColors.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
internal fun RoninMangaResultsGrid(
    results: List<SManga>,
    source: Source,
    loading: Boolean,
    error: String?,
    hasNext: Boolean,
    onOpenManga: (SManga) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    selectedUrl: String? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = RoninMangaMetrics.gridCellMinWidth),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(RoninLayout.gridGap),
        verticalArrangement = Arrangement.spacedBy(RoninSpacing.large),
    ) {
        items(results, key = SManga::url) { manga ->
            RoninSourceMangaCard(
                manga = manga,
                source = source,
                onClick = { onOpenManga(manga) },
                selected = manga.url == selectedUrl,
            )
        }
        if (loading || error != null || hasNext) {
            item(
                key = "source-results-footer",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = RoninSpacing.medium),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                ) {
                    error?.let {
                        Text(
                            it,
                            color = RoninColors.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (loading) {
                        RoninLoadingState(
                            title = "Loading more",
                            detail = source.roninSourceDisplayName(),
                        )
                    } else {
                        RoninSecondaryButton(
                            label = if (error == null) "Load more" else "Retry",
                            onClick = onLoadMore,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun RoninSourceMangaCard(
    manga: SManga,
    source: Source,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val interactionSource = remember(manga.url) { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    Surface(
        modifier = modifier
            .fillMaxWidth()
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
            if (selected) {
                RoninColors.accentCoral
            } else if (hovered) {
                RoninColors.accentCoral.copy(alpha = 0.42f)
            } else {
                RoninColors.borderSubtle
            },
        ),
    ) {
        Column(Modifier.fillMaxWidth()) {
            RoninCover(Modifier.fillMaxWidth().aspectRatio(RoninMangaMetrics.COVER_ASPECT_RATIO)) {
                DesktopCover(
                    manga.thumbnail_url,
                    source,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    mangaUrl = manga.url,
                )
            }
            Column(
                Modifier.padding(RoninSpacing.small),
                verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
            ) {
                Text(
                    manga.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(manga.author?.takeIf(String::isNotBlank), manga.genre?.takeIf(String::isNotBlank))
                        .joinToString(" · ").ifBlank { source.name },
                    color = RoninColors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
