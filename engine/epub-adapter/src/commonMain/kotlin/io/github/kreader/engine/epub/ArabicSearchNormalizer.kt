package io.github.kreader.engine.epub

data class NormalizedSearchMatch(
    val rawStart: Int,
    val rawEnd: Int,
    val before: String,
    val highlight: String,
    val after: String,
    val progression: Float
)

/**
 * Normalizes Arabic and Latin text for resilient in-book search while preserving
 * exact 1:1 character offsets back to the original chapter text for Readium DOM TextQuote matching.
 */
object ArabicSearchNormalizer {

    private val stripTagsCommentsRegex = Regex("<!--.*?-->", setOf(RegexOption.DOT_MATCHES_ALL))
    private val stripHeadStyleScriptRegex = Regex(
        "<(head|style|script|title)\\b[^>]*>.*?</\\1>",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val blockTagRegex = Regex(
        "</?(?:p|div|h[1-6]|li|ul|ol|blockquote|pre|br|hr|tr|td|th|table|section|article|header|footer|aside|nav|figure|figcaption)\\b[^>]*>",
        RegexOption.IGNORE_CASE
    )
    private val anyTagRegex = Regex("<[^>]+>")
    private val whitespaceRegex = Regex("[\\r\\n\\t\\u00A0 ]+")

    fun isArabicDiacriticOrIgnorable(ch: Char): Boolean {
        val code = ch.code
        return code in 0x064B..0x065F ||
            code == 0x0670 ||
            code in 0x06D6..0x06ED ||
            code == 0x0640 || // Tatweel
            code in 0x200B..0x200F ||
            code == 0xFEFF
    }

    fun normalizeChar(ch: Char): Char = when (ch) {
        '\u0623', '\u0625', '\u0622', '\u0671' -> '\u0627' // أ إ آ ٱ -> ا
        '\u0649' -> '\u064A' // ى -> ي
        else -> ch.lowercaseChar()
    }

    fun normalizeQuery(query: String): String {
        val sb = StringBuilder(query.length)
        var lastWasSpace = false
        for (ch in query) {
            if (isArabicDiacriticOrIgnorable(ch)) continue
            if (ch.isWhitespace() || ch == '\u00A0') {
                if (!lastWasSpace && sb.isNotEmpty()) {
                    sb.append(' ')
                    lastWasSpace = true
                }
            } else {
                sb.append(normalizeChar(ch))
                lastWasSpace = false
            }
        }
        return sb.toString().trim()
    }

    fun extractVisibleHtmlText(html: String): String {
        if (html.isBlank()) return ""
        val cleaned = html
            .replace(stripTagsCommentsRegex, " ")
            .replace(stripHeadStyleScriptRegex, " ")
            .replace(blockTagRegex, " ")
            .replace(anyTagRegex, "")
        val unescaped = unescapeHtmlEntities(cleaned)
        return unescaped.replace(whitespaceRegex, " ").trim()
    }

    fun search(
        rawText: String,
        query: String,
        maxMatches: Int = 100
    ): List<NormalizedSearchMatch> {
        val normQuery = normalizeQuery(query)
        if (normQuery.isEmpty() || rawText.isEmpty()) return emptyList()

        val normChars = StringBuilder(rawText.length)
        val rawOffsets = ArrayList<Int>(rawText.length)

        var lastWasSpace = false
        for (i in rawText.indices) {
            val ch = rawText[i]
            if (isArabicDiacriticOrIgnorable(ch)) continue
            if (ch.isWhitespace() || ch == '\u00A0') {
                if (!lastWasSpace && normChars.isNotEmpty()) {
                    normChars.append(' ')
                    rawOffsets.add(i)
                    lastWasSpace = true
                }
            } else {
                normChars.append(normalizeChar(ch))
                rawOffsets.add(i)
                lastWasSpace = false
            }
        }

        val normText = normChars.toString()
        if (normText.isEmpty()) return emptyList()

        val results = mutableListOf<NormalizedSearchMatch>()
        var searchFrom = 0
        val rawLen = rawText.length.coerceAtLeast(1)

        while (searchFrom < normText.length && results.size < maxMatches) {
            val matchNormStart = normText.indexOf(normQuery, searchFrom)
            if (matchNormStart < 0) break
            val matchNormEnd = matchNormStart + normQuery.length

            val rawStart = rawOffsets[matchNormStart]
            var rawEnd = if (matchNormEnd < rawOffsets.size) {
                rawOffsets[matchNormEnd]
            } else {
                rawText.length
            }

            // Include trailing diacritics attached to the final matched character, exclude trailing whitespace
            while (rawEnd > rawStart && rawText[rawEnd - 1].isWhitespace()) {
                rawEnd--
            }
            while (rawEnd < rawText.length && isArabicDiacriticOrIgnorable(rawText[rawEnd])) {
                rawEnd++
            }

            val beforeStart = (rawStart - 45).coerceAtLeast(0)
            val afterEnd = (rawEnd + 45).coerceAtMost(rawText.length)

            results.add(
                NormalizedSearchMatch(
                    rawStart = rawStart,
                    rawEnd = rawEnd,
                    before = rawText.substring(beforeStart, rawStart).trimStart(),
                    highlight = rawText.substring(rawStart, rawEnd),
                    after = rawText.substring(rawEnd, afterEnd).trimEnd(),
                    progression = (rawStart.toFloat() / rawLen.toFloat()).coerceIn(0f, 1f)
                )
            )

            searchFrom = matchNormStart + normQuery.length.coerceAtLeast(1)
        }

        return results
    }

    private fun unescapeHtmlEntities(text: String): String {
        if (!text.contains('&')) return text
        return text
            .replace("&nbsp;", " ")
            .replace("&#160;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&laquo;", "«")
            .replace("&raquo;", "»")
            .replace("&ndash;", "–")
            .replace("&mdash;", "—")
            .replace("&hellip;", "…")
            .replace(Regex("&#(\\d+);")) { match ->
                match.groupValues[1].toIntOrNull()?.let { code -> Char(code).toString() } ?: match.value
            }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
                match.groupValues[1].toIntOrNull(16)?.let { code -> Char(code).toString() } ?: match.value
            }
    }
}
