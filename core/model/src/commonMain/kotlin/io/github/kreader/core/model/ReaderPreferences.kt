package io.github.kreader.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class PageLayout {
    SINGLE,
    DOUBLE
}

@Serializable
enum class OrientationLock {
    AUTO,
    PORTRAIT,
    LANDSCAPE
}

@Serializable
data class CustomFont(
    val name: String,
    val path: String
)

@Serializable
data class ReaderPreferences(
    val theme: ReaderTheme = ReaderTheme(),
    val typography: ReaderTypography = ReaderTypography(paddingHorizontal = 16, paddingVertical = 0, lineHeightMultiplier = 1.6f),
    val isScrollMode: Boolean = false,
    val pageLayout: PageLayout = PageLayout.SINGLE,
    val pageMargins: Float = 0f,
    val brightness: Float = 1.0f,
    val volumeKeyNavigation: Boolean = true,
    val customFonts: List<CustomFont> = emptyList(),
    val keepScreenOn: Boolean = false,
    val orientationLock: OrientationLock = OrientationLock.AUTO
)

@Serializable
data class ReaderTypographyOverride(
    val fontFamily: String? = null,
    val fontSizeSp: Float? = null,
    val lineHeightMultiplier: Float? = null,
    val letterSpacingSp: Float? = null,
    val textAlignment: ReaderTypography.TextAlignment? = null,
    val paddingHorizontal: Int? = null,
    val paddingVertical: Int? = null,
    val overrideFont: Boolean? = null
) {
    fun isEmpty(): Boolean =
        fontFamily == null &&
        fontSizeSp == null &&
        lineHeightMultiplier == null &&
        letterSpacingSp == null &&
        textAlignment == null &&
        paddingHorizontal == null &&
        paddingVertical == null &&
        overrideFont == null
}

@Serializable
data class ReaderPreferencesOverride(
    val theme: ReaderTheme? = null,
    val typography: ReaderTypographyOverride? = null,
    val isScrollMode: Boolean? = null,
    val pageLayout: PageLayout? = null,
    val pageMargins: Float? = null,
    val brightness: Float? = null,
    val volumeKeyNavigation: Boolean? = null,
    val keepScreenOn: Boolean? = null,
    val orientationLock: OrientationLock? = null
) {
    fun isEmpty(): Boolean =
        theme == null &&
        (typography == null || typography.isEmpty()) &&
        isScrollMode == null &&
        pageLayout == null &&
        pageMargins == null &&
        brightness == null &&
        volumeKeyNavigation == null &&
        keepScreenOn == null &&
        orientationLock == null
}

fun ReaderPreferences.mergeWith(override: ReaderPreferencesOverride?): ReaderPreferences {
    if (override == null) return this
    val mergedTypography = if (override.typography == null) {
        this.typography
    } else {
        this.typography.copy(
            fontFamily = override.typography.fontFamily ?: this.typography.fontFamily,
            fontSizeSp = override.typography.fontSizeSp ?: this.typography.fontSizeSp,
            lineHeightMultiplier = override.typography.lineHeightMultiplier ?: this.typography.lineHeightMultiplier,
            letterSpacingSp = override.typography.letterSpacingSp ?: this.typography.letterSpacingSp,
            textAlignment = override.typography.textAlignment ?: this.typography.textAlignment,
            paddingHorizontal = override.typography.paddingHorizontal ?: this.typography.paddingHorizontal,
            paddingVertical = override.typography.paddingVertical ?: this.typography.paddingVertical,
            overrideFont = override.typography.overrideFont ?: this.typography.overrideFont
        )
    }
    return this.copy(
        theme = override.theme ?: this.theme,
        typography = mergedTypography,
        isScrollMode = override.isScrollMode ?: this.isScrollMode,
        pageLayout = override.pageLayout ?: this.pageLayout,
        pageMargins = override.pageMargins ?: this.pageMargins,
        brightness = override.brightness ?: this.brightness,
        volumeKeyNavigation = override.volumeKeyNavigation ?: this.volumeKeyNavigation,
        keepScreenOn = override.keepScreenOn ?: this.keepScreenOn,
        orientationLock = override.orientationLock ?: this.orientationLock
    )
}
