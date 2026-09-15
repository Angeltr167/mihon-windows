package mihon.platform.android

import android.content.Context
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

@BindingContainer
object AndroidPlatformBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppDirectories(context: Context): AppDirectories = AndroidAppDirectoriesProvider(context).resolve()

    @Provides
    @SingleIn(AppScope::class)
    fun provideKeyValueStore(context: Context): KeyValueStore = AndroidKeyValueStore(context)

    @Provides
    @SingleIn(AppScope::class)
    fun provideClipboardService(context: Context): ClipboardService = AndroidClipboardService(context)

    @Provides
    @SingleIn(AppScope::class)
    fun provideBrowserService(context: Context): BrowserService = AndroidBrowserService(context)

    @Provides
    @SingleIn(AppScope::class)
    fun provideFileDialogService(): FileDialogService = AndroidFileDialogService()

    @Provides
    @SingleIn(AppScope::class)
    fun provideNotificationService(): NotificationService = AndroidNotificationService()

    @Provides
    @SingleIn(AppScope::class)
    fun provideNetworkStateService(context: Context): NetworkStateService = AndroidNetworkStateService(context)

    @Provides
    @SingleIn(AppScope::class)
    fun provideLocaleService(context: Context): LocaleService = AndroidLocaleService(context)

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppMetadataService(context: Context): AppMetadataService = AndroidAppMetadataService(context)

    @Provides
    @SingleIn(AppScope::class)
    fun provideUriService(browserService: BrowserService): UriService = AndroidUriService(browserService)

    @Provides
    @SingleIn(AppScope::class)
    fun provideApplicationLifecycle(): ApplicationLifecycle = AndroidApplicationLifecycle()

    @Provides
    @SingleIn(AppScope::class)
    fun provideExternalOpenService(context: Context): ExternalOpenService = AndroidExternalOpenService(context)
}
