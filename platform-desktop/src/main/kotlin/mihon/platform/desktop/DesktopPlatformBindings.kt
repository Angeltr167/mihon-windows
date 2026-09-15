package mihon.platform.desktop

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
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
import java.nio.file.Paths

@BindingContainer
object DesktopPlatformBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppDirectories(): AppDirectories = DesktopAppDirectoriesProvider().resolve()

    @Provides
    @SingleIn(AppScope::class)
    fun provideKeyValueStore(appDirectories: AppDirectories): KeyValueStore {
        return PropertiesKeyValueStore(Paths.get(appDirectories.config).resolve("platform.properties"))
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideClipboardService(): ClipboardService = DesktopClipboardService()

    @Provides
    @SingleIn(AppScope::class)
    fun provideBrowserService(): BrowserService = DesktopBrowserService()

    @Provides
    @SingleIn(AppScope::class)
    fun provideFileDialogService(): FileDialogService = DesktopFileDialogService()

    @Provides
    @SingleIn(AppScope::class)
    fun provideNotificationService(): NotificationService = DesktopNotificationService()

    @Provides
    @SingleIn(AppScope::class)
    fun provideNetworkStateService(): NetworkStateService = DesktopNetworkStateService()

    @Provides
    @SingleIn(AppScope::class)
    fun provideLocaleService(): LocaleService = DesktopLocaleService()

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppMetadataService(): AppMetadataService = DesktopAppMetadataService()

    @Provides
    @SingleIn(AppScope::class)
    fun provideUriService(browserService: BrowserService): UriService = DesktopUriService(browserService)

    @Provides
    @SingleIn(AppScope::class)
    fun provideApplicationLifecycle(): ApplicationLifecycle = DesktopApplicationLifecycle()

    @Provides
    @SingleIn(AppScope::class)
    fun provideExternalOpenService(): ExternalOpenService = DesktopExternalOpenService()
}
