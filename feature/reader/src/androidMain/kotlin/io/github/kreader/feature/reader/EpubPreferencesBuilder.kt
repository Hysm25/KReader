package io.github.kreader.feature.reader

import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ReaderTypography
import io.github.kreader.core.model.ThemeMode
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalReadiumApi::class)
fun buildReadiumPreferences(
    theme: ReaderTheme,
    typography: ReaderTypography,
    isScrollMode: Boolean,
    isRtl: Boolean
): EpubPreferences {
    val backgroundColor = when (theme.mode) {
        ThemeMode.LIGHT -> Color(0xFFFFFFFF.toInt())
        ThemeMode.DARK -> if (theme.backgroundColorHex.equals("#000000", ignoreCase = true)) {
            Color(0xFF000000.toInt()) // AMOLED Black
        } else {
            Color(0xFF1E1E1E.toInt()) // Soft Dark
        }
        ThemeMode.SEPIA -> Color(0xFFFBF0D9.toInt())
        ThemeMode.SYSTEM -> null
    }

    val textColor = when (theme.mode) {
        ThemeMode.LIGHT -> Color(0xFF121212.toInt())
        ThemeMode.DARK -> Color(0xFFE8E8E8.toInt())
        ThemeMode.SEPIA -> Color(0xFF5F4B32.toInt())
        ThemeMode.SYSTEM -> null
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

    return EpubPreferences(
        backgroundColor = backgroundColor,
        textColor = textColor,
        fontFamily = fontFamily,
        fontSize = (typography.fontSizeSp / 16.0).toDouble().coerceIn(0.6, 2.5),
        lineHeight = (typography.lineHeightMultiplier.toDouble()).coerceAtLeast(1.5),
        scroll = isScrollMode,
        readingProgression = progression,
        ligatures = true,
        publisherStyles = false
    )
}
