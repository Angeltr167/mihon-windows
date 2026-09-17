package mihon.core.network.desktop

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.Cookie as BrowserCookie
import mihon.core.network.ChallengeSolver
import okhttp3.Cookie
import okhttp3.HttpUrl
import java.io.IOException

class PlaywrightChallengeSolver(
    private val preferredChannel: String = System.getenv("MIHON_BROWSER_CHANNEL") ?: "msedge",
) : ChallengeSolver {
    override fun solve(
        url: HttpUrl,
        userAgent: String,
        cookies: List<Cookie>,
        timeoutMillis: Long,
    ): List<Cookie> {
        if (Thread.currentThread().isInterrupted) throw InterruptedException("Challenge solve cancelled")

        Playwright.create().use { playwright ->
            val browser = launchBrowser(playwright)
            try {
                val context = browser.newContext(
                    Browser.NewContextOptions().setUserAgent(userAgent),
                )
                try {
                    if (cookies.isNotEmpty()) {
                        context.addCookies(cookies.map(::toBrowserCookie))
                    }

                    val page = context.newPage()
                    page.navigate(
                        url.toString(),
                        Page.NavigateOptions().setTimeout(timeoutMillis.toDouble()),
                    )

                    val deadline = System.currentTimeMillis() + timeoutMillis
                    while (System.currentTimeMillis() < deadline) {
                        if (Thread.currentThread().isInterrupted) {
                            throw InterruptedException("Challenge solve cancelled")
                        }
                        val solved = context.cookies(url.toString())
                            .firstOrNull { it.name == CLEARANCE_COOKIE }
                        if (solved != null) {
                            return context.cookies(url.toString()).mapNotNull(::toOkHttpCookie)
                        }
                        Thread.sleep(POLL_INTERVAL_MS)
                    }
                    throw IOException("Browser challenge timed out after ${timeoutMillis}ms")
                } finally {
                    context.close()
                }
            } finally {
                browser.close()
            }
        }
    }

    private fun launchBrowser(playwright: Playwright): Browser {
        val channels = listOf(preferredChannel, "msedge", "chrome").filter(String::isNotBlank).distinct()
        var lastError: RuntimeException? = null
        for (channel in channels) {
            try {
                return playwright.chromium().launch(
                    BrowserType.LaunchOptions()
                        .setChannel(channel)
                        .setHeadless(true),
                )
            } catch (e: RuntimeException) {
                lastError = e
            }
        }

        return try {
            playwright.chromium().launch(BrowserType.LaunchOptions().setHeadless(true))
        } catch (e: RuntimeException) {
            throw IOException("No supported Chromium/Edge browser is available for challenge solving", lastError ?: e)
        }
    }

    private fun toBrowserCookie(cookie: Cookie): BrowserCookie = BrowserCookie(cookie.name, cookie.value)
        .setDomain(if (cookie.hostOnly) cookie.domain else ".${cookie.domain}")
        .setPath(cookie.path)
        .setHttpOnly(cookie.httpOnly)
        .setSecure(cookie.secure)
        .apply {
            if (cookie.expiresAt != Long.MAX_VALUE) {
                setExpires(cookie.expiresAt / 1000.0)
            }
        }

    private fun toOkHttpCookie(cookie: BrowserCookie): Cookie? = runCatching {
        Cookie.Builder()
            .name(cookie.name)
            .value(cookie.value)
            .apply {
                val normalizedDomain = cookie.domain.removePrefix(".")
                if (cookie.domain.startsWith('.')) domain(normalizedDomain) else hostOnlyDomain(normalizedDomain)
                path(cookie.path)
                if (cookie.expires > 0) expiresAt((cookie.expires * 1000).toLong())
                if (cookie.secure) secure()
                if (cookie.httpOnly) httpOnly()
            }
            .build()
    }.getOrNull()

    private companion object {
        const val CLEARANCE_COOKIE = "cf_clearance"
        const val POLL_INTERVAL_MS = 50L
    }
}
