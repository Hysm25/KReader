package io.github.kreader.feature.reader

import io.github.kreader.core.model.Bookmark
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ReaderTypography
import io.github.kreader.core.model.ThemeMode
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
    fun testAdvancedTypographyIsEnabledSoLineHeightAndAlignmentActuallyApply() {
        val preferences = buildReadiumPreferences(
            theme = ReaderTheme(mode = ThemeMode.LIGHT),
            typography = ReaderTypography(
                fontSizeSp = 20f,
                lineHeightMultiplier = 1.8f,
                paddingHorizontal = 24,
                textAlignment = ReaderTypography.TextAlignment.JUSTIFY
            ),
            isScrollMode = false,
            isRtl = false
        )

        // Bundled Readium CSS 3.0 modules gate `--USER__lineHeight`, `--USER__textAlign` and
        // `--USER__typeScale` behind `readium-advanced-on`, which is emitted from
        // `advancedSettings = !publisherStyles`. Keeping publisherStyles enabled would make the
        // line-height and alignment controls silently inert.
        assertEquals(
            false,
            preferences.publisherStyles,
            "Advanced Readium CSS settings are required for live line-height and alignment"
        )
        assertEquals(
            1.25,
            preferences.typeScale ?: 0.0,
            0.001,
            "A stable type scale keeps h1-h6 proportional and hierarchical"
        )
        assertTrue(
            (preferences.lineHeight ?: 0.0) > 1.0,
            "Line height must be forwarded to Readium CSS"
        )
        assertEquals(TextAlign.JUSTIFY, preferences.textAlign)
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

        val enabled = buildReadiumPreferences(
            theme = ReaderTheme(mode = ThemeMode.LIGHT),
            typography = ReaderTypography(fontFamily = "Cinzel", overrideFont = true),
            isScrollMode = false,
            isRtl = false
        )
        assertEquals("Cinzel", enabled.fontFamily?.name)
        assertEquals(false, enabled.publisherStyles)
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
