package mihon.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import mihon.desktop.design.RoninBorders
import mihon.desktop.design.RoninColors
import mihon.desktop.design.RoninLayout
import mihon.desktop.design.RoninMangaMetrics
import mihon.desktop.design.RoninRadius
import mihon.desktop.design.RoninSpacing

@Composable
internal fun RoninPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(RoninRadius.panel),
        color = RoninColors.elevatedSurface,
        border = BorderStroke(RoninBorders.hairline, RoninColors.border),
        content = content,
    )
}

@Composable
internal fun RoninSectionHeader(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth().padding(bottom = RoninSpacing.medium)) {
        val narrow = maxWidth < 720.dp
        val label: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(RoninSpacing.micro)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = RoninColors.textSecondary,
                    )
                }
            }
        }

        if (narrow) {
            Column(verticalArrangement = Arrangement.spacedBy(RoninSpacing.medium)) {
                label()
                trailing?.invoke()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                label()
                trailing?.invoke()
            }
        }
    }
}

@Composable
internal fun RoninButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(RoninMangaMetrics.controlHeight),
        shape = RoundedCornerShape(RoninRadius.control),
        colors = ButtonDefaults.buttonColors(
            containerColor = RoninColors.accentCoral,
            contentColor = RoninColors.appBackground,
            disabledContainerColor = RoninColors.secondarySurface,
            disabledContentColor = RoninColors.textDisabled,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun RoninSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(RoninMangaMetrics.controlHeight),
        shape = RoundedCornerShape(RoninRadius.control),
        border = BorderStroke(RoninBorders.hairline, RoninColors.border),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun RoninTextButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(RoninMangaMetrics.controlHeight),
        shape = RoundedCornerShape(RoninRadius.control),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun RoninInlineAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(32.dp),
        shape = RoundedCornerShape(RoninRadius.control),
        contentPadding = PaddingValues(horizontal = RoninSpacing.small),
        colors = ButtonDefaults.textButtonColors(
            contentColor = RoninColors.textSecondary,
            disabledContentColor = RoninColors.textDisabled,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun RoninIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(RoninMangaMetrics.controlHeight),
        content = content,
    )
}

@Composable
internal fun RoninSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        placeholder = {
            Text(
                placeholder,
                color = RoninColors.textMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        leadingIcon = leadingIcon,
        shape = RoundedCornerShape(RoninRadius.control),
    )
}

@Composable
internal fun RoninChip(
    label: String,
    selected: Boolean = false,
    accent: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    role: Role = Role.Button,
) {
    val highlighted = selected || accent
    val interactiveModifier = if (onClick != null) {
        modifier.clickable(role = role, onClick = onClick)
    } else {
        modifier
    }

    Surface(
        modifier = interactiveModifier,
        shape = RoundedCornerShape(RoninRadius.pill),
        color = when {
            selected -> RoninColors.accentCoral.copy(alpha = 0.14f)
            accent -> RoninColors.accentCoral.copy(alpha = 0.12f)
            else -> RoninColors.secondarySurface
        },
        border = BorderStroke(
            RoninBorders.hairline,
            if (highlighted) RoninColors.accentCoral.copy(alpha = 0.52f) else RoninColors.borderSubtle,
        ),
    ) {
        Text(
            label,
            modifier = Modifier.padding(
                horizontal = if (selected) RoninSpacing.medium else RoninSpacing.small,
                vertical = if (selected) RoninSpacing.small else RoninSpacing.xSmall,
            ),
            color = if (highlighted) RoninColors.accentCoral else RoninColors.textSecondary,
            style = if (selected) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
internal fun RoninFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RoninChip(
        label = label,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        role = Role.Tab,
    )
}

@Composable
internal fun RoninBadge(
    label: String,
    accent: Boolean = false,
    modifier: Modifier = Modifier,
) {
    RoninChip(
        label = label,
        accent = accent,
        modifier = modifier,
    )
}

@Composable
internal fun RoninTabStrip(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
        verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
    ) {
        labels.forEachIndexed { index, label ->
            RoninFilterPill(
                label = label,
                selected = selectedIndex == index,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
internal fun RoninCover(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .widthIn(min = RoninMangaMetrics.coverCompactWidth)
            .clip(RoundedCornerShape(RoninRadius.cover)),
        shape = RoundedCornerShape(RoninRadius.cover),
        color = RoninColors.secondarySurface,
        border = BorderStroke(RoninBorders.hairline, RoninColors.borderSubtle),
        content = content,
    )
}

@Composable
internal fun RoninMangaCard(
    title: String,
    metadata: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    cover: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val clickableModifier = if (onClick != null) {
        modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        modifier
    }

    RoninPanel(clickableModifier) {
        Row(
            Modifier.fillMaxWidth().padding(RoninSpacing.medium),
            horizontalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            cover?.invoke()
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                metadata?.let {
                    Text(
                        it,
                        color = RoninColors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

@Composable
internal fun RoninChapterRow(
    title: String,
    subtitle: String? = null,
    read: Boolean? = null,
    bookmarked: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val rowModifier = if (onClick != null) {
        modifier
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
            ) { onClick() }
    } else {
        modifier
    }

    Surface(
        modifier = rowModifier.fillMaxWidth(),
        shape = RoundedCornerShape(RoninRadius.control),
        color = if (hovered) RoninColors.hoverSurface else Color.Transparent,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 760.dp && trailing != null
            val copy: @Composable (Modifier) -> Unit = { copyModifier ->
                Column(
                    copyModifier,
                    verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                ) {
                    Text(
                        title,
                        color = if (read == true) RoninColors.textMuted else RoninColors.textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                        verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall),
                    ) {
                        subtitle?.let {
                            Text(
                                it,
                                color = RoninColors.textMuted,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (read == true) {
                            RoninBadge(label = "Read")
                        }
                        if (bookmarked) {
                            RoninBadge(label = "Bookmarked", accent = true)
                        }
                    }
                }
            }

            if (compact) {
                Column(
                    Modifier.fillMaxWidth()
                        .heightIn(min = RoninLayout.chapterRowMinHeight)
                        .padding(horizontal = RoninSpacing.medium, vertical = RoninSpacing.small),
                    verticalArrangement = Arrangement.spacedBy(RoninSpacing.small),
                ) {
                    copy(Modifier.fillMaxWidth())
                    trailing?.invoke()
                }
            } else {
                Row(
                    Modifier.fillMaxWidth()
                        .heightIn(min = RoninLayout.chapterRowMinHeight)
                        .padding(horizontal = RoninSpacing.medium, vertical = RoninSpacing.small),
                    horizontalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    copy(Modifier.weight(1f))
                    trailing?.invoke()
                }
            }
        }
    }
}

@Composable
internal fun RoninProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val normalized = progress.coerceIn(0f, 1f)
    Box(
        modifier.height(4.dp)
            .clip(RoundedCornerShape(RoninRadius.pill))
            .background(RoninColors.borderSubtle),
    ) {
        Box(
            Modifier.fillMaxWidth(normalized)
                .height(4.dp)
                .background(RoninColors.accentSage),
        )
    }
}

@Composable
internal fun RoninStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(RoninSpacing.micro),
    ) {
        Text(
            value,
            color = RoninColors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            label,
            color = RoninColors.textMuted,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
internal fun RoninEmptyState(
    title: String,
    detail: String? = null,
    modifier: Modifier = Modifier,
) {
    RoninStatePanel(
        title = title,
        detail = detail,
        modifier = modifier,
        accent = RoninColors.textMuted,
    )
}

@Composable
internal fun RoninLoadingState(
    title: String,
    detail: String? = null,
    modifier: Modifier = Modifier,
) {
    RoninPanel(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(RoninSpacing.large),
            horizontalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = RoninColors.accentSage,
            )
            Column(verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                detail?.let {
                    Text(
                        it,
                        color = RoninColors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
internal fun RoninErrorState(
    title: String,
    detail: String? = null,
    modifier: Modifier = Modifier,
) {
    RoninStatePanel(
        title = title,
        detail = detail,
        modifier = modifier,
        accent = RoninColors.error,
    )
}

@Composable
private fun RoninStatePanel(
    title: String,
    detail: String?,
    modifier: Modifier,
    accent: Color,
) {
    RoninPanel(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(RoninSpacing.large),
            horizontalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
        ) {
            Box(
                Modifier.width(3.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(RoninRadius.pill))
                    .background(accent),
            )
            Column(verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                detail?.let {
                    Text(
                        it,
                        color = RoninColors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun RoninSidebarItem(
    title: String,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (Color) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    var tooltipVisible by remember(title) { mutableStateOf(false) }

    Box(modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth()
                .semantics {
                    this.selected = selected
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
                ) { onClick() },
            shape = RoundedCornerShape(RoninRadius.control),
            color = when {
                selected -> RoninColors.selectedSurface
                hovered -> RoninColors.hoverSurface
                else -> Color.Transparent
            },
            border = if (selected) {
                BorderStroke(RoninBorders.hairline, RoninColors.accentSage.copy(alpha = 0.34f))
            } else {
                null
            },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = if (compact) RoninSpacing.small else RoninSpacing.medium,
                    vertical = 10.dp,
                ),
                horizontalArrangement = if (compact) {
                    Arrangement.Center
                } else {
                    Arrangement.spacedBy(RoninSpacing.small)
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icon(if (selected) RoninColors.accentSage else RoninColors.textSecondary)
                if (!compact) {
                    Text(
                        title,
                        color = if (selected) RoninColors.accentSage else RoninColors.textPrimary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        if (selected) {
            Box(
                Modifier.align(Alignment.CenterStart)
                    .width(2.dp)
                    .height(28.dp)
                    .background(
                        RoninColors.accentSage,
                        RoundedCornerShape(RoninRadius.control),
                    ),
            )
        }
    }

    if (compact && tooltipVisible) {
        Popup(alignment = Alignment.CenterEnd, offset = IntOffset(12, 0)) {
            Surface(
                shape = RoundedCornerShape(RoninRadius.control),
                color = RoninColors.secondarySurface,
                border = BorderStroke(RoninBorders.hairline, RoninColors.border),
            ) {
                Text(
                    title,
                    Modifier.padding(
                        horizontal = RoninSpacing.medium,
                        vertical = RoninSpacing.small,
                    ),
                    color = RoninColors.textPrimary,
                )
            }
        }
    }
}
