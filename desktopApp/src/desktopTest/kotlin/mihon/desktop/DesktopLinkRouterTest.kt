package mihon.desktop

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.ResolvableSource
import eu.kanade.tachiyomi.source.online.UriType
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesktopLinkRouterTest {
    private val router = DesktopLinkRouter()

    @Test
    fun `repository links are accepted only for safe HTTPS destinations`() = runTest {
        val valid = router.resolve("mihon://extension-store?url=https%3A%2F%2Fexample.org%2Findex.json", emptyList())
        assertEquals("https://example.org/index.json", (valid as DesktopLinkTarget.ExtensionRepository).url.toString())
        assertNull(router.resolve("tachiyomi://add-repo?url=http%3A%2F%2Fexample.org%2Findex.json", emptyList()))
        assertNull(router.resolve("mihon://extension-store?url=file%3A%2F%2FC%3A%2Fsecret", emptyList()))
        assertNull(router.resolve("mihon://anilist-auth?access_token=secret", emptyList()))
    }

    @Test
    fun `recognized source links resolve to manga and chapter`() = runTest {
        val source = FixtureSource()
        val result = router.resolve("https://fixture.example/chapter/1", listOf(source))
        assertTrue(result is DesktopLinkTarget.Manga)
        result as DesktopLinkTarget.Manga
        assertEquals(source.id, result.source.id)
        assertEquals("/manga/1", result.manga.url)
        assertEquals("/chapter/1", result.chapter?.url)
        assertNull(router.resolve("https://unknown.example/manga/1", listOf(source)))
        assertNull(router.resolve("file:///C:/private.txt", listOf(source)))
    }

    private class FixtureSource : HttpSource(), ResolvableSource {
        override val id = 98765L
        override val name = "Link fixture"
        override val lang = "en"
        override val supportsLatest = false
        override val baseUrl = "https://fixture.example"

        override fun getUriType(uri: String): UriType = when {
            uri.startsWith("$baseUrl/chapter/") -> UriType.Chapter
            uri.startsWith("$baseUrl/manga/") -> UriType.Manga
            else -> UriType.Unknown
        }

        override suspend fun getManga(uri: String): SManga = SManga.create().apply {
            url = "/manga/1"
            title = "Fixture manga"
        }

        override suspend fun getChapter(uri: String): SChapter = SChapter.create().apply {
            url = "/chapter/1"
            name = "Chapter 1"
        }

        override fun popularMangaRequest(page: Int): Request = error("unused")
        override fun popularMangaParse(response: Response): MangasPage = error("unused")
    }
}
