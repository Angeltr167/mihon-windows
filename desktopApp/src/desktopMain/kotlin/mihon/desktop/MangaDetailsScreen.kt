package mihon.desktop

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
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
    coverActions: @Composable () -> Unit = {},
    coverSourceId: Long? = source?.id,
    modifier: Modifier = Modifier,
) {
    var expandedSynopsis by remember(manga.url) { mutableStateOf(false) }
    var moreExpanded by remember(manga.url) { mutableStateOf(false) }
    val author = manga.author?.trim()?.takeIf(String::isNotBlank)
    val artist = manga.artist?.trim()?.takeIf(String::isNotBlank)
    val genres = manga.getGenres().orEmpty()
    val uncategorizedLabel = roninText("Uncategorized", "Sin categoría")
    val assignedCategories = categories.filter { it.id in categoryIds }.map { it.name.ifBlank { uncategorizedLabel } }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RoninSpacing.large)) {
        RoninInlineAction(roninText("‹ Library", "‹ Biblioteca"), onBack)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val narrow = maxWidth < 600.dp
            val coverWidth = if (narrow) 132.dp else RoninMangaMetrics.coverDetailWidth
            val cover: @Composable () -> Unit = {
                Column(Modifier.width(coverWidth), verticalArrangement = Arrangement.spacedBy(RoninSpacing.small)) {
                    RoninCover(Modifier.width(coverWidth).aspectRatio(RoninMangaMetrics.COVER_ASPECT_RATIO)) {
                        DesktopCover(
                            manga.thumbnail_url,
                            source,
                            Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            mangaUrl = manga.url,
                            sourceId = coverSourceId,
                        )
                    }
                    coverActions()
                }
            }
            val identity: @Composable (Modifier) -> Unit = { identityModifier ->
                Column(identityModifier, verticalArrangement = Arrangement.spacedBy(RoninSpacing.medium)) {
                    Text(
                        manga.title,
                        style = if (narrow) {
                            MaterialTheme.typography.headlineLarge
                        } else {
                            MaterialTheme.typography.displayMedium
                        },
                        color = RoninColors.textPrimary,
                    )
                    author?.let {
                        Text(it, color = RoninColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                    artist?.takeIf {
                        it != author
                    }?.let { Text(it, color = RoninColors.textMuted, style = MaterialTheme.typography.bodySmall) }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                        verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                    ) {
                        genres.take(4).forEach { RoninBadge(it) }
                        if (genres.size > 4) RoninBadge("+${genres.size - 4}")
                    }
                    manga.description?.takeIf(String::isNotBlank)?.let { description ->
                        Text(
                            description,
                            color = RoninColors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (expandedSynopsis) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        RoninInlineAction(
                            roninText(
                                if (expandedSynopsis) "Show less" else "Read more",
                                if (expandedSynopsis) "Ver menos" else "Leer más",
                            ),
                            { expandedSynopsis = !expandedSynopsis },
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                        verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                    ) {
                        RoninButton("▶ $continueLabel", onRead, enabled = canRead)
                        RoninSecondaryButton(
                            if (inLibrary) {
                                roninText(
                                    "✓ In library",
                                    "✓ En biblioteca",
                                )
                            } else {
                                roninText("Add to library", "Añadir a biblioteca")
                            },
                            onToggleLibrary,
                            enabled = canToggleLibrary,
                        )
                        Box {
                            RoninSecondaryButton("⋮", { moreExpanded = true })
                            DropdownMenu(moreExpanded, { moreExpanded = false }) {
                                if (inLibrary) {
                                    DropdownMenuItem(
                                        text = { Text(roninText("Remove from library", "Quitar de biblioteca")) },
                                        onClick = {
                                            moreExpanded = false
                                            onToggleLibrary()
                                        },
                                        enabled = canToggleLibrary,
                                    )
                                }
                                onOpenBrowser?.let { action ->
                                    DropdownMenuItem(text = { Text("Open in browser") }, onClick = {
                                        moreExpanded =
                                            false
                                        action()
                                    })
                                }
                                onCopyLink?.let { action ->
                                    DropdownMenuItem(text = { Text("Copy link") }, onClick = {
                                        moreExpanded =
                                            false
                                        action()
                                    })
                                }
                                DropdownMenuItem(text = {
                                    Text(roninText("Manage tracking", "Gestionar seguimiento"))
                                }, onClick = {
                                    moreExpanded =
                                        false
                                    onToggleTracking()
                                }, enabled = canManageTracking)
                            }
                        }
                    }
                    progressLabel?.let {
                        Text(it, color = RoninColors.accentSage, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        listOf(sourceName, roninMangaStatusLabel(manga.status)).joinToString(" · "),
                        color = RoninColors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (narrow) {
                Column(verticalArrangement = Arrangement.spacedBy(RoninSpacing.medium)) {
                    cover()
                    identity(Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(RoninSpacing.large),
                    verticalAlignment = Alignment.Top,
                ) {
                    cover()
                    identity(Modifier.weight(1f))
                }
            }
        }
        if (trackingExpanded) {
            RoninPanel(Modifier.fillMaxWidth()) {
                FlowRow(
                    Modifier.padding(RoninSpacing.medium),
                    horizontalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
                    verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                ) {
                    Text(
                        linkedTrackers.joinToString(" · ").ifBlank {
                            "No trackers linked"
                        },
                        color = RoninColors.textSecondary,
                    )
                    assignedCategories.forEach { RoninBadge(it) }
                    RoninInlineAction(roninText("Close tracking", "Cerrar seguimiento"), onToggleTracking)
                }
            }
        }
        when {
            detailsLoading -> RoninLoadingState(roninText("Refreshing details", "Actualizando detalles"))
            detailsError != null -> Column(verticalArrangement = Arrangement.spacedBy(RoninSpacing.small)) {
                RoninErrorState(
                    roninText("Could not refresh details", "No se pudieron actualizar los detalles"),
                    detailsError,
                )
                RoninSecondaryButton(roninText("Retry", "Reintentar"), onRetry)
            }
        }
        HorizontalDivider(color = RoninColors.borderSubtle)
    }
}

@Composable
internal fun roninMangaStatusLabel(status: Int): String = when (status) {
    SManga.ONGOING -> roninText("Ongoing", "En publicación")
    SManga.COMPLETED -> roninText("Completed", "Completado")
    SManga.LICENSED -> roninText("Licensed", "Licenciado")
    SManga.PUBLISHING_FINISHED -> roninText("Publishing finished", "Publicación terminada")
    SManga.CANCELLED -> roninText("Cancelled", "Cancelado")
    SManga.ON_HIATUS -> roninText("On hiatus", "En pausa")
    else -> roninText("Unknown status", "Estado desconocido")
}
