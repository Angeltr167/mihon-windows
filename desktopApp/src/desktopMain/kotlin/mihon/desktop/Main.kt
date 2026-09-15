package mihon.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.icerock.moko.resources.compose.stringResource
import dev.zacsweers.metro.createGraphFactory
import mihon.desktop.data.DesktopDatabaseDriver
import mihon.platform.desktop.DesktopPlatformGraph
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import java.nio.file.Path

fun main() {
    val graph = createGraphFactory<DesktopPlatformGraph.Factory>().create()

    if (System.getenv(DESKTOP_SMOKE_ENV) == "1") {
        graph.keyValueStore.putString(SMOKE_PREFERENCE_KEY, "ok")
        check(graph.keyValueStore.getString(SMOKE_PREFERENCE_KEY) == "ok")
        graph.keyValueStore.remove(SMOKE_PREFERENCE_KEY)
        check(graph.appDirectories.cache.isNotBlank())
        check(graph.localeService.currentLanguageTag().isNotBlank())

        val databasePath = Path.of(graph.appDirectories.database).resolve(DATABASE_FILE_NAME)
        DesktopDatabaseDriver.open(databasePath).use { }
        check(Manga.create().id == -1L)

        println("MIHON_DESKTOP_PLATFORM_GRAPH_OK")
        println("MIHON_DESKTOP_DATABASE_OK")
        return
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Mihon",
        ) {
            MihonDesktopBootstrap(graph)
        }
    }
}

@Composable
private fun MihonDesktopBootstrap(graph: DesktopPlatformGraph) {
    val metadata = graph.appMetadataService.current()
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(
                    space = 12.dp,
                    alignment = Alignment.CenterVertically,
                ),
            ) {
                Text(
                    text = stringResource(MR.strings.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text("${metadata.platform} · ${metadata.versionName}")
            }
        }
    }
}

private const val DESKTOP_SMOKE_ENV = "MIHON_DESKTOP_SMOKE_TEST"
private const val SMOKE_PREFERENCE_KEY = "__platform_smoke_test"
private const val DATABASE_FILE_NAME = "tachiyomi.db"
