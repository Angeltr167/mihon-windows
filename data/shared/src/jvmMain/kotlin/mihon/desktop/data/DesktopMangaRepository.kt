package mihon.desktop.data

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import eu.kanade.tachiyomi.source.model.SChapter
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

    fun addToLibrary(sourceId: Long, manga: SManga): Mangas = ensureManga(sourceId, manga, true)

    fun ensureManga(sourceId: Long, manga: SManga, favorite: Boolean = false): Mangas {
        val existing = database.mangasQueries.getMangaByUrlAndSource(manga.url, sourceId).executeAsOneOrNull()
        if (existing != null) {
            if (favorite) setFavorite(existing._id, true)
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
            favorite = favorite,
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

    fun syncChapters(mangaId: Long, chapters: List<SChapter>): List<Chapters> {
        requireNotNull(manga(mangaId)) { "Unknown manga: $mangaId" }
        database.transaction {
            chapters.forEachIndexed { order, chapter ->
                val existing = database.chaptersQueries.getChapterByUrlAndMangaId(chapter.url, mangaId)
                    .executeAsOneOrNull()
                if (existing == null) {
                    database.chaptersQueries.insertReturningId(
                        mangaId = mangaId,
                        url = chapter.url,
                        name = chapter.name,
                        scanlator = chapter.scanlator,
                        read = false,
                        bookmark = false,
                        lastPageRead = 0,
                        chapterNumber = chapter.chapter_number.toDouble(),
                        sourceOrder = order.toLong(),
                        dateFetch = System.currentTimeMillis(),
                        dateUpload = chapter.date_upload,
                        version = 0,
                        memo = chapter.memo,
                    ).executeAsOne()
                } else {
                    driver.execute(
                        null,
                        "UPDATE chapters SET name = ?, scanlator = ?, chapter_number = ?, source_order = ?, date_upload = ? WHERE _id = ?",
                        6,
                    ) {
                        bindString(0, chapter.name)
                        bindString(1, chapter.scanlator)
                        bindDouble(2, chapter.chapter_number.toDouble())
                        bindLong(3, order.toLong())
                        bindLong(4, chapter.date_upload)
                        bindLong(5, existing._id)
                    }
                }
            }
        }
        return database.chaptersQueries.getChaptersByMangaId(mangaId, 0).executeAsList()
    }

    fun chapter(mangaId: Long, chapterUrl: String): Chapters? =
        database.chaptersQueries.getChapterByUrlAndMangaId(chapterUrl, mangaId).executeAsOneOrNull()

    fun saveProgress(chapterId: Long, pageIndex: Int, pageCount: Int, readTimeMillis: Long = 0) {
        require(pageCount > 0 && pageIndex in 0 until pageCount)
        require(readTimeMillis >= 0)
        database.transaction {
            driver.execute(null, "UPDATE chapters SET last_page_read = ?, read = ? WHERE _id = ?", 3) {
                bindLong(0, pageIndex.toLong())
                bindBoolean(1, pageIndex == pageCount - 1)
                bindLong(2, chapterId)
            }
            database.historyQueries.upsert(chapterId, Date(), readTimeMillis)
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
