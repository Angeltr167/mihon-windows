package mihon.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import mihon.desktop.design.RoninColors
import org.jetbrains.skia.Image as SkiaImage

@Composable
internal fun RoninBrandMark(modifier: Modifier = Modifier) {
    val mark = remember {
        runCatching {
            requireNotNull(RoninColors::class.java.getResourceAsStream("/ronin/ronin-mark.png"))
                .use { SkiaImage.makeFromEncoded(it.readBytes()).toComposeImageBitmap() }
        }.getOrNull()
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (mark == null) {
            Text("R", color = RoninColors.accentCoral)
        } else {
            Image(mark, contentDescription = "Ronin", modifier = Modifier.fillMaxSize())
        }
    }
}
