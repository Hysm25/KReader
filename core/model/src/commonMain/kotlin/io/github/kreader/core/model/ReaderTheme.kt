package io.github.kreader.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode {
    LIGHT,
    DARK,
    SEPIA,
    SYSTEM
}

@Serializable
data class ReaderTheme(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val backgroundColorHex: String = "#FFFFFF",
    val textColorHex: String = "#111111",
    val accentColorHex: String = "#3B82F6"
)

@Serializable
data class ReaderTypography(
    val fontFamily: String = "Default",
    val fontSizeSp: Float = 16f,
    val lineHeightMultiplier: Float = 1.4f,
    val letterSpacingSp: Float = 0f,
    val textAlignment: TextAlignment = TextAlignment.JUSTIFY
) {
    @Serializable
    enum class TextAlignment {
        START,
        CENTER,
        END,
        JUSTIFY
    }
}
