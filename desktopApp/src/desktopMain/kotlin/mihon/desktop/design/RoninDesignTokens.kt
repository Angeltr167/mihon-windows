package mihon.desktop.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Canonical visual tokens for the Ronin desktop product.
 *
 * Screen-specific composables should consume these semantic tokens instead of
 * introducing one-off colors, dimensions, radii, or layout constants.
 */
internal object RoninColors {
    val appBackground = Color(0xFF090E11)
    val elevatedSurface = Color(0xE70F171A)
    val secondarySurface = Color(0xE8192225)
    val selectedSurface = Color(0xFF2A201D)
    val hoverSurface = Color(0xFF202626)
    val sidebarSurface = Color(0xFF0B1215)

    val border = Color(0xFF383D3B)
    val borderSubtle = Color(0xFF282E2D)

    val textPrimary = Color(0xFFF0EBDD)
    val textSecondary = Color(0xFFC0BBAF)
    val textMuted = Color(0xFF918E86)
    val textDisabled = Color(0xFF666963)

    // Coral/apricot is the product action accent from the approved mockups.
    // Sage is deliberately semantic/secondary: progress, healthy states and quiet metadata.
    val accentCoral = Color(0xFFE58D73)
    val accentCoralHover = Color(0xFFF0A087)
    val accentSage = Color(0xFFB8C7A5)
    val accentSageHover = Color(0xFFC9D5B9)
    val accentGold = Color(0xFFC7A66A)

    val success = Color(0xFFAFC39D)
    val warning = Color(0xFFD7B276)
    val error = Color(0xFFFFB4AB)
    val errorContainer = Color(0xFF3A2525)
}

internal object RoninSpacing {
    val micro = 2.dp
    val xSmall = 4.dp
    val small = 8.dp
    val medium = 16.dp
    val large = 24.dp
    val xLarge = 32.dp
    val huge = 48.dp
}

internal object RoninRadius {
    val control = 6.dp
    val cover = 7.dp
    val card = 8.dp
    val panel = 10.dp
    val pill = 999.dp
}

internal object RoninBorders {
    val hairline = 1.dp
    val emphasized = 1.dp
}

internal object RoninLayout {
    val sidebarExpanded = 308.dp
    val sidebarCompact = 68.dp
    val sidebarCompactBreakpoint = 960.dp

    val gutterCompact = 12.dp
    val gutterDesktop = 28.dp
    val gutterWide = 32.dp
    val contentMaxWidth = 2100.dp
    val rightPanelWidth = 340.dp

    // Measured inside the content area, after navigation and page gutters.
    val rightPanelBreakpoint = 1120.dp
    val settingsNavigationWidth = 248.dp
    val compactSummaryMaxHeight = 180.dp
    val libraryControlsMaxHeight = 160.dp
    val settingsPanelMinHeight = 220.dp
    val wideContentBreakpoint = 1600.dp

    val gridGap = 20.dp
    val listRowMinHeight = 64.dp
    val chapterRowMinHeight = 64.dp
}

internal object RoninReaderMetrics {
    val chromeTopMaxWidth = 1800.dp
    val chromeBottomMaxWidth = 1760.dp
    val chromeHorizontalMargin = 24.dp
    val chromeTopMargin = 14.dp
    val chromeBottomMargin = 18.dp
    val pageHorizontalMargin = 22.dp
    val pageVerticalMargin = 14.dp
    val compactControlsBreakpoint = 1040.dp
    val narrowHeaderBreakpoint = 980.dp
    val pageSeamWidth = 2.dp

    const val AUTO_HIDE_DELAY_MILLIS = 2_800L
    const val WHEEL_GESTURE_RESET_MILLIS = 260L
}

internal object RoninMangaMetrics {
    const val COVER_ASPECT_RATIO = 0.70f

    val coverCompactWidth = 84.dp
    val coverCompactHeight = 120.dp
    val coverGridWidth = 180.dp
    val coverGridHeight = 257.dp
    val gridCellMinWidth = 188.dp
    val continueCardWidth = 190.dp
    val coverDetailWidth = 190.dp
    val coverDetailHeight = 272.dp

    val controlHeight = 44.dp
    val denseCardMetadataGap = 6.dp
}
