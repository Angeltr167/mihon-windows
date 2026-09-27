package mihon.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.source.local.desktop.DesktopLocalSource

private fun Source.searchDisplayName(): String =
    if (this is DesktopLocalSource || lang == "localsourcelang") name else "$name · ${lang.uppercase()}"

@Composable
internal fun SearchScreen(
    sources: List<Source>,
    selectedSource: Source?,
    query: String,
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

    Column(Modifier.fillMaxSize()) {
        MihonSectionHeader(
            "Search",
            "Find manga in an installed source",
            trailing = {
                TextButton(onClick = onToggleLinkTools) {
                    Text(if (showLinkTools) "Hide link tools" else "Open manga link…")
                }
            },
        )

        if (showLinkTools) {
            MihonPanel(Modifier.fillMaxWidth().padding(bottom = MihonSpacing.md)) {
                Row(
                    Modifier.fillMaxWidth().padding(MihonSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(MihonSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        link,
                        onLinkChange,
                        label = { Text("Manga or Mihon link") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = onOpenLink, enabled = link.isNotBlank()) { Text("Open") }
                    TextButton(onClick = onPasteLink) { Text("Paste") }
                }
            }
        }

        MihonPanel(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(MihonSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(MihonSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    MihonChoiceChip(
                        label = selectedSource?.searchDisplayName() ?: "Choose source",
                        selected = selectedSource != null,
                        onClick = { sourceMenuExpanded = true },
                    )
                    DropdownMenu(
                        expanded = sourceMenuExpanded,
                        onDismissRequest = { sourceMenuExpanded = false },
                    ) {
                        sources.forEach { source ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(source.name)
                                        if (source !is DesktopLocalSource && source.lang != "localsourcelang") {
                                            Text(
                                                source.lang.uppercase(),
                                                color = MihonPalette.muted,
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    sourceMenuExpanded = false
                                    onSelectSource(source)
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    query,
                    onQueryChange,
                    label = { Text("Search manga…") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onSearch, enabled = selectedSource != null && !loading) {
                    Text(if (loading) "Searching…" else "Search")
                }
            }
        }

        when {
            selectedSource == null -> MihonEmptyState(
                "Choose a source",
                "Select an installed source above, then search without leaving this screen.",
            )
            loading && results.isEmpty() -> MihonEmptyState("Searching…", selectedSource.searchDisplayName())
            error != null && results.isEmpty() -> {
                MihonEmptyState("Search failed", error)
                TextButton(onClick = onSearch) { Text("Retry") }
            }
            results.isEmpty() -> MihonEmptyState(
                if (query.isBlank()) "Nothing to show yet" else "No manga found",
                if (query.isBlank()) {
                    "Enter a title or browse the selected source."
                } else {
                    "Try a different query or source."
                },
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(320.dp),
                modifier = Modifier.weight(1f).padding(top = MihonSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(MihonSpacing.md),
                verticalArrangement = Arrangement.spacedBy(MihonSpacing.md),
            ) {
                items(results, key = SManga::url) { manga ->
                    MihonPanel(
                        Modifier.fillMaxWidth().clickable { onOpenManga(manga) },
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(MihonSpacing.sm),
                            horizontalArrangement = Arrangement.spacedBy(MihonSpacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DesktopCover(
                                manga.thumbnail_url,
                                selectedSource,
                                Modifier.width(78.dp).height(112.dp),
                            )
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(MihonSpacing.xs),
                            ) {
                                Text(manga.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                                Text(
                                    selectedSource.searchDisplayName(),
                                    color = MihonPalette.muted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    "Open details",
                                    color = MihonPalette.sage,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
                if (loading || error != null || hasNext) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            error?.let {
                                Text(it, color = MaterialTheme.colorScheme.error)
                            }
                            if (loading) {
                                Text("Loading…", color = MihonPalette.muted)
                            } else if (hasNext || error != null) {
                                TextButton(onClick = onLoadMore) {
                                    Text(if (error == null) "Load more" else "Retry")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
