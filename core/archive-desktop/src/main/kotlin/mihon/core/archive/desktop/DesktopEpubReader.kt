package mihon.core.archive.desktop

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.nio.file.Path

class DesktopEpubReader(path: Path) : Closeable {
    private val reader = DesktopArchiveReader(path)
    private val pathSeparator = getPathSeparator()

    fun getInputStream(entryName: String): InputStream? = reader.openEntry(entryName)

    fun getImagesFromPages(): List<String> {
        val ref = getPackageHref()
        val doc = getPackageDocument(ref)
        val pages = getPagesFromDocument(doc)
        return getImagesFromPages(pages, ref)
    }

    fun getPackageHref(): String {
        val meta = getInputStream(resolveZipPath("META-INF", "container.xml"))
        if (meta != null) {
            val metaDoc = meta.use { Jsoup.parse(it, null, "", Parser.xmlParser()) }
            metaDoc.getElementsByTag("rootfile").first()?.attr("full-path")?.let { return it }
        }
        return resolveZipPath("OEBPS", "content.opf")
    }

    fun getPackageDocument(ref: String): Document =
        getInputStream(ref)!!.use { Jsoup.parse(it, null, "", Parser.xmlParser()) }

    override fun close() = reader.close()

    private fun getPagesFromDocument(document: Document): List<String> {
        val pages = document.select("manifest > item")
            .filter { node -> "application/xhtml+xml" == node.attr("media-type") }
            .associateBy { it.attr("id") }
        val spine = document.select("spine > itemref").map { it.attr("idref") }
        return spine.mapNotNull { pages[it] }.map { it.attr("href") }
    }

    private fun getImagesFromPages(pages: List<String>, packageHref: String): List<String> {
        val result = mutableListOf<String>()
        val basePath = getParentDirectory(packageHref)
        pages.forEach { page ->
            val entryPath = resolveZipPath(basePath, page)
            val document = getInputStream(entryPath)!!.use { Jsoup.parse(it, null, "") }
            val imageBasePath = getParentDirectory(entryPath)
            document.allElements.forEach {
                when (it.tagName()) {
                    "img" -> result.add(resolveZipPath(imageBasePath, it.attr("src")))
                    "image" -> result.add(resolveZipPath(imageBasePath, it.attr("xlink:href")))
                }
            }
        }
        return result
    }

    private fun getPathSeparator(): String {
        val meta = getInputStream("META-INF\\container.xml")
        return if (meta != null) {
            meta.close()
            "\\"
        } else {
            "/"
        }
    }

    private fun resolveZipPath(basePath: String, relativePath: String): String {
        if (relativePath.startsWith(pathSeparator)) return relativePath
        var fixedBasePath = basePath.replace(pathSeparator, File.separator)
        if (!fixedBasePath.startsWith(File.separator)) fixedBasePath = "${File.separator}$fixedBasePath"
        val fixedRelativePath = relativePath.replace(pathSeparator, File.separator)
        val resolvedPath = File(fixedBasePath, fixedRelativePath).canonicalPath
        return resolvedPath.replace(File.separator, pathSeparator).substring(1)
    }

    private fun getParentDirectory(path: String): String {
        val separatorIndex = path.lastIndexOf(pathSeparator)
        return if (separatorIndex >= 0) path.substring(0, separatorIndex) else ""
    }
}
