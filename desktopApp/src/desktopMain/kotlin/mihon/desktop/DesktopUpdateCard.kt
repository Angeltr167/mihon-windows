package mihon.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.source.Source
import tachiyomi.view.UpdatesView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun DesktopUpdateCard(
    entry: UpdatesView,
    source: Source?,
    onRead: () -> Unit,
    onDownload: () -> Unit,
) {
    val uploaded = if (entry.dateUpload > 0L) {
        Instant.ofEpochMilli(entry.dateUpload).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d"))
    } else {
        "Date unavailable"
    }
    MihonPanel {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val narrow = maxWidth < 520.dp
            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DesktopCover(entry.thumbnailUrl, source, Modifier.width(70.dp).height(100.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.mangaTitle, style = MaterialTheme.typography.titleMedium)
                        Text(entry.chapterName, color = MihonPalette.muted)
                        Text(
                            if (entry.read) "Read" else "Unread",
                            color = if (entry.read) MihonPalette.muted else MihonPalette.sage,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    if (!narrow) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(uploaded, color = MihonPalette.muted, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = onRead) { Text("Read") }
                            TextButton(onClick = onDownload) { Text("Download") }
                        }
                    }
                }
                if (narrow) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(uploaded, color = MihonPalette.muted, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onRead) { Text("Read") }
                        TextButton(onClick = onDownload) { Text("Download") }
                    }
                }
            }
        }
    }
}
