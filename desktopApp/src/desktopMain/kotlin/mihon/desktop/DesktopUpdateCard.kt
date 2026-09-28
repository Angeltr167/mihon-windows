package mihon.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import tachiyomi.view.UpdatesView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun DesktopUpdateCard(
    entry: UpdatesView,
    source: Source?,
    download: DesktopDownload?,
    onRead: () -> Unit,
    onDownload: () -> Unit,
    onResumeDownload: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val published = formatUpdateTimestamp(entry.dateUpload)
    val sourceLabel = source?.let {
        if (it.lang == "localsourcelang") it.name else "${it.name} · ${it.lang.uppercase()}"
    }
    val downloadLabel = when (download?.status) {
        null -> "Download"
        DesktopDownloadStatus.PENDING -> "Queued"
        DesktopDownloadStatus.RUNNING -> if (download.pageCount > 0) {
            "Downloading ${download.pagesDone}/${download.pageCount}"
        } else {
            "Downloading"
        }
        DesktopDownloadStatus.PAUSED -> "Resume download"
        DesktopDownloadStatus.FAILED -> "Retry download"
        DesktopDownloadStatus.COMPLETED -> "Downloaded"
    }
    val downloadEnabled = download == null ||
        download.status == DesktopDownloadStatus.PAUSED ||
        download.status == DesktopDownloadStatus.FAILED
    val downloadAction = if (
        download?.status == DesktopDownloadStatus.PAUSED ||
        download?.status == DesktopDownloadStatus.FAILED
    ) {
        onResumeDownload
    } else {
        onDownload
    }

    Column(Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth()
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onRead,
                ),
            color = if (hovered) RoninColors.hoverSurface else Color.Transparent,
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
                            )
                        }
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                        ) {
                            Text(
                                entry.mangaTitle,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                entry.chapterName,
                                color = if (entry.read) RoninColors.textMuted else RoninColors.textPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                                verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                            ) {
                                entry.scanlator?.takeIf(String::isNotBlank)?.let {
                                    RoninBadge(label = it)
                                }
                                sourceLabel?.let {
                                    RoninBadge(label = it)
                                }
                                if (entry.read) {
                                    RoninBadge(label = "Read", accent = true)
                                }
                            }
                            if (compact) {
                                Text(
                                    published,
                                    color = RoninColors.textMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        if (!compact) {
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                            ) {
                                Text(
                                    published,
                                    color = RoninColors.textMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RoninInlineAction(
                                        label = downloadLabel,
                                        onClick = downloadAction,
                                        enabled = downloadEnabled,
                                    )
                                    RoninButton(
                                        label = "Read",
                                        onClick = onRead,
                                    )
                                }
                            }
                        }
                    }
                    if (compact) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RoninInlineAction(
                                label = downloadLabel,
                                onClick = downloadAction,
                                enabled = downloadEnabled,
                            )
                            RoninButton(
                                label = "Read",
                                onClick = onRead,
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = RoninColors.borderSubtle)
    }
}

private fun formatUpdateTimestamp(epochMillis: Long): String {
    if (epochMillis <= 0L) return "Date unavailable"
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d · HH:mm"))
}
