package mihon.desktop

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import mihon.desktop.design.RoninMangaMetrics

// Compatibility names for DesktopShell. The visual-fidelity pass moved the
// canonical tokens into mihon.desktop.design; keep unqualified shell usages
// bound to those same objects without changing runtime behavior.
internal val RoninColors = mihon.desktop.design.RoninColors
internal val RoninSpacing = mihon.desktop.design.RoninSpacing
internal val RoninRadius = mihon.desktop.design.RoninRadius
internal val RoninLayout = mihon.desktop.design.RoninLayout

// Content-lambda variant for settings and download toolbar actions.
@Composable
internal fun RoninTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(RoninMangaMetrics.controlHeight),
        shape = RoundedCornerShape(RoninRadius.control),
        content = { content() },
    )
}

// ExtensionsScreen uses Box only as a layout host for its section content.
// Keeping this package-local bridge avoids touching extension behavior.
@Composable
internal fun Box(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    propagateMinConstraints: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier,
        contentAlignment = contentAlignment,
        propagateMinConstraints = propagateMinConstraints,
        content = content,
    )
}
