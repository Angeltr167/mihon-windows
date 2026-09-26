package mihon.desktop

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import tachiyomi.source.local.desktop.DesktopLocalPage
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties
import kotlin.coroutines.coroutineContext

internal enum class DesktopDownloadStatus { PENDING, RUNNING, PAUSED, FAILED, COMPLETED }

internal data class DesktopDownload(
    val sourceId: Long,
    val mangaUrl: String,
    val mangaTitle: String,
    val chapterUrl: String,
    val chapterName: String,
    val status: DesktopDownloadStatus = DesktopDownloadStatus.PENDING,
    val pagesDone: Int = 0,
    val pageCount: Int = 0,
    val error: String? = null,
) {
    val key: String get() = downloadKey(sourceId, mangaUrl, chapterUrl)
}

/** All persisted paths are derived from a hash, never from extension-provided names or URLs. */
internal class DesktopDownloadStore(private val root: Path) {
    private val chapters = root.resolve("chapters")
    private val queueFile = root.resolve("queue.properties")

    init {
        Files.createDirectories(chapters)
    }

    fun loadQueue(): List<DesktopDownload> {
        if (!Files.isRegularFile(queueFile)) return emptyList()
        return runCatching {
            val properties = Properties().apply { Files.newInputStream(queueFile).use(::load) }
            val count = properties.getProperty("count")?.toIntOrNull()?.takeIf { it in 0..100_000 }
                ?: error("Invalid download queue length")
            (0 until count).map { index ->
                val prefix = "$index."
                fun required(field: String) = properties.getProperty(prefix + field) ?: error("Missing $field")
                DesktopDownload(
                    sourceId = required("sourceId").toLong(),
                    mangaUrl = required("mangaUrl"),
                    mangaTitle = required("mangaTitle"),
                    chapterUrl = required("chapterUrl"),
                    chapterName = required("chapterName"),
                    status = DesktopDownloadStatus.valueOf(required("status")),
                    pagesDone = required("pagesDone").toInt(),
                    pageCount = required("pageCount").toInt(),
                    error = properties.getProperty(prefix + "error"),
                )
            }.distinctBy(DesktopDownload::key)
        }.getOrElse {
            val recovery = root.resolve("queue.corrupt-${System.currentTimeMillis()}.properties")
            Files.move(queueFile, recovery)
            emptyList()
        }
    }

    fun saveQueue(items: List<DesktopDownload>) {
        val properties = Properties().apply {
            setProperty("count", items.size.toString())
            items.forEachIndexed { index, item ->
                val prefix = "$index."
                setProperty(prefix + "sourceId", item.sourceId.toString())
                setProperty(prefix + "mangaUrl", item.mangaUrl)
                setProperty(prefix + "mangaTitle", item.mangaTitle)
                setProperty(prefix + "chapterUrl", item.chapterUrl)
                setProperty(prefix + "chapterName", item.chapterName)
                setProperty(prefix + "status", item.status.name)
                setProperty(prefix + "pagesDone", item.pagesDone.toString())
                setProperty(prefix + "pageCount", item.pageCount.toString())
                item.error?.let { setProperty(prefix + "error", it) }
            }
        }
        val temp = Files.createTempFile(root, "queue-", ".tmp")
        try {
            Files.newOutputStream(temp).use { properties.store(it, "Mihon Desktop downloads") }
            try {
                Files.move(temp, queueFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, queueFile, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    fun completedPages(sourceId: Long, mangaUrl: String, chapterUrl: String): List<DesktopLocalPage>? =
        completedPagesForKey(downloadKey(sourceId, mangaUrl, chapterUrl))

    private fun completedPagesForKey(key: String): List<DesktopLocalPage>? {
        val chapter = finalDirectory(key)
        if (!Files.isDirectory(chapter, LinkOption.NOFOLLOW_LINKS) ||
            !Files.isRegularFile(chapter.resolve("complete"), LinkOption.NOFOLLOW_LINKS)
        ) {
            return null
        }
        val expectedPages =
            runCatching { Files.readString(chapter.resolve("complete")) }.getOrNull()?.trim()?.toIntOrNull()
                ?.takeIf { it in 1..100_000 } ?: return null
        return Files.list(chapter).use { stream ->
            stream.filter {
                Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) && Files.size(it) > 0 &&
                    it.fileName.toString().startsWith("page-")
            }
                .sorted()
                .map { DesktopLocalPage(it, null) }
                .toList()
        }.takeIf { it.size == expectedPages }
    }

    fun isComplete(item: DesktopDownload): Boolean =
        completedPages(item.sourceId, item.mangaUrl, item.chapterUrl) != null

    fun stageDirectory(key: String): Path = chapters.resolve("$key.partial")

    fun finalDirectory(key: String): Path = chapters.resolve(key)

    fun clearPartial(key: String) {
        val stage = stageDirectory(key)
        require(stage.parent == chapters && key.matches(Regex("[0-9a-f]{64}")))
        if (!Files.exists(stage)) return
        Files.walk(stage).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::delete)
        }
    }

    fun commit(key: String, pageCount: Int) {
        require(pageCount > 0)
        val stage = stageDirectory(key)
        Files.writeString(stage.resolve("complete"), pageCount.toString())
        val destination = finalDirectory(key)
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            require(completedPagesForKey(key) == null) { "Chapter is already complete" }
            Files.move(destination, chapters.resolve("$key.corrupt-${System.currentTimeMillis()}"))
        }
        try {
            Files.move(stage, destination, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(stage, destination)
        }
    }
}

internal fun interface DesktopChapterTransfer {
    suspend fun download(
        item: DesktopDownload,
        source: Source,
        manga: SManga,
        chapter: SChapter,
        onProgress: (done: Int, total: Int) -> Unit,
    )
}

/** Transfer logic has no scheduler or Android dependency. An interrupted chapter restarts from page one. */
internal class DesktopDownloadEngine(
    private val store: DesktopDownloadStore,
    private val fetcher: DesktopPageFetcher,
) : DesktopChapterTransfer {
    override suspend fun download(
        item: DesktopDownload,
        source: Source,
        manga: SManga,
        chapter: SChapter,
        onProgress: (done: Int, total: Int) -> Unit,
    ) = withContext(Dispatchers.IO) {
        require(source.id == item.sourceId && manga.url == item.mangaUrl && chapter.url == item.chapterUrl)
        if (store.isComplete(item)) return@withContext
        val pages = fetcher.pages(source, chapter)
        store.clearPartial(item.key)
        val stage = store.stageDirectory(item.key)
        Files.createDirectories(stage)
        pages.forEachIndexed { index, page ->
            coroutineContext.ensureActive()
            val bytes = fetcher.bytes(page)
            Data.makeFromBytes(bytes).use { data ->
                Codec.makeFromData(data).use { codec ->
                    DesktopPageLoader.checkDimensions(codec.width, codec.height)
                }
            }
            coroutineContext.ensureActive()
            Files.write(stage.resolve("page-${"%05d".format(index)}.${imageExtension(bytes)}"), bytes)
            onProgress(index + 1, pages.size)
        }
        coroutineContext.ensureActive()
        store.commit(item.key, pages.size)
    }
}

private fun downloadKey(sourceId: Long, mangaUrl: String, chapterUrl: String): String {
    val input = "$sourceId\u0000$mangaUrl\u0000$chapterUrl".toByteArray(Charsets.UTF_8)
    return MessageDigest.getInstance("SHA-256").digest(input).joinToString("") { "%02x".format(it) }
}

internal fun imageExtension(bytes: ByteArray): String = when {
    bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(
        byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a),
    ) -> "png"
    bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() -> "jpg"
    bytes.size >= 3 && bytes.copyOfRange(0, 3).decodeToString() == "GIF" -> "gif"
    bytes.size >= 12 && bytes.copyOfRange(0, 4).decodeToString() == "RIFF" &&
        bytes.copyOfRange(8, 12).decodeToString() == "WEBP" -> "webp"
    else -> "png" // Skia detects encoded format from bytes; suffix keeps local-page discovery compatible.
}
