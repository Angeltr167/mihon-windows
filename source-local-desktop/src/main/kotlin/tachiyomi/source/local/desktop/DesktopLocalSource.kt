package tachiyomi.source.local.desktop

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mihon.core.archive.desktop.DesktopArchiveReaderFactory
import mihon.core.archive.desktop.DesktopEpubReader
import net.greypanther.natsort.CaseInsensitiveSimpleNaturalComparator
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import tachiyomi.domain.chapter.service.ChapterRecognition
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

class DesktopLocalSource(
    private val fileSystem: DesktopLocalSourceFileSystem,
) : Source, UnmeteredSource {

    override val name: String = "Local source"
    override val id: Long = ID
    override val lang: String = "other"
    override val supportsLatest: Boolean = true

    override suspend fun getPopularManga(page: Int): MangasPage = browse(query = "", latest = false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = browse(query = "", latest = true)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
        browse(query = query, latest = false)

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val updatedManga = if (fetchDetails) getMangaDetails(manga) else manga
        val updatedChapters = if (fetchChapters) getChapterList(updatedManga) else chapters
        return SMangaUpdate(updatedManga, updatedChapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> =
        throw UnsupportedOperationException("Local reader resolves pages from the chapter format")

    private fun browse(query: String, latest: Boolean): MangasPage {
        val now = System.currentTimeMillis()
        val candidates = fileSystem.mangaDirectories()
            .filter { path ->
                when {
                    query.isNotBlank() -> path.name.contains(query, ignoreCase = true)
                    latest -> fileSystem.lastModified(path) >= now - LATEST_THRESHOLD_MS
                    else -> true
                }
            }
            .let { paths ->
                if (latest) {
                    paths.sortedByDescending(fileSystem::lastModified)
                } else {
                    paths.sortedWith { left, right -> NATURAL_COMPARATOR.compare(left.name, right.name) }
                }
            }
            .map { directory ->
                SManga.create().apply {
                    title = directory.name
                    url = directory.name
                    fileSystem.cover(directory.name)?.let { thumbnail_url = it.toUri().toString() }
                }
            }

        return MangasPage(candidates, false)
    }

    private fun getMangaDetails(manga: SManga): SManga {
        fileSystem.cover(manga.url)?.let { manga.thumbnail_url = it.toUri().toString() }

        val comicInfo = fileSystem.comicInfo(manga.url)
        if (comicInfo != null) {
            Files.newInputStream(comicInfo).use { applyComicInfoToManga(it, manga) }
            return manga
        }

        val legacyJson = fileSystem.legacyJson(manga.url)
        if (legacyJson != null) {
            applyLegacyJson(Files.readString(legacyJson), manga)
            return manga
        }

        if (fileSystem.noXmlMarker(manga.url) == null) {
            val chapterComicInfo = fileSystem.chapterFiles(manga.url)
                .asSequence()
                .mapNotNull(::openComicInfoFromChapter)
                .firstOrNull()

            if (chapterComicInfo != null) {
                val bytes = chapterComicInfo.use { it.readBytes() }
                applyComicInfoToManga(bytes.inputStream(), manga)
                fileSystem.writeComicInfo(manga.url, bytes)
            } else {
                fileSystem.createNoXmlMarker(manga.url)
            }
        }

        return manga
    }

    private fun getChapterList(manga: SManga): List<SChapter> {
        return fileSystem.chapterFiles(manga.url)
            .map { chapterPath ->
                SChapter.create().apply {
                    url = "${manga.url}/${chapterPath.name}"
                    name = if (Files.isDirectory(chapterPath)) chapterPath.name else chapterPath.nameWithoutExtension
                    date_upload = fileSystem.lastModified(chapterPath)
                    chapter_number = ChapterRecognition
                        .parseChapterNumber(manga.title, name, chapter_number.toDouble())
                        .toFloat()

                    if (chapterPath.extension.equals("epub", ignoreCase = true)) {
                        applyEpubMetadata(chapterPath, manga, this)
                    } else {
                        openComicInfoFromChapter(chapterPath)?.use { applyComicInfoToChapter(it, this) }
                    }
                }
            }
            .sortedWith { left, right -> NATURAL_COMPARATOR.compare(right.name, left.name) }
    }

    private fun openComicInfoFromChapter(path: Path): InputStream? {
        if (Files.isDirectory(path)) {
            val comicInfo = path.resolve(DesktopLocalSourceFileSystem.COMIC_INFO_FILE)
            return comicInfo.takeIf(Files::isRegularFile)?.let(Files::newInputStream)
        }
        if (!DesktopArchiveReaderFactory.isSupported(path)) return null
        val reader = DesktopArchiveReaderFactory.open(path)
        val stream = reader.openEntry(DesktopLocalSourceFileSystem.COMIC_INFO_FILE)
        if (stream == null) reader.close()
        return stream
    }

    private fun applyComicInfoToManga(stream: InputStream, manga: SManga) {
        val doc = stream.use { Jsoup.parse(it, null, "", Parser.xmlParser()) }
        doc.getElementsByTag("Series").first()?.text()?.takeIf(String::isNotBlank)?.let { manga.title = it }
        doc.getElementsByTag("Writer").first()?.text()?.takeIf(String::isNotBlank)?.let { manga.author = it }
        doc.getElementsByTag("Penciller").first()?.text()?.takeIf(String::isNotBlank)?.let { manga.artist = it }
        doc.getElementsByTag("Summary").first()?.text()?.takeIf(String::isNotBlank)?.let { manga.description = it }
        listOf("Genre", "Tags", "Categories")
            .mapNotNull { tag -> doc.getElementsByTag(tag).first()?.text()?.takeIf(String::isNotBlank) }
            .distinct()
            .joinToString(", ")
            .takeIf(String::isNotBlank)
            ?.let { manga.genre = it }
    }

    private fun applyComicInfoToChapter(stream: InputStream, chapter: SChapter) {
        val doc = stream.use { Jsoup.parse(it, null, "", Parser.xmlParser()) }
        doc.getElementsByTag("Title").first()?.text()?.takeIf(String::isNotBlank)?.let { chapter.name = it }
        doc.getElementsByTag("Number").first()?.text()?.toFloatOrNull()?.let { chapter.chapter_number = it }
        doc.getElementsByTag("Translator").first()?.text()?.takeIf(String::isNotBlank)?.let { chapter.scanlator = it }
    }

    private fun applyLegacyJson(raw: String, manga: SManga) {
        val payload = Json.parseToJsonElement(raw).jsonObject
        payload["title"]?.jsonPrimitive?.content?.let { manga.title = it }
        payload["author"]?.jsonPrimitive?.content?.let { manga.author = it }
        payload["artist"]?.jsonPrimitive?.content?.let { manga.artist = it }
        payload["description"]?.jsonPrimitive?.content?.let { manga.description = it }
        payload["genre"]?.jsonArray?.map { it.jsonPrimitive.content }?.let { manga.genre = it.joinToString() }
        payload["status"]?.jsonPrimitive?.content?.toIntOrNull()?.let { manga.status = it }
    }

    private fun applyEpubMetadata(path: Path, manga: SManga, chapter: SChapter) {
        DesktopEpubReader(path).use { epub ->
            val ref = epub.getPackageHref()
            val doc = epub.getPackageDocument(ref)
            val title = doc.getElementsByTag("dc:title").first()
            val publisher = doc.getElementsByTag("dc:publisher").first()
            val creator = doc.getElementsByTag("dc:creator").first()
            val description = doc.getElementsByTag("dc:description").first()

            creator?.text()?.takeIf(String::isNotBlank)?.let { manga.author = it }
            description?.text()?.takeIf(String::isNotBlank)?.let { manga.description = it }
            title?.text()?.takeIf(String::isNotBlank)?.let { chapter.name = it }
            chapter.scanlator = publisher?.text()?.takeIf(String::isNotBlank)
                ?: creator?.text()?.takeIf(String::isNotBlank)
        }
    }

    companion object {
        const val ID = 0L
        private const val LATEST_THRESHOLD_MS = 7L * 24L * 60L * 60L * 1000L
        private val NATURAL_COMPARATOR = CaseInsensitiveSimpleNaturalComparator.getInstance<String>()
    }
}
