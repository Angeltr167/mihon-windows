package mihon.desktop

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.backup.shared.BackupCategory
import mihon.backup.shared.BackupChapter
import mihon.backup.shared.BackupContainer
import mihon.backup.shared.BackupHistory
import mihon.backup.shared.BackupManga
import mihon.backup.shared.BackupTracking
import mihon.backup.shared.MihonBackup
import mihon.desktop.data.DesktopMangaRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesktopBackupImporterTest {
    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `imports Android protobuf backup into Mihon schema and can safely merge it twice`() {
        val repository = DesktopMangaRepository.inMemory()
        val backup = MihonBackup(
            manga = listOf(
                BackupManga(
                    source = 1_000_001,
                    url = "/fixture/manga/one",
                    title = "Fixture Manga",
                    artist = "Fixture Artist",
                    genre = listOf("Action", "Drama"),
                    favorite = true,
                    dateAdded = 1_690_000_000_000,
                    chapters = listOf(
                        BackupChapter(
                            url = "/fixture/manga/one/chapter/1",
                            name = "Chapter 1",
                            read = true,
                            bookmark = true,
                            lastPageRead = 5,
                            chapterNumber = 1F,
                            sourceOrder = 7,
                        ),
                    ),
                    categories = listOf(42),
                    tracking = listOf(
                        BackupTracking(
                            syncId = 2,
                            libraryId = 123,
                            mediaId = 456,
                            trackingUrl = "https://anilist.co/manga/456",
                            title = "Fixture Manga",
                            lastChapterRead = 1F,
                            totalChapters = 12,
                            score = 8F,
                            status = 2,
                        ),
                    ),
                    history = listOf(BackupHistory("/fixture/manga/one/chapter/1", 1_700_000_000_000, 2_500)),
                ),
            ),
            categories = listOf(BackupCategory(name = "Reading", order = 1, id = 42)),
        )
        val fileBytes = BackupContainer.encode(ProtoBuf.encodeToByteArray(MihonBackup.serializer(), backup))

        try {
            val imported = DesktopBackupImporter.importBytes(fileBytes, repository)
            assertEquals(1, imported.manga)
            assertEquals(1, imported.chapters)
            assertEquals(1, imported.categories)
            assertEquals(1, imported.trackerEntries)

            DesktopBackupImporter.importBytes(fileBytes, repository)

            val manga = repository.library().single()
            assertEquals(1_000_001, manga.source)
            assertEquals("Fixture Manga", manga.title)
            assertEquals(listOf("Action", "Drama"), manga.genre)
            val chapter = repository.chapter(manga._id, "/fixture/manga/one/chapter/1")!!
            assertTrue(chapter.read)
            assertTrue(chapter.bookmark)
            assertEquals(5, chapter.last_page_read)
            assertEquals(
                setOf(
                    repository.categories().single {
                        it.name == "Reading"
                    }.id,
                ),
                repository.mangaCategories(manga._id),
            )
            assertEquals(2_500, repository.history().single { it.mangaId == manga._id }.readDuration)
            val tracking = repository.track(manga._id, 2)!!
            assertEquals(456, tracking.remote_id)
            assertEquals(123, tracking.library_id)
        } finally {
            repository.close()
        }
    }
}
