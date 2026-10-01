package mihon.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
internal fun DesktopBackupSettings(graph: DesktopPlatformGraph, session: DesktopSession, onRestored: () -> Unit) {
    val scope = rememberCoroutineScope()
    var folder by remember {
        mutableStateOf(
            graph.keyValueStore.getString(DesktopPreferences.BACKUP_DIRECTORY)
                ?: Path.of(graph.appDirectories.config).resolve("backups").toString(),
        )
    }
    var importPath by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var resultPath by remember { mutableStateOf<Path?>(null) }
    var status by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    val creating = roninText("Creating backup…", "Creando copia…")
    val restoring = roninText("Restoring backup…", "Restaurando copia…")
    val created = roninText("Backup saved", "Copia guardada")
    val restored = roninText("Backup restored", "Copia restaurada")
    val chaptersLabel = roninText("chapters", "capítulos")
    val errorLabel = roninText("Backup failed", "Error de copia de seguridad")
    val chooseFolder = roninText("Choose backup folder", "Elegir carpeta de copias de seguridad")
    val chooseFile = roninText("Choose Ronin or Mihon backup", "Elegir copia de Ronin o Mihon")
    RoninPanel(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(RoninSpacing.large),
            verticalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
        ) {
            Text(roninText("Local backup", "Copia de seguridad local"), style = MaterialTheme.typography.titleLarge)
            Text(
                roninText(
                    "Includes library, categories, chapter progress, bookmarks, history, tracker links and basic preferences. Downloaded images, local files, extensions and login credentials are not included.",
                    "Incluye biblioteca, categorías, progreso, marcadores, historial, vínculos de seguimiento y preferencias básicas. No incluye imágenes descargadas, archivos locales, extensiones ni credenciales.",
                ),
                color = RoninColors.textMuted,
            )
            OutlinedTextField(
                folder,
                { folder = it },
                enabled = !busy,
                singleLine = true,
                label = {
                    Text(roninText("Destination folder", "Carpeta de destino"))
                },
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
            ) {
                RoninSecondaryButton(roninText("Choose folder…", "Elegir carpeta…"), {
                    scope.launch {
                        busy = true
                        try {
                            withContext(Dispatchers.IO) {
                                graph.fileDialogService.chooseDirectory(chooseFolder, folder)
                            }?.let {
                                folder = it
                                graph.keyValueStore.putString(DesktopPreferences.BACKUP_DIRECTORY, it)
                            }
                        } finally {
                            busy = false
                        }
                    }
                }, enabled = !busy)
                RoninButton(roninText("Create backup", "Crear copia"), {
                    scope.launch {
                        busy = true
                        failed = false
                        status = creating
                        resultPath = null
                        try {
                            val path = withContext(Dispatchers.IO) {
                                DesktopBackupExporter.export(
                                    Path.of(folder.trim()),
                                    session.library,
                                    DesktopPreferences(graph.keyValueStore).export(),
                                )
                            }
                            graph.keyValueStore.putString(DesktopPreferences.BACKUP_DIRECTORY, path.parent.toString())
                            folder = path.parent.toString()
                            resultPath = path
                            status = "$created: $path"
                        } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            failed = true
                            status = "$errorLabel: ${error.message}"
                        } finally {
                            busy = false
                        }
                    }
                }, enabled = !busy && folder.isNotBlank())
                resultPath?.let { path ->
                    RoninTextButton(roninText("Open folder", "Abrir carpeta"), {
                        graph.externalOpenService.openPath(path.parent.toString())
                    })
                }
            }
            Text(
                roninText(
                    "Each copy creates a new .tachibk file without replacing previous copies.",
                    "Cada copia crea un archivo .tachibk nuevo sin reemplazar las copias anteriores.",
                ),
                color = RoninColors.textMuted,
            )
            androidx.compose.material3.HorizontalDivider(color = RoninColors.borderSubtle)
            Text(roninText("Restore backup", "Restaurar copia"), style = MaterialTheme.typography.titleMedium)
            Text(
                roninText(
                    "Restore a Ronin or Mihon .tachibk file. Matching records and included preferences are updated; other library entries are kept.",
                    "Restaura un archivo .tachibk de Ronin o Mihon. Se actualizan los registros coincidentes y las preferencias incluidas; se conservan las demás entradas.",
                ),
                color = RoninColors.textMuted,
            )
            OutlinedTextField(
                importPath,
                { importPath = it },
                enabled = !busy,
                singleLine = true,
                label = {
                    Text(roninText("Backup file (.tachibk)", "Archivo de copia (.tachibk)"))
                },
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
            ) {
                RoninSecondaryButton(roninText("Choose file…", "Elegir archivo…"), {
                    scope.launch {
                        busy = true
                        try {
                            withContext(Dispatchers.IO) {
                                graph.fileDialogService.chooseOpenFile(
                                    OpenFileRequest(chooseFile, extensions = setOf("tachibk")),
                                )
                            }?.let { importPath = it }
                        } finally {
                            busy = false
                        }
                    }
                }, enabled = !busy)
                RoninButton(roninText("Restore backup", "Restaurar copia"), {
                    scope.launch {
                        busy = true
                        failed = false
                        status = restoring
                        try {
                            val summary = withContext(Dispatchers.IO) {
                                DesktopBackupImporter.import(
                                    Path.of(importPath.trim()),
                                    session.library,
                                    graph.keyValueStore,
                                )
                            }
                            onRestored()
                            status = "$restored: ${summary.manga} manga · ${summary.chapters} $chaptersLabel"
                        } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            failed = true
                            status = "$errorLabel: ${error.message}"
                        } finally {
                            busy = false
                        }
                    }
                }, enabled = !busy && importPath.isNotBlank())
            }
            if (status.isNotBlank()) Text(status, color = if (failed) RoninColors.error else RoninColors.textSecondary)
        }
    }
}
