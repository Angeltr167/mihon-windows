package mihon.desktop

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate

/** Source identity for a completed download when its extension is unavailable. */
internal class DownloadedSource(override val id: Long) : Source {
    override val name = "Downloaded chapter"
    override val lang = "localsourcelang"
    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage = error("Source is unavailable")

    override suspend fun getLatestUpdates(page: Int): MangasPage = error("Source is unavailable")

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
        error("Source is unavailable")

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = error("Source is unavailable")

    override suspend fun getPageList(chapter: SChapter): List<Page> = error("Source is unavailable")
}
