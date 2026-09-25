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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class DesktopBangumiTrackerTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `browser code login collection creation refresh and progress sync`() = runTest {
        assumeTrue(System.getProperty("os.name").startsWith("Windows", ignoreCase = true))
        val clock = AtomicInteger(1000)
        val created = AtomicBoolean()
        val refreshes = AtomicInteger()
        val updates = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val body = exchange.requestBody.readAllBytes().decodeToString()
            val auth = exchange.requestHeaders.getFirst("Authorization")
            val response = when {
                path == "/oauth/access_token" && "grant_type=authorization_code" in body ->
                    """{"access_token":"old-token","refresh_token":"old-refresh","created_at":1000,"expires_in":7200}"""
                path == "/oauth/access_token" && "grant_type=refresh_token" in body -> {
                    refreshes.incrementAndGet()
                    """{"access_token":"new-token","refresh_token":"new-refresh","created_at":10000,"expires_in":7200}"""
                }
                auth !in setOf("Bearer old-token", "Bearer new-token") -> {
                    exchange.sendResponseHeaders(401, -1)
                    exchange.close()
                    return@createContext
                }
                path == "/v0/me" -> """{"username":"fixture","nickname":"Fixture User"}"""
                path == "/v0/subjects/42" ->
                    """{"id":42,"name":"Fixture manga","name_cn":"","platform":"漫画"}"""
                path == "/v0/users/fixture/collections/42" && !created.get() -> {
                    exchange.sendResponseHeaders(404, -1)
                    exchange.close()
                    return@createContext
                }
                path == "/v0/users/fixture/collections/42" ->
                    """{"type":1,"rate":0,"ep_status":0,"subject":{"eps":2}}"""
                path == "/v0/users/-/collections/42" && exchange.requestMethod == "POST" -> {
                    assertTrue("\"type\":1" in body)
                    created.set(true)
                    exchange.sendResponseHeaders(202, -1)
                    exchange.close()
                    return@createContext
                }
                path == "/v0/users/-/collections/42" && exchange.requestMethod == "PATCH" -> {
                    updates.incrementAndGet()
                    assertTrue("\"ep_status\":" in body)
                    exchange.sendResponseHeaders(204, -1)
                    exchange.close()
                    return@createContext
                }
                else -> error("Unexpected Bangumi request: $path")
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
                val tracker = DesktopBangumiTracker(OkHttpClient(), library, DesktopSecretStore(tempDir), base, base) {
                    clock.get().toLong()
                }
                assertTrue(tracker.beginLogin().contains("response_type=code"))
                assertThrows(IllegalArgumentException::class.java) {
                    kotlinx.coroutines.runBlocking {
                        tracker.loginFromCallback("https://attacker.test/?code=fixture-code")
                    }
                }
                assertEquals("Fixture User", tracker.loginFromCallback("mihon://bangumi-auth?code=fixture-code"))
                assertFalse(
                    Files.readAllBytes(tempDir.resolve("bangumi-session.dpapi"))
                        .decodeToString().contains("old-token"),
                )
                tracker.bind(manga._id, 42)
                clock.set(10000)
                tracker.syncCompletedChapter(manga._id, 1.0)
                tracker.syncCompletedChapter(manga._id, 2.0)
                assertEquals(1, refreshes.get())
                assertEquals(2, updates.get())
                assertEquals(2L, library.track(manga._id, DesktopBangumiTracker.TRACKER_ID)?.status)
                tracker.logout()
                assertFalse(tracker.isLoggedIn)
            }
        } finally {
            server.stop(0)
        }
    }
}
