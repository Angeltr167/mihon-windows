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

class DesktopAniListTrackerTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `callback login binding and read progress use protected credentials`() = runTest {
        assumeTrue(System.getProperty("os.name").startsWith("Windows", ignoreCase = true))
        val token = "fixture-anilist-access-token-12345"
        val progress = AtomicInteger()
        val mutations = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/graphql") { exchange ->
            val request = exchange.requestBody.readAllBytes().decodeToString()
            val authorized = exchange.requestHeaders.getFirst("Authorization") == "Bearer $token"
            val response = when {
                !authorized -> """{"errors":[{"message":"unauthorized"}]}"""
                "Viewer" in request -> """{"data":{"Viewer":{"id":17,"name":"Fixture User"}}}"""
                "SaveMediaListEntry" in request -> {
                    val next = Regex("\"progress\":(\\d+)").find(request)?.groupValues?.get(1)?.toInt()
                        ?: error("Missing progress")
                    progress.set(next)
                    mutations.incrementAndGet()
                    """{"data":{"SaveMediaListEntry":{"id":123,"progress":$next,"status":"CURRENT"}}}"""
                }
                "Media(" in request ->
                    """{"data":{"Media":{"id":42,"title":{"userPreferred":"Fixture manga"},"chapters":2,"mediaListEntry":null}}}"""
                else -> error("Unexpected GraphQL request")
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
                val secrets = DesktopSecretStore(tempDir)
                val endpoint = "http://127.0.0.1:${server.address.port}/graphql"
                val tracker = DesktopAniListTracker(OkHttpClient(), library, secrets, endpoint)
                assertEquals("Fixture User", tracker.loginFromCallback("mihon://anilist-auth#access_token=$token"))
                assertFalse(Files.readAllBytes(tempDir.resolve("anilist-token.dpapi")).decodeToString().contains(token))
                assertTrue(
                    DesktopAniListTracker(OkHttpClient(), library, DesktopSecretStore(tempDir), endpoint).isLoggedIn,
                )
                tracker.bind(manga._id, 42)
                assertEquals(42L, library.track(manga._id, DesktopAniListTracker.TRACKER_ID)?.remote_id)
                tracker.syncCompletedChapter(manga._id, 1.0)
                tracker.syncCompletedChapter(manga._id, 1.0)
                assertEquals(1, mutations.get())
                assertEquals(1.0, library.track(manga._id, DesktopAniListTracker.TRACKER_ID)?.last_chapter_read)
                tracker.syncCompletedChapter(manga._id, 2.0)
                assertEquals(2, progress.get())
                assertEquals(2L, library.track(manga._id, DesktopAniListTracker.TRACKER_ID)?.status)
                tracker.logout()
                assertFalse(tracker.isLoggedIn)
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `unrelated callback cannot store a token`() {
        val tracker =
            DesktopAniListTracker(OkHttpClient(), DesktopMangaRepository.inMemory(), DesktopSecretStore(tempDir))
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { tracker.loginFromCallback("file:///secret#access_token=fake") }
        }
        assertFalse(Files.exists(tempDir.resolve("anilist-token.dpapi")))
    }
}
