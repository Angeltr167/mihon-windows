package mihon.desktop

import com.sun.net.httpserver.HttpServer
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import tachiyomi.source.local.desktop.DesktopLocalChapterPages
import tachiyomi.source.local.desktop.DesktopLocalPage
import tachiyomi.source.local.desktop.DesktopLocalSourceFileSystem
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger

class DesktopPageLoaderTest {
    @Test
    fun `high resolution pages are bounded before decoding`() {
        DesktopPageLoader.checkDimensions(4000, 5000)
        assertEquals(160_000_000L, DesktopPageLoader.MAX_CACHED_DECODED_BYTES)
        assertThrows(IllegalArgumentException::class.java) {
            DesktopPageLoader.checkDimensions(5000, 5000)
        }
    }

    @Test
    fun `long chapter keeps only two decoded pages`() = runTest {
        val root = Files.createTempDirectory("mihon-webtoon-stress")
        val loader = DesktopPageLoader(DesktopLocalChapterPages(DesktopLocalSourceFileSystem(root)))
        repeat(1000) { index ->
            val path = root.resolve("page-$index.png")
            Files.write(path, PNG)
            loader.image(DesktopPage.Local(DesktopLocalPage(path, null)))
            assertEquals(minOf(index + 1, 2), loader.cachedPageCount())
        }
    }

    @Test
    fun `online page uses source request headers`() = runTest {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var token = ""
        val requests = AtomicInteger()
        server.createContext("/page") { exchange ->
            requests.incrementAndGet()
            token = exchange.requestHeaders.getFirst("X-Reader-Test")
            exchange.responseHeaders.add("Content-Type", "image/png")
            exchange.sendResponseHeaders(200, PNG.size.toLong())
            exchange.responseBody.use { it.write(PNG) }
        }
        server.start()
        try {
            NetworkHelper.install(OkHttpClient(), { "Mihon reader test" })
            val source = object : HttpSource() {
                override val name = "Reader fixture"
                override val lang = "en"
                override val supportsLatest = false
                override val baseUrl = "http://127.0.0.1:${server.address.port}"

                override fun headersBuilder(): Headers.Builder =
                    super.headersBuilder().add("X-Reader-Test", "secret")

                override suspend fun getPageList(chapter: SChapter): List<Page> =
                    listOf(Page(0, imageUrl = "$baseUrl/page"))

                override fun popularMangaRequest(page: Int): Request = error("unused")
                override fun popularMangaParse(response: Response): MangasPage = error("unused")
            }
            val local = DesktopLocalChapterPages(DesktopLocalSourceFileSystem(Files.createTempDirectory("mihon-pages")))
            val loader = DesktopPageLoader(local)
            val pages = loader.pages(source, SChapter.create())
            assertEquals(1, pages.size)
            loader.prefetch(pages.single())
            assertEquals(1, loader.image(pages.single()).width)
            assertEquals("secret", token)
            assertEquals(1, requests.get())
        } finally {
            server.stop(0)
        }
    }

    private companion object {
        val PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/lXcAAAAASUVORK5CYII=",
        )
    }
}
