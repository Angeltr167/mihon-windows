package mihon.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import dev.icerock.moko.resources.StringResource
import dev.icerock.moko.resources.compose.stringResource
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun DesktopNavigation(
    selected: Screen,
    compact: Boolean,
    onNavigate: (Screen) -> Unit,
) {
    Box(
        Modifier.width(if (compact) MihonSizes.navigationCompact else MihonSizes.navigationExpanded)
            .fillMaxHeight()
            .background(MihonPalette.panel),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = if (compact) MihonSpacing.xs else MihonSpacing.md,
                    vertical = MihonSpacing.md,
                ),
            verticalArrangement = Arrangement.spacedBy(MihonSpacing.xs),
        ) {
            RoninNavigationBrand(compact)

            Box(
                Modifier.fillMaxWidth()
                    .padding(
                        start = if (compact) MihonSpacing.xs else MihonSpacing.sm,
                        end = if (compact) MihonSpacing.xs else MihonSpacing.sm,
                        top = MihonSpacing.sm,
                        bottom = MihonSpacing.md,
                    )
                    .height(1.dp)
                    .background(MihonPalette.outlineSoft),
            )

            Screen.entries.forEachIndexed { index, item ->
                val isSelected = selected == item
                val title = stringResource(item.title)
                val interactionSource = remember { MutableInteractionSource() }
                val hovered by interactionSource.collectIsHoveredAsState()
                var tooltipVisible by remember(item) { mutableStateOf(false) }

                Box(Modifier.fillMaxWidth()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth()
                            .semantics {
                                this.selected = isSelected
                                contentDescription = title
                            }
                            .onPointerEvent(PointerEventType.Enter) { tooltipVisible = true }
                            .onPointerEvent(PointerEventType.Exit) { tooltipVisible = false }
                            .onFocusChanged { tooltipVisible = it.isFocused }
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                role = Role.Tab,
                            ) { onNavigate(item) },
                        shape = RoundedCornerShape(MihonRadius.control),
                        color = when {
                            isSelected -> MihonPalette.selected
                            hovered -> MihonPalette.hover
                            else -> androidx.compose.ui.graphics.Color.Transparent
                        },
                        border = if (isSelected) {
                            BorderStroke(1.dp, MihonPalette.sage.copy(alpha = 0.34f))
                        } else {
                            null
                        },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(
                                horizontal = if (compact) MihonSpacing.sm else MihonSpacing.md,
                                vertical = 10.dp,
                            ),
                            horizontalArrangement = if (compact) {
                                Arrangement.Center
                            } else {
                                Arrangement.spacedBy(MihonSpacing.sm)
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DesktopNavigationIcon(
                                index,
                                if (isSelected) MihonPalette.sage else MihonPalette.muted,
                            )
                            if (!compact) {
                                Text(
                                    title,
                                    color = if (isSelected) MihonPalette.sage else MihonPalette.ivory,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }

                    if (isSelected) {
                        Box(
                            Modifier.align(Alignment.CenterStart)
                                .width(2.dp)
                                .height(28.dp)
                                .background(
                                    MihonPalette.sage,
                                    RoundedCornerShape(MihonRadius.control),
                                ),
                        )
                    }
                }

                if (compact && tooltipVisible) {
                    Popup(alignment = Alignment.CenterEnd, offset = IntOffset(12, 0)) {
                        Surface(
                            shape = RoundedCornerShape(MihonRadius.control),
                            color = MihonPalette.raised,
                            border = BorderStroke(1.dp, MihonPalette.outline),
                        ) {
                            Text(
                                title,
                                Modifier.padding(
                                    horizontal = MihonSpacing.md,
                                    vertical = MihonSpacing.sm,
                                ),
                                color = MihonPalette.ivory,
                            )
                        }
                    }
                }
            }
        }

        Box(
            Modifier.align(Alignment.CenterEnd)
                .width(1.dp)
                .fillMaxHeight()
                .background(MihonPalette.outlineSoft),
        )
    }
}

@Composable
private fun RoninNavigationBrand(compact: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = if (compact) MihonSpacing.xs else MihonSpacing.sm,
            vertical = MihonSpacing.sm,
        ),
        horizontalArrangement = if (compact) Arrangement.Center else Arrangement.spacedBy(MihonSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = RoundedCornerShape(MihonRadius.control),
            color = MihonPalette.selected,
            border = BorderStroke(1.dp, MihonPalette.outline),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "R",
                    color = MihonPalette.sage,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (!compact) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "RONIN",
                    color = MihonPalette.ivory,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.6.sp,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MihonSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.width(18.dp).height(1.dp).background(MihonPalette.gold),
                    )
                    Text(
                        "MANGA READER",
                        color = MihonPalette.mutedQuiet,
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 0.8.sp,
                    )
                }
            }
        }
    }
}
