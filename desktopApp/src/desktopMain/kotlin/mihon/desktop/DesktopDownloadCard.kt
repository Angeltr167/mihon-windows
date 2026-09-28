package mihon.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.source.Source
import mihon.desktop.design.RoninColors
import mihon.desktop.design.RoninMangaMetrics
import mihon.desktop.design.RoninSpacing

@Composable
internal fun DesktopDownloadCard(
    download: DesktopDownload,
    coverUrl: String?,
    source: Source?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRead: () -> Unit,
) {
    RoninPanel {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val narrow = maxWidth < 520.dp
            Column(Modifier.fillMaxWidth().padding(mihon.desktop.design.RoninSpacing.medium)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(mihon.desktop.design.RoninSpacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DesktopCover(
                        coverUrl,
                        source,
                        Modifier.width(
                            RoninMangaMetrics.coverCompactWidth,
                        ).height(RoninMangaMetrics.coverCompactHeight),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall)) {
                        Text(download.mangaTitle, style = MaterialTheme.typography.titleMedium)
                        Text(download.chapterName, color = RoninColors.textMuted)
                        Text(
                            when (download.status) {
                                DesktopDownloadStatus.PENDING -> "Queued"
                                DesktopDownloadStatus.RUNNING -> "Downloading"
                                DesktopDownloadStatus.PAUSED -> "Paused"
                                DesktopDownloadStatus.FAILED -> "Failed"
                                DesktopDownloadStatus.COMPLETED -> "Completed"
                            },
                            color = if (download.status == DesktopDownloadStatus.FAILED) {
                                MaterialTheme.colorScheme.error
                            } else {
                                RoninColors.accentSage
                            },
                            style = MaterialTheme.typography.labelMedium,
                        )
                        if (download.pageCount > 0 && download.status != DesktopDownloadStatus.FAILED) {
                            LinearProgressIndicator(
                                progress = { (download.pagesDone.toFloat() / download.pageCount).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                                color = RoninColors.accentSage,
                                trackColor = RoninColors.borderSubtle,
                            )
                            Text(
                                "${download.pagesDone} / ${download.pageCount} pages",
                                color = RoninColors.textMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        download.error?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (!narrow) {
                        DownloadActions(download, onPause, onResume, onCancel, onRead)
                    }
                }
                if (narrow) {
                    DownloadActions(download, onPause, onResume, onCancel, onRead)
                }
            }
        }
    }
}

@Composable
private fun DownloadActions(
    download: DesktopDownload,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRead: () -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.End) {
        when (download.status) {
            DesktopDownloadStatus.PENDING, DesktopDownloadStatus.RUNNING -> {
                TextButton(onClick = onPause) { Text("Pause") }
            }
            DesktopDownloadStatus.PAUSED, DesktopDownloadStatus.FAILED -> {
                TextButton(onClick = onResume) {
                    Text(if (download.status == DesktopDownloadStatus.FAILED) "Retry" else "Resume")
                }
            }
            DesktopDownloadStatus.COMPLETED -> {
                TextButton(onClick = onRead) { Text("Read") }
            }
        }
        if (download.status != DesktopDownloadStatus.COMPLETED) {
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}
