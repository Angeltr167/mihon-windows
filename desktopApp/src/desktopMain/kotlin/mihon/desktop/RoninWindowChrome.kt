package mihon.desktop

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.awt.Frame
import java.awt.Window
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

private interface DwmApi : StdCallLibrary {
    @Suppress("FunctionName")
    fun DwmSetWindowAttribute(window: Pointer, attribute: Int, value: IntByReference, valueSize: Int): Int
}

internal fun configureRoninWindow(window: Window) {
    runCatching {
        requireNotNull(RoninWindowChromeMarker::class.java.getResourceAsStream("/ronin/ronin-mark.png"))
            .use { (window as? Frame)?.iconImage = ImageIO.read(it) }
    }
    if (!System.getProperty("os.name").startsWith("Windows")) return
    SwingUtilities.invokeLater {
        runCatching {
            val dwm = Native.load("dwmapi", DwmApi::class.java)
            val enabled = IntByReference(1)
            val handle = Native.getWindowPointer(window)
            if (dwm.DwmSetWindowAttribute(handle, 20, enabled, Int.SIZE_BYTES) != 0) {
                dwm.DwmSetWindowAttribute(handle, 19, enabled, Int.SIZE_BYTES)
            }
        }
    }
}

private object RoninWindowChromeMarker
