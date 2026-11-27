package io.github.kreader.engine.epub

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArabicSearchNormalizerTest {

    @Test
    fun testArabicTashkeelAndAlefYaNormalizationPreservesRawMatch() {
        val raw = "قَالَ اللَّهُ تَعَالَى إِلَى أَحْمَدَ وَإِبْرَاهِيمَ فِي ذَلِكَ الكِتَابِ."
        val matches1 = ArabicSearchNormalizer.search(raw, "احمد")
        assertEquals(1, matches1.size)
        assertEquals("أَحْمَدَ", matches1[0].highlight)

        val matches2 = ArabicSearchNormalizer.search(raw, "الي")
        assertEquals(1, matches2.size)
        assertEquals("إِلَى", matches2[0].highlight)

        val matches3 = ArabicSearchNormalizer.search(raw, "الله")
        assertEquals(1, matches3.size)
        assertEquals("اللَّهُ", matches3[0].highlight)
    }

    @Test
    fun testHtmlExtractionAndEnglishCaseInsensitiveSearch() {
        val html = """
            <html><head><style>body { color: #000; }</style></head>
            <body><p>Hello <em>World</em>!</p><p>Second paragraph.</p></body></html>
        """.trimIndent()
        val text = ArabicSearchNormalizer.extractVisibleHtmlText(html)
        assertTrue(text.contains("Hello World!"))
        val matches = ArabicSearchNormalizer.search(text, "hello world")
        assertEquals(1, matches.size)
        assertEquals("Hello World", matches[0].highlight)
    }
}
