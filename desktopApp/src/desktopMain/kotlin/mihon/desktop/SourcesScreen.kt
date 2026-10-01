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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SManga
import mihon.desktop.design.RoninBorders
import mihon.desktop.design.RoninColors
import mihon.desktop.design.RoninRadius
import mihon.desktop.design.RoninSpacing
import tachiyomi.source.local.desktop.DesktopLocalSource

@Composable
internal fun SourcesScreen(
    sources: List<Source>,
    sourceSearch: String,
    onSourceSearchChange: (String) -> Unit,
    selectedSource: Source?,
    query: String,
    activeQuery: String,
    onQueryChange: (String) -> Unit,
    results: List<SManga>,
    loading: Boolean,
    error: String?,
    hasNext: Boolean,
    onSelectSource: (Source) -> Unit,
    onClearSource: () -> Unit,
    onBrowsePopular: () -> Unit,
    onSearch: () -> Unit,
    onOpenManga: (SManga) -> Unit,
    onLoadMore: () -> Unit,
) {
    if (selectedSource == null) {
        SourceDirectory(
            sources = sources,
            search = sourceSearch,
            onSearchChange = onSourceSearchChange,
            onSelectSource = onSelectSource,
        )
    } else {
        SourceBrowser(
            source = selectedSource,
            query = query,
            activeQuery = activeQuery,
            onQueryChange = onQueryChange,
            results = results,
            loading = loading,
            error = error,
            hasNext = hasNext,
            onBack = onClearSource,
            onBrowsePopular = onBrowsePopular,
            onSearch = onSearch,
            onOpenManga = onOpenManga,
            onLoadMore = onLoadMore,
        )
    }
}

@Composable
private fun SourceDirectory(
    sources: List<Source>,
    search: String,
    onSearchChange: (String) -> Unit,
    onSelectSource: (Source) -> Unit,
) {
    val uiLanguage = LocalRoninLanguage.current
    val languages = remember(sources) {
        sources.map { it.roninSourceLanguage() }.distinct().sorted()
    }
    var selectedLanguage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(languages) {
        if (selectedLanguage != null && selectedLanguage !in languages) {
            selectedLanguage = null
        }
    }

    val visibleSources = remember(sources, search, selectedLanguage) {
        sources.filter { source ->
            val matchesSearch = search.isBlank() ||
                source.name.contains(search, ignoreCase = true) ||
                source.lang.contains(search, ignoreCase = true)
            val matchesLanguage = selectedLanguage == null ||
                source.roninSourceLanguage() == selectedLanguage
            matchesSearch && matchesLanguage
        }
    }
    val groupedSources = remember(visibleSources) {
        visibleSources
            .groupBy { source ->
                if (source is DesktopLocalSource) {
                    "local:${source.id}"
                } else {
                    source.name.lowercase()
                }
            }
            .values
            .map { variants -> variants.sortedBy { it.lang } }
            .sortedBy { variants -> variants.first().name.lowercase() }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
    ) {
        RoninSectionHeader(
            title = Screen.SOURCES.localizedTitle(),
            pageHeading = true,
            subtitle = roninCopy(
                "${sources.size} installed source variants available to browse",
                "${sources.size} variantes de fuentes instaladas disponibles",
                uiLanguage,
            ),
            trailing = {
                RoninSearchField(
                    value = search,
                    onValueChange = onSearchChange,
                    placeholder = roninCopy("Search sources…", "Buscar fuentes…", uiLanguage),
                    modifier = Modifier.widthIn(min = 260.dp, max = 420.dp),
                )
            },
        )

        if (languages.size > 1) {
            RoninPanel(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(RoninSpacing.medium),
                    verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                ) {
                    Text(
                        roninCopy("Language", "Idioma", uiLanguage),
                        color = RoninColors.textMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                        verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                    ) {
                        RoninFilterPill(
                            label = roninCopy("All", "Todos", uiLanguage),
                            selected = selectedLanguage == null,
                            onClick = { selectedLanguage = null },
                        )
                        languages.forEach { language ->
                            RoninFilterPill(
                                label = language,
                                selected = selectedLanguage == language,
                                onClick = { selectedLanguage = language },
                            )
                        }
                    }
                }
            }
        }

        when {
            sources.isEmpty() -> RoninEmptyState(
                title = roninCopy("No sources available", "No hay fuentes disponibles", uiLanguage),
                detail = roninCopy(
                    "Install an extension or use the local source, then return here to browse.",
                    "Instala una extensión o utiliza la fuente local para explorar su catálogo.",
                    uiLanguage,
                ),
                modifier = Modifier.padding(top = RoninSpacing.small),
            )

            groupedSources.isEmpty() -> RoninEmptyState(
                title = roninCopy("No sources found", "No se encontraron fuentes", uiLanguage),
                detail = roninCopy(
                    "Change the source name or language filter.",
                    "Cambia el nombre de la fuente o el filtro de idioma.",
                    uiLanguage,
                ),
                modifier = Modifier.padding(top = RoninSpacing.small),
            )

            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(
                        top = RoninSpacing.small,
                        bottom = RoninSpacing.xSmall,
                    ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        roninCopy("Installed sources", "Fuentes instaladas", uiLanguage),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        roninCopy(
                            "${groupedSources.size} groups · ${visibleSources.size} variants",
                            "${groupedSources.size} grupos · ${visibleSources.size} variantes",
                            uiLanguage,
                        ),
                        color = RoninColors.textMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                RoninPanel(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = RoninSpacing.xSmall),
                    ) {
                        items(
                            items = groupedSources,
                            key = { variants ->
                                variants.joinToString("|") { it.id.toString() }
                            },
                        ) { variants ->
                            SourceGroupRow(
                                variants = variants,
                                onSelectSource = onSelectSource,
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = RoninSpacing.medium),
                                color = RoninColors.borderSubtle,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceGroupRow(
    variants: List<Source>,
    onSelectSource: (Source) -> Unit,
) {
    val primary = variants.preferredSourceVariant()
    val interactionSource = remember(primary.id, variants.size) { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
            ) { onSelectSource(primary) },
        color = if (hovered) RoninColors.hoverSurface else Color.Transparent,
    ) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().padding(
                horizontal = RoninSpacing.medium,
                vertical = RoninSpacing.small,
            ),
        ) {
            val narrow = maxWidth < 680.dp
            val stackActions = narrow || variants.size > 8
            if (stackActions) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                ) {
                    SourceIdentity(
                        source = primary,
                        variants = variants,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SourceVariantActions(
                        variants = variants,
                        onSelectSource = onSelectSource,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SourceIdentity(
                        source = primary,
                        variants = variants,
                        modifier = Modifier.weight(1f),
                    )
                    SourceVariantActions(
                        variants = variants,
                        onSelectSource = onSelectSource,
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceIdentity(
    source: Source,
    variants: List<Source>,
    modifier: Modifier = Modifier,
) {
    val uiLanguage = LocalRoninLanguage.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(RoninRadius.control),
            color = RoninColors.selectedSurface,
            border = BorderStroke(RoninBorders.hairline, RoninColors.border),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    source.name.firstOrNull()?.uppercase() ?: "?",
                    color = RoninColors.accentSage,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
        ) {
            Text(
                localizedSourceName(source, uiLanguage),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    source is DesktopLocalSource -> roninCopy(
                        "Local files on this device",
                        "Archivos locales de este equipo",
                        uiLanguage,
                    )
                    variants.size == 1 -> roninCopy(
                        "${source.roninSourceLanguage()} · Installed source",
                        "${source.roninSourceLanguage()} · Fuente instalada",
                        uiLanguage,
                    )
                    else -> roninCopy(
                        "${variants.size} installed language variants",
                        "${variants.size} variantes de idioma instaladas",
                        uiLanguage,
                    )
                },
                color = RoninColors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SourceVariantActions(
    variants: List<Source>,
    onSelectSource: (Source) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiLanguage = LocalRoninLanguage.current
    val primary = variants.preferredSourceVariant()
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(RoninSpacing.small),
        verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
    ) {
        RoninBadge(
            label = if (primary is DesktopLocalSource) {
                roninCopy(
                    "On device",
                    "En este equipo",
                    uiLanguage,
                )
            } else {
                roninCopy("Installed", "Instaladas", uiLanguage)
            },
            accent = true,
        )
        when {
            primary is DesktopLocalSource -> RoninBadge(primary.roninSourceLanguage())
            variants.size > 8 -> {
                RoninChip(
                    label = primary.roninSourceLanguage(),
                    onClick = { onSelectSource(primary) },
                )
                RoninBadge(roninCopy("${variants.size} languages", "${variants.size} idiomas", uiLanguage))
            }
            variants.size > 1 -> variants.forEach { variant ->
                RoninChip(
                    label = variant.roninSourceLanguage(),
                    onClick = { onSelectSource(variant) },
                )
            }
            else -> RoninBadge(primary.roninSourceLanguage())
        }
        Text(
            roninCopy("Browse →", "Explorar →", uiLanguage),
            color = RoninColors.accentSage,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

private fun List<Source>.preferredSourceVariant(): Source =
    firstOrNull { it.roninSourceLanguage() == "EN" } ?: first()

@Composable
private fun SourceBrowser(
    source: Source,
    query: String,
    activeQuery: String,
    onQueryChange: (String) -> Unit,
    results: List<SManga>,
    loading: Boolean,
    error: String?,
    hasNext: Boolean,
    onBack: () -> Unit,
    onBrowsePopular: () -> Unit,
    onSearch: () -> Unit,
    onOpenManga: (SManga) -> Unit,
    onLoadMore: () -> Unit,
) {
    val uiLanguage = LocalRoninLanguage.current
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
    ) {
        RoninSectionHeader(
            title = localizedSourceName(source, uiLanguage),
            subtitle = if (source is DesktopLocalSource) {
                roninCopy("Local · Manga stored on this device", "Local · Manga guardado en este equipo", uiLanguage)
            } else {
                roninCopy(
                    "${source.roninSourceLanguage()} · Installed source",
                    "${source.roninSourceLanguage()} · Fuente instalada",
                    uiLanguage,
                )
            },
            trailing = {
                RoninTextButton(
                    label = roninCopy("← All sources", "← Todas las fuentes", uiLanguage),
                    onClick = onBack,
                )
            },
        )

        SourceBrowseControls(
            source = source,
            query = query,
            activeQuery = activeQuery,
            onQueryChange = onQueryChange,
            loading = loading,
            onBrowsePopular = onBrowsePopular,
            onSearch = onSearch,
        )

        when {
            loading && results.isEmpty() -> RoninLoadingState(
                title = if (activeQuery.isBlank()) {
                    roninCopy(
                        "Loading popular manga",
                        "Cargando manga populares",
                        uiLanguage,
                    )
                } else {
                    roninCopy("Searching source", "Buscando en la fuente", uiLanguage)
                },
                detail = source.roninSourceDisplayName(),
                modifier = Modifier.padding(top = RoninSpacing.small),
            )

            error != null && results.isEmpty() -> Column(
                verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
            ) {
                RoninErrorState(
                    title = roninCopy("Source request failed", "Falló la consulta a la fuente", uiLanguage),
                    detail = error,
                )
                RoninSecondaryButton(
                    label = roninCopy("Retry", "Reintentar", uiLanguage),
                    onClick = onLoadMore,
                )
            }

            results.isEmpty() -> RoninEmptyState(
                title = if (activeQuery.isBlank()) {
                    roninCopy(
                        "No manga to show",
                        "No hay manga para mostrar",
                        uiLanguage,
                    )
                } else {
                    roninCopy("No manga found", "No se encontraron manga", uiLanguage)
                },
                detail = if (activeQuery.isBlank()) {
                    roninCopy(
                        "This source did not return popular manga.",
                        "Esta fuente no devolvió manga populares.",
                        uiLanguage,
                    )
                } else {
                    roninCopy(
                        "No results for “$activeQuery” in ${source.name}.",
                        "No hay resultados para “$activeQuery” en ${source.name}.",
                        uiLanguage,
                    )
                },
                modifier = Modifier.padding(top = RoninSpacing.small),
            )

            else -> {
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
                            if (activeQuery.isBlank()) {
                                roninCopy(
                                    "Popular",
                                    "Populares",
                                    uiLanguage,
                                )
                            } else {
                                roninCopy(
                                    "Results for “$activeQuery”",
                                    "Resultados de “$activeQuery”",
                                    uiLanguage,
                                )
                            },
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            roninCopy("${results.size} manga loaded", "${results.size} manga cargados", uiLanguage),
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
                            label = if (source is DesktopLocalSource) {
                                roninCopy(
                                    "On device",
                                    "En este equipo",
                                    uiLanguage,
                                )
                            } else {
                                roninCopy("Installed", "Instaladas", uiLanguage)
                            },
                            accent = true,
                        )
                    }
                }
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
            }
        }
    }
}

@Composable
private fun SourceBrowseControls(
    source: Source,
    query: String,
    activeQuery: String,
    onQueryChange: (String) -> Unit,
    loading: Boolean,
    onBrowsePopular: () -> Unit,
    onSearch: () -> Unit,
) {
    val uiLanguage = LocalRoninLanguage.current
    RoninPanel(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(RoninSpacing.medium)) {
            val narrow = maxWidth < 760.dp

            if (narrow) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                ) {
                    SourceContextChips(source, activeQuery)
                    RoninSearchField(
                        value = query,
                        onValueChange = onQueryChange,
                        placeholder = roninCopy("Search ${source.name}…", "Buscar en ${source.name}…", uiLanguage),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                    ) {
                        RoninButton(
                            label = if (loading) {
                                roninCopy(
                                    "Searching…",
                                    "Buscando…",
                                    uiLanguage,
                                )
                            } else {
                                roninCopy("Search", "Buscar", uiLanguage)
                            },
                            onClick = onSearch,
                            enabled = !loading,
                            modifier = Modifier.weight(1f),
                        )
                        RoninSecondaryButton(
                            label = roninCopy("Popular", "Populares", uiLanguage),
                            onClick = onBrowsePopular,
                            enabled = !loading,
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
                    SourceContextChips(source, activeQuery)
                    RoninSearchField(
                        value = query,
                        onValueChange = onQueryChange,
                        placeholder = roninCopy("Search ${source.name}…", "Buscar en ${source.name}…", uiLanguage),
                        modifier = Modifier.weight(1f),
                    )
                    RoninButton(
                        label = if (loading) {
                            roninCopy(
                                "Searching…",
                                "Buscando…",
                                uiLanguage,
                            )
                        } else {
                            roninCopy("Search", "Buscar", uiLanguage)
                        },
                        onClick = onSearch,
                        enabled = !loading,
                        modifier = Modifier.width(112.dp),
                    )
                    RoninSecondaryButton(
                        label = roninCopy("Popular", "Populares", uiLanguage),
                        onClick = onBrowsePopular,
                        enabled = !loading,
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceContextChips(
    source: Source,
    activeQuery: String,
) {
    val uiLanguage = LocalRoninLanguage.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
        verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
    ) {
        RoninBadge(source.roninSourceLanguage())
        RoninChip(
            label = if (activeQuery.isBlank()) {
                roninCopy(
                    "Popular",
                    "Populares",
                    uiLanguage,
                )
            } else {
                roninCopy("Search", "Buscar", uiLanguage)
            },
            selected = true,
        )
    }
}
