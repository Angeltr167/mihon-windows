package mihon.desktop

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mihon.desktop.data.DesktopMangaRepository
import mihon.platform.desktop.PropertiesKeyValueStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import tachiyomi.source.local.desktop.DesktopLocalSource
import tachiyomi.source.local.desktop.DesktopLocalSourceFileSystem
import java.nio.file.Files

class DesktopLibraryUpdateSchedulerTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `in-app schedule discovers new chapters when due`() = runTest {
        val root = Files.createTempDirectory("mihon-library-update")
        Files.createDirectories(root.resolve("Manga").resolve("Chapter 1"))
        val source = DesktopLocalSource(DesktopLocalSourceFileSystem(root))
        val preferences = PropertiesKeyValueStore(root.resolve("prefs.properties"))
        var now = 1_000_000L
        DesktopMangaRepository.inMemory().use { library ->
            val manga = SManga.create().apply {
                url = "Manga"
                title = "Manga"
            }
            val stored = library.addToLibrary(source.id, manga)
            val scheduler = DesktopLibraryUpdateScheduler(
                library,
                sourceForId = { source },
                preferences = preferences,
                networkAvailable = { true },
                clock = { now },
                scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
            )
            try {
                scheduler.setIntervalHours(6)
                runCurrent()
                assertFalse(scheduler.state.value.running)
                now += 6 * 3_600_000L
                advanceTimeBy(60_000)
                runCurrent()
                assertEquals(1, scheduler.state.value.checkedManga)
                assertEquals(1, scheduler.state.value.newChapters)
                assertNotNull(library.chapter(stored._id, "Manga/Chapter 1"))
                assertEquals(now, preferences.getLong(DesktopLibraryUpdateScheduler.LAST_UPDATE_KEY))
            } finally {
                scheduler.close()
            }
        }
    }
}
