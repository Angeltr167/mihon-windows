package mihon.desktop.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal object RoninTypeScale {
    val branding = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 29.sp,
    )

    val displayHeading = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 48.sp,
        lineHeight = 56.sp,
    )

    val screenHeading = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 32.sp,
        lineHeight = 39.sp,
    )

    val sectionHeading = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 25.sp,
        lineHeight = 31.sp,
    )

    val cardHeading = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    )

    val body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    )

    val metadata = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )

    val label = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )

    val caption = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
}

internal fun roninMaterialTypography(base: Typography): Typography = base.copy(
    displayLarge = RoninTypeScale.displayHeading.copy(fontSize = 48.sp, lineHeight = 56.sp),
    displayMedium = RoninTypeScale.displayHeading,
    displaySmall = RoninTypeScale.screenHeading.copy(fontSize = 32.sp, lineHeight = 38.sp),
    headlineLarge = RoninTypeScale.screenHeading,
    headlineMedium = RoninTypeScale.sectionHeading.copy(fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = RoninTypeScale.sectionHeading,
    titleLarge = RoninTypeScale.sectionHeading,
    titleMedium = RoninTypeScale.cardHeading,
    titleSmall = RoninTypeScale.cardHeading.copy(fontSize = 14.sp, lineHeight = 18.sp),
    bodyLarge = RoninTypeScale.body.copy(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = RoninTypeScale.body,
    bodySmall = RoninTypeScale.metadata,
    labelLarge = RoninTypeScale.label.copy(fontSize = 14.sp, lineHeight = 19.sp),
    labelMedium = RoninTypeScale.label.copy(fontSize = 13.sp, lineHeight = 17.sp),
    labelSmall = RoninTypeScale.caption.copy(fontWeight = FontWeight.Medium),
)
