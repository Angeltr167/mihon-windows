package mihon.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mihon.desktop.data.DesktopMangaRepository
import mihon.platform.desktop.DesktopSecretStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest

/** Kavita uses a per-server plugin API key, protected for the current Windows user. */
internal class DesktopKavitaTracker(
    private val client: OkHttpClient,
    private val library: DesktopMangaRepository,
    private val secrets: DesktopSecretStore,
) : DesktopTrackProgressUpdater {
    private val syncLock = Mutex()

    suspend fun bind(mangaId: Long, seriesUrl: String, apiKey: String) = syncLock.withLock {
        require(apiKey.isNotBlank()) { "Enter the Kavita API key" }
        val series = parseSeriesUrl(seriesUrl)
        val token = authenticate(series.apiUrl, apiKey)
        val remote = readSeries(series, token)
        secrets.write(secretKey(series.apiUrl), apiKey)
        withContext(Dispatchers.IO) {
            library.addTrack(
                mangaId = mangaId,
                trackerId = TRACKER_ID,
                title = remote.title,
                remoteUrl = seriesUrl,
                lastChapterRead = remote.lastRead,
                totalChapters = remote.total,
                status = remote.status,
                remoteId = series.id.toLong(),
            )
        }
    }

    override suspend fun syncCompletedChapter(mangaId: Long, chapterNumber: Double) = syncLock.withLock {
        if (!chapterNumber.isFinite() || chapterNumber <= 0) return@withLock
        val track = withContext(Dispatchers.IO) { library.track(mangaId, TRACKER_ID) } ?: return@withLock
        if (chapterNumber <= track.last_chapter_read) return@withLock
        val series = parseSeriesUrl(track.remote_url)
        val apiKey = secrets.read(secretKey(series.apiUrl)) ?: error("Kavita API key is missing; relink the tracker")
        val token = authenticate(series.apiUrl, apiKey)
        val url = "${series.apiUrl}/Tachiyomi/mark-chapter-until-as-read?seriesId=${series.id}" +
            "&chapterNumber=$chapterNumber"
        request(url, token, method = "POST")
        val remote = readSeries(series, token)
        withContext(Dispatchers.IO) {
            library.updateTrackProgress(track, remote.lastRead, remote.total, remote.status)
        }
    }

    private suspend fun authenticate(apiUrl: String, key: String): String = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(key, Charsets.UTF_8)
        val url = "$apiUrl/Plugin/authenticate?apiKey=$encoded&pluginName=Tachiyomi-Kavita"
        val response = request(url, method = "POST")
        response?.jsonObject?.get("token")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: error("Kavita did not return an authentication token")
    }

    private suspend fun readSeries(series: Series, token: String): RemoteSeries {
        val item = request(series.url, token)?.jsonObject ?: error("Kavita series not found")
        val title = item["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: error("Kavita series has no title")
        val pages = item["pages"]?.jsonPrimitive?.intOrNull ?: error("Kavita did not return page count")
        val pagesRead = item["pagesRead"]?.jsonPrimitive?.intOrNull
            ?: error("Kavita did not return read page count")
        check(pages >= 0 && pagesRead in 0..pages)
        val volumes = request("${series.apiUrl}/Series/volumes?seriesId=${series.id}", token)?.jsonArray
            ?: error("Kavita did not return volumes")
        var volumeCount = 0L
        var maxChapter = 0L
        volumes.forEach { volume ->
            val chapters = volume.jsonObject["chapters"]?.jsonArray.orEmpty()
            val maxNumber = chapters.mapNotNull {
                it.jsonObject["number"]?.jsonPrimitive?.content?.replace(',', '.')?.toDoubleOrNull()
            }.maxOrNull() ?: 0.0
            if (maxNumber == 0.0) volumeCount++ else maxChapter = maxOf(maxChapter, maxNumber.toLong())
        }
        val latest = request("${series.apiUrl}/Tachiyomi/latest-chapter?seriesId=${series.id}", token)
        val lastRead = latest?.jsonObject?.get("number")?.jsonPrimitive?.content
            ?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
        check(lastRead.isFinite() && lastRead >= 0)
        return RemoteSeries(
            title,
            maxOf(volumeCount, maxChapter),
            lastRead,
            when {
                pagesRead == pages -> COMPLETED
                pagesRead == 0 -> UNREAD
                else -> READING
            },
        )
    }

    private suspend fun request(url: String, token: String? = null, method: String = "GET") = withContext(
        Dispatchers.IO,
    ) {
        val builder = Request.Builder().url(url)
        token?.let { builder.header("Authorization", "Bearer $it") }
        if (method == "POST") builder.post("{}".toRequestBody(JSON_MEDIA_TYPE)) else builder.get()
        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 204) return@use null
            check(response.isSuccessful) { "Kavita request failed: HTTP ${response.code}" }
            Json.parseToJsonElement(response.body.string())
        }
    }

    private fun parseSeriesUrl(raw: String): Series {
        val uri = URI(raw.trim())
        require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null)
        require(uri.rawQuery == null && uri.rawFragment == null)
        val path = uri.rawPath.orEmpty()
        val marker = "/api/Series/"
        val index = path.lastIndexOf(marker)
        require(index >= 0) { "Only Kavita series API links can be tracked" }
        val id = path.substring(index + marker.length).toIntOrNull()?.takeIf { it > 0 }
            ?: error("Invalid Kavita series ID")
        val prefix = path.substring(0, index)
        val apiUrl = URI(uri.scheme, null, uri.host, uri.port, "$prefix/api", null, null).toString()
        return Series(uri.toString(), apiUrl, id)
    }

    private fun secretKey(apiUrl: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(apiUrl.toByteArray(Charsets.UTF_8))
        return "kavita-" + digest.take(16).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private data class Series(val url: String, val apiUrl: String, val id: Int)
    private data class RemoteSeries(val title: String, val total: Long, val lastRead: Double, val status: Long)

    companion object {
        const val TRACKER_ID = 8L
        private const val UNREAD = 1L
        private const val READING = 2L
        private const val COMPLETED = 3L
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
