package mihon.desktop

import mihon.backup.shared.BackupManga
import mihon.backup.shared.MihonBackup
import mihon.desktop.data.DesktopMangaRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesktopLibraryScaleTest {
    @Test
    fun `library query returns ten thousand migrated favorites within five seconds`() {
        DesktopMangaRepository.inMemory().use { repository ->
            repository.importBackup(
                MihonBackup(
                    manga = (0 until 10_000).map { index ->
                        BackupManga(
                            source = 42,
                            url = "/scale/$index",
                            title = "Scale fixture $index",
                            favorite = true,
                        )
                    },
                ),
            )

            val started = System.nanoTime()
            val count = repository.library().size
            val elapsedMillis = (System.nanoTime() - started) / 1_000_000

            assertEquals(10_000, count)
            assertTrue(elapsedMillis < 5_000, "10,000-item library query took ${elapsedMillis}ms")
        }
    }
}
