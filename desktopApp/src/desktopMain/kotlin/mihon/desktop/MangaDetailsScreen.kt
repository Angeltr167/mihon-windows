package mihon.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SManga
import mihon.desktop.design.RoninColors
import mihon.desktop.design.RoninLayout
import mihon.desktop.design.RoninMangaMetrics
import mihon.desktop.design.RoninSpacing
import tachiyomi.data.GetCategories

@Composable
internal fun RoninMangaDetailsHero(
    manga: SManga,
    source: Source?,
    sourceName: String,
    inLibrary: Boolean,
    categoryIds: Set<Long>,
    categories: List<GetCategories>,
    linkedTrackers: List<String>,
    continueLabel: String,
    progressLabel: String?,
    detailsLoading: Boolean,
    detailsError: String?,
    canRead: Boolean,
    canToggleLibrary: Boolean,
    canManageTracking: Boolean,
    trackingExpanded: Boolean,
    onBack: () -> Unit,
    onRead: () -> Unit,
    onToggleLibrary: () -> Unit,
    onToggleTracking: () -> Unit,
    onRetry: () -> Unit,
    onOpenBrowser: (() -> Unit)?,
    onCopyLink: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val authors = listOfNotNull(manga.author, manga.artist)
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
    val genres = manga.getGenres().orEmpty()
    val assignedCategories = categories.filter { it.id in categoryIds }
        .map { it.name.ifBlank { "Uncategorized" } }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
    ) {
        RoninTextButton(label = "← Back", onClick = onBack)

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val useSidePanel = maxWidth >= 1080.dp

            val identity: @Composable (Modifier) -> Unit = { identityModifier ->
                RoninPanel(identityModifier) {
                    BoxWithConstraints(
                        Modifier.fillMaxWidth().padding(RoninSpacing.large),
                    ) {
                        val compactIdentity = maxWidth < 690.dp

                        val cover: @Composable () -> Unit = {
                            RoninCover {
                                DesktopCover(
                                    manga.thumbnail_url,
                                    source,
                                    Modifier
                                        .width(
                                            if (compactIdentity) {
                                                RoninMangaMetrics.coverGridWidth
                                            } else {
                                                RoninMangaMetrics.coverDetailWidth
                                            },
                                        )
                                        .height(
                                            if (compactIdentity) {
                                                RoninMangaMetrics.coverGridHeight
                                            } else {
                                                RoninMangaMetrics.coverDetailHeight
                                            },
                                        ),
                                )
                            }
                        }

                        val copy: @Composable () -> Unit = {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall)) {
                                    Text(
                                        manga.title,
                                        style = MaterialTheme.typography.displayMedium,
                                        color = RoninColors.textPrimary,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (authors.isNotEmpty()) {
                                        Text(
                                            authors.joinToString(" · "),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = RoninColors.textSecondary,
                                        )
                                    }
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                                    verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                                ) {
                                    RoninBadge(
                                        label = roninMangaStatusLabel(manga.status),
                                        accent = manga.status != SManga.UNKNOWN,
                                    )
                                    if (genres.isEmpty()) {
                                        RoninBadge(label = "No genres listed")
                                    } else {
                                        genres.forEach { genre ->
                                            RoninBadge(label = genre)
                                        }
                                    }
                                }

                                Text(
                                    manga.description.orEmpty().ifBlank { "No description available." },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = RoninColors.textSecondary,
                                    maxLines = 10,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                                    verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                                ) {
                                    RoninButton(
                                        label = continueLabel,
                                        onClick = onRead,
                                        enabled = canRead,
                                    )
                                    RoninSecondaryButton(
                                        label = if (inLibrary) "Remove from Library" else "Add to Library",
                                        onClick = onToggleLibrary,
                                        enabled = canToggleLibrary,
                                    )
                                    if (onOpenBrowser != null) {
                                        RoninTextButton(label = "Open in browser", onClick = onOpenBrowser)
                                    }
                                    if (onCopyLink != null) {
                                        RoninTextButton(label = "Copy link", onClick = onCopyLink)
                                    }
                                }

                                progressLabel?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = RoninColors.accentSage,
                                    )
                                }
                            }
                        }

                        if (compactIdentity) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(RoninSpacing.large),
                                horizontalAlignment = Alignment.Start,
                            ) {
                                cover()
                                copy()
                            }
                        } else {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(RoninSpacing.large),
                                verticalAlignment = Alignment.Top,
                            ) {
                                cover()
                                Box(Modifier.weight(1f)) { copy() }
                            }
                        }
                    }
                }
            }

            val sidePanel: @Composable (Modifier) -> Unit = { sideModifier ->
                RoninPanel(sideModifier) {
                    Column(
                        Modifier.fillMaxWidth().padding(RoninSpacing.large),
                        verticalArrangement = Arrangement.spacedBy(RoninSpacing.large),
                    ) {
                        RoninMetadataGroup(
                            label = "SOURCE",
                            value = sourceName,
                        )
                        RoninMetadataGroup(
                            label = "STATUS",
                            value = roninMangaStatusLabel(manga.status),
                            accent = manga.status != SManga.UNKNOWN,
                        )
                        RoninMetadataGroup(
                            label = "LIBRARY",
                            value = if (inLibrary) "In library" else "Not in library",
                            accent = inLibrary,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(RoninSpacing.small)) {
                            Text(
                                "CATEGORIES",
                                style = MaterialTheme.typography.labelSmall,
                                color = RoninColors.textMuted,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (assignedCategories.isEmpty()) {
                                Text(
                                    "Uncategorized",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = RoninColors.textSecondary,
                                )
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                                    verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                                ) {
                                    assignedCategories.forEach { RoninBadge(it) }
                                }
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(RoninSpacing.small)) {
                            Text(
                                "TRACKING",
                                style = MaterialTheme.typography.labelSmall,
                                color = RoninColors.textMuted,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                if (linkedTrackers.isEmpty()) {
                                    "No trackers linked"
                                } else {
                                    linkedTrackers.joinToString(" · ")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (linkedTrackers.isEmpty()) {
                                    RoninColors.textMuted
                                } else {
                                    RoninColors.textPrimary
                                },
                            )
                            RoninSecondaryButton(
                                label = if (trackingExpanded) "Close tracking" else "Manage tracking",
                                onClick = onToggleTracking,
                                enabled = canManageTracking,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            if (useSidePanel) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(RoninSpacing.large),
                    verticalAlignment = Alignment.Top,
                ) {
                    identity(Modifier.weight(1f))
                    sidePanel(Modifier.width(RoninLayout.rightPanelWidth))
                }
            } else {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
                ) {
                    identity(Modifier.fillMaxWidth())
                    sidePanel(Modifier.fillMaxWidth())
                }
            }
        }

        when {
            detailsLoading -> RoninLoadingState(
                title = "Refreshing manga details",
                detail = "Loading metadata and chapters from the source.",
            )
            detailsError != null -> Column(verticalArrangement = Arrangement.spacedBy(RoninSpacing.small)) {
                RoninErrorState(
                    title = "Could not refresh manga details",
                    detail = detailsError,
                )
                RoninSecondaryButton(label = "Retry", onClick = onRetry)
            }
        }
    }
}

@Composable
private fun RoninMetadataGroup(
    label: String,
    value: String,
    accent: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = RoninColors.textMuted,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (accent) RoninColors.accentSage else RoninColors.textPrimary,
        )
    }
}

internal fun roninMangaStatusLabel(status: Int): String = when (status) {
    SManga.ONGOING -> "Ongoing"
    SManga.COMPLETED -> "Completed"
    SManga.LICENSED -> "Licensed"
    SManga.PUBLISHING_FINISHED -> "Publishing finished"
    SManga.CANCELLED -> "Cancelled"
    SManga.ON_HIATUS -> "On hiatus"
    else -> "Unknown status"
}
