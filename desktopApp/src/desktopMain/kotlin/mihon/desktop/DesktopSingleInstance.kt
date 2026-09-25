package mihon.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** Keeps one Desktop process active and forwards protocol/browser links from later launches to it. */
internal class DesktopSingleInstance(private val configDirectory: Path) : AutoCloseable {
    private val _incomingLink = MutableStateFlow<String?>(null)
    val incomingLink: StateFlow<String?> = _incomingLink

    private var lockChannel: FileChannel? = null
    private var processLock: FileLock? = null
    private var server: ServerSocket? = null
    private var serverThread: Thread? = null
    private var secret: String? = null
    private val closed = AtomicBoolean(false)

    /** Returns true when this process owns the app instance; false after forwarding or a duplicate launch. */
    fun startOrForward(arguments: Array<String>): Boolean {
        require(arguments.size <= 1) { "Mihon accepts one startup link" }
        Files.createDirectories(configDirectory)
        val channel = FileChannel.open(
            configDirectory.resolve(LOCK_FILE),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
        )
        val lock = try {
            channel.tryLock()
        } catch (_: OverlappingFileLockException) {
            null
        }
        if (lock != null) {
            lockChannel = channel
            processLock = lock
            startServer()
            return true
        }
        channel.close()
        val link = arguments.singleOrNull() ?: return false
        require(isSupportedLink(link)) { "Unsupported Mihon link" }
        forwardToRunningInstance(link)
        return false
    }

    private fun startServer() {
        val instanceSecret = ByteArray(32).also(SecureRandom()::nextBytes)
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        val listener = ServerSocket().apply {
            reuseAddress = false
            bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 8)
        }
        server = listener
        secret = instanceSecret
        publishEndpoint(listener.localPort, instanceSecret)
        serverThread = thread(name = "mihon-link-receiver", isDaemon = true) {
            while (!closed.get()) {
                try {
                    listener.accept().use(::receiveLink)
                } catch (_: SocketException) {
                    if (!closed.get()) continue
                } catch (_: IOException) {
                    if (!closed.get()) continue
                }
            }
        }
    }

    private fun receiveLink(socket: Socket) {
        socket.soTimeout = SOCKET_TIMEOUT_MILLIS
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())
        val providedSecret = runCatching { input.readUTF() }.getOrNull()
        val link = runCatching { input.readUTF() }.getOrNull()
        val accepted = providedSecret == secret && link != null && isSupportedLink(link)
        if (accepted) _incomingLink.value = link
        output.writeBoolean(accepted)
        output.flush()
    }

    private fun forwardToRunningInstance(link: String) {
        val endpointPath = configDirectory.resolve(ENDPOINT_FILE)
        var lastError: Exception? = null
        repeat(FORWARD_ATTEMPTS) {
            val properties = Properties()
            if (Files.isRegularFile(endpointPath)) {
                runCatching { Files.newInputStream(endpointPath).use(properties::load) }
                val port = properties.getProperty(PORT_PROPERTY)?.toIntOrNull()
                val instanceSecret = properties.getProperty(SECRET_PROPERTY)
                if (port != null && port in 1..65535 && !instanceSecret.isNullOrBlank()) {
                    try {
                        Socket().use { socket ->
                            socket.connect(
                                InetSocketAddress(InetAddress.getLoopbackAddress(), port),
                                CONNECT_TIMEOUT_MILLIS,
                            )
                            socket.soTimeout = SOCKET_TIMEOUT_MILLIS
                            val output = DataOutputStream(socket.getOutputStream())
                            output.writeUTF(instanceSecret)
                            output.writeUTF(link)
                            output.flush()
                            check(DataInputStream(socket.getInputStream()).readBoolean()) {
                                "The running Mihon instance rejected the link"
                            }
                            return
                        }
                    } catch (e: Exception) {
                        lastError = e
                    }
                }
            }
            try {
                Thread.sleep(RETRY_DELAY_MILLIS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Interrupted while forwarding a link", lastError)
            }
        }
        throw IOException("Could not reach the running Mihon instance to open the link", lastError)
    }

    private fun publishEndpoint(port: Int, instanceSecret: String) {
        val endpoint = configDirectory.resolve(ENDPOINT_FILE)
        val temporary = configDirectory.resolve("$ENDPOINT_FILE.tmp")
        val properties = Properties().apply {
            setProperty(PORT_PROPERTY, port.toString())
            setProperty(SECRET_PROPERTY, instanceSecret)
        }
        Files.newOutputStream(temporary, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use {
            properties.store(it, null)
        }
        try {
            Files.move(temporary, endpoint, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: IOException) {
            Files.move(temporary, endpoint, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { server?.close() }
        runCatching { serverThread?.join(SOCKET_TIMEOUT_MILLIS.toLong()) }
        val endpoint = configDirectory.resolve(ENDPOINT_FILE)
        runCatching {
            val properties = Properties()
            if (Files.isRegularFile(endpoint)) Files.newInputStream(endpoint).use(properties::load)
            if (properties.getProperty(SECRET_PROPERTY) == secret) Files.deleteIfExists(endpoint)
        }
        runCatching { processLock?.release() }
        runCatching { lockChannel?.close() }
    }

    private fun isSupportedLink(link: String): Boolean {
        if (link.length !in 1..MAX_LINK_LENGTH || link.any(Char::isISOControl)) return false
        val uri = runCatching { java.net.URI(link) }.getOrNull() ?: return false
        return when (uri.scheme?.lowercase()) {
            "http", "https" -> !uri.host.isNullOrBlank() && uri.userInfo == null
            "mihon", "tachiyomi" -> !uri.host.isNullOrBlank() && uri.userInfo == null
            else -> false
        }
    }

    private companion object {
        const val LOCK_FILE = "desktop-instance.lock"
        const val ENDPOINT_FILE = "desktop-instance.properties"
        const val PORT_PROPERTY = "port"
        const val SECRET_PROPERTY = "secret"
        const val MAX_LINK_LENGTH = 8192
        const val FORWARD_ATTEMPTS = 20
        const val RETRY_DELAY_MILLIS = 100L
        const val CONNECT_TIMEOUT_MILLIS = 1000
        const val SOCKET_TIMEOUT_MILLIS = 3000
    }
}
