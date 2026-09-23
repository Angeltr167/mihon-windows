package mihon.desktop.data

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import tachiyomi.data.Chapters
import tachiyomi.data.History
import tachiyomi.data.Mangas
import java.io.Closeable
import java.nio.file.Path
import java.util.Date

/** Desktop access to the existing Mihon schema; no second library database is introduced. */
class DesktopMangaRepository private constructor(
    private val driver: SqlDriver,
) : Closeable {
    private val database = Database(
        driver = driver,
        chaptersAdapter = Chapters.Adapter(memoAdapter),
        historyAdapter = History.Adapter(dateAdapter),
        mangasAdapter = Mangas.Adapter(genreAdapter, updateStrategyAdapter, memoAdapter),
    )

    fun library(): List<Mangas> = database.mangasQueries.getFavorites().executeAsList()

    fun history() = database.historyViewQueries.history("").executeAsList()

    fun updates() = database.updatesViewQueries.getRecentUpdates(after = 0, limit = 100).executeAsList()

    fun manga(id: Long): Mangas? = database.mangasQueries.getMangaById(id).executeAsOneOrNull()

    fun find(sourceId: Long, url: String): Mangas? =
        database.mangasQueries.getMangaByUrlAndSource(url, sourceId).executeAsOneOrNull()

    fun addToLibrary(sourceId: Long, manga: SManga): Mangas {
        val existing = database.mangasQueries.getMangaByUrlAndSource(manga.url, sourceId).executeAsOneOrNull()
        if (existing != null) {
            setFavorite(existing._id, true)
            return requireNotNull(this.manga(existing._id))
        }
        val id = database.mangasQueries.insertReturningId(
            source = sourceId,
            url = manga.url,
            artist = manga.artist,
            author = manga.author,
            description = manga.description,
            genre = manga.getGenres(),
            title = manga.title,
            status = manga.status.toLong(),
            thumbnailUrl = manga.thumbnail_url,
            favorite = true,
            lastUpdate = null,
            nextUpdate = null,
            initialized = manga.initialized,
            viewerFlags = 0,
            chapterFlags = 0,
            coverLastModified = 0,
            dateAdded = System.currentTimeMillis(),
            updateStrategy = manga.update_strategy,
            calculateInterval = 0,
            version = 0,
            notes = "",
            memo = manga.memo,
        ).executeAsOne()
        return requireNotNull(this.manga(id))
    }

    fun setFavorite(mangaId: Long, favorite: Boolean) {
        driver.execute(null, "UPDATE mangas SET favorite = ? WHERE _id = ?", 2) {
            bindBoolean(0, favorite)
            bindLong(1, mangaId)
        }
    }

    fun categories() = database.categoriesQueries.getCategories().executeAsList()

    fun mangaCategories(mangaId: Long): Set<Long> =
        database.categoriesQueries.getCategoriesByMangaId(mangaId).executeAsList().map { it.id }.toSet()

    fun setMangaCategories(mangaId: Long, categoryIds: Set<Long>) {
        database.transaction {
            database.mangas_categoriesQueries.deleteMangaCategoryByMangaId(mangaId)
            categoryIds.forEach { database.mangas_categoriesQueries.insert(mangaId, it) }
        }
    }

    fun addCategory(name: String) {
        require(name.isNotBlank())
        database.categoriesQueries.insert(name.trim(), categories().size.toLong(), 0)
    }

    override fun close() = driver.close()

    companion object {
        fun open(path: Path) = DesktopMangaRepository(DesktopDatabaseDriver.open(path))
        fun inMemory() = DesktopMangaRepository(DesktopDatabaseDriver.inMemory())

        private val dateAdapter = object : ColumnAdapter<Date, Long> {
            override fun decode(databaseValue: Long) = Date(databaseValue)
            override fun encode(value: Date) = value.time
        }
        private val genreAdapter = object : ColumnAdapter<List<String>, String> {
            override fun decode(databaseValue: String) =
                if (databaseValue.isEmpty()) emptyList() else databaseValue.split(", ")
            override fun encode(value: List<String>) = value.joinToString(", ")
        }
        private val updateStrategyAdapter = object : ColumnAdapter<UpdateStrategy, Long> {
            override fun decode(databaseValue: Long) =
                UpdateStrategy.entries.getOrElse(databaseValue.toInt()) { UpdateStrategy.ALWAYS_UPDATE }
            override fun encode(value: UpdateStrategy) = value.ordinal.toLong()
        }
        private val memoAdapter = object : ColumnAdapter<JsonObject, ByteArray> {
            override fun decode(
                databaseValue: ByteArray,
            ) = Json.decodeFromString<JsonObject>(databaseValue.decodeToString())
            override fun encode(value: JsonObject) = value.toString().encodeToByteArray()
        }
    }
}
