package mihon.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import mihon.desktop.design.RoninBorders
import mihon.desktop.design.RoninColors
import mihon.desktop.design.RoninLayout
import mihon.desktop.design.RoninSpacing

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
                    RoninColors.appBackground,
                    RoninColors.elevatedSurface.copy(alpha = 0.72f),
                    RoninColors.appBackground,
                ),
            ),
        ),
    ) {
        if (!readerMode) {
            RoninAtmosphere(Modifier.fillMaxSize())
        }
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

/** Original low-contrast landscape shapes used as Ronin's desktop atmosphere. */
@Composable
internal fun RoninAtmosphere(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val width = size.width
        val height = size.height
        val shortSide = size.minDimension

        drawCircle(
            color = RoninColors.atmosphereMoon.copy(alpha = 0.28f),
            radius = shortSide * 0.038f,
            center = Offset(width * 0.88f, height * 0.15f),
        )
        drawCircle(
            color = RoninColors.atmosphereMoon.copy(alpha = 0.10f),
            radius = shortSide * 0.052f,
            center = Offset(width * 0.88f, height * 0.15f),
        )

        val farRidge = Path().apply {
            moveTo(0f, height * 0.77f)
            cubicTo(width * 0.14f, height * 0.65f, width * 0.22f, height * 0.78f, width * 0.34f, height * 0.70f)
            cubicTo(width * 0.49f, height * 0.59f, width * 0.54f, height * 0.72f, width * 0.67f, height * 0.65f)
            cubicTo(width * 0.79f, height * 0.59f, width * 0.86f, height * 0.72f, width, height * 0.61f)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        drawPath(farRidge, RoninColors.atmosphereFar.copy(alpha = 0.48f))

        val middleRidge = Path().apply {
            moveTo(0f, height * 0.87f)
            cubicTo(width * 0.16f, height * 0.78f, width * 0.24f, height * 0.88f, width * 0.42f, height * 0.79f)
            cubicTo(width * 0.58f, height * 0.70f, width * 0.72f, height * 0.83f, width, height * 0.74f)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        drawPath(middleRidge, RoninColors.atmosphereMid.copy(alpha = 0.56f))

        val nearRidge = Path().apply {
            moveTo(0f, height * 0.93f)
            cubicTo(width * 0.21f, height * 0.85f, width * 0.35f, height * 0.97f, width * 0.56f, height * 0.88f)
            cubicTo(width * 0.72f, height * 0.82f, width * 0.85f, height * 0.94f, width, height * 0.86f)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        drawPath(nearRidge, RoninColors.atmosphereNear.copy(alpha = 0.70f))

        val toriiX = width * 0.80f
        val toriiBase = height * 0.90f
        val toriiHeight = height * 0.10f
        val toriiColor = RoninColors.accentCoral.copy(alpha = 0.18f)
        drawLine(
            toriiColor,
            Offset(toriiX - toriiHeight * 0.42f, toriiBase),
            Offset(
                toriiX - toriiHeight * 0.42f,
                toriiBase - toriiHeight,
            ),
            strokeWidth = 2.dp.toPx(),
        )
        drawLine(
            toriiColor,
            Offset(toriiX + toriiHeight * 0.42f, toriiBase),
            Offset(
                toriiX + toriiHeight * 0.42f,
                toriiBase - toriiHeight,
            ),
            strokeWidth = 2.dp.toPx(),
        )
        drawLine(
            toriiColor,
            Offset(toriiX - toriiHeight * 0.64f, toriiBase - toriiHeight),
            Offset(toriiX + toriiHeight * 0.64f, toriiBase - toriiHeight),
            strokeWidth = 3.dp.toPx(),
        )
        drawLine(
            toriiColor,
            Offset(toriiX - toriiHeight * 0.50f, toriiBase - toriiHeight * 0.76f),
            Offset(toriiX + toriiHeight * 0.50f, toriiBase - toriiHeight * 0.76f),
            strokeWidth = 2.dp.toPx(),
        )
    }
}
