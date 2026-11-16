package io.github.kreader.core.database

/**
 * Converts EPUB chapter HTML/XHTML documents into clean, structured Markdown.
 */
fun convertHtmlToMarkdown(html: String): String {
    if (html.isBlank()) return ""

    // 1. Remove head, style, script, and nav blocks
    var text = html
        .replace(Regex("""<head[^>]*>.*?</head>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        .replace(Regex("""<style[^>]*>.*?</style>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        .replace(Regex("""<script[^>]*>.*?</script>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        .replace(Regex("""<nav[^>]*>.*?</nav>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")

    // 2. Line breaks & horizontal rules
    text = text
        .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""<hr\s*/?>""", RegexOption.IGNORE_CASE), "\n\n---\n\n")

    // 3. Headings
    text = text
        .replace(Regex("""<h1[^>]*>(.*?)</h1>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))) {
            "\n\n# ${it.groupValues[1].cleanInlineHtml().trim()}\n\n"
        }
        .replace(Regex("""<h2[^>]*>(.*?)</h2>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))) {
            "\n\n## ${it.groupValues[1].cleanInlineHtml().trim()}\n\n"
        }
        .replace(Regex("""<h3[^>]*>(.*?)</h3>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))) {
            "\n\n### ${it.groupValues[1].cleanInlineHtml().trim()}\n\n"
        }
        .replace(Regex("""<h4[^>]*>(.*?)</h4>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))) {
            "\n\n#### ${it.groupValues[1].cleanInlineHtml().trim()}\n\n"
        }
        .replace(Regex("""<h5[^>]*>(.*?)</h5>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))) {
            "\n\n##### ${it.groupValues[1].cleanInlineHtml().trim()}\n\n"
        }
        .replace(Regex("""<h6[^>]*>(.*?)</h6>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))) {
            "\n\n###### ${it.groupValues[1].cleanInlineHtml().trim()}\n\n"
        }

    // 4. Blockquotes
    text = text.replace(Regex("""<blockquote[^>]*>(.*?)</blockquote>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))) {
        val content = it.groupValues[1].cleanInlineHtml().trim()
        val quoted = content.lines().joinToString("\n") { line -> "> $line" }
        "\n\n$quoted\n\n"
    }

    // 5. List items
    text = text.replace(Regex("""<li[^>]*>(.*?)</li>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))) {
        val item = it.groupValues[1].cleanInlineHtml().trim()
        "\n- $item"
    }

    // 6. Paragraphs
    text = text.replace(Regex("""<p[^>]*>(.*?)</p>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))) {
        val p = it.groupValues[1].cleanInlineHtml().trim()
        if (p.isNotEmpty()) "\n\n$p\n\n" else ""
    }

    // 7. Strip any remaining HTML tags
    text = text.cleanInlineHtml()

    // 8. Decode HTML entities
    text = decodeHtmlEntities(text)

    // 9. Normalize spacing
    return text.lines()
        .map { it.trimEnd() }
        .joinToString("\n")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()
}

private fun String.cleanInlineHtml(): String {
    return this
        // Bold tags
        .replace(Regex("""<(?:b|strong)[^>]*>(.*?)</(?:b|strong)>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))) {
            val inner = it.groupValues[1].trim()
            if (inner.isNotEmpty()) "**$inner**" else ""
        }
        // Italic tags
        .replace(Regex("""<(?:i|em)[^>]*>(.*?)</(?:i|em)>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))) {
            val inner = it.groupValues[1].trim()
            if (inner.isNotEmpty()) "*$inner*" else ""
        }
        // Links: [text](href) if valid, else plain text
        .replace(Regex("""<a\s+[^>]*href=["']([^"']+)["'][^>]*>(.*?)</a>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))) {
            val href = it.groupValues[1].trim()
            val label = it.groupValues[2].cleanInlineHtml().trim()
            if (label.isNotEmpty() && !href.startsWith("#")) "[$label]($href)" else label
        }
        // Remove all remaining tags (span, div, article, etc.)
        .replace(Regex("""<[^>]+>"""), "")
}

private fun decodeHtmlEntities(input: String): String {
    return input
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
        .replace("&mdash;", "—")
        .replace("&ndash;", "–")
        .replace("&hellip;", "…")
        .replace("&laquo;", "«")
        .replace("&raquo;", "»")
        .replace("&ldquo;", "“")
        .replace("&rdquo;", "”")
        .replace("&lsquo;", "‘")
        .replace("&rsquo;", "’")
        .replace(Regex("""&#(\d+);""")) {
            val code = it.groupValues[1].toIntOrNull()
            if (code != null) code.toChar().toString() else it.value
        }
        .replace(Regex("""&#x([0-9a-fA-F]+);""")) {
            val code = it.groupValues[1].toIntOrNull(16)
            if (code != null) code.toChar().toString() else it.value
        }
}
