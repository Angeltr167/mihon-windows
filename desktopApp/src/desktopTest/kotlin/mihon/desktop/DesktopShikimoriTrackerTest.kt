package mihon.desktop

import com.sun.net.httpserver.HttpServer
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.test.runTest
import mihon.desktop.data.DesktopMangaRepository
import mihon.platform.desktop.DesktopSecretStore
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class DesktopShikimoriTrackerTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `callback login binding refresh and progress sync use protected session`() = runTest {
        assumeTrue(System.getProperty("os.name").startsWith("Windows", ignoreCase = true))
        val clock = AtomicInteger(1000)
        val refreshes = AtomicInteger()
        val updates = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val body = exchange.requestBody.readAllBytes().decodeToString()
            val authorization = exchange.requestHeaders.getFirst("Authorization")
            val response = when {
                path == "/oauth/token" && "grant_type=authorization_code" in body ->
                    """{"access_token":"old-token","refresh_token":"old-refresh","created_at":1000,"expires_in":7200}"""
                path == "/oauth/token" && "grant_type=refresh_token" in body -> {
                    refreshes.incrementAndGet()
                    """{"access_token":"new-token","refresh_token":"new-refresh","created_at":10000,"expires_in":7200}"""
                }
                authorization !in setOf("Bearer old-token", "Bearer new-token") -> {
                    exchange.sendResponseHeaders(401, -1)
                    exchange.close()
                    return@createContext
                }
                path == "/api/graphql" && "currentUser" in body ->
                    """{"data":{"currentUser":{"id":"17","nickname":"Fixture User"}}}"""
                path == "/api/graphql" && "mangas(" in body ->
                    """{"data":{"mangas":[{"id":"42","url":"https://shikimori.io/mangas/42","name":"Fixture manga","chapters":2,"userRate":null}]}}"""
                path == "/api/v2/user_rates" && exchange.requestMethod == "POST" -> {
                    assertTrue("\"target_id\":42" in body)
                    """{"id":75}"""
                }
                path == "/api/v2/user_rates/75" && exchange.requestMethod == "PUT" -> {
                    updates.incrementAndGet()
                    assertTrue("\"status\":\"watching\"" in body || "\"status\":\"completed\"" in body)
                    """{"id":75}"""
                }
                else -> error("Unexpected Shikimori request: $path")
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
                val tracker = DesktopShikimoriTracker(
                    OkHttpClient(),
                    library,
                    DesktopSecretStore(tempDir),
                    "http://127.0.0.1:${server.address.port}",
                ) { clock.get().toLong() }
                assertTrue(tracker.beginLogin().contains("response_type=code"))
                assertThrows(IllegalArgumentException::class.java) {
                    kotlinx.coroutines.runBlocking {
                        tracker.loginFromCallback("https://attacker.test/?code=fixture-code")
                    }
                }
                assertEquals("Fixture User", tracker.loginFromCallback("mihon://shikimori-auth?code=fixture-code"))
                assertFalse(
                    Files.readAllBytes(
                        tempDir.resolve("shikimori-session.dpapi"),
                    ).decodeToString().contains("old-token"),
                )
                tracker.bind(manga._id, 42)
                assertEquals(75L, library.track(manga._id, DesktopShikimoriTracker.TRACKER_ID)?.library_id)
                clock.set(10000)
                tracker.syncCompletedChapter(manga._id, 1.0)
                tracker.syncCompletedChapter(manga._id, 2.0)
                assertEquals(1, refreshes.get())
                assertEquals(2, updates.get())
                assertEquals(2L, library.track(manga._id, DesktopShikimoriTracker.TRACKER_ID)?.status)
                tracker.logout()
                assertFalse(tracker.isLoggedIn)
            }
        } finally {
            server.stop(0)
        }
    }
}
