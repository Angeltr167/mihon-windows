package mihon.backup.shared

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupContainerJvmTest {

    @Test
    fun `gzip round trip preserves protobuf payload bytes`() {
        val payload = byteArrayOf(8, 1, 18, 3, 77, 105, 104)
        val encoded = BackupContainer.encode(payload)

        assertTrue(BackupContainer.isGzip(encoded))
        assertArrayEquals(payload, BackupContainer.decode(encoded))
    }

    @Test
    fun `legacy uncompressed payload passes through unchanged`() {
        val payload = byteArrayOf(8, 1, 18, 0)

        assertFalse(BackupContainer.isGzip(payload))
        assertArrayEquals(payload, BackupContainer.decode(payload))
    }
}
