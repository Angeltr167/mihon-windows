package mihon.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** MangaBaka's PKCE OAuth and user-library API on Desktop. */
internal class DesktopMangaBakaTracker(
    private val client: OkHttpClient,
    private val library: DesktopMangaRepository,
    private val secrets: DesktopSecretStore,
    private val apiBaseUrl: String = "https://api.mangabaka.org",
    private val siteBaseUrl: String = "https://mangabaka.org",
    private val clockSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) : DesktopTrackProgressUpdater {
    private val syncLock = Mutex()
    private val authLock = Mutex()

    val isLoggedIn: Boolean get() = runCatching { secrets.read(TOKEN_KEY) }.getOrNull() != null

    fun beginLogin(): String {
        val verifier = randomString(48)
        val state = randomString(16)
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.encodeToByteArray()),
        )
        secrets.write(PENDING_KEY, "${clockSeconds()}:$state:$verifier")
        return "$siteBaseUrl/auth/oauth2/authorize?client_id=$CLIENT_ID" +
            "&code_challenge=$challenge&code_challenge_method=S256&response_type=code" +
            "&scope=library.read%20library.write%20offline_access%20openid" +
            "&redirect_uri=mihon%3A%2F%2Fmangabaka-auth&state=$state"
    }

    suspend fun loginFromCallback(raw: String): String {
        val uri = URI(raw.trim())
        require(
            uri.scheme == "mihon" && uri.host == "mangabaka-auth" && uri.userInfo == null &&
                uri.port == -1 && uri.rawPath.isNullOrEmpty() && uri.rawFragment == null,
        ) { "Paste the MangaBaka redirect URL beginning mihon://mangabaka-auth" }
        val parameters = uri.rawQuery.orEmpty().split('&').map { it.split('=', limit = 2) }
        fun single(name: String): String {
            val matching = parameters.filter { it.size == 2 && it[0] == name }
            require(matching.size == 1) { "Missing or duplicate MangaBaka $name" }
            return URLDecoder.decode(matching.single()[1], Charsets.UTF_8)
        }
        val code = single("code")
        val state = single("state")
        require(code.length in 8..8192 && state.length in 20..128)
        val pending = secrets.read(PENDING_KEY)?.split(':')
        require(
            pending?.size == 3 && pending[1] == state &&
                clockSeconds() - (pending[0].toLongOrNull() ?: 0) in 0..600,
        ) { "MangaBaka sign-in expired or callback state did not match" }
        val token = tokenRequest(
            FormBody.Builder().add("client_id", CLIENT_ID).add("code", code)
                .add("code_verifier", pending[2]).add("code_challenge_method", "S256")
                .add("grant_type", "authorization_code").add("redirect_uri", REDIRECT_URL)
                .add("scope", SCOPES).build(),
        )
        val profile = (
            get("/v1/my/profile", token.accessToken)
                ?: error("MangaBaka did not identify the account")
            ).getValue("data").jsonObject
        val name = sequenceOf("nickname", "preferred_username", "id")
            .mapNotNull { profile[it]?.takeIf { value -> value !is JsonNull }?.jsonPrimitive?.content }
            .firstOrNull { it.isNotBlank() } ?: error("MangaBaka did not identify the account")
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
        val item = (get("/v1/series/$remoteId") ?: error("MangaBaka series not found"))
            .getValue("data").jsonObject
        require(item["id"]?.jsonPrimitive?.longOrNull == remoteId) { "MangaBaka returned a different series" }
        val title = (item["titles"] as? JsonArray)?.mapNotNull { entry ->
            entry.jsonObject["title"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        }?.firstOrNull() ?: "MangaBaka series $remoteId"
        val libraryEntry = get("/v1/my/library/$remoteId", allowNotFound = true)?.get("data")?.jsonObject
        val state = libraryEntry?.get("state")?.jsonPrimitive?.content ?: "plan_to_read"
        val progress = libraryEntry?.get("progress_chapter")?.takeIf { it !is JsonNull }
            ?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
        if (libraryEntry == null) updateRemote(remoteId, "POST", state, progress, false, 0)
        withContext(Dispatchers.IO) {
            library.addTrack(
                mangaId,
                TRACKER_ID,
                title,
                "$siteBaseUrl/$remoteId",
                progress,
                0,
                statusCode(state),
                remoteId,
            )
        }
    }

    override suspend fun syncCompletedChapter(mangaId: Long, chapterNumber: Double) = syncLock.withLock {
        if (!chapterNumber.isFinite() || chapterNumber <= 0) return@withLock
        val track = withContext(Dispatchers.IO) { library.track(mangaId, TRACKER_ID) } ?: return@withLock
        if (chapterNumber <= track.last_chapter_read) return@withLock
        val status = if (track.total_chapters > 0 && chapterNumber >= track.total_chapters) "completed" else "reading"
        updateRemote(track.remote_id, "PUT", status, chapterNumber, track.private_, track.score.toInt())
        withContext(Dispatchers.IO) {
            library.updateTrackProgress(track, chapterNumber, track.total_chapters, statusCode(status))
        }
    }

    private suspend fun updateRemote(
        remoteId: Long,
        method: String,
        status: String,
        progress: Double,
        isPrivate: Boolean,
        score: Int,
    ) = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("state", status)
            put("is_private", isPrivate)
            if (progress >
                0
            ) {
                put("progress_chapter", progress)
            } else if (method == "PUT") {
                put("progress_chapter", JsonNull)
            }
            if (score > 0) {
                put("rating", score.coerceIn(0, 100))
            } else if (method == "PUT") {
                put("rating", JsonNull)
            }
            if (method == "PUT") {
                put("start_date", JsonNull)
                put("finish_date", JsonNull)
            }
        }
        val request = Request.Builder().url("$apiBaseUrl/v1/my/library/$remoteId")
            .header("Authorization", "Bearer ${validToken()}").header("User-Agent", USER_AGENT)
            .method(method, body.toString().toRequestBody(JSON)).build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "MangaBaka library update failed: HTTP ${response.code}" }
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
                check(response.isSuccessful) { "MangaBaka request failed: HTTP ${response.code}" }
                Json.parseToJsonElement(response.body.string()) as? JsonObject
                    ?: error("MangaBaka returned an invalid response")
            }
        }

    private suspend fun validToken(): String = authLock.withLock {
        val token = parseToken(secrets.read(TOKEN_KEY) ?: error("Sign in to MangaBaka first"))
        if (clockSeconds() + 60 < token.expiresAt) return@withLock token.accessToken
        val renewed = tokenRequest(
            FormBody.Builder().add("grant_type", "refresh_token").add("client_id", CLIENT_ID)
                .add("refresh_token", token.refreshToken).add("redirect_uri", REDIRECT_URL).build(),
        )
        secrets.write(TOKEN_KEY, renewed.json)
        renewed.accessToken
    }

    private suspend fun tokenRequest(body: FormBody): Token = withContext(Dispatchers.IO) {
        client.newCall(
            Request.Builder().url("$siteBaseUrl/auth/oauth2/token")
                .header("User-Agent", USER_AGENT).post(body).build(),
        ).execute().use { response ->
            check(response.isSuccessful) { "MangaBaka sign-in failed: HTTP ${response.code}" }
            parseToken(response.body.string())
        }
    }

    private fun parseToken(raw: String): Token {
        val json = Json.parseToJsonElement(raw) as? JsonObject ?: error("Invalid MangaBaka session")
        val access = json["access_token"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: error("MangaBaka returned no access token")
        val refresh = json["refresh_token"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: error("MangaBaka returned no refresh token")
        val expires = json["expires_at"]?.jsonPrimitive?.longOrNull ?: error("Invalid MangaBaka expiration")
        return Token(raw, access, refresh, expires)
    }

    private fun statusCode(status: String): Long = when (status) {
        "reading" -> 1
        "completed" -> 2
        "paused" -> 3
        "dropped" -> 4
        "plan_to_read" -> 5
        "rereading" -> 6
        "considering" -> 7
        else -> error("Unknown MangaBaka status")
    }

    private fun randomString(size: Int) = ByteArray(size).also(SecureRandom()::nextBytes).let {
        Base64.getUrlEncoder().withoutPadding().encodeToString(it)
    }

    private data class Token(val json: String, val accessToken: String, val refreshToken: String, val expiresAt: Long)

    companion object {
        const val TRACKER_ID = 11L
        private const val TOKEN_KEY = "mangabaka-session"
        private const val PENDING_KEY = "mangabaka-pkce"
        private const val CLIENT_ID = "zEZYMHXLWsLsafgbvJHXqzGvqQNOdkpo"
        private const val SCOPES = "library.read library.write offline_access openid"
        private const val REDIRECT_URL = "mihon://mangabaka-auth"
        private const val USER_AGENT = "Mihon Windows (https://github.com/mihonapp/mihon)"
        private val JSON = "application/json".toMediaType()
    }
}
