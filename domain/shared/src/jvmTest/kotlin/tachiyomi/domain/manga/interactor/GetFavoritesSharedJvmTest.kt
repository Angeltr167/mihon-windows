package tachiyomi.domain.manga.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.manga.repository.MangaRepository

class GetFavoritesSharedJvmTest {

    @Test
    fun `shared interactor executes against repository contract on plain JVM`() = runTest {
        val favorite = Manga.create().copy(id = 7L, title = "JVM favorite", favorite = true)
        val repository = FakeMangaRepository(listOf(favorite))
        val interactor = GetFavoritesShared(repository)

        assertEquals(listOf(favorite), interactor.await())
        assertEquals(listOf(favorite), interactor.subscribe(favorite.source).first())
    }

    private class FakeMangaRepository(
        private val favorites: List<Manga>,
    ) : MangaRepository {
        override suspend fun getMangaById(id: Long): Manga = favorites.first { it.id == id }

        override fun getMangaByIdAsFlow(id: Long): Flow<Manga> =
            flowOf(favorites.first { it.id == id })

        override suspend fun getMangaByUrlAndSourceId(url: String, sourceId: Long): Manga? =
            favorites.firstOrNull { it.url == url && it.source == sourceId }

        override fun getMangaByUrlAndSourceIdAsFlow(
            url: String,
            sourceId: Long,
        ): Flow<Manga?> = flowOf(favorites.firstOrNull { it.url == url && it.source == sourceId })

        override suspend fun getFavorites(): List<Manga> = favorites

        override suspend fun getReadMangaNotInLibrary(): List<Manga> = emptyList()

        override suspend fun getLibraryManga(): List<LibraryManga> = emptyList()

        override fun getLibraryMangaAsFlow(): Flow<List<LibraryManga>> = flowOf(emptyList())

        override fun getFavoritesBySourceId(sourceId: Long): Flow<List<Manga>> =
            MutableStateFlow(favorites.filter { it.source == sourceId })

        override suspend fun getDuplicateLibraryManga(
            id: Long,
            title: String,
        ): List<MangaWithChapterCount> = emptyList()

        override suspend fun getUpcomingManga(
            statuses: Set<Long>,
            excludedCategories: List<Long>,
            includedCategories: List<Long>,
        ): Flow<List<Manga>> = flowOf(emptyList())

        override suspend fun resetViewerFlags(): Boolean = true

        override suspend fun setMangaCategories(mangaId: Long, categoryIds: List<Long>) = Unit

        override suspend fun update(update: MangaUpdate): Boolean = true

        override suspend fun updateAll(mangaUpdates: List<MangaUpdate>): Boolean = true

        override suspend fun insertNetworkManga(manga: List<Manga>): List<Manga> = manga
    }
}
