package mihon.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.source.Source
import tachiyomi.data.Chapters
import tachiyomi.data.Mangas
import tachiyomi.view.History

private enum class LibraryShelfFilter(val label: String) {
    ALL("All"),
    READING("In progress"),
    NOT_STARTED("Not started"),
    LAST_CHAPTER_FINISHED("Chapter finished"),
}

@Composable
internal fun LibraryScreen(
    library: List<Mangas>,
    membership: Map<Long, Set<Long>>,
    historyEntries: List<History>,
    historyChapters: Map<Long, Chapters?>,
    sources: List<Source>,
    selectedCategory: Long?,
    onClearCategory: () -> Unit,
    search: String,
    onSearchChange: (String) -> Unit,
    loading: Boolean,
    onOpenManga: (Long, String?) -> Unit,
) {
    val recentChapters = remember(historyEntries, historyChapters) {
        historyEntries.mapNotNull { entry ->
            historyChapters[entry.chapterId]?.let { chapter -> entry.mangaId to chapter }
        }.toMap()
    }
    var shelfFilter by remember { mutableStateOf(LibraryShelfFilter.ALL) }
    val visibleLibrary = remember(library, membership, selectedCategory, search, shelfFilter, recentChapters) {
        library.asSequence()
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
    }

    Column(Modifier.fillMaxSize()) {
        MihonSectionHeader(
            "Library",
            if (selectedCategory == null) {
                "${library.size} manga on this device"
            } else {
                "${visibleLibrary.size} manga in this category"
            },
            trailing = {
                androidx.compose.material3.OutlinedTextField(
                    search,
                    onSearchChange,
                    label = { Text("Search your library…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(0.42f),
                )
            },
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = MihonSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(MihonSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(MihonSpacing.xs),
        ) {
            LibraryShelfFilter.entries.forEach { filter ->
                MihonChoiceChip(
                    label = filter.label,
                    selected = shelfFilter == filter,
                    onClick = { shelfFilter = filter },
                )
            }
            if (selectedCategory != null) {
                MihonChoiceChip("All library", selected = false, onClick = onClearCategory)
            }
        }

        when {
            loading -> MihonEmptyState("Loading library…")
            library.isEmpty() -> MihonEmptyState(
                "Your library is empty",
                "Browse an installed source and add a manga to start reading.",
            )
            visibleLibrary.isEmpty() -> MihonEmptyState(
                "No manga found",
                "Change the search, shelf filter, or category.",
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(190.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(MihonSpacing.md),
                verticalArrangement = Arrangement.spacedBy(MihonSpacing.lg),
            ) {
                items(visibleLibrary, key = Mangas::_id) { manga ->
                    val source = sources.firstOrNull { it.id == manga.source }
                    val chapter = recentChapters[manga._id]
                    MihonPanel {
                        Column(Modifier.fillMaxWidth().padding(MihonSpacing.sm)) {
                            Box {
                                DesktopCover(
                                    manga.thumbnail_url,
                                    source,
                                    Modifier.fillMaxWidth().aspectRatio(0.72f),
                                    contentScale = ContentScale.Crop,
                                )
                                val badge = when {
                                    chapter == null -> "NEW"
                                    chapter.read -> "✓"
                                    else -> "P${chapter.last_page_read + 1}"
                                }
                                Surface(
                                    modifier = Modifier.align(Alignment.TopEnd)
                                        .padding(MihonSpacing.xs),
                                    color = MihonPalette.graphite.copy(alpha = 0.90f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(MihonRadius.control),
                                ) {
                                    Text(
                                        badge,
                                        modifier = Modifier.padding(
                                            horizontal = MihonSpacing.sm,
                                            vertical = MihonSpacing.xs,
                                        ),
                                        color = if (chapter?.read == false) MihonPalette.sage else MihonPalette.ivory,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                            Text(
                                manga.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                modifier = Modifier.fillMaxWidth().padding(top = MihonSpacing.sm),
                            )
                            Text(
                                chapter?.name ?: "Not started",
                                color = MihonPalette.muted,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                            )
                            if (chapter != null) {
                                Text(
                                    if (chapter.read) {
                                        "Last chapter finished"
                                    } else {
                                        "Continue from page ${chapter.last_page_read + 1}"
                                    },
                                    color = if (chapter.read) MihonPalette.muted else MihonPalette.sage,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                )
                            }
                            TextButton(
                                onClick = { onOpenManga(manga._id, chapter?.url) },
                                modifier = Modifier.align(Alignment.End),
                            ) {
                                Text(if (chapter != null) "Continue" else "Details")
                            }
                        }
                    }
                }
            }
        }
    }
}
