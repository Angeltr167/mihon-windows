package mihon.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import mihon.desktop.data.DesktopMangaRepository
import mihon.platform.desktop.DesktopSecretStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Desktop adapter for the existing MangaUpdates REST tracker identity and status values. */
internal class DesktopMangaUpdatesTracker(
    private val client: OkHttpClient,
    private val library: DesktopMangaRepository,
    private val secrets: DesktopSecretStore,
    private val baseUrl: String = "https://api.mangaupdates.com",
) : DesktopTrackProgressUpdater {
    private val syncLock = Mutex()

    val isLoggedIn: Boolean get() = runCatching { secrets.read(TOKEN_KEY) }.getOrNull() != null

    suspend fun login(username: String, password: String): String {
        require(username.isNotBlank() && password.isNotBlank())
        val body = buildJsonObject {
            put("username", username)
            put("password", password)
        }
        val context = requireNotNull(request("/v1/account/login", "PUT", body)).jsonObject["context"]?.jsonObject
            ?: error("MangaUpdates did not return a session")
        val token = context["session_token"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: error("MangaUpdates did not return a session token")
        val profile = requireNotNull(request("/v1/account/profile", token = token)).jsonObject
        val displayName = profile["username"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: error("MangaUpdates did not identify the account")
        secrets.write(TOKEN_KEY, token)
        return displayName
    }

    fun logout() = secrets.remove(TOKEN_KEY)

    suspend fun bind(mangaId: Long, seriesId: Long) = syncLock.withLock {
        require(seriesId > 0)
        val details = requireNotNull(request("/v1/series/$seriesId")).jsonObject
        val title = details["title"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: error("MangaUpdates series has no title")
        val url = details["url"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: "https://www.mangaupdates.com/series/$seriesId"
        val token = secrets.read(TOKEN_KEY) ?: error("Sign in to MangaUpdates first")
        val existing = listItem(seriesId, token)
        val item = if (existing == null) {
            val body = buildJsonArray {
                add(
                    buildJsonObject {
                        putJsonObject("series") { put("id", seriesId) }
                        put("list_id", WISH_LIST)
                    },
                )
            }
            request("/v1/lists/series", "POST", body, token)
            listItem(seriesId, token) ?: error("MangaUpdates did not add the series")
        } else {
            existing
        }
        withContext(Dispatchers.IO) {
            library.addTrack(
                mangaId = mangaId,
                trackerId = TRACKER_ID,
                title = title,
                remoteUrl = url,
                lastChapterRead = item.progress,
                totalChapters = 0,
                status = item.status,
                remoteId = seriesId,
            )
        }
    }

    override suspend fun syncCompletedChapter(mangaId: Long, chapterNumber: Double) = syncLock.withLock {
        if (!chapterNumber.isFinite() || chapterNumber <= 0) return@withLock
        val track = withContext(Dispatchers.IO) { library.track(mangaId, TRACKER_ID) } ?: return@withLock
        if (chapterNumber <= track.last_chapter_read) return@withLock
        check(track.remote_id > 0)
        val token = secrets.read(TOKEN_KEY) ?: error("Sign in to MangaUpdates first")
        val body = buildJsonArray {
            add(
                buildJsonObject {
                    putJsonObject("series") { put("id", track.remote_id) }
                    put("list_id", READING_LIST)
                    putJsonObject("status") { put("chapter", chapterNumber.toInt()) }
                },
            )
        }
        request("/v1/lists/series/update", "POST", body, token)
        val item = listItem(track.remote_id, token) ?: error("MangaUpdates did not confirm progress")
        withContext(Dispatchers.IO) {
            library.updateTrackProgress(track, item.progress, track.total_chapters, item.status)
        }
    }

    private suspend fun listItem(seriesId: Long, token: String): ListItem? {
        val response = request("/v1/lists/series/$seriesId", token = token, allowNotFound = true) ?: return null
        val item = response.jsonObject
        val status = item["list_id"]?.jsonPrimitive?.longOrNull ?: READING_LIST
        require(status in 0L..4L) { "Invalid MangaUpdates list status" }
        val progress = (item["status"] as? JsonObject)?.get("chapter")?.jsonPrimitive?.doubleOrNull ?: 0.0
        check(progress.isFinite() && progress >= 0)
        return ListItem(status, progress)
    }

    private suspend fun request(
        path: String,
        method: String = "GET",
        body: kotlinx.serialization.json.JsonElement? = null,
        token: String? = null,
        allowNotFound: Boolean = false,
    ): kotlinx.serialization.json.JsonElement? = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url("$baseUrl$path")
        token?.let { builder.header("Authorization", "Bearer $it") }
        when (method) {
            "PUT" -> builder.put(requireNotNull(body).toString().toRequestBody(JSON_MEDIA_TYPE))
            "POST" -> builder.post(requireNotNull(body).toString().toRequestBody(JSON_MEDIA_TYPE))
            else -> builder.get()
        }
        client.newCall(builder.build()).execute().use { response ->
            if (allowNotFound && response.code == 404) return@use null
            check(response.isSuccessful) { "MangaUpdates request failed: HTTP ${response.code}" }
            Json.parseToJsonElement(response.body.string())
        }
    }

    private data class ListItem(val status: Long, val progress: Double)

    companion object {
        const val TRACKER_ID = 7L
        private const val TOKEN_KEY = "mangaupdates-token"
        private const val READING_LIST = 0L
        private const val WISH_LIST = 1L
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
