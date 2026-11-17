package io.github.kreader.engine.epub

import io.github.kreader.core.engine.TocItem
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.isRtlLanguage
import io.github.kreader.core.model.isRtlText
import org.jsoup.Jsoup
import java.io.File
import java.text.Normalizer
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.math.max
import kotlin.math.min

/**
 * Metadata extracted from the EPUB OPF package document.
 */
data class DesktopEpubMetadata(
    val title: String,
    val author: String? = null,
    val description: String? = null,
    val language: String? = null,
    val isRtl: Boolean = false
)

/**
 * A linear reading order entry in the EPUB spine.
 */
data class DesktopSpineItem(
    val id: String,
    val zipPath: String,
    val originalHref: String,
    val mediaType: String
)

/**
 * Lightweight, thread-safe EPUB document reader for Desktop JVM.
 * Parses container.xml, OPF package document, manifest, spine, NCX / NavDoc TOC,
 * and provides full-text search with Unicode NFC and Arabic diacritic normalization.
 */
class DesktopEpubDocument private constructor(
    private val zipFile: ZipFile,
    val metadata: DesktopEpubMetadata,
    val spine: List<DesktopSpineItem>,
    val tableOfContents: List<TocItem>,
    private val manifestMap: Map<String, DesktopSpineItem>,
    private val opfDir: String
) : AutoCloseable {

    companion object {
        fun open(file: File): Result<DesktopEpubDocument> = runCatching {
            require(file.exists() && file.isFile && file.length() > 0L) {
                "EPUB file not found or empty: ${file.absolutePath}"
            }
            val zip = ZipFile(file)
            try {
                // 1. Locate rootfile OPF via META-INF/container.xml
                val containerEntry = zip.getEntry("META-INF/container.xml")
                    ?: zip.entries().asSequence().firstOrNull { it.name.equals("META-INF/container.xml", ignoreCase = true) }
                    ?: throw IllegalArgumentException("META-INF/container.xml not found in EPUB")

                val containerXml = zip.getInputStream(containerEntry).bufferedReader().readText()
                val opfPath = Regex("""full-path=["']([^"']+)["']""").find(containerXml)?.groupValues?.get(1)
                    ?: "OEBPS/content.opf"
                val opfDir = if (opfPath.contains('/')) opfPath.substringBeforeLast('/') else ""

                val opfEntry = zip.getEntry(opfPath)
                    ?: zip.entries().asSequence().firstOrNull { it.name.equals(opfPath, ignoreCase = true) }
                    ?: throw IllegalArgumentException("OPF package document not found at $opfPath")

                val opfXml = zip.getInputStream(opfEntry).bufferedReader().readText()
                val opfDoc = Jsoup.parse(opfXml, "", org.jsoup.parser.Parser.xmlParser())

                // 2. Parse Metadata
                val title = opfDoc.select("metadata > dc|title, metadata > title").first()?.text().orEmpty().ifBlank {
                    file.nameWithoutExtension
                }
                val author = opfDoc.select("metadata > dc|creator, metadata > creator").first()?.text()
                val description = opfDoc.select("metadata > dc|description, metadata > description").first()?.text()
                val language = opfDoc.select("metadata > dc|language, metadata > language").first()?.text()
                val spineElem = opfDoc.select("spine").first()
                val spineDirection = spineElem?.attr("page-progression-direction").orEmpty()

                val isRtl = spineDirection.equals("rtl", ignoreCase = true) ||
                    (language != null && isRtlLanguage(language)) ||
                    isRtlText(title) ||
                    isRtlText(author.orEmpty())

                val metadata = DesktopEpubMetadata(
                    title = title,
                    author = author,
                    description = description,
                    language = language,
                    isRtl = isRtl
                )

                // 3. Parse Manifest
                val manifestMap = mutableMapOf<String, DesktopSpineItem>()
                val navDocHref = mutableListOf<String>()
                var ncxHref: String? = null

                opfDoc.select("manifest > item").forEach { item ->
                    val id = item.attr("id")
                    val href = item.attr("href")
                    val mediaType = item.attr("media-type")
                    val properties = item.attr("properties")

                    if (id.isNotBlank() && href.isNotBlank()) {
                        val resolvedZipPath = resolvePath(opfDir, href)
                        val spineItem = DesktopSpineItem(
                            id = id,
                            zipPath = resolvedZipPath,
                            originalHref = href,
                            mediaType = mediaType
                        )
                        manifestMap[id] = spineItem

                        if (properties.contains("nav", ignoreCase = true)) {
                            navDocHref.add(resolvedZipPath)
                        }
                        if (mediaType.equals("application/x-dtbncx+xml", ignoreCase = true) || id.equals("ncx", ignoreCase = true)) {
                            ncxHref = resolvedZipPath
                        }
                    }
                }

                // 4. Parse Spine
                val spineList = mutableListOf<DesktopSpineItem>()
                opfDoc.select("spine > itemref").forEach { itemref ->
                    val idref = itemref.attr("idref")
                    val item = manifestMap[idref]
                    if (item != null) {
                        spineList.add(item)
                    }
                }

                // Fallback: If spine was empty, include all xhtml manifest items
                if (spineList.isEmpty()) {
                    manifestMap.values.filter {
                        it.mediaType.contains("xhtml", ignoreCase = true) || it.mediaType.contains("html", ignoreCase = true)
                    }.forEach { spineList.add(it) }
                }

                // 5. Parse Table of Contents (NavDoc or NCX)
                var toc = emptyList<TocItem>()
                if (navDocHref.isNotEmpty()) {
                    for (navPath in navDocHref) {
                        val navEntry = zip.getEntry(navPath)
                        if (navEntry != null) {
                            val navXml = zip.getInputStream(navEntry).bufferedReader().readText()
                            toc = parseNavDoc(navXml, opfDir)
                            if (toc.isNotEmpty()) break
                        }
                    }
                }

                if (toc.isEmpty() && ncxHref != null) {
                    val ncxEntry = zip.getEntry(ncxHref)
                    if (ncxEntry != null) {
                        val ncxXml = zip.getInputStream(ncxEntry).bufferedReader().readText()
                        toc = parseNcx(ncxXml, opfDir)
                    }
                }

                if (toc.isEmpty()) {
                    // Fallback TOC from spine items
                    toc = spineList.mapIndexed { idx, item ->
                        TocItem(
                            title = "Chapter ${idx + 1}",
                            locator = Locator.initial(item.originalHref, item.mediaType)
                        )
                    }
                }

                DesktopEpubDocument(
                    zipFile = zip,
                    metadata = metadata,
                    spine = spineList,
                    tableOfContents = toc,
                    manifestMap = manifestMap,
                    opfDir = opfDir
                )
            } catch (t: Throwable) {
                zip.close()
                throw t
            }
        }

        private fun resolvePath(baseDir: String, relativePath: String): String {
            val clean = relativePath.substringBefore('#').substringBefore('?')
            val combined = if (baseDir.isEmpty()) clean else "$baseDir/$clean"
            val parts = mutableListOf<String>()
            for (part in combined.split('/')) {
                when (part) {
                    "", "." -> continue
                    ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                    else -> parts.add(part)
                }
            }
            return parts.joinToString("/")
        }

        private fun parseNavDoc(xhtml: String, opfDir: String): List<TocItem> {
            val doc = Jsoup.parse(xhtml)
            val nav = doc.select("nav[*|type=toc], nav[epub|type=toc], nav#toc").first()
                ?: doc.select("nav").first() ?: return emptyList()

            val topList = nav.selectFirst("> ol, > ul") ?: return emptyList()
            return parseNavList(topList, opfDir)
        }

        private fun parseNavList(listElem: org.jsoup.nodes.Element, opfDir: String): List<TocItem> {
            val results = mutableListOf<TocItem>()
            for (li in listElem.select("> li")) {
                val anchor = li.selectFirst("> a")
                val span = li.selectFirst("> span")
                val title = anchor?.text()?.trim() ?: span?.text()?.trim() ?: continue
                val href = anchor?.attr("href")?.trim().orEmpty()
                val locator = Locator.initial(
                    href = href.ifBlank { "content.xhtml" },
                    type = "application/xhtml+xml"
                ).copy(title = title)

                val subList = li.selectFirst("> ol, > ul")
                val children = if (subList != null) parseNavList(subList, opfDir) else emptyList()

                results.add(TocItem(title = title, locator = locator, children = children))
            }
            return results
        }

        private fun parseNcx(ncxXml: String, opfDir: String): List<TocItem> {
            val doc = Jsoup.parse(ncxXml, "", org.jsoup.parser.Parser.xmlParser())
            val navMap = doc.selectFirst("navMap") ?: return emptyList()
            return parseNavPoints(navMap, opfDir)
        }

        private fun parseNavPoints(parent: org.jsoup.nodes.Element, opfDir: String): List<TocItem> {
            val results = mutableListOf<TocItem>()
            for (navPoint in parent.select("> navPoint")) {
                val title = navPoint.selectFirst("> navLabel > text")?.text()?.trim().orEmpty().ifBlank { "Untitled" }
                val contentSrc = navPoint.selectFirst("> content")?.attr("src")?.trim().orEmpty()
                val locator = Locator.initial(
                    href = contentSrc.ifBlank { "content.xhtml" },
                    type = "application/xhtml+xml"
                ).copy(title = title)

                val children = parseNavPoints(navPoint, opfDir)
                results.add(TocItem(title = title, locator = locator, children = children))
            }
            return results
        }
    }

    /**
     * Reads the raw XHTML/HTML content of a spine item.
     */
    fun readChapter(spineItem: DesktopSpineItem): String {
        val entry = zipFile.getEntry(spineItem.zipPath)
            ?: zipFile.entries().asSequence().firstOrNull { it.name.equals(spineItem.zipPath, ignoreCase = true) }
            ?: return ""
        return zipFile.getInputStream(entry).bufferedReader().readText()
    }

    /**
     * Reads chapter XHTML by relative or original href.
     */
    fun readChapterByHref(href: String): String? {
        val clean = href.substringBefore('#').substringBefore('?')
        val item = spine.firstOrNull {
            it.originalHref.equals(clean, ignoreCase = true) ||
            it.zipPath.endsWith(clean, ignoreCase = true) ||
            clean.endsWith(it.originalHref, ignoreCase = true)
        } ?: manifestMap[clean] ?: return null
        return readChapter(item)
    }

    /**
     * Resolves and extracts an asset (e.g. image) from the EPUB ZIP.
     */
    fun readAssetBytes(assetHref: String, fromChapterZipPath: String? = null): ByteArray? {
        val clean = assetHref.substringBefore('#').substringBefore('?')
        val chapterDir = if (fromChapterZipPath != null && fromChapterZipPath.contains('/')) {
            fromChapterZipPath.substringBeforeLast('/')
        } else {
            opfDir
        }
        val targetPath = resolvePath(chapterDir, clean)

        val entry = zipFile.getEntry(targetPath)
            ?: zipFile.getEntry(clean)
            ?: zipFile.entries().asSequence().firstOrNull {
                it.name.equals(targetPath, ignoreCase = true) || it.name.endsWith("/$clean", ignoreCase = true)
            } ?: return null

        return zipFile.getInputStream(entry).use { it.readBytes() }
    }

    /**
     * In-book text search across all spine chapters with Unicode NFC normalization
     * and Arabic diacritic stripping for maximal search recall.
     */
    fun search(query: String): List<Locator> {
        val cleanQuery = normalizeForSearch(query)
        if (cleanQuery.isBlank()) return emptyList()

        val results = mutableListOf<Locator>()
        val totalSpine = spine.size.coerceAtLeast(1)

        for ((spineIndex, item) in spine.withIndex()) {
            val xhtml = readChapter(item)
            if (xhtml.isBlank()) continue

            val doc = Jsoup.parse(xhtml)
            val bodyText = doc.body().text()
            val normalizedBody = normalizeForSearch(bodyText)

            var startIndex = 0
            while (startIndex < normalizedBody.length) {
                val matchIndex = normalizedBody.indexOf(cleanQuery, startIndex)
                if (matchIndex < 0) break

                val chapterProgress = matchIndex.toFloat() / normalizedBody.length.coerceAtLeast(1)
                val totalProgress = (spineIndex.toFloat() + chapterProgress) / totalSpine.toFloat()

                val beforeStart = max(0, matchIndex - 40)
                val before = bodyText.substring(beforeStart, matchIndex).trimStart()
                val highlight = bodyText.substring(matchIndex, min(bodyText.length, matchIndex + query.length))
                val afterEnd = min(bodyText.length, matchIndex + query.length + 40)
                val after = bodyText.substring(min(bodyText.length, matchIndex + query.length), afterEnd).trimEnd()

                results.add(
                    Locator(
                        href = item.originalHref,
                        type = item.mediaType.ifBlank { "application/xhtml+xml" },
                        title = null,
                        locations = Locator.Locations(
                            progression = chapterProgress,
                            position = matchIndex,
                            totalProgression = totalProgress
                        ),
                        text = Locator.LocatorText(
                            before = before,
                            highlight = highlight,
                            after = after
                        )
                    )
                )

                startIndex = matchIndex + max(1, cleanQuery.length)
            }
        }
        return results
    }

    private fun normalizeForSearch(input: String): String {
        val nfc = Normalizer.normalize(input, Normalizer.Form.NFC)
        // Strip Arabic diacritics/tashkeel (\u064B to \u065F, \u0670) and Tatweel (\u0640)
        return nfc.replace(Regex("""[\u064B-\u065F\u0670\u0640]"""), "")
            .lowercase()
            .trim()
    }

    override fun close() {
        runCatching { zipFile.close() }
    }
}
