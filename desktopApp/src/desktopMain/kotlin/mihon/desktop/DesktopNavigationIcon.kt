package mihon.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Small code-native navigation marks; no raster assets or icon library is needed. */
@Composable
internal fun DesktopNavigationIcon(index: Int, color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val unit = size.minDimension / 24f
        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, point(x1, y1), point(x2, y2), strokeWidth = 1.7f * unit)
        fun ring(x: Float, y: Float, radius: Float) =
            drawCircle(color, radius * unit, point(x, y), style = Stroke(1.7f * unit))
        fun path(vararg coordinates: Pair<Float, Float>) {
            val shape = Path().apply {
                coordinates.forEachIndexed { position, coordinate ->
                    if (position == 0) {
                        moveTo(coordinate.first * unit, coordinate.second * unit)
                    } else {
                        lineTo(coordinate.first * unit, coordinate.second * unit)
                    }
                }
            }
            drawPath(shape, color, style = Stroke(1.7f * unit))
        }
        when (index) {
            0 -> {
                path(
                    3f to 4f, 8f to 3f, 12f to 5f, 16f to 3f, 21f to 4f, 21f to 19f, 16f to 18f,
                    12f to 20f, 8f to 18f, 3f to 19f, 3f to 4f,
                )
                line(12f, 5f, 12f, 20f)
            }
            1 -> {
                ring(12f, 12f, 8f)
                path(17f to 4f, 20f to 4f, 20f to 8f)
                path(7f to 20f, 4f to 20f, 4f to 16f)
            }
            2 -> {
                ring(12f, 12f, 9f)
                line(12f, 6f, 12f, 12f)
                line(12f, 12f, 16f, 14f)
            }
            3 -> {
                drawOval(color, point(3f, 3f), Size(18f * unit, 5f * unit), style = Stroke(1.7f * unit))
                path(3f to 5.5f, 3f to 18f, 6f to 20f, 18f to 20f, 21f to 18f, 21f to 5.5f)
                path(3f to 11f, 6f to 13f, 18f to 13f, 21f to 11f)
            }
            4 -> {
                ring(10f, 10f, 7f)
                line(15f, 15f, 21f, 21f)
            }
            5 -> {
                path(
                    4f to 4f, 10f to 4f, 10f to 7f, 14f to 7f, 14f to 4f, 20f to 4f,
                    20f to 10f, 17f to 10f, 17f to 14f, 20f to 14f, 20f to 20f,
                    14f to 20f, 14f to 17f, 10f to 17f, 10f to 20f, 4f to 20f, 4f to 4f,
                )
            }
            6 -> path(
                3f to 7f,
                10f to 7f,
                12f to 10f,
                21f to 10f,
                21f to 20f,
                3f to 20f,
                3f to 7f,
            )
            7 -> {
                ring(12f, 12f, 5f)
                for (angle in 0 until 8) {
                    val radians = angle * kotlin.math.PI / 4
                    line(
                        (12 + 8 * kotlin.math.cos(radians)).toFloat(),
                        (12 + 8 * kotlin.math.sin(radians)).toFloat(),
                        (12 + 11 * kotlin.math.cos(radians)).toFloat(),
                        (12 + 11 * kotlin.math.sin(radians)).toFloat(),
                    )
                }
            }
            8 -> {
                line(12f, 3f, 12f, 16f)
                path(7f to 11f, 12f to 16f, 17f to 11f)
                line(4f, 21f, 20f, 21f)
            }
        }
    }
}
