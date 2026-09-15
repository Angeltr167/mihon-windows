package mihon.platform.desktop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DesktopPlatformFoundationTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `windows directories follow roaming and local app data roots`() {
        val appData = tempDir.resolve("Roaming")
        val localAppData = tempDir.resolve("Local")
        val home = tempDir.resolve("Home")
        val directories = DesktopAppDirectoriesProvider(
            environment = mapOf(
                "APPDATA" to appData.toString(),
                "LOCALAPPDATA" to localAppData.toString(),
            ),
            userHome = home.toString(),
        ).resolve()

        assertEquals(appData.resolve("Mihon").toString(), directories.config)
        assertEquals(localAppData.resolve("Mihon").toString(), directories.data)
        assertTrue(Files.isDirectory(Path.of(directories.database)))
        assertTrue(Files.isDirectory(Path.of(directories.extensions)))
        assertTrue(Files.isDirectory(Path.of(directories.downloads)))
    }

    @Test
    fun `properties store persists platform values`() {
        val file = tempDir.resolve("settings").resolve("platform.properties")
        val store = PropertiesKeyValueStore(file)

        store.putString("name", "mihon")
        store.putLong("count", 7L)
        store.putBoolean("enabled", true)

        val reopened = PropertiesKeyValueStore(file)
        assertEquals("mihon", reopened.getString("name"))
        assertEquals(7L, reopened.getLong("count"))
        assertTrue(reopened.getBoolean("enabled"))
        assertFalse(reopened.contains("missing"))
    }
}
