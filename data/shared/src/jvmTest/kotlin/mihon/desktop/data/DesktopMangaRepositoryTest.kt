package mihon.desktop.data

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class DesktopMangaRepositoryTest {
    @Test
    fun `chapter progress and history survive reopen and metadata refresh`() {
        val path = Files.createTempDirectory("mihon-reading-progress").resolve("tachiyomi.db")
        val manga = SManga.create().apply {
            url = "/manga"
            title = "Manga"
        }
        val chapter = SChapter.create().apply {
            url = "/chapter"
            name = "Chapter"
        }
        val nextChapter = SChapter.create().apply {
            url = "/chapter-2"
            name = "Chapter 2"
        }
        val mangaId = DesktopMangaRepository.open(path).use { repository ->
            val id = repository.addToLibrary(17, manga)._id
            repository.syncChapters(id, listOf(chapter, nextChapter))
            val stored = requireNotNull(repository.chapter(id, chapter.url))
            repository.saveProgress(stored._id, 1, 3, 1000)
            repository.setChapterBookmark(stored._id, true)
            val nextStored = requireNotNull(repository.chapter(id, nextChapter.url))
            repository.saveProgress(nextStored._id, 0, 2, 2000)
            id
        }
        DesktopMangaRepository.open(path).use { repository ->
            val stored = repository.chapter(mangaId, chapter.url)!!
            assertEquals(1, stored.last_page_read)
            assertTrue(stored.bookmark)
            assertTrue(repository.history().any { it.mangaId == mangaId })
            chapter.name = "Renamed chapter"
            repository.syncChapters(mangaId, listOf(chapter, nextChapter))
            assertEquals(1, repository.chapter(mangaId, chapter.url)!!.last_page_read)
            assertEquals(0, repository.chapter(mangaId, nextChapter.url)!!.last_page_read)
            assertEquals("Renamed chapter", repository.chapter(mangaId, chapter.url)!!.name)
            repository.saveProgress(stored._id, 2, 3)
            assertTrue(repository.chapter(mangaId, chapter.url)!!.read)
        }
    }

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

    @Test
    fun `category rename reorder and delete preserve existing schema contracts`() {
        val path = Files.createTempDirectory("mihon-category-management").resolve("tachiyomi.db")
        val manga = SManga.create().apply {
            url = "/category-fixture"
            title = "Category fixture"
        }

        DesktopMangaRepository.open(path).use { repository ->
            val mangaId = repository.addToLibrary(73, manga)._id
            repository.addCategory("Reading")
            repository.addCategory("Planned")

            val readingId = repository.categories().single { it.name == "Reading" }.id
            val plannedId = repository.categories().single { it.name == "Planned" }.id
            repository.setMangaCategories(mangaId, setOf(readingId))

            repository.renameCategory(readingId, "Now reading")
            assertEquals("Now reading", repository.categories().single { it.id == readingId }.name)

            repository.moveCategory(plannedId, -1)
            assertEquals(
                listOf(plannedId, readingId),
                repository.categories().filter { it.id > 0 }.map { it.id },
            )

            repository.deleteCategory(readingId)
            assertTrue(repository.categories().none { it.id == readingId })
            assertTrue(repository.mangaCategories(mangaId).isEmpty())
        }
    }

    @Test
    fun `tracker binding and progress persist in the existing schema`() {
        val path = Files.createTempDirectory("mihon-tracker-progress").resolve("tachiyomi.db")
        val manga = SManga.create().apply {
            url = "/komga"
            title = "Tracked manga"
        }
        val mangaId = DesktopMangaRepository.open(path).use { repository ->
            val id = repository.addToLibrary(18, manga)._id
            repository.addTrack(id, 6, manga.title, "https://komga.example/api/v1/series/abc", 0.0, 2, 1)
            repository.addTrack(id, 2, manga.title, "https://anilist.co/manga/42", 0.0, 2, 5, 42, 123)
            val track = requireNotNull(repository.track(id, 6))
            repository.updateTrackProgress(track, 1.0, 2, 2)
            assertEquals(track._id, repository.track(id, 6)?._id)
            id
        }
        DesktopMangaRepository.open(path).use { repository ->
            val track = requireNotNull(repository.track(mangaId, 6))
            assertEquals("https://komga.example/api/v1/series/abc", track.remote_url)
            assertEquals(1.0, track.last_chapter_read)
            assertEquals(2L, track.status)
            val aniList = requireNotNull(repository.track(mangaId, 2))
            assertEquals(42L, aniList.remote_id)
            assertEquals(123L, aniList.library_id)
        }
    }
}
