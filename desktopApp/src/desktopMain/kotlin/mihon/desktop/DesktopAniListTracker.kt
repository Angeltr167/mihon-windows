package mihon.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mihon.desktop.data.DesktopMangaRepository
import mihon.platform.desktop.DesktopSecretStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.net.URLDecoder

/** Desktop AniList binding using Android's tracker ID and OAuth client registration. */
internal class DesktopAniListTracker(
    private val client: OkHttpClient,
    private val library: DesktopMangaRepository,
    private val secrets: DesktopSecretStore,
    private val endpoint: String = "https://graphql.anilist.co/",
) : DesktopTrackProgressUpdater {
    private val syncLock = Mutex()

    val isLoggedIn: Boolean get() = runCatching { secrets.read(TOKEN_KEY) }.getOrNull() != null

    suspend fun loginFromCallback(raw: String): String {
        val uri = URI(raw.trim())
        require(
            uri.scheme == "mihon" && uri.host == "anilist-auth" && uri.userInfo == null &&
                uri.port == -1 && uri.rawPath.isNullOrEmpty() && uri.rawQuery == null && uri.rawFragment != null,
        ) {
            "Paste the AniList redirect URL beginning mihon://anilist-auth"
        }
        val parameters = uri.rawFragment.split('&').map { it.split('=', limit = 2) }
        require(parameters.count { it.firstOrNull() == "access_token" && it.size == 2 } == 1) {
            "Missing AniList access token"
        }
        val token = URLDecoder.decode(parameters.first { it[0] == "access_token" }[1], Charsets.UTF_8)
        require(token.matches(Regex("[A-Za-z0-9._~-]{20,8192}"))) { "Invalid AniList access token" }
        val viewer = request(VIEWER_QUERY, token = token).data("Viewer")
        val name = viewer["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: error("AniList did not identify the account")
        secrets.write(TOKEN_KEY, token)
        return name
    }

    fun logout() = secrets.remove(TOKEN_KEY)

    suspend fun bind(mangaId: Long, mediaId: Int) = syncLock.withLock {
        require(mediaId > 0)
        val media = request(
            MEDIA_QUERY,
            buildJsonObject { put("id", mediaId) },
        ).data("Media")
        val actualId = media["id"]?.jsonPrimitive?.intOrNull ?: error("AniList manga not found")
        val title = media["title"]?.jsonObject?.get("userPreferred")?.jsonPrimitive?.content
            ?: error("AniList manga has no title")
        val entry = media["mediaListEntry"] as? JsonObject
        val total = media["chapters"]?.jsonPrimitive?.intOrNull?.coerceAtLeast(0) ?: 0
        withContext(Dispatchers.IO) {
            library.addTrack(
                mangaId = mangaId,
                trackerId = TRACKER_ID,
                title = title,
                remoteUrl = "https://anilist.co/manga/$actualId",
                lastChapterRead = entry?.get("progress")?.jsonPrimitive?.intOrNull?.toDouble() ?: 0.0,
                totalChapters = total.toLong(),
                status = statusCode(entry?.get("status")?.jsonPrimitive?.content),
                remoteId = actualId.toLong(),
                libraryId = entry?.get("id")?.jsonPrimitive?.intOrNull?.toLong(),
            )
        }
    }

    override suspend fun syncCompletedChapter(mangaId: Long, chapterNumber: Double) = syncLock.withLock {
        if (!chapterNumber.isFinite() || chapterNumber <= 0) return@withLock
        val track = withContext(Dispatchers.IO) { library.track(mangaId, TRACKER_ID) } ?: return@withLock
        if (chapterNumber <= track.last_chapter_read) return@withLock
        check(track.remote_id in 1..Int.MAX_VALUE.toLong()) { "Invalid AniList media ID" }
        val progress = chapterNumber.toInt().coerceAtLeast(1)
        val status = if (track.total_chapters > 0 && progress >= track.total_chapters) "COMPLETED" else "CURRENT"
        val entry = request(
            SAVE_PROGRESS_MUTATION,
            buildJsonObject {
                put("id", track.remote_id.toInt())
                put("progress", progress)
                put("status", status)
            },
        ).data("SaveMediaListEntry")
        val actual = entry["progress"]?.jsonPrimitive?.intOrNull ?: error("AniList did not confirm progress")
        withContext(Dispatchers.IO) {
            library.updateTrackProgress(track, actual.toDouble(), track.total_chapters, statusCode(status))
        }
    }

    private suspend fun request(
        query: String,
        variables: JsonObject = JsonObject(emptyMap()),
        token: String = secrets.read(TOKEN_KEY) ?: error("Sign in to AniList first"),
    ): JsonObject = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        client.newCall(
            Request.Builder().url(endpoint).header("Authorization", "Bearer $token").post(body).build(),
        ).execute().use { response ->
            check(response.isSuccessful) { "AniList request failed: HTTP ${response.code}" }
            val json = Json.parseToJsonElement(response.body.string()).jsonObject
            check(json["errors"] == null) { "AniList rejected the request" }
            json
        }
    }

    private fun JsonObject.data(field: String): JsonObject =
        this["data"]?.jsonObject?.get(field)?.jsonObject ?: error("AniList response lacks $field")

    private fun statusCode(status: String?): Long = when (status) {
        "CURRENT" -> 1L
        "COMPLETED" -> 2L
        "PAUSED" -> 3L
        "DROPPED" -> 4L
        "PLANNING" -> 5L
        "REPEATING" -> 6L
        null -> 5L
        else -> error("Unknown AniList status")
    }

    companion object {
        const val TRACKER_ID = 2L
        const val AUTH_URL = "https://anilist.co/api/v2/oauth/authorize?client_id=16329&response_type=token"
        private const val TOKEN_KEY = "anilist-token"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val VIEWER_QUERY = "query { Viewer { id name } }"
        private const val MEDIA_QUERY =
            "query (${'$'}id: Int!) { Media(id: ${'$'}id, type: MANGA) " +
                "{ id title { userPreferred } chapters mediaListEntry { id progress status } } }"
        private const val SAVE_PROGRESS_MUTATION =
            "mutation (${'$'}id: Int!, ${'$'}progress: Int!, ${'$'}status: MediaListStatus!) " +
                "{ SaveMediaListEntry(mediaId: ${'$'}id, progress: ${'$'}progress, status: ${'$'}status) " +
                "{ id progress status } }"
    }
}
