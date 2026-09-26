package mihon.desktop.data

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import mihon.backup.shared.BackupImportSummary
import mihon.backup.shared.MihonBackup
import tachiyomi.data.Chapters
import tachiyomi.data.History
import tachiyomi.data.Manga_sync
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

    fun setChapterBookmark(chapterId: Long, bookmarked: Boolean) {
        driver.execute(null, "UPDATE chapters SET bookmark = ? WHERE _id = ?", 2) {
            bindBoolean(0, bookmarked)
            bindLong(1, chapterId)
        }
    }

    fun track(mangaId: Long, trackerId: Long): Manga_sync? =
        database.manga_syncQueries.getTracksByMangaId(mangaId).executeAsList()
            .firstOrNull { it.sync_id == trackerId }

    fun addTrack(
        mangaId: Long,
        trackerId: Long,
        title: String,
        remoteUrl: String,
        lastChapterRead: Double,
        totalChapters: Long,
        status: Long,
        remoteId: Long = 0,
        libraryId: Long? = null,
    ) {
        requireNotNull(manga(mangaId)) { "Unknown manga: $mangaId" }
        require(track(mangaId, trackerId) == null) { "Tracker is already bound" }
        database.manga_syncQueries.insert(
            mangaId = mangaId,
            syncId = trackerId,
            remoteId = remoteId,
            libraryId = libraryId,
            title = title,
            lastChapterRead = lastChapterRead,
            totalChapters = totalChapters,
            status = status,
            score = 0.0,
            remoteUrl = remoteUrl,
            startDate = 0,
            finishDate = 0,
            `private` = false,
        )
    }

    fun updateTrackProgress(track: Manga_sync, lastChapterRead: Double, totalChapters: Long, status: Long) {
        database.manga_syncQueries.update(
            mangaId = null,
            syncId = null,
            mediaId = null,
            libraryId = null,
            title = null,
            lastChapterRead = lastChapterRead,
            totalChapter = totalChapters,
            status = status,
            score = null,
            trackingUrl = null,
            startDate = null,
            finishDate = null,
            `private` = null,
            id = track._id,
        )
    }

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

    /** Merge an Android protobuf backup into Mihon's existing schema. */
    fun importBackup(backup: MihonBackup): BackupImportSummary {
        var importedChapters = 0
        var importedTrackers = 0
        val categoryIds = mutableMapOf<Long, Long>()

        database.transaction {
            backup.categories.sortedBy { it.order }.forEach { category ->
                val name = category.name.trim()
                if (name.isNotEmpty()) {
                    val existing = categories().firstOrNull { it.name == name }
                    val id = existing?.id ?: run {
                        database.categoriesQueries.insert(name, category.order, category.flags)
                        categories().first { it.name == name }.id
                    }
                    categoryIds[category.id] = id
                }
            }

            backup.manga.forEach { item ->
                require(item.url.isNotBlank()) { "Backup contains manga with an empty URL" }
                val manga = SManga.create().apply {
                    url = item.url
                    title = item.title.ifBlank { item.url }
                    artist = item.artist
                    author = item.author
                    description = item.description
                    genre = item.genre.takeIf(List<String>::isNotEmpty)?.joinToString(", ")
                    status = item.status
                    thumbnail_url = item.thumbnailUrl
                    initialized = item.initialized
                    update_strategy = UpdateStrategy.entries.getOrElse(item.updateStrategy) {
                        UpdateStrategy.ALWAYS_UPDATE
                    }
                    memo = decodeMemo(item.memo)
                }
                val stored = ensureManga(item.source, manga, item.favorite)
                val mangaId = stored._id
                setFavorite(mangaId, item.favorite)
                driver.execute(
                    null,
                    """UPDATE mangas SET artist = ?, author = ?, description = ?, genre = ?, title = ?,
                        status = ?, thumbnail_url = ?, date_added = ?, update_strategy = ?, initialized = ?,
                        viewer = ?, chapter_flags = ?, version = ?, notes = ?, memo = ?, is_syncing = 1
                        WHERE _id = ?
                    """.trimIndent(),
                    16,
                ) {
                    bindString(0, item.artist)
                    bindString(1, item.author)
                    bindString(2, item.description)
                    bindString(3, item.genre.takeIf(List<String>::isNotEmpty)?.joinToString(", "))
                    bindString(4, manga.title)
                    bindLong(5, item.status.toLong())
                    bindString(6, item.thumbnailUrl)
                    bindLong(7, item.dateAdded)
                    bindLong(8, manga.update_strategy.ordinal.toLong())
                    bindBoolean(9, item.initialized)
                    bindLong(10, (item.viewerFlags ?: 0).toLong())
                    bindLong(11, item.chapterFlags.toLong())
                    bindLong(12, item.version)
                    bindString(13, item.notes)
                    bindBytes(14, item.memo)
                    bindLong(15, mangaId)
                }

                val importedCategoryIds = item.categories.mapNotNull(categoryIds::get).toSet()
                database.mangas_categoriesQueries.deleteMangaCategoryByMangaId(mangaId)
                importedCategoryIds.forEach { database.mangas_categoriesQueries.insert(mangaId, it) }

                val chapterIds = mutableMapOf<String, Long>()
                item.chapters.forEach { chapter ->
                    require(chapter.url.isNotBlank()) { "Backup contains chapter with an empty URL" }
                    val existing = database.chaptersQueries.getChapterByUrlAndMangaId(chapter.url, mangaId)
                        .executeAsOneOrNull()
                    val id = existing?._id ?: database.chaptersQueries.insertReturningId(
                        mangaId = mangaId,
                        url = chapter.url,
                        name = chapter.name,
                        scanlator = chapter.scanlator,
                        read = chapter.read,
                        bookmark = chapter.bookmark,
                        lastPageRead = chapter.lastPageRead,
                        chapterNumber = chapter.chapterNumber.toDouble(),
                        sourceOrder = chapter.sourceOrder,
                        dateFetch = chapter.dateFetch,
                        dateUpload = chapter.dateUpload,
                        version = chapter.version,
                        memo = decodeMemo(chapter.memo),
                    ).executeAsOne()
                    driver.execute(
                        null,
                        """UPDATE chapters SET name = ?, scanlator = ?, read = ?, bookmark = ?, last_page_read = ?,
                                chapter_number = ?, source_order = ?, date_fetch = ?, date_upload = ?,
                                last_modified_at = ?, version = ?,
                                memo = ?, is_syncing = 1 WHERE _id = ?
                        """.trimIndent(),
                        13,
                    ) {
                        bindString(0, chapter.name)
                        bindString(1, chapter.scanlator)
                        bindBoolean(2, chapter.read)
                        bindBoolean(3, chapter.bookmark)
                        bindLong(4, chapter.lastPageRead)
                        bindDouble(5, chapter.chapterNumber.toDouble())
                        bindLong(6, chapter.sourceOrder)
                        bindLong(7, chapter.dateFetch)
                        bindLong(8, chapter.dateUpload)
                        bindLong(9, chapter.lastModifiedAt)
                        bindLong(10, chapter.version)
                        bindBytes(11, chapter.memo)
                        bindLong(12, id)
                    }
                    chapterIds[chapter.url] = id
                    importedChapters++
                }

                item.history.forEach { entry ->
                    chapterIds[entry.url]?.let { chapterId ->
                        driver.execute(
                            null,
                            """INSERT INTO history(chapter_id, last_read, time_read) VALUES (?, ?, ?)
                                ON CONFLICT(chapter_id) DO UPDATE SET last_read = excluded.last_read,
                                time_read = excluded.time_read
                            """.trimIndent(),
                            3,
                        ) {
                            bindLong(0, chapterId)
                            bindLong(1, entry.lastRead)
                            bindLong(2, entry.readDuration)
                        }
                    }
                }

                item.tracking.forEach { track ->
                    database.manga_syncQueries.insert(
                        mangaId = mangaId,
                        syncId = track.syncId.toLong(),
                        remoteId = if (track.mediaIdInt != 0) track.mediaIdInt.toLong() else track.mediaId,
                        libraryId = track.libraryId,
                        title = track.title,
                        lastChapterRead = track.lastChapterRead.toDouble(),
                        totalChapters = track.totalChapters.toLong(),
                        status = track.status.toLong(),
                        score = track.score.toDouble(),
                        remoteUrl = track.trackingUrl,
                        startDate = track.startedReadingDate,
                        finishDate = track.finishedReadingDate,
                        `private` = track.private,
                    )
                    importedTrackers++
                }
                driver.execute(null, "UPDATE mangas SET is_syncing = 0 WHERE _id = ?", 1) {
                    bindLong(0, mangaId)
                }
            }
        }

        return BackupImportSummary(
            manga = backup.manga.size,
            chapters = importedChapters,
            categories = categoryIds.size,
            trackerEntries = importedTrackers,
        )
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

        private fun decodeMemo(value: ByteArray): JsonObject =
            runCatching { Json.decodeFromString<JsonObject>(value.decodeToString()) }
                .getOrDefault(JsonObject(emptyMap()))
    }
}
