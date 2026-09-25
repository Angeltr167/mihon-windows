package mihon.desktop

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.Closeable
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties

internal fun interface DesktopTrackProgressUpdater {
    suspend fun syncCompletedChapter(mangaId: Long, chapterNumber: Double)
}

internal data class PendingTrackSync(val mangaId: Long, val chapterNumber: Double, val error: String? = null)

/** In-app worker; unfinished chapter-completion syncs retry on the next launch. */
internal class DesktopTrackerSyncScheduler(
    private val file: Path,
    private val updater: DesktopTrackProgressUpdater,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : Closeable {
    private val lock = Any()
    private val mutablePending = MutableStateFlow(load().map { it.copy(error = null) })
    val pending: StateFlow<List<PendingTrackSync>> = mutablePending
    private var worker: Job? = null

    init {
        save(mutablePending.value)
        startWorker()
    }

    fun enqueue(mangaId: Long, chapterNumber: Double) {
        require(mangaId > 0 && chapterNumber.isFinite() && chapterNumber > 0)
        update { items ->
            val existing = items.firstOrNull { it.mangaId == mangaId }
            items.filterNot { it.mangaId == mangaId } + PendingTrackSync(
                mangaId,
                maxOf(chapterNumber, existing?.chapterNumber ?: 0.0),
            )
        }
        startWorker()
    }

    fun retry(mangaId: Long) {
        update { items -> items.map { if (it.mangaId == mangaId) it.copy(error = null) else it } }
        startWorker()
    }

    override fun close() = scope.cancel()

    private fun startWorker() {
        synchronized(lock) {
            if (scope.coroutineContext[Job]?.isActive != true) return
            if (worker?.isActive == true || mutablePending.value.none { it.error == null }) return
            worker = scope.launch { process() }.also { job -> job.invokeOnCompletion { startWorker() } }
        }
    }

    private suspend fun process() {
        while (true) {
            val item = synchronized(lock) { mutablePending.value.firstOrNull { it.error == null } } ?: return
            try {
                updater.syncCompletedChapter(item.mangaId, item.chapterNumber)
                update { items ->
                    items.mapNotNull {
                        when {
                            it.mangaId != item.mangaId -> it
                            it.chapterNumber > item.chapterNumber -> it
                            else -> null
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                update { items ->
                    items.map {
                        if (it.mangaId == item.mangaId && it.chapterNumber == item.chapterNumber) {
                            it.copy(error = error.message ?: "Tracker sync failed")
                        } else {
                            it
                        }
                    }
                }
            }
        }
    }

    private fun update(transform: (List<PendingTrackSync>) -> List<PendingTrackSync>) {
        synchronized(lock) {
            val next = transform(mutablePending.value)
            save(next)
            mutablePending.value = next
        }
    }

    private fun load(): List<PendingTrackSync> {
        if (!Files.isRegularFile(file)) return emptyList()
        return runCatching {
            val properties = Properties().apply { Files.newInputStream(file).use(::load) }
            val count = properties.getProperty("count")?.toIntOrNull()?.takeIf { it in 0..100_000 }
                ?: error("Invalid tracker sync queue")
            (0 until count).map { index ->
                val mangaId = properties.getProperty("$index.mangaId")?.toLongOrNull()?.takeIf { it > 0 }
                    ?: error("Invalid manga ID")
                val number = properties.getProperty("$index.chapterNumber")?.toDoubleOrNull()
                    ?.takeIf { it.isFinite() && it > 0 } ?: error("Invalid chapter number")
                PendingTrackSync(mangaId, number, properties.getProperty("$index.error"))
            }.distinctBy(PendingTrackSync::mangaId)
        }.getOrElse {
            Files.move(file, file.resolveSibling("${file.fileName}.corrupt-${System.currentTimeMillis()}"))
            emptyList()
        }
    }

    private fun save(items: List<PendingTrackSync>) {
        Files.createDirectories(file.parent)
        val properties = Properties().apply {
            setProperty("count", items.size.toString())
            items.forEachIndexed { index, item ->
                setProperty("$index.mangaId", item.mangaId.toString())
                setProperty("$index.chapterNumber", item.chapterNumber.toString())
                item.error?.let { setProperty("$index.error", it) }
            }
        }
        val temp = Files.createTempFile(file.parent, "tracker-sync-", ".tmp")
        try {
            Files.newOutputStream(temp).use { properties.store(it, "Mihon Desktop tracker sync") }
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }
}
