package mihon.platform.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
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
import kotlin.system.exitProcess

class AndroidClipboardService(
    private val context: Context,
) : ClipboardService {
    private val clipboard: ClipboardManager
        get() = context.getSystemService(ClipboardManager::class.java)

    override fun readText(): String? = clipboard.primaryClip
        ?.getItemAt(0)
        ?.coerceToText(context)
        ?.toString()

    override fun writeText(text: String): Boolean = runCatching {
        clipboard.setPrimaryClip(ClipData.newPlainText("Mihon", text))
        true
    }.getOrDefault(false)
}

class AndroidBrowserService(
    private val context: Context,
) : BrowserService {
    override fun open(url: String): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}

class AndroidFileDialogService : FileDialogService {
    override val supported: Boolean = false

    override fun chooseOpenFile(request: OpenFileRequest): String? = null

    override fun chooseDirectory(title: String, initialDirectory: String?): String? = null
}

class AndroidNotificationService : NotificationService {
    override val supported: Boolean = false

    override fun show(title: String, body: String): Boolean = false
}

class AndroidNetworkStateService(
    private val context: Context,
) : NetworkStateService {
    override fun isOnline(): Boolean = runCatching {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(false)
}

class AndroidLocaleService(
    private val context: Context,
) : LocaleService {
    override fun currentLanguageTag(): String {
        return context.resources.configuration.locales[0].toLanguageTag()
    }
}

class AndroidAppMetadataService(
    private val context: Context,
) : AppMetadataService {
    override fun current(): AppMetadata {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return AppMetadata(
            name = context.applicationInfo.loadLabel(context.packageManager).toString(),
            versionName = info.versionName.orEmpty(),
            buildType = if ((context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) !=
                0
            ) {
                "debug"
            } else {
                "release"
            },
            platform = "android",
        )
    }
}

class AndroidUriService(
    private val browserService: BrowserService,
) : UriService {
    override fun open(uri: String): Boolean = browserService.open(uri)
}

class AndroidApplicationLifecycle : ApplicationLifecycle {
    override fun registerShutdownHook(name: String, callback: () -> Unit) {
        Runtime.getRuntime().addShutdownHook(Thread(callback, name))
    }

    override fun requestExit(exitCode: Int) = exitProcess(exitCode)
}

class AndroidExternalOpenService(
    private val context: Context,
) : ExternalOpenService {
    override fun openPath(path: String): Boolean = false

    override fun shareText(text: String): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}
