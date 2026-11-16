package io.github.kreader.feature.reader

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ThemeMode

@Composable
actual fun getReaderColorScheme(theme: ReaderTheme): ColorScheme {
    val isSystemDark = isSystemInDarkTheme()
    val isAmoled = theme.mode == ThemeMode.DARK && theme.backgroundColorHex.equals("#000000", ignoreCase = true)

    return when (theme.mode) {
        ThemeMode.LIGHT -> PureLightColorScheme
        ThemeMode.SEPIA -> SepiaColorScheme
        ThemeMode.DARK -> if (isAmoled) AmoledColorScheme else SoftDarkColorScheme
        ThemeMode.SYSTEM -> if (isSystemDark) SoftDarkColorScheme else PureLightColorScheme
    }
}
