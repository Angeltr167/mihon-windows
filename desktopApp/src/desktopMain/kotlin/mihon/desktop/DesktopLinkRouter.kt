package mihon.desktop

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ResolvableSource
import eu.kanade.tachiyomi.source.online.UriType
import java.net.URI
import java.net.URLDecoder

internal sealed interface DesktopLinkTarget {
    data class ExtensionRepository(val url: URI) : DesktopLinkTarget
    data class Manga(val source: Source, val manga: SManga, val chapter: SChapter?) : DesktopLinkTarget
}

/** Routes only recognized links. An arbitrary URI is never passed to the OS or treated as a file path. */
internal class DesktopLinkRouter {
    suspend fun resolve(raw: String, sources: Iterable<Source>): DesktopLinkTarget? {
        if (raw.length !in 1..8192) return null
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
        if (uri.scheme in setOf("mihon", "tachiyomi")) {
            val host = uri.host?.lowercase()
            if ((uri.scheme == "mihon" && host == "extension-store") ||
                (uri.scheme == "tachiyomi" && host == "add-repo")
            ) {
                val query = uri.rawQuery.orEmpty().split('&').firstOrNull { it.substringBefore('=') == "url" }
                    ?.substringAfter('=', "") ?: return null
                val repository = runCatching { URI(URLDecoder.decode(query, Charsets.UTF_8)) }.getOrNull()
                    ?: return null
                if (repository.scheme != "https" || repository.host.isNullOrBlank() ||
                    repository.userInfo != null || repository.fragment != null
                ) {
                    return null
                }
                return DesktopLinkTarget.ExtensionRepository(repository)
            }
            return null
        }
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null) {
            return null
        }
        for (source in sources.filterIsInstance<ResolvableSource>()) {
            val type = runCatching { source.getUriType(raw) }.getOrDefault(UriType.Unknown)
            if (type == UriType.Unknown) continue
            val manga = runCatching { source.getManga(raw) }.getOrNull() ?: continue
            val chapter = if (type == UriType.Chapter) runCatching { source.getChapter(raw) }.getOrNull() else null
            return DesktopLinkTarget.Manga(source, manga, chapter)
        }
        return null
    }
}
