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
import tachiyomi.i18n.MR

fun main() {
    if (System.getenv(DESKTOP_SMOKE_ENV) == "1") {
        println("MIHON_DESKTOP_BOOTSTRAP_OK")
        return
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Mihon",
        ) {
            MihonDesktopBootstrap()
        }
    }
}

@Composable
private fun MihonDesktopBootstrap() {
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
                Text("Windows desktop bootstrap")
            }
        }
    }
}

private const val DESKTOP_SMOKE_ENV = "MIHON_DESKTOP_SMOKE_TEST"
