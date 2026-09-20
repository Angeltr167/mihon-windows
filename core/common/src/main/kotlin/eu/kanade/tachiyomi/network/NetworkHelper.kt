package eu.kanade.tachiyomi.network

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import mihon.core.network.NetworkClientFactory
import okhttp3.OkHttpClient
import java.io.File

@Inject
@SingleIn(AppScope::class)
class NetworkHelper(
    private val context: Context,
    private val preferences: NetworkPreferences,
) {

    val cookieJar = AndroidCookieJar()

    val client = NetworkClientFactory(
        cookieStore = cookieJar,
        cacheDirectory = File(context.cacheDir, "network_cache"),
        userAgentProvider = ::defaultUserAgentProvider,
        challengeSolver = CloudflareInterceptor(context, cookieJar, ::defaultUserAgentProvider),
        verboseLogging = preferences.verboseLogging.get(),
        additionalInterceptors = listOf(UncaughtExceptionInterceptor()),
    ) {
        when (preferences.dohProvider.get()) {
            PREF_DOH_CLOUDFLARE -> dohCloudflare()
            PREF_DOH_GOOGLE -> dohGoogle()
            PREF_DOH_ADGUARD -> dohAdGuard()
            PREF_DOH_QUAD9 -> dohQuad9()
            PREF_DOH_ALIDNS -> dohAliDNS()
            PREF_DOH_DNSPOD -> dohDNSPod()
            PREF_DOH_360 -> doh360()
            PREF_DOH_QUAD101 -> dohQuad101()
            PREF_DOH_MULLVAD -> dohMullvad()
            PREF_DOH_CONTROLD -> dohControlD()
            PREF_DOH_NJALLA -> dohNajalla()
            PREF_DOH_SHECAN -> dohShecan()
        }
    }.create()

    /**
     * @deprecated Since extension-lib 1.5
     */
    @Deprecated("The regular client handles Cloudflare by default")
    @Suppress("UNUSED")
    val cloudflareClient: OkHttpClient = client

    fun defaultUserAgentProvider() = preferences.defaultUserAgent.get().trim()
}
