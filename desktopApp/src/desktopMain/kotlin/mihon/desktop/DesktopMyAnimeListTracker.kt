package mihon.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import mihon.desktop.data.DesktopMangaRepository
import mihon.platform.desktop.DesktopSecretStore
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.Base64

/** MyAnimeList's existing PKCE OAuth client and v2 list API on Desktop. */
internal class DesktopMyAnimeListTracker(
    private val client: OkHttpClient,
    private val library: DesktopMangaRepository,
    private val secrets: DesktopSecretStore,
    private val oauthBaseUrl: String = "https://myanimelist.net/v1/oauth2",
    private val apiBaseUrl: String = "https://api.myanimelist.net/v2",
    private val clockSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) : DesktopTrackProgressUpdater {
    private val syncLock = Mutex()
    private val authLock = Mutex()

    val isLoggedIn: Boolean get() = runCatching { secrets.read(TOKEN_KEY) }.getOrNull() != null

    fun beginLogin(): String {
        val verifier = randomString()
        val state = randomString()
        secrets.write(PENDING_KEY, "${clockSeconds()}:$state:$verifier")
        return "$oauthBaseUrl/authorize?response_type=code&client_id=$CLIENT_ID" +
            "&code_challenge=$verifier&state=$state"
    }

    suspend fun loginFromCallback(raw: String): String {
        val uri = URI(raw.trim())
        require(
            uri.scheme == "mihon" && uri.host == "myanimelist-auth" && uri.userInfo == null &&
                uri.port == -1 && uri.rawPath.isNullOrEmpty() && uri.rawFragment == null,
        ) { "Paste the MyAnimeList redirect URL beginning mihon://myanimelist-auth" }
        val parameters = uri.rawQuery.orEmpty().split('&').map { it.split('=', limit = 2) }
        fun single(name: String): String {
            val matching = parameters.filter { it.size == 2 && it[0] == name }
            require(matching.size == 1) { "Missing or duplicate MyAnimeList $name" }
            return URLDecoder.decode(matching.single()[1], Charsets.UTF_8)
        }
        val code = single("code")
        val state = single("state")
        require(code.length in 8..8192 && state.length in 32..128)
        val pending = secrets.read(PENDING_KEY)?.split(':')
        require(
            pending?.size == 3 && pending[1] == state &&
                clockSeconds() - (pending[0].toLongOrNull() ?: 0) in 0..600,
        ) { "MyAnimeList sign-in expired or callback state did not match" }
        val verifier = pending[2]
        val token = tokenRequest(
            FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("code", code)
                .add("code_verifier", verifier)
                .add("grant_type", "authorization_code")
                .build(),
        )
        val user = apiRequest("/users/@me", token.accessToken)
        val name = user["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: error("MyAnimeList did not identify the account")
        secrets.write(TOKEN_KEY, token.json)
        secrets.remove(PENDING_KEY)
        return name
    }

    fun logout() {
        secrets.remove(TOKEN_KEY)
        secrets.remove(PENDING_KEY)
    }

    suspend fun bind(mangaId: Long, remoteId: Long) = syncLock.withLock {
        require(remoteId > 0)
        val details = apiRequest(
            "/manga/$remoteId?fields=num_chapters,my_list_status%7Bstart_date,finish_date%7D",
        )
        val title = details["title"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: error("MyAnimeList manga has no title")
        val total = details["num_chapters"]?.jsonPrimitive?.longOrNull?.coerceAtLeast(0) ?: 0
        val existing = details["my_list_status"] as? JsonObject
        val status = existing ?: updateRemote(remoteId, 0, "plan_to_read", false, 0.0)
        withContext(Dispatchers.IO) {
            library.addTrack(
                mangaId = mangaId,
                trackerId = TRACKER_ID,
                title = title,
                remoteUrl = "https://myanimelist.net/manga/$remoteId",
                lastChapterRead = status["num_chapters_read"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                totalChapters = total,
                status = statusCode(status),
                remoteId = remoteId,
            )
        }
    }

    override suspend fun syncCompletedChapter(mangaId: Long, chapterNumber: Double) = syncLock.withLock {
        if (!chapterNumber.isFinite() || chapterNumber <= 0) return@withLock
        val track = withContext(Dispatchers.IO) { library.track(mangaId, TRACKER_ID) } ?: return@withLock
        if (chapterNumber <= track.last_chapter_read) return@withLock
        val status = if (track.total_chapters > 0 && chapterNumber >= track.total_chapters) "completed" else "reading"
        val updated = updateRemote(track.remote_id, chapterNumber.toInt(), status, false, track.score)
        val confirmed = updated["num_chapters_read"]?.jsonPrimitive?.content?.toDoubleOrNull()
            ?: error("MyAnimeList did not confirm progress")
        withContext(Dispatchers.IO) {
            library.updateTrackProgress(track, confirmed, track.total_chapters, statusCode(updated))
        }
    }

    private suspend fun updateRemote(
        remoteId: Long,
        progress: Int,
        status: String,
        rereading: Boolean,
        score: Double,
    ): JsonObject = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("status", status)
            .add("is_rereading", rereading.toString())
            .add("score", score.toInt().toString())
            .add("num_chapters_read", progress.toString())
            .build()
        val token = validToken()
        client.newCall(
            Request.Builder().url("$apiBaseUrl/manga/$remoteId/my_list_status")
                .header("Authorization", "Bearer $token")
                .put(body)
                .build(),
        ).execute().use { response ->
            check(response.isSuccessful) { "MyAnimeList update failed: HTTP ${response.code}" }
            Json.parseToJsonElement(response.body.string()).jsonObject
        }
    }

    private suspend fun apiRequest(path: String, token: String? = null): JsonObject = withContext(Dispatchers.IO) {
        val access = token ?: validToken()
        client.newCall(
            Request.Builder().url("$apiBaseUrl$path")
                .header("Authorization", "Bearer $access")
                .get()
                .build(),
        ).execute().use { response ->
            check(response.isSuccessful) { "MyAnimeList request failed: HTTP ${response.code}" }
            Json.parseToJsonElement(response.body.string()).jsonObject
        }
    }

    private suspend fun validToken(): String = authLock.withLock {
        val stored = secrets.read(TOKEN_KEY) ?: error("Sign in to MyAnimeList first")
        val token = parseToken(stored)
        if (clockSeconds() + 60 < token.createdAt + token.expiresIn) return@withLock token.accessToken
        val refreshed = tokenRequest(
            FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("refresh_token", token.refreshToken)
                .add("grant_type", "refresh_token")
                .build(),
        )
        secrets.write(TOKEN_KEY, refreshed.json)
        refreshed.accessToken
    }

    private suspend fun tokenRequest(body: FormBody): Token = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url("$oauthBaseUrl/token").post(body).build()).execute().use { response ->
            check(response.isSuccessful) { "MyAnimeList sign-in failed: HTTP ${response.code}" }
            parseToken(response.body.string())
        }
    }

    private fun parseToken(raw: String): Token {
        val parsed = Json.parseToJsonElement(raw).jsonObject
        val access = parsed["access_token"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: error("MyAnimeList returned no access token")
        val refresh = parsed["refresh_token"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: error("MyAnimeList returned no refresh token")
        val expires = parsed["expires_in"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 }
            ?: error("MyAnimeList returned an invalid lifetime")
        val created = parsed["created_at"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.longOrNull ?: clockSeconds()
        val json = buildJsonObject {
            put("access_token", access)
            put("refresh_token", refresh)
            put("expires_in", expires)
            put("created_at", created)
        }.toString()
        return Token(json, access, refresh, expires, created)
    }

    private fun statusCode(status: JsonObject): Long {
        if (status["is_rereading"]?.jsonPrimitive?.content == "true") return 7L
        return when (status["status"]?.jsonPrimitive?.content) {
            "reading" -> 1L
            "completed" -> 2L
            "on_hold" -> 3L
            "dropped" -> 4L
            "plan_to_read" -> 6L
            else -> error("Unknown MyAnimeList status")
        }
    }

    private fun randomString(): String = ByteArray(48).also(SecureRandom()::nextBytes).let {
        Base64.getUrlEncoder().withoutPadding().encodeToString(it)
    }

    private data class Token(
        val json: String,
        val accessToken: String,
        val refreshToken: String,
        val expiresIn: Long,
        val createdAt: Long,
    )

    companion object {
        const val TRACKER_ID = 1L
        private const val TOKEN_KEY = "myanimelist-session"
        private const val PENDING_KEY = "myanimelist-pkce"
        private const val CLIENT_ID = "c46c9e24640a64dad5be5ca7a1a53a0f"
    }
}
