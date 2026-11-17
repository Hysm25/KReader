package io.github.kreader.core.database

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EpubToMarkdownTest {

    @Test
    fun testConvertHtmlToMarkdownHeadersAndParagraphs() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Chapter 1</title>
                <style>p { color: red; }</style>
            </head>
            <body>
                <h1>Chapter 1: The Beginning</h1>
                <p>This is the first paragraph of the story.</p>
                <p>This is the second paragraph with <b>bold text</b>.</p>
            </body>
            </html>
        """.trimIndent()

        val markdown = convertHtmlToMarkdown(html).trim()

        assertTrue(markdown.contains("# Chapter 1: The Beginning"))
        assertTrue(markdown.contains("This is the first paragraph of the story."))
        assertTrue(markdown.contains("This is the second paragraph with bold text."))
        // Ensure head and style tags are completely removed
        assertTrue(!markdown.contains("<style>"))
        assertTrue(!markdown.contains("color: red;"))
    }

    @Test
    fun testConvertHtmlToMarkdownBlockquotesAndLists() {
        val html = """
            <blockquote>
                To be, or not to be, that is the question.
            </blockquote>
            <ul>
                <li>Point A</li>
                <li>Point B</li>
            </ul>
        """.trimIndent()

        val markdown = convertHtmlToMarkdown(html).trim()

        assertTrue(markdown.contains("> To be, or not to be, that is the question."))
        assertTrue(markdown.contains("- Point A"))
        assertTrue(markdown.contains("- Point B"))
    }

    @Test
    fun testConvertHtmlToMarkdownEmptyInput() {
        assertEquals("", convertHtmlToMarkdown(""))
        assertEquals("", convertHtmlToMarkdown("   "))
    }
}
