package mihon.desktop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Only the extension/source API is used; Mihon's own database and reader remain authoritative. */
internal class SuwayomiClient(
    val baseUri: URI,
    private val transport: (String, JsonObject) -> JsonObject = ::request,
) {
    fun isReady(): Boolean = runCatching {
        execute("query { sources(first: 1) { nodes { id } } }")
    }.isSuccess

    fun addStore(indexUrl: String) {
        execute(
            "mutation(\$input: AddExtensionStoreInput!) { addExtensionStore(input: \$input) { extensionStore { indexUrl } } }",
            buildJsonObject { put("input", buildJsonObject { put("indexUrl", indexUrl) }) },
        ).requiredObject("addExtensionStore")
    }

    fun refreshExtensions(): List<SuwayomiExtension> = execute(
        "mutation { fetchExtensions(input: {}) { extensions { pkgName name versionName versionCodeLong " +
            "contentWarning isInstalled hasUpdate isObsolete } } }",
    ).requiredObject("fetchExtensions").requiredArray("extensions").map(::extension)

    fun extensions(): List<SuwayomiExtension> = execute(
        "query { extensions { nodes { pkgName name versionName versionCodeLong " +
            "contentWarning isInstalled hasUpdate isObsolete } } }",
    ).requiredObject("extensions").requiredArray("nodes").map(::extension)

    fun setInstalled(pkgName: String, action: String) {
        require(action in setOf("install", "update", "uninstall"))
        val patch = buildJsonObject { put(action, true) }
        execute(
            "mutation(\$input: UpdateExtensionInput!) { updateExtension(input: \$input) { extension { pkgName isInstalled } } }",
            buildJsonObject {
                put(
                    "input",
                    buildJsonObject {
                        put("id", pkgName)
                        put("patch", patch)
                    },
                )
            },
        ).requiredObject("updateExtension")
    }

    fun sources(): List<SuwayomiSourceInfo> = execute(
        "query { sources { nodes { id name lang supportsLatest homeUrl extension { pkgName } } } }",
    ).requiredObject("sources").requiredArray("nodes").mapNotNull { element ->
        val node = element.jsonObject
        val pkgName = (node["extension"] as? JsonObject)?.optionalString("pkgName") ?: return@mapNotNull null
        SuwayomiSourceInfo(
            id = node.requiredLong("id"),
            name = node.requiredString("name"),
            lang = node.requiredString("lang"),
            supportsLatest = node.requiredBoolean("supportsLatest"),
            homeUrl = node.optionalString("homeUrl"),
            packageName = pkgName,
        )
    }

    fun browse(sourceId: Long, type: String, page: Int, query: String = ""): SuwayomiBrowsePage {
        require(type in setOf("POPULAR", "LATEST", "SEARCH"))
        val input = buildJsonObject {
            put("source", sourceId.toString())
            put("type", type)
            put("page", page)
            if (type == "SEARCH") put("query", query)
        }
        val result = execute(
            "mutation(\$input: FetchSourceMangaInput!) { fetchSourceManga(input: \$input) { " +
                "mangas { id url title thumbnailUrl artist author description genre status } hasNextPage } }",
            buildJsonObject { put("input", input) },
        ).requiredObject("fetchSourceManga")
        return SuwayomiBrowsePage(
            result.requiredArray("mangas").map {
                it.jsonObject
            },
            result.requiredBoolean("hasNextPage"),
        )
    }

    fun mangaId(sourceId: Long, url: String): Int {
        val result = execute(
            "query(\$condition: MangaConditionInput!) { mangas(condition: \$condition, first: 1) { nodes { id } } }",
            buildJsonObject {
                put(
                    "condition",
                    buildJsonObject {
                        put("sourceId", sourceId.toString())
                        put("url", url)
                    },
                )
            },
        ).requiredObject("mangas").requiredArray("nodes")
        return result.firstOrNull()?.jsonObject?.requiredInt("id")
            ?: error("Manga is not available in the local extension engine")
    }

    fun mangaAndChapters(id: Int, details: Boolean, chapters: Boolean): SuwayomiMangaUpdate {
        val result = execute(
            "mutation(\$input: FetchMangaAndChaptersInput!) { fetchMangaAndChapters(input: \$input) { " +
                "manga { id url title thumbnailUrl artist author description genre status } " +
                "chapters { id url name chapterNumber uploadDate scanlator mangaId } } }",
            buildJsonObject {
                put(
                    "input",
                    buildJsonObject {
                        put("id", id)
                        put("fetchManga", details)
                        put("fetchChapters", chapters)
                    },
                )
            },
        ).requiredObject("fetchMangaAndChapters")
        return SuwayomiMangaUpdate(
            result.requiredObject("manga"),
            result.requiredArray("chapters").map {
                it.jsonObject
            },
        )
    }

    fun pages(chapterId: Int): List<String> = execute(
        "mutation(\$input: FetchChapterPagesInput!) { fetchChapterPages(input: \$input) { pages } }",
        buildJsonObject { put("input", buildJsonObject { put("chapterId", chapterId) }) },
    ).requiredObject("fetchChapterPages").requiredArray("pages").map { absolute(it.jsonPrimitive.content) }

    fun absolute(path: String): String {
        require(path.startsWith("/api/v1/")) { "Unexpected Suwayomi media path" }
        return baseUri.resolve(path).toString()
    }

    private fun execute(query: String, variables: JsonObject = JsonObject(emptyMap())): JsonObject {
        val response = transport(
            baseUri.resolve("/api/graphql").toString(),
            buildJsonObject {
                put("query", query)
                put("variables", variables)
            },
        )
        val errors = response["errors"] as? JsonArray
        if (!errors.isNullOrEmpty()) {
            val message = errors.firstOrNull()?.jsonObject?.optionalString("message") ?: "Suwayomi request failed"
            error(message)
        }
        return response.requiredObject("data")
    }

    private companion object {
        val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
        val json = Json { ignoreUnknownKeys = true }

        fun request(url: String, body: JsonObject): JsonObject {
            val request = HttpRequest.newBuilder(URI(url))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            require(response.statusCode() == 200) { "Suwayomi returned HTTP ${response.statusCode()}" }
            return json.parseToJsonElement(response.body()).jsonObject
        }

        fun extension(element: kotlinx.serialization.json.JsonElement): SuwayomiExtension {
            val node = element.jsonObject
            return SuwayomiExtension(
                pkgName = node.requiredString("pkgName"),
                name = node.requiredString("name"),
                versionName = node.requiredString("versionName"),
                versionCode = node.requiredLong("versionCodeLong"),
                contentWarning = node.requiredString("contentWarning"),
                installed = node.requiredBoolean("isInstalled"),
                hasUpdate = node.requiredBoolean("hasUpdate"),
                obsolete = node.requiredBoolean("isObsolete"),
            )
        }
    }
}

internal data class SuwayomiExtension(
    val pkgName: String,
    val name: String,
    val versionName: String,
    val versionCode: Long,
    val contentWarning: String,
    val installed: Boolean,
    val hasUpdate: Boolean,
    val obsolete: Boolean,
)

internal data class SuwayomiSourceInfo(
    val id: Long,
    val name: String,
    val lang: String,
    val supportsLatest: Boolean,
    val homeUrl: String?,
    val packageName: String,
)

internal data class SuwayomiBrowsePage(val mangas: List<JsonObject>, val hasNextPage: Boolean)
internal data class SuwayomiMangaUpdate(val manga: JsonObject, val chapters: List<JsonObject>)

internal fun JsonObject.requiredObject(name: String): JsonObject = this[name]?.jsonObject ?: error("Missing $name")
internal fun JsonObject.requiredArray(name: String): JsonArray = this[name]?.jsonArray ?: error("Missing $name")
internal fun JsonObject.requiredString(name: String): String = optionalString(name) ?: error("Missing $name")
internal fun JsonObject.optionalString(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
internal fun JsonObject.requiredLong(name: String): Long =
    this[name]?.jsonPrimitive?.content?.toLongOrNull() ?: error("Invalid $name")
internal fun JsonObject.requiredInt(name: String): Int = this[name]?.jsonPrimitive?.int ?: error("Invalid $name")
internal fun JsonObject.requiredBoolean(name: String): Boolean =
    this[name]?.jsonPrimitive?.boolean ?: error("Invalid $name")
