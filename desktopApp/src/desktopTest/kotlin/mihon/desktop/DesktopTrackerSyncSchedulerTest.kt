package mihon.desktop

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class DesktopTrackerSyncSchedulerTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `failed sync is visible and retries after restart`() = runBlocking {
        val queueFile = tempDir.resolve("tracker-sync.properties")
        val attempts = AtomicInteger()
        DesktopTrackerSyncScheduler(
            queueFile,
            DesktopTrackProgressUpdater { _, _ ->
                attempts.incrementAndGet()
                error("Offline")
            },
        ).use { scheduler ->
            scheduler.enqueue(17, 2.0)
            withTimeout(5_000) {
                while (scheduler.pending.value.singleOrNull()?.error != "Offline") delay(10)
            }
            assertEquals(1, attempts.get())
        }
        DesktopTrackerSyncScheduler(
            queueFile,
            DesktopTrackProgressUpdater { mangaId, chapterNumber ->
                assertEquals(17L, mangaId)
                assertEquals(2.0, chapterNumber)
                attempts.incrementAndGet()
            },
        ).use { scheduler ->
            withTimeout(5_000) { while (scheduler.pending.value.isNotEmpty()) delay(10) }
            assertEquals(2, attempts.get())
        }
    }

    @Test
    fun `newer chapter supersedes pending progress for the same manga`() = runBlocking {
        val queueFile = tempDir.resolve("tracker-sync.properties")
        val values = mutableListOf<Double>()
        val attempts = AtomicInteger()
        DesktopTrackerSyncScheduler(
            queueFile,
            DesktopTrackProgressUpdater { _, number ->
                synchronized(values) { values += number }
                if (attempts.incrementAndGet() == 1) error("First attempt failed")
            },
        ).use { scheduler ->
            scheduler.enqueue(8, 1.0)
            withTimeout(5_000) {
                while (scheduler.pending.value.singleOrNull()?.error == null) delay(10)
            }
            scheduler.enqueue(8, 2.0)
            withTimeout(5_000) { while (scheduler.pending.value.isNotEmpty()) delay(10) }
        }
        assertEquals(listOf(1.0, 2.0), values)
        assertTrue(attempts.get() == 2)
    }
}
