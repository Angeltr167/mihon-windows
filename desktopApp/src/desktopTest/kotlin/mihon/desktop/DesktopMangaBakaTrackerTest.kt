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
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class DesktopMangaBakaTrackerTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `PKCE callback state collection creation refresh and reading sync`() = runTest {
        assumeTrue(System.getProperty("os.name").startsWith("Windows", ignoreCase = true))
        val clock = AtomicInteger(1000)
        val refreshes = AtomicInteger()
        val updates = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val body = exchange.requestBody.readAllBytes().decodeToString()
            val auth = exchange.requestHeaders.getFirst("Authorization")
            val response = when {
                path == "/auth/oauth2/token" && "grant_type=authorization_code" in body -> {
                    assertTrue("code_verifier=" in body)
                    """{"access_token":"old-token","refresh_token":"old-refresh","expires_at":8000}"""
                }
                path == "/auth/oauth2/token" && "grant_type=refresh_token" in body -> {
                    refreshes.incrementAndGet()
                    """{"access_token":"new-token","refresh_token":"new-refresh","expires_at":20000}"""
                }
                auth !in setOf("Bearer old-token", "Bearer new-token") -> {
                    exchange.sendResponseHeaders(401, -1)
                    exchange.close()
                    return@createContext
                }
                path == "/v1/my/profile" ->
                    """{"data":{"id":"17","nickname":"Fixture User","rating_steps":10}}"""
                path == "/v1/series/42" ->
                    """{"data":{"id":42,"titles":[{"title":"Fixture manga"}]}}"""
                path == "/v1/my/library/42" && exchange.requestMethod == "GET" -> {
                    exchange.sendResponseHeaders(404, -1)
                    exchange.close()
                    return@createContext
                }
                path == "/v1/my/library/42" && exchange.requestMethod == "POST" -> {
                    assertTrue("\"state\":\"plan_to_read\"" in body)
                    """{"status":201,"data":true}"""
                }
                path == "/v1/my/library/42" && exchange.requestMethod == "PUT" -> {
                    updates.incrementAndGet()
                    assertTrue("\"progress_chapter\":" in body)
                    """{"status":200,"data":true}"""
                }
                else -> error("Unexpected MangaBaka request: $path")
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
                val base = "http://127.0.0.1:${server.address.port}"
                val tracker =
                    DesktopMangaBakaTracker(OkHttpClient(), library, DesktopSecretStore(tempDir), base, base) {
                        clock.get().toLong()
                    }
                val authUrl = URI(tracker.beginLogin())
                assertTrue(authUrl.rawQuery.contains("code_challenge_method=S256"))
                val state = authUrl.rawQuery.substringAfter("state=").substringBefore('&')
                assertThrows(IllegalArgumentException::class.java) {
                    kotlinx.coroutines.runBlocking {
                        tracker.loginFromCallback("mihon://mangabaka-auth?code=fixture-code&state=${"x".repeat(22)}")
                    }
                }
                assertEquals(
                    "Fixture User",
                    tracker.loginFromCallback("mihon://mangabaka-auth?code=fixture-code&state=$state"),
                )
                assertFalse(
                    Files.readAllBytes(tempDir.resolve("mangabaka-session.dpapi"))
                        .decodeToString().contains("old-token"),
                )
                tracker.bind(manga._id, 42)
                clock.set(10000)
                tracker.syncCompletedChapter(manga._id, 1.0)
                assertEquals(1, refreshes.get())
                assertEquals(1, updates.get())
                assertEquals(1L, library.track(manga._id, DesktopMangaBakaTracker.TRACKER_ID)?.status)
                tracker.logout()
                assertFalse(tracker.isLoggedIn)
            }
        } finally {
            server.stop(0)
        }
    }
}
