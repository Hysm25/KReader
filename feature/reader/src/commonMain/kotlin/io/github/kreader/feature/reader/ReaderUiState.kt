package io.github.kreader.feature.reader

import androidx.compose.runtime.Immutable
import io.github.kreader.core.engine.ReaderEngineState
import io.github.kreader.core.engine.TocItem
import io.github.kreader.core.model.Book
import io.github.kreader.core.model.Bookmark
import io.github.kreader.core.model.CbzImageFit
import io.github.kreader.core.model.CbzReadingMode
import io.github.kreader.core.model.CustomFont
import io.github.kreader.core.model.CustomTheme
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.OrientationLock
import io.github.kreader.core.model.PageLayout
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ReaderTypography
import kotlin.math.abs

enum class NavigationDrawerTab {
    CONTENTS,
    BOOKMARKS
}

@Immutable
data class ReaderUiState(
    val book: Book? = null,
    val engineState: ReaderEngineState = ReaderEngineState.Idle,
    val isControlsVisible: Boolean = true,
    val isNavigationDrawerOpen: Boolean = false,
    val navigationDrawerTab: NavigationDrawerTab = NavigationDrawerTab.CONTENTS,
    val isSettingsOpen: Boolean = false,
    val isSearchOpen: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<Locator> = emptyList(),
    val isSearching: Boolean = false,
    val isScrollMode: Boolean = false,
    val pageLayout: PageLayout = PageLayout.SINGLE,
    val theme: ReaderTheme = ReaderTheme(),
    val typography: ReaderTypography = ReaderTypography(lineHeightMultiplier = 1.6f),
    val currentLocator: Locator? = null,
    val totalProgression: Float = 0f,
    val isRtl: Boolean = false,
    val tocItems: List<TocItem> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val keepScreenOn: Boolean = false,
    val volumeKeyNavigation: Boolean = true,
    val orientationLock: OrientationLock = OrientationLock.AUTO,
    val customFonts: List<CustomFont> = emptyList(),
    val customThemes: List<CustomTheme> = emptyList(),
    val totalPageCount: Int = 0,
    val cbzReadingMode: CbzReadingMode = CbzReadingMode.COMIC_LTR,
    val cbzImageFit: CbzImageFit = CbzImageFit.FIT_SCREEN
) {
    val isCurrentBookmarked: Boolean
        get() = findMatchingBookmark(bookmarks, currentLocator, totalProgression) != null
}

fun normalizeTocHref(href: String?): String =
    href.orEmpty().substringBefore('#').trimStart('/')

fun findTocTitleForHref(tocItems: List<TocItem>, href: String?): String? {
    val target = normalizeTocHref(href)
    if (target.isBlank()) return null
    for (item in tocItems) {
        if (normalizeTocHref(item.locator.href) == target) {
            return item.title
        }
        val childMatch = findTocTitleForHref(item.children, target)
        if (childMatch != null) return childMatch
    }
    return null
}

fun findMatchingBookmark(
    bookmarks: List<Bookmark>,
    currentLocator: Locator?,
    totalProgression: Float
): Bookmark? {
    if (currentLocator == null) return null
    val currPos = currentLocator.locations.position
    if (currPos != null && currPos > 0) {
        val matchByPos = bookmarks.find { b -> b.locator.locations.position == currPos }
        if (matchByPos != null) return matchByPos
    }
    val currHref = currentLocator.href.substringBefore('#')
    val currProg = currentLocator.locations.progression ?: totalProgression
    return bookmarks.find { b ->
        val bHref = b.locator.href.substringBefore('#')
        if (bHref != currHref) return@find false
        val bPos = b.locator.locations.position
        if (currPos != null && bPos != null && currPos > 0 && bPos > 0) {
            bPos == currPos
        } else {
            val bProg = b.locator.locations.progression ?: b.locator.locations.totalProgression ?: 0f
            abs(bProg - currProg) < 0.05f
        }
    }
}
