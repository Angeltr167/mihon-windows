package mihon.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import eu.kanade.tachiyomi.source.Source
import mihon.core.reader.FitMode
import mihon.core.reader.ReadingMode
import tachiyomi.source.local.desktop.DesktopLocalSource
import java.util.Locale

internal val LocalRoninLanguage = staticCompositionLocalOf { Locale.getDefault().language }

@Composable
internal fun roninText(english: String, spanish: String): String =
    roninCopy(english, spanish, LocalRoninLanguage.current)

internal fun roninCopy(english: String, spanish: String, languageTag: String): String =
    if (languageTag.substringBefore('-').substringBefore('_').equals("es", ignoreCase = true)) spanish else english

internal fun localizedSourceName(source: Source, languageTag: String): String =
    if (source is DesktopLocalSource || source.lang == "localsourcelang") {
        roninCopy("Local source", "Fuente local", languageTag)
    } else {
        source.name
    }

internal fun formatChapterNumber(number: Double): String =
    if (number.isFinite()) {
        java.math.BigDecimal.valueOf(number)
            .setScale(4, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    } else {
        "—"
    }

@Composable
internal fun localizedReadingModeLabel(mode: ReadingMode): String = roninText(
    readingModeLabel(mode),
    when (mode) {
        ReadingMode.SINGLE_LTR -> "Una página · izquierda a derecha"
        ReadingMode.SINGLE_RTL -> "Una página · derecha a izquierda"
        ReadingMode.DOUBLE_LTR -> "Doble página · izquierda a derecha"
        ReadingMode.DOUBLE_RTL -> "Doble página · derecha a izquierda"
        ReadingMode.VERTICAL -> "Vertical"
        ReadingMode.WEBTOON -> "Webtoon"
    },
)

@Composable
internal fun localizedFitModeLabel(fit: FitMode): String = roninText(
    fitModeLabel(fit),
    when (fit) {
        FitMode.WIDTH -> "Ajustar al ancho"
        FitMode.HEIGHT -> "Ajustar al alto"
        FitMode.ORIGINAL -> "Tamaño original"
    },
)

@Composable
internal fun roninUiText(label: String): String = localizeRoninLabel(label, LocalRoninLanguage.current)

internal fun localizeRoninLabel(label: String, languageTag: String): String {
    val spanish = spanishUiLabels[label] ?: when {
        label.matches(Regex("\\d+ extensions available")) ->
            "${label.substringBefore(' ')} extensiones disponibles"
        label.endsWith(" changed") -> "${label.removeSuffix(" changed")} actualizado"
        else -> label
    }
    return roninCopy(label, spanish, languageTag)
}

private val spanishUiLabels = mapOf(
    "Yesterday" to "Ayer",
    "In progress" to "En curso",
    "Engine ready" to "Motor listo",
    "Engine offline" to "Motor desconectado",
    "Working…" to "Procesando…",
    "Starting local extension engine…" to "Iniciando motor local de extensiones…",
    "Local extension engine ready" to "Motor local de extensiones listo",
    "Local extension engine failed" to "Falló el motor local de extensiones",
    "Could not refresh extensions" to "No se pudieron actualizar las extensiones",
    "Extension action failed" to "Falló la operación de la extensión",
    "All" to "Todos",
    "Today" to "Hoy",
    "This week" to "Esta semana",
    "Earlier" to "Anteriores",
    "Date unavailable" to "Fecha no disponible",
    "Recently added" to "Añadidos recientemente",
    "Recently updated" to "Actualizados recientemente",
    "Grid" to "Cuadrícula",
    "List" to "Lista",
    "Not started" to "Sin empezar",
    "NEW" to "NUEVO",
    "DONE" to "TERMINADO",
    "READ" to "LEER",
    "Last chapter finished" to "Último capítulo terminado",
    "Installing…" to "Instalando…",
    "Updating…" to "Actualizando…",
    "Removing…" to "Eliminando…",
    "Trusting…" to "Registrando confianza…",
)
