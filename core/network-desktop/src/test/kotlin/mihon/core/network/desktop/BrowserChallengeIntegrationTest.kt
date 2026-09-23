package mihon.core.network.desktop

import com.sun.net.httpserver.HttpServer
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.coroutines.runBlocking
import mihon.core.network.NetworkClientFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files

class BrowserChallengeIntegrationTest {
    @Test
    fun `representative source completes a javascript challenge and reuses clearance cookie`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/source") { exchange ->
            val hasClearance = exchange.requestHeaders.getFirst("Cookie")
                .orEmpty()
                .contains("cf_clearance=passed")
            if (hasClearance) {
                val payload = "Mihon Challenge Source".toByteArray()
                exchange.sendResponseHeaders(200, payload.size.toLong())
                exchange.responseBody.use { it.write(payload) }
            } else {
                val payload = """
                    <!doctype html>
                    <html><body>
                    <script>
                      document.cookie = "cf_clearance=passed; path=/";
                      setTimeout(() => location.reload(), 25);
                    </script>
                    </body></html>
                """.trimIndent().toByteArray()
                exchange.responseHeaders.add("cf-mitigated", "challenge")
                exchange.responseHeaders.add("Server", "cloudflare")
                exchange.responseHeaders.add("Content-Type", "text/html")
                exchange.sendResponseHeaders(403, payload.size.toLong())
                exchange.responseBody.use { it.write(payload) }
            }
        }
        server.start()

        try {
            val root = Files.createTempDirectory("mihon-browser-challenge")
            val store = DesktopCookieStore(root.resolve("cookies.json"))
            val client = NetworkClientFactory(
                cookieStore = store,
                cacheDirectory = root.resolve("cache").toFile(),
                userAgentProvider = { "Mihon-Windows-Challenge-Test" },
                challengeSolver = PlaywrightChallengeSolver(),
            ).create()
            val endpoint = "http://localhost:${server.address.port}/source"
            val source = RepresentativeHttpSource(client, endpoint)

            val page = source.getPopularManga(1)

            assertEquals("Mihon Challenge Source", page.mangas.single().title)
            assertTrue(
                store.loadForRequest(Request.Builder().url(endpoint).build().url).any {
                    it.name == "cf_clearance"
                },
            )
            assertTrue(Files.size(root.resolve("cookies.json")) > 0L)
        } finally {
            server.stop(0)
        }
    }

    private class RepresentativeHttpSource(
        private val client: OkHttpClient,
        private val endpoint: String,
    ) : Source {
        override val id = 5L
        override val name = "Challenge fixture"
        override val supportsLatest = false

        override suspend fun getPopularManga(page: Int): MangasPage {
            val title = client.newCall(Request.Builder().url(endpoint).build()).execute().use { response ->
                check(response.isSuccessful) { "Challenge request failed with ${response.code}" }
                response.body.string()
            }
            return MangasPage(
                mangas = listOf(SManga.create().apply { this.title = title }),
                hasNextPage = false,
            )
        }

        override suspend fun getLatestUpdates(page: Int): MangasPage = error("not used")

        override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage = error(
            "not used",
        )

        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): SMangaUpdate = error("not used")

        override suspend fun getPageList(chapter: SChapter): List<Page> = error("not used")
    }
}
