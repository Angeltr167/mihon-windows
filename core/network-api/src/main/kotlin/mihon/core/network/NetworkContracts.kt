package mihon.core.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

interface CookieStore : CookieJar {
    fun get(url: HttpUrl): List<Cookie> = loadForRequest(url)

    fun remove(
        url: HttpUrl,
        cookieNames: List<String>? = null,
        maxAge: Int = -1,
    ): Int

    fun removeAll()
}

fun interface ChallengeSolver {
    fun solve(
        url: HttpUrl,
        userAgent: String,
        cookies: List<Cookie>,
        timeoutMillis: Long,
    ): List<Cookie>
}
