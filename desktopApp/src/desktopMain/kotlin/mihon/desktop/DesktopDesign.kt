package mihon.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
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
    val selected = RoninColors.selectedSurface
    val hover = RoninColors.hoverSurface
    val ivory = RoninColors.textPrimary
    val muted = RoninColors.textSecondary
    val mutedQuiet = RoninColors.textMuted
    val sage = RoninColors.accentSage
    val gold = RoninColors.accentGold
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

/**
 * Compatibility bridge for pre-Ronin desktop call sites.
 *
 * New UI should call the Ronin* primitives directly. Existing screens keep
 * their current names until their dedicated redesign phase, but render through
 * the shared Ronin component system now.
 */
@Composable
internal fun MihonPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    RoninPanel(modifier = modifier, content = content)
}

@Composable
internal fun MihonSectionHeader(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    RoninSectionHeader(
        title = title,
        subtitle = subtitle,
        trailing = trailing,
    )
}

@Composable
internal fun MihonEmptyState(
    title: String,
    detail: String? = null,
) {
    RoninEmptyState(
        title = title,
        detail = detail,
    )
}

@Composable
internal fun MihonChoiceChip(
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
internal fun MihonCompactChip(
    label: String,
    accent: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    RoninChip(
        label = label,
        accent = accent,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
internal fun MihonTabStrip(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    RoninTabStrip(
        labels = labels,
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        modifier = modifier,
    )
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
