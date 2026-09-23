package eu.kanade.tachiyomi.network

import okhttp3.CookieJar
import okhttp3.OkHttpClient

/** JVM counterpart of the Android extension-facing network helper. */
class NetworkHelper private constructor(
    val client: OkHttpClient,
    private val userAgentProvider: () -> String,
) {
    val cookieJar: CookieJar get() = client.cookieJar
    val cloudflareClient: OkHttpClient get() = client

    fun defaultUserAgentProvider(): String = userAgentProvider()

    companion object {
        @Volatile private var installed: NetworkHelper? = null

        fun install(client: OkHttpClient, userAgentProvider: () -> String) {
            installed = NetworkHelper(client, userAgentProvider)
        }

        fun current(): NetworkHelper = checkNotNull(installed) { "Desktop network helper is not initialized" }
    }
}
