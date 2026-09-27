package mihon.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.source.Source
import tachiyomi.data.Chapters
import tachiyomi.data.Mangas
import tachiyomi.view.History

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
    val visibleLibrary = remember(library, membership, selectedCategory, search) {
        library.asSequence()
            .filter { it.title.contains(search, ignoreCase = true) }
            .filter { manga ->
                selectedCategory == null || run {
                    val assigned = membership[manga._id].orEmpty()
                    if (selectedCategory == 0L) assigned.isEmpty() else selectedCategory in assigned
                }
            }
            .toList()
    }

    Column(Modifier.fillMaxSize()) {
        MihonSectionHeader(
            "Your library",
            if (selectedCategory == null) {
                "${library.size} manga saved on this device"
            } else {
                "${visibleLibrary.size} manga in this category"
            },
            trailing = {
                androidx.compose.material3.OutlinedTextField(
                    search,
                    onSearchChange,
                    label = { Text("Search library") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(0.36f),
                )
            },
        )
        if (selectedCategory != null) {
            TextButton(onClick = onClearCategory) { Text("All library") }
        }
        when {
            loading -> MihonEmptyState("Loading library…")
            library.isEmpty() -> MihonEmptyState(
                "Your library is empty",
                "Browse an installed source and add a manga to start reading.",
            )
            visibleLibrary.isEmpty() -> MihonEmptyState("No manga found", "Try a different search or category.")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(220.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(MihonSpacing.md),
                verticalArrangement = Arrangement.spacedBy(MihonSpacing.md),
            ) {
                items(visibleLibrary, key = Mangas::_id) { manga ->
                    val source = sources.firstOrNull { it.id == manga.source }
                    val chapter = recentChapters[manga._id]
                    MihonPanel {
                        Column(Modifier.fillMaxWidth().padding(MihonSpacing.sm)) {
                            DesktopCover(
                                manga.thumbnail_url,
                                source,
                                Modifier.fillMaxWidth().aspectRatio(0.75f)
                                    .clickable { onOpenManga(manga._id, null) },
                                contentScale = ContentScale.Crop,
                            )
                            Text(
                                manga.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                modifier = Modifier.fillMaxWidth().padding(top = MihonSpacing.sm),
                            )
                            Text(
                                chapter?.name ?: "Not read yet",
                                color = MihonPalette.muted,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                            )
                            if (chapter != null) {
                                Text(
                                    if (chapter.read) "Chapter finished" else "Page ${chapter.last_page_read + 1}",
                                    color = if (chapter.read) MihonPalette.muted else MihonPalette.sage,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            TextButton(onClick = { onOpenManga(manga._id, chapter?.url) }) {
                                Text(if (chapter != null) "Continue reading" else "Details")
                            }
                        }
                    }
                }
            }
        }
    }
}
