package mihon.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import mihon.desktop.data.DesktopMangaRepository
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI

internal data class DesktopTrackerSourceSession(
    val baseUrl: String,
    val client: OkHttpClient,
    val headers: Headers,
)

/** Suwayomi tracking uses the installed Tachidesk source's HTTP session, as on Android. */
internal class DesktopSuwayomiTracker(
    private val library: DesktopMangaRepository,
    private val sourceForId: (Long) -> DesktopTrackerSourceSession?,
) : DesktopTrackProgressUpdater {
    private val syncLock = Mutex()

    suspend fun bind(mangaId: Long, sourceId: Long, mangaUrl: String) = syncLock.withLock {
        val session = sourceForId(sourceId) ?: error("Suwayomi source is not available")
        val id = mangaIdFromUrl(mangaUrl)
        val remote = readManga(session, id)
        withContext(Dispatchers.IO) {
            library.addTrack(
                mangaId = mangaId,
                trackerId = TRACKER_ID,
                title = remote.title,
                remoteUrl = "${validatedBaseUrl(session.baseUrl)}/manga/$id",
                lastChapterRead = remote.lastRead,
                totalChapters = remote.total.toLong(),
                status = remote.status,
                remoteId = id.toLong(),
            )
        }
    }

    override suspend fun syncCompletedChapter(mangaId: Long, chapterNumber: Double) = syncLock.withLock {
        if (!chapterNumber.isFinite() || chapterNumber <= 0) return@withLock
        val track = withContext(Dispatchers.IO) { library.track(mangaId, TRACKER_ID) } ?: return@withLock
        if (chapterNumber <= track.last_chapter_read) return@withLock
        val sourceId = withContext(Dispatchers.IO) { library.manga(mangaId)?.source }
            ?: error("Tracked manga is missing")
        val session = sourceForId(sourceId) ?: error("Suwayomi source is not available")
        check(track.remote_id in 1..Int.MAX_VALUE.toLong()) { "Invalid Suwayomi manga ID" }
        val id = track.remote_id.toInt()
        val unread = request(
            session,
            UNREAD_CHAPTERS_QUERY,
            buildJsonObject { put("mangaId", id) },
        ).data("chapters")["nodes"]?.jsonArray ?: error("Suwayomi did not return unread chapters")
        val ids = unread.mapNotNull { node ->
            val chapter = node.jsonObject
            val number = chapter["chapterNumber"]?.jsonPrimitive?.content?.toDoubleOrNull()
            val chapterId = chapter["id"]?.jsonPrimitive?.intOrNull
            chapterId?.takeIf { number != null && number <= chapterNumber + 0.001 }
        }
        if (ids.isNotEmpty()) {
            request(
                session,
                MARK_CHAPTERS_MUTATION,
                buildJsonObject {
                    putJsonArray("chapters") { ids.forEach { add(JsonPrimitive(it)) } }
                },
            )
        }
        request(session, TRACK_PROGRESS_MUTATION, buildJsonObject { put("mangaId", id) })
        val refreshed = readManga(session, id)
        withContext(Dispatchers.IO) {
            library.updateTrackProgress(track, refreshed.lastRead, refreshed.total.toLong(), refreshed.status)
        }
    }

    private suspend fun readManga(session: DesktopTrackerSourceSession, id: Int): RemoteManga {
        val manga = request(session, MANGA_QUERY, buildJsonObject { put("mangaId", id) }).data("manga")
        val title = manga["title"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: error("Suwayomi manga has no title")
        val total = manga["chapters"]?.jsonObject?.get("totalCount")?.jsonPrimitive?.intOrNull
            ?: error("Suwayomi did not return chapter count")
        val unread = manga["unreadCount"]?.jsonPrimitive?.intOrNull
            ?: error("Suwayomi did not return unread count")
        val lastRead = (manga["latestReadChapter"] as? JsonObject)?.get("chapterNumber")
            ?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
        check(total >= 0 && unread in 0..total && lastRead.isFinite() && lastRead >= 0)
        return RemoteManga(
            title,
            total,
            lastRead,
            when {
                unread == total -> UNREAD
                unread == 0 -> COMPLETED
                else -> READING
            },
        )
    }

    private suspend fun request(
        session: DesktopTrackerSourceSession,
        query: String,
        variables: JsonObject,
    ): JsonObject = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder().url("${validatedBaseUrl(session.baseUrl)}/api/graphql")
            .headers(session.headers)
            .post(body)
            .build()
        session.client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Suwayomi request failed: HTTP ${response.code}" }
            val json = Json.parseToJsonElement(response.body.string()).jsonObject
            check(json["errors"] == null) { "Suwayomi rejected the request" }
            json
        }
    }

    private fun JsonObject.data(field: String): JsonObject =
        this["data"]?.jsonObject?.get(field)?.jsonObject ?: error("Suwayomi response lacks $field")

    private fun mangaIdFromUrl(url: String): Int =
        url.trimEnd('/').substringAfterLast('/').toIntOrNull()?.takeIf { it > 0 }
            ?: error("Invalid Suwayomi manga URL")

    private fun validatedBaseUrl(raw: String): String {
        val value = raw.trimEnd('/')
        val uri = URI(value)
        require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null)
        require(uri.rawQuery == null && uri.rawFragment == null)
        return value
    }

    private data class RemoteManga(val title: String, val total: Int, val lastRead: Double, val status: Long)

    companion object {
        const val TRACKER_ID = 9L
        const val SOURCE_CLASS = "eu.kanade.tachiyomi.extension.all.tachidesk.Tachidesk"
        private const val UNREAD = 1L
        private const val READING = 2L
        private const val COMPLETED = 3L
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val MANGA_QUERY =
            "query (${'$'}mangaId: Int!) { manga(id: ${'$'}mangaId) " +
                "{ title chapters { totalCount } latestReadChapter { chapterNumber } unreadCount } }"
        private const val UNREAD_CHAPTERS_QUERY =
            "query (${'$'}mangaId: Int!) { chapters(condition: {mangaId: ${'$'}mangaId, isRead: false}) " +
                "{ nodes { id chapterNumber } } }"
        private const val MARK_CHAPTERS_MUTATION =
            "mutation (${'$'}chapters: [Int!]!) { updateChapters(input: " +
                "{ids: ${'$'}chapters, patch: {isRead: true}}) { __typename } }"
        private const val TRACK_PROGRESS_MUTATION =
            "mutation (${'$'}mangaId: Int!) { trackProgress(input: {mangaId: ${'$'}mangaId}) { __typename } }"
    }
}
