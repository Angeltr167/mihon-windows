package mihon.desktop.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import mihon.desktop.LocalRoninLanguage
import java.util.Locale

@Composable
internal fun RoninDesktopTheme(
    languageTag: String = Locale.getDefault().toLanguageTag(),
    covers: mihon.desktop.DesktopCustomCovers? = null,
    coverRevision: Int = 0,
    content: @Composable () -> Unit,
) {
    val baseTypography = MaterialTheme.typography

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = RoninColors.accentCoral,
            onPrimary = RoninColors.appBackground,
            primaryContainer = RoninColors.selectedSurface,
            onPrimaryContainer = RoninColors.accentCoralHover,
            secondary = RoninColors.accentSage,
            onSecondary = RoninColors.appBackground,
            secondaryContainer = RoninColors.secondarySurface,
            onSecondaryContainer = RoninColors.textPrimary,
            tertiary = RoninColors.accentGold,
            onTertiary = RoninColors.appBackground,
            tertiaryContainer = RoninColors.secondarySurface,
            onTertiaryContainer = RoninColors.accentGold,
            background = RoninColors.appBackground,
            onBackground = RoninColors.textPrimary,
            surface = RoninColors.elevatedSurface,
            onSurface = RoninColors.textPrimary,
            surfaceVariant = RoninColors.secondarySurface,
            onSurfaceVariant = RoninColors.textSecondary,
            surfaceDim = RoninColors.appBackground,
            surfaceBright = RoninColors.hoverSurface,
            surfaceContainerLowest = RoninColors.appBackground,
            surfaceContainerLow = RoninColors.elevatedSurface,
            surfaceContainer = RoninColors.elevatedSurface,
            surfaceContainerHigh = RoninColors.secondarySurface,
            surfaceContainerHighest = RoninColors.hoverSurface,
            surfaceTint = RoninColors.accentCoral,
            outline = RoninColors.border,
            outlineVariant = RoninColors.borderSubtle,
            error = RoninColors.error,
            onError = RoninColors.appBackground,
            errorContainer = RoninColors.errorContainer,
            onErrorContainer = RoninColors.error,
            inverseSurface = RoninColors.textPrimary,
            inverseOnSurface = RoninColors.appBackground,
            inversePrimary = RoninColors.appBackground,
        ),
        typography = roninMaterialTypography(baseTypography),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(RoninRadius.control),
            small = RoundedCornerShape(RoninRadius.control),
            medium = RoundedCornerShape(RoninRadius.card),
            large = RoundedCornerShape(RoninRadius.panel),
            extraLarge = RoundedCornerShape(RoninRadius.panel),
        ),
        content = {
            CompositionLocalProvider(
                LocalRoninLanguage provides languageTag,
                mihon.desktop.LocalRoninCovers provides covers,
                mihon.desktop.LocalRoninCoverRevision provides coverRevision,
            ) { content() }
        },
    )
}
