package mihon.desktop

import com.sun.net.httpserver.HttpServer
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.test.runTest
import mihon.desktop.data.DesktopMangaRepository
import mihon.platform.desktop.DesktopSecretStore
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class DesktopKavitaTrackerTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `plugin API key is protected and chapter progress survives reauthentication`() = runTest {
        assumeTrue(System.getProperty("os.name").startsWith("Windows", ignoreCase = true))
        val progress = AtomicInteger()
        val authCount = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/") { exchange ->
            val path = exchange.requestURI.path
            val authorized = exchange.requestHeaders.getFirst("Authorization") == "Bearer fixture-jwt"
            val body = when {
                path == "/api/Plugin/authenticate" &&
                    exchange.requestURI.rawQuery?.contains("apiKey=fixture-secret-key") == true -> {
                    authCount.incrementAndGet()
                    """{"username":"fixture","token":"fixture-jwt","apiKey":"fixture-secret-key"}"""
                }
                !authorized -> {
                    exchange.sendResponseHeaders(401, -1)
                    exchange.close()
                    return@createContext
                }
                path == "/api/Series/42" ->
                    """{"id":42,"name":"Fixture manga","pages":20,"pagesRead":${progress.get() * 10}}"""
                path == "/api/Series/volumes" ->
                    """[{"chapters":[{"number":"1"},{"number":"2"}]}]"""
                path == "/api/Tachiyomi/latest-chapter" && progress.get() == 0 -> {
                    exchange.sendResponseHeaders(204, -1)
                    exchange.close()
                    return@createContext
                }
                path == "/api/Tachiyomi/latest-chapter" ->
                    """{"number":"${progress.get()}"}"""
                path == "/api/Tachiyomi/mark-chapter-until-as-read" -> {
                    progress.set(if (exchange.requestURI.rawQuery?.contains("chapterNumber=2.0") == true) 2 else 1)
                    "{}"
                }
                else -> error("Unexpected Kavita request: $path")
            }.encodeToByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
            exchange.close()
        }
        server.start()
        try {
            DesktopMangaRepository.inMemory().use { library ->
                val manga = library.addToLibrary(
                    100L,
                    SManga.create().apply {
                        url = "/api/Series/42"
                        title = "Fixture manga"
                    },
                )
                val tracker = DesktopKavitaTracker(OkHttpClient(), library, DesktopSecretStore(tempDir))
                val url = "http://127.0.0.1:${server.address.port}/api/Series/42"
                tracker.bind(manga._id, url, "fixture-secret-key")
                assertEquals(42L, library.track(manga._id, DesktopKavitaTracker.TRACKER_ID)?.remote_id)
                assertFalse(
                    Files.list(tempDir).use { stream ->
                        stream.anyMatch { Files.readAllBytes(it).decodeToString().contains("fixture-secret-key") }
                    },
                )
                tracker.syncCompletedChapter(manga._id, 1.0)
                tracker.syncCompletedChapter(manga._id, 2.0)
                assertEquals(2, progress.get())
                assertEquals(3L, library.track(manga._id, DesktopKavitaTracker.TRACKER_ID)?.status)
                assertEquals(3, authCount.get())
            }
        } finally {
            server.stop(0)
        }
    }
}
