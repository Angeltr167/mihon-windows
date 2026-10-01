package mihon.desktop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Color
import java.awt.image.BufferedImage
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

class DesktopCustomCoversTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `cover survives restart and removal of selected file without leaking to other manga`() {
        val input = image("input.png", Color.RED)
        val store = DesktopCustomCovers(root.resolve("covers"))
        store.importCover(1, "/manga", input)
        store.setFit(1, "/manga", true)
        Files.delete(input)
        val reopened = DesktopCustomCovers(root.resolve("covers"))
        val saved = Path.of(URI(requireNotNull(reopened.cover(1, "/manga"))))
        assertTrue(Files.exists(saved))
        assertEquals(Color.RED.rgb, ImageIO.read(saved.toFile()).getRGB(0, 0))
        assertTrue(reopened.useFit(1, "/manga"))
        assertNull(reopened.cover(2, "/manga"))
        assertNull(reopened.cover(1, "/other"))
        reopened.remove(1, "/manga")
        assertNull(reopened.cover(1, "/manga"))
        assertFalse(reopened.useFit(1, "/manga"))
    }

    @Test
    fun `invalid replacement preserves previous artwork and does not touch original files`() {
        val input = image("original.png", Color.BLUE)
        val invalid = Files.writeString(root.resolve("invalid.png"), "not an image")
        val store = DesktopCustomCovers(root.resolve("covers"))
        store.importCover(3, "../../outside", input)
        val saved = Path.of(URI(requireNotNull(store.cover(3, "../../outside"))))
        assertThrows(IllegalArgumentException::class.java) { store.importCover(3, "../../outside", invalid) }
        assertEquals(Color.BLUE.rgb, ImageIO.read(saved.toFile()).getRGB(0, 0))
        store.remove(3, "../../outside")
        assertTrue(Files.exists(input))
        assertTrue(Files.exists(invalid))
    }

    @Test
    fun `replacement updates image and normalizes oversized artwork`() {
        val store = DesktopCustomCovers(root.resolve("covers"))
        store.importCover(1, "test", image("first.png", Color.RED))
        store.importCover(1, "test", image("second.png", Color.GREEN, 2400, 1200))
        val saved = ImageIO.read(Path.of(URI(requireNotNull(store.cover(1, "test")))).toFile())
        assertEquals(1920, saved.width)
        assertEquals(960, saved.height)
        assertEquals(Color.GREEN.rgb, saved.getRGB(0, 0))
    }

    private fun image(name: String, color: Color, width: Int = 20, height: Int = 30): Path {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().let {
            try {
                it.color = color
                it.fillRect(0, 0, width, height)
            } finally {
                it.dispose()
            }
        }
        return root.resolve(name).also { ImageIO.write(image, "png", it.toFile()) }
    }
}
