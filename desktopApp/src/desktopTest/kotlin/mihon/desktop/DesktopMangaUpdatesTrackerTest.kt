package mihon.desktop

import com.sun.net.httpserver.HttpServer
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.test.runTest
import mihon.desktop.data.DesktopMangaRepository
import mihon.platform.desktop.DesktopSecretStore
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class DesktopMangaUpdatesTrackerTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `credential login binding and completed chapters use existing list API`() = runTest {
        assumeTrue(System.getProperty("os.name").startsWith("Windows", ignoreCase = true))
        val token = "fixture-session-token-12345"
        val list = AtomicInteger(-1)
        val progress = AtomicInteger()
        val updates = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/") { exchange ->
            val path = exchange.requestURI.path
            val body = exchange.requestBody.readAllBytes().decodeToString()
            val authorized = exchange.requestHeaders.getFirst("Authorization") == "Bearer $token"
            val response = when {
                path == "/v1/account/login" &&
                    "\"username\":\"fixture\"" in body && "\"password\":\"password\"" in body ->
                    """{"context":{"session_token":"$token","uid":17}}"""
                path == "/v1/series/42" ->
                    """{"series_id":42,"title":"Fixture manga","url":"https://www.mangaupdates.com/series/42"}"""
                !authorized -> {
                    exchange.sendResponseHeaders(401, -1)
                    exchange.close()
                    return@createContext
                }
                path == "/v1/account/profile" -> """{"username":"Fixture User"}"""
                path == "/v1/lists/series/42" && list.get() < 0 -> {
                    exchange.sendResponseHeaders(404, -1)
                    exchange.close()
                    return@createContext
                }
                path == "/v1/lists/series/42" ->
                    """{"list_id":${list.get()},"status":{"chapter":${progress.get()}}}"""
                path == "/v1/lists/series" -> {
                    list.set(1)
                    "[]"
                }
                path == "/v1/lists/series/update" -> {
                    list.set(0)
                    progress.set(Regex("\"chapter\":(\\d+)").find(body)?.groupValues?.get(1)?.toInt() ?: -1)
                    updates.incrementAndGet()
                    "[]"
                }
                else -> error("Unexpected MangaUpdates request: $path")
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
                        url = "/fixture"
                        title = "Fixture manga"
                    },
                )
                val tracker = DesktopMangaUpdatesTracker(
                    OkHttpClient(),
                    library,
                    DesktopSecretStore(tempDir),
                    "http://127.0.0.1:${server.address.port}",
                )
                assertEquals("Fixture User", tracker.login("fixture", "password"))
                assertTrue(tracker.isLoggedIn)
                assertFalse(
                    Files.readAllBytes(tempDir.resolve("mangaupdates-token.dpapi")).decodeToString().contains(token),
                )
                tracker.bind(manga._id, 42)
                assertEquals(1L, library.track(manga._id, DesktopMangaUpdatesTracker.TRACKER_ID)?.status)
                tracker.syncCompletedChapter(manga._id, 1.0)
                tracker.syncCompletedChapter(manga._id, 1.0)
                assertEquals(1, updates.get())
                assertEquals(1.0, library.track(manga._id, DesktopMangaUpdatesTracker.TRACKER_ID)?.last_chapter_read)
                assertEquals(0L, library.track(manga._id, DesktopMangaUpdatesTracker.TRACKER_ID)?.status)
                tracker.logout()
                assertFalse(tracker.isLoggedIn)
            }
        } finally {
            server.stop(0)
        }
    }
}
