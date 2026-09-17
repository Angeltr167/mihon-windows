package mihon.core.network.desktop

import com.sun.net.httpserver.HttpServer
import mihon.core.network.NetworkClientFactory
import mihon.core.network.RateLimiter
import okhttp3.Dns
import okhttp3.Request
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.nio.file.Files
import java.util.zip.GZIPOutputStream

class DesktopNetworkCoreTest {
    @Test
    fun `http redirects gzip custom dns proxy and rate limiter are supported`() {
        val root = Files.createTempDirectory("mihon-network")
        val store = DesktopCookieStore(root.resolve("cookies.json"))
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.add("Location", "/gzip")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/gzip") { exchange ->
            val payload = gzip("network-ok")
            exchange.responseHeaders.add("Content-Encoding", "gzip")
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.createContext("/proxy") { exchange ->
            val payload = "proxy-ok".toByteArray()
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.start()

        try {
            val dns = Dns { listOf(InetAddress.getLoopbackAddress()) }
            val client = NetworkClientFactory(
                cookieStore = store,
                cacheDirectory = root.resolve("cache").toFile(),
                userAgentProvider = { "Mihon-Windows-Test" },
                dns = dns,
            ).create()
            val url = "http://mihon.test:${server.address.port}/redirect"
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                assertEquals(200, response.code)
                assertEquals("network-ok", response.body.string())
                assertEquals("Mihon-Windows-Test", response.request.header("User-Agent"))
            }

            val proxyClient = NetworkClientFactory(
                cookieStore = store,
                cacheDirectory = root.resolve("proxy-cache").toFile(),
                userAgentProvider = { "Mihon-Windows-Test" },
                proxy = Proxy(Proxy.Type.HTTP, server.address),
            ).create()
            proxyClient.newCall(Request.Builder().url("http://example.invalid/proxy").build()).execute().use { response ->
                assertEquals(200, response.code)
                assertEquals("proxy-ok", response.body.string())
            }

            var now = 1_000L
            val sleeps = mutableListOf<Long>()
            val limiter = RateLimiter(
                minimumIntervalMillis = 100L,
                clock = { now },
                sleeper = { delay -> sleeps += delay; now += delay },
            )
            limiter.acquire()
            limiter.acquire()
            assertEquals(listOf(100L), sleeps)
            assertTrue(root.resolve("cache").toFile().exists())
        } finally {
            server.stop(0)
        }
    }

    private fun gzip(value: String): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(value.toByteArray()) }
        return output.toByteArray()
    }
}
