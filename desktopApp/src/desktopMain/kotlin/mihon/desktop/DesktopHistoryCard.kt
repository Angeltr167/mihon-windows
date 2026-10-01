package mihon.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.source.Source
import mihon.desktop.design.RoninColors
import mihon.desktop.design.RoninSpacing
import tachiyomi.data.Chapters
import tachiyomi.view.History
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun DesktopHistoryCard(
    entry: History,
    chapter: Chapters?,
    source: Source?,
    pageCount: Int?,
    onContinue: () -> Unit,
    mangaUrl: String? = null,
) {
    val uiLanguage = LocalRoninLanguage.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val canResume = chapter != null
    val pageIndex = chapter?.last_page_read
    val normalizedPageCount = pageCount?.takeIf { it > 0 }
    val progress = when {
        chapter?.read == true -> 1f
        pageIndex != null && normalizedPageCount != null ->
            ((pageIndex + 1).coerceAtMost(normalizedPageCount.toLong()).toFloat() / normalizedPageCount)
        else -> null
    }
    val progressLabel = when {
        chapter == null -> roninCopy("Chapter data unavailable", "Datos del capítulo no disponibles", uiLanguage)
        chapter.read -> roninCopy("Chapter finished", "Capítulo terminado", uiLanguage)
        normalizedPageCount != null ->
            roninCopy(
                "Page ${(chapter.last_page_read + 1).coerceAtMost(
                    normalizedPageCount.toLong(),
                )} of $normalizedPageCount",
                "Página ${(chapter.last_page_read + 1).coerceAtMost(
                    normalizedPageCount.toLong(),
                )} de $normalizedPageCount",
                uiLanguage,
            )
        else -> roninCopy("Page ${chapter.last_page_read + 1}", "Página ${chapter.last_page_read + 1}", uiLanguage)
    }
    val readAtLabel = entry.readAt?.time?.takeIf { it > 0L }?.let {
        Instant.ofEpochMilli(it)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d · HH:mm", java.util.Locale.forLanguageTag(uiLanguage)))
    } ?: roninCopy("Reading date unavailable", "Fecha de lectura no disponible", uiLanguage)
    val sourceLabel = source?.let {
        "${localizedSourceName(it, uiLanguage)} · ${it.roninSourceLanguage()}"
    }

    Column(Modifier.fillMaxWidth()) {
        Surface(
            modifier = if (canResume) {
                Modifier.fillMaxWidth()
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onContinue,
                    )
            } else {
                Modifier.fillMaxWidth()
            },
            color = if (hovered && canResume) RoninColors.hoverSurface else Color.Transparent,
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val compact = maxWidth < 760.dp
                Column(
                    Modifier.fillMaxWidth().padding(
                        horizontal = RoninSpacing.medium,
                        vertical = RoninSpacing.small,
                    ),
                    verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RoninCover(Modifier.width(70.dp)) {
                            DesktopCover(
                                entry.thumbnailUrl,
                                source,
                                Modifier.fillMaxWidth().height(100.dp),
                                mangaUrl = mangaUrl,
                                sourceId = entry.source,
                            )
                        }
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                        ) {
                            Text(
                                entry.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                chapter?.name
                                    ?: roninCopy(
                                        "Chapter ${entry.chapterNumber}",
                                        "Capítulo ${formatChapterNumber(entry.chapterNumber)}",
                                        uiLanguage,
                                    ),
                                color = RoninColors.textSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    progressLabel,
                                    color = RoninColors.textMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                sourceLabel?.let {
                                    RoninBadge(label = it)
                                }
                            }
                            progress?.let {
                                RoninProgressBar(
                                    progress = it,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            if (compact) {
                                Text(
                                    readAtLabel,
                                    color = RoninColors.textMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        if (!compact) {
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                            ) {
                                Text(
                                    readAtLabel,
                                    color = RoninColors.textMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                RoninButton(
                                    label = roninCopy("Resume", "Continuar", uiLanguage),
                                    onClick = onContinue,
                                    enabled = canResume,
                                )
                            }
                        }
                    }
                    if (compact) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            RoninButton(
                                label = roninCopy("Resume", "Continuar", uiLanguage),
                                onClick = onContinue,
                                enabled = canResume,
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = RoninColors.borderSubtle)
    }
}
