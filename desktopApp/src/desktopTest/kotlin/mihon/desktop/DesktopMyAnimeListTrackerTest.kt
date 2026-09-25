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

class DesktopMyAnimeListTrackerTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `PKCE callback state token refresh and reading sync survive protected storage`() = runTest {
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
                path == "/v1/oauth2/token" && "grant_type=authorization_code" in body ->
                    """{"access_token":"old-mal-token","refresh_token":"old-refresh-token","expires_in":7200}"""
                path == "/v1/oauth2/token" && "grant_type=refresh_token" in body -> {
                    refreshes.incrementAndGet()
                    """{"access_token":"new-mal-token","refresh_token":"new-refresh-token","expires_in":7200}"""
                }
                authorization !in setOf("Bearer old-mal-token", "Bearer new-mal-token") -> {
                    exchange.sendResponseHeaders(401, -1)
                    exchange.close()
                    return@createContext
                }
                path == "/v2/users/@me" -> """{"name":"Fixture User"}"""
                path == "/v2/manga/42" ->
                    """{"id":42,"title":"Fixture manga","num_chapters":2,"my_list_status":null}"""
                path == "/v2/manga/42/my_list_status" -> {
                    updates.incrementAndGet()
                    val progress = Regex("num_chapters_read=(\\d+)").find(body)?.groupValues?.get(1)?.toInt() ?: 0
                    val status = when {
                        "status=completed" in body -> "completed"
                        "status=reading" in body -> "reading"
                        else -> "plan_to_read"
                    }
                    """{"is_rereading":false,"status":"$status","num_chapters_read":$progress,"score":0}"""
                }
                else -> error("Unexpected MyAnimeList request: $path")
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
                val tracker = DesktopMyAnimeListTracker(
                    OkHttpClient(),
                    library,
                    DesktopSecretStore(tempDir),
                    "$base/v1/oauth2",
                    "$base/v2",
                ) { clock.get().toLong() }
                val authUrl = URI(tracker.beginLogin())
                assertTrue(authUrl.rawQuery.contains("code_challenge="))
                val state = authUrl.rawQuery.substringAfter("state=").substringBefore('&')
                assertThrows(IllegalArgumentException::class.java) {
                    kotlinx.coroutines.runBlocking {
                        tracker.loginFromCallback("mihon://myanimelist-auth?code=fixture-code&state=${"x".repeat(64)}")
                    }
                }
                assertEquals(
                    "Fixture User",
                    tracker.loginFromCallback("mihon://myanimelist-auth?code=fixture-code&state=$state"),
                )
                assertFalse(
                    Files.readAllBytes(tempDir.resolve("myanimelist-session.dpapi")).decodeToString()
                        .contains("old-mal-token"),
                )
                tracker.bind(manga._id, 42)
                clock.set(10000)
                tracker.syncCompletedChapter(manga._id, 1.0)
                tracker.syncCompletedChapter(manga._id, 2.0)
                assertEquals(1, refreshes.get())
                assertEquals(3, updates.get())
                assertEquals(2L, library.track(manga._id, DesktopMyAnimeListTracker.TRACKER_ID)?.status)
                tracker.logout()
                assertFalse(tracker.isLoggedIn)
            }
        } finally {
            server.stop(0)
        }
    }
}
