package mihon.platform.desktop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class WindowsProtocolRegistrarTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `launcher command quotes installed paths and forwards the URI argument`() {
        val launcher = Files.createFile(tempDir.resolve("Mihon Windows.exe"))
        assertEquals(
            "\"${launcher.toAbsolutePath()}\" \"%1\"",
            WindowsProtocolRegistrar(launcher).launcherCommand(),
        )
    }

    @Test
    fun `development java executable cannot be registered as protocol handler`() {
        val java = Files.createFile(tempDir.resolve("java.exe"))
        assertThrows(IllegalArgumentException::class.java) {
            WindowsProtocolRegistrar(java).launcherCommand()
        }
        assertThrows(IllegalArgumentException::class.java) {
            WindowsProtocolRegistrar(tempDir.resolve("missing.exe")).launcherCommand()
        }
    }
}
