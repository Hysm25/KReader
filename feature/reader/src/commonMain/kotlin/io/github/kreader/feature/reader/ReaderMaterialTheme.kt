package io.github.kreader.feature.reader

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ThemeMode

@Composable
fun getReaderColorScheme(theme: ReaderTheme): ColorScheme {
    val isAmoled = theme.mode == ThemeMode.DARK && theme.backgroundColorHex.equals("#000000", ignoreCase = true)
    return when (theme.mode) {
        ThemeMode.LIGHT -> lightColorScheme(
            primary = Color(0xFF2563EB),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFDBEAFE),
            onPrimaryContainer = Color(0xFF1E40AF),
            surface = Color(0xFFFAFAFA),
            onSurface = Color(0xFF1E1E1E),
            surfaceVariant = Color(0xFFF0F0F0),
            onSurfaceVariant = Color(0xFF555555),
            background = Color(0xFFFFFFFF),
            onBackground = Color(0xFF121212),
            outline = Color(0xFFE2E8F0),
            outlineVariant = Color(0xFFCBD5E1)
        )
        ThemeMode.SEPIA -> lightColorScheme(
            primary = Color(0xFF8B5E34),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFEADBBE),
            onPrimaryContainer = Color(0xFF4A2E12),
            surface = Color(0xFFF4E8CE),
            onSurface = Color(0xFF4A3B2C),
            surfaceVariant = Color(0xFFEADBBE),
            onSurfaceVariant = Color(0xFF6B5844),
            background = Color(0xFFFBF0D9),
            onBackground = Color(0xFF5F4B32),
            outline = Color(0xFFDCC8A9),
            outlineVariant = Color(0xFFCEB896)
        )
        ThemeMode.DARK -> if (isAmoled) {
            darkColorScheme(
                primary = Color(0xFF60A5FA),
                onPrimary = Color.Black,
                primaryContainer = Color(0xFF1E3A8A),
                onPrimaryContainer = Color(0xFFBFDBFE),
                surface = Color(0xFF121212),
                onSurface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFF1E1E1E),
                onSurfaceVariant = Color(0xFFB0B0B0),
                background = Color(0xFF000000),
                onBackground = Color(0xFFFFFFFF),
                outline = Color(0xFF2E2E2E),
                outlineVariant = Color(0xFF383838)
            )
        } else {
            darkColorScheme(
                primary = Color(0xFF60A5FA),
                onPrimary = Color(0xFF0F172A),
                primaryContainer = Color(0xFF1E3A8A),
                onPrimaryContainer = Color(0xFFBFDBFE),
                surface = Color(0xFF252525),
                onSurface = Color(0xFFE8E8E8),
                surfaceVariant = Color(0xFF2F2F2F),
                onSurfaceVariant = Color(0xFFAAAAAA),
                background = Color(0xFF1E1E1E),
                onBackground = Color(0xFFE8E8E8),
                outline = Color(0xFF3F3F3F),
                outlineVariant = Color(0xFF4A4A4A)
            )
        }
        ThemeMode.SYSTEM -> MaterialTheme.colorScheme
    }
}
