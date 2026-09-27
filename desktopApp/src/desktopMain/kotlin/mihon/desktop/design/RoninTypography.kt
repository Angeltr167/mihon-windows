package mihon.desktop.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal object RoninTypeScale {
    val branding = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
    )

    val displayHeading = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 36.sp,
        lineHeight = 42.sp,
    )

    val screenHeading = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    )

    val sectionHeading = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    )

    val cardHeading = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    )

    val body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )

    val metadata = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
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
        fontSize = 11.sp,
        lineHeight = 14.sp,
    )
}

internal fun roninMaterialTypography(base: Typography): Typography = base.copy(
    displayLarge = RoninTypeScale.displayHeading.copy(fontSize = 44.sp, lineHeight = 50.sp),
    displayMedium = RoninTypeScale.displayHeading,
    displaySmall = RoninTypeScale.screenHeading.copy(fontSize = 32.sp, lineHeight = 38.sp),
    headlineLarge = RoninTypeScale.screenHeading,
    headlineMedium = RoninTypeScale.sectionHeading.copy(fontSize = 24.sp, lineHeight = 30.sp),
    headlineSmall = RoninTypeScale.sectionHeading,
    titleLarge = RoninTypeScale.sectionHeading,
    titleMedium = RoninTypeScale.cardHeading,
    titleSmall = RoninTypeScale.cardHeading.copy(fontSize = 14.sp, lineHeight = 18.sp),
    bodyLarge = RoninTypeScale.body.copy(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = RoninTypeScale.body,
    bodySmall = RoninTypeScale.metadata,
    labelLarge = RoninTypeScale.label.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = RoninTypeScale.label,
    labelSmall = RoninTypeScale.caption.copy(fontWeight = FontWeight.Medium),
)
