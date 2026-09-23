package mihon.core.network.desktop

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.core.network.CookieStore
import okhttp3.Cookie
import okhttp3.HttpUrl
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class DesktopCookieStore(
    private val file: Path,
) : CookieStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private var cookies: MutableList<StoredCookie> = load().toMutableList()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            this.cookies.removeAll { stored ->
                stored.expiresAt <= now || cookies.any {
                    it.name == stored.name && it.domain == stored.domain && it.path == stored.path
                }
            }
            this.cookies += cookies
                .filter { it.expiresAt > now }
                .map(StoredCookie::from)
            persist()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(lock) {
        val now = System.currentTimeMillis()
        val expired = cookies.removeAll { it.expiresAt <= now }
        if (expired) persist()
        cookies.mapNotNull(StoredCookie::toCookie).filter { it.matches(url) }
    }

    override fun get(url: HttpUrl): List<Cookie> = loadForRequest(url)

    override fun remove(url: HttpUrl, cookieNames: List<String>?, maxAge: Int): Int = synchronized(lock) {
        val names = cookieNames?.toSet()
        val before = cookies.size
        cookies.removeAll { stored ->
            val cookie = stored.toCookie() ?: return@removeAll true
            cookie.matches(url) && (names == null || stored.name in names)
        }
        val removed = before - cookies.size
        if (removed > 0) persist()
        removed
    }

    override fun removeAll() {
        synchronized(lock) {
            cookies.clear()
            persist()
        }
    }

    private fun load(): List<StoredCookie> {
        if (!Files.isRegularFile(file)) return emptyList()
        return runCatching {
            json.decodeFromString<List<StoredCookie>>(Files.readString(file))
        }.getOrDefault(emptyList())
    }

    private fun persist() {
        file.parent?.let(Files::createDirectories)
        val temp = Files.createTempFile(file.parent, "cookies", ".tmp")
        Files.writeString(temp, json.encodeToString(cookies))
        try {
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

@Serializable
private data class StoredCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
    val expiresAt: Long,
    val secure: Boolean,
    val httpOnly: Boolean,
    val hostOnly: Boolean,
) {
    fun toCookie(): Cookie? = runCatching {
        Cookie.Builder()
            .name(name)
            .value(value)
            .apply {
                if (hostOnly) hostOnlyDomain(domain) else domain(domain)
                path(path)
                expiresAt(expiresAt)
                if (secure) secure()
                if (httpOnly) httpOnly()
            }
            .build()
    }.getOrNull()

    companion object {
        fun from(cookie: Cookie) = StoredCookie(
            name = cookie.name,
            value = cookie.value,
            domain = cookie.domain,
            path = cookie.path,
            expiresAt = cookie.expiresAt,
            secure = cookie.secure,
            httpOnly = cookie.httpOnly,
            hostOnly = cookie.hostOnly,
        )
    }
}
