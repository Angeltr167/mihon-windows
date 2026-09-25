package mihon.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mihon.desktop.data.DesktopMangaRepository
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI

/** Komga uses the same authenticated HTTP session as its source extension. */
internal class DesktopKomgaTracker(
    private val client: OkHttpClient,
    private val library: DesktopMangaRepository,
) : DesktopTrackProgressUpdater {
    private val syncLock = Mutex()

    suspend fun bind(mangaId: Long, title: String, seriesUrl: String) = syncLock.withLock {
        val progress = readProgress(seriesUrl)
        withContext(Dispatchers.IO) {
            library.addTrack(
                mangaId,
                TRACKER_ID,
                title,
                seriesUrl,
                progress.lastRead,
                progress.total,
                progress.status,
            )
        }
    }

    override suspend fun syncCompletedChapter(mangaId: Long, chapterNumber: Double) = syncLock.withLock {
        if (!chapterNumber.isFinite() || chapterNumber <= 0) return@withLock
        val track = withContext(Dispatchers.IO) { library.track(mangaId, TRACKER_ID) } ?: return@withLock
        if (chapterNumber <= track.last_chapter_read) return@withLock
        val progressUrl = progressUrl(track.remote_url)
        val payload = """{"lastBookNumberSortRead":$chapterNumber}"""
        withContext(Dispatchers.IO) {
            client.newCall(
                Request.Builder().url(progressUrl).put(payload.toRequestBody(JSON_MEDIA_TYPE)).build(),
            ).execute().use { response ->
                check(response.isSuccessful) { "Komga progress update failed: HTTP ${response.code}" }
            }
        }
        val refreshed = readProgress(track.remote_url)
        withContext(Dispatchers.IO) {
            library.updateTrackProgress(track, refreshed.lastRead, refreshed.total, refreshed.status)
        }
    }

    private suspend fun readProgress(seriesUrl: String): Progress = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(progressUrl(seriesUrl)).get().build()).execute().use { response ->
            check(response.isSuccessful) { "Komga progress lookup failed: HTTP ${response.code}" }
            val body = response.body.string()
            val json = Json.parseToJsonElement(body).jsonObject
            val total = json["maxNumberSort"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            val lastRead = json["lastReadContinuousNumberSort"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            val booksCount = json["booksCount"]?.jsonPrimitive?.intOrNull ?: 0
            val booksRead = json["booksReadCount"]?.jsonPrimitive?.intOrNull ?: 0
            val unread = json["booksUnreadCount"]?.jsonPrimitive?.intOrNull ?: 0
            check(total.isFinite() && lastRead.isFinite() && total >= 0 && lastRead >= 0) {
                "Komga returned invalid progress"
            }
            Progress(
                lastRead = lastRead,
                total = total.toLong(),
                status = when {
                    booksCount > 0 && unread == 0 -> COMPLETED
                    booksRead > 0 || lastRead > 0 -> READING
                    else -> UNREAD
                },
            )
        }
    }

    private fun progressUrl(seriesUrl: String): String {
        val uri = URI(seriesUrl.trim())
        require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null)
        require(uri.query == null && uri.fragment == null)
        val path = uri.rawPath.orEmpty()
        val marker = "/api/v1/series/"
        val index = path.lastIndexOf(marker)
        require(index >= 0 && path.substring(index + marker.length).matches(Regex("[A-Za-z0-9_-]+"))) {
            "Only Komga series API links can be tracked"
        }
        val prefix = path.substring(0, index)
        val id = path.substring(index + marker.length)
        return URI(
            uri.scheme,
            null,
            uri.host,
            uri.port,
            "$prefix/api/v2/series/$id/read-progress/tachiyomi",
            null,
            null,
        )
            .toString()
    }

    private data class Progress(val lastRead: Double, val total: Long, val status: Long)

    companion object {
        const val TRACKER_ID = 6L // Existing Android Komga tracker ID and backup/database identity.
        private const val UNREAD = 1L
        private const val READING = 2L
        private const val COMPLETED = 3L
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
