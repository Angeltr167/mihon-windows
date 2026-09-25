package mihon.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import mihon.desktop.data.DesktopMangaRepository
import mihon.platform.desktop.DesktopSecretStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.net.URLDecoder
import java.util.UUID

/** Hikka's browser reference exchange and read-list API on Desktop. */
internal class DesktopHikkaTracker(
    private val client: OkHttpClient,
    private val library: DesktopMangaRepository,
    private val secrets: DesktopSecretStore,
    private val apiBaseUrl: String = "https://api.hikka.io",
    private val siteBaseUrl: String = "https://hikka.io",
    private val clockSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) : DesktopTrackProgressUpdater {
    private val syncLock = Mutex()
    private val authLock = Mutex()

    val isLoggedIn: Boolean get() = runCatching { secrets.read(TOKEN_KEY) }.getOrNull() != null

    fun beginLogin(): String = "$siteBaseUrl/oauth?reference=$CLIENT_REFERENCE&scope=readlist%2Cread%3Auser-details"

    suspend fun loginFromCallback(raw: String): String {
        val uri = URI(raw.trim())
        require(
            uri.scheme == "mihon" && uri.host == "hikka-auth" && uri.userInfo == null &&
                uri.port == -1 && uri.rawPath.isNullOrEmpty() && uri.rawFragment == null,
        ) { "Paste the Hikka redirect URL beginning mihon://hikka-auth" }
        val matches = uri.rawQuery.orEmpty().split('&').map { it.split('=', limit = 2) }
            .filter { it.size == 2 && it[0] == "reference" }
        require(matches.size == 1) { "Missing or duplicate Hikka reference" }
        val reference = URLDecoder.decode(matches.single()[1], Charsets.UTF_8)
        require(reference.length in 8..256)
        val token = tokenRequest(reference)
        val user = request("/user/me", "GET", token.accessToken)
        val name = user?.get("username")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: error("Hikka did not identify the account")
        secrets.write(TOKEN_KEY, token.json)
        return name
    }

    fun logout() = secrets.remove(TOKEN_KEY)

    suspend fun bind(mangaId: Long, slug: String) = syncLock.withLock {
        require(slug.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "Invalid Hikka manga slug" }
        val manga = request("/manga/$slug", "GET") ?: error("Hikka manga not found")
        require(manga["slug"]?.jsonPrimitive?.content == slug) { "Hikka returned a different manga" }
        val title = sequenceOf("title_ua", "title_en", "title_original")
            .mapNotNull { key -> manga[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content }
            .firstOrNull { it.isNotBlank() } ?: error("Hikka manga has no title")
        val total = manga["chapters"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.longOrNull?.coerceAtLeast(0) ?: 0
        val existing = request("/read/manga/$slug", "GET", allowNotFound = true)
        val progress = existing?.get("chapters")?.jsonPrimitive?.longOrNull ?: 0
        val status = existing?.get("status")?.jsonPrimitive?.content ?: if (progress > 0) "reading" else "planned"
        val rate = existing ?: updateRemote(slug, progress.toInt(), status, 0, 0)
        withContext(Dispatchers.IO) {
            library.addTrack(
                mangaId,
                TRACKER_ID,
                title,
                "$siteBaseUrl/manga/$slug",
                rate["chapters"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                total,
                statusCode(rate["status"]?.jsonPrimitive?.content ?: status),
                UUID.nameUUIDFromBytes(slug.toByteArray()).mostSignificantBits and Long.MAX_VALUE,
            )
        }
    }

    override suspend fun syncCompletedChapter(mangaId: Long, chapterNumber: Double) = syncLock.withLock {
        if (!chapterNumber.isFinite() || chapterNumber <= 0) return@withLock
        val track = withContext(Dispatchers.IO) { library.track(mangaId, TRACKER_ID) } ?: return@withLock
        if (chapterNumber <= track.last_chapter_read) return@withLock
        val slug = URI(track.remote_url).path.removePrefix("/manga/")
        require(slug.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "Invalid linked Hikka manga slug" }
        val status = if (track.total_chapters > 0 && chapterNumber >= track.total_chapters) "completed" else "reading"
        val updated = updateRemote(slug, chapterNumber.toInt(), status, track.score.toInt(), 0)
        val confirmed = updated["chapters"]?.jsonPrimitive?.longOrNull
            ?: error("Hikka did not confirm progress")
        withContext(Dispatchers.IO) {
            library.updateTrackProgress(track, confirmed.toDouble(), track.total_chapters, statusCode(status))
        }
    }

    private suspend fun updateRemote(slug: String, chapters: Int, status: String, score: Int, rereads: Int) =
        request(
            "/read/manga/$slug",
            "PUT",
            body = buildJsonObject {
                put("note", "")
                put("chapters", chapters)
                put("volumes", 0)
                put("rereads", rereads)
                put("score", score)
                put("status", status)
                put("start_date", JsonNull)
                put("end_date", JsonNull)
            },
        ) ?: error("Hikka returned no read-list entry")

    private suspend fun request(
        path: String,
        method: String,
        token: String? = null,
        allowNotFound: Boolean = false,
        body: JsonObject? = null,
    ): JsonObject? = withContext(Dispatchers.IO) {
        val access = token ?: validToken()
        val builder = Request.Builder().url("$apiBaseUrl$path")
            .header("auth", access).header("accept", "application/json")
        val request = if (body == null) {
            builder.get().build()
        } else {
            builder.method(method, body.toString().toRequestBody(JSON)).build()
        }
        client.newCall(request).execute().use { response ->
            if (allowNotFound && response.code == 404) return@withContext null
            check(response.isSuccessful) { "Hikka request failed: HTTP ${response.code}" }
            Json.parseToJsonElement(response.body.string()).let { it as? JsonObject }
                ?: error("Hikka returned an invalid response")
        }
    }

    private suspend fun validToken(): String = authLock.withLock {
        val token = parseToken(secrets.read(TOKEN_KEY) ?: error("Sign in to Hikka first"))
        if (clockSeconds() + 300 < token.expiration) return@withLock token.accessToken
        requestWithToken("/user/me", token.accessToken)
        val info = requestWithToken("/auth/token/info", token.accessToken)
        val expiration = info["expiration"]?.jsonPrimitive?.longOrNull
            ?: error("Hikka returned no token expiration")
        secrets.write(
            TOKEN_KEY,
            buildJsonObject {
                put("secret", token.accessToken)
                put("created", info["created"]?.jsonPrimitive?.longOrNull ?: clockSeconds())
                put("expiration", expiration)
            }.toString(),
        )
        token.accessToken
    }

    private suspend fun requestWithToken(path: String, token: String): JsonObject = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url("$apiBaseUrl$path").header("auth", token).get().build())
            .execute().use { response ->
                check(response.isSuccessful) { "Hikka session expired: HTTP ${response.code}" }
                Json.parseToJsonElement(response.body.string()) as? JsonObject
                    ?: error("Hikka returned an invalid response")
            }
    }

    private suspend fun tokenRequest(reference: String): Token = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("request_reference", reference)
            put("client_secret", CLIENT_SECRET)
        }
        client.newCall(
            Request.Builder().url("$apiBaseUrl/auth/token").post(body.toString().toRequestBody(JSON)).build(),
        )
            .execute().use { response ->
                check(response.isSuccessful) { "Hikka sign-in failed: HTTP ${response.code}" }
                parseToken(response.body.string())
            }
    }

    private fun parseToken(raw: String): Token {
        val json = Json.parseToJsonElement(raw).let { it as? JsonObject } ?: error("Invalid Hikka session")
        val access = json["secret"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: error("Hikka returned no token")
        val expiration = json["expiration"]?.jsonPrimitive?.longOrNull ?: error("Invalid Hikka expiration")
        return Token(raw, access, expiration)
    }

    private fun statusCode(status: String): Long = when (status) {
        "reading" -> 0
        "completed" -> 1
        "on_hold" -> 2
        "dropped" -> 3
        "planned" -> 4
        else -> error("Unknown Hikka status")
    }

    private data class Token(val json: String, val accessToken: String, val expiration: Long)

    companion object {
        const val TRACKER_ID = 10L
        private const val TOKEN_KEY = "hikka-session"
        private const val CLIENT_REFERENCE = "598ef1f5-b9d2-4e66-8b65-06949d5e14fc"
        private const val CLIENT_SECRET = "OKwzrNOZxq40psFgfcCUYddnvaeZWDnd34rt7fdcB5GmHoBBQuNTWX" +
            "61sZs8KECEWVXtMUDtq8QC4t9WX4DwWWYLXEVlgnlUXGT1fWCb-18c" +
            "Zd2m8Co-8HN6JQcjoP-B"
        private val JSON = "application/json".toMediaType()
    }
}
