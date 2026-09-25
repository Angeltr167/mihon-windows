package mihon.platform.desktop

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

/** Per-user registration for the Mihon OAuth and repository URI scheme. */
class WindowsProtocolRegistrar(
    private val launcherPath: Path = Path.of(ProcessHandle.current().info().command().orElse("")),
) {
    fun registerMihonProtocol() {
        check(isWindows) { "Mihon link registration is available on Windows only" }
        val command = launcherCommand(launcherPath)
        Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, PROTOCOL_KEY)
        Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, PROTOCOL_KEY, "", "URL:Mihon link")
        Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, PROTOCOL_KEY, "URL Protocol", "")
        Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, OPEN_COMMAND_KEY)
        Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, OPEN_COMMAND_KEY, "", command)
    }

    fun unregisterMihonProtocol() {
        check(isWindows) { "Mihon link registration is available on Windows only" }
        if (!isRegistered()) return
        Advapi32Util.registryDeleteKey(WinReg.HKEY_CURRENT_USER, OPEN_COMMAND_KEY)
        Advapi32Util.registryDeleteKey(WinReg.HKEY_CURRENT_USER, OPEN_KEY)
        Advapi32Util.registryDeleteKey(WinReg.HKEY_CURRENT_USER, SHELL_KEY)
        Advapi32Util.registryDeleteKey(WinReg.HKEY_CURRENT_USER, PROTOCOL_KEY)
    }

    fun isRegistered(): Boolean {
        if (!isWindows || !Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, OPEN_COMMAND_KEY)) return false
        return runCatching {
            Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, OPEN_COMMAND_KEY, "") ==
                launcherCommand(launcherPath)
        }.getOrDefault(false)
    }

    fun launcherCommand(): String = launcherCommand(launcherPath)

    private fun launcherCommand(path: Path): String {
        val absolute = path.toAbsolutePath().normalize()
        require(Files.isRegularFile(absolute)) { "Run Mihon from its installed Windows launcher to register links" }
        val fileName = absolute.fileName.toString().lowercase(Locale.ROOT)
        require(fileName.endsWith(".exe") && fileName !in setOf("java.exe", "javaw.exe")) {
            "Run Mihon from its installed Windows launcher to register links"
        }
        val pathText = absolute.toString()
        require('"' !in pathText && pathText.none(Char::isISOControl)) { "Invalid Mihon launcher path" }
        return "\"$pathText\" \"%1\""
    }

    private val isWindows: Boolean get() = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

    private companion object {
        const val PROTOCOL_KEY = "Software\\Classes\\mihon"
        const val SHELL_KEY = "$PROTOCOL_KEY\\shell"
        const val OPEN_KEY = "$SHELL_KEY\\open"
        const val OPEN_COMMAND_KEY = "$OPEN_KEY\\command"
    }
}
