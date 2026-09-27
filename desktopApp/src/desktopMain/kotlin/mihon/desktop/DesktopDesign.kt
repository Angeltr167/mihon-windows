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
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal object MihonPalette {
    val graphite = Color(0xFF0B1013)
    val panel = Color(0xFF11191D)
    val raised = Color(0xFF182125)
    val ivory = Color(0xFFE8E9E2)
    val muted = Color(0xFFA5ABA9)
    val sage = Color(0xFFC6DEA1)
    val error = Color(0xFFFFB4AB)
    val outline = Color(0xFF344044)
    val outlineSoft = Color(0xFF273237)
    val errorContainer = Color(0xFF3A2525)
}

internal object MihonSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val section = 32.dp
}

internal object MihonRadius {
    val control = 6.dp
    val card = 10.dp
    val panel = 12.dp
}

internal object MihonSizes {
    val navigationExpanded = 202.dp
    val navigationCompact = 64.dp
    val coverSmallWidth = 70.dp
    val coverSmallHeight = 100.dp
    val coverDetailWidth = 170.dp
    val coverDetailHeight = 245.dp
    val controlHeight = 40.dp
}

@Composable
internal fun MihonDesktopTheme(content: @Composable () -> Unit) {
    val base = MaterialTheme.typography
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = MihonPalette.sage,
            onPrimary = MihonPalette.graphite,
            primaryContainer = MihonPalette.raised,
            onPrimaryContainer = MihonPalette.sage,
            secondary = MihonPalette.muted,
            onSecondary = MihonPalette.graphite,
            secondaryContainer = MihonPalette.raised,
            onSecondaryContainer = MihonPalette.ivory,
            tertiary = MihonPalette.sage,
            onTertiary = MihonPalette.graphite,
            tertiaryContainer = MihonPalette.raised,
            onTertiaryContainer = MihonPalette.sage,
            background = MihonPalette.graphite,
            onBackground = MihonPalette.ivory,
            surface = MihonPalette.panel,
            onSurface = MihonPalette.ivory,
            surfaceVariant = MihonPalette.raised,
            onSurfaceVariant = MihonPalette.muted,
            surfaceDim = MihonPalette.graphite,
            surfaceBright = MihonPalette.raised,
            surfaceContainerLowest = MihonPalette.graphite,
            surfaceContainer = MihonPalette.panel,
            surfaceContainerLow = MihonPalette.panel,
            surfaceContainerHigh = MihonPalette.raised,
            surfaceContainerHighest = MihonPalette.raised,
            surfaceTint = MihonPalette.sage,
            outline = MihonPalette.outline,
            outlineVariant = MihonPalette.outlineSoft,
            error = MihonPalette.error,
            onError = MihonPalette.graphite,
            errorContainer = MihonPalette.errorContainer,
            onErrorContainer = MihonPalette.error,
            inverseSurface = MihonPalette.ivory,
            inverseOnSurface = MihonPalette.graphite,
            inversePrimary = MihonPalette.graphite,
        ),
        typography = base.copy(
            displayLarge = base.displayLarge.copy(fontFamily = FontFamily.Serif),
            displayMedium = base.displayMedium.copy(fontFamily = FontFamily.Serif),
            displaySmall = base.displaySmall.copy(fontFamily = FontFamily.Serif),
            headlineLarge = base.headlineLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium),
            headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium),
            headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium),
            titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium),
        ),
        content = content,
    )
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
