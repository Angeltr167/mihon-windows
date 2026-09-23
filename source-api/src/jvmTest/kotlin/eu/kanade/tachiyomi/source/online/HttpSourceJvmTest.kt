package eu.kanade.tachiyomi.source.online

import com.sun.net.httpserver.HttpServer
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

class HttpSourceJvmTest {
    @Test
    fun `desktop HTTP source executes through shared source API`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var userAgent = ""
        server.createContext("/manga") { exchange ->
            userAgent = exchange.requestHeaders.getFirst("User-Agent")
            val bytes = "Fixture manga".encodeToByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            NetworkHelper.install(OkHttpClient(), { "Mihon Desktop test" })
            @Suppress("DEPRECATION")
            val source = object : HttpSource() {
                override val name = "Fixture"
                override val lang = "en"
                override val supportsLatest = false
                override val baseUrl = "http://127.0.0.1:${server.address.port}"

                override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga", headers)
                override fun popularMangaParse(response: Response): MangasPage = MangasPage(
                    listOf(
                        SManga.create().apply {
                            title = response.body.string()
                            url = "/manga"
                        },
                    ),
                    false,
                )
            }
            assertEquals("Fixture manga", source.getPopularManga(1).mangas.single().title)
            assertEquals("Mihon Desktop test", userAgent)
        } finally {
            server.stop(0)
        }
    }
}
