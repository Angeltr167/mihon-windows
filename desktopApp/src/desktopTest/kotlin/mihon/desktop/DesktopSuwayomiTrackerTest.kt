package mihon.desktop

import com.sun.net.httpserver.HttpServer
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.test.runTest
import mihon.desktop.data.DesktopMangaRepository
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

class DesktopSuwayomiTrackerTest {
    @Test
    fun `source authenticated binding and reading mark only completed chapters`() = runTest {
        val readCount = AtomicInteger()
        val marked = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/graphql") { exchange ->
            val request = exchange.requestBody.readAllBytes().decodeToString()
            val authorized = exchange.requestHeaders.getFirst("X-Source-Session") == "fixture-key"
            val response = when {
                !authorized -> """{"errors":[{"message":"unauthorized"}]}"""
                "updateChapters" in request -> {
                    if ("\"chapters\":[10]" in request) {
                        readCount.set(1)
                        marked.incrementAndGet()
                    } else if ("\"chapters\":[10,11]" in request) {
                        readCount.set(2)
                        marked.incrementAndGet()
                    }
                    """{"data":{"updateChapters":{"__typename":"UpdateChaptersPayload"}}}"""
                }
                "trackProgress" in request ->
                    """{"data":{"trackProgress":{"__typename":"TrackProgressPayload"}}}"""
                "chapters(condition" in request ->
                    """{"data":{"chapters":{"nodes":[{"id":10,"chapterNumber":1.0},{"id":11,"chapterNumber":2.0}]}}}"""
                "manga(id" in request ->
                    """{"data":{"manga":{"title":"Fixture manga","chapters":{"totalCount":2},"latestReadChapter":{"chapterNumber":${readCount.get().toDouble()}},"unreadCount":${2 - readCount.get()}}}}"""
                else -> error("Unexpected Suwayomi request")
            }.encodeToByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
            exchange.close()
        }
        server.start()
        try {
            DesktopMangaRepository.inMemory().use { library ->
                val manga = library.addToLibrary(
                    100L,
                    SManga.create().apply {
                        url = "/manga/42"
                        title = "Fixture manga"
                    },
                )
                val source = DesktopTrackerSourceSession(
                    "http://127.0.0.1:${server.address.port}",
                    OkHttpClient(),
                    Headers.Builder().add("X-Source-Session", "fixture-key").build(),
                )
                val tracker = DesktopSuwayomiTracker(library) { if (it == 100L) source else null }
                tracker.bind(manga._id, 100L, "/manga/42")
                assertEquals(42L, library.track(manga._id, DesktopSuwayomiTracker.TRACKER_ID)?.remote_id)
                tracker.syncCompletedChapter(manga._id, 1.0)
                tracker.syncCompletedChapter(manga._id, 1.0)
                assertEquals(1, marked.get())
                assertEquals(1.0, library.track(manga._id, DesktopSuwayomiTracker.TRACKER_ID)?.last_chapter_read)
                tracker.syncCompletedChapter(manga._id, 2.0)
                assertEquals(2, marked.get())
                assertEquals(3L, library.track(manga._id, DesktopSuwayomiTracker.TRACKER_ID)?.status)
            }
        } finally {
            server.stop(0)
        }
    }
}
