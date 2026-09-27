package mihon.desktop

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI

/** Presents Suwayomi's installed sources to Mihon's existing browser, library and reader. */
internal class SuwayomiSource(
    private val info: SuwayomiSourceInfo,
    private val client: SuwayomiClient,
) : Source {
    override val id: Long get() = info.id
    override val name: String get() = info.name
    override val lang: String get() = info.lang
    override val supportsLatest: Boolean get() = info.supportsLatest

    /** Resolve persisted media paths against this launch's loopback port. */
    internal fun coverUrl(savedUrl: String): String {
        val uri = URI(savedUrl)
        val path = if (uri.isAbsolute) {
            require(uri.scheme == "http" && uri.host == "127.0.0.1" && uri.userInfo == null) {
                "Unexpected Suwayomi cover origin"
            }
            uri.rawPath + (uri.rawQuery?.let { "?$it" } ?: "")
        } else {
            savedUrl
        }
        return client.absolute(path)
    }

    override suspend fun getPopularManga(page: Int): MangasPage = browse("POPULAR", page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = browse("LATEST", page)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        require(filters.isEmpty()) { "Source filters are not yet available through Mihon Windows" }
        return browse("SEARCH", page, query)
    }

    private fun browse(type: String, page: Int, query: String = ""): MangasPage {
        val result = client.browse(id, type, page, query)
        return MangasPage(result.mangas.map(::toManga), result.hasNextPage)
    }

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchDetails && !fetchChapters) return SMangaUpdate(manga, chapters)
        val remoteId = client.mangaId(id, manga.url)
        val result = client.mangaAndChapters(remoteId, fetchDetails, fetchChapters)
        return SMangaUpdate(
            if (fetchDetails) toManga(result.manga) else manga,
            if (fetchChapters) result.chapters.map(::toChapter) else chapters,
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.memo[CHAPTER_ID]?.jsonPrimitive?.content?.toIntOrNull()
            ?: error("Chapter must be refreshed before reading")
        return client.pages(chapterId).mapIndexed { index, url -> Page(index, url, url) }
    }

    private fun toManga(node: JsonObject): SManga = SManga.create().apply {
        url = node.requiredString("url")
        title = node.requiredString("title")
        thumbnail_url = node.optionalString("thumbnailUrl")
        artist = node.optionalString("artist")
        author = node.optionalString("author")
        description = node.optionalString("description")
        genre = node["genre"]?.let { value ->
            runCatching { value.jsonArray.joinToString(", ") { it.jsonPrimitive.content } }.getOrNull()
        }
        status = when (node.optionalString("status")) {
            "ONGOING" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            "LICENSED" -> SManga.LICENSED
            "PUBLISHING_FINISHED" -> SManga.PUBLISHING_FINISHED
            "CANCELLED" -> SManga.CANCELLED
            "ON_HIATUS" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        initialized = !description.isNullOrBlank()
        memo = buildJsonObject { put(MANGA_ID, node.requiredInt("id")) }
    }

    private fun toChapter(node: JsonObject): SChapter = SChapter.create().apply {
        url = node.requiredString("url")
        name = node.requiredString("name")
        chapter_number = node["chapterNumber"]?.jsonPrimitive?.content?.toFloatOrNull() ?: -1f
        date_upload = node["uploadDate"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
        scanlator = node.optionalString("scanlator")
        memo = buildJsonObject {
            put(CHAPTER_ID, node.requiredInt("id"))
            put(MANGA_ID, node.requiredInt("mangaId"))
        }
    }

    private companion object {
        const val MANGA_ID = "mihon.suwayomi.mangaId"
        const val CHAPTER_ID = "mihon.suwayomi.chapterId"
    }
}
