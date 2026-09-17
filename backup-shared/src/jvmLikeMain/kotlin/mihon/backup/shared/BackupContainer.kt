package mihon.backup.shared

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object BackupContainer {
    fun encode(payload: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(payload) }
        return output.toByteArray()
    }

    fun decode(container: ByteArray): ByteArray {
        if (!isGzip(container)) return container
        return GZIPInputStream(ByteArrayInputStream(container)).use { it.readBytes() }
    }

    fun isGzip(bytes: ByteArray): Boolean {
        return bytes.size >= 2 &&
            bytes[0].toInt() and 0xff == 0x1f &&
            bytes[1].toInt() and 0xff == 0x8b
    }
}
