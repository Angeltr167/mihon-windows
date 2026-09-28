package mihon.desktop

import com.sun.net.httpserver.HttpServer
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.source.local.desktop.DesktopLocalChapterPages
import tachiyomi.source.local.desktop.DesktopLocalSourceFileSystem
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger

class DesktopDownloadTest {
    @Test
    fun `authenticated download is readable with the server offline`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var requestedWithToken = 0
        server.createContext("/page") { exchange ->
            if (exchange.requestHeaders.getFirst("X-Download-Test") == "secret") requestedWithToken++
            exchange.sendResponseHeaders(200, PNG.size.toLong())
            exchange.responseBody.use { it.write(PNG) }
        }
        server.start()
        try {
            NetworkHelper.install(OkHttpClient(), { "Mihon download test" })
            val source = source("http://127.0.0.1:${server.address.port}")
            val manga = manga()
            val chapter = chapter()
            val store = DesktopDownloadStore(Files.createTempDirectory("mihon-downloads"))
            val local = DesktopLocalChapterPages(
                DesktopLocalSourceFileSystem(Files.createTempDirectory("mihon-download-local")),
            )
            val item = DesktopDownload(source.id, manga.url, manga.title, chapter.url, chapter.name)
            val abandoned = store.stageDirectory(item.key)
            Files.createDirectories(abandoned)
            Files.writeString(abandoned.resolve("page-00000.png"), "interrupted page")
            val corruptFinal = store.finalDirectory(item.key)
            Files.createDirectories(corruptFinal)
            Files.writeString(corruptFinal.resolve("complete"), "2")
            Files.writeString(corruptFinal.resolve("page-00000.png"), "damaged page")
            val progress = mutableListOf<Pair<Int, Int>>()
            DesktopDownloadEngine(store, DesktopPageFetcher(local)).download(item, source, manga, chapter) {
                    done,
                    total,
                ->
                progress += done to total
            }
            assertEquals(listOf(1 to 2, 2 to 2), progress)
            assertEquals(2, requestedWithToken)
            assertEquals(2, store.completedPages(source.id, manga.url, chapter.url)?.size)
            assertTrue(Files.notExists(abandoned))
            assertTrue(
                Files.list(corruptFinal.parent).use { files ->
                    files.anyMatch { it.fileName.toString().startsWith("${item.key}.corrupt-") }
                },
            )
            server.stop(0)
            val offlinePages = DesktopPageLoader(local, store).pages(DownloadedSource(source.id), chapter, manga.url)
            assertEquals(2, offlinePages.size)
            assertTrue(offlinePages.all { it is DesktopPage.Local })
            assertEquals(1, DesktopPageLoader(local, store).image(offlinePages.first()).width)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `queue pause resume cancel and running recovery persist`() = runBlocking {
        val store = DesktopDownloadStore(Files.createTempDirectory("mihon-download-queue"))
        val source = source("http://127.0.0.1")
        val manga = manga()
        val chapter = chapter()
        val item = DesktopDownload(source.id, manga.url, manga.title, chapter.url, chapter.name)
        val blockFirst = CompletableDeferred<Unit>()
        val attempts = AtomicInteger()
        val transfer = DesktopChapterTransfer { download, _, _, _, onProgress ->
            if (attempts.incrementAndGet() == 1) {
                onProgress(1, 2)
                blockFirst.await()
            }
            store.clearPartial(download.key)
            val stage = store.stageDirectory(download.key)
            Files.createDirectories(stage)
            Files.write(stage.resolve("page-00000.png"), PNG)
            onProgress(2, 2)
            store.commit(download.key, 1)
        }
        DesktopDownloadScheduler(store, transfer, { source }, networkAvailable = { true }).use { scheduler ->
            scheduler.enqueue(source, manga, chapter)
            withTimeout(5_000) { scheduler.queue.first { it.singleOrNull()?.pagesDone == 1 } }
            scheduler.pause(item.key)
            assertEquals(DesktopDownloadStatus.PAUSED, scheduler.queue.value.single().status)
            assertEquals(DesktopDownloadStatus.PAUSED, store.loadQueue().single().status)
            scheduler.resume(item.key)
            withTimeout(5_000) {
                scheduler.queue.first { it.singleOrNull()?.status == DesktopDownloadStatus.COMPLETED }
            }
            assertEquals(2, attempts.get())
            assertNotNull(store.completedPages(source.id, manga.url, chapter.url))
            scheduler.cancel(item.key)
            withTimeout(5_000) { scheduler.queue.first { it.isEmpty() } }
            assertTrue(store.loadQueue().isEmpty())
        }

        val interrupted = item.copy(status = DesktopDownloadStatus.RUNNING, pagesDone = 1, pageCount = 2)
        store.saveQueue(listOf(interrupted))
        DesktopDownloadScheduler(store, transfer, { source }, networkAvailable = { true }).use { scheduler ->
            withTimeout(5_000) {
                scheduler.queue.first { it.singleOrNull()?.status == DesktopDownloadStatus.COMPLETED }
            }
            assertTrue(store.isComplete(item))
        }
    }

    @Test
    fun `clear finished removes queue entries but keeps downloaded chapter`() = runBlocking {
        val store = DesktopDownloadStore(Files.createTempDirectory("mihon-download-clear-finished"))
        val source = source("http://127.0.0.1")
        val manga = manga()
        val chapter = chapter()
        val item = DesktopDownload(source.id, manga.url, manga.title, chapter.url, chapter.name)
        val stage = store.stageDirectory(item.key)
        Files.createDirectories(stage)
        Files.write(stage.resolve("page-00000.png"), PNG)
        store.commit(item.key, 1)
        store.saveQueue(listOf(item.copy(status = DesktopDownloadStatus.COMPLETED, pagesDone = 1, pageCount = 1)))

        DesktopDownloadScheduler(store, DesktopChapterTransfer { _, _, _, _, _ -> }, { source }).use { scheduler ->
            assertEquals(DesktopDownloadStatus.COMPLETED, scheduler.queue.value.single().status)
            scheduler.clearFinished()
            assertTrue(scheduler.queue.value.isEmpty())
            assertTrue(store.loadQueue().isEmpty())
            assertNotNull(store.completedPages(source.id, manga.url, chapter.url))
        }
    }

    @Test
    fun `cancel interrupts active transfer without committing a chapter`() = runBlocking {
        val store = DesktopDownloadStore(Files.createTempDirectory("mihon-download-cancel"))
        val source = source("http://127.0.0.1")
        val manga = manga()
        val chapter = chapter()
        val item = DesktopDownload(source.id, manga.url, manga.title, chapter.url, chapter.name)
        val started = CompletableDeferred<Unit>()
        val transfer = DesktopChapterTransfer { download, _, _, _, _ ->
            val stage = store.stageDirectory(download.key)
            Files.createDirectories(stage)
            Files.write(stage.resolve("page-00000.png"), PNG)
            started.complete(Unit)
            CompletableDeferred<Unit>().await()
            store.commit(download.key, 1)
        }
        DesktopDownloadScheduler(store, transfer, { source }, networkAvailable = { true }).use { scheduler ->
            scheduler.enqueue(source, manga, chapter)
            withTimeout(5_000) { started.await() }
            scheduler.cancel(item.key)
            withTimeout(5_000) {
                while (Files.exists(store.stageDirectory(item.key))) delay(10)
            }
            assertTrue(scheduler.queue.value.isEmpty())
            assertTrue(Files.notExists(store.finalDirectory(item.key)))
        }
    }

    private fun manga() = SManga.create().apply {
        url = "/manga"
        title = "Download fixture"
    }

    private fun chapter() = SChapter.create().apply {
        url = "/chapter"
        name = "Chapter 1"
    }

    private fun source(url: String) = object : HttpSource() {
        override val id = 12345L
        override val name = "Download source"
        override val lang = "en"
        override val supportsLatest = false
        override val baseUrl = url

        override fun headersBuilder(): Headers.Builder =
            super.headersBuilder().add("X-Download-Test", "secret")

        override suspend fun getPageList(chapter: SChapter): List<Page> =
            listOf(Page(0, imageUrl = "$baseUrl/page"), Page(1, imageUrl = "$baseUrl/page"))

        override fun popularMangaRequest(page: Int): Request = error("unused")
        override fun popularMangaParse(response: Response): MangasPage = error("unused")
    }

    private companion object {
        val PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/lXcAAAAASUVORK5CYII=",
        )
    }
}
