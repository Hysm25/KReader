package io.github.kreader.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ReaderPreferences(
    val theme: ReaderTheme = ReaderTheme(),
    val typography: ReaderTypography = ReaderTypography(lineHeightMultiplier = 1.6f),
    val isScrollMode: Boolean = false,
    val pageMargins: Float = 16f,
    val brightness: Float = 1.0f,
    val volumeKeyNavigation: Boolean = true
)
