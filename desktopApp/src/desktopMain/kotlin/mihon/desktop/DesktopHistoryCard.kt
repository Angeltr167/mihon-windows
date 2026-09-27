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
    onContinue: () -> Unit,
) {
    val progressLabel = if (chapter?.read == true) "Chapter finished" else "Page ${(chapter?.last_page_read ?: 0) + 1}"
    val readAtLabel = Instant.ofEpochMilli(entry.readAt?.time ?: 0L)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d · HH:mm"))
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
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(entry.title, style = MaterialTheme.typography.titleMedium)
                        Text(chapter?.name ?: "Chapter ${entry.chapterNumber}", color = MihonPalette.muted)
                        Text(progressLabel, color = MihonPalette.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!narrow) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(readAtLabel, color = MihonPalette.muted, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = onContinue) { Text("Continue") }
                        }
                    }
                }
                if (narrow) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(readAtLabel, color = MihonPalette.muted, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onContinue) { Text("Continue") }
                    }
                }
            }
        }
    }
}
