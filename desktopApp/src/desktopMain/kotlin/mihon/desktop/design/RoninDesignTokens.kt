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
    val appBackground = Color(0xFF0B1013)
    val elevatedSurface = Color(0xFF11191D)
    val secondarySurface = Color(0xFF182125)
    val selectedSurface = Color(0xFF1B2825)
    val hoverSurface = Color(0xFF202A2D)

    val border = Color(0xFF344044)
    val borderSubtle = Color(0xFF273237)

    val textPrimary = Color(0xFFE8E9E2)
    val textSecondary = Color(0xFFB8BDBA)
    val textMuted = Color(0xFF8F9793)
    val textDisabled = Color(0xFF626B67)

    val accentSage = Color(0xFFC6DEA1)
    val accentSageHover = Color(0xFFD6E9B8)
    val accentGold = Color(0xFFC7A66A)

    val success = Color(0xFFA9C98F)
    val warning = Color(0xFFD8B46A)
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
    val cover = 8.dp
    val card = 10.dp
    val panel = 12.dp
    val pill = 999.dp
}

internal object RoninBorders {
    val hairline = 1.dp
    val emphasized = 1.dp
}

internal object RoninLayout {
    val sidebarExpanded = 202.dp
    val sidebarCompact = 64.dp
    val sidebarCompactBreakpoint = 900.dp

    val gutterCompact = 12.dp
    val gutterDesktop = 24.dp
    val gutterWide = 48.dp
    val contentMaxWidth = 1680.dp
    val rightPanelWidth = 320.dp

    val gridGap = 16.dp
    val listRowMinHeight = 56.dp
    val chapterRowMinHeight = 58.dp
}

internal object RoninMangaMetrics {
    const val coverAspectRatio = 0.70f

    val coverCompactWidth = 70.dp
    val coverCompactHeight = 100.dp
    val coverGridWidth = 142.dp
    val coverGridHeight = 203.dp
    val coverDetailWidth = 170.dp
    val coverDetailHeight = 245.dp

    val controlHeight = 40.dp
    val denseCardMetadataGap = 4.dp
}
