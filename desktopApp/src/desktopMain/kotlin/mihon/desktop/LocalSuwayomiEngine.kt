package mihon.desktop

import mihon.platform.api.AppDirectories
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CountDownLatch

internal const val SUWAYOMI_CHILD_ARGUMENT = "--mihon-suwayomi-child"

/** Allows a jpackage image to fork its own launcher; jpackage omits java.exe/javaw.exe. */
internal fun runSuwayomiChild(args: Array<String>) {
    require(args.size >= 3 && args[0] == SUWAYOMI_CHILD_ARGUMENT)
    args.drop(2).forEach { option ->
        val key = option.substringBefore('=')
        require(key.startsWith("suwayomi.tachidesk.config.server.") && '=' in option)
        System.setProperty(key, option.substringAfter('='))
    }
    val loader = URLClassLoader(arrayOf(Path.of(args[1]).toUri().toURL()), ClassLoader.getPlatformClassLoader())
    Thread.currentThread().contextClassLoader = loader
    loader.loadClass("suwayomi.tachidesk.MainKt")
        .getMethod("main", Array<String>::class.java)
        .invoke(null, emptyArray<String>() as Any)
    // Suwayomi starts asynchronously; closing its classloader when main returns breaks its workers.
    CountDownLatch(1).await()
}

/** Owns the loopback-only extension process bundled with the Windows installer. */
internal class LocalSuwayomiEngine(private val directories: AppDirectories) : AutoCloseable {
    private val profile = Path.of(directories.data).resolve("suwayomi")
    private var process: Process? = null
    private var activeClient: SuwayomiClient? = null

    @Volatile private var loadedSources: List<SuwayomiSource> = emptyList()

    val isRunning: Boolean get() = process?.isAlive == true && activeClient != null
    val logPath: Path get() = profile.resolve("launcher.log")

    @Synchronized
    fun start(): List<SuwayomiSource> {
        if (isRunning) return loadedSources
        val jar = bundledJar()
        require(Files.isRegularFile(jar)) {
            "Suwayomi engine is missing: $jar. Run the packaged app or set mihon.suwayomi.jar."
        }
        Files.createDirectories(profile)
        val port = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val client = SuwayomiClient(URI("http://127.0.0.1:$port"))
        val settings = listOf(
            "suwayomi.tachidesk.config.server.rootDir=${profile.toAbsolutePath()}",
            "suwayomi.tachidesk.config.server.ip=127.0.0.1",
            "suwayomi.tachidesk.config.server.port=$port",
            "suwayomi.tachidesk.config.server.webUIEnabled=false",
            "suwayomi.tachidesk.config.server.initialOpenInBrowserEnabled=false",
            "suwayomi.tachidesk.config.server.systemTrayEnabled=false",
            "suwayomi.tachidesk.config.server.globalUpdateInterval=0",
            "suwayomi.tachidesk.config.server.backupInterval=0",
        ) + listOfNotNull(
            System.getProperty("mihon.suwayomi.kcefEnabled")?.let {
                "suwayomi.tachidesk.config.server.kcefEnabled=$it"
            },
        )
        val command = processCommand(jar, settings)
        val child = ProcessBuilder(command)
            .directory(profile.toFile())
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(logPath.toFile()))
            .start()
        process = child
        return try {
            val deadline = System.nanoTime() + Duration.ofSeconds(75).toNanos()
            while (System.nanoTime() < deadline && child.isAlive && !client.isReady()) {
                Thread.sleep(300)
            }
            check(child.isAlive && client.isReady()) { "Suwayomi did not start. See $logPath" }
            activeClient = client
            refreshSources()
        } catch (failure: Exception) {
            child.destroy()
            activeClient = null
            process = null
            throw failure
        }
    }

    fun sources(): List<SuwayomiSource> = loadedSources

    @Synchronized
    fun refreshSources(): List<SuwayomiSource> {
        val client = requireNotNull(activeClient) { "Extension engine is not running" }
        return client.sources().map { SuwayomiSource(it, client) }.also { loadedSources = it }
    }

    fun client(): SuwayomiClient = requireNotNull(activeClient) { "Extension engine is not running" }

    @Synchronized
    override fun close() {
        activeClient = null
        loadedSources = emptyList()
        process?.let { child ->
            if (child.isAlive) {
                child.destroy()
                if (!child.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) child.destroyForcibly()
            }
        }
        process = null
    }

    private fun bundledJar(): Path {
        System.getProperty("mihon.suwayomi.jar")?.takeIf(String::isNotBlank)?.let { return Path.of(it) }
        val resources = System.getProperty("compose.application.resources.dir")
            ?: return Path.of("desktopApp", "build", "suwayomi-resources", "common", "suwayomi-server.bin")
        return Path.of(resources).resolve("suwayomi-server.bin")
    }

    private fun processCommand(jar: Path, settings: List<String>): List<String> {
        val bin = Path.of(System.getProperty("java.home"), "bin")
        val windows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
        val executable = bin.resolve(if (windows) "javaw.exe" else "java")
        if (Files.isRegularFile(executable)) {
            return listOf(executable.toString()) + settings.map { "-D$it" } +
                listOf("-jar", jar.toAbsolutePath().toString())
        }
        val launcher = Path.of(System.getProperty("java.home")).parent.resolve("Mihon.exe")
        require(windows && Files.isRegularFile(launcher)) {
            "Neither Java nor the bundled Mihon launcher is available for the extension engine"
        }
        return listOf(launcher.toString(), SUWAYOMI_CHILD_ARGUMENT, jar.toAbsolutePath().toString()) + settings
    }
}
