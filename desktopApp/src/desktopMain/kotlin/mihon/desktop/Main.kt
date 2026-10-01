package mihon.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.zacsweers.metro.createGraphFactory
import mihon.desktop.data.DesktopDatabaseDriver
import mihon.platform.desktop.DesktopPlatformGraph
import tachiyomi.domain.manga.model.Manga
import java.awt.Dimension
import java.nio.file.Path

fun main(args: Array<String>) {
    if (args.firstOrNull() == SUWAYOMI_CHILD_ARGUMENT) {
        runSuwayomiChild(args)
        return
    }
    val graph = createGraphFactory<DesktopPlatformGraph.Factory>().create()

    if (System.getenv(DESKTOP_SMOKE_ENV) == "1") {
        graph.keyValueStore.putString(SMOKE_PREFERENCE_KEY, "ok")
        check(graph.keyValueStore.getString(SMOKE_PREFERENCE_KEY) == "ok")
        graph.keyValueStore.remove(SMOKE_PREFERENCE_KEY)
        check(graph.appDirectories.cache.isNotBlank())
        check(graph.localeService.currentLanguageTag().isNotBlank())

        val databasePath = Path.of(graph.appDirectories.database).resolve(DATABASE_FILE_NAME)
        DesktopDatabaseDriver.open(databasePath).use { }
        DesktopSession(graph).use { session ->
            check(session.sources().any { it.name == "Local source" })
            check(session.sources() === session.sources())
            session.library.library()
        }
        check(Manga.create().id == -1L)

        println("MIHON_DESKTOP_PLATFORM_GRAPH_OK")
        println("MIHON_DESKTOP_DATABASE_OK")
        println("MIHON_DESKTOP_SESSION_OK")
        return
    }

    DesktopSingleInstance(Path.of(graph.appDirectories.config)).use { instance ->
        if (!instance.startOrForward(args)) return
        application {
            val rememberSize = graph.keyValueStore.getBoolean(DesktopPreferences.REMEMBER_SIZE, true)
            val state = rememberWindowState(
                width = (if (rememberSize) graph.keyValueStore.getLong("desktop.window.width", 1100) else 1100)
                    .coerceIn(800, 3840).toInt().dp,
                height = (if (rememberSize) graph.keyValueStore.getLong("desktop.window.height", 750) else 750)
                    .coerceIn(560, 2160).toInt().dp,
                placement = if (graph.keyValueStore.getBoolean(DesktopPreferences.MAXIMIZED, true)) {
                    WindowPlacement.Maximized
                } else {
                    WindowPlacement.Floating
                },
            )
            Window(
                onCloseRequest = {
                    if (graph.keyValueStore.getBoolean(DesktopPreferences.REMEMBER_SIZE, true)) {
                        graph.keyValueStore.putLong("desktop.window.width", state.size.width.value.toLong())
                        graph.keyValueStore.putLong("desktop.window.height", state.size.height.value.toLong())
                    }
                    exitApplication()
                },
                title = "Ronin",
                state = state,
            ) {
                window.minimumSize = Dimension(800, 560)
                DisposableEffect(window) {
                    configureRoninWindow(window)
                    onDispose { }
                }
                DesktopShell(
                    graph,
                    initialLink = args.singleOrNull(),
                    incomingLinks = instance.incomingLink,
                    onToggleFullscreen = {
                        state.placement = if (state.placement == WindowPlacement.Fullscreen) {
                            WindowPlacement.Maximized
                        } else {
                            WindowPlacement.Fullscreen
                        }
                    },
                )
            }
        }
    }
}

private const val DESKTOP_SMOKE_ENV = "MIHON_DESKTOP_SMOKE_TEST"
private const val SMOKE_PREFERENCE_KEY = "__platform_smoke_test"
private const val DATABASE_FILE_NAME = "tachiyomi.db"
