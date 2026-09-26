package mihon.backup.shared

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object BackupContainer {
    const val MAX_DECODED_SIZE = 256 * 1024 * 1024

    fun encode(payload: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(payload) }
        return output.toByteArray()
    }

    fun decode(container: ByteArray, maxDecodedSize: Int = MAX_DECODED_SIZE): ByteArray {
        require(maxDecodedSize > 0)
        if (!isGzip(container)) {
            if (container.size > maxDecodedSize) throw IOException("Backup exceeds the size limit")
            return container
        }
        return GZIPInputStream(ByteArrayInputStream(container)).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxDecodedSize) throw IOException("Backup exceeds the size limit")
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }

    fun isGzip(bytes: ByteArray): Boolean {
        return bytes.size >= 2 &&
            bytes[0].toInt() and 0xff == 0x1f &&
            bytes[1].toInt() and 0xff == 0x8b
    }
}
