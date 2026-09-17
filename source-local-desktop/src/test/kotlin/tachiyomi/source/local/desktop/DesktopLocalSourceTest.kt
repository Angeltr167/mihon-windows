package tachiyomi.source.local.desktop

import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DesktopLocalSourceTest {

    @Test
    fun `imports local manga metadata cover and naturally ordered chapters`() = runTest {
        val root = Files.createTempDirectory("mihon-local-source")
        val manga = Files.createDirectories(root.resolve("My Manga"))
        Files.write(manga.resolve("cover.jpg"), byteArrayOf(1, 2, 3))
        Files.writeString(
            manga.resolve("ComicInfo.xml"),
            """
                <ComicInfo>
                  <Series>My Better Manga</Series>
                  <Writer>Author</Writer>
                  <Summary>Local summary</Summary>
                  <Genre>Action</Genre>
                </ComicInfo>
            """.trimIndent(),
        )
        Files.createDirectories(manga.resolve("Chapter 10"))
        createCbz(
            manga.resolve("Chapter 2.cbz"),
            """
                <ComicInfo>
                  <Title>Chapter Two</Title>
                  <Number>2</Number>
                  <Translator>Local Team</Translator>
                </ComicInfo>
            """.trimIndent(),
        )

        val source = DesktopLocalSource(DesktopLocalSourceFileSystem(root))
        val discovered = source.getSearchManga(1, "My", FilterList()).mangas.single()
        val update = source.getMangaUpdate(discovered, emptyList(), fetchDetails = true, fetchChapters = true)

        assertEquals("My Better Manga", update.manga.title)
        assertEquals("Author", update.manga.author)
        assertEquals("Local summary", update.manga.description)
        assertTrue(update.manga.thumbnail_url!!.startsWith("file:"))
        // Match Android LocalSource: ComicInfo metadata updates the display name before the final natural sort.
        assertEquals(listOf("Chapter Two", "Chapter 10"), update.chapters.map { it.name })
        assertEquals(2f, update.chapters[0].chapter_number)
        assertEquals(10f, update.chapters[1].chapter_number)
        assertEquals("Local Team", update.chapters[0].scanlator)
    }

    private fun createCbz(path: Path, comicInfo: String) {
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            zip.putNextEntry(ZipEntry(DesktopLocalSourceFileSystem.COMIC_INFO_FILE))
            zip.write(comicInfo.toByteArray())
            zip.closeEntry()
        }
    }
}
