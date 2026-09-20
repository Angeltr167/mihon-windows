package mihon.core.extension.desktop

import kotlinx.serialization.Serializable

@Serializable
data class DesktopExtensionManifest(
    val formatVersion: Int,
    val id: String,
    val name: String,
    val versionName: String,
    val versionCode: Long,
    val libVersion: Double,
    val contentWarning: ContentWarning = ContentWarning.SAFE,
    val sourceClasses: List<String>,
    val jarSha256: String,
)

@Serializable
data class DesktopExtensionSignature(
    val algorithm: String = "Ed25519",
    val publicKey: String,
    val signature: String,
)

@Serializable
data class InstalledExtension(
    val id: String,
    val versionCode: Long,
    val fingerprint: String,
)

@Serializable
enum class ContentWarning { SAFE, MIXED, NSFW }

sealed interface DesktopExtensionInstallResult {
    data class Installed(val manifest: DesktopExtensionManifest) : DesktopExtensionInstallResult
    data class Rejected(val reason: Reason) : DesktopExtensionInstallResult

    enum class Reason {
        MALFORMED,
        UNSIGNED,
        UNTRUSTED,
        DOWNGRADE,
        SIGNATURE_CHANGED,
    }
}

sealed interface DesktopExtensionLoadResult {
    data class Loaded(
        val manifest: DesktopExtensionManifest,
        val sources: List<eu.kanade.tachiyomi.source.Source>,
    ) : DesktopExtensionLoadResult

    data class NotLoaded(val id: String, val reason: Reason, val message: String? = null) : DesktopExtensionLoadResult

    enum class Reason { MALFORMED, UNSIGNED, UNTRUSTED, UNSUPPORTED_LIB_VERSION, FAILED }
}
