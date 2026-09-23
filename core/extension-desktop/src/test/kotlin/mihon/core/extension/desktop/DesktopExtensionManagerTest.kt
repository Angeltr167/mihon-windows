package mihon.core.extension.desktop

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.platform.api.AppDirectories
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPairGenerator
import javax.tools.ToolProvider
import kotlin.io.path.writeText

class DesktopExtensionManagerTest {
    @Test
    fun `empty extension directory has no installed extensions`() {
        val root = Files.createTempDirectory("mihonext-empty")
        assertTrue(DesktopExtensionManager(directories(root)).loadInstalled().isEmpty())
    }

    @Test
    fun `trusted package loads source and rejects invalid neighbors`() {
        val root = Files.createTempDirectory("mihonext")
        val manager = DesktopExtensionManager(directories(root))
        val key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val extensionJar = compileReferenceExtension(root)
        val packageFile = createPackage(root, extensionJar, key, versionCode = 2)

        assertType<DesktopExtensionInstallResult.Rejected>(manager.install(packageFile)).also {
            assertEquals(DesktopExtensionInstallResult.Reason.UNTRUSTED, it.reason)
        }
        manager.trust(fingerprint(key.public.encoded))
        assertType<DesktopExtensionInstallResult.Installed>(manager.install(packageFile))

        val loaded = manager.loadInstalled()
        val extension = assertType<DesktopExtensionLoadResult.Loaded>(loaded.single())
        assertEquals("fixture-source", extension.sources.single().name)
        assertEquals(42L, extension.sources.single().id)
        assertTrue(runBlocking { extension.sources.single().getPopularManga(1) }.mangas.isEmpty())
        val cachedJar = Path.of(directories(root).extensions).resolve("runtime")
            .resolve("${extension.manifest.id}-${extension.manifest.jarSha256}.jar")
        Files.writeString(cachedJar, "tampered")
        assertType<DesktopExtensionLoadResult.Loaded>(manager.loadInstalled().single())
        assertEquals(extension.manifest.jarSha256, DesktopExtensionPackager.sha256(Files.readAllBytes(cachedJar)))

        val downgrade = createPackage(root.resolve("downgrade"), extensionJar, key, versionCode = 1)
        assertEquals(
            DesktopExtensionInstallResult.Reason.DOWNGRADE,
            assertType<DesktopExtensionInstallResult.Rejected>(manager.install(downgrade)).reason,
        )

        val otherKey = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        manager.trust(fingerprint(otherKey.public.encoded))
        val replaced = createPackage(root.resolve("replacement"), extensionJar, otherKey, versionCode = 3)
        assertEquals(
            DesktopExtensionInstallResult.Reason.SIGNATURE_CHANGED,
            assertType<DesktopExtensionInstallResult.Rejected>(manager.install(replaced)).reason,
        )

        Files.writeString(
            Path.of(directories(root).extensions).resolve("packages").resolve("broken.mihonext"),
            "not a zip",
        )
        assertTrue(manager.loadInstalled().any { it is DesktopExtensionLoadResult.NotLoaded })
    }

    @Test
    fun `load requires installed record and respects content warning filter`() {
        val root = Files.createTempDirectory("mihonext-policy")
        val key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val jar = compileReferenceExtension(root)
        val packageFile = createPackage(root, jar, key, versionCode = 1, warning = ContentWarning.NSFW)
        val manager = DesktopExtensionManager(directories(root), enabledContentWarnings = setOf(ContentWarning.SAFE))
        manager.trust(fingerprint(key.public.encoded))
        assertEquals(
            DesktopExtensionLoadResult.Reason.NOT_INSTALLED,
            assertType<DesktopExtensionLoadResult.NotLoaded>(manager.load(packageFile)).reason,
        )
        assertType<DesktopExtensionInstallResult.Installed>(manager.install(packageFile))
        assertEquals(
            DesktopExtensionLoadResult.Reason.FILTERED,
            assertType<DesktopExtensionLoadResult.NotLoaded>(manager.loadInstalled().single()).reason,
        )
    }

    @Test
    fun `repository discovers updates and verifies listed package before install`() {
        val root = Files.createTempDirectory("mihonext-repository")
        val key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val jar = compileReferenceExtension(root)
        val packageBytes = Files.readAllBytes(createPackage(root, jar, key, versionCode = 3))
        val fingerprint = fingerprint(key.public.encoded)
        val entry = DesktopRepositoryEntry(
            id = "fixture.extension",
            name = "Fixture",
            versionCode = 3,
            packageUrl = "fixture.mihonext",
            sha256 = DesktopExtensionPackager.sha256(packageBytes),
            fingerprint = fingerprint,
        )
        val indexUri = URI("https://extensions.example/index.json")
        var listed = entry
        val repository = DesktopExtensionRepository(root.resolve("temp")) { uri, _ ->
            when (uri) {
                indexUri -> Json.encodeToString(DesktopRepositoryIndex(1, listOf(listed))).encodeToByteArray()
                indexUri.resolve(entry.packageUrl) -> packageBytes
                else -> error("Unexpected repository URL: $uri")
            }
        }
        val manager = DesktopExtensionManager(directories(root))
        manager.trust(fingerprint)
        assertEquals(listOf(entry), repository.updates(indexUri, manager))
        listed = entry.copy(fingerprint = "0".repeat(64))
        assertEquals(
            DesktopExtensionInstallResult.Reason.INDEX_MISMATCH,
            assertType<DesktopExtensionInstallResult.Rejected>(repository.install(indexUri, listed, manager)).reason,
        )
        assertTrue(manager.installedExtensions().isEmpty())
        listed = entry
        assertType<DesktopExtensionInstallResult.Installed>(repository.install(indexUri, entry, manager))
        assertTrue(repository.updates(indexUri, manager).isEmpty())
        assertType<DesktopExtensionLoadResult.Loaded>(manager.loadInstalled().single())
    }

    @Test
    fun `throwing extension does not prevent a healthy neighbor from loading`() {
        val root = Files.createTempDirectory("mihonext-failure")
        val key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val jar = compileReferenceExtension(root)
        val manager = DesktopExtensionManager(directories(root))
        manager.trust(fingerprint(key.public.encoded))
        assertType<DesktopExtensionInstallResult.Installed>(
            manager.install(
                createPackage(
                    root.resolve("bad"),
                    jar,
                    key,
                    1,
                    id = "bad.extension",
                    sourceClass = "fixture.Factory\$ThrowingFactory",
                ),
            ),
        )
        assertType<DesktopExtensionInstallResult.Installed>(
            manager.install(createPackage(root.resolve("good"), jar, key, 1, id = "good.extension")),
        )
        val loaded = manager.loadInstalled()
        assertEquals(2, loaded.size)
        assertTrue(loaded.any { it is DesktopExtensionLoadResult.NotLoaded && it.id == "bad.extension" })
        assertTrue(loaded.any { it is DesktopExtensionLoadResult.Loaded && it.manifest.id == "good.extension" })
    }

    @Test
    fun `packager CLI builds installable desktop artifact`() {
        val root = Files.createTempDirectory("mihonext-cli")
        val key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val jar = compileReferenceExtension(root)
        val manifest = DesktopExtensionManifest(
            formatVersion = 1,
            id = "cli.extension",
            name = "CLI fixture",
            versionName = "1.0",
            versionCode = 1,
            libVersion = 1.6,
            sourceClasses = listOf("fixture.Factory"),
            jarSha256 = DesktopExtensionPackager.sha256(Files.readAllBytes(jar)),
        )
        val manifestFile = root.resolve("manifest.json")
        val publicKey = root.resolve("public.der")
        val privateKey = root.resolve("private.pkcs8")
        val packageFile = root.resolve("cli.mihonext")
        Files.writeString(manifestFile, Json.encodeToString(manifest))
        Files.write(publicKey, key.public.encoded)
        Files.write(privateKey, key.private.encoded)
        DesktopExtensionPackagerCli.main(
            arrayOf(
                packageFile.toString(),
                manifestFile.toString(),
                jar.toString(),
                publicKey.toString(),
                privateKey.toString(),
            ),
        )
        val manager = DesktopExtensionManager(directories(root))
        manager.trust(fingerprint(key.public.encoded))
        assertType<DesktopExtensionInstallResult.Installed>(manager.install(packageFile))
        assertType<DesktopExtensionLoadResult.Loaded>(manager.loadInstalled().single())
    }

    private fun createPackage(
        root: Path,
        jar: Path,
        key: java.security.KeyPair,
        versionCode: Long,
        warning: ContentWarning = ContentWarning.SAFE,
        id: String = "fixture.extension",
        sourceClass: String = "fixture.Factory",
    ): Path {
        val packageFile = root.resolve("fixture-$versionCode.mihonext")
        DesktopExtensionPackager.create(
            output = packageFile,
            manifest = DesktopExtensionManifest(
                formatVersion = 1,
                id = id,
                name = "Fixture",
                versionName = "1.$versionCode",
                versionCode = versionCode,
                libVersion = 1.6,
                contentWarning = warning,
                sourceClasses = listOf(sourceClass),
                jarSha256 = DesktopExtensionPackager.sha256(Files.readAllBytes(jar)),
            ),
            extensionJar = jar,
            publicKey = key.public.encoded,
            privateKey = key.private,
        )
        return packageFile
    }

    private fun compileReferenceExtension(root: Path): Path {
        val source = root.resolve("Factory.java")
        source.writeText(
            """
            package fixture;
            import eu.kanade.tachiyomi.source.*;
            import eu.kanade.tachiyomi.source.model.*;
            import java.util.*;
            import kotlin.coroutines.Continuation;
            public final class Factory implements SourceFactory {
              public List<Source> createSources() { return Collections.singletonList(new FixtureSource()); }
              public static final class ThrowingFactory implements SourceFactory {
                public List<Source> createSources() { throw new IllegalStateException("fixture failure"); }
              }
              public static final class FixtureSource implements Source {
                public long getId() { return 42L; }
                public String getName() { return "fixture-source"; }
                public boolean getSupportsLatest() { return false; }
                public Object getPopularManga(int p, Continuation<? super MangasPage> c) { return new MangasPage(Collections.emptyList(), false); }
                public Object getLatestUpdates(int p, Continuation<? super MangasPage> c) { return new MangasPage(Collections.emptyList(), false); }
                public Object getSearchManga(int p, String q, FilterList f, Continuation<? super MangasPage> c) { return new MangasPage(Collections.emptyList(), false); }
                public Object getMangaUpdate(SManga m, List<? extends SChapter> c, boolean d, boolean h, Continuation<? super SMangaUpdate> k) { return null; }
                public Object getPageList(SChapter c, Continuation<? super List<? extends Page>> k) { return null; }
              }
            }
            """.trimIndent(),
        )
        val output = root.resolve("classes")
        Files.createDirectories(output)
        val compiler = requireNotNull(ToolProvider.getSystemJavaCompiler())
        assertEquals(
            0,
            compiler.run(
                null,
                null,
                null,
                "-classpath",
                System.getProperty("java.class.path"),
                "-d",
                output.toString(),
                source.toString(),
            ),
        )
        val jar = root.resolve("extension.jar")
        java.util.jar.JarOutputStream(Files.newOutputStream(jar)).use { outputJar ->
            Files.walk(output).use { files ->
                files.filter(Files::isRegularFile).forEach { file ->
                    val name = output.relativize(file).toString().replace('\\', '/')
                    outputJar.putNextEntry(java.util.jar.JarEntry(name))
                    Files.copy(file, outputJar)
                    outputJar.closeEntry()
                }
            }
        }
        return jar
    }

    private fun directories(root: Path) = AppDirectories(
        config = root.resolve("config").toString(),
        data = root.resolve("data").toString(),
        cache = root.resolve("cache").toString(),
        database = root.resolve("database").toString(),
        downloads = root.resolve("downloads").toString(),
        localLibrary = root.resolve("local").toString(),
        extensions = root.resolve("extensions").toString(),
        temp = root.resolve("temp").toString(),
    )

    private fun fingerprint(key: ByteArray) = DesktopExtensionPackager.sha256(key)

    private inline fun <reified T> assertType(value: Any): T {
        assertTrue(value is T, "Expected ${T::class.simpleName}, got ${value::class.simpleName}")
        return value as T
    }
}
