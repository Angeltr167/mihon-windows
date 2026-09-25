package mihon.desktop

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DesktopSingleInstanceTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `second launch forwards browser URL to the active app`() = runBlocking {
        DesktopSingleInstance(tempDir).use { primary ->
            assertTrue(primary.startOrForward(emptyArray()))
            DesktopSingleInstance(tempDir).use { secondary ->
                assertFalse(secondary.startOrForward(arrayOf("mihon://anilist-auth#access_token=fixture")))
            }
            assertEquals(
                "mihon://anilist-auth#access_token=fixture",
                withTimeout(2000) { primary.incomingLink.first { it != null } },
            )
        }
    }

    @Test
    fun `unsupported links and multiple startup arguments are rejected`() {
        DesktopSingleInstance(tempDir).use { primary ->
            assertTrue(primary.startOrForward(emptyArray()))
            DesktopSingleInstance(tempDir).use { secondary ->
                assertThrows(IllegalArgumentException::class.java) {
                    secondary.startOrForward(arrayOf("file:///C:/private.txt"))
                }
                assertThrows(IllegalArgumentException::class.java) {
                    secondary.startOrForward(arrayOf("https://example.org", "https://example.net"))
                }
                assertThrows(IllegalArgumentException::class.java) {
                    secondary.startOrForward(arrayOf("javascript:alert(1)"))
                }
            }
        }
    }
}
