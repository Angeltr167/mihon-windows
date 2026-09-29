package mihon.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import dev.icerock.moko.resources.compose.stringResource
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
internal fun DesktopNavigation(
    selected: Screen,
    compact: Boolean,
    onNavigate: (Screen) -> Unit,
) {
    Box(
        Modifier.width(if (compact) RoninLayout.sidebarCompact else RoninLayout.sidebarExpanded)
            .fillMaxHeight()
            .background(
                Brush.verticalGradient(
                    listOf(
                        RoninColors.sidebarSurface.copy(alpha = 0.88f),
                        RoninColors.sidebarSurface.copy(alpha = 0.80f),
                        RoninColors.sidebarSurface.copy(alpha = 0.48f),
                    ),
                ),
            ),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = if (compact) RoninSpacing.xSmall else RoninSpacing.medium,
                    vertical = RoninSpacing.large,
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
                        bottom = RoninSpacing.medium,
                    )
                    .height(RoninBorders.hairline)
                    .background(RoninColors.borderSubtle),
            )

            Screen.entries.forEachIndexed { index, item ->
                val title = stringResource(item.title)
                RoninSidebarItem(
                    title = title,
                    selected = selected == item,
                    compact = compact,
                    onClick = { onNavigate(item) },
                ) { color ->
                    DesktopNavigationIcon(index, color)
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.width(18.dp).height(RoninBorders.hairline).background(RoninColors.accentCoral),
                    )
                    Text(
                        "MANGA READER",
                        color = RoninColors.textMuted,
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 0.8.sp,
                    )
                }
            }
        }
    }
}
