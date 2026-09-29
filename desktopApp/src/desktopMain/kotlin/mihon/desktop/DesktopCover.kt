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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.desktop.design.RoninColors
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
    contentScale: ContentScale = ContentScale.Fit,
) {
    if (url.isNullOrBlank()) {
        Box(
            modifier.background(RoninColors.secondarySurface),
            contentAlignment = Alignment.Center,
        ) {
            Text("No cover", style = MaterialTheme.typography.labelSmall, color = RoninColors.textMuted)
        }
        return
    }
    var bitmap by remember(url, source) { mutableStateOf<Result<ImageBitmap>?>(null) }
    LaunchedEffect(url, source) {
        bitmap = null
        val loaded = runCatching { loadCover(url, source) }
        loaded.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        bitmap = loaded
    }
    bitmap?.getOrNull()?.let { image ->
        Image(
            bitmap = image,
            contentDescription = "Manga cover",
            contentScale = contentScale,
            modifier = modifier.background(RoninColors.appBackground),
        )
    } ?: run {
        Box(
            modifier.background(RoninColors.secondarySurface),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (bitmap == null) "Loading cover…" else "Cover unavailable",
                style = MaterialTheme.typography.labelSmall,
                color = RoninColors.textMuted,
            )
        }
    }
}

private suspend fun loadCover(url: String, source: Source?): ImageBitmap = withContext(Dispatchers.IO) {
    val uri = URI(if (source is SuwayomiSource) source.coverUrl(url) else url)
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
