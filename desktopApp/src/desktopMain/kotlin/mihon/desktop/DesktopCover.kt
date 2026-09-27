package mihon.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import org.jetbrains.skia.Image as SkiaImage

@Composable
fun DesktopCover(
    url: String?,
    source: Source?,
    modifier: Modifier = Modifier.width(90.dp).height(130.dp),
) {
    if (url.isNullOrBlank()) {
        Box(
            modifier.background(Color(0xFF182125)),
            contentAlignment = Alignment.Center,
        ) {
            Text("No cover", style = MaterialTheme.typography.labelSmall, color = MihonPalette.muted)
        }
        return
    }
    val bitmap by produceState<ImageBitmap?>(null, url, source) {
        value = runCatching { loadCover(url, source) }.getOrNull()
    }
    bitmap?.let { image ->
        Image(
            bitmap = image,
            contentDescription = "Manga cover",
            contentScale = ContentScale.Fit,
            modifier = modifier.background(Color(0xFF080D10)),
        )
    } ?: run {
        Box(
            modifier.background(Color(0xFF182125)),
            contentAlignment = Alignment.Center,
        ) {
            Text("Cover unavailable", style = MaterialTheme.typography.labelSmall, color = MihonPalette.muted)
        }
    }
}

private suspend fun loadCover(url: String, source: Source?): ImageBitmap = withContext(Dispatchers.IO) {
    val uri = URI(url)
    val bytes = if (uri.scheme.equals("file", ignoreCase = true)) {
        Files.newInputStream(Path.of(uri)).use { it.readNBytes(MAX_IMAGE_BYTES + 1) }
    } else {
        val httpSource = source as? HttpSource
        val resolved = if (uri.isAbsolute) uri else URI(requireNotNull(httpSource).baseUrl).resolve(uri)
        require(resolved.scheme == "https" || resolved.scheme == "http") { "Unsupported cover URL" }
        val request = Request.Builder().url(resolved.toString()).apply {
            httpSource?.headers?.let(::headers)
        }.build()
        val client = httpSource?.client ?: eu.kanade.tachiyomi.network.NetworkHelper.current().client
        client.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "Cover request failed: ${response.code}" }
            response.body.byteStream().use { it.readNBytes(MAX_IMAGE_BYTES + 1) }
        }
    }
    require(bytes.size <= MAX_IMAGE_BYTES) { "Cover image is too large" }
    SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
}

private const val MAX_IMAGE_BYTES = 16 * 1024 * 1024
