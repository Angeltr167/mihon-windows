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

class DesktopKitsuTrackerTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `password grant library binding refresh and reading sync use protected tokens`() = runTest {
        assumeTrue(System.getProperty("os.name").startsWith("Windows", ignoreCase = true))
        val clock = AtomicInteger(1000)
        val refreshes = AtomicInteger()
        val updates = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/") { exchange ->
            val path = exchange.requestURI.path
            val body = exchange.requestBody.readAllBytes().decodeToString()
            val token = exchange.requestHeaders.getFirst("Authorization")
            val response = when {
                path == "/api/oauth/token" && "grant_type=password" in body ->
                    """{"access_token":"old-fixture-token","created_at":1000,"expires_in":7200,"refresh_token":"refresh-fixture-token"}"""
                path == "/api/oauth/token" && "grant_type=refresh_token" in body -> {
                    refreshes.incrementAndGet()
                    """{"access_token":"new-fixture-token","created_at":10000,"expires_in":7200,"refresh_token":"renewed-fixture-token"}"""
                }
                token !in setOf("Bearer old-fixture-token", "Bearer new-fixture-token") -> {
                    exchange.sendResponseHeaders(401, -1)
                    exchange.close()
                    return@createContext
                }
                "currentAccount" in body ->
                    """{"data":{"currentAccount":{"id":"17","profile":{"name":"Fixture User"}}}}"""
                "findMangaById" in body ->
                    """{"data":{"findMangaById":{"id":"42","titles":{"preferred":"Fixture manga"},"slug":"fixture-manga","chapterCount":2,"myLibraryEntry":null}}}"""
                "create(input" in body ->
                    """{"data":{"libraryEntry":{"create":{"errors":[],"libraryEntry":{"id":"123","progress":0,"status":"PLANNED"}}}}}"""
                "update(input" in body -> {
                    updates.incrementAndGet()
                    """{"data":{"libraryEntry":{"update":{"errors":[],"libraryEntry":{"id":"123"}}}}}"""
                }
                else -> error("Unexpected Kitsu request: $body")
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
                val tracker = DesktopKitsuTracker(
                    OkHttpClient(),
                    library,
                    DesktopSecretStore(tempDir),
                    "http://127.0.0.1:${server.address.port}",
                ) { clock.get().toLong() }
                assertEquals("Fixture User", tracker.login("fixture", "password"))
                assertTrue(tracker.isLoggedIn)
                assertFalse(
                    Files.readAllBytes(tempDir.resolve("kitsu-session.dpapi")).decodeToString()
                        .contains("old-fixture-token"),
                )
                tracker.bind(manga._id, 42)
                assertEquals(123L, library.track(manga._id, DesktopKitsuTracker.TRACKER_ID)?.library_id)
                clock.set(10000)
                tracker.syncCompletedChapter(manga._id, 1.0)
                tracker.syncCompletedChapter(manga._id, 2.0)
                assertEquals(1, refreshes.get())
                assertEquals(2, updates.get())
                assertEquals(2L, library.track(manga._id, DesktopKitsuTracker.TRACKER_ID)?.status)
                tracker.logout()
                assertFalse(tracker.isLoggedIn)
            }
        } finally {
            server.stop(0)
        }
    }
}
