package mihon.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.icerock.moko.resources.StringResource
import mihon.desktop.design.RoninBorders
import mihon.desktop.design.RoninColors
import mihon.desktop.design.RoninLayout
import mihon.desktop.design.RoninRadius
import mihon.desktop.design.RoninSpacing
import mihon.desktop.design.RoninTypeScale
import tachiyomi.i18n.MR

internal enum class Screen(val title: StringResource) {
    LIBRARY(MR.strings.label_library),
    UPDATES(MR.strings.label_recent_updates),
    HISTORY(MR.strings.label_recent_manga),
    SOURCES(MR.strings.label_sources),
    SEARCH(MR.strings.action_search),
    EXTENSIONS(MR.strings.label_extensions),
    CATEGORIES(MR.strings.categories),
    SETTINGS(MR.strings.label_settings),
    DOWNLOADS(MR.strings.label_download_queue),
}

@Composable
internal fun Screen.localizedTitle(): String = when (this) {
    Screen.LIBRARY -> roninText("Library", "Biblioteca")
    Screen.UPDATES -> roninText("Updates", "Actualizaciones")
    Screen.HISTORY -> roninText("History", "Historial")
    Screen.SOURCES -> roninText("Sources", "Fuentes")
    Screen.SEARCH -> roninText("Search", "Buscar")
    Screen.EXTENSIONS -> roninText("Extensions", "Extensiones")
    Screen.CATEGORIES -> roninText("Categories", "Categorías")
    Screen.SETTINGS -> roninText("Settings", "Ajustes")
    Screen.DOWNLOADS -> roninText("Downloads", "Descargas")
}

@Composable
internal fun DesktopNavigation(
    selected: Screen,
    compact: Boolean,
    onNavigate: (Screen) -> Unit,
) {
    BoxWithConstraints(
        Modifier.width(if (compact) RoninLayout.sidebarCompact else RoninLayout.sidebarExpanded)
            .fillMaxHeight()
            .background(
                Brush.verticalGradient(
                    listOf(
                        RoninColors.sidebarSurface,
                        RoninColors.sidebarSurface,
                        RoninColors.appBackground,
                    ),
                ),
            ),
    ) {
        val shortWindow = maxHeight < 680.dp
        Column(
            Modifier.fillMaxWidth()
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = if (compact) RoninSpacing.xSmall else RoninSpacing.medium,
                    vertical = if (shortWindow) RoninSpacing.small else RoninSpacing.large,
                ),
            verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
        ) {
            RoninNavigationBrand(compact)

            Box(
                Modifier.fillMaxWidth()
                    .padding(
                        start = if (compact) RoninSpacing.xSmall else RoninSpacing.small,
                        end = if (compact) RoninSpacing.xSmall else RoninSpacing.small,
                        top = RoninSpacing.small,
                        bottom = if (shortWindow) RoninSpacing.xSmall else RoninSpacing.medium,
                    )
                    .height(RoninBorders.hairline)
                    .background(RoninColors.borderSubtle),
            )

            val groups = listOf(
                listOf(Screen.LIBRARY, Screen.UPDATES, Screen.HISTORY),
                listOf(Screen.SOURCES, Screen.SEARCH, Screen.EXTENSIONS),
                listOf(Screen.CATEGORIES, Screen.DOWNLOADS, Screen.SETTINGS),
            )
            groups.forEachIndexed { groupIndex, group ->
                if (groupIndex > 0) {
                    Box(
                        Modifier.fillMaxWidth().padding(
                            vertical = if (shortWindow) RoninSpacing.xSmall else RoninSpacing.medium,
                        )
                            .height(RoninBorders.hairline).background(RoninColors.borderSubtle),
                    )
                }
                group.forEach { item ->
                    RoninSidebarItem(
                        title = item.localizedTitle(),
                        selected = selected == item,
                        compact = compact,
                        dense = shortWindow,
                        onClick = { onNavigate(item) },
                    ) { color -> DesktopNavigationIcon(item.ordinal, color) }
                }
            }
        }

        Box(
            Modifier.align(Alignment.CenterEnd)
                .width(RoninBorders.hairline)
                .fillMaxHeight()
                .background(RoninColors.borderSubtle),
        )
    }
}

@Composable
private fun RoninNavigationBrand(compact: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = if (compact) RoninSpacing.xSmall else RoninSpacing.small,
            vertical = RoninSpacing.small,
        ),
        horizontalArrangement = if (compact) Arrangement.Center else Arrangement.spacedBy(RoninSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoninBrandMark(Modifier.size(38.dp))

        if (!compact) {
            Column(verticalArrangement = Arrangement.spacedBy(RoninSpacing.micro)) {
                Text(
                    "RONIN",
                    color = RoninColors.textPrimary,
                    style = RoninTypeScale.branding,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.6.sp,
                )
            }
        }
    }
}
