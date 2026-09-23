package mihon.desktop.data

import eu.kanade.tachiyomi.source.model.SManga
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class DesktopMangaRepositoryTest {
    @Test
    fun `library and categories persist across reopen`() {
        val databasePath = Files.createTempDirectory("mihon-library").resolve("tachiyomi.db")
        val manga = SManga.create().apply {
            url = "/fixture"
            title = "Fixture manga"
            genre = "Adventure, Comedy"
        }
        DesktopMangaRepository.open(databasePath).use { repository ->
            val stored = repository.addToLibrary(42, manga)
            assertEquals("Fixture manga", stored.title)
            assertEquals(listOf("Adventure", "Comedy"), stored.genre)
            assertEquals(1, repository.library().size)
            repository.addCategory("Reading")
            val readingId = repository.categories().single { it.name == "Reading" }.id
            repository.setMangaCategories(stored._id, setOf(readingId))
            assertEquals(setOf(readingId), repository.mangaCategories(stored._id))
        }
        DesktopMangaRepository.open(databasePath).use { repository ->
            assertEquals("Fixture manga", repository.library().single().title)
            assertTrue(repository.categories().any { it.name == "Reading" })
            val id = repository.addToLibrary(42, manga)._id
            assertEquals(
                setOf(
                    repository.categories().single {
                        it.name == "Reading"
                    }.id,
                ),
                repository.mangaCategories(id),
            )
            assertEquals(1, repository.library().size)
            repository.setFavorite(id, false)
            assertTrue(repository.library().isEmpty())
        }
    }
}
