package mihon.desktop.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

@Composable
internal fun RoninDesktopTheme(content: @Composable () -> Unit) {
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
        content = content,
    )
}
