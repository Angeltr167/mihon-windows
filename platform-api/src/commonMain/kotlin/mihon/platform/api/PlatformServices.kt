package mihon.platform.api

data class OpenFileRequest(
    val title: String,
    val initialDirectory: String? = null,
    val extensions: Set<String> = emptySet(),
)

data class AppMetadata(
    val name: String,
    val versionName: String,
    val buildType: String,
    val platform: String,
)

interface ClipboardService {
    fun readText(): String?

    fun writeText(text: String): Boolean
}

interface BrowserService {
    fun open(url: String): Boolean
}

interface FileDialogService {
    val supported: Boolean

    fun chooseOpenFile(request: OpenFileRequest): String?

    fun chooseDirectory(title: String, initialDirectory: String? = null): String?
}

interface NotificationService {
    val supported: Boolean

    fun show(title: String, body: String): Boolean
}

interface NetworkStateService {
    fun isOnline(): Boolean
}

interface LocaleService {
    fun currentLanguageTag(): String
}

interface AppMetadataService {
    fun current(): AppMetadata
}

interface UriService {
    fun open(uri: String): Boolean
}

interface ApplicationLifecycle {
    fun registerShutdownHook(name: String, callback: () -> Unit)

    fun requestExit(exitCode: Int = 0)
}

interface ExternalOpenService {
    fun openPath(path: String): Boolean

    fun shareText(text: String): Boolean
}
