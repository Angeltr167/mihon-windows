package mihon.desktop

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.backup.shared.BackupContainer
import mihon.backup.shared.BackupImportSummary
import mihon.backup.shared.MihonBackup
import mihon.desktop.data.DesktopMangaRepository
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

object DesktopBackupImporter {
    @OptIn(ExperimentalSerializationApi::class)
    fun import(path: Path, repository: DesktopMangaRepository): BackupImportSummary {
        val size = Files.size(path)
        if (size == 0L || size > BackupContainer.MAX_DECODED_SIZE) {
            throw IOException("Backup is empty or exceeds the 256 MiB limit")
        }
        return importBytes(Files.readAllBytes(path), repository)
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun importBytes(container: ByteArray, repository: DesktopMangaRepository): BackupImportSummary {
        val payload = BackupContainer.decode(container)
        val backup = ProtoBuf.decodeFromByteArray(MihonBackup.serializer(), payload)
        require(backup.manga.isNotEmpty() || backup.categories.isNotEmpty()) {
            "Backup contains no library or category data"
        }
        return repository.importBackup(backup)
    }
}
