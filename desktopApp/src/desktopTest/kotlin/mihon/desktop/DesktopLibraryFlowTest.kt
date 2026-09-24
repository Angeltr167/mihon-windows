package mihon.desktop

import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.coroutines.test.runTest
import mihon.desktop.data.DesktopMangaRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.source.local.desktop.DesktopLocalSource
import tachiyomi.source.local.desktop.DesktopLocalSourceFileSystem
import java.nio.file.Files

class DesktopLibraryFlowTest {
    @Test
    fun `local source browse details and library survive restart`() = runTest {
        val root = Files.createTempDirectory("mihon-desktop-flow")
        val mangaDirectory = Files.createDirectories(root.resolve("Fixture Manga"))
        Files.createDirectories(mangaDirectory.resolve("Chapter 1"))
        val database = root.resolve("tachiyomi.db")
        val source = DesktopLocalSource(DesktopLocalSourceFileSystem(root))

        val browsed = source.getPopularManga(1).mangas.single()
        assertEquals(browsed.url, source.getSearchManga(1, "Fixture", FilterList()).mangas.single().url)
        val details = source.getMangaUpdate(browsed, emptyList(), true, true)
        assertEquals("Chapter 1", details.chapters.single().name)

        val mangaId = DesktopMangaRepository.open(database).use { library ->
            val stored = library.addToLibrary(source.id, details.manga)
            library.addCategory("Favorites")
            val categoryId = library.categories().single { it.name == "Favorites" }.id
            library.setMangaCategories(stored._id, setOf(categoryId))
            stored._id
        }
        DesktopMangaRepository.open(database).use { library ->
            val stored = library.library().single()
            assertEquals(mangaId, stored._id)
            assertEquals(source.id, stored.source)
            assertEquals("Fixture Manga", stored.title)
            assertEquals(1, library.mangaCategories(stored._id).size)
            library.setFavorite(stored._id, false)
            assertFalse(library.manga(stored._id)!!.favorite)
            assertTrue(library.library().isEmpty())
        }
        DesktopMangaRepository.open(database).use { library ->
            assertTrue(library.library().isEmpty())
            assertTrue(library.categories().any { it.name == "Favorites" })
        }
    }
}
