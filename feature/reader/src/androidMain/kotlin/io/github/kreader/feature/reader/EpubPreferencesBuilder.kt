package io.github.kreader.feature.reader

import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ReaderTypography
import io.github.kreader.core.model.ThemeMode
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.navigator.preferences.Spread
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.shared.ExperimentalReadiumApi

/**
 * Maps KReader's reading preferences to the official Readium [EpubPreferences].
 *
 * Readium CSS applies user typography with two independent flags:
 *
 * - `publisherStyles = true` keeps author stylesheets authoritative but disables *all* advanced
 *   user settings in the bundled Readium CSS 3.0 modules, including `--USER__lineHeight`,
 *   `--USER__textAlign` and `--USER__typeScale`. Author stylesheets are still injected either way,
 *   so disabling `publisherStyles` does not remove publisher CSS: it only lets the reading system
 *   take control of typography.
 * - `publisherStyles = false` (`advancedSettings = true`) is therefore required to make the
 *   continuous line-height and alignment controls functional. A stable [typeScale] keeps `h1`-`h6`
 *   proportional and hierarchical at every font size, while centered `<hr>`, RTL flow, images and
 *   every other author rule remain untouched.
 *
 * Font size is *not* delegated to Readium CSS: the navigator is configured with
 * `useReadiumCssFontSize = false` so `WebSettings.textZoom` scales fixed `px`/`pt` stylesheets too.
 */
@OptIn(ExperimentalReadiumApi::class)
fun buildReadiumPreferences(
    theme: ReaderTheme,
    typography: ReaderTypography,
    isScrollMode: Boolean,
    isRtl: Boolean,
    isSystemInDarkTheme: Boolean = false
): EpubPreferences {
    val backgroundColor = when (theme.mode) {
        ThemeMode.LIGHT -> Color(0xFFFFFFFF.toInt())
        ThemeMode.DARK -> if (theme.backgroundColorHex.equals("#000000", ignoreCase = true)) {
            Color(0xFF000000.toInt()) // AMOLED Black
        } else {
            Color(0xFF121212.toInt()) // Neutral Dark
        }
        ThemeMode.SEPIA -> Color(0xFFFBF0D9.toInt())
        ThemeMode.SYSTEM -> if (isSystemInDarkTheme) {
            Color(0xFF121212.toInt())
        } else {
            Color(0xFFFAFAFA.toInt())
        }
        ThemeMode.SYSTEM_LIGHT -> Color(0xFFFAFAFA.toInt())
        ThemeMode.CUSTOM -> Color(parseHexColorInt(theme.backgroundColorHex, 0xFFFFFFFF.toInt()))
    }

    val textColor = when (theme.mode) {
        ThemeMode.LIGHT -> Color(0xFF1A1A1A.toInt())
        ThemeMode.DARK -> if (theme.backgroundColorHex.equals("#000000", ignoreCase = true)) {
            Color(0xFFDCDCDC.toInt())
        } else {
            Color(0xFFE6E6E6.toInt())
        }
        ThemeMode.SEPIA -> Color(0xFF3B2F2F.toInt())
        ThemeMode.SYSTEM -> if (isSystemInDarkTheme) {
            Color(0xFFE6E6E6.toInt())
        } else {
            Color(0xFF1A1A1A.toInt())
        }
        ThemeMode.SYSTEM_LIGHT -> Color(0xFF1A1A1A.toInt())
        ThemeMode.CUSTOM -> Color(parseHexColorInt(theme.textColorHex, 0xFF1A1A1A.toInt()))
    }

    val textAlign = when (typography.textAlignment) {
        ReaderTypography.TextAlignment.JUSTIFY -> TextAlign.JUSTIFY
        ReaderTypography.TextAlignment.START -> TextAlign.START
        ReaderTypography.TextAlignment.DEFAULT -> null
    }

    // Readium's page margins are expressed as a factor of the root em gutter.
    val pageMargins = (typography.paddingHorizontal.toDouble() / 16.0).coerceIn(0.0, 3.0)

    return EpubPreferences(
        backgroundColor = backgroundColor,
        textColor = textColor,
        fontFamily = readiumFontFamily(typography),
        textAlign = textAlign,
        fontSize = (typography.fontSizeSp / 16.0).toDouble().coerceIn(0.5, 3.0),
        lineHeight = typography.lineHeightMultiplier.toDouble().coerceIn(1.0, 3.0),
        columnCount = ColumnCount.ONE,
        spread = Spread.NEVER,
        pageMargins = pageMargins,
        scroll = isScrollMode,
        readingProgression = if (isRtl) ReadingProgression.RTL else ReadingProgression.LTR,
        ligatures = true,
        publisherStyles = false,
        typeScale = HEADING_TYPE_SCALE
    )
}

/**
 * Ratio between consecutive heading levels when the reading system owns typography.
 *
 * Readium CSS derives `h3`, `h2` and `h1` from the body size with this ratio, so headings stay
 * proportional to the text at every font size instead of collapsing to the body size.
 */
private const val HEADING_TYPE_SCALE = 1.25

/**
 * Resolves the font family that Readium CSS should enforce.
 *
 * Returns `null` when the override is disabled so that publisher fonts are left untouched, which
 * makes the toggle yield 100% to the book. Built-in families and generic CSS families are passed
 * through by their canonical names; imported families are declared by the EPUB navigator
 * configuration.
 */
@OptIn(ExperimentalReadiumApi::class)
fun readiumFontFamily(typography: ReaderTypography): FontFamily? {
    if (!typography.overrideFont) return null

    val name = typography.fontFamily.trim()
    if (name.isEmpty() || name.equals("Default", ignoreCase = true)) return null

    return FontFamily(
        when (name.lowercase()) {
            "serif" -> FontFamily.SERIF.name
            "sans-serif", "sans serif" -> FontFamily.SANS_SERIF.name
            "monospace" -> FontFamily.MONOSPACE.name
            "cursive" -> FontFamily.CURSIVE.name
            "fantasy" -> FontFamily.FANTASY.name
            "accessible dfa", "accessibledfa" -> FontFamily.ACCESSIBLE_DFA.name
            "ia writer duospace", "iawriterduospace" -> FontFamily.IA_WRITER_DUOSPACE.name
            "opendyslexic" -> FontFamily.OPEN_DYSLEXIC.name
            else -> name
        }
    )
}

private fun parseHexColorInt(hex: String, fallback: Int): Int {
    return runCatching {
        val clean = hex.removePrefix("#").trim()
        val parsed = clean.toLong(16)
        if (clean.length <= 6) {
            (0xFF000000 or parsed).toInt()
        } else {
            parsed.toInt()
        }
    }.getOrDefault(fallback)
}
