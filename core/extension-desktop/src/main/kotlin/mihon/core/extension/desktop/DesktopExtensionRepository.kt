package mihon.core.extension.desktop

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/** Desktop-only discovery and update transport. Package trust is still enforced by [DesktopExtensionManager]. */
class DesktopExtensionRepository(
    private val temporaryDirectory: Path,
    private val fetch: (URI, Int) -> ByteArray = ::fetchHttps,
) {
    private val json = Json { ignoreUnknownKeys = false }

    fun discover(indexUri: URI): List<DesktopRepositoryEntry> {
        requireHttps(indexUri)
        val indexBytes = fetch(indexUri, MAX_INDEX_SIZE)
        require(indexBytes.size <= MAX_INDEX_SIZE) { "Extension index is too large" }
        val index = json.decodeFromString<DesktopRepositoryIndex>(indexBytes.decodeToString())
        require(index.formatVersion == 1) { "Unsupported Desktop extension index version" }
        require(index.extensions.size <= MAX_EXTENSIONS) { "Extension index is too large" }
        require(index.extensions.map { it.id }.toSet().size == index.extensions.size) { "Duplicate extension identity" }
        return index.extensions.onEach { entry ->
            require(entry.id.matches(EXTENSION_ID)) { "Invalid extension identity" }
            require(entry.versionCode >= 0) { "Invalid extension version" }
            require(entry.sha256.matches(SHA256) && entry.fingerprint.matches(SHA256)) {
                "Invalid extension digest or fingerprint"
            }
            requireHttps(indexUri.resolve(entry.packageUrl))
        }
    }

    fun updates(indexUri: URI, manager: DesktopExtensionManager): List<DesktopRepositoryEntry> {
        val installed = manager.installedExtensions().associateBy { it.id }
        return discover(indexUri).filter { entry ->
            installed[entry.id]?.let { entry.versionCode > it.versionCode } ?: true
        }
    }

    fun install(
        indexUri: URI,
        entry: DesktopRepositoryEntry,
        manager: DesktopExtensionManager,
    ): DesktopExtensionInstallResult {
        val listed = discover(indexUri).singleOrNull { it.id == entry.id && it == entry }
            ?: return DesktopExtensionInstallResult.Rejected(DesktopExtensionInstallResult.Reason.INDEX_MISMATCH)
        val packageUri = indexUri.resolve(listed.packageUrl)
        requireHttps(packageUri)
        Files.createDirectories(temporaryDirectory)
        val temporary = Files.createTempFile(temporaryDirectory, "mihonext-", ".mihonext")
        return try {
            val bytes = fetch(packageUri, MAX_PACKAGE_SIZE)
            if (bytes.size > MAX_PACKAGE_SIZE || DesktopExtensionPackager.sha256(bytes) != listed.sha256) {
                DesktopExtensionInstallResult.Rejected(DesktopExtensionInstallResult.Reason.INDEX_MISMATCH)
            } else {
                Files.write(temporary, bytes)
                manager.install(temporary, ExpectedDesktopExtension(listed.id, listed.versionCode, listed.fingerprint))
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun requireHttps(uri: URI) {
        require(
            uri.scheme.equals("https", ignoreCase = true) && uri.host != null && uri.userInfo == null &&
                uri.fragment == null,
        ) {
            "Desktop extension repositories require HTTPS"
        }
    }

    private companion object {
        const val MAX_INDEX_SIZE = 1024 * 1024
        const val MAX_PACKAGE_SIZE = 64 * 1024 * 1024
        const val MAX_EXTENSIONS = 5000
        val EXTENSION_ID = Regex("[A-Za-z0-9._-]+")
        val SHA256 = Regex("[0-9a-f]{64}")

        fun fetchHttps(uri: URI, limit: Int): ByteArray {
            val client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(15))
                .build()
            val request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            require(response.statusCode() == 200) { "Extension repository returned ${response.statusCode()}" }
            return response.body().use { stream ->
                val bytes = stream.readNBytes(limit + 1)
                require(bytes.size <= limit) { "Extension repository response is too large" }
                bytes
            }
        }
    }
}

@Serializable
data class DesktopRepositoryIndex(
    val formatVersion: Int,
    val extensions: List<DesktopRepositoryEntry>,
)

@Serializable
data class DesktopRepositoryEntry(
    val id: String,
    val name: String,
    val versionCode: Long,
    val packageUrl: String,
    val sha256: String,
    val fingerprint: String,
)
