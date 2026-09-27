package mihon.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import mihon.desktop.design.RoninColors
import mihon.desktop.design.RoninDesktopTheme
import mihon.desktop.design.RoninLayout
import mihon.desktop.design.RoninMangaMetrics
import mihon.desktop.design.RoninRadius
import mihon.desktop.design.RoninSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal object MihonPalette {
    val graphite = RoninColors.appBackground
    val panel = RoninColors.elevatedSurface
    val raised = RoninColors.secondarySurface
    val ivory = RoninColors.textPrimary
    val muted = RoninColors.textSecondary
    val sage = RoninColors.accentSage
    val error = RoninColors.error
    val outline = RoninColors.border
    val outlineSoft = RoninColors.borderSubtle
    val errorContainer = RoninColors.errorContainer
}

internal object MihonSpacing {
    val xxs = RoninSpacing.micro
    val xs = RoninSpacing.xSmall
    val sm = RoninSpacing.small
    val md = RoninSpacing.small + RoninSpacing.xSmall
    val lg = RoninSpacing.medium
    val xl = RoninSpacing.large
    val section = RoninSpacing.xLarge
}

internal object MihonRadius {
    val control = RoninRadius.control
    val card = RoninRadius.card
    val panel = RoninRadius.panel
}

internal object MihonSizes {
    val navigationExpanded = RoninLayout.sidebarExpanded
    val navigationCompact = RoninLayout.sidebarCompact
    val navigationCompactBreakpoint = RoninLayout.sidebarCompactBreakpoint
    val gutterCompact = RoninLayout.gutterCompact
    val gutterDesktop = RoninLayout.gutterDesktop
    val gutterWide = RoninLayout.gutterWide
    val contentMaxWidth = RoninLayout.contentMaxWidth
    val rightPanelWidth = RoninLayout.rightPanelWidth
    val coverSmallWidth = RoninMangaMetrics.coverCompactWidth
    val coverSmallHeight = RoninMangaMetrics.coverCompactHeight
    val coverDetailWidth = RoninMangaMetrics.coverDetailWidth
    val coverDetailHeight = RoninMangaMetrics.coverDetailHeight
    val controlHeight = RoninMangaMetrics.controlHeight
}

@Composable
internal fun MihonDesktopTheme(content: @Composable () -> Unit) {
    RoninDesktopTheme(content)
}

@Composable
internal fun MihonPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(MihonRadius.panel),
        color = MihonPalette.panel,
        border = BorderStroke(1.dp, MihonPalette.outline),
        content = content,
    )
}

@Composable
internal fun MihonSectionHeader(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = MihonSpacing.md)) {
        val narrow = maxWidth < 720.dp
        val label: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(MihonSpacing.xxs)) {
                androidx.compose.material3.Text(title, style = MaterialTheme.typography.headlineSmall)
                subtitle?.let {
                    androidx.compose.material3.Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MihonPalette.muted,
                    )
                }
            }
        }
        if (narrow) {
            Column(verticalArrangement = Arrangement.spacedBy(MihonSpacing.md)) {
                label()
                trailing?.invoke()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                label()
                trailing?.invoke()
            }
        }
    }
}

@Composable
internal fun MihonEmptyState(
    title: String,
    detail: String? = null,
) {
    MihonPanel(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            androidx.compose.material3.Text(title, style = MaterialTheme.typography.titleMedium)
            detail?.let {
                androidx.compose.material3.Text(
                    it,
                    color = MihonPalette.muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
internal fun MihonChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(role = Role.Tab, onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) MihonPalette.sage.copy(alpha = 0.14f) else MihonPalette.panel,
        border = BorderStroke(
            1.dp,
            if (selected) MihonPalette.sage.copy(alpha = 0.62f) else MihonPalette.outlineSoft,
        ),
    ) {
        androidx.compose.material3.Text(
            label,
            modifier = Modifier.padding(horizontal = MihonSpacing.md, vertical = MihonSpacing.sm),
            color = if (selected) MihonPalette.sage else MihonPalette.muted,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
internal fun MihonCompactChip(
    label: String,
    accent: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val interactiveModifier = if (onClick != null) {
        modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        modifier
    }
    Surface(
        modifier = interactiveModifier,
        shape = RoundedCornerShape(999.dp),
        color = if (accent) MihonPalette.sage.copy(alpha = 0.12f) else MihonPalette.raised,
        border = BorderStroke(
            1.dp,
            if (accent) MihonPalette.sage.copy(alpha = 0.48f) else MihonPalette.outlineSoft,
        ),
    ) {
        androidx.compose.material3.Text(
            label,
            modifier = Modifier.padding(horizontal = MihonSpacing.sm, vertical = MihonSpacing.xs),
            color = if (accent) MihonPalette.sage else MihonPalette.muted,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
internal fun MihonTabStrip(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MihonSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(MihonSpacing.xs),
    ) {
        labels.forEachIndexed { index, label ->
            MihonChoiceChip(
                label = label,
                selected = selectedIndex == index,
                onClick = { onSelect(index) },
            )
        }
    }
}

internal fun desktopDateGroup(epochMillis: Long): String {
    if (epochMillis <= 0L) return "Date unavailable"
    val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return when (date) {
        LocalDate.now() -> "Today"
        LocalDate.now().minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    }
}
