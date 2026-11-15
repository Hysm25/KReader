package io.github.kreader.feature.reader

import io.github.kreader.core.engine.ReaderEngineState
import io.github.kreader.core.engine.TocItem
import io.github.kreader.core.model.Book
import io.github.kreader.core.model.Bookmark
import io.github.kreader.core.model.Highlight
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ReaderTypography
import kotlin.math.abs

enum class NavigationDrawerTab {
    CONTENTS,
    BOOKMARKS,
    HIGHLIGHTS
}

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
    val theme: ReaderTheme = ReaderTheme(),
    val typography: ReaderTypography = ReaderTypography(lineHeightMultiplier = 1.6f),
    val currentLocator: Locator? = null,
    val totalProgression: Float = 0f,
    val tocItems: List<TocItem> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val highlights: List<Highlight> = emptyList(),
    val selectedHighlightForDetail: Highlight? = null,
    val pendingNoteLocator: Locator? = null,
    val pendingNoteText: String? = null
) {
    val isCurrentBookmarked: Boolean
        get() {
            val currHref = currentLocator?.href ?: return false
            val currProg = currentLocator.locations.progression ?: totalProgression
            return bookmarks.any { b ->
                b.locator.href == currHref && abs((b.locator.locations.progression ?: 0f) - currProg) < 0.05f
            }
        }
}
