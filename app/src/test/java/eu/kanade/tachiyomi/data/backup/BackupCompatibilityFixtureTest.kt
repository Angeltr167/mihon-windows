package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

@OptIn(ExperimentalSerializationApi::class)
class BackupCompatibilityFixtureTest {

    @Test
    fun `baseline protobuf backup round trips without semantic drift`() {
        val fixture = Backup(
            backupManga = listOf(
                BackupManga(
                    source = 1_000_001L,
                    url = "/fixture/manga/one",
                    title = "Fixture Manga",
                    artist = "Fixture Artist",
                    author = "Fixture Author",
                    description = "Cross-platform backup compatibility fixture",
                    genre = listOf("Action", "Drama"),
                    status = 1,
                    thumbnailUrl = "https://example.invalid/fixture-cover.jpg",
                    dateAdded = 1_690_000_000_000L,
                    favorite = true,
                    chapterFlags = 3,
                    viewer_flags = 2,
                    lastModifiedAt = 1_700_000_000_000L,
                    favoriteModifiedAt = 1_700_000_000_000L,
                    version = 7,
                    notes = "phase-0 fixture",
                    initialized = true,
                ),
            ),
            backupCategories = listOf(
                BackupCategory(
                    name = "Fixture Category",
                    order = 2,
                    id = 42,
                    flags = 5,
                ),
            ),
        )

        val encoded = ProtoBuf.encodeToByteArray(Backup.serializer(), fixture)
        assertFalse(encoded.isEmpty())

        val decoded = ProtoBuf.decodeFromByteArray(Backup.serializer(), encoded)

        assertEquals(1, decoded.backupManga.size)
        assertEquals(1, decoded.backupCategories.size)

        val manga = decoded.backupManga.single()
        assertEquals(1_000_001L, manga.source)
        assertEquals("/fixture/manga/one", manga.url)
        assertEquals("Fixture Manga", manga.title)
        assertEquals(listOf("Action", "Drama"), manga.genre)
        assertEquals(true, manga.favorite)
        assertEquals(3, manga.chapterFlags)
        assertEquals(2, manga.viewer_flags)
        assertEquals(7, manga.version)
        assertEquals("phase-0 fixture", manga.notes)
        assertEquals(true, manga.initialized)

        val category = decoded.backupCategories.single()
        assertEquals("Fixture Category", category.name)
        assertEquals(2L, category.order)
        assertEquals(42L, category.id)
        assertEquals(5L, category.flags)
    }
}
