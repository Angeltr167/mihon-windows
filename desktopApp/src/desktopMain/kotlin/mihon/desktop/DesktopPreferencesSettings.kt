package mihon.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import mihon.desktop.design.RoninColors
import mihon.desktop.design.RoninSpacing
import mihon.platform.desktop.DesktopPlatformGraph

@Composable
internal fun DesktopPreferencesSettings(
    graph: DesktopPlatformGraph,
    revision: Int,
    onPreferencesChanged: () -> Unit,
) {
    val preferences = remember(graph) { DesktopPreferences(graph.keyValueStore) }
    var languageExpanded by remember { mutableStateOf(false) }
    var startExpanded by remember { mutableStateOf(false) }
    val language = remember(revision) { preferences.language }
    val startScreen = remember(revision) {
        DesktopPreferences.START_SCREENS.firstOrNull {
            it.name == graph.keyValueStore.getString(DesktopPreferences.START_SCREEN)
        } ?: Screen.LIBRARY
    }
    RoninPanel(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(RoninSpacing.large),
            verticalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
        ) {
            Text(roninText("Preferences", "Preferencias"), style = MaterialTheme.typography.titleLarge)
            Text(
                roninText(
                    "Changes are saved automatically. Window and start page settings apply on the next launch.",
                    "Los cambios se guardan automáticamente. Las opciones de ventana e inicio se aplican al volver a abrir.",
                ),
                color = RoninColors.textMuted,
            )
            Column {
                Text(roninText("Language", "Idioma"), style = MaterialTheme.typography.titleMedium)
                RoninTextButton(languageLabel(language), { languageExpanded = true })
                DropdownMenu(languageExpanded, { languageExpanded = false }) {
                    DesktopPreferences.LANGUAGES.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(languageLabel(option)) },
                            onClick = {
                                preferences.language = option
                                languageExpanded = false
                                onPreferencesChanged()
                            },
                        )
                    }
                }
            }
            Column {
                Text(roninText("Start page", "Página de inicio"), style = MaterialTheme.typography.titleMedium)
                RoninTextButton(startScreen.localizedTitle(), { startExpanded = true })
                DropdownMenu(startExpanded, { startExpanded = false }) {
                    DesktopPreferences.START_SCREENS.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.localizedTitle()) },
                            onClick = {
                                graph.keyValueStore.putString(DesktopPreferences.START_SCREEN, option.name)
                                startExpanded = false
                                onPreferencesChanged()
                            },
                        )
                    }
                }
            }
            PreferenceSwitch(
                graph,
                revision,
                DesktopPreferences.MAXIMIZED,
                roninText("Start maximized", "Iniciar maximizada"),
                onPreferencesChanged,
            )
            PreferenceSwitch(
                graph,
                revision,
                DesktopPreferences.REMEMBER_SIZE,
                roninText("Remember window size", "Recordar tamaño de ventana"),
                onPreferencesChanged,
            )
            PreferenceSwitch(
                graph,
                revision,
                DesktopPreferences.NOTIFICATIONS,
                roninText("Download completion and error notifications", "Avisos de descargas completadas y errores"),
                onPreferencesChanged,
            )
        }
    }
}

@Composable
private fun languageLabel(language: String): String = when (language) {
    "es" -> "Español"
    "en" -> "English"
    else -> roninText("System language", "Idioma del sistema")
}

@Composable
private fun PreferenceSwitch(
    graph: DesktopPlatformGraph,
    revision: Int,
    key: String,
    label: String,
    onChanged: () -> Unit,
) {
    val checked = remember(revision) { graph.keyValueStore.getBoolean(key, true) }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked, {
            graph.keyValueStore.putBoolean(key, it)
            onChanged()
        })
    }
}
