package mihon.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
internal fun DesktopSettingsNavigation(
    selected: DesktopSettingsSection,
    onSelect: (DesktopSettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(RoninSpacing.small),
        verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
    ) {
        Text(
            "SETTINGS",
            modifier = Modifier.padding(horizontal = RoninSpacing.small, vertical = RoninSpacing.medium),
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
                color = if (isSelected) RoninColors.accentCoral.copy(alpha = 0.13f) else Color.Transparent,
                border = if (isSelected) BorderStroke(RoninBorders.hairline, RoninColors.accentCoral.copy(alpha = 0.38f)) else null,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = RoninSpacing.medium, vertical = RoninSpacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        section.title,
                        color = if (isSelected) RoninColors.accentCoral else RoninColors.textPrimary,
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    )
                }
            }
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
            Text("Default reading mode", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Text(
                "New chapters open with this mode. Change it at any time from the reader controls.",
                color = RoninColors.textMuted,
            )
            androidx.compose.foundation.layout.Box {
                RoninTextButton(label = readingModeLabel(mode), onClick = { modeExpanded = true })
                DropdownMenu(expanded = modeExpanded, onDismissRequest = { modeExpanded = false }) {
                    ReadingMode.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(readingModeLabel(option)) },
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
            Text("Default page fit", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Text(
                "Width scrolls tall pages, height keeps the full page visible, and original avoids upscaling.",
                color = RoninColors.textMuted,
            )
            androidx.compose.foundation.layout.Box {
                RoninTextButton(label = fitModeLabel(fit), onClick = { fitExpanded = true })
                DropdownMenu(expanded = fitExpanded, onDismissRequest = { fitExpanded = false }) {
                    FitMode.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(fitModeLabel(option)) },
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
