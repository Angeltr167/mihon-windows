package mihon.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
        border = BorderStroke(RoninBorders.hairline, RoninColors.borderSubtle),
        content = content,
    )
}

@Composable
internal fun RoninSectionHeader(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    pageHeading: Boolean = false,
    compactBreakpoint: androidx.compose.ui.unit.Dp = 860.dp,
) {
    BoxWithConstraints(modifier.fillMaxWidth().padding(bottom = RoninSpacing.medium)) {
        val narrow = maxWidth < compactBreakpoint
        val compactHeading = maxWidth < 760.dp
        val label: @Composable (Modifier) -> Unit = { labelModifier ->
            Column(labelModifier, verticalArrangement = Arrangement.spacedBy(RoninSpacing.xSmall)) {
                Text(
                    title,
                    style = if (pageHeading) {
                        if (compactHeading) {
                            MaterialTheme.typography.headlineLarge
                        } else {
                            MaterialTheme.typography.displayMedium
                        }
                    } else {
                        MaterialTheme.typography.headlineSmall
                    },
                )
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
                label(Modifier.fillMaxWidth())
                trailing?.invoke()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                label(Modifier.weight(1f).padding(end = RoninSpacing.medium))
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
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        colors = ButtonDefaults.outlinedButtonColors(contentColor = RoninColors.textPrimary),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    onSearch: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.onPreviewKeyEvent {
            if (onSearch != null && it.key == Key.Enter && it.type == KeyEventType.KeyDown) {
                onSearch()
                true
            } else {
                false
            }
        },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
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
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = RoninColors.elevatedSurface,
            unfocusedContainerColor = RoninColors.elevatedSurface,
            unfocusedBorderColor = RoninColors.borderSubtle,
            focusedBorderColor = RoninColors.accentCoral,
        ),
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
        modifier.clip(RoundedCornerShape(RoninRadius.pill))
            .semantics { this.selected = selected }
            .clickable(role = role, onClick = onClick)
    } else {
        modifier
    }

    Surface(
        modifier = interactiveModifier,
        shape = RoundedCornerShape(RoninRadius.pill),
        color = when {
            selected -> RoninColors.accentCoral
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
                horizontal = if (onClick != null) RoninSpacing.medium else RoninSpacing.small,
                vertical = if (onClick != null) RoninSpacing.small else RoninSpacing.xSmall,
            ),
            color = when {
                selected -> RoninColors.appBackground
                accent -> RoninColors.accentCoral
                else -> RoninColors.textSecondary
            },
            style = if (onClick != null) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
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
internal fun RoninChapterTableHeader() {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 760.dp) {
            Row(
                Modifier.fillMaxWidth().background(RoninColors.elevatedSurface)
                    .padding(horizontal = RoninSpacing.medium, vertical = RoninSpacing.small),
                horizontalArrangement = Arrangement.spacedBy(RoninSpacing.medium),
            ) {
                Text("#", Modifier.width(36.dp), color = RoninColors.textMuted)
                Text(roninText("Chapter", "Capítulo"), Modifier.weight(1f), color = RoninColors.textSecondary)
                Text(roninText("Date", "Fecha"), Modifier.width(132.dp), color = RoninColors.textSecondary)
                Text(roninText("State", "Estado"), Modifier.width(132.dp), color = RoninColors.textSecondary)
                Box(Modifier.width(140.dp))
            }
        }
    }
}

@Composable
internal fun RoninChapterRow(
    title: String,
    number: String = "",
    date: String? = null,
    inProgress: Boolean = false,
    subtitle: String? = null,
    read: Boolean? = null,
    bookmarked: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val rowColor by animateColorAsState(
        if (hovered) {
            RoninColors.hoverSurface
        } else if (inProgress) {
            RoninColors.elevatedSurface
        } else {
            Color.Transparent
        },
        animationSpec = tween(120),
    )
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
        color = rowColor,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 760.dp
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
                        if (compact && date != null) Text(date, color = RoninColors.textMuted)
                        if (compact && read != null) {
                            RoninBadge(
                                label = if (read) {
                                    roninText("Read", "Leído")
                                } else if (inProgress) {
                                    roninText("In progress", "En progreso")
                                } else {
                                    roninText("Unread", "No leído")
                                },
                                accent = inProgress,
                            )
                        }
                        if (bookmarked) {
                            RoninBadge(label = roninText("Bookmarked", "Marcado"), accent = true)
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
                    Text(number, Modifier.width(36.dp), color = RoninColors.textMuted)
                    copy(Modifier.weight(1f))
                    Text(
                        date ?: "—",
                        Modifier.width(132.dp),
                        color = RoninColors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        if (read == true) {
                            roninText("✓ Read", "✓ Leído")
                        } else if (inProgress) {
                            roninText("◉ In progress", "◉ En progreso")
                        } else {
                            roninText("○ Unread", "○ No leído")
                        },
                        Modifier.width(132.dp),
                        color = if (read == true || inProgress) RoninColors.accentSage else RoninColors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Box(Modifier.width(140.dp)) { trailing?.invoke() }
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

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun RoninSidebarItem(
    title: String,
    selected: Boolean,
    compact: Boolean,
    dense: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (Color) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(title) } },
        state = rememberTooltipState(),
        focusable = false,
        enableUserInput = compact,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Surface(
                modifier = Modifier.fillMaxWidth()
                    .semantics {
                        this.selected = selected
                        contentDescription = title
                    }
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
                    BorderStroke(RoninBorders.hairline, RoninColors.accentCoral.copy(alpha = 0.34f))
                } else {
                    null
                },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = if (compact) RoninSpacing.small else RoninSpacing.medium,
                        vertical = if (dense) 6.dp else 10.dp,
                    ),
                    horizontalArrangement = if (compact) {
                        Arrangement.Center
                    } else {
                        Arrangement.spacedBy(RoninSpacing.small)
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    icon(if (selected) RoninColors.accentCoral else RoninColors.textSecondary)
                    if (!compact) {
                        Text(
                            title,
                            color = if (selected) RoninColors.accentCoral else RoninColors.textPrimary,
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
                            RoninColors.accentCoral,
                            RoundedCornerShape(RoninRadius.control),
                        ),
                )
            }
        }
    }
}
