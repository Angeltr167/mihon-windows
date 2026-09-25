package mihon.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import mihon.desktop.data.DesktopMangaRepository
import mihon.platform.desktop.DesktopSecretStore
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** Bangumi's Mihon OAuth client and v0 collection API on Desktop. */
internal class DesktopBangumiTracker(
    private val client: OkHttpClient,
    private val library: DesktopMangaRepository,
    private val secrets: DesktopSecretStore,
    private val apiBaseUrl: String = "https://api.bgm.tv",
    private val oauthBaseUrl: String = "https://bgm.tv",
    private val clockSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) : DesktopTrackProgressUpdater {
    private val syncLock = Mutex()
    private val authLock = Mutex()

    val isLoggedIn: Boolean get() = runCatching { secrets.read(TOKEN_KEY) }.getOrNull() != null

    fun beginLogin(): String = "$oauthBaseUrl/oauth/authorize?client_id=$CLIENT_ID" +
        "&response_type=code&redirect_uri=mihon%3A%2F%2Fbangumi-auth"

    suspend fun loginFromCallback(raw: String): String {
        val uri = URI(raw.trim())
        require(
            uri.scheme == "mihon" && uri.host == "bangumi-auth" && uri.userInfo == null &&
                uri.port == -1 && uri.rawPath.isNullOrEmpty() && uri.rawFragment == null,
        ) { "Paste the Bangumi redirect URL beginning mihon://bangumi-auth" }
        val matches = uri.rawQuery.orEmpty().split('&').map { it.split('=', limit = 2) }
            .filter { it.size == 2 && it[0] == "code" }
        require(matches.size == 1) { "Missing or duplicate Bangumi code" }
        val code = URLDecoder.decode(matches.single()[1], Charsets.UTF_8)
        require(code.length in 8..8192)
        val token = tokenRequest(
            FormBody.Builder().add("grant_type", "authorization_code")
                .add("client_id", CLIENT_ID).add("client_secret", CLIENT_SECRET)
                .add("code", code).add("redirect_uri", REDIRECT_URL).build(),
        )
        val user = get("/v0/me", token.accessToken) ?: error("Bangumi did not identify the account")
        val username = user["username"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: error("Bangumi account has no username")
        secrets.write(TOKEN_KEY, token.json)
        secrets.write(USERNAME_KEY, username)
        return user["nickname"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
            ?.takeIf { it.isNotBlank() } ?: username
    }

    fun logout() {
        secrets.remove(TOKEN_KEY)
        secrets.remove(USERNAME_KEY)
    }

    suspend fun bind(mangaId: Long, remoteId: Long) = syncLock.withLock {
        require(remoteId > 0)
        val subject = get("/v0/subjects/$remoteId") ?: error("Bangumi manga not found")
        require(subject["id"]?.jsonPrimitive?.longOrNull == remoteId) { "Bangumi returned a different subject" }
        val title = subject["name_cn"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: subject["name"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: error("Bangumi manga has no title")
        val platform = subject["platform"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
        require(platform == null || platform == "漫画") { "Bangumi subject is not manga" }
        val username = secrets.read(USERNAME_KEY) ?: error("Sign in to Bangumi first")
        val collection = get(
            "/v0/users/${URLEncoder.encode(username, Charsets.UTF_8)}/collections/$remoteId",
            allowNotFound = true,
        ) ?: run {
            updateRemote(remoteId, 1, 0, 0, false, "POST")
            get("/v0/users/${URLEncoder.encode(username, Charsets.UTF_8)}/collections/$remoteId")
                ?: error("Bangumi did not create the collection")
        }
        withContext(Dispatchers.IO) {
            library.addTrack(
                mangaId,
                TRACKER_ID,
                title,
                "$oauthBaseUrl/subject/$remoteId",
                collection["ep_status"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                collection["subject"]?.takeIf { it !is JsonNull }?.jsonObject?.get("eps")
                    ?.jsonPrimitive?.longOrNull ?: 0,
                statusCode(collection["type"]?.jsonPrimitive?.longOrNull ?: 1),
                remoteId,
            )
        }
    }

    override suspend fun syncCompletedChapter(mangaId: Long, chapterNumber: Double) = syncLock.withLock {
        if (!chapterNumber.isFinite() || chapterNumber <= 0) return@withLock
        val track = withContext(Dispatchers.IO) { library.track(mangaId, TRACKER_ID) } ?: return@withLock
        if (chapterNumber <= track.last_chapter_read) return@withLock
        val status = if (track.total_chapters > 0 && chapterNumber >= track.total_chapters) 2L else 3L
        updateRemote(track.remote_id, status, track.score.toInt(), chapterNumber.toInt(), track.private_, "PATCH")
        withContext(Dispatchers.IO) {
            library.updateTrackProgress(track, chapterNumber.toInt().toDouble(), track.total_chapters, status)
        }
    }

    private suspend fun updateRemote(
        remoteId: Long,
        status: Long,
        score: Int,
        progress: Int,
        isPrivate: Boolean,
        method: String,
    ) = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("type", status)
            put("rate", score.coerceIn(0, 10))
            put("ep_status", progress)
            put("private", isPrivate)
        }
        val request = Request.Builder().url("$apiBaseUrl/v0/users/-/collections/$remoteId")
            .header("Authorization", "Bearer ${validToken()}")
            .header("User-Agent", USER_AGENT)
            .method(method, payload.toString().toRequestBody(JSON)).build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Bangumi collection update failed: HTTP ${response.code}" }
        }
    }

    private suspend fun get(path: String, token: String? = null, allowNotFound: Boolean = false): JsonObject? =
        withContext(Dispatchers.IO) {
            val access = token ?: validToken()
            client.newCall(
                Request.Builder().url("$apiBaseUrl$path").header("Authorization", "Bearer $access")
                    .header("User-Agent", USER_AGENT).get().build(),
            ).execute().use { response ->
                if (allowNotFound && response.code == 404) return@withContext null
                check(response.isSuccessful) { "Bangumi request failed: HTTP ${response.code}" }
                Json.parseToJsonElement(response.body.string()) as? JsonObject
                    ?: error("Bangumi returned an invalid response")
            }
        }

    private suspend fun validToken(): String = authLock.withLock {
        val token = parseToken(secrets.read(TOKEN_KEY) ?: error("Sign in to Bangumi first"))
        if (clockSeconds() + 3600 < token.createdAt + token.expiresIn) return@withLock token.accessToken
        val refresh = token.refreshToken ?: error("Bangumi session expired; sign in again")
        val renewed = tokenRequest(
            FormBody.Builder().add("grant_type", "refresh_token")
                .add("client_id", CLIENT_ID).add("client_secret", CLIENT_SECRET)
                .add("refresh_token", refresh).add("redirect_uri", REDIRECT_URL).build(),
        )
        secrets.write(TOKEN_KEY, renewed.json)
        renewed.accessToken
    }

    private suspend fun tokenRequest(body: FormBody): Token = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url("$oauthBaseUrl/oauth/access_token").post(body).build())
            .execute().use { response ->
                check(response.isSuccessful) { "Bangumi sign-in failed: HTTP ${response.code}" }
                parseToken(response.body.string())
            }
    }

    private fun parseToken(raw: String): Token {
        val json = Json.parseToJsonElement(raw) as? JsonObject ?: error("Invalid Bangumi session")
        val access = json["access_token"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: error("Bangumi returned no access token")
        val created = json["created_at"]?.jsonPrimitive?.longOrNull ?: clockSeconds()
        val expires = json["expires_in"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 }
            ?: error("Bangumi returned an invalid lifetime")
        val refresh = json["refresh_token"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
        val protected = buildJsonObject {
            put("access_token", access)
            put("created_at", created)
            put("expires_in", expires)
            put("refresh_token", refresh)
        }.toString()
        return Token(protected, access, refresh, created, expires)
    }

    private fun statusCode(status: Long): Long = status.takeIf { it in 1..5 } ?: error("Unknown Bangumi status")

    private data class Token(
        val json: String,
        val accessToken: String,
        val refreshToken: String?,
        val createdAt: Long,
        val expiresIn: Long,
    )

    companion object {
        const val TRACKER_ID = 5L
        private const val TOKEN_KEY = "bangumi-session"
        private const val USERNAME_KEY = "bangumi-username"
        private const val REDIRECT_URL = "mihon://bangumi-auth"
        private const val CLIENT_ID = "bgm291665acbd06a4c28"
        private const val CLIENT_SECRET = "43e5ce36b207de16e5d3cfd3e79118db"
        private const val USER_AGENT = "Mihon Windows (https://github.com/mihonapp/mihon)"
        private val JSON = "application/json".toMediaType()
    }
}
