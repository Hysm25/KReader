package io.github.kreader.feature.reader

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ThemeMode

val PureLightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF1F1F1F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5E5E5),
    onPrimaryContainer = Color(0xFF141414),
    secondary = Color(0xFF4A4A4A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEBEBEB),
    onSecondaryContainer = Color(0xFF262626),
    tertiary = Color(0xFF5E5E5E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF0F0F0),
    onTertiaryContainer = Color(0xFF2B2B2B),
    surface = Color(0xFFFAFAFA),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFF4A4A4A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F7F7),
    surfaceContainer = Color(0xFFF2F2F2),
    surfaceContainerHigh = Color(0xFFEDEDED),
    surfaceContainerHighest = Color(0xFFE5E5E5),
    inverseSurface = Color(0xFF1A1A1A),
    inverseOnSurface = Color(0xFFF5F5F5),
    inversePrimary = Color(0xFFD6D6D6),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1A1A1A),
    outline = Color(0xFFD6D6D6),
    outlineVariant = Color(0xFFE8E8E8)
)

val SepiaColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF7B5E3C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADBBE),
    onPrimaryContainer = Color(0xFF382A15),
    secondary = Color(0xFF6B533E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6D7BD),
    onSecondaryContainer = Color(0xFF2C2013),
    tertiary = Color(0xFF826244),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEDDFCC),
    onTertiaryContainer = Color(0xFF322316),
    surface = Color(0xFFF4ECD8),
    onSurface = Color(0xFF3B2F2F),
    surfaceVariant = Color(0xFFE8DBC0),
    onSurfaceVariant = Color(0xFF5C4A3A),
    surfaceContainerLowest = Color(0xFFFCF6E8),
    surfaceContainerLow = Color(0xFFF1E7CF),
    surfaceContainer = Color(0xFFEBE0C5),
    surfaceContainerHigh = Color(0xFFE5D8BC),
    surfaceContainerHighest = Color(0xFFDFD1B2),
    inverseSurface = Color(0xFF3B2F2F),
    inverseOnSurface = Color(0xFFF4ECD8),
    inversePrimary = Color(0xFFDFD1B2),
    background = Color(0xFFFBF0D9),
    onBackground = Color(0xFF3B2F2F),
    outline = Color(0xFFD4C2A3),
    outlineVariant = Color(0xFFE4D5BC)
)

val SoftDarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFE0E0E0),
    onPrimary = Color(0xFF121212),
    primaryContainer = Color(0xFF2D2D2D),
    onPrimaryContainer = Color(0xFFE6E6E6),
    secondary = Color(0xFFC4C4C4),
    onSecondary = Color(0xFF1E1E1E),
    secondaryContainer = Color(0xFF363636),
    onSecondaryContainer = Color(0xFFE0E0E0),
    tertiary = Color(0xFFADADAD),
    onTertiary = Color(0xFF1E1E1E),
    tertiaryContainer = Color(0xFF2F2F2F),
    onTertiaryContainer = Color(0xFFD6D6D6),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE6E6E6),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFA8A8A8),
    surfaceContainerLowest = Color(0xFF0F0F0F),
    surfaceContainerLow = Color(0xFF171717),
    surfaceContainer = Color(0xFF202020),
    surfaceContainerHigh = Color(0xFF282828),
    surfaceContainerHighest = Color(0xFF323232),
    inverseSurface = Color(0xFFE6E6E6),
    inverseOnSurface = Color(0xFF1E1E1E),
    inversePrimary = Color(0xFF383838),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE6E6E6),
    outline = Color(0xFF383838),
    outlineVariant = Color(0xFF2C2C2C)
)

val AmoledColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFE0E0E0),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF1F1F1F),
    onPrimaryContainer = Color(0xFFDCDCDC),
    secondary = Color(0xFFB8B8B8),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF262626),
    onSecondaryContainer = Color(0xFFDCDCDC),
    tertiary = Color(0xFFA3A3A3),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF1E1E1E),
    onTertiaryContainer = Color(0xFFCCCCCC),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFDCDCDC),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFF9E9E9E),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF141414),
    surfaceContainerHigh = Color(0xFF1C1C1C),
    surfaceContainerHighest = Color(0xFF242424),
    inverseSurface = Color(0xFFDCDCDC),
    inverseOnSurface = Color(0xFF121212),
    inversePrimary = Color(0xFF282828),
    background = Color(0xFF000000),
    onBackground = Color(0xFFDCDCDC),
    outline = Color(0xFF333333),
    outlineVariant = Color(0xFF222222)
)

@Composable
expect fun getReaderColorScheme(theme: ReaderTheme): ColorScheme
