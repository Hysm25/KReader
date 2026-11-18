package io.github.kreader.feature.reader

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
    fun testEpubPreferences_publisherStylesDisabledAllowsUserOverrides() {
        val theme = ReaderTheme(mode = ThemeMode.LIGHT)
        val typography = ReaderTypography(
            fontSizeSp = 20f,
            lineHeightMultiplier = 1.8f,
            paddingHorizontal = 24,
            paddingVertical = 16,
            textAlignment = ReaderTypography.TextAlignment.JUSTIFY,
            fontFamily = "Cinzel"
        )

        val preferences = buildReadiumPreferences(
            theme = theme,
            typography = typography,
            isScrollMode = false,
            isRtl = false,
            isSystemInDarkTheme = false
        )

        // publisherStyles MUST be false so ReadiumCSS allows user typography overrides to apply
        assertFalse(
            preferences.publisherStyles ?: true,
            "publisherStyles must be explicitly false so ReadiumCSS honors user typography preferences"
        )

        // Font size scaled relative to 16sp
        assertEquals(1.25, preferences.fontSize ?: 0.0, 0.01)

        // Line height applied
        assertEquals(1.8, preferences.lineHeight ?: 0.0, 0.01)

        // Horizontal margin scaled
        assertEquals(1.5, preferences.pageMargins ?: 0.0, 0.01)

        // Text alignment justified
        assertEquals(TextAlign.JUSTIFY, preferences.textAlign)

        // Font family set
        assertEquals("Cinzel", preferences.fontFamily?.name)

        // Reading progression LTR
        assertEquals(ReadingProgression.LTR, preferences.readingProgression)

        // Single column, no spread
        assertEquals(ColumnCount.ONE, preferences.columnCount)
        assertEquals(Spread.NEVER, preferences.spread)
        assertFalse(preferences.scroll ?: true)
    }

    @Test
    fun testEpubPreferences_arabicRtlProgressionAndDefaultAlignment() {
        val theme = ReaderTheme(mode = ThemeMode.LIGHT)
        val typography = ReaderTypography(
            fontSizeSp = 18f,
            lineHeightMultiplier = 1.5f,
            paddingHorizontal = 16,
            textAlignment = ReaderTypography.TextAlignment.DEFAULT,
            fontFamily = "Amiri"
        )

        val preferences = buildReadiumPreferences(
            theme = theme,
            typography = typography,
            isScrollMode = true,
            isRtl = true,
            isSystemInDarkTheme = false
        )

        // RTL progression configured natively for Readium
        assertEquals(
            ReadingProgression.RTL,
            preferences.readingProgression,
            "Arabic/RTL texts must configure ReadingProgression.RTL natively in EpubPreferences"
        )

        // DEFAULT textAlignment must be null to preserve publisher and RTL default direction
        assertNull(
            preferences.textAlign,
            "DEFAULT text alignment must map to null in EpubPreferences so publisher alignment / RTL flow is not clobbered"
        )

        assertTrue(preferences.scroll ?: false)
    }

    @Test
    fun testEpubPreferences_themeColors() {
        // Light
        val lightPrefs = buildReadiumPreferences(
            theme = ReaderTheme(mode = ThemeMode.LIGHT),
            typography = ReaderTypography(),
            isScrollMode = false,
            isRtl = false
        )
        assertNotNull(lightPrefs.backgroundColor)
        assertNotNull(lightPrefs.textColor)
        assertEquals(Color(0xFFFFFFFF.toInt()), lightPrefs.backgroundColor)
        assertEquals(Color(0xFF1A1A1A.toInt()), lightPrefs.textColor)

        // System Light preset
        val sysLightPrefs = buildReadiumPreferences(
            theme = ReaderTheme(mode = ThemeMode.SYSTEM_LIGHT),
            typography = ReaderTypography(),
            isScrollMode = false,
            isRtl = false,
            isSystemInDarkTheme = true // Even if OS is dark, SYSTEM_LIGHT stays light
        )
        assertEquals(Color(0xFFFAFAFA.toInt()), sysLightPrefs.backgroundColor)
        assertEquals(Color(0xFF1A1A1A.toInt()), sysLightPrefs.textColor)

        // Sepia
        val sepiaPrefs = buildReadiumPreferences(
            theme = ReaderTheme(mode = ThemeMode.SEPIA),
            typography = ReaderTypography(),
            isScrollMode = false,
            isRtl = false
        )
        assertEquals(Color(0xFFFBF0D9.toInt()), sepiaPrefs.backgroundColor)
        assertEquals(Color(0xFF3B2F2F.toInt()), sepiaPrefs.textColor)

        // Dark
        val darkPrefs = buildReadiumPreferences(
            theme = ReaderTheme(mode = ThemeMode.DARK, backgroundColorHex = "#121212"),
            typography = ReaderTypography(),
            isScrollMode = false,
            isRtl = false
        )
        assertEquals(Color(0xFF121212.toInt()), darkPrefs.backgroundColor)
        assertEquals(Color(0xFFE6E6E6.toInt()), darkPrefs.textColor)

        // AMOLED Dark
        val amoledPrefs = buildReadiumPreferences(
            theme = ReaderTheme(mode = ThemeMode.DARK, backgroundColorHex = "#000000"),
            typography = ReaderTypography(),
            isScrollMode = false,
            isRtl = false
        )
        assertEquals(Color(0xFF000000.toInt()), amoledPrefs.backgroundColor)
        assertEquals(Color(0xFFDCDCDC.toInt()), amoledPrefs.textColor)
    }

    @Test
    fun testPublisherLayoutIntegrity_noInjectedStyleSheetInterference() {
        // Assert that default typography uses null text alignment so centered <hr> and publisher headings
        // are not forced into justify/start.
        val defaultTypography = ReaderTypography()
        assertEquals(ReaderTypography.TextAlignment.DEFAULT, defaultTypography.textAlignment)

        val prefs = buildReadiumPreferences(
            theme = ReaderTheme(mode = ThemeMode.LIGHT),
            typography = defaultTypography,
            isScrollMode = false,
            isRtl = false
        )

        // In ReadiumCSS, when textAlign is null and publisherStyles is false,
        // user custom property --USER__textAlign is not set, leaving HTML5/CSS defaults:
        // <hr> maintains margin-left: auto; margin-right: auto; (centered)
        // <h1>-<h6> and blockquotes maintain their semantic publisher styling.
        assertNull(prefs.textAlign)
        assertFalse(prefs.publisherStyles ?: true)
    }
}
