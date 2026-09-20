package mihon.core.extension.desktop

import mihon.platform.api.AppDirectories
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPairGenerator
import javax.tools.ToolProvider
import kotlin.io.path.writeText

class DesktopExtensionManagerTest {
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

    private fun createPackage(root: Path, jar: Path, key: java.security.KeyPair, versionCode: Long): Path {
        val packageFile = root.resolve("fixture-$versionCode.mihonext")
        DesktopExtensionPackager.create(
            output = packageFile,
            manifest = DesktopExtensionManifest(
                formatVersion = 1,
                id = "fixture.extension",
                name = "Fixture",
                versionName = "1.$versionCode",
                versionCode = versionCode,
                libVersion = 1.6,
                sourceClasses = listOf("fixture.Factory"),
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
              public static final class FixtureSource implements Source {
                public long getId() { return 42L; }
                public String getName() { return "fixture-source"; }
                public boolean getSupportsLatest() { return false; }
                public Object getPopularManga(int p, Continuation<? super MangasPage> c) { return null; }
                public Object getLatestUpdates(int p, Continuation<? super MangasPage> c) { return null; }
                public Object getSearchManga(int p, String q, FilterList f, Continuation<? super MangasPage> c) { return null; }
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
