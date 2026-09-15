package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SourceContractJvmTest {

    @Test
    fun `plain JVM implementation can consume source contract`() {
        val source = object : Source {
            override val id = 42L
            override val name = "fixture"
            override val supportsLatest = true

            override suspend fun getPopularManga(page: Int): MangasPage = error("not called")

            override suspend fun getLatestUpdates(page: Int): MangasPage = error("not called")

            override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage = error(
                "not called",
            )

            override suspend fun getMangaUpdate(
                manga: SManga,
                chapters: List<SChapter>,
                fetchDetails: Boolean,
                fetchChapters: Boolean,
            ): SMangaUpdate = error("not called")

            override suspend fun getPageList(chapter: SChapter): List<Page> = error("not called")
        }

        assertEquals(42L, source.id)
        assertEquals("fixture", source.name)
        assertEquals("", source.lang)
    }
}
