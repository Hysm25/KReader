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
import org.readium.r2.shared.ExperimentalReadiumApi

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
        ThemeMode.CUSTOM -> {
            val parsed = parseHexColorInt(theme.backgroundColorHex, 0xFFFFFFFF.toInt())
            Color(parsed)
        }
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
        ThemeMode.CUSTOM -> {
            val parsed = parseHexColorInt(theme.textColorHex, 0xFF1A1A1A.toInt())
            Color(parsed)
        }
    }

    val progression = if (isRtl) {
        ReadingProgression.RTL
    } else {
        ReadingProgression.LTR
    }

    val fontFamily = if (typography.fontFamily.isBlank() || typography.fontFamily.equals("Default", ignoreCase = true)) {
        null
    } else {
        FontFamily(typography.fontFamily)
    }

    val scaledMargin = (typography.paddingHorizontal.toDouble() / 16.0).coerceIn(0.15, 2.5)

    return EpubPreferences(
        backgroundColor = backgroundColor,
        textColor = textColor,
        fontFamily = fontFamily,
        fontSize = (typography.fontSizeSp / 16.0).toDouble().coerceIn(0.6, 2.5),
        lineHeight = (typography.lineHeightMultiplier.toDouble()).coerceAtLeast(1.5),
        columnCount = ColumnCount.ONE,
        spread = Spread.NEVER,
        pageMargins = scaledMargin,
        scroll = isScrollMode,
        readingProgression = progression,
        ligatures = true,
        publisherStyles = false
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
