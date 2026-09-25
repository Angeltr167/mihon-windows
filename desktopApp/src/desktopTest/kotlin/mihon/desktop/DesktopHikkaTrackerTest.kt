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

class DesktopHikkaTrackerTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `browser reference login binding token renewal and progress sync`() = runTest {
        assumeTrue(System.getProperty("os.name").startsWith("Windows", ignoreCase = true))
        val clock = AtomicInteger(1000)
        val renewals = AtomicInteger()
        val updates = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val body = exchange.requestBody.readAllBytes().decodeToString()
            val auth = exchange.requestHeaders.getFirst("auth")
            val response = when {
                path == "/auth/token" -> {
                    assertTrue("fixture-reference" in body)
                    """{"secret":"hikka-secret","created":1000,"expiration":8000}"""
                }
                auth != "hikka-secret" -> {
                    exchange.sendResponseHeaders(401, -1)
                    exchange.close()
                    return@createContext
                }
                path == "/user/me" -> {
                    if (clock.get() > 8000) renewals.incrementAndGet()
                    """{"reference":"user-1","username":"Fixture User"}"""
                }
                path == "/auth/token/info" ->
                    """{"reference":"token-1","created":10000,"expiration":20000}"""
                path == "/manga/fixture-slug" ->
                    """{"slug":"fixture-slug","title_original":"Fixture manga","chapters":2}"""
                path == "/read/manga/fixture-slug" && exchange.requestMethod == "GET" -> {
                    exchange.sendResponseHeaders(404, -1)
                    exchange.close()
                    return@createContext
                }
                path == "/read/manga/fixture-slug" && exchange.requestMethod == "PUT" -> {
                    updates.incrementAndGet()
                    val progress = Regex("\"chapters\":(\\d+)").find(body)?.groupValues?.get(1) ?: "0"
                    val status = if ("\"status\":\"completed\"" in body) {
                        "completed"
                    } else if (
                        "\"status\":\"reading\"" in body
                    ) {
                        "reading"
                    } else {
                        "planned"
                    }
                    """{"chapters":$progress,"status":"$status"}"""
                }
                else -> error("Unexpected Hikka request: $path")
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
                val tracker = DesktopHikkaTracker(OkHttpClient(), library, DesktopSecretStore(tempDir), base, base) {
                    clock.get().toLong()
                }
                assertTrue(tracker.beginLogin().contains("reference="))
                assertThrows(IllegalArgumentException::class.java) {
                    kotlinx.coroutines.runBlocking {
                        tracker.loginFromCallback("https://attacker.test/?reference=fixture-reference")
                    }
                }
                assertEquals(
                    "Fixture User",
                    tracker.loginFromCallback("mihon://hikka-auth?reference=fixture-reference"),
                )
                assertFalse(
                    Files.readAllBytes(tempDir.resolve("hikka-session.dpapi"))
                        .decodeToString().contains("hikka-secret"),
                )
                tracker.bind(manga._id, "fixture-slug")
                clock.set(10000)
                tracker.syncCompletedChapter(manga._id, 1.0)
                tracker.syncCompletedChapter(manga._id, 2.0)
                assertEquals(1, renewals.get())
                assertEquals(3, updates.get())
                assertEquals(1L, library.track(manga._id, DesktopHikkaTracker.TRACKER_ID)?.status)
                tracker.logout()
                assertFalse(tracker.isLoggedIn)
            }
        } finally {
            server.stop(0)
        }
    }
}
