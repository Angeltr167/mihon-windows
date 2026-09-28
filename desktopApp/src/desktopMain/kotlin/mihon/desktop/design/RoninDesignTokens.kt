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
    val appBackground = Color(0xFF0A0E0F)
    val elevatedSurface = Color(0xFF111718)
    val secondarySurface = Color(0xFF171E1E)
    val selectedSurface = Color(0xFF2A201D)
    val hoverSurface = Color(0xFF202626)
    val sidebarSurface = Color(0xFF0D1213)

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
    val sidebarExpanded = 220.dp
    val sidebarCompact = 64.dp
    val sidebarCompactBreakpoint = 900.dp

    val gutterCompact = 12.dp
    val gutterDesktop = 24.dp
    val gutterWide = 40.dp
    val contentMaxWidth = 1680.dp
    val rightPanelWidth = 326.dp
    val rightPanelBreakpoint = 1320.dp
    val wideContentBreakpoint = 1600.dp

    val gridGap = 16.dp
    val listRowMinHeight = 56.dp
    val chapterRowMinHeight = 58.dp
}

internal object RoninReaderMetrics {
    val chromeTopMaxWidth = 1180.dp
    val chromeBottomMaxWidth = 1040.dp
    val chromeHorizontalMargin = 16.dp
    val chromeTopMargin = 10.dp
    val chromeBottomMargin = 12.dp
    val pageHorizontalMargin = 18.dp
    val pageVerticalMargin = 10.dp
    val compactControlsBreakpoint = 860.dp
    val narrowHeaderBreakpoint = 820.dp
    val pageSeamWidth = 2.dp

    const val AUTO_HIDE_DELAY_MILLIS = 2_800L
    const val WHEEL_GESTURE_RESET_MILLIS = 260L
}

internal object RoninMangaMetrics {
    const val COVER_ASPECT_RATIO = 0.70f

    val coverCompactWidth = 70.dp
    val coverCompactHeight = 100.dp
    val coverGridWidth = 142.dp
    val coverGridHeight = 203.dp
    val coverDetailWidth = 170.dp
    val coverDetailHeight = 245.dp

    val controlHeight = 40.dp
    val denseCardMetadataGap = 4.dp
}
