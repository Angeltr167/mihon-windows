package mihon.core.network.desktop

import mihon.core.network.NetworkClientFactory
import mihon.core.network.RateLimiter
import mihon.platform.api.AppDirectories
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.io.File
import java.net.Proxy
import java.nio.file.Path

class DesktopNetworkHelper(
    directories: AppDirectories,
    userAgentProvider: () -> String,
    proxy: Proxy? = null,
    dns: Dns = Dns.SYSTEM,
    minimumRequestIntervalMillis: Long = 0L,
    challengeSolver: PlaywrightChallengeSolver = PlaywrightChallengeSolver(),
) {
    val cookieStore = DesktopCookieStore(Path.of(directories.data).resolve("network").resolve("cookies.json"))

    val client: OkHttpClient = NetworkClientFactory(
        cookieStore = cookieStore,
        cacheDirectory = File(directories.cache, "network_cache"),
        userAgentProvider = userAgentProvider,
        challengeSolver = challengeSolver,
        proxy = proxy,
        dns = dns,
        rateLimiter = minimumRequestIntervalMillis
            .takeIf { it > 0L }
            ?.let(::RateLimiter),
    ).create()
}
