package mihon.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal object MihonPalette {
    val graphite = Color(0xFF0B1013)
    val panel = Color(0xFF11191D)
    val raised = Color(0xFF182125)
    val ivory = Color(0xFFE8E9E2)
    val muted = Color(0xFFA5ABA9)
    val sage = Color(0xFFC6DEA1)
    val error = Color(0xFFFFB4AB)
    val outline = Color(0xFF344044)
}

@Composable
internal fun MihonDesktopTheme(content: @Composable () -> Unit) {
    val base = MaterialTheme.typography
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = MihonPalette.sage,
            onPrimary = MihonPalette.graphite,
            secondary = MihonPalette.muted,
            onSecondary = MihonPalette.graphite,
            background = MihonPalette.graphite,
            onBackground = MihonPalette.ivory,
            surface = MihonPalette.panel,
            onSurface = MihonPalette.ivory,
            surfaceVariant = MihonPalette.raised,
            onSurfaceVariant = MihonPalette.muted,
            outline = MihonPalette.outline,
            error = MihonPalette.error,
            onError = MihonPalette.graphite,
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
        shape = RoundedCornerShape(12.dp),
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
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            androidx.compose.material3.Text(title, style = MaterialTheme.typography.headlineSmall)
            subtitle?.let {
                androidx.compose.material3.Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MihonPalette.muted,
                )
            }
        }
        trailing?.invoke()
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
