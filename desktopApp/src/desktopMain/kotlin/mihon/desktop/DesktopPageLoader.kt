package mihon.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import tachiyomi.source.local.desktop.DesktopLocalChapterPages
import tachiyomi.source.local.desktop.DesktopLocalPage
import tachiyomi.source.local.desktop.DesktopLocalSource
import org.jetbrains.skia.Image as SkiaImage

internal sealed interface DesktopPage {
    data class Local(val value: DesktopLocalPage) : DesktopPage
    data class Online(val value: Page, val source: Source) : DesktopPage
}

internal class DesktopPageLoader(private val localPages: DesktopLocalChapterPages) {
    suspend fun pages(source: Source, chapter: SChapter): List<DesktopPage> = withContext(Dispatchers.IO) {
        if (source is DesktopLocalSource) {
            localPages.pages(chapter.url).map(DesktopPage::Local)
        } else {
            source.getPageList(chapter).map { DesktopPage.Online(it, source) }
        }.also { require(it.isNotEmpty()) { "Chapter has no readable pages" } }
    }

    suspend fun image(page: DesktopPage): ImageBitmap = withContext(Dispatchers.IO) {
        val bytes = when (page) {
            is DesktopPage.Local -> page.value.readBytes()
            is DesktopPage.Online -> {
                val source = page.source
                if (source is HttpSource) {
                    if (page.value.imageUrl.isNullOrBlank()) {
                        page.value.imageUrl = source.getImageUrl(page.value)
                    }
                    source.getImage(page.value).use { response ->
                        response.body.byteStream().use { it.readNBytes(MAX_PAGE_BYTES + 1) }
                    }
                } else {
                    val url = page.value.imageUrl ?: page.value.url
                    val request = Request.Builder().url(url).build()
                    NetworkHelper.current().client.newCall(request).execute().use { response ->
                        require(response.isSuccessful) { "Page request failed: ${response.code}" }
                        response.body.byteStream().use { it.readNBytes(MAX_PAGE_BYTES + 1) }
                    }
                }
            }
        }
        require(bytes.size <= MAX_PAGE_BYTES) { "Page exceeds the size limit" }
        Data.makeFromBytes(bytes).use { data ->
            Codec.makeFromData(data).use { codec ->
                require(codec.width > 0 && codec.height > 0 && codec.width.toLong() * codec.height <= MAX_PAGE_PIXELS) {
                    "Page dimensions exceed the memory limit"
                }
            }
        }
        SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
    }

    private companion object {
        const val MAX_PAGE_BYTES = DesktopLocalPage.MAX_PAGE_BYTES
        const val MAX_PAGE_PIXELS = 20_000_000L
    }
}
