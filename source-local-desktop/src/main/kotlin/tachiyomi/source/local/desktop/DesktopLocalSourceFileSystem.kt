package tachiyomi.source.local.desktop

import mihon.core.archive.desktop.DesktopArchiveReaderFactory
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

class DesktopLocalSourceFileSystem(
    root: Path,
) {
    val root: Path = root.toAbsolutePath().normalize()

    init {
        Files.createDirectories(this.root)
    }

    fun mangaDirectories(): List<Path> = list(root)
        .filter { Files.isDirectory(it, LinkOption.NOFOLLOW_LINKS) }
        .filterNot { it.name.startsWith('.') }
        .distinctBy { it.name }

    fun mangaDirectory(name: String): Path? = safeChild(root, name)
        .takeIf { Files.isDirectory(it, LinkOption.NOFOLLOW_LINKS) }

    fun mangaFiles(name: String): List<Path> = mangaDirectory(name)?.let(::list).orEmpty()

    fun cover(name: String): Path? = mangaFiles(name)
        .firstOrNull {
            Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) &&
                it.nameWithoutExtension.equals("cover", ignoreCase = true) &&
                it.extension.lowercase() in IMAGE_EXTENSIONS
        }

    fun chapterFiles(name: String): List<Path> = mangaFiles(name)
        .filterNot { it.name.startsWith('.') }
        .filter {
            Files.isDirectory(it, LinkOption.NOFOLLOW_LINKS) ||
                DesktopArchiveReaderFactory.isSupported(it) ||
                it.extension.equals("epub", ignoreCase = true)
        }

    fun comicInfo(name: String): Path? = mangaFiles(name)
        .firstOrNull { Files.isRegularFile(it) && it.name == COMIC_INFO_FILE }

    fun legacyJson(name: String): Path? = mangaFiles(name)
        .firstOrNull { Files.isRegularFile(it) && it.extension.equals("json", ignoreCase = true) }

    fun noXmlMarker(name: String): Path? = mangaDirectory(name)
        ?.resolve(NO_XML_MARKER)
        ?.takeIf(Files::exists)

    fun createNoXmlMarker(name: String) {
        val directory = mangaDirectory(name) ?: return
        Files.write(directory.resolve(NO_XML_MARKER), byteArrayOf())
    }

    fun writeComicInfo(name: String, bytes: ByteArray): Path {
        val directory = mangaDirectory(name) ?: error("Unknown local manga: $name")
        val target = directory.resolve(COMIC_INFO_FILE)
        val temp = Files.createTempFile(directory, "ComicInfo", ".tmp")
        Files.write(temp, bytes)
        try {
            Files.move(
                temp,
                target,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        return target
    }

    fun lastModified(path: Path): Long = Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis()

    private fun list(directory: Path): List<Path> = Files.list(directory).use { stream ->
        stream.toList()
    }

    private fun safeChild(parent: Path, name: String): Path {
        val child = parent.resolve(name).normalize()
        require(child.startsWith(parent)) { "Path escapes local source root: $name" }
        return child
    }

    companion object {
        const val COMIC_INFO_FILE = "ComicInfo.xml"
        private const val NO_XML_MARKER = ".noxml"
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "avif", "jxl")
    }
}
