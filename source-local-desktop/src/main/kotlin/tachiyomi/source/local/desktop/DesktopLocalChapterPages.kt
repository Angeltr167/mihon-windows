package tachiyomi.source.local.desktop

import mihon.core.archive.desktop.DesktopArchiveReaderFactory
import mihon.core.archive.desktop.DesktopEpubReader
import net.greypanther.natsort.CaseInsensitiveSimpleNaturalComparator
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.io.path.extension

data class DesktopLocalPage(val chapterFile: Path, val entryName: String?) {
    fun readBytes(maxBytes: Int = MAX_PAGE_BYTES): ByteArray {
        val input = if (entryName == null) {
            Files.newInputStream(chapterFile)
        } else {
            requireNotNull(DesktopArchiveReaderFactory.open(chapterFile).openEntry(entryName))
        }
        return input.use { it.readNBytes(maxBytes + 1) }.also {
            require(it.size <= maxBytes) { "Page exceeds the size limit" }
        }
    }

    companion object {
        const val MAX_PAGE_BYTES = 32 * 1024 * 1024
    }
}

class DesktopLocalChapterPages(private val fileSystem: DesktopLocalSourceFileSystem) {
    fun pages(chapterUrl: String): List<DesktopLocalPage> {
        val chapter = fileSystem.chapterFile(chapterUrl)
        val entries = when {
            Files.isDirectory(chapter, LinkOption.NOFOLLOW_LINKS) -> Files.list(chapter).use { stream ->
                stream.filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) && isImage(it.toString()) }
                    .map { DesktopLocalPage(it, null) }.toList()
            }
            chapter.extension.equals("epub", ignoreCase = true) -> return DesktopEpubReader(chapter).use { epub ->
                epub.getImagesFromPages().map { DesktopLocalPage(chapter, it) }
            }
            else -> DesktopArchiveReaderFactory.open(chapter).use { archive ->
                archive.entries().filter { it.isFile && isImage(it.name) }
                    .map { DesktopLocalPage(chapter, it.name) }
            }
        }
        return entries.sortedWith { left, right ->
            comparator.compare(
                left.entryName ?: left.chapterFile.fileName.toString(),
                right.entryName ?: right.chapterFile.fileName.toString(),
            )
        }
    }

    private fun isImage(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

    companion object {
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "avif", "jxl")
        private val comparator = CaseInsensitiveSimpleNaturalComparator.getInstance<String>()
    }
}
