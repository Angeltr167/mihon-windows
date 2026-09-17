package mihon.core.archive.api

import java.io.Closeable
import java.io.InputStream

interface ArchiveReader : Closeable {
    fun entries(): List<ArchiveEntry>

    fun openEntry(entryName: String): InputStream?
}
