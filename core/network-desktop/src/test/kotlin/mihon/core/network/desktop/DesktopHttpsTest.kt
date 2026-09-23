package mihon.core.network.desktop

import mihon.core.network.NetworkClientFactory
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Request
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files

class DesktopHttpsTest {
    @Test
    fun `https works with a trusted local certificate`() {
        val certificate = HeldCertificate.Builder()
            .addSubjectAlternativeName("localhost")
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(certificate)
            .build()
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(certificate.certificate)
            .build()

        MockWebServer().use { server ->
            server.useHttps(serverCertificates.sslSocketFactory())
            server.enqueue(MockResponse.Builder().body("secure-ok").build())
            server.start()

            val root = Files.createTempDirectory("mihon-https")
            val base = NetworkClientFactory(
                cookieStore = DesktopCookieStore(root.resolve("cookies.json")),
                cacheDirectory = root.resolve("cache").toFile(),
                userAgentProvider = { "Mihon-Windows-Test" },
            ).create()
            val client = base.newBuilder()
                .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
                .build()

            client.newCall(Request.Builder().url(server.url("/secure")).build()).execute().use { response ->
                assertEquals(200, response.code)
                assertEquals("secure-ok", response.body.string())
            }
        }
    }
}
