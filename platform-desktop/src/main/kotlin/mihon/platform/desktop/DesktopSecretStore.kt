package mihon.platform.desktop

import com.sun.jna.platform.win32.Crypt32Util
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** DPAPI current-user protection keeps tracker tokens out of plaintext preferences and backups. */
class DesktopSecretStore(private val directory: Path) {
    fun write(key: String, secret: String) {
        require(secret.isNotBlank())
        val destination = pathFor(key)
        requireWindows()
        Files.createDirectories(directory)
        val plain = secret.toByteArray(Charsets.UTF_8)
        val encrypted = try {
            Crypt32Util.cryptProtectData(plain)
        } finally {
            plain.fill(0)
        }
        val temp = Files.createTempFile(directory, "secret-", ".tmp")
        try {
            Files.write(temp, encrypted)
            try {
                Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            encrypted.fill(0)
            Files.deleteIfExists(temp)
        }
    }

    fun read(key: String): String? {
        val path = pathFor(key)
        requireWindows()
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return null
        require(Files.size(path) in 1..65_536) { "Invalid encrypted credential size" }
        val encrypted = Files.readAllBytes(path)
        val plain = try {
            Crypt32Util.cryptUnprotectData(encrypted)
        } finally {
            encrypted.fill(0)
        }
        return try {
            plain.toString(Charsets.UTF_8)
        } finally {
            plain.fill(0)
        }
    }

    fun remove(key: String) {
        Files.deleteIfExists(pathFor(key))
    }

    private fun pathFor(key: String): Path {
        require(key.matches(Regex("[a-z0-9][a-z0-9.-]{0,63}"))) { "Invalid secret key" }
        return directory.resolve("$key.dpapi")
    }

    private fun requireWindows() {
        check(System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            "Desktop credential storage requires Windows DPAPI"
        }
    }
}
