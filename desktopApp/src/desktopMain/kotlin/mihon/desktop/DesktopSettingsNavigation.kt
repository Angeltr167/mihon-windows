package mihon.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import mihon.core.reader.FitMode
import mihon.core.reader.ReadingMode
import mihon.desktop.design.RoninBorders
import mihon.desktop.design.RoninColors
import mihon.desktop.design.RoninLayout
import mihon.desktop.design.RoninRadius
import mihon.desktop.design.RoninSpacing
import mihon.platform.desktop.DesktopPlatformGraph

internal enum class DesktopSettingsSection(val title: String, val description: String) {
    GENERAL("General", "Language and application preferences"),
    LIBRARY("Library", "Library organization and update schedule"),
    READER("Reader", "Reading mode and page fit defaults"),
    DOWNLOADS("Downloads", "Queue behavior and completed chapters"),
    TRACKING("Tracking", "Tracker sign-in and manga links"),
    STORAGE("Storage & Backup", "Local paths and Android backup import"),
    ADVANCED("Advanced", "Windows links and extension trust"),
}

@Composable
internal fun DesktopSettingsSection.localizedTitle(): String = when (this) {
    DesktopSettingsSection.GENERAL -> roninText("General", "General")
    DesktopSettingsSection.LIBRARY -> roninText("Library", "Biblioteca")
    DesktopSettingsSection.READER -> roninText("Reader", "Lector")
    DesktopSettingsSection.DOWNLOADS -> roninText("Downloads", "Descargas")
    DesktopSettingsSection.TRACKING -> roninText("Tracking", "Seguimiento")
    DesktopSettingsSection.STORAGE -> roninText("Storage & Backup", "Almacenamiento y copias")
    DesktopSettingsSection.ADVANCED -> roninText("Advanced", "Avanzado")
}

@Composable
internal fun DesktopSettingsSection.localizedDescription(): String = when (this) {
    DesktopSettingsSection.GENERAL -> roninText(description, "Idioma y preferencias de la aplicación")
    DesktopSettingsSection.LIBRARY -> roninText(description, "Organización y actualización de la biblioteca")
    DesktopSettingsSection.READER -> roninText(description, "Modo de lectura y ajuste de páginas")
    DesktopSettingsSection.DOWNLOADS -> roninText(description, "Cola y capítulos descargados")
    DesktopSettingsSection.TRACKING -> roninText(description, "Cuentas y vínculos de seguimiento")
    DesktopSettingsSection.STORAGE -> roninText(description, "Copias locales y restauración")
    DesktopSettingsSection.ADVANCED -> roninText(description, "Enlaces de Windows y confianza en extensiones")
}

@Composable
internal fun DesktopSettingsNavigation(
    selected: DesktopSettingsSection,
    onSelect: (DesktopSettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    RoninPanel(modifier) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(RoninSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
        ) {
            Text(
                roninText("PREFERENCES", "PREFERENCIAS"),
                modifier = Modifier.padding(horizontal = RoninSpacing.small, vertical = RoninSpacing.xSmall),
                color = RoninColors.textMuted,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            )
            DesktopSettingsSection.entries.forEach { section ->
                val isSelected = selected == section
                Surface(
                    modifier = Modifier.fillMaxWidth()
                        .semantics { this.selected = isSelected }
                        .clickable(role = Role.Tab) { onSelect(section) },
                    shape = RoundedCornerShape(RoninRadius.control),
                    color = if (isSelected) RoninColors.selectedSurface else Color.Transparent,
                    border = if (isSelected) {
                        BorderStroke(
                            RoninBorders.hairline,
                            RoninColors.accentCoral.copy(alpha = 0.38f),
                        )
                    } else {
                        null
                    },
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(
                            horizontal = RoninSpacing.medium,
                            vertical = RoninSpacing.small,
                        ),
                        verticalArrangement = Arrangement.spacedBy(RoninSpacing.micro),
                    ) {
                        Text(
                            section.localizedTitle(),
                            color = if (isSelected) RoninColors.accentCoral else RoninColors.textPrimary,
                            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            section.localizedDescription(),
                            color = RoninColors.textMuted,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DesktopSettingsOverview(
    graph: DesktopPlatformGraph,
    languageTag: String,
    mangaCount: Int,
    updateIntervalHours: Long,
    queuedDownloadCount: Int,
    activeDownloadCount: Int,
    failedDownloadCount: Int,
    connectedTrackers: List<String>,
    installedExtensionCount: Int,
    protocolRegistered: Boolean,
    onSelectSection: (DesktopSettingsSection) -> Unit,
    onOpenDownloads: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val narrow = maxWidth < 920.dp
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
        ) {
            if (narrow) {
                SettingsOverviewPanel(
                    title = roninText("Application & library", "Aplicación y biblioteca"),
                    detail = applicationLibrarySummary(languageTag, mangaCount, updateIntervalHours),
                    actionLabel = roninText("Library preferences", "Preferencias de biblioteca"),
                    onAction = { onSelectSection(DesktopSettingsSection.LIBRARY) },
                )
                DesktopReaderDefaultsSettings(graph)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
                    verticalAlignment = Alignment.Top,
                ) {
                    SettingsOverviewPanel(
                        title = roninText("Application & library", "Aplicación y biblioteca"),
                        detail = applicationLibrarySummary(languageTag, mangaCount, updateIntervalHours),
                        actionLabel = roninText("Library preferences", "Preferencias de biblioteca"),
                        onAction = { onSelectSection(DesktopSettingsSection.LIBRARY) },
                        modifier = Modifier.weight(1f),
                    )
                    Box(Modifier.weight(1f)) {
                        DesktopReaderDefaultsSettings(graph)
                    }
                }
            }
            if (narrow) {
                SettingsOverviewPanel(
                    title = roninText("Downloads", "Descargas"),
                    detail = downloadSummary(queuedDownloadCount, activeDownloadCount, failedDownloadCount),
                    actionLabel = roninText("Open download queue", "Abrir cola de descargas"),
                    onAction = onOpenDownloads,
                )
                SettingsOverviewPanel(
                    title = roninText("Tracking", "Seguimiento"),
                    detail = trackerSummary(connectedTrackers),
                    actionLabel = roninText("Manage trackers", "Administrar seguimiento"),
                    onAction = { onSelectSection(DesktopSettingsSection.TRACKING) },
                )
                SettingsOverviewPanel(
                    title = roninText("Storage & backup", "Almacenamiento y copias"),
                    detail =
                    "Local library: ${graph.appDirectories.localLibrary}\n" +
                        "Database: ${graph.appDirectories.database}",
                    actionLabel = roninText("Storage preferences", "Copias de seguridad"),
                    onAction = { onSelectSection(DesktopSettingsSection.STORAGE) },
                )
                SettingsOverviewPanel(
                    title = roninText("Windows links & extensions", "Enlaces y extensiones"),
                    detail = "Browser links are ${if (protocolRegistered) "registered" else "not registered"}. " +
                        "$installedExtensionCount extensions installed. Local trust records a device decision.",
                    actionLabel = roninText("Advanced preferences", "Preferencias avanzadas"),
                    onAction = { onSelectSection(DesktopSettingsSection.ADVANCED) },
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
                    verticalAlignment = Alignment.Top,
                ) {
                    SettingsOverviewPanel(
                        title = roninText("Downloads", "Descargas"),
                        detail = downloadSummary(queuedDownloadCount, activeDownloadCount, failedDownloadCount),
                        actionLabel = roninText("Open download queue", "Abrir cola de descargas"),
                        onAction = onOpenDownloads,
                        modifier = Modifier.weight(1f),
                    )
                    SettingsOverviewPanel(
                        title = roninText("Tracking", "Seguimiento"),
                        detail = trackerSummary(connectedTrackers),
                        actionLabel = roninText("Manage trackers", "Administrar seguimiento"),
                        onAction = { onSelectSection(DesktopSettingsSection.TRACKING) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
                    verticalAlignment = Alignment.Top,
                ) {
                    SettingsOverviewPanel(
                        title = roninText("Storage & backup", "Almacenamiento y copias"),
                        detail =
                        "Local library: ${graph.appDirectories.localLibrary}\n" +
                            "Database: ${graph.appDirectories.database}",
                        actionLabel = roninText("Storage preferences", "Copias de seguridad"),
                        onAction = { onSelectSection(DesktopSettingsSection.STORAGE) },
                        modifier = Modifier.weight(1f),
                    )
                    SettingsOverviewPanel(
                        title = roninText("Windows links & extensions", "Enlaces y extensiones"),
                        detail = "Browser links are ${if (protocolRegistered) "registered" else "not registered"}. " +
                            "$installedExtensionCount extensions installed. Local trust records a device decision.",
                        actionLabel = roninText("Advanced preferences", "Preferencias avanzadas"),
                        onAction = { onSelectSection(DesktopSettingsSection.ADVANCED) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun applicationLibrarySummary(languageTag: String, mangaCount: Int, updateIntervalHours: Long): String {
    val schedule = if (updateIntervalHours == 0L) {
        roninText("Scheduled updates are off", "Actualizaciones programadas desactivadas")
    } else {
        roninText(
            "Updates run every $updateIntervalHours hours while Ronin is open",
            "Actualización cada $updateIntervalHours horas mientras Ronin esté abierto",
        )
    }
    return roninText(
        "Language: $languageTag\n$mangaCount manga on this device\n$schedule",
        "Idioma: $languageTag\n$mangaCount manga en este equipo\n$schedule",
    )
}

@Composable
private fun downloadSummary(queuedDownloadCount: Int, activeDownloadCount: Int, failedDownloadCount: Int): String =
    roninText(
        "$queuedDownloadCount chapters in the queue\n$activeDownloadCount active · $failedDownloadCount failed",
        "$queuedDownloadCount capítulos en la cola\n$activeDownloadCount activos · $failedDownloadCount con errores",
    )

@Composable
private fun trackerSummary(connectedTrackers: List<String>): String =
    if (connectedTrackers.isEmpty()) {
        roninText("No tracker accounts are currently signed in.", "No hay cuentas de seguimiento conectadas.")
    } else {
        roninText(
            "Signed in to ${connectedTrackers.joinToString(", ")}.",
            "Conectado a ${connectedTrackers.joinToString(", ")}.",
        )
    }

@Composable
private fun SettingsOverviewPanel(
    title: String,
    detail: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RoninPanel(modifier.fillMaxWidth().heightIn(min = RoninLayout.settingsPanelMinHeight)) {
        Column(
            Modifier.fillMaxWidth().padding(RoninSpacing.large),
            verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
        ) {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            Text(
                detail,
                color = RoninColors.textSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
            RoninSecondaryButton(actionLabel, onAction)
        }
    }
}

@Composable
internal fun DesktopReaderDefaultsSettings(graph: DesktopPlatformGraph) {
    var mode by remember {
        mutableStateOf(
            runCatching {
                ReadingMode.valueOf(
                    graph.keyValueStore.getString("desktop.reader.mode", ReadingMode.SINGLE_LTR.name)
                        ?: ReadingMode.SINGLE_LTR.name,
                )
            }.getOrDefault(ReadingMode.SINGLE_LTR),
        )
    }
    var fit by remember {
        mutableStateOf(
            runCatching {
                FitMode.valueOf(
                    graph.keyValueStore.getString("desktop.reader.fit", FitMode.HEIGHT.name)
                        ?: FitMode.HEIGHT.name,
                )
            }.getOrDefault(FitMode.HEIGHT),
        )
    }
    var modeExpanded by remember { mutableStateOf(false) }
    var fitExpanded by remember { mutableStateOf(false) }

    RoninPanel(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(RoninSpacing.large),
            verticalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
        ) {
            Text(
                roninText("Default reading mode", "Modo de lectura predeterminado"),
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            )
            Text(
                roninText(
                    "New chapters open with this mode. Change it at any time from the reader controls.",
                    "Los capítulos se abren con este modo. Puedes cambiarlo desde los controles del lector.",
                ),
                color = RoninColors.textMuted,
            )
            androidx.compose.foundation.layout.Box {
                RoninTextButton(label = localizedReadingModeLabel(mode), onClick = { modeExpanded = true })
                DropdownMenu(expanded = modeExpanded, onDismissRequest = { modeExpanded = false }) {
                    ReadingMode.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(localizedReadingModeLabel(option)) },
                            onClick = {
                                mode = option
                                modeExpanded = false
                                graph.keyValueStore.putString("desktop.reader.mode", option.name)
                            },
                        )
                    }
                }
            }
            androidx.compose.material3.HorizontalDivider(color = RoninColors.borderSubtle)
            Text(
                roninText("Default page fit", "Ajuste de página predeterminado"),
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            )
            Text(
                roninText(
                    "Width scrolls tall pages, height keeps the full page visible, and original avoids upscaling.",
                    "Ancho permite desplazar páginas largas, alto muestra la página completa y original conserva su tamaño.",
                ),
                color = RoninColors.textMuted,
            )
            androidx.compose.foundation.layout.Box {
                RoninTextButton(label = localizedFitModeLabel(fit), onClick = { fitExpanded = true })
                DropdownMenu(expanded = fitExpanded, onDismissRequest = { fitExpanded = false }) {
                    FitMode.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(localizedFitModeLabel(option)) },
                            onClick = {
                                fit = option
                                fitExpanded = false
                                graph.keyValueStore.putString("desktop.reader.fit", option.name)
                            },
                        )
                    }
                }
            }
        }
    }
}
