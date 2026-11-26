package io.github.kreader.feature.reader

import io.github.kreader.core.engine.TocItem
import io.github.kreader.core.model.Bookmark
import io.github.kreader.core.model.CustomFont
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ReaderTypography
import io.github.kreader.core.model.ThemeMode
import io.github.kreader.engine.epub.ReadiumFontResources
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.navigator.preferences.Spread
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.shared.ExperimentalReadiumApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalReadiumApi::class)
class EpubPreferencesAndLayoutTest {

    @Test
    fun testEpubPreferences_publisherStylesPreservesNativeHeadingScaleAndAllowsUserOverrides() {
        val theme = ReaderTheme(mode = ThemeMode.LIGHT)
        val typography = ReaderTypography(
            fontSizeSp = 20f,
            lineHeightMultiplier = 1.8f,
            paddingHorizontal = 24,
            textAlignment = ReaderTypography.TextAlignment.JUSTIFY,
            fontFamily = "Cinzel",
            overrideFont = true
        )

        val preferences = buildReadiumPreferences(
            theme = theme,
            typography = typography,
            isScrollMode = false,
            isRtl = false,
            isSystemInDarkTheme = false
        )

        // publisherStyles MUST be true so ReadiumCSS honors native EPUB CSS heading sizes (h1-h6), centered <hr>, and publisher styling proportions
        assertTrue(
            preferences.publisherStyles ?: false,
            "publisherStyles must be explicitly true so ReadiumCSS honors author heading scale and layout styling"
        )
        assertNull(
            preferences.typeScale,
            "typeScale must be null so Readium does not distort EPUB original heading scale"
        )

        // Font size scaled relative to 16sp
        assertEquals(1.25, preferences.fontSize ?: 0.0, 0.01)

        // Line height applied
        assertEquals(1.8, preferences.lineHeight ?: 0.0, 0.01)

        // Horizontal margin scaled
        assertEquals(1.5, preferences.pageMargins ?: 0.0, 0.01)

        // Text alignment justified
        assertEquals(TextAlign.JUSTIFY, preferences.textAlign)

        // Font family set when overrideFont is true
        assertEquals("Cinzel", preferences.fontFamily?.name)

        // Reading progression LTR
        assertEquals(ReadingProgression.LTR, preferences.readingProgression)

        // Single column, no spread
        assertEquals(ColumnCount.ONE, preferences.columnCount)
        assertEquals(Spread.NEVER, preferences.spread)
        assertFalse(preferences.scroll ?: true)
    }

    @Test
    fun testOfficialPreferenceMapping() {
        val preferences = buildReadiumPreferences(
            theme = ReaderTheme(mode = ThemeMode.LIGHT),
            typography = ReaderTypography(
                fontSizeSp = 20f,
                lineHeightMultiplier = 1.8f,
                paddingHorizontal = 24,
                textAlignment = ReaderTypography.TextAlignment.JUSTIFY,
                fontFamily = "Serif",
                overrideFont = true
            ),
            isScrollMode = false,
            isRtl = false
        )

        assertEquals(1.25, preferences.fontSize ?: 0.0, 0.001)
        assertEquals(1.8, preferences.lineHeight ?: 0.0, 0.001)
        assertEquals(1.5, preferences.pageMargins ?: 0.0, 0.001)
        assertEquals(TextAlign.JUSTIFY, preferences.textAlign)
        assertEquals("serif", preferences.fontFamily?.name)
        assertEquals(ReadingProgression.LTR, preferences.readingProgression)
        assertEquals(ColumnCount.ONE, preferences.columnCount)
        assertEquals(Spread.NEVER, preferences.spread)
        assertFalse(preferences.scroll ?: true)
        assertTrue(preferences.publisherStyles ?: false)
        assertNull(preferences.typeScale)
    }

    @Test
    fun testFontOverrideToggle() {
        val disabled = buildReadiumPreferences(
            theme = ReaderTheme(mode = ThemeMode.LIGHT),
            typography = ReaderTypography(fontFamily = "Cinzel", overrideFont = false),
            isScrollMode = false,
            isRtl = false
        )
        assertNull(
            disabled.fontFamily,
            "Disabling the override must yield entirely to publisher fonts"
        )
        assertTrue(disabled.publisherStyles ?: false)

        val enabled = buildReadiumPreferences(
            theme = ReaderTheme(mode = ThemeMode.LIGHT),
            typography = ReaderTypography(fontFamily = "Cinzel", overrideFont = true),
            isScrollMode = false,
            isRtl = false
        )
        assertEquals("Cinzel", enabled.fontFamily?.name)
        assertTrue(enabled.publisherStyles ?: false)
    }

    @Test
    fun testTypographyOverrides_fontOverrideAppliesToAllElementsWhileYieldingWhenDisabled() {
        val defaultTypography = ReaderTypography(fontFamily = "Default", overrideFont = false)
        val defaultPrefs = buildReadiumPreferences(
            theme = ReaderTheme(mode = ThemeMode.LIGHT),
            typography = defaultTypography,
            isScrollMode = false,
            isRtl = false
        )
        // Disabled override yields 100% to publisher fonts
        assertNull(defaultPrefs.fontFamily)
        assertTrue(defaultPrefs.publisherStyles ?: false)

        val overriddenTypography = ReaderTypography(fontFamily = "Amiri", overrideFont = true)
        val overriddenPrefs = buildReadiumPreferences(
            theme = ReaderTheme(mode = ThemeMode.LIGHT),
            typography = overriddenTypography,
            isScrollMode = false,
            isRtl = false
        )
        // Enabled override specifies fontFamily for Readium while publisherStyles = true preserves heading size scale
        assertEquals("Amiri", overriddenPrefs.fontFamily?.name)
        assertTrue(overriddenPrefs.publisherStyles ?: false)
    }

    @Test
    fun testFontSizeScaling_withHardcodedPxAndPtStylesheets() {
        val samplePublisherCss = """
            p {
                font-size: 14px;
                line-height: 1.5;
            }
            h1.chapter-title {
                font-size: 24px;
                text-align: center;
            }
            h2.section-header {
                font-size: 20px;
            }
            span.footnote {
                font-size: 10pt;
            }
            .calibre_1 {
                font-size: 11pt !important;
            }
            hr.divider {
                width: 40%;
                margin-left: auto;
                margin-right: auto;
                border: 1px solid #ccc;
            }
        """.trimIndent()

        // 1. Normalize CSS to replace hardcoded absolute sizes with dynamic calc using --kreader-font-scale
        val normalizedCss = EpubStyleNormalizer.normalizeCssAbsoluteFontSizes(samplePublisherCss)

        assertTrue(normalizedCss.contains("font-size: calc(14px * var(--kreader-font-scale, 1))"))
        assertTrue(normalizedCss.contains("font-size: calc(24px * var(--kreader-font-scale, 1))"))
        assertTrue(normalizedCss.contains("font-size: calc(20px * var(--kreader-font-scale, 1))"))
        assertTrue(normalizedCss.contains("font-size: calc(10pt * var(--kreader-font-scale, 1))"))
        assertTrue(normalizedCss.contains("font-size: calc(11pt * var(--kreader-font-scale, 1)) !important"))

        // Centered <hr> and other properties remain intact
        assertTrue(normalizedCss.contains("margin-left: auto;"))
        assertTrue(normalizedCss.contains("margin-right: auto;"))
        assertTrue(normalizedCss.contains("line-height: 1.5;"))
        assertTrue(normalizedCss.contains("text-align: center;"))

        // 2. Test scaling calculations at 20sp (scale 1.25)
        val scaleUp = 20f / 16f
        val scaledP = EpubStyleNormalizer.computeScaledFontSize("14px", scaleUp)
        val scaledH1 = EpubStyleNormalizer.computeScaledFontSize("24px", scaleUp)
        val scaledH2 = EpubStyleNormalizer.computeScaledFontSize("20px", scaleUp)
        val scaledFootnote = EpubStyleNormalizer.computeScaledFontSize("10pt", scaleUp)
        val scaledCalibre = EpubStyleNormalizer.computeScaledFontSize("11pt", scaleUp)

        assertEquals("17.5px", scaledP)
        assertEquals("30px", scaledH1)
        assertEquals("25px", scaledH2)
        assertEquals("12.5pt", scaledFootnote)
        assertEquals("13.75pt", scaledCalibre)

        // Verify proportional heading-to-body ratio is strictly preserved
        val originalRatio = 24f / 14f
        val scaledRatio = 30f / 17.5f
        assertEquals(originalRatio, scaledRatio, 0.001f)

        // 3. Test scaling calculations at 12sp (scale 0.75)
        val scaleDown = 12f / 16f
        assertEquals("10.5px", EpubStyleNormalizer.computeScaledFontSize("14px", scaleDown))
        assertEquals("18px", EpubStyleNormalizer.computeScaledFontSize("24px", scaleDown))
        val scaledDownRatio = 18f / 10.5f
        assertEquals(originalRatio, scaledDownRatio, 0.001f)
    }

    @Test
    fun testFontSizeScaling_normalizationScriptIncludesStyleSheetAndInlineDOMTraversal() {
        val script = EpubStyleNormalizer.buildNormalizationScript(1.25f)
        assertTrue(script.contains("--kreader-font-scale"))
        assertTrue(script.contains("document.styleSheets"))
        assertTrue(script.contains("processRules"))
        assertTrue(script.contains("querySelectorAll('[style*=\"font-size\"]')"))
        assertTrue(script.contains("calc("))
    }

    @Test
    fun testGenericFontFamiliesUseCanonicalCssNames() {
        val cases = mapOf(
            "Serif" to "serif",
            "Sans-Serif" to "sans-serif",
            "Monospace" to "monospace",
            "Cursive" to "cursive",
            "Fantasy" to "fantasy",
            "OpenDyslexic" to "OpenDyslexic",
            "Amiri" to "Amiri"
        )

        cases.forEach { (input, expected) ->
            val family = readiumFontFamily(
                ReaderTypography(fontFamily = input, overrideFont = true)
            )
            assertEquals(expected, family?.name, "Unexpected mapping for $input")
        }

        assertNull(readiumFontFamily(ReaderTypography(fontFamily = "Default", overrideFont = true)))
        assertNull(readiumFontFamily(ReaderTypography(fontFamily = "Serif", overrideFont = false)))
    }

    @Test
    fun testArabicRtlProgressionAndPublisherAlignment() {
        val preferences = buildReadiumPreferences(
            theme = ReaderTheme(mode = ThemeMode.LIGHT),
            typography = ReaderTypography(
                fontSizeSp = 18f,
                paddingHorizontal = 16,
                textAlignment = ReaderTypography.TextAlignment.DEFAULT,
                fontFamily = "Amiri",
                overrideFont = true
            ),
            isScrollMode = true,
            isRtl = true
        )

        assertEquals(ReadingProgression.RTL, preferences.readingProgression)
        assertNull(
            preferences.textAlign,
            "DEFAULT alignment must leave publisher and RTL flow untouched"
        )
        assertEquals("Amiri", preferences.fontFamily?.name)
        assertTrue(preferences.scroll ?: false)
    }

    @Test
    fun testThemeColorsAcrossAllModes() {
        val light = buildReadiumPreferences(
            ReaderTheme(mode = ThemeMode.LIGHT), ReaderTypography(), false, false
        )
        assertEquals(Color(0xFFFFFFFF.toInt()), light.backgroundColor)
        assertEquals(Color(0xFF1A1A1A.toInt()), light.textColor)

        val systemLight = buildReadiumPreferences(
            ReaderTheme(mode = ThemeMode.SYSTEM_LIGHT), ReaderTypography(), false, false,
            isSystemInDarkTheme = true
        )
        assertEquals(Color(0xFFFAFAFA.toInt()), systemLight.backgroundColor)
        assertEquals(Color(0xFF1A1A1A.toInt()), systemLight.textColor)

        val systemDark = buildReadiumPreferences(
            ReaderTheme(mode = ThemeMode.SYSTEM), ReaderTypography(), false, false,
            isSystemInDarkTheme = true
        )
        assertEquals(Color(0xFF121212.toInt()), systemDark.backgroundColor)
        assertEquals(Color(0xFFE6E6E6.toInt()), systemDark.textColor)

        val sepia = buildReadiumPreferences(
            ReaderTheme(mode = ThemeMode.SEPIA), ReaderTypography(), false, false
        )
        assertEquals(Color(0xFFFBF0D9.toInt()), sepia.backgroundColor)
        assertEquals(Color(0xFF3B2F2F.toInt()), sepia.textColor)

        val dark = buildReadiumPreferences(
            ReaderTheme(mode = ThemeMode.DARK, backgroundColorHex = "#121212"),
            ReaderTypography(), false, false
        )
        assertEquals(Color(0xFF121212.toInt()), dark.backgroundColor)
        assertEquals(Color(0xFFE6E6E6.toInt()), dark.textColor)

        val amoled = buildReadiumPreferences(
            ReaderTheme(mode = ThemeMode.DARK, backgroundColorHex = "#000000"),
            ReaderTypography(), false, false
        )
        assertEquals(Color(0xFF000000.toInt()), amoled.backgroundColor)
        assertEquals(Color(0xFFDCDCDC.toInt()), amoled.textColor)

        val custom = buildReadiumPreferences(
            ReaderTheme(
                mode = ThemeMode.CUSTOM,
                backgroundColorHex = "#102030",
                textColorHex = "#F0E0D0"
            ),
            ReaderTypography(), false, false
        )
        assertEquals(Color(0xFF102030.toInt()), custom.backgroundColor)
        assertEquals(Color(0xFFF0E0D0.toInt()), custom.textColor)
    }

    @Test
    fun testPageMarginsAreClampedToSupportedRange() {
        val tight = buildReadiumPreferences(
            ReaderTheme(mode = ThemeMode.LIGHT),
            ReaderTypography(paddingHorizontal = 0),
            false,
            false
        )
        assertEquals(0.0, tight.pageMargins ?: -1.0, 0.001)

        val wide = buildReadiumPreferences(
            ReaderTheme(mode = ThemeMode.LIGHT),
            ReaderTypography(paddingHorizontal = 200),
            false,
            false
        )
        assertEquals(3.0, wide.pageMargins ?: -1.0, 0.001)
    }

    @Test
    fun testBookmarkMatchingUsesAuthoritativePosition() {
        val bookmarks = listOf(
            bookmark(id = "a", href = "chapter1.xhtml", position = 4, progression = 0.1f),
            bookmark(id = "b", href = "chapter2.xhtml", position = 12, progression = 0.2f)
        )

        assertEquals(
            "a",
            findMatchingBookmark(
                bookmarks,
                Locator(
                    href = "chapter1.xhtml",
                    type = "application/xhtml+xml",
                    locations = Locator.Locations(position = 4, progression = 0.9f)
                ),
                totalProgression = 0.9f
            )?.id
        )

        assertEquals(
            "b",
            findMatchingBookmark(
                bookmarks,
                Locator(
                    href = "chapter2.xhtml",
                    type = "application/xhtml+xml",
                    locations = Locator.Locations(position = 12, progression = 0.95f)
                ),
                totalProgression = 0.95f
            )?.id
        )

        assertNull(
            findMatchingBookmark(
                bookmarks,
                Locator(
                    href = "chapter1.xhtml",
                    type = "application/xhtml+xml",
                    locations = Locator.Locations(position = 9)
                ),
                totalProgression = 0.5f
            )
        )
    }

    @Test
    fun testBookmarkMatchingFallsBackToChapterProgression() {
        val bookmarks = listOf(
            bookmark(id = "a", href = "chapter1.xhtml", position = null, progression = 0.42f)
        )
        val current = Locator(
            href = "chapter1.xhtml#fragment",
            type = "application/xhtml+xml",
            locations = Locator.Locations(progression = 0.44f)
        )

        assertNotNull(findMatchingBookmark(bookmarks, current, totalProgression = 0.6f))
    }

    @Test
    fun testFindTocTitleForHrefMatchesNestedChaptersAndFragments() {
        val toc = listOf(
            TocItem(
                title = "Part I",
                locator = Locator(href = "OEBPS/part1.xhtml", type = "application/xhtml+xml"),
                children = listOf(
                    TocItem(
                        title = "Chapter 1: Dawn",
                        locator = Locator(href = "OEBPS/ch1.xhtml#sec1", type = "application/xhtml+xml")
                    )
                )
            )
        )

        assertEquals("Chapter 1: Dawn", findTocTitleForHref(toc, "/OEBPS/ch1.xhtml#para3"))
        assertEquals("Part I", findTocTitleForHref(toc, "OEBPS/part1.xhtml"))
        assertNull(findTocTitleForHref(toc, "OEBPS/missing.xhtml"))
    }

    @Test
    fun testCustomArabicAndUnicodeFontsGenerateUnifiedFontFaceAndOverrideCss() {
        val arabicFont = CustomFont(
            name = "أميري للكتب",
            path = "/data/user/0/io.github.kreader/files/fonts/أميري للكتب.ttf"
        )
        val woff2Font = CustomFont(
            name = "Kitab Naskh",
            path = "/data/user/0/io.github.kreader/files/fonts/Kitab Naskh.woff2"
        )
        val fonts = listOf(arabicFont, woff2Font)

        val arabicCssName = ReadiumFontResources.cssFamilyName(arabicFont)
        val arabicUrl = ReadiumFontResources.url(arabicFont).toString()
        assertTrue(arabicCssName.startsWith("kreader-font-"))
        assertTrue(arabicCssName.all { it.code < 128 && !it.isWhitespace() })
        assertTrue(arabicUrl.startsWith("https://readium/publication/kreader-fonts/"))
        assertTrue(arabicUrl.endsWith(".ttf"))

        val typography = ReaderTypography(
            fontFamily = "أميري للكتب",
            overrideFont = true
        )

        val prefs = buildReadiumPreferences(
            theme = ReaderTheme(mode = ThemeMode.LIGHT),
            typography = typography,
            isScrollMode = false,
            isRtl = true,
            customFonts = fonts
        )
        assertEquals(arabicCssName, prefs.fontFamily?.name)

        val css = buildFontOverrideCss(typography, fonts)
        assertNotNull(css)
        assertTrue(css.contains("@font-face { font-family: '$arabicCssName';"))
        assertTrue(css.contains("src: url('$arabicUrl') format('truetype');"))
        assertTrue(css.contains("font-weight: 100 900; font-style: normal;"))
        assertTrue(css.contains("font-weight: 100 900; font-style: italic;"))
        assertTrue(css.contains("body, h1, h2, h3, h4, h5, h6,"))
        assertTrue(css.contains("font-family: '$arabicCssName', sans-serif !important;"))

        val woff2Css = buildFontOverrideCss(
            ReaderTypography(fontFamily = "Kitab Naskh", overrideFont = true),
            fonts
        )
        assertNotNull(woff2Css)
        assertTrue(woff2Css.contains("format('woff2')"))
        assertTrue(woff2Css.contains(ReadiumFontResources.cssFamilyName(woff2Font)))

        val script = buildFontOverrideScript(typography, fonts)
        assertTrue(script.contains("kreader-font-override"))
        assertTrue(script.contains("DOMContentLoaded"))
        assertTrue(script.contains(arabicCssName))

        val disabledTypography = typography.copy(overrideFont = false)
        assertNull(buildFontOverrideCss(disabledTypography, fonts))
        assertTrue(buildFontOverrideScript(disabledTypography, fonts).contains("el.remove()"))
    }

    private fun bookmark(
        id: String,
        href: String,
        position: Int?,
        progression: Float
    ): Bookmark = Bookmark(
        id = id,
        bookId = "book",
        locator = Locator(
            href = href,
            type = "application/xhtml+xml",
            locations = Locator.Locations(position = position, progression = progression)
        ),
        title = "Chapter",
        createdTimestamp = 0L
    )
}
