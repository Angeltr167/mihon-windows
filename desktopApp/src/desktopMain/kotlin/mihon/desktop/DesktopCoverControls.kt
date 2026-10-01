package mihon.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.desktop.design.RoninColors
import mihon.desktop.design.RoninSpacing
import mihon.platform.api.OpenFileRequest
import mihon.platform.desktop.DesktopPlatformGraph
import java.nio.file.Path

@Composable
internal fun DesktopCoverControls(
    graph: DesktopPlatformGraph,
    covers: DesktopCustomCovers,
    sourceId: Long,
    mangaUrl: String,
    onChanged: () -> Unit,
) {
    val language = LocalRoninLanguage.current
    val scope = rememberCoroutineScope()
    var busy by remember(sourceId, mangaUrl) { mutableStateOf(false) }
    var error by remember(sourceId, mangaUrl) { mutableStateOf<String?>(null) }
    LocalRoninCoverRevision.current
    val custom = covers.cover(sourceId, mangaUrl) != null
    val fit = covers.useFit(sourceId, mangaUrl)
    val chooseLabel = roninText("Choose cover", "Elegir portada")
    fun change(action: suspend () -> Boolean) {
        scope.launch {
            busy = true
            error = null
            try {
                if (action()) onChanged()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                error = roninCopy(
                    "Could not change the cover. Choose a valid PNG or JPEG (up to 16 MB).",
                    "No se pudo cambiar la portada. Elige un PNG o JPEG válido (hasta 16 MB).",
                    language,
                )
            } finally {
                busy = false
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall)) {
        RoninSecondaryButton(
            if (busy) {
                roninText("Saving…", "Guardando…")
            } else if (custom) {
                roninText("Change cover…", "Cambiar portada…")
            } else {
                roninText("Choose cover…", "Elegir portada…")
            },
            onClick = {
                change {
                    withContext(Dispatchers.IO) {
                        val selected = graph.fileDialogService.chooseOpenFile(
                            OpenFileRequest(chooseLabel, extensions = setOf("png", "jpg", "jpeg")),
                        ) ?: return@withContext false
                        covers.importCover(sourceId, mangaUrl, Path.of(selected))
                        true
                    }
                }
            },
            enabled = !busy,
        )
        if (custom) {
            RoninTextButton(
                if (fit) {
                    roninText("Fill frame", "Rellenar")
                } else {
                    roninText("Show full image", "Ver completa")
                },
                onClick = {
                    change {
                        withContext(Dispatchers.IO) { covers.setFit(sourceId, mangaUrl, !fit) }
                        true
                    }
                },
                enabled = !busy,
            )
            RoninTextButton(
                roninText("Restore original", "Restaurar original"),
                onClick = {
                    change {
                        withContext(Dispatchers.IO) { covers.remove(sourceId, mangaUrl) }
                        true
                    }
                },
                enabled = !busy,
            )
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("PNG · JPEG", style = MaterialTheme.typography.labelSmall, color = RoninColors.textMuted)
    }
}
