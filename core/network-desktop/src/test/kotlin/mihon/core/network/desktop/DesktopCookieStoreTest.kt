package mihon.core.network.desktop

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class DesktopCookieStoreTest {
    @Test
    fun `cookies survive store recreation`() {
        val file = Files.createTempDirectory("mihon-cookies").resolve("cookies.json")
        val url = "https://example.com/path".toHttpUrl()
        val cookie = Cookie.Builder()
            .name("session")
            .value("abc")
            .hostOnlyDomain("example.com")
            .path("/")
            .expiresAt(System.currentTimeMillis() + 60_000L)
            .httpOnly()
            .secure()
            .build()

        DesktopCookieStore(file).saveFromResponse(url, listOf(cookie))
        val restored = DesktopCookieStore(file).loadForRequest(url)

        assertEquals("abc", restored.single().value)
        assertTrue(restored.single().httpOnly)
        assertTrue(restored.single().secure)
    }
}
