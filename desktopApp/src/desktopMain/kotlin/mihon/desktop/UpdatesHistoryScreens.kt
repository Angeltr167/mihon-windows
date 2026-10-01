package mihon.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.source.Source
import mihon.desktop.design.RoninColors
import mihon.desktop.design.RoninLayout
import mihon.desktop.design.RoninSpacing
import tachiyomi.data.Chapters
import tachiyomi.data.Mangas
import tachiyomi.view.History
import tachiyomi.view.UpdatesView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class DownloadTarget(
    val sourceId: Long,
    val mangaUrl: String,
    val chapterUrl: String,
)

@Composable
internal fun RoninUpdatesScreen(
    entries: List<UpdatesView>,
    library: List<Mangas>,
    sources: List<Source>,
    downloads: List<DesktopDownload>,
    loading: Boolean,
    error: String?,
    updateRunning: Boolean,
    updateMessage: String,
    updateFailures: Int,
    onCheckUpdates: () -> Unit,
    onRetry: () -> Unit,
    onRead: (UpdatesView) -> Unit,
    onDownload: (UpdatesView) -> Unit,
    onResumeDownload: (DesktopDownload) -> Unit,
) {
    val uiLanguage = LocalRoninLanguage.current
    val libraryById = remember(library) { library.associateBy(Mangas::_id) }
    val sourcesById = remember(sources) { sources.associateBy(Source::id) }
    val downloadsByTarget = remember(downloads) {
        downloads.associateBy {
            DownloadTarget(
                sourceId = it.sourceId,
                mangaUrl = it.mangaUrl,
                chapterUrl = it.chapterUrl,
            )
        }
    }
    val groups = remember(entries, uiLanguage) {
        entries.groupBy { desktopDateGroup(it.dateUpload, uiLanguage) }.toList()
    }

    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
    ) {
        RoninSectionHeader(
            title = Screen.UPDATES.localizedTitle(),
            pageHeading = true,
            subtitle = roninText("New chapters from manga in your library", "Capítulos nuevos de tu biblioteca"),
            trailing = {
                RoninButton(
                    label = if (updateRunning) {
                        roninCopy(
                            "Checking…",
                            "Comprobando…",
                            uiLanguage,
                        )
                    } else {
                        roninCopy("Check library", "Actualizar biblioteca", uiLanguage)
                    },
                    onClick = onCheckUpdates,
                    enabled = !updateRunning,
                )
            },
        )

        if (updateMessage.isNotBlank()) {
            RoninPanel(Modifier.fillMaxWidth()) {
                Text(
                    updateMessage,
                    modifier = Modifier.fillMaxWidth().padding(RoninSpacing.medium),
                    color = if (updateFailures > 0) RoninColors.error else RoninColors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        when {
            loading -> RoninLoadingState(
                title = roninCopy("Loading updates", "Cargando actualizaciones", uiLanguage),
                detail = roninCopy(
                    "Reading the current library update feed.",
                    "Cargando los capítulos nuevos de tu biblioteca.",
                    uiLanguage,
                ),
            )
            error != null && entries.isEmpty() -> RoninStateWithRetry(
                title = roninCopy("Could not load updates", "No se pudieron cargar las actualizaciones", uiLanguage),
                detail = error,
                onRetry = onRetry,
            )
            updateRunning && entries.isEmpty() -> RoninLoadingState(
                title = roninCopy("Checking library", "Comprobando biblioteca", uiLanguage),
                detail = roninCopy(
                    "New chapters will appear here as sources finish.",
                    "Los capítulos nuevos aparecerán al terminar de consultar las fuentes.",
                    uiLanguage,
                ),
            )
            entries.isEmpty() -> RoninEmptyState(
                title = roninCopy("No updates yet", "Todavía no hay actualizaciones", uiLanguage),
                detail = roninCopy(
                    "Check your library when sources are available to look for new chapters.",
                    "Actualiza la biblioteca para buscar capítulos nuevos.",
                    uiLanguage,
                ),
            )
            else -> {
                if (error != null) {
                    RoninErrorState(
                        title = roninCopy("Refresh failed", "Falló la actualización", uiLanguage),
                        detail = error,
                    )
                }
                RoninResponsiveFeedLayout(
                    modifier = Modifier.weight(1f),
                    summary = {
                        UpdatesSummary(entries)
                    },
                ) {
                    RoninPanel(Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = RoninSpacing.small),
                        ) {
                            groups.forEach { (group, groupEntries) ->
                                item(key = "updates-group-$group") {
                                    RoninFeedGroupHeader(group)
                                }
                                items(groupEntries, key = UpdatesView::chapterId) { entry ->
                                    val manga = libraryById[entry.mangaId]
                                    val download = manga?.let {
                                        downloadsByTarget[
                                            DownloadTarget(
                                                sourceId = entry.source,
                                                mangaUrl = it.url,
                                                chapterUrl = entry.chapterUrl,
                                            ),
                                        ]
                                    }
                                    DesktopUpdateCard(
                                        entry = entry,
                                        mangaUrl = manga?.url,
                                        source = sourcesById[entry.source],
                                        download = download,
                                        onRead = { onRead(entry) },
                                        onDownload = { onDownload(entry) },
                                        onResumeDownload = {
                                            download?.let(onResumeDownload)
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

@Composable
internal fun RoninHistoryScreen(
    entries: List<History>,
    chapters: Map<Long, Chapters?>,
    library: List<Mangas>,
    sources: List<Source>,
    downloads: List<DesktopDownload>,
    search: String,
    onSearchChange: (String) -> Unit,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onResume: (History, Chapters?) -> Unit,
) {
    val uiLanguage = LocalRoninLanguage.current
    val libraryById = remember(library) { library.associateBy(Mangas::_id) }
    val sourcesById = remember(sources) { sources.associateBy(Source::id) }
    val downloadsByTarget = remember(downloads) {
        downloads.associateBy {
            DownloadTarget(
                sourceId = it.sourceId,
                mangaUrl = it.mangaUrl,
                chapterUrl = it.chapterUrl,
            )
        }
    }
    val visibleHistory = remember(entries, search) {
        if (search.isBlank()) {
            entries
        } else {
            entries.filter { it.title.contains(search, ignoreCase = true) }
        }
    }
    val groups = remember(visibleHistory) {
        val grouped = visibleHistory.groupBy { historyGroup(it.readAt?.time ?: 0L) }
        listOf("Today", "This week", "Earlier").mapNotNull { name ->
            grouped[name]?.let { name to it }
        }
    }

    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
    ) {
        RoninSectionHeader(
            title = roninText("Reading history", "Historial de lectura"),
            pageHeading = true,
            subtitle = roninText(
                "Recent chapters and saved reading positions",
                "Capítulos recientes y progreso guardado",
            ),
            trailing = {
                RoninSearchField(
                    value = search,
                    onValueChange = onSearchChange,
                    placeholder = roninCopy("Search history", "Buscar en el historial", uiLanguage),
                    modifier = Modifier.width(280.dp),
                )
            },
        )

        when {
            loading -> RoninLoadingState(
                title = roninCopy("Loading history", "Cargando historial", uiLanguage),
                detail = roninCopy(
                    "Reading saved activity and progress.",
                    "Cargando la actividad y el progreso guardados.",
                    uiLanguage,
                ),
            )
            error != null && entries.isEmpty() -> RoninStateWithRetry(
                title = roninCopy("Could not load history", "No se pudo cargar el historial", uiLanguage),
                detail = error,
                onRetry = onRetry,
            )
            visibleHistory.isEmpty() -> RoninEmptyState(
                title = if (search.isBlank()) {
                    roninCopy(
                        "Nothing read yet",
                        "Aún no has leído capítulos",
                        uiLanguage,
                    )
                } else {
                    roninCopy("No history found", "No se encontró historial", uiLanguage)
                },
                detail = if (search.isBlank()) {
                    roninCopy(
                        "Your reading activity will appear here after you open a chapter.",
                        "Tu actividad aparecerá aquí después de abrir un capítulo.",
                        uiLanguage,
                    )
                } else {
                    roninCopy("Try a different manga title.", "Prueba otro título de manga.", uiLanguage)
                },
            )
            else -> {
                if (error != null) {
                    RoninErrorState(
                        title = roninCopy("Refresh failed", "Falló la actualización", uiLanguage),
                        detail = error,
                    )
                }
                RoninResponsiveFeedLayout(
                    modifier = Modifier.weight(1f),
                    summary = {
                        HistorySummary(
                            entries = visibleHistory,
                            chapters = chapters,
                        )
                    },
                ) {
                    RoninPanel(Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = RoninSpacing.small),
                        ) {
                            groups.forEach { (group, groupEntries) ->
                                item(key = "history-group-$group") {
                                    RoninFeedGroupHeader(group)
                                }
                                items(groupEntries, key = History::id) { entry ->
                                    val chapter = chapters[entry.chapterId]
                                    val manga = libraryById[entry.mangaId]
                                    val pageCount = if (chapter != null && manga != null) {
                                        downloadsByTarget[
                                            DownloadTarget(
                                                sourceId = entry.source,
                                                mangaUrl = manga.url,
                                                chapterUrl = chapter.url,
                                            ),
                                        ]?.pageCount?.takeIf { it > 0 }
                                    } else {
                                        null
                                    }
                                    DesktopHistoryCard(
                                        entry = entry,
                                        mangaUrl = manga?.url,
                                        chapter = chapter,
                                        source = sourcesById[entry.source],
                                        pageCount = pageCount,
                                        onContinue = { onResume(entry, chapter) },
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

@Composable
private fun RoninResponsiveFeedLayout(
    modifier: Modifier = Modifier,
    summary: @Composable () -> Unit,
    feed: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth >= RoninLayout.rightPanelBreakpoint) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
            ) {
                Box(Modifier.weight(1f).fillMaxSize()) {
                    feed()
                }
                Box(
                    Modifier.width(RoninLayout.rightPanelWidth).fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    summary()
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier.heightIn(max = RoninLayout.compactSummaryMaxHeight).verticalScroll(rememberScrollState()),
                ) {
                    summary()
                }
                Spacer(Modifier.height(RoninSpacing.medium))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    feed()
                }
            }
        }
    }
}

@Composable
private fun RoninFeedGroupHeader(title: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            roninUiText(title),
            modifier = Modifier.padding(
                start = RoninSpacing.medium,
                top = RoninSpacing.medium,
                end = RoninSpacing.medium,
                bottom = RoninSpacing.small,
            ),
            color = RoninColors.accentSage,
            style = MaterialTheme.typography.labelLarge,
        )
        HorizontalDivider(color = RoninColors.borderSubtle)
    }
}

@Composable
private fun UpdatesSummary(entries: List<UpdatesView>) {
    val uiLanguage = LocalRoninLanguage.current
    val unread = remember(entries) { entries.count { !it.read } }
    val sourceCount = remember(entries) { entries.map(UpdatesView::source).distinct().size }

    RoninPanel(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(RoninSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
        ) {
            Text(
                roninCopy("Feed snapshot", "Resumen de actualizaciones", uiLanguage),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(RoninSpacing.large),
            ) {
                RoninStat(
                    label = roninCopy("Shown", "Mostrados", uiLanguage),
                    value = entries.size.toString(),
                    modifier = Modifier.weight(1f),
                )
                RoninStat(
                    label = roninCopy("Unread", "Sin leer", uiLanguage),
                    value = unread.toString(),
                    modifier = Modifier.weight(1f),
                )
                RoninStat(
                    label = roninCopy("Sources", "Fuentes", uiLanguage),
                    value = sourceCount.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider(color = RoninColors.borderSubtle)
            Text(
                roninCopy("Recent activity", "Actividad reciente", uiLanguage),
                color = RoninColors.textSecondary,
                style = MaterialTheme.typography.labelLarge,
            )
            entries.take(3).forEach { entry ->
                RecentActivityLine(
                    title = entry.mangaTitle,
                    detail = entry.chapterName,
                    timestamp = compactTimestamp(entry.dateUpload, uiLanguage),
                )
            }
        }
    }
}

@Composable
private fun HistorySummary(
    entries: List<History>,
    chapters: Map<Long, Chapters?>,
) {
    val uiLanguage = LocalRoninLanguage.current
    val finished = remember(entries, chapters) {
        entries.count { chapters[it.chapterId]?.read == true }
    }

    RoninPanel(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(RoninSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
        ) {
            Text(
                roninCopy("Reading activity", "Actividad de lectura", uiLanguage),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(RoninSpacing.large),
            ) {
                RoninStat(
                    label = roninCopy("Titles shown", "Títulos mostrados", uiLanguage),
                    value = entries.size.toString(),
                    modifier = Modifier.weight(1f),
                )
                RoninStat(
                    label = roninCopy("Latest finished", "Últimos terminados", uiLanguage),
                    value = finished.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider(color = RoninColors.borderSubtle)
            Text(
                roninCopy("Recent activity", "Actividad reciente", uiLanguage),
                color = RoninColors.textSecondary,
                style = MaterialTheme.typography.labelLarge,
            )
            entries.take(3).forEach { entry ->
                RecentActivityLine(
                    title = entry.title,
                    detail = roninCopy(
                        "Chapter ${entry.chapterNumber}",
                        "Capítulo ${formatChapterNumber(entry.chapterNumber)}",
                        uiLanguage,
                    ),
                    timestamp = compactTimestamp(entry.readAt?.time ?: 0L, uiLanguage),
                )
            }
        }
    }
}

@Composable
private fun RecentActivityLine(
    title: String,
    detail: String,
    timestamp: String,
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RoninSpacing.micro),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
        Text(
            detail,
            color = RoninColors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
        )
        Text(
            timestamp,
            color = RoninColors.textMuted,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun RoninStateWithRetry(
    title: String,
    detail: String,
    onRetry: () -> Unit,
) {
    val uiLanguage = LocalRoninLanguage.current
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
        horizontalAlignment = Alignment.Start,
    ) {
        RoninErrorState(
            title = title,
            detail = detail,
        )
        RoninSecondaryButton(
            label = roninCopy("Retry", "Reintentar", uiLanguage),
            onClick = onRetry,
        )
    }
}

private fun historyGroup(epochMillis: Long): String {
    if (epochMillis <= 0L) return "Earlier"
    val date = Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    val today = java.time.LocalDate.now()
    return when {
        date == today -> "Today"
        !date.isBefore(today.minusDays(6)) -> "This week"
        else -> "Earlier"
    }
}

private fun compactTimestamp(epochMillis: Long, languageTag: String): String {
    if (epochMillis <= 0L) return localizeRoninLabel("Date unavailable", languageTag)
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d · HH:mm", java.util.Locale.forLanguageTag(languageTag)))
}
