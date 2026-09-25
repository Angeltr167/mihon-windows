package mihon.desktop

import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import mihon.core.extension.desktop.DesktopExtensionLoadResult
import mihon.core.extension.desktop.DesktopExtensionManager
import mihon.core.extension.desktop.DesktopExtensionRepository
import mihon.core.network.desktop.DesktopNetworkHelper
import mihon.desktop.data.DesktopMangaRepository
import mihon.platform.desktop.DesktopPlatformGraph
import mihon.platform.desktop.DesktopSecretStore
import tachiyomi.source.local.desktop.DesktopLocalChapterPages
import tachiyomi.source.local.desktop.DesktopLocalSource
import tachiyomi.source.local.desktop.DesktopLocalSourceFileSystem
import java.io.Closeable
import java.nio.file.Path

/** Desktop composition root for the existing source and database contracts. */
class DesktopSession(graph: DesktopPlatformGraph) : Closeable {
    private val userAgentProvider = { "Mihon Windows/${graph.appMetadataService.current().versionName}" }
    private val network = DesktopNetworkHelper(graph.appDirectories, userAgentProvider)

    init {
        NetworkHelper.install(network.client, userAgentProvider)
    }

    val library = DesktopMangaRepository.open(Path.of(graph.appDirectories.database).resolve("tachiyomi.db"))
    val extensions = DesktopExtensionManager(graph.appDirectories)
    private val localFileSystem = DesktopLocalSourceFileSystem(Path.of(graph.appDirectories.localLibrary))
    private val localSource = DesktopLocalSource(localFileSystem)
    val localPages = DesktopLocalChapterPages(localFileSystem)
    private var loadedSources: List<Source>? = null
    private val secrets = DesktopSecretStore(Path.of(graph.appDirectories.data).resolve("secrets"))
    internal val komgaTracker = DesktopKomgaTracker(network.client, library)
    internal val aniListTracker = DesktopAniListTracker(
        network.client,
        library,
        secrets,
    )
    internal val kavitaTracker = DesktopKavitaTracker(network.client, library, secrets)
    internal val suwayomiTracker = DesktopSuwayomiTracker(library) { sourceId ->
        sources().filterIsInstance<HttpSource>()
            .firstOrNull { it.id == sourceId && it.javaClass.name == DesktopSuwayomiTracker.SOURCE_CLASS }
            ?.let { DesktopTrackerSourceSession(it.baseUrl, it.client, it.headers) }
    }
    internal val trackerSync = DesktopTrackerSyncScheduler(
        Path.of(graph.appDirectories.config).resolve("tracker-sync.properties"),
        DesktopTrackProgressUpdater { mangaId, chapterNumber ->
            komgaTracker.syncCompletedChapter(mangaId, chapterNumber)
            aniListTracker.syncCompletedChapter(mangaId, chapterNumber)
            kavitaTracker.syncCompletedChapter(mangaId, chapterNumber)
            suwayomiTracker.syncCompletedChapter(mangaId, chapterNumber)
        },
    )
    val extensionRepository = DesktopExtensionRepository(Path.of(graph.appDirectories.temp))
    private val downloadNotifications = DesktopDownloadNotifications()
    private val downloadStore = DesktopDownloadStore(Path.of(graph.appDirectories.downloads))
    internal val downloads = DesktopDownloadScheduler(
        downloadStore,
        DesktopDownloadEngine(downloadStore, DesktopPageFetcher(localPages)),
        sourceForId = { id -> sources().firstOrNull { it.id == id } },
        notify = downloadNotifications::show,
    )
    internal val libraryUpdates = DesktopLibraryUpdateScheduler(
        library,
        sourceForId = { id -> sources().firstOrNull { it.id == id } },
        preferences = graph.keyValueStore,
    )

    @Synchronized
    fun sources(): List<Source> = loadedSources ?: refreshSources()

    @Synchronized
    fun refreshSources(): List<Source> = (
        listOf(localSource) + extensions.loadInstalled()
            .filterIsInstance<DesktopExtensionLoadResult.Loaded>()
            .flatMap(DesktopExtensionLoadResult.Loaded::sources)
        ).also { loadedSources = it }

    override fun close() {
        trackerSync.close()
        downloads.close()
        libraryUpdates.close()
        downloadNotifications.close()
        library.close()
    }
}
