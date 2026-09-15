package mihon.platform.desktop

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import mihon.platform.api.AppDirectories
import mihon.platform.api.AppMetadataService
import mihon.platform.api.ApplicationLifecycle
import mihon.platform.api.BrowserService
import mihon.platform.api.ClipboardService
import mihon.platform.api.ExternalOpenService
import mihon.platform.api.FileDialogService
import mihon.platform.api.KeyValueStore
import mihon.platform.api.LocaleService
import mihon.platform.api.NetworkStateService
import mihon.platform.api.NotificationService
import mihon.platform.api.UriService

@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [DesktopPlatformBindings::class],
)
interface DesktopPlatformGraph {
    val appDirectories: AppDirectories
    val keyValueStore: KeyValueStore
    val clipboardService: ClipboardService
    val browserService: BrowserService
    val fileDialogService: FileDialogService
    val notificationService: NotificationService
    val networkStateService: NetworkStateService
    val localeService: LocaleService
    val appMetadataService: AppMetadataService
    val uriService: UriService
    val applicationLifecycle: ApplicationLifecycle
    val externalOpenService: ExternalOpenService

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(): DesktopPlatformGraph
    }
}
