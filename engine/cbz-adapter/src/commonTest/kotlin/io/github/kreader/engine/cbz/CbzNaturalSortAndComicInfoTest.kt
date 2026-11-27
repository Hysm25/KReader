package io.github.kreader.engine.cbz

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CbzNaturalSortAndComicInfoTest {

    @Test
    fun testNaturalSortOrdersNumericPagesCorrectly() {
        val unsorted = listOf(
            "page10.jpg",
            "page2.jpg",
            "page1.jpg",
            "page20.png",
            "page3.webp",
            "ch2/page11.jpg",
            "ch2/page2.jpg",
            "ch10/page1.jpg"
        )

        val sorted = unsorted.sortedWith(CbzNaturalSort.comparator)
        assertEquals(
            listOf(
                "ch2/page2.jpg",
                "ch2/page11.jpg",
                "ch10/page1.jpg",
                "page1.jpg",
                "page2.jpg",
                "page3.webp",
                "page10.jpg",
                "page20.png"
            ),
            sorted
        )
    }

    @Test
    fun testNaturalSortHandlesLargeNumbersWithoutOverflow() {
        val entries = listOf(
            "scan_99999999999999999999999.jpg",
            "scan_100000000000000000000000.jpg",
            "scan_2.jpg"
        )
        val sorted = entries.sortedWith(CbzNaturalSort.comparator)
        assertEquals(
            listOf(
                "scan_2.jpg",
                "scan_99999999999999999999999.jpg",
                "scan_100000000000000000000000.jpg"
            ),
            sorted
        )
    }

    @Test
    fun testImageEntryFiltering() {
        assertTrue(CbzNaturalSort.isSupportedImageEntry("001.JPG"))
        assertTrue(CbzNaturalSort.isSupportedImageEntry("chapter_1/page_02.jpeg"))
        assertTrue(CbzNaturalSort.isSupportedImageEntry("03.png"))
        assertTrue(CbzNaturalSort.isSupportedImageEntry("04.webp"))
        assertTrue(CbzNaturalSort.isSupportedImageEntry("05.gif"))
        assertTrue(CbzNaturalSort.isSupportedImageEntry("06.bmp"))

        assertFalse(CbzNaturalSort.isSupportedImageEntry("ComicInfo.xml"))
        assertFalse(CbzNaturalSort.isSupportedImageEntry("chapter_1/"))
        assertFalse(CbzNaturalSort.isSupportedImageEntry("__MACOSX/._001.jpg"))
        assertFalse(CbzNaturalSort.isSupportedImageEntry("chapter_1/._page1.jpg"))
        assertFalse(CbzNaturalSort.isSupportedImageEntry(".DS_Store"))
    }

    @Test
    fun testComicInfoParser() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <ComicInfo xmlns:xsd="http://www.w3.org/2001/XMLSchema">
              <Title>The Crimson Arc</Title>
              <Series>Blade &amp; Spirit</Series>
              <Number>12</Number>
              <Writer>Kenji Sato</Writer>
              <PageCount>192</PageCount>
              <Manga>YesAndRightToLeft</Manga>
            </ComicInfo>
        """.trimIndent()

        val info = ComicInfoParser.parse(xml)
        assertEquals("The Crimson Arc", info.title)
        assertEquals("Blade & Spirit", info.series)
        assertEquals("12", info.number)
        assertEquals("Kenji Sato", info.writer)
        assertEquals(192, info.pageCount)
        assertEquals(true, info.isMangaRtl)
        assertEquals(
            "Blade & Spirit #12 - The Crimson Arc",
            ComicInfoParser.resolveDisplayTitle(info, "fallback")
        )
    }
}
