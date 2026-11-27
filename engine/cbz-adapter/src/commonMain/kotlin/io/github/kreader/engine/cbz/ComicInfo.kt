package io.github.kreader.engine.cbz

/**
 * Metadata extracted from a `ComicInfo.xml` manifest inside a CBZ archive.
 */
data class ComicInfo(
    val title: String? = null,
    val series: String? = null,
    val writer: String? = null,
    val number: String? = null,
    val pageCount: Int? = null,
    val manga: String? = null,
    val isMangaRtl: Boolean? = null
)

object ComicInfoParser {

    fun parse(xml: String): ComicInfo {
        val title = extractTag(xml, "Title")
        val series = extractTag(xml, "Series")
        val writer = extractTag(xml, "Writer") ?: extractTag(xml, "Penciller")
        val number = extractTag(xml, "Number")
        val pageCount = extractTag(xml, "PageCount")?.toIntOrNull()?.takeIf { it > 0 }
        val manga = extractTag(xml, "Manga")
        val isMangaRtl = when {
            manga == null -> null
            manga.equals("Yes", ignoreCase = true) ||
                manga.equals("YesAndRightToLeft", ignoreCase = true) -> true
            manga.equals("No", ignoreCase = true) -> false
            else -> null
        }
        return ComicInfo(
            title = title,
            series = series,
            writer = writer,
            number = number,
            pageCount = pageCount,
            manga = manga,
            isMangaRtl = isMangaRtl
        )
    }

    fun resolveDisplayTitle(comicInfo: ComicInfo?, fallbackTitle: String): String {
        if (comicInfo == null) return fallbackTitle
        val title = comicInfo.title?.takeIf { it.isNotBlank() }
        val series = comicInfo.series?.takeIf { it.isNotBlank() }
        val number = comicInfo.number?.takeIf { it.isNotBlank() }
        return when {
            title != null && series != null && !title.contains(series, ignoreCase = true) && number != null ->
                "$series #$number - $title"
            title != null -> title
            series != null && number != null -> "$series #$number"
            series != null -> series
            else -> fallbackTitle
        }
    }

    private fun extractTag(xml: String, tagName: String): String? {
        val regex = Regex(
            """<$tagName(?:\s[^>]*)?>(.*?)</$tagName>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val raw = regex.find(xml)?.groupValues?.get(1) ?: return null
        val withoutCdata = raw
            .replace(Regex("""<!\[CDATA\[(.*?)]]""", RegexOption.DOT_MATCHES_ALL)) { it.groupValues[1] }
            .replace(Regex("<[^>]+>"), "")
        return unescapeXmlEntities(withoutCdata).trim().ifBlank { null }
    }

    private fun unescapeXmlEntities(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("""&#(\d+);""")) { match ->
                match.groupValues[1].toIntOrNull()?.let { code -> Char(code).toString() } ?: match.value
            }
            .replace(Regex("""&#x([0-9a-fA-F]+);""")) { match ->
                match.groupValues[1].toIntOrNull(16)?.let { code -> Char(code).toString() } ?: match.value
            }
    }
}
