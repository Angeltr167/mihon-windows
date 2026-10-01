package mihon.desktop

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.backup.shared.BackupCategory
import mihon.backup.shared.BackupChapter
import mihon.backup.shared.BackupContainer
import mihon.backup.shared.BackupDesktopPreference
import mihon.backup.shared.BackupHistory
import mihon.backup.shared.BackupManga
import mihon.backup.shared.BackupTracking
import mihon.backup.shared.MihonBackup
import mihon.desktop.data.DesktopMangaRepository
import mihon.platform.desktop.PropertiesKeyValueStore
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@OptIn(ExperimentalSerializationApi::class)
class DesktopBackupExporterTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `local file restores reading data tracking categories and preferences into a fresh profile`() {
        DesktopMangaRepository.inMemory().use { source ->
            source.importBackup(fixture())
            val settings = PropertiesKeyValueStore(directory.resolve("source.properties"))
            settings.putString(DesktopPreferences.LANGUAGE, "es")
            settings.putBoolean(DesktopPreferences.NOTIFICATIONS, false)
            settings.putString("desktop.reader.mode", "WEBTOON")
            settings.putString("desktop.reader.imageFilter", "INVERT")
            settings.putString("tracker.token", "must-not-be-exported")
            settings.putString(DesktopPreferences.BACKUP_DIRECTORY, "C:/private/source/path")
            val file = DesktopBackupExporter.export(
                directory.resolve("Mis copias locales"),
                source,
                DesktopPreferences(settings).export(),
            )
            val decoded = ProtoBuf.decodeFromByteArray(
                MihonBackup.serializer(),
                BackupContainer.decode(Files.readAllBytes(file)),
            )
            assertFalse(
                decoded.desktopPreferences.any {
                    it.value == "must-not-be-exported" ||
                        it.key == DesktopPreferences.BACKUP_DIRECTORY
                },
            )

            DesktopMangaRepository.inMemory().use { restored ->
                val targetSettings = PropertiesKeyValueStore(directory.resolve("restored.properties"))
                val result = DesktopBackupImporter.import(file, restored, targetSettings)
                assertEquals(2, result.manga)
                assertEquals(2, result.chapters)
                val manga = restored.library().single()
                assertEquals("Fixture Manga", manga.title)
                assertEquals("Fixture Author", manga.author)
                assertEquals(listOf("Action", "Drama"), manga.genre)
                assertEquals("Personal notes", manga.notes)
                assertEquals(1_690_000_000_000, manga.date_added)
                val chapter = restored.chapter(manga._id, "/series/one/chapter/1")!!
                assertTrue(chapter.read)
                assertTrue(chapter.bookmark)
                assertEquals(5, chapter.last_page_read)
                assertEquals("Scanlator", chapter.scanlator)
                assertEquals(2_500, restored.history().single { it.mangaId == manga._id }.readDuration)
                assertEquals(1_700_000_000_000, restored.history().single { it.mangaId == manga._id }.readAt?.time)
                assertEquals(
                    setOf(
                        restored.categories().single {
                            it.name == "Reading"
                        }.id,
                    ),
                    restored.mangaCategories(manga._id),
                )
                val track = restored.track(manga._id, 2)!!
                assertEquals(4_500_000_000, track.remote_id)
                assertEquals(8.5, track.score)
                assertTrue(track.private_)
                val outsideLibrary = restored.find(99, "/series/outside")!!
                assertFalse(outsideLibrary.favorite)
                assertEquals(2, restored.chapter(outsideLibrary._id, "/series/outside/chapter/1")!!.last_page_read)
                assertEquals("es", DesktopPreferences(targetSettings).language)
                assertFalse(targetSettings.getBoolean(DesktopPreferences.NOTIFICATIONS, true))
                assertEquals("WEBTOON", targetSettings.getString("desktop.reader.mode"))
                assertEquals("INVERT", targetSettings.getString("desktop.reader.imageFilter"))
                assertEquals(
                    "es",
                    DesktopPreferences(PropertiesKeyValueStore(directory.resolve("restored.properties"))).language,
                )
                DesktopBackupImporter.import(file, restored, targetSettings)
                assertEquals(1, restored.library().size)
                assertEquals(2_500, restored.history().single { it.mangaId == manga._id }.readDuration)
            }
        }
    }

    @Test
    fun `repeated exports create separate files and leave earlier copies untouched`() {
        DesktopMangaRepository.inMemory().use { source ->
            source.importBackup(fixture())
            val first = DesktopBackupExporter.export(directory, source)
            val before = Files.readAllBytes(first)
            source.setFavorite(source.library().single()._id, false)
            val second = DesktopBackupExporter.export(directory, source)
            assertNotEquals(first, second)
            assertArrayEquals(before, Files.readAllBytes(first))
            assertTrue(Files.size(second) > 0)
            Files.list(directory).use { paths ->
                assertFalse(paths.anyMatch { it.fileName.toString().endsWith(".tmp") })
            }
        }
    }

    @Test
    fun `empty library can still back up and restore basic preferences`() {
        DesktopMangaRepository.inMemory().use { source ->
            val settings = PropertiesKeyValueStore(directory.resolve("empty.properties"))
            val file = DesktopBackupExporter.export(directory, source, DesktopPreferences(settings).export())
            DesktopMangaRepository.inMemory().use { target ->
                settings.putString(DesktopPreferences.LANGUAGE, "es")
                assertEquals(0, DesktopBackupImporter.import(file, target, settings).manga)
                assertEquals("system", DesktopPreferences(settings).language)
            }
        }
    }

    @Test
    fun `untrusted backup preferences cannot import paths tokens or invalid values`() {
        val settings = PropertiesKeyValueStore(directory.resolve("preferences.properties"))
        settings.putString(DesktopPreferences.LANGUAGE, "en")
        settings.putString(DesktopPreferences.BACKUP_DIRECTORY, directory.toString())
        DesktopPreferences(settings).restore(
            listOf(
                BackupDesktopPreference(DesktopPreferences.LANGUAGE, "invalid"),
                BackupDesktopPreference(DesktopPreferences.BACKUP_DIRECTORY, "C:/other"),
                BackupDesktopPreference("desktop.reader.mode", "invalid"),
                BackupDesktopPreference("desktop.window.width", "-1"),
                BackupDesktopPreference("tracker.token", "untrusted-token"),
                BackupDesktopPreference(DesktopPreferences.NOTIFICATIONS, "false"),
            ),
        )
        assertEquals("en", DesktopPreferences(settings).language)
        assertEquals(directory.toString(), settings.getString(DesktopPreferences.BACKUP_DIRECTORY))
        assertFalse(settings.contains("tracker.token"))
        assertFalse(settings.contains("desktop.reader.mode"))
        assertFalse(settings.contains("desktop.window.width"))
        assertFalse(settings.getBoolean(DesktopPreferences.NOTIFICATIONS, true))
    }

    @Test
    fun `invalid library backup leaves both library and preferences unchanged`() {
        DesktopMangaRepository.inMemory().use { repository ->
            repository.importBackup(fixture())
            val settings = PropertiesKeyValueStore(directory.resolve("invalid.properties"))
            settings.putString(DesktopPreferences.LANGUAGE, "en")
            val invalid = MihonBackup(
                manga = listOf(BackupManga(source = 99, url = "", title = "Invalid")),
                categories = listOf(BackupCategory("Should roll back", id = 88)),
                desktopPreferences = listOf(BackupDesktopPreference(DesktopPreferences.LANGUAGE, "es")),
            )
            assertThrows(IllegalArgumentException::class.java) {
                DesktopBackupImporter.importBytes(
                    BackupContainer.encode(ProtoBuf.encodeToByteArray(MihonBackup.serializer(), invalid)),
                    repository,
                    settings,
                )
            }
            assertEquals("en", DesktopPreferences(settings).language)
            assertEquals("Fixture Manga", repository.library().single().title)
            assertFalse(repository.categories().any { it.name == "Should roll back" })
        }
    }

    private fun fixture() = MihonBackup(
        manga = listOf(
            BackupManga(
                source = 99, url = "/series/one", title = "Fixture Manga", author = "Fixture Author",
                genre = listOf("Action", "Drama"), dateAdded = 1_690_000_000_000, notes = "Personal notes",
                chapters = listOf(
                    BackupChapter(
                        url = "/series/one/chapter/1",
                        name = "Chapter 1",
                        scanlator = "Scanlator",
                        read = true,
                        bookmark = true,
                        lastPageRead = 5,
                        chapterNumber = 1F,
                    ),
                ),
                categories = listOf(42),
                tracking = listOf(
                    BackupTracking(
                        syncId = 2, mediaId = 4_500_000_000, title = "Fixture Manga",
                        trackingUrl = "https://anilist.co/manga/456", lastChapterRead = 1F, totalChapters = 12,
                        score = 8.5F, status = 2, private = true,
                    ),
                ),
                history = listOf(BackupHistory("/series/one/chapter/1", 1_700_000_000_000, 2_500)),
            ),
            BackupManga(
                source = 99,
                url = "/series/outside",
                title = "Outside library",
                favorite = false,
                chapters = listOf(
                    BackupChapter(url = "/series/outside/chapter/1", name = "Chapter 1", lastPageRead = 2),
                ),
            ),
        ),
        categories = listOf(BackupCategory("Reading", order = 1, id = 42)),
    )
}
