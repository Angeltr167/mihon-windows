package mihon.platform.desktop

import mihon.platform.api.AppMetadata
import mihon.platform.api.AppMetadataService
import mihon.platform.api.ApplicationLifecycle
import mihon.platform.api.BrowserService
import mihon.platform.api.ClipboardService
import mihon.platform.api.ExternalOpenService
import mihon.platform.api.FileDialogService
import mihon.platform.api.LocaleService
import mihon.platform.api.NetworkStateService
import mihon.platform.api.NotificationService
import mihon.platform.api.OpenFileRequest
import mihon.platform.api.UriService
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.NetworkInterface
import java.net.URI
import java.util.Locale
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.system.exitProcess

class DesktopClipboardService : ClipboardService {
    override fun readText(): String? = runCatching {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.getData(DataFlavor.stringFlavor) as? String
    }.getOrNull()

    override fun writeText(text: String): Boolean = runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        true
    }.getOrDefault(false)
}

class DesktopBrowserService : BrowserService {
    override fun open(url: String): Boolean = runCatching {
        if (!Desktop.isDesktopSupported()) return false
        Desktop.getDesktop().browse(URI(url))
        true
    }.getOrDefault(false)
}

class DesktopFileDialogService : FileDialogService {
    override val supported: Boolean = true

    override fun chooseOpenFile(request: OpenFileRequest): String? = runCatching {
        val chooser = JFileChooser(request.initialDirectory)
        chooser.dialogTitle = request.title
        chooser.fileSelectionMode = JFileChooser.FILES_ONLY
        if (request.extensions.isNotEmpty()) {
            chooser.fileFilter = FileNameExtensionFilter(
                request.extensions.joinToString(", ") { "*.$it" },
                *request.extensions.toTypedArray(),
            )
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile.absolutePath
        } else {
            null
        }
    }.getOrNull()

    override fun chooseDirectory(title: String, initialDirectory: String?): String? = runCatching {
        val chooser = JFileChooser(initialDirectory)
        chooser.dialogTitle = title
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile.absolutePath
        } else {
            null
        }
    }.getOrNull()
}

class DesktopNotificationService : NotificationService {
    override val supported: Boolean = false

    override fun show(title: String, body: String): Boolean = false
}

class DesktopNetworkStateService : NetworkStateService {
    override fun isOnline(): Boolean = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().any { network ->
            network.isUp && !network.isLoopback
        }
    }.getOrDefault(false)
}

class DesktopLocaleService : LocaleService {
    override fun currentLanguageTag(): String = Locale.getDefault().toLanguageTag()
}

class DesktopAppMetadataService : AppMetadataService {
    override fun current(): AppMetadata {
        val version = javaClass.`package`?.implementationVersion ?: "dev"
        return AppMetadata(
            name = "Mihon",
            versionName = version,
            buildType = "desktop",
            platform = "windows-jvm",
        )
    }
}

class DesktopUriService(
    private val browserService: BrowserService,
) : UriService {
    override fun open(uri: String): Boolean = browserService.open(uri)
}

class DesktopApplicationLifecycle : ApplicationLifecycle {
    override fun registerShutdownHook(name: String, callback: () -> Unit) {
        Runtime.getRuntime().addShutdownHook(Thread(callback, name))
    }

    override fun requestExit(exitCode: Int) = exitProcess(exitCode)
}

class DesktopExternalOpenService : ExternalOpenService {
    override fun openPath(path: String): Boolean = runCatching {
        if (!Desktop.isDesktopSupported()) return false
        Desktop.getDesktop().open(File(path))
        true
    }.getOrDefault(false)

    override fun shareText(text: String): Boolean = false
}
