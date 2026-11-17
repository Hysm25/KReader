package io.github.kreader.feature.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import io.github.kreader.core.model.Highlight
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.text.Normalizer

/**
 * Immutable AST nodes representing rendered EPUB content blocks.
 */
sealed interface ReaderContentBlock {
    val id: String
    val rawText: String

    data class Paragraph(
        override val id: String,
        override val rawText: String,
        val annotatedText: AnnotatedString
    ) : ReaderContentBlock

    data class Heading(
        override val id: String,
        val level: Int,
        override val rawText: String,
        val annotatedText: AnnotatedString
    ) : ReaderContentBlock

    data class Blockquote(
        override val id: String,
        override val rawText: String,
        val annotatedText: AnnotatedString
    ) : ReaderContentBlock

    data class ListItem(
        override val id: String,
        val bullet: String,
        override val rawText: String,
        val annotatedText: AnnotatedString
    ) : ReaderContentBlock

    data class ImageBlock(
        override val id: String,
        val imageSource: String,
        val altText: String? = null
    ) : ReaderContentBlock {
        override val rawText: String get() = altText.orEmpty()
    }

    data class Divider(
        override val id: String
    ) : ReaderContentBlock {
        override val rawText: String get() = ""
    }
}

/**
 * Parses EPUB chapter XHTML into an AST of [ReaderContentBlock]s with inline styling.
 */
object EpubBlockParser {

    fun parse(xhtml: String, chapterHref: String): List<ReaderContentBlock> {
        if (xhtml.isBlank()) return emptyList()

        val doc = Jsoup.parse(xhtml)
        val body = doc.body() ?: return emptyList()
        val blocks = mutableListOf<ReaderContentBlock>()
        var blockCounter = 0

        fun nextId(prefix: String) = "$chapterHref#$prefix-${blockCounter++}"

        for (child in body.children()) {
            val tagName = child.tagName().lowercase()
            when (tagName) {
                "h1" -> blocks.add(createHeading(child, 1, nextId("h1")))
                "h2" -> blocks.add(createHeading(child, 2, nextId("h2")))
                "h3" -> blocks.add(createHeading(child, 3, nextId("h3")))
                "h4" -> blocks.add(createHeading(child, 4, nextId("h4")))
                "h5" -> blocks.add(createHeading(child, 5, nextId("h5")))
                "h6" -> blocks.add(createHeading(child, 6, nextId("h6")))
                "blockquote" -> {
                    val raw = child.text().trim()
                    if (raw.isNotBlank()) {
                        blocks.add(
                            ReaderContentBlock.Blockquote(
                                id = nextId("quote"),
                                rawText = raw,
                                annotatedText = buildAnnotatedContent(child)
                            )
                        )
                    }
                }
                "hr" -> blocks.add(ReaderContentBlock.Divider(id = nextId("hr")))
                "ul" -> {
                    child.select("> li").forEachIndexed { _, li ->
                        val raw = li.text().trim()
                        if (raw.isNotBlank()) {
                            blocks.add(
                                ReaderContentBlock.ListItem(
                                    id = nextId("li"),
                                    bullet = "•",
                                    rawText = raw,
                                    annotatedText = buildAnnotatedContent(li)
                                )
                            )
                        }
                    }
                }
                "ol" -> {
                    child.select("> li").forEachIndexed { idx, li ->
                        val raw = li.text().trim()
                        if (raw.isNotBlank()) {
                            blocks.add(
                                ReaderContentBlock.ListItem(
                                    id = nextId("li"),
                                    bullet = "${idx + 1}.",
                                    rawText = raw,
                                    annotatedText = buildAnnotatedContent(li)
                                )
                            )
                        }
                    }
                }
                "img" -> {
                    val src = child.attr("src")
                    if (src.isNotBlank()) {
                        blocks.add(
                            ReaderContentBlock.ImageBlock(
                                id = nextId("img"),
                                imageSource = src,
                                altText = child.attr("alt")
                            )
                        )
                    }
                }
                "figure" -> {
                    val img = child.selectFirst("img")
                    if (img != null) {
                        val src = img.attr("src")
                        if (src.isNotBlank()) {
                            blocks.add(
                                ReaderContentBlock.ImageBlock(
                                    id = nextId("img"),
                                    imageSource = src,
                                    altText = child.selectFirst("figcaption")?.text() ?: img.attr("alt")
                                )
                            )
                        }
                    } else {
                        val raw = child.text().trim()
                        if (raw.isNotBlank()) {
                            blocks.add(
                                ReaderContentBlock.Paragraph(
                                    id = nextId("p"),
                                    rawText = raw,
                                    annotatedText = buildAnnotatedContent(child)
                                )
                            )
                        }
                    }
                }
                "div", "section", "article" -> {
                    // Check if child contains images or nested paragraphs
                    val img = child.selectFirst("> img")
                    if (img != null && child.children().size == 1) {
                        blocks.add(
                            ReaderContentBlock.ImageBlock(
                                id = nextId("img"),
                                imageSource = img.attr("src"),
                                altText = img.attr("alt")
                            )
                        )
                    } else {
                        val paragraphs = child.select("> p")
                        if (paragraphs.isNotEmpty()) {
                            for (p in paragraphs) {
                                val raw = p.text().trim()
                                if (raw.isNotBlank()) {
                                    blocks.add(
                                        ReaderContentBlock.Paragraph(
                                            id = nextId("p"),
                                            rawText = raw,
                                            annotatedText = buildAnnotatedContent(p)
                                        )
                                    )
                                }
                            }
                        } else {
                            val raw = child.text().trim()
                            if (raw.isNotBlank()) {
                                blocks.add(
                                    ReaderContentBlock.Paragraph(
                                        id = nextId("p"),
                                        rawText = raw,
                                        annotatedText = buildAnnotatedContent(child)
                                    )
                                )
                            }
                        }
                    }
                }
                else -> {
                    // Default to Paragraph (e.g. <p> or other container tags)
                    val raw = child.text().trim()
                    if (raw.isNotBlank()) {
                        blocks.add(
                            ReaderContentBlock.Paragraph(
                                id = nextId("p"),
                                rawText = raw,
                                annotatedText = buildAnnotatedContent(child)
                            )
                        )
                    }
                }
            }
        }

        return blocks
    }

    private fun createHeading(elem: Element, level: Int, id: String): ReaderContentBlock.Heading {
        val raw = elem.text().trim()
        return ReaderContentBlock.Heading(
            id = id,
            level = level,
            rawText = raw,
            annotatedText = buildAnnotatedContent(elem)
        )
    }

    /**
     * Traverses DOM nodes recursively, extracting text and applying inline Compose span styles.
     */
    fun buildAnnotatedContent(root: Element): AnnotatedString {
        return buildAnnotatedString {
            appendNode(root, this)
        }
    }

    private fun appendNode(node: Node, builder: AnnotatedString.Builder) {
        if (node is TextNode) {
            val text = Normalizer.normalize(node.text(), Normalizer.Form.NFC)
            builder.append(text)
            return
        }

        if (node is Element) {
            val tag = node.tagName().lowercase()
            val style = when (tag) {
                "b", "strong" -> SpanStyle(fontWeight = FontWeight.Bold)
                "i", "em" -> SpanStyle(fontStyle = FontStyle.Italic)
                "u" -> SpanStyle(textDecoration = TextDecoration.Underline)
                "s", "strike", "del" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                "code", "kbd", "samp" -> SpanStyle(fontFamily = FontFamily.Monospace)
                "mark" -> SpanStyle(background = Color(0x66FFEB3B))
                else -> null
            }

            if (style != null) {
                val start = builder.length
                for (child in node.childNodes()) {
                    appendNode(child, builder)
                }
                val end = builder.length
                if (end > start) {
                    builder.addStyle(style, start, end)
                }
            } else {
                for (child in node.childNodes()) {
                    appendNode(child, builder)
                }
            }
        }
    }

    /**
     * Injects highlight background spans into an [AnnotatedString] using TextQuoteSelector matching.
     */
    fun applyHighlights(
        rawText: String,
        baseAnnotated: AnnotatedString,
        highlights: List<Highlight>
    ): AnnotatedString {
        if (highlights.isEmpty() || rawText.isBlank()) return baseAnnotated

        val builder = AnnotatedString.Builder(baseAnnotated)
        val normalizedRaw = Normalizer.normalize(rawText, Normalizer.Form.NFC).replace(Regex("""\s+"""), " ")

        for (h in highlights) {
            val quote = h.locator.text?.highlight?.let { Normalizer.normalize(it, Normalizer.Form.NFC).replace(Regex("""\s+"""), " ") }
            if (quote.isNullOrBlank()) continue

            val matchIndex = normalizedRaw.indexOf(quote)
            if (matchIndex >= 0 && matchIndex + quote.length <= baseAnnotated.length) {
                val color = runCatching {
                    val hex = h.colorHex.removePrefix("#")
                    val colorInt = hex.toLong(16)
                    if (hex.length == 6) Color(colorInt or 0x66000000L) else Color(colorInt)
                }.getOrDefault(Color(0x66FFEB3B))

                builder.addStyle(
                    style = SpanStyle(background = color),
                    start = matchIndex,
                    end = matchIndex + quote.length
                )
            }
        }
        return builder.toAnnotatedString()
    }
}
