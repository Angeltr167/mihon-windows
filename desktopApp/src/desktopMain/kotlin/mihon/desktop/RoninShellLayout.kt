package mihon.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import mihon.desktop.design.RoninBorders
import mihon.desktop.design.RoninColors
import mihon.desktop.design.RoninLayout
import mihon.desktop.design.RoninSpacing
import org.jetbrains.skia.Image as SkiaImage

@Composable
internal fun RoninWindowSurface(
    modifier: Modifier = Modifier,
    readerMode: Boolean,
    content: @Composable () -> Unit,
) {
    Surface(modifier = modifier, color = RoninColors.appBackground) {
        Box(Modifier.fillMaxSize()) {
            if (!readerMode) RoninAtmosphere(Modifier.fillMaxSize())
            content()
        }
    }
}

/**
 * Global desktop content frame for Ronin.
 *
 * It keeps screen content centered at desktop widths, preserves a dense compact
 * gutter on smaller windows, and owns the optional contextual right-panel slot.
 */
@Composable
internal fun RowScope.RoninMainContent(
    compactNavigation: Boolean,
    readerMode: Boolean,
    modifier: Modifier = Modifier,
    rightPanel: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    RoninColors.appBackground.copy(alpha = 0.10f),
                    RoninColors.appBackground.copy(alpha = 0.15f),
                    RoninColors.appBackground.copy(alpha = 0.22f),
                ),
            ),
        ),
    ) {
        val showRightPanel =
            !readerMode && rightPanel != null && maxWidth >= RoninLayout.rightPanelBreakpoint
        val horizontalGutter = when {
            readerMode -> RoninSpacing.small
            maxWidth >= RoninLayout.wideContentBreakpoint -> RoninLayout.gutterWide
            compactNavigation -> RoninLayout.gutterCompact
            else -> RoninLayout.gutterDesktop
        }
        val verticalGutter = when {
            readerMode -> RoninSpacing.small
            compactNavigation -> RoninLayout.gutterCompact
            else -> RoninLayout.gutterDesktop
        }

        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                val contentModifier = if (readerMode) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.align(Alignment.TopCenter)
                        .widthIn(max = RoninLayout.contentMaxWidth)
                        .fillMaxWidth()
                        .fillMaxHeight()
                }
                Column(
                    modifier = contentModifier.padding(
                        start = horizontalGutter,
                        top = verticalGutter,
                        end = horizontalGutter,
                        bottom = verticalGutter,
                    ),
                    content = content,
                )
            }

            if (showRightPanel) {
                Surface(
                    modifier = Modifier.width(RoninLayout.rightPanelWidth).fillMaxHeight(),
                    color = RoninColors.elevatedSurface.copy(alpha = 0.96f),
                    border = BorderStroke(RoninBorders.hairline, RoninColors.borderSubtle),
                ) {
                    Box(Modifier.fillMaxSize().padding(RoninSpacing.large)) {
                        rightPanel.invoke()
                    }
                }
            }
        }
    }
}

/** Original landscape artwork shared by the shell and reader. */
@Composable
internal fun RoninAtmosphere(
    modifier: Modifier = Modifier,
) {
    val landscape = remember {
        runCatching {
            requireNotNull(RoninColors::class.java.getResourceAsStream("/ronin/night-landscape.png"))
                .use { SkiaImage.makeFromEncoded(it.readBytes()).toComposeImageBitmap() }
        }.getOrNull()
    }
    Box(modifier.background(RoninColors.appBackground)) {
        if (landscape != null) {
            Image(
                bitmap = landscape,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                alpha = 0.92f,
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        RoninColors.appBackground.copy(alpha = 0.26f),
                        RoninColors.appBackground.copy(alpha = 0.17f),
                    ),
                ),
            ),
        )
    }
}
