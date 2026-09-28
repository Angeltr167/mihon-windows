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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SManga
import mihon.desktop.design.RoninBorders
import mihon.desktop.design.RoninColors
import mihon.desktop.design.RoninLayout
import mihon.desktop.design.RoninMangaMetrics
import mihon.desktop.design.RoninRadius
import mihon.desktop.design.RoninSpacing
import tachiyomi.source.local.desktop.DesktopLocalSource

internal fun Source.roninSourceDisplayName(): String =
    if (this is DesktopLocalSource || lang == "localsourcelang") name else "$name · ${lang.uppercase()}"

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
    onLoadMore: () -> Unit,
) {
    var sourceMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
    ) {
        RoninSectionHeader(
            title = "Search",
            subtitle = "Search one installed source with the source's real catalog and pagination",
            trailing = {
                RoninTextButton(
                    label = if (showLinkTools) "Hide link tools" else "Open manga link…",
                    onClick = onToggleLinkTools,
                )
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

        RoninPanel(Modifier.fillMaxWidth()) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(RoninSpacing.medium)) {
                val narrow = maxWidth < 760.dp
                if (narrow) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                    ) {
                        SearchSourcePicker(
                            sources = sources,
                            selectedSource = selectedSource,
                            expanded = sourceMenuExpanded,
                            onExpandedChange = { sourceMenuExpanded = it },
                            onSelectSource = onSelectSource,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        RoninSearchField(
                            value = query,
                            onValueChange = onQueryChange,
                            placeholder = "Search manga…",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        RoninButton(
                            label = if (loading) "Searching…" else "Search",
                            onClick = onSearch,
                            enabled = selectedSource != null && !loading,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SearchSourcePicker(
                            sources = sources,
                            selectedSource = selectedSource,
                            expanded = sourceMenuExpanded,
                            onExpandedChange = { sourceMenuExpanded = it },
                            onSelectSource = onSelectSource,
                            modifier = Modifier.widthIn(min = 190.dp, max = 280.dp),
                        )
                        RoninSearchField(
                            value = query,
                            onValueChange = onQueryChange,
                            placeholder = "Search manga…",
                            modifier = Modifier.weight(1f),
                        )
                        RoninButton(
                            label = if (loading) "Searching…" else "Search",
                            onClick = onSearch,
                            enabled = selectedSource != null && !loading,
                            modifier = Modifier.width(112.dp),
                        )
                    }
                }
            }
        }

        when {
            selectedSource == null -> RoninEmptyState(
                title = "Choose a source",
                detail = "Select an installed source above. Ronin will keep the search scoped to that source.",
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
                onLoadMore = onLoadMore,
                modifier = Modifier.weight(1f),
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
            label = selectedSource?.roninSourceDisplayName() ?: "Choose source",
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
                                    source.name,
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
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                top = RoninSpacing.small,
                bottom = RoninSpacing.small,
            ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(RoninSpacing.micro)) {
                Text(
                    if (activeQuery.isBlank()) "Source browse" else "Results for “$activeQuery”",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "${results.size} loaded",
                    color = RoninColors.textMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
            ) {
                RoninBadge(source.roninSourceLanguage())
                RoninBadge(
                    label = if (source is DesktopLocalSource) "On device" else "Installed",
                    accent = true,
                )
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth >= 1180.dp) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
                ) {
                    RoninMangaResultsGrid(
                        results = results,
                        source = source,
                        loading = loading,
                        error = error,
                        hasNext = hasNext,
                        onOpenManga = onOpenManga,
                        onLoadMore = onLoadMore,
                        modifier = Modifier.weight(1f),
                    )
                    SearchContextPanel(
                        source = source,
                        activeQuery = activeQuery,
                        resultCount = results.size,
                        modifier = Modifier.width(260.dp),
                    )
                }
            } else {
                RoninMangaResultsGrid(
                    results = results,
                    source = source,
                    loading = loading,
                    error = error,
                    hasNext = hasNext,
                    onOpenManga = onOpenManga,
                    onLoadMore = onLoadMore,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun SearchContextPanel(
    source: Source,
    activeQuery: String,
    resultCount: Int,
    modifier: Modifier = Modifier,
) {
    RoninPanel(modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(RoninSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
        ) {
            Text("Search scope", style = MaterialTheme.typography.titleMedium)
            RoninStat(label = "Loaded", value = resultCount.toString())
            Column(verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall)) {
                Text(
                    "Source",
                    color = RoninColors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    source.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall)) {
                Text(
                    "Language",
                    color = RoninColors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(source.roninSourceLanguage(), style = MaterialTheme.typography.bodyMedium)
            }
            Column(verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall)) {
                Text(
                    "Mode",
                    color = RoninColors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    if (activeQuery.isBlank()) "Popular browse" else "Source search",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                "Results and pagination come directly from the selected installed source.",
                color = RoninColors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
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
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 174.dp),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(RoninLayout.gridGap),
        verticalArrangement = Arrangement.spacedBy(RoninSpacing.large),
    ) {
        items(results, key = SManga::url) { manga ->
            RoninSourceMangaCard(
                manga = manga,
                source = source,
                onClick = { onOpenManga(manga) },
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
) {
    val interactionSource = remember(manga.url) { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val status = mangaStatusLabel(manga.status)

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
            if (hovered) RoninColors.accentCoral.copy(alpha = 0.42f) else RoninColors.border,
        ),
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
                status?.let {
                    RoninBadge(
                        label = it,
                        accent = manga.status == SManga.ONGOING,
                        modifier = Modifier.align(Alignment.TopEnd).padding(RoninSpacing.small),
                    )
                }
            }
            Text(
                manga.title,
                modifier = Modifier.fillMaxWidth().padding(top = RoninSpacing.small),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                source.name,
                color = RoninColors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            manga.author?.takeIf { it.isNotBlank() }?.let { author ->
                Text(
                    author,
                    color = RoninColors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = RoninSpacing.xSmall),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoninBadge(source.roninSourceLanguage())
                Text(
                    "Details →",
                    color = RoninColors.accentSage,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

private fun mangaStatusLabel(status: Int): String? = when (status) {
    SManga.ONGOING -> "ONGOING"
    SManga.COMPLETED -> "COMPLETE"
    SManga.LICENSED -> "LICENSED"
    SManga.PUBLISHING_FINISHED -> "FINISHED"
    SManga.CANCELLED -> "CANCELLED"
    SManga.ON_HIATUS -> "HIATUS"
    else -> null
}
