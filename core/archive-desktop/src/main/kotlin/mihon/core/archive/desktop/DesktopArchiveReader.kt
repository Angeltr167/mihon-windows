package mihon.core.archive.desktop

import mihon.core.archive.api.ArchiveEntry
import mihon.core.archive.api.ArchiveReader
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarFile
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.Closeable
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.extension
import com.github.junrar.Archive as RarArchive

class DesktopArchiveReader(
    private val path: Path,
) : ArchiveReader {

    private val type = ArchiveType.from(path)

    override fun entries(): List<ArchiveEntry> = when (type) {
        ArchiveType.ZIP -> ZipFile.builder().setPath(path).get().use { archive ->
            archive.entries.asSequence().map { ArchiveEntry(it.name, !it.isDirectory) }.toList()
        }
        ArchiveType.TAR -> TarFile(path).use { archive ->
            archive.entries.map { ArchiveEntry(it.name, !it.isDirectory) }
        }
        ArchiveType.SEVEN_Z -> SevenZFile.builder().setPath(path).get().use { archive ->
            archive.entries.map { ArchiveEntry(it.name, !it.isDirectory) }
        }
        ArchiveType.RAR -> RarArchive(path.toFile()).use { archive ->
            archive.fileHeaders.map { ArchiveEntry(it.fileName, !it.isDirectory) }
        }
    }

    override fun openEntry(entryName: String): InputStream? = when (type) {
        ArchiveType.ZIP -> {
            val archive = ZipFile.builder().setPath(path).get()
            val entry = archive.getEntry(entryName) ?: return archive.closeAndNull()
            OwnedInputStream(archive.getInputStream(entry), archive)
        }
        ArchiveType.TAR -> {
            val archive = TarFile(path)
            val entry = archive.entries.firstOrNull { it.name == entryName } ?: return archive.closeAndNull()
            OwnedInputStream(archive.getInputStream(entry), archive)
        }
        ArchiveType.SEVEN_Z -> {
            val archive = SevenZFile.builder().setPath(path).get()
            val entry = archive.entries.firstOrNull { it.name == entryName } ?: return archive.closeAndNull()
            OwnedInputStream(archive.getInputStream(entry), archive)
        }
        ArchiveType.RAR -> {
            val archive = RarArchive(path.toFile())
            val entry = archive.fileHeaders.firstOrNull { it.fileName == entryName } ?: return archive.closeAndNull()
            OwnedInputStream(archive.getInputStream(entry), archive)
        }
    }

    override fun close() = Unit

    private enum class ArchiveType {
        ZIP,
        TAR,
        SEVEN_Z,
        RAR,
        ;

        companion object {
            fun from(path: Path): ArchiveType = when (path.extension.lowercase()) {
                "zip", "cbz", "epub" -> ZIP
                "tar", "cbt" -> TAR
                "7z", "cb7" -> SEVEN_Z
                "rar", "cbr" -> RAR
                else -> throw UnsupportedArchiveFormatException(path)
            }
        }
    }
}

object DesktopArchiveReaderFactory {
    val supportedExtensions: Set<String> = setOf("zip", "cbz", "rar", "cbr", "7z", "cb7", "tar", "cbt")

    fun isSupported(path: Path): Boolean = path.extension.lowercase() in supportedExtensions

    fun open(path: Path): ArchiveReader {
        require(isSupported(path)) { "Unsupported archive: $path" }
        return DesktopArchiveReader(path)
    }
}

class UnsupportedArchiveFormatException(path: Path) :
    IllegalArgumentException("Unsupported archive format: $path")

private class OwnedInputStream(
    delegate: InputStream,
    private val owner: Closeable,
) : FilterInputStream(delegate) {
    override fun close() {
        try {
            super.close()
        } finally {
            owner.close()
        }
    }
}

private fun <T> Closeable.closeAndNull(): T? {
    close()
    return null
}
