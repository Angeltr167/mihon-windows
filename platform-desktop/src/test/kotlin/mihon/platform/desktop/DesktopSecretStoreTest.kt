package mihon.platform.desktop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DesktopSecretStoreTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `tokens are protected for the Windows user and survive restart`() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows", ignoreCase = true))
        val store = DesktopSecretStore(tempDir)
        assertNull(store.read("anilist-token"))
        store.write("anilist-token", "fixture-secret-token")
        val file = tempDir.resolve("anilist-token.dpapi")
        assertFalse(Files.readAllBytes(file).decodeToString().contains("fixture-secret-token"))
        assertEquals("fixture-secret-token", DesktopSecretStore(tempDir).read("anilist-token"))
        store.remove("anilist-token")
        assertNull(store.read("anilist-token"))
    }

    @Test
    fun `secret names cannot escape the credential directory`() {
        val store = DesktopSecretStore(tempDir)
        assertThrows(IllegalArgumentException::class.java) { store.read("../outside") }
    }
}
