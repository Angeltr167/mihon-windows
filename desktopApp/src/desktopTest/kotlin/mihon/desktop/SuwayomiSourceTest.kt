package mihon.desktop

import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import mihon.platform.api.AppDirectories
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files

class SuwayomiSourceTest {
    @Test
    fun `catalog and source map into the existing manga and page contracts`() = runTest {
        val calls = mutableListOf<JsonObject>()
        val client = SuwayomiClient(URI("http://127.0.0.1:49100")) { url, body ->
            assertEquals("http://127.0.0.1:49100/api/graphql", url)
            calls += body
            val query = body.requiredString("query")
            val data = when {
                "addExtensionStore" in query ->
                    """{"addExtensionStore":{
                    "extensionStore":{"indexUrl":"https://example.org/index.pb"}
                }}"""
                "fetchExtensions" in query ->
                    """{"fetchExtensions":{"extensions":[{
                    "pkgName":"example.ext","name":"Example","versionName":"1","versionCodeLong":"1",
                    "contentWarning":"SAFE","isInstalled":false,"hasUpdate":false,"isObsolete":false
                }]}}"""
                "updateExtension" in query ->
                    """{"updateExtension":{
                    "extension":{"pkgName":"example.ext","isInstalled":true}
                }}"""
                "sources" in query ->
                    """{"sources":{"nodes":[{
                    "id":"123","name":"Example source","lang":"en","supportsLatest":true,
                    "homeUrl":"https://example.org","extension":{"pkgName":"example.ext"}
                }]}}"""
                "fetchSourceManga" in query ->
                    """{"fetchSourceManga":{
                    "mangas":[{"id":7,"url":"/manga/one","title":"One",
                    "thumbnailUrl":"/api/v1/manga/7/thumbnail","status":"ONGOING"}],
                    "hasNextPage":false
                }}"""
                "mangas(condition:" in query -> """{"mangas":{"nodes":[{"id":7}]}}"""
                "fetchMangaAndChapters" in query ->
                    """{"fetchMangaAndChapters":{
                    "manga":{"id":7,"url":"/manga/one","title":"One",
                    "thumbnailUrl":"/api/v1/manga/7/thumbnail","description":"Details","status":"ONGOING"},
                    "chapters":[{"id":9,"mangaId":7,"url":"/chapter/one","name":"Chapter 1",
                    "chapterNumber":1,"uploadDate":0}]
                }}"""
                "fetchChapterPages" in query ->
                    """{"fetchChapterPages":{
                    "pages":["/api/v1/manga/7/chapter/9/page/0"]
                }}"""
                else -> error("Unexpected query: $query")
            }
            Json.parseToJsonElement("""{"data":$data}""").jsonObject
        }

        client.addStore("https://example.org/index.pb")
        assertEquals("Example", client.refreshExtensions().single().name)
        client.setInstalled("example.ext", "install")
        val source = SuwayomiSource(client.sources().single(), client)
        val manga = source.getPopularManga(1).mangas.single()
        assertEquals("/manga/one", manga.url)
        assertEquals("/api/v1/manga/7/thumbnail", manga.thumbnail_url)
        assertEquals(
            "http://127.0.0.1:49100/api/v1/manga/7/thumbnail",
            source.coverUrl(manga.thumbnail_url!!),
        )
        assertEquals(
            "http://127.0.0.1:49100/api/v1/manga/7/thumbnail",
            source.coverUrl("http://127.0.0.1:45678/api/v1/manga/7/thumbnail"),
        )
        val update = source.getMangaUpdate(manga, emptyList(), true, true)
        assertEquals("Details", update.manga.description)
        assertEquals("/chapter/one", update.chapters.single().url)
        assertEquals(
            "http://127.0.0.1:49100/api/v1/manga/7/chapter/9/page/0",
            source.getPageList(update.chapters.single()).single().imageUrl,
        )
        assertEquals(
            "123",
            calls.first { "fetchSourceManga" in it.requiredString("query") }
                .requiredObject("variables").requiredObject("input").requiredString("source"),
        )
        assertTrue(calls.size >= 7)
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "MIHON_SUWAYOMI_BASE_URL", matches = ".+")
    fun `installed Keiyoushi extension serves readable images through Mihon source`() = runTest {
        val client = SuwayomiClient(URI(System.getenv("MIHON_SUWAYOMI_BASE_URL")))
        val info = client.sources().first {
            it.packageName == "eu.kanade.tachiyomi.extension.all.mangadex" && it.lang == "es"
        }
        val source = SuwayomiSource(info, client)
        val manga = source.getPopularManga(1).mangas.first()
        val update = source.getMangaUpdate(manga, emptyList(), true, true)
        assertTrue(update.chapters.isNotEmpty())
        val page = source.getPageList(update.chapters.first()).first()
        val request = HttpRequest.newBuilder(URI(page.imageUrl)).GET().build()
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray())
        assertEquals(200, response.statusCode())
        assertTrue(response.body().size > 100)
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "MIHON_SUWAYOMI_JAR", matches = ".+")
    fun `local engine starts and stops as an owned process`() {
        val root = Files.createTempDirectory("mihon-suwayomi-engine")
        val path = System.getenv("MIHON_SUWAYOMI_JAR")
        val directories = AppDirectories(
            config = root.toString(),
            data = root.toString(),
            cache = root.toString(),
            database = root.toString(),
            downloads = root.toString(),
            localLibrary = root.toString(),
            extensions = root.toString(),
            temp = root.toString(),
        )
        System.setProperty("mihon.suwayomi.jar", path)
        System.setProperty("mihon.suwayomi.kcefEnabled", "false")
        try {
            LocalSuwayomiEngine(directories).use { engine ->
                engine.start()
                assertTrue(engine.isRunning)
                assertTrue(engine.client().isReady())
                engine.close()
                engine.start()
                assertTrue(engine.client().isReady())
            }
        } finally {
            System.clearProperty("mihon.suwayomi.jar")
            System.clearProperty("mihon.suwayomi.kcefEnabled")
        }
    }
}
