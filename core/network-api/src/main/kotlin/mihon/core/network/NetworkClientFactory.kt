package mihon.core.network

import okhttp3.Cache
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.File
import java.net.Proxy
import java.util.concurrent.TimeUnit

class NetworkClientFactory(
    private val cookieStore: CookieStore,
    private val cacheDirectory: File,
    private val userAgentProvider: () -> String,
    private val challengeSolver: ChallengeSolver? = null,
    private val proxy: Proxy? = null,
    private val dns: Dns = Dns.SYSTEM,
    private val rateLimiter: RateLimiter? = null,
) {
    fun create(): OkHttpClient {
        cacheDirectory.mkdirs()

        return OkHttpClient.Builder()
            .cookieJar(cookieStore)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(2, TimeUnit.MINUTES)
            .cache(Cache(cacheDirectory, 5L * 1024L * 1024L))
            .dns(dns)
            .apply {
                this@NetworkClientFactory.proxy?.let { proxy(it) }
                rateLimiter?.let { addInterceptor(RateLimitInterceptor(it)) }
                addInterceptor(UserAgentInterceptor(userAgentProvider))
                challengeSolver?.let {
                    addInterceptor(
                        ChallengeInterceptor(
                            cookieStore = cookieStore,
                            solver = it,
                            userAgentProvider = userAgentProvider,
                        ),
                    )
                }
            }
            .build()
    }
}

class ChallengeInterceptor(
    private val cookieStore: CookieStore,
    private val solver: ChallengeSolver,
    private val userAgentProvider: () -> String,
    private val timeoutMillis: Long = 30_000L,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!response.isChallenge()) return response

        response.close()
        cookieStore.remove(request.url, listOf(CLEARANCE_COOKIE), 0)
        val solvedCookies = solver.solve(
            url = request.url,
            userAgent = userAgentProvider(),
            cookies = cookieStore.get(request.url),
            timeoutMillis = timeoutMillis,
        )
        cookieStore.saveFromResponse(request.url, solvedCookies)
        return chain.proceed(request)
    }

    private fun Response.isChallenge(): Boolean =
        header("cf-mitigated") == "challenge" &&
            header("Server")?.lowercase() in CLOUDFLARE_SERVERS

    private companion object {
        const val CLEARANCE_COOKIE = "cf_clearance"
        val CLOUDFLARE_SERVERS = setOf("cloudflare", "cloudflare-nginx")
    }
}

class UserAgentInterceptor(
    private val userAgentProvider: () -> String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val userAgent = userAgentProvider().trim()
        val outgoing = if (userAgent.isBlank() || request.header("User-Agent") != null) {
            request
        } else {
            request.newBuilder().header("User-Agent", userAgent).build()
        }
        return chain.proceed(outgoing)
    }
}

class RateLimiter(
    private val minimumIntervalMillis: Long,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleeper: (Long) -> Unit = Thread::sleep,
) {
    private var nextAllowedAt = 0L

    @Synchronized
    fun acquire() {
        val now = clock()
        val waitMillis = (nextAllowedAt - now).coerceAtLeast(0L)
        if (waitMillis > 0L) sleeper(waitMillis)
        nextAllowedAt = clock() + minimumIntervalMillis
    }
}

private class RateLimitInterceptor(
    private val limiter: RateLimiter,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        limiter.acquire()
        return chain.proceed(chain.request())
    }
}
