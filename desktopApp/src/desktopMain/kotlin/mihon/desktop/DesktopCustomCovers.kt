package mihon.desktop

import androidx.compose.runtime.staticCompositionLocalOf
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.imageio.ImageIO

internal val LocalRoninCovers = staticCompositionLocalOf<DesktopCustomCovers?> { null }

/** Keeps user artwork separate from source metadata and downloaded books. */
internal class DesktopCustomCovers(private val root: Path) {
    fun cover(sourceId: Long, mangaUrl: String): String? =
        path(sourceId, mangaUrl).takeIf(Files::isRegularFile)?.toUri()?.toString()

    fun useFit(sourceId: Long, mangaUrl: String): Boolean = Files.exists(fitPath(sourceId, mangaUrl))

    fun setFit(sourceId: Long, mangaUrl: String, fit: Boolean) {
        Files.createDirectories(root)
        if (fit) {
            Files.writeString(fitPath(sourceId, mangaUrl), "fit")
        } else {
            Files.deleteIfExists(fitPath(sourceId, mangaUrl))
        }
    }

    fun importCover(sourceId: Long, mangaUrl: String, input: Path) {
        require(Files.size(input) in 1..16L * 1024 * 1024) { "Choose an image smaller than 16 MB" }
        val image = ImageIO.createImageInputStream(input.toFile()).use { stream ->
            requireNotNull(stream) { "Cannot open image" }
            val readers = ImageIO.getImageReaders(stream)
            require(readers.hasNext()) { "Choose a PNG or JPEG image" }
            val reader = readers.next()
            try {
                reader.input = stream
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                require(width > 0 && height > 0 && width.toLong() * height <= 32_000_000) {
                    "Image dimensions are too large"
                }
                reader.read(0)
            } finally {
                reader.dispose()
            }
        }
        val ratio = minOf(1.0, 1920.0 / maxOf(image.width, image.height))
        val normalized = BufferedImage(
            maxOf(1, (image.width * ratio).toInt()),
            maxOf(1, (image.height * ratio).toInt()),
            BufferedImage.TYPE_INT_ARGB,
        )
        normalized.createGraphics().let { graphics ->
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                graphics.drawImage(image, 0, 0, normalized.width, normalized.height, null)
            } finally {
                graphics.dispose()
            }
        }
        Files.createDirectories(root)
        val temporary = Files.createTempFile(root, "cover-", ".tmp")
        try {
            check(ImageIO.write(normalized, "png", temporary.toFile()))
            Files.move(temporary, path(sourceId, mangaUrl), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    fun remove(sourceId: Long, mangaUrl: String) {
        Files.deleteIfExists(path(sourceId, mangaUrl))
        Files.deleteIfExists(fitPath(sourceId, mangaUrl))
    }

    private fun key(sourceId: Long, mangaUrl: String): String = MessageDigest.getInstance("SHA-256")
        .digest("$sourceId\n$mangaUrl".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun path(sourceId: Long, mangaUrl: String) = root.resolve("${key(sourceId, mangaUrl)}.png")
    private fun fitPath(sourceId: Long, mangaUrl: String) = root.resolve("${key(sourceId, mangaUrl)}.fit")
}

internal val LocalRoninCoverRevision = staticCompositionLocalOf { 0 }
