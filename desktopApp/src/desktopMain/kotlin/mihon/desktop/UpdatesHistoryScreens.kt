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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    val groups = remember(entries) {
        entries.groupBy { desktopDateGroup(it.dateUpload) }.toList()
    }

    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
    ) {
        RoninSectionHeader(
            title = "Updates",
            subtitle = "New chapters from manga in your library",
            trailing = {
                RoninButton(
                    label = if (updateRunning) "Checking…" else "Check library",
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
                title = "Loading updates",
                detail = "Reading the current library update feed.",
            )
            error != null && entries.isEmpty() -> RoninStateWithRetry(
                title = "Could not load updates",
                detail = error,
                onRetry = onRetry,
            )
            updateRunning && entries.isEmpty() -> RoninLoadingState(
                title = "Checking library",
                detail = "New chapters will appear here as sources finish.",
            )
            entries.isEmpty() -> RoninEmptyState(
                title = "No updates yet",
                detail = "Check your library when sources are available to look for new chapters.",
            )
            else -> {
                if (error != null) {
                    RoninErrorState(
                        title = "Refresh failed",
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
            title = "Reading history",
            subtitle = "Recent chapters and saved reading positions",
            trailing = {
                RoninSearchField(
                    value = search,
                    onValueChange = onSearchChange,
                    placeholder = "Search history",
                    modifier = Modifier.width(280.dp),
                )
            },
        )

        when {
            loading -> RoninLoadingState(
                title = "Loading history",
                detail = "Reading saved activity and progress.",
            )
            error != null && entries.isEmpty() -> RoninStateWithRetry(
                title = "Could not load history",
                detail = error,
                onRetry = onRetry,
            )
            visibleHistory.isEmpty() -> RoninEmptyState(
                title = if (search.isBlank()) "Nothing read yet" else "No history found",
                detail = if (search.isBlank()) {
                    "Your reading activity will appear here after you open a chapter."
                } else {
                    "Try a different manga title."
                },
            )
            else -> {
                if (error != null) {
                    RoninErrorState(
                        title = "Refresh failed",
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
        if (maxWidth >= 1080.dp) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
            ) {
                Box(Modifier.weight(1f).fillMaxSize()) {
                    feed()
                }
                Box(
                    Modifier.width(290.dp).fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    summary()
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                summary()
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
            title,
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
    val unread = remember(entries) { entries.count { !it.read } }
    val sourceCount = remember(entries) { entries.map(UpdatesView::source).distinct().size }

    RoninPanel(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(RoninSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
        ) {
            Text(
                "Feed snapshot",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(RoninSpacing.large),
            ) {
                RoninStat(
                    label = "Shown",
                    value = entries.size.toString(),
                    modifier = Modifier.weight(1f),
                )
                RoninStat(
                    label = "Unread",
                    value = unread.toString(),
                    modifier = Modifier.weight(1f),
                )
                RoninStat(
                    label = "Sources",
                    value = sourceCount.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider(color = RoninColors.borderSubtle)
            Text(
                "Recent activity",
                color = RoninColors.textSecondary,
                style = MaterialTheme.typography.labelLarge,
            )
            entries.take(3).forEach { entry ->
                RecentActivityLine(
                    title = entry.mangaTitle,
                    detail = entry.chapterName,
                    timestamp = compactTimestamp(entry.dateUpload),
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
    val finished = remember(entries, chapters) {
        entries.count { chapters[it.chapterId]?.read == true }
    }

    RoninPanel(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(RoninSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
        ) {
            Text(
                "Reading activity",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(RoninSpacing.large),
            ) {
                RoninStat(
                    label = "Titles shown",
                    value = entries.size.toString(),
                    modifier = Modifier.weight(1f),
                )
                RoninStat(
                    label = "Latest finished",
                    value = finished.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider(color = RoninColors.borderSubtle)
            Text(
                "Recent activity",
                color = RoninColors.textSecondary,
                style = MaterialTheme.typography.labelLarge,
            )
            entries.take(3).forEach { entry ->
                RecentActivityLine(
                    title = entry.title,
                    detail = "Chapter ${entry.chapterNumber}",
                    timestamp = compactTimestamp(entry.readAt?.time ?: 0L),
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
            label = "Retry",
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

private fun compactTimestamp(epochMillis: Long): String {
    if (epochMillis <= 0L) return "Date unavailable"
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d · HH:mm"))
}
