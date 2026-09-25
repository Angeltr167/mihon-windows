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
import kotlinx.serialization.json.putJsonObject
import mihon.desktop.data.DesktopMangaRepository
import mihon.platform.desktop.DesktopSecretStore
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.net.URLDecoder

/** Shikimori's Mihon OAuth client and manga user-rate API, without Android dependencies. */
internal class DesktopShikimoriTracker(
    private val client: OkHttpClient,
    private val library: DesktopMangaRepository,
    private val secrets: DesktopSecretStore,
    private val baseUrl: String = "https://shikimori.io",
    private val clockSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) : DesktopTrackProgressUpdater {
    private val syncLock = Mutex()
    private val authLock = Mutex()

    val isLoggedIn: Boolean get() = runCatching { secrets.read(TOKEN_KEY) }.getOrNull() != null

    fun beginLogin(): String = "$baseUrl/oauth/authorize?client_id=$CLIENT_ID" +
        "&redirect_uri=mihon%3A%2F%2Fshikimori-auth&response_type=code"

    suspend fun loginFromCallback(raw: String): String {
        val uri = URI(raw.trim())
        require(
            uri.scheme == "mihon" && uri.host == "shikimori-auth" && uri.userInfo == null &&
                uri.port == -1 && uri.rawPath.isNullOrEmpty() && uri.rawFragment == null,
        ) { "Paste the Shikimori redirect URL beginning mihon://shikimori-auth" }
        val matches = uri.rawQuery.orEmpty().split('&').map { it.split('=', limit = 2) }
            .filter { it.size == 2 && it[0] == "code" }
        require(matches.size == 1) { "Missing or duplicate Shikimori code" }
        val code = URLDecoder.decode(matches.single()[1], Charsets.UTF_8)
        require(code.length in 8..8192)
        val token = tokenRequest(
            FormBody.Builder().add("grant_type", "authorization_code")
                .add("client_id", CLIENT_ID).add("client_secret", CLIENT_SECRET)
                .add("code", code).add("redirect_uri", REDIRECT_URL).build(),
        )
        val user = graphQl(CURRENT_USER_QUERY, token.accessToken).data("currentUser").jsonObject
        val name = user["nickname"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: error("Shikimori did not identify the account")
        secrets.write(TOKEN_KEY, token.json)
        secrets.write(USER_ID_KEY, user["id"]?.jsonPrimitive?.content ?: error("Shikimori account has no ID"))
        return name
    }

    fun logout() {
        secrets.remove(TOKEN_KEY)
        secrets.remove(USER_ID_KEY)
    }

    suspend fun bind(mangaId: Long, remoteId: Long) = syncLock.withLock {
        require(remoteId > 0)
        val manga = (
            graphQl(
                MANGA_QUERY,
                variables = buildJsonObject { put("id", remoteId.toString()) },
            ).data("mangas") as JsonArray
            ).firstOrNull()?.jsonObject ?: error("Shikimori manga not found")
        require(manga["id"]?.jsonPrimitive?.content == remoteId.toString()) { "Shikimori returned a different manga" }
        val title = manga["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: error("Shikimori manga has no title")
        val total = manga["chapters"]?.jsonPrimitive?.longOrNull?.coerceAtLeast(0) ?: 0
        val rate = (manga["userRate"] as? JsonObject) ?: restRequest(
            "/api/v2/user_rates",
            "POST",
            buildJsonObject {
                putJsonObject("user_rate") {
                    put("user_id", secrets.read(USER_ID_KEY) ?: error("Sign in to Shikimori first"))
                    put("target_id", remoteId)
                    put("target_type", "Manga")
                    put("chapters", 0)
                    put("score", 0)
                    put("status", "planned")
                }
            },
        )
        val entryId = rate["id"]?.jsonPrimitive?.content?.toLongOrNull()
            ?: error("Shikimori did not return a library entry")
        withContext(Dispatchers.IO) {
            library.addTrack(
                mangaId, TRACKER_ID, title,
                manga["url"]?.jsonPrimitive?.content ?: "$baseUrl/mangas/$remoteId",
                rate["chapters"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                total,
                statusCode(rate["status"]?.jsonPrimitive?.content ?: "planned"),
                remoteId,
                entryId,
            )
        }
    }

    override suspend fun syncCompletedChapter(mangaId: Long, chapterNumber: Double) = syncLock.withLock {
        if (!chapterNumber.isFinite() || chapterNumber <= 0) return@withLock
        val track = withContext(Dispatchers.IO) { library.track(mangaId, TRACKER_ID) } ?: return@withLock
        if (chapterNumber <= track.last_chapter_read) return@withLock
        val entryId = track.library_id?.takeIf { it > 0 } ?: error("Shikimori library entry is missing; relink")
        val status = if (track.total_chapters > 0 && chapterNumber >= track.total_chapters) "completed" else "watching"
        val updated = restRequest(
            "/api/v2/user_rates/$entryId",
            "PUT",
            buildJsonObject {
                putJsonObject("user_rate") {
                    put("chapters", chapterNumber.toInt())
                    put("score", track.score.toInt())
                    put("status", status)
                }
            },
        )
        require(updated["id"]?.jsonPrimitive?.content?.toLongOrNull() == entryId) {
            "Shikimori did not confirm the library update"
        }
        withContext(Dispatchers.IO) {
            library.updateTrackProgress(
                track,
                chapterNumber.toInt().toDouble(),
                track.total_chapters,
                statusCode(status),
            )
        }
    }

    private suspend fun graphQl(query: String, token: String? = null, variables: JsonObject = JsonObject(emptyMap())) =
        withContext(Dispatchers.IO) {
            val access = token ?: validToken()
            val body = buildJsonObject {
                put("query", query)
                put("variables", variables)
            }
            client.newCall(
                Request.Builder().url("$baseUrl/api/graphql").header("Authorization", "Bearer $access")
                    .post(body.toString().toRequestBody(JSON)).build(),
            ).execute().use { response ->
                check(response.isSuccessful) { "Shikimori request failed: HTTP ${response.code}" }
                Json.parseToJsonElement(response.body.string()).jsonObject.also {
                    check(it["errors"] == null) { "Shikimori rejected the request" }
                }
            }
        }

    private suspend fun restRequest(path: String, method: String, body: JsonObject): JsonObject =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url("$baseUrl$path").header("Authorization", "Bearer ${validToken()}")
                .method(method, body.toString().toRequestBody(JSON)).build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Shikimori update failed: HTTP ${response.code}" }
                Json.parseToJsonElement(response.body.string()).jsonObject
            }
        }

    private suspend fun validToken(): String = authLock.withLock {
        val token = parseToken(secrets.read(TOKEN_KEY) ?: error("Sign in to Shikimori first"))
        if (clockSeconds() + 3600 < token.createdAt + token.expiresIn) return@withLock token.accessToken
        val refresh = token.refreshToken ?: error("Shikimori session expired; sign in again")
        val renewed = tokenRequest(
            FormBody.Builder().add("grant_type", "refresh_token").add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET).add("refresh_token", refresh).build(),
        )
        secrets.write(TOKEN_KEY, renewed.json)
        renewed.accessToken
    }

    private suspend fun tokenRequest(body: FormBody): Token = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url("$baseUrl/oauth/token").post(body).build()).execute().use { response ->
            check(response.isSuccessful) { "Shikimori sign-in failed: HTTP ${response.code}" }
            parseToken(response.body.string())
        }
    }

    private fun parseToken(raw: String): Token {
        val json = Json.parseToJsonElement(raw).jsonObject
        val access = json["access_token"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: error("Shikimori returned no access token")
        val created = json["created_at"]?.jsonPrimitive?.longOrNull ?: clockSeconds()
        val expires = json["expires_in"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 }
            ?: error("Shikimori returned an invalid lifetime")
        val refresh = json["refresh_token"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
        return Token(raw, access, refresh, created, expires)
    }

    private fun JsonObject.data(field: String) = this["data"]?.jsonObject?.get(field)
        ?: error("Shikimori response lacks $field")

    private fun statusCode(status: String): Long = when (status) {
        "watching" -> 1
        "completed" -> 2
        "on_hold" -> 3
        "dropped" -> 4
        "planned" -> 5
        "rewatching" -> 6
        else -> error("Unknown Shikimori status")
    }

    private data class Token(
        val json: String,
        val accessToken: String,
        val refreshToken: String?,
        val createdAt: Long,
        val expiresIn: Long,
    )

    companion object {
        const val TRACKER_ID = 4L
        private const val TOKEN_KEY = "shikimori-session"
        private const val USER_ID_KEY = "shikimori-user-id"
        private const val REDIRECT_URL = "mihon://shikimori-auth"
        private const val CLIENT_ID = "PB9dq8DzI405s7wdtwTdirYqHiyVMh--djnP7lBUqSA"
        private const val CLIENT_SECRET = "NajpZcOBKB9sJtgNcejf8OB9jBN1OYYoo-k4h2WWZus"
        private val JSON = "application/json".toMediaType()
        private const val CURRENT_USER_QUERY = "{ currentUser { id nickname } }"
        private const val MANGA_QUERY =
            "query(${'$'}id: String) { mangas(ids: ${'$'}id, limit: 1) " +
                "{ id url name chapters userRate { id chapters status score } } }"
    }
}
