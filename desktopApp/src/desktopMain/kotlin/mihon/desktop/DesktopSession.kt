package mihon.desktop

import eu.kanade.tachiyomi.source.Source
import mihon.core.extension.desktop.DesktopExtensionLoadResult
import mihon.core.extension.desktop.DesktopExtensionManager
import mihon.core.extension.desktop.DesktopExtensionRepository
import mihon.desktop.data.DesktopMangaRepository
import mihon.platform.desktop.DesktopPlatformGraph
import tachiyomi.source.local.desktop.DesktopLocalSource
import tachiyomi.source.local.desktop.DesktopLocalSourceFileSystem
import java.io.Closeable
import java.nio.file.Path

/** Desktop composition root for the existing source and database contracts. */
class DesktopSession(graph: DesktopPlatformGraph) : Closeable {
    val library = DesktopMangaRepository.open(Path.of(graph.appDirectories.database).resolve("tachiyomi.db"))
    val extensions = DesktopExtensionManager(graph.appDirectories)
    val extensionRepository = DesktopExtensionRepository(Path.of(graph.appDirectories.temp))
    private val localSource = DesktopLocalSource(
        DesktopLocalSourceFileSystem(Path.of(graph.appDirectories.localLibrary)),
    )

    fun sources(): List<Source> = listOf(localSource) + extensions.loadInstalled()
        .filterIsInstance<DesktopExtensionLoadResult.Loaded>()
        .flatMap(DesktopExtensionLoadResult.Loaded::sources)

    override fun close() = library.close()
}
