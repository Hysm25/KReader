package io.github.kreader.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class PageLayout {
    SINGLE,
    DOUBLE
}

@Serializable
data class CustomFont(
    val name: String,
    val path: String
)

@Serializable
data class ReaderPreferences(
    val theme: ReaderTheme = ReaderTheme(),
    val typography: ReaderTypography = ReaderTypography(paddingHorizontal = 0, paddingVertical = 0, lineHeightMultiplier = 1.6f),
    val isScrollMode: Boolean = false,
    val pageLayout: PageLayout = PageLayout.SINGLE,
    val pageMargins: Float = 0f,
    val brightness: Float = 1.0f,
    val volumeKeyNavigation: Boolean = true,
    val customFonts: List<CustomFont> = emptyList()
)
