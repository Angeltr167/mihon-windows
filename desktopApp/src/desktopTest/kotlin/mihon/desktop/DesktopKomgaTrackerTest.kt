package mihon.desktop

import com.sun.net.httpserver.HttpServer
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.test.runTest
import mihon.desktop.data.DesktopMangaRepository
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class DesktopKomgaTrackerTest {
    @Test
    fun `binding and completed reading sync use the shared authenticated session`() = runTest {
        val progress = AtomicReference(0.0)
        val putCount = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v2/series/test-series/read-progress/tachiyomi") { exchange ->
            if (exchange.requestHeaders.getFirst("X-API-Key") != "test-key") {
                exchange.sendResponseHeaders(401, -1)
            } else if (exchange.requestMethod == "PUT") {
                val payload = exchange.requestBody.readAllBytes().decodeToString()
                progress.set(Regex(""""lastBookNumberSortRead":([0-9.]+)""").find(payload)!!.groupValues[1].toDouble())
                putCount.incrementAndGet()
                exchange.sendResponseHeaders(204, -1)
            } else {
                val body = """
                    {
                      "booksCount": 2,
                      "booksReadCount": ${progress.get().toInt()},
                      "booksUnreadCount": ${2 - progress.get().toInt()},
                      "lastReadContinuousNumberSort": ${progress.get()},
                      "maxNumberSort": 2.0
                    }
                """.trimIndent().encodeToByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            exchange.close()
        }
        server.start()
        try {
            DesktopMangaRepository.inMemory().use { library ->
                val manga = SManga.create().apply {
                    url = "/manga"
                    title = "Tracked manga"
                }
                val stored = library.addToLibrary(100L, manga)
                val client = OkHttpClient.Builder().addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header("X-API-Key", "test-key").build())
                }.build()
                val tracker = DesktopKomgaTracker(client, library)
                val url = "http://127.0.0.1:${server.address.port}/api/v1/series/test-series"
                tracker.bind(stored._id, manga.title, url)
                assertEquals(0.0, library.track(stored._id, DesktopKomgaTracker.TRACKER_ID)?.last_chapter_read)
                tracker.syncCompletedChapter(stored._id, 1.0)
                tracker.syncCompletedChapter(stored._id, 1.0)
                assertEquals(1, putCount.get())
                assertEquals(1.0, library.track(stored._id, DesktopKomgaTracker.TRACKER_ID)?.last_chapter_read)
                tracker.syncCompletedChapter(stored._id, 2.0)
                assertEquals(3L, library.track(stored._id, DesktopKomgaTracker.TRACKER_ID)?.status)
                assertEquals(2, putCount.get())
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `non-series URLs are rejected before a request`() = runTest {
        DesktopMangaRepository.inMemory().use { library ->
            val manga = library.addToLibrary(
                100L,
                SManga.create().apply {
                    url = "/x"
                    title = "x"
                },
            )
            val tracker = DesktopKomgaTracker(OkHttpClient(), library)
            assertThrows(IllegalArgumentException::class.java) {
                kotlinx.coroutines.runBlocking {
                    tracker.bind(manga._id, "x", "file:///C:/private")
                }
            }
        }
    }
}
