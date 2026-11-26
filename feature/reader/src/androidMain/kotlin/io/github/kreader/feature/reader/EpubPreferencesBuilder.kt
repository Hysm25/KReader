package io.github.kreader.feature.reader

import io.github.kreader.core.model.CustomFont
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ReaderTypography
import io.github.kreader.core.model.ThemeMode
import io.github.kreader.engine.epub.ReadiumFontResources
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.navigator.preferences.Spread
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.shared.ExperimentalReadiumApi

/**
 * Maps KReader's reading preferences to Readium [EpubPreferences].
 *
 * Keeps publisher styles authoritative (`publisherStyles = true`, `typeScale = null`) so the EPUB's
 * original CSS (heading proportions h1–h6, centered <hr> dividers, blockquotes, and Arabic RTL layout)
 * renders faithfully without artificial scale distortion. User typography overrides (font size,
 * override font, line height, text alignment, margins) are scaled and applied reliably across all publications.
 */
@OptIn(ExperimentalReadiumApi::class)
fun buildReadiumPreferences(
    theme: ReaderTheme,
    typography: ReaderTypography,
    isScrollMode: Boolean,
    isRtl: Boolean,
    isSystemInDarkTheme: Boolean = false,
    customFonts: List<CustomFont> = emptyList()
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
        fontFamily = readiumFontFamily(typography, customFonts),
        textAlign = textAlign,
        fontSize = (typography.fontSizeSp / 16.0).toDouble().coerceIn(0.5, 3.0),
        lineHeight = typography.lineHeightMultiplier.toDouble().coerceIn(1.0, 3.0),
        columnCount = ColumnCount.ONE,
        spread = Spread.NEVER,
        pageMargins = pageMargins,
        scroll = isScrollMode,
        readingProgression = if (isRtl) ReadingProgression.RTL else ReadingProgression.LTR,
        ligatures = true,
        publisherStyles = true,
        typeScale = null
    )
}

/**
 * Resolves the font family that Readium CSS should enforce.
 *
 * Returns `null` when the override is disabled so that publisher fonts are left untouched, which
 * makes the toggle yield 100% to the book. Built-in families and generic CSS families are passed
 * through by their canonical names; imported families are declared by the EPUB navigator
 * configuration.
 */
@OptIn(ExperimentalReadiumApi::class)
fun readiumFontFamily(
    typography: ReaderTypography,
    customFonts: List<CustomFont> = emptyList()
): FontFamily? {
    if (!typography.overrideFont) return null

    val name = typography.fontFamily.trim()
    if (name.isEmpty() || name.equals("Default", ignoreCase = true)) return null

    val importedFont = customFonts.find { it.name.equals(name, ignoreCase = true) }
    if (importedFont != null) {
        return FontFamily(ReadiumFontResources.cssFamilyName(importedFont))
    }

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

/**
 * Builds the `#kreader-font-override` CSS payload for WebView injection.
 *
 * Returns `null` when `overrideFont` is disabled or set to `"Default"` so embedded publisher
 * fonts remain untouched. For imported custom fonts, emits `@font-face` rules covering weights
 * `100 900` across both `normal` and `italic` styles using [ReadiumFontResources.cssFamilyName]
 * and [ReadiumFontResources.url] so headings, bold spans, and Arabic body text never fall back
 * to Android system fonts.
 */
fun buildFontOverrideCss(
    typography: ReaderTypography,
    customFonts: List<CustomFont>
): String? {
    if (!typography.overrideFont) return null
    val rawName = typography.fontFamily.trim()
    if (rawName.isEmpty() || rawName.equals("Default", ignoreCase = true)) return null

    val customFont = customFonts.find { it.name.equals(rawName, ignoreCase = true) }
    val (fontFaceCss, fontCssValue) = if (customFont != null) {
        val cssFamily = ReadiumFontResources.cssFamilyName(customFont)
        val fontUrl = ReadiumFontResources.url(customFont).toString()
        val formatHint = ReadiumFontResources.cssFormatHint(customFont)
        val face = buildString {
            append("@font-face { font-family: '").append(cssFamily).append("'; ")
            append("src: url('").append(fontUrl).append("') format('").append(formatHint).append("'); ")
            append("font-weight: 100 900; font-style: normal; font-display: swap; }\n")
            append("@font-face { font-family: '").append(cssFamily).append("'; ")
            append("src: url('").append(fontUrl).append("') format('").append(formatHint).append("'); ")
            append("font-weight: 100 900; font-style: italic; font-display: swap; }\n")
        }
        face to "'$cssFamily', sans-serif !important"
    } else {
        val escaped = rawName
            .replace("\\", "\\\\")
            .replace("'", "\\'")
        val cssVal = when (rawName.lowercase()) {
            "serif" -> "serif !important"
            "sans-serif", "sans serif" -> "sans-serif !important"
            "monospace" -> "monospace !important"
            "cursive" -> "cursive !important"
            "fantasy" -> "fantasy !important"
            "accessible dfa", "accessibledfa" -> "'Accessible-DFA', sans-serif !important"
            "ia writer duospace", "iawriterduospace" -> "'IA Writer Duospace', monospace !important"
            "opendyslexic" -> "'OpenDyslexic', sans-serif !important"
            else -> "'$escaped', sans-serif !important"
        }
        "" to cssVal
    }

    return "${fontFaceCss}body, h1, h2, h3, h4, h5, h6, p, div, span, li, blockquote, a, em, strong, i, b, td, th, figcaption { font-family: $fontCssValue; }"
}

/**
 * Wraps [buildFontOverrideCss] in an idempotent DOM script that installs or removes
 * `<style id="kreader-font-override">` (re-applying on `DOMContentLoaded` if still loading).
 */
fun buildFontOverrideScript(
    typography: ReaderTypography,
    customFonts: List<CustomFont>
): String {
    val css = buildFontOverrideCss(typography, customFonts) ?: return """
        (function() {
            var el = document.getElementById('kreader-font-override');
            if (el) el.remove();
        })();
    """.trimIndent()

    val jsLiteral = css
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "")

    return """
        (function() {
            function applyKReaderFontOverride() {
                var el = document.getElementById('kreader-font-override');
                if (!el) {
                    el = document.createElement('style');
                    el.id = 'kreader-font-override';
                    (document.head || document.documentElement).appendChild(el);
                }
                el.textContent = "$jsLiteral";
            }
            applyKReaderFontOverride();
            if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', applyKReaderFontOverride, { once: true });
            }
        })();
    """.trimIndent()
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
