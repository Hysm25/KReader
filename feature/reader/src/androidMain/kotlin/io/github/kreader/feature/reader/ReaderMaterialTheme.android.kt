package io.github.kreader.feature.reader

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ThemeMode

@Composable
actual fun getReaderColorScheme(theme: ReaderTheme): ColorScheme {
    val context = LocalContext.current
    val isSystemDark = isSystemInDarkTheme()
    val isAmoled = theme.mode == ThemeMode.DARK && theme.backgroundColorHex.equals("#000000", ignoreCase = true)

    return when (theme.mode) {
        ThemeMode.LIGHT -> PureLightColorScheme
        ThemeMode.SEPIA -> SepiaColorScheme
        ThemeMode.DARK -> if (isAmoled) AmoledColorScheme else SoftDarkColorScheme
        ThemeMode.SYSTEM -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (isSystemDark) {
                    dynamicDarkColorScheme(context)
                } else {
                    dynamicLightColorScheme(context)
                }
            } else {
                if (isSystemDark) SoftDarkColorScheme else PureLightColorScheme
            }
        }
        ThemeMode.SYSTEM_LIGHT -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicLightColorScheme(context)
            } else {
                PureLightColorScheme
            }
        }
        ThemeMode.CUSTOM -> createCustomColorScheme(theme)
    }
}
