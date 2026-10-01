package mihon.desktop

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.backup.shared.BackupContainer
import mihon.backup.shared.BackupDesktopPreference
import mihon.backup.shared.MihonBackup
import mihon.desktop.data.DesktopMangaRepository
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

object DesktopBackupExporter {
    /** Publish only a fully written backup; each creation uses a new name. */
    @OptIn(ExperimentalSerializationApi::class)
    fun export(
        directory: Path,
        repository: DesktopMangaRepository,
        preferences: List<BackupDesktopPreference> = emptyList(),
    ): Path {
        val folder = directory.toAbsolutePath().normalize()
        Files.createDirectories(folder)
        require(Files.isDirectory(folder)) { "Backup destination must be a directory" }
        val backup = repository.exportBackup().copy(desktopPreferences = preferences)
        val payload = ProtoBuf.encodeToByteArray(MihonBackup.serializer(), backup)
        require(payload.size <= BackupContainer.MAX_DECODED_SIZE) { "Backup exceeds the 256 MiB limit" }
        val encoded = BackupContainer.encode(payload)
        require(encoded.size <= BackupContainer.MAX_DECODED_SIZE) { "Backup exceeds the 256 MiB limit" }
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        val output = folder.resolve("Ronin_${stamp}_${UUID.randomUUID()}.tachibk")
        val temporary = Files.createTempFile(folder, ".ronin-backup-", ".tmp")
        try {
            Files.write(temporary, encoded)
            try {
                Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, output)
            }
            return output
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
