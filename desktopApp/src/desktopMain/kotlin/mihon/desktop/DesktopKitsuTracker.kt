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

/** Kitsu's Android password grant and GraphQL tracking contract, without Android dependencies. */
internal class DesktopKitsuTracker(
    private val client: OkHttpClient,
    private val library: DesktopMangaRepository,
    private val secrets: DesktopSecretStore,
    private val baseUrl: String = "https://kitsu.app",
    private val clockSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) : DesktopTrackProgressUpdater {
    private val syncLock = Mutex()
    private val authLock = Mutex()

    val isLoggedIn: Boolean get() = runCatching { secrets.read(TOKEN_KEY) }.getOrNull() != null

    suspend fun login(username: String, password: String): String {
        require(username.isNotBlank() && password.isNotBlank())
        val token = tokenRequest(
            FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .add("grant_type", "password")
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .build(),
        )
        val account = graphQl(CURRENT_ACCOUNT_QUERY, token.accessToken).data("currentAccount")
        val name = account["profile"]?.jsonObject?.get("name")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: error("Kitsu did not identify the account")
        secrets.write(TOKEN_KEY, token.json)
        return name
    }

    fun logout() = secrets.remove(TOKEN_KEY)

    suspend fun bind(mangaId: Long, mediaId: Long) = syncLock.withLock {
        require(mediaId > 0)
        val manga = graphQl(
            MANGA_QUERY,
            variables = buildJsonObject { put("id", mediaId.toString()) },
        ).data("findMangaById")
        val id = manga["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: error("Kitsu manga not found")
        val title = manga["titles"]?.jsonObject?.get("preferred")?.jsonPrimitive?.content
            ?: error("Kitsu manga has no title")
        val slug = manga["slug"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: id.toString()
        val total = manga["chapterCount"]?.jsonPrimitive?.longOrNull?.coerceAtLeast(0) ?: 0
        val existing = manga["myLibraryEntry"] as? JsonObject
        val libraryEntry = existing ?: graphQl(
            CREATE_ENTRY_MUTATION,
            variables = buildJsonObject {
                put("id", id.toString())
                put("status", "PLANNED")
            },
        ).data("libraryEntry").getValue("create").jsonObject.also(::checkMutation).getValue("libraryEntry").jsonObject
        val entryId = libraryEntry["id"]?.jsonPrimitive?.content?.toLongOrNull()
            ?: error("Kitsu did not return a library entry")
        val progress = libraryEntry["progress"]?.jsonPrimitive?.longOrNull?.toDouble() ?: 0.0
        val status = statusCode(libraryEntry["status"]?.jsonPrimitive?.content ?: "PLANNED")
        withContext(Dispatchers.IO) {
            library.addTrack(
                mangaId = mangaId,
                trackerId = TRACKER_ID,
                title = title,
                remoteUrl = "$baseUrl/manga/$slug",
                lastChapterRead = progress,
                totalChapters = total,
                status = status,
                remoteId = id,
                libraryId = entryId,
            )
        }
    }

    override suspend fun syncCompletedChapter(mangaId: Long, chapterNumber: Double) = syncLock.withLock {
        if (!chapterNumber.isFinite() || chapterNumber <= 0) return@withLock
        val track = withContext(Dispatchers.IO) { library.track(mangaId, TRACKER_ID) } ?: return@withLock
        if (chapterNumber <= track.last_chapter_read) return@withLock
        val entryId = track.library_id?.takeIf { it > 0 } ?: error("Kitsu library entry is missing; relink")
        val status = if (track.total_chapters > 0 && chapterNumber >= track.total_chapters) {
            "COMPLETED"
        } else {
            "CURRENT"
        }
        val result = graphQl(
            UPDATE_ENTRY_MUTATION,
            variables = buildJsonObject {
                put("id", entryId.toString())
                put("progress", chapterNumber.toInt())
                put("status", status)
                put("private", track.private_)
            },
        ).data("libraryEntry").getValue("update").jsonObject
        checkMutation(result)
        withContext(Dispatchers.IO) {
            library.updateTrackProgress(
                track,
                chapterNumber.toInt().toDouble(),
                track.total_chapters,
                statusCode(status),
            )
        }
    }

    private suspend fun graphQl(
        query: String,
        token: String? = null,
        variables: JsonObject = JsonObject(emptyMap()),
    ): JsonObject = withContext(Dispatchers.IO) {
        val accessToken = token ?: validToken()
        val payload = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        client.newCall(
            Request.Builder().url("$baseUrl/api/graphql")
                .header("Authorization", "Bearer $accessToken")
                .header("Accept", "application/vnd.api+json")
                .post(payload)
                .build(),
        ).execute().use { response ->
            check(response.isSuccessful) { "Kitsu request failed: HTTP ${response.code}" }
            val result = Json.parseToJsonElement(response.body.string()).jsonObject
            check(result["errors"] == null && result["error"] == null) { "Kitsu rejected the request" }
            result
        }
    }

    private suspend fun validToken(): String = authLock.withLock {
        val stored = secrets.read(TOKEN_KEY) ?: error("Sign in to Kitsu first")
        val token = parseToken(stored)
        if (clockSeconds() + 3600 < token.createdAt + token.expiresIn) return@withLock token.accessToken
        val refresh = token.refreshToken ?: error("Kitsu session expired; sign in again")
        val renewed = tokenRequest(
            FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refresh)
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .build(),
        )
        secrets.write(TOKEN_KEY, renewed.json)
        renewed.accessToken
    }

    private suspend fun tokenRequest(body: FormBody): Token = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url("$baseUrl/api/oauth/token").post(body).build()).execute().use { response ->
            check(response.isSuccessful) { "Kitsu sign-in failed: HTTP ${response.code}" }
            parseToken(response.body.string())
        }
    }

    private fun parseToken(raw: String): Token {
        val json = Json.parseToJsonElement(raw).jsonObject
        val access = json["access_token"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: error("Kitsu returned no access token")
        val created = json["created_at"]?.jsonPrimitive?.longOrNull ?: error("Invalid Kitsu session time")
        val expires = json["expires_in"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 }
            ?: error("Invalid Kitsu session lifetime")
        val refresh = json["refresh_token"]?.takeIf { it !is JsonNull }
            ?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        return Token(raw, access, created, expires, refresh)
    }

    private fun JsonObject.data(field: String): JsonObject =
        this["data"]?.jsonObject?.get(field)?.jsonObject ?: error("Kitsu response lacks $field")

    private fun checkMutation(result: JsonObject) {
        check((result["errors"] as? JsonArray).isNullOrEmpty()) { "Kitsu rejected the library update" }
    }

    private fun statusCode(status: String): Long = when (status) {
        "CURRENT" -> 1
        "COMPLETED" -> 2
        "ON_HOLD" -> 3
        "DROPPED" -> 4
        "PLANNED" -> 5
        else -> error("Unknown Kitsu status")
    }

    private data class Token(
        val json: String,
        val accessToken: String,
        val createdAt: Long,
        val expiresIn: Long,
        val refreshToken: String?,
    )

    companion object {
        const val TRACKER_ID = 3L
        private const val TOKEN_KEY = "kitsu-session"
        private const val CLIENT_ID = "dd031b32d2f56c990b1425efe6c42ad847e7fe3ab46bf1299f05ecd856bdb7dd"
        private const val CLIENT_SECRET = "54d7307928f63414defd96399fc31ba847961ceaecef3a5fd93144e960c0e151"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val CURRENT_ACCOUNT_QUERY = "query { currentAccount { id profile { name } } }"
        private const val MANGA_QUERY =
            "query (${'$'}id: ID!) { findMangaById(id: ${'$'}id) " +
                "{ id titles { preferred } slug chapterCount myLibraryEntry { id progress status } } }"
        private const val CREATE_ENTRY_MUTATION =
            "mutation (${'$'}id: ID!, ${'$'}status: LibraryEntryStatusEnum!) { libraryEntry " +
                "{ create(input: {mediaId: ${'$'}id, mediaType: MANGA, status: ${'$'}status, " +
                "progress: 0, private: false}) { errors { message } libraryEntry { id progress status } } } }"
        private const val UPDATE_ENTRY_MUTATION =
            "mutation (${'$'}id: ID!, ${'$'}progress: Int!, ${'$'}status: LibraryEntryStatusEnum!, " +
                "${'$'}private: Boolean!) { libraryEntry { update(input: {id: ${'$'}id, progress: ${'$'}progress, " +
                "status: ${'$'}status, private: ${'$'}private}) { errors { message } libraryEntry { id } } } }"
    }
}
