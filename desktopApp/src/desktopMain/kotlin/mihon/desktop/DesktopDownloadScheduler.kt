package mihon.desktop

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.Closeable
import java.net.NetworkInterface

/** Runs only while the Desktop app is open. Android's WorkManager scheduler is untouched. */
internal class DesktopDownloadScheduler(
    val store: DesktopDownloadStore,
    private val engine: DesktopChapterTransfer,
    private val sourceForId: (Long) -> Source?,
    private val notify: (DesktopDownload) -> Unit = {},
    private val networkAvailable: () -> Boolean = ::desktopHasNetwork,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : Closeable {
    private val lock = Any()
    private val mutableQueue = MutableStateFlow(
        store.loadQueue().map { item ->
            when {
                store.isComplete(item) -> item.copy(status = DesktopDownloadStatus.COMPLETED, error = null)
                item.status == DesktopDownloadStatus.RUNNING -> item.copy(status = DesktopDownloadStatus.PENDING)
                item.status == DesktopDownloadStatus.COMPLETED ->
                    item.copy(status = DesktopDownloadStatus.PENDING, pagesDone = 0)
                else -> item
            }
        },
    )
    val queue: StateFlow<List<DesktopDownload>> = mutableQueue
    private var worker: Job? = null
    private var activeKey: String? = null
    private var activeTransfer: Job? = null

    init {
        store.saveQueue(mutableQueue.value)
        startWorker()
    }

    fun enqueue(source: Source, manga: SManga, chapter: SChapter) {
        val item = DesktopDownload(source.id, manga.url, manga.title, chapter.url, chapter.name)
        update { items ->
            if (items.any { it.key == item.key }) items else items + item
        }
        startWorker()
    }

    fun pause(key: String? = null) {
        update { items ->
            items.map { item ->
                if ((key == null || item.key == key) && item.status in ACTIVE_STATUSES) {
                    item.copy(status = DesktopDownloadStatus.PAUSED, error = null)
                } else {
                    item
                }
            }
        }
        synchronized(lock) { if (key == null || activeKey == key) activeTransfer?.cancel() }
    }

    fun resume(key: String? = null) {
        update { items ->
            items.map { item ->
                if ((key == null || item.key == key) && item.status in RESUMABLE_STATUSES) {
                    item.copy(status = DesktopDownloadStatus.PENDING, error = null)
                } else {
                    item
                }
            }
        }
        startWorker()
    }

    fun cancel(key: String) {
        val transfer = synchronized(lock) { activeTransfer.takeIf { activeKey == key } }
        update { items -> items.filterNot { it.key == key } }
        transfer?.cancel()
        scope.launch {
            transfer?.join()
            store.clearPartial(key)
        }
    }

    fun clearFinished() {
        update { items -> items.filterNot { it.status == DesktopDownloadStatus.COMPLETED } }
    }

    override fun close() {
        scope.cancel()
    }

    private fun update(transform: (List<DesktopDownload>) -> List<DesktopDownload>) {
        synchronized(lock) {
            val next = transform(mutableQueue.value)
            store.saveQueue(next)
            mutableQueue.value = next
        }
    }

    private fun startWorker() {
        synchronized(lock) {
            if (worker?.isActive == true || mutableQueue.value.none { it.status == DesktopDownloadStatus.PENDING }) {
                return
            }
            worker = scope.launch { processQueue() }
        }
    }

    private suspend fun processQueue() {
        while (true) {
            val item = synchronized(lock) {
                mutableQueue.value.firstOrNull { it.status == DesktopDownloadStatus.PENDING }
            } ?: return
            if (!networkAvailable()) {
                update { items ->
                    items.map { if (it.key == item.key) it.copy(error = "Waiting for a network connection") else it }
                }
                delay(30_000)
                continue
            }
            val source = sourceForId(item.sourceId)
            if (source == null) {
                finish(item.key, DesktopDownloadStatus.FAILED, "Source is not installed")
                continue
            }
            val manga = SManga.create().apply {
                url = item.mangaUrl
                title = item.mangaTitle
            }
            val chapter = SChapter.create().apply {
                url = item.chapterUrl
                name = item.chapterName
            }
            update { items ->
                items.map {
                    if (it.key == item.key) it.copy(status = DesktopDownloadStatus.RUNNING, error = null) else it
                }
            }
            var failure: Throwable? = null
            for (attempt in 1..3) {
                if (synchronized(lock) {
                        mutableQueue.value.none {
                            it.key == item.key &&
                                it.status == DesktopDownloadStatus.RUNNING
                        }
                    }
                ) {
                    break
                }
                val transfer = scope.async {
                    val resolvedChapter = if (source is SuwayomiSource) {
                        source.getMangaUpdate(manga, emptyList(), false, true).chapters
                            .firstOrNull { it.url == chapter.url }
                            ?: error("Chapter is no longer available from this source")
                    } else {
                        chapter
                    }
                    engine.download(item, source, manga, resolvedChapter) { done, total ->
                        update { items ->
                            items.map {
                                if (it.key == item.key && it.status == DesktopDownloadStatus.RUNNING) {
                                    it.copy(pagesDone = done, pageCount = total)
                                } else {
                                    it
                                }
                            }
                        }
                    }
                }
                synchronized(lock) {
                    activeKey = item.key
                    activeTransfer = transfer
                }
                try {
                    transfer.await()
                    failure = null
                    break
                } catch (cancelled: CancellationException) {
                    if (!scope.coroutineContext[Job]!!.isActive) throw cancelled
                    failure = cancelled
                    break
                } catch (error: Exception) {
                    failure = error
                    if (attempt < 3) delay(attempt * 1_000L)
                } finally {
                    synchronized(lock) {
                        if (activeTransfer == transfer) {
                            activeTransfer = null
                            activeKey = null
                        }
                    }
                }
            }
            if (synchronized(lock) {
                    mutableQueue.value.any {
                        it.key == item.key &&
                            it.status == DesktopDownloadStatus.RUNNING
                    }
                }
            ) {
                if (failure == null) {
                    finish(item.key, DesktopDownloadStatus.COMPLETED, null)
                } else {
                    finish(item.key, DesktopDownloadStatus.FAILED, failure.message ?: "Download failed")
                }
            }
        }
    }

    private fun finish(key: String, status: DesktopDownloadStatus, error: String?) {
        var completed: DesktopDownload? = null
        update { items ->
            items.map {
                if (it.key ==
                    key
                ) {
                    it.copy(status = status, error = error).also { updated -> completed = updated }
                } else {
                    it
                }
            }
        }
        completed?.let(notify)
    }

    private companion object {
        val ACTIVE_STATUSES = setOf(DesktopDownloadStatus.PENDING, DesktopDownloadStatus.RUNNING)
        val RESUMABLE_STATUSES = setOf(DesktopDownloadStatus.PAUSED, DesktopDownloadStatus.FAILED)
    }
}

internal fun desktopHasNetwork(): Boolean = runCatching {
    NetworkInterface.getNetworkInterfaces()?.asSequence()?.any { it.isUp && !it.isLoopback } == true
}.getOrDefault(true)
