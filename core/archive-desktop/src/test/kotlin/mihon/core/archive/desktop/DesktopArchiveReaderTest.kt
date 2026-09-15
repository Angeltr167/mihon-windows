package mihon.core.archive.desktop

import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DesktopArchiveReaderTest {

    @Test
    fun `matches Mihon archive extensions`() {
        assertEquals(
            setOf("zip", "cbz", "rar", "cbr", "7z", "cb7", "tar", "cbt"),
            DesktopArchiveReaderFactory.supportedExtensions,
        )
    }

    @Test
    fun `reads zip and cbz entries`() {
        val path = Files.createTempFile("mihon-archive", ".cbz")
        writeZip(path, "001.txt", "zip-page")

        DesktopArchiveReaderFactory.open(path).use { reader ->
            assertTrue(reader.entries().any { it.name == "001.txt" && it.isFile })
            assertEquals("zip-page", reader.openEntry("001.txt")!!.bufferedReader().use { it.readText() })
        }
    }

    @Test
    fun `reads tar and cbt entries`() {
        val path = Files.createTempFile("mihon-archive", ".cbt")
        writeTar(path, "001.txt", "tar-page")

        DesktopArchiveReaderFactory.open(path).use { reader ->
            assertTrue(reader.entries().any { it.name == "001.txt" && it.isFile })
            assertEquals("tar-page", reader.openEntry("001.txt")!!.bufferedReader().use { it.readText() })
        }
    }

    @Test
    fun `reads 7z and cb7 entries`() {
        val path = Files.createTempFile("mihon-archive", ".cb7")
        val content = Files.createTempFile("mihon-page", ".txt")
        Files.writeString(content, "7z-page")

        SevenZOutputFile(path.toFile()).use { archive ->
            val entry = archive.createArchiveEntry(content, "001.txt")
            archive.putArchiveEntry(entry)
            archive.write(content)
            archive.closeArchiveEntry()
        }

        DesktopArchiveReaderFactory.open(path).use { reader ->
            assertTrue(reader.entries().any { it.name == "001.txt" && it.isFile })
            assertEquals("7z-page", reader.openEntry("001.txt")!!.bufferedReader().use { it.readText() })
        }
    }

    private fun writeZip(path: Path, name: String, content: String) {
        ZipOutputStream(Files.newOutputStream(path)).use { archive ->
            archive.putNextEntry(ZipEntry(name))
            archive.write(content.toByteArray())
            archive.closeEntry()
        }
    }

    private fun writeTar(path: Path, name: String, content: String) {
        val bytes = content.toByteArray()
        TarArchiveOutputStream(Files.newOutputStream(path)).use { archive ->
            val entry = TarArchiveEntry(name).apply { size = bytes.size.toLong() }
            archive.putArchiveEntry(entry)
            archive.write(bytes)
            archive.closeArchiveEntry()
        }
    }
}
