package mihon.desktop

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mihon.desktop.data.DesktopMangaRepository
import mihon.platform.api.KeyValueStore
import java.io.Closeable

internal data class DesktopLibraryUpdateState(
    val running: Boolean = false,
    val checkedManga: Int = 0,
    val newChapters: Int = 0,
    val failures: Int = 0,
    val message: String = "",
)

/** Scheduled checks run inside the open app only; closing Mihon stops the coroutine. */
internal class DesktopLibraryUpdateScheduler(
    private val library: DesktopMangaRepository,
    private val sourceForId: (Long) -> Source?,
    private val preferences: KeyValueStore,
    private val networkAvailable: () -> Boolean = ::desktopHasNetwork,
    private val clock: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : Closeable {
    private val mutableState = MutableStateFlow(DesktopLibraryUpdateState())
    val state: StateFlow<DesktopLibraryUpdateState> = mutableState
    private val lock = Any()
    private var updating = false

    init {
        scope.launch {
            while (isActive) {
                val hours = intervalHours()
                val dueAt = preferences.getLong(LAST_UPDATE_KEY) + hours * 3_600_000L
                if (hours > 0 && clock() >= dueAt && networkAvailable()) updateNow()
                delay(60_000)
            }
        }
    }

    fun intervalHours(): Long = preferences.getLong(INTERVAL_KEY, 0).takeIf { it in INTERVALS } ?: 0

    fun setIntervalHours(hours: Long) {
        require(hours in INTERVALS)
        val previous = intervalHours()
        preferences.putLong(INTERVAL_KEY, hours)
        if (hours > 0 && previous == 0L) {
            preferences.putLong(LAST_UPDATE_KEY, clock())
        }
    }

    fun updateNow() {
        synchronized(lock) {
            if (updating) return
            updating = true
        }
        scope.launch {
            var checked = 0
            var added = 0
            var failures = 0
            mutableState.value = DesktopLibraryUpdateState(running = true, message = "Checking library…")
            try {
                if (!networkAvailable()) {
                    mutableState.value = DesktopLibraryUpdateState(message = "Waiting for a network connection")
                    return@launch
                }
                for (stored in library.library()) {
                    val source = sourceForId(stored.source)
                    if (source == null) {
                        failures++
                        continue
                    }
                    val manga = SManga.create().apply {
                        url = stored.url
                        title = stored.title
                    }
                    try {
                        val update = source.getMangaUpdate(manga, emptyList(), true, true)
                        added += update.chapters.count { library.chapter(stored._id, it.url) == null }
                        library.syncChapters(stored._id, update.chapters)
                        checked++
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        failures++
                    }
                    mutableState.value = DesktopLibraryUpdateState(true, checked, added, failures, "Checking library…")
                }
                preferences.putLong(LAST_UPDATE_KEY, clock())
                mutableState.value = DesktopLibraryUpdateState(
                    checkedManga = checked,
                    newChapters = added,
                    failures = failures,
                    message = "Checked $checked manga; $added new chapters; $failures failures",
                )
            } finally {
                synchronized(lock) { updating = false }
            }
        }
    }

    override fun close() {
        scope.cancel()
    }

    internal companion object {
        const val INTERVAL_KEY = "desktop.library.updateIntervalHours"
        const val LAST_UPDATE_KEY = "desktop.library.lastUpdateAt"
        val INTERVALS = listOf(0L, 6L, 12L, 24L)
    }
}
