package mihon.desktop

import java.awt.Color
import java.awt.Font
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.io.Closeable

/** Optional Windows tray notifications; the queue screen remains the fallback when no tray is available. */
internal class DesktopDownloadNotifications : Closeable {
    private val tray = if (SystemTray.isSupported()) runCatching { SystemTray.getSystemTray() }.getOrNull() else null
    private val icon = tray?.let { systemTray ->
        runCatching {
            val image = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
            val graphics = image.createGraphics()
            try {
                graphics.color = Color(99, 77, 165)
                graphics.fillRoundRect(0, 0, 32, 32, 8, 8)
                graphics.color = Color.WHITE
                graphics.font = Font(Font.SANS_SERIF, Font.BOLD, 23)
                graphics.drawString("M", 5, 25)
            } finally {
                graphics.dispose()
            }
            TrayIcon(image, "Mihon downloads").also {
                it.isImageAutoSize = true
                systemTray.add(it)
            }
        }.getOrNull()
    }

    fun show(item: DesktopDownload) {
        if (item.status !in setOf(DesktopDownloadStatus.COMPLETED, DesktopDownloadStatus.FAILED)) return
        icon?.displayMessage(
            "Mihon download",
            if (item.status == DesktopDownloadStatus.COMPLETED) {
                "${item.mangaTitle} — ${item.chapterName} downloaded"
            } else {
                "${item.mangaTitle} — ${item.chapterName}: ${item.error ?: "failed"}"
            },
            if (item.status ==
                DesktopDownloadStatus.COMPLETED
            ) {
                TrayIcon.MessageType.INFO
            } else {
                TrayIcon.MessageType.ERROR
            },
        )
    }

    override fun close() {
        if (tray != null && icon != null) tray.remove(icon)
    }
}
