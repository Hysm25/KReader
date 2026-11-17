package io.github.kreader.feature.reader

import androidx.compose.runtime.Immutable
import io.github.kreader.core.engine.ReaderEngineState
import io.github.kreader.core.engine.TocItem
import io.github.kreader.core.model.Book
import io.github.kreader.core.model.Bookmark
import io.github.kreader.core.model.CustomTheme
import io.github.kreader.core.model.Highlight
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.OrientationLock
import io.github.kreader.core.model.PageLayout
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ReaderTypography
import kotlin.math.abs

enum class NavigationDrawerTab {
    CONTENTS,
    BOOKMARKS,
    HIGHLIGHTS
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
    val highlights: List<Highlight> = emptyList(),
    val selectedHighlightForDetail: Highlight? = null,
    val pendingNoteLocator: Locator? = null,
    val pendingNoteText: String? = null,
    val keepScreenOn: Boolean = false,
    val orientationLock: OrientationLock = OrientationLock.AUTO,
    val customThemes: List<CustomTheme> = emptyList()
) {
    val isCurrentBookmarked: Boolean
        get() {
            val currPos = currentLocator?.locations?.position
            if (currPos != null) {
                val matched = bookmarks.any { b -> b.locator.locations.position == currPos }
                if (matched) return true
            }
            val currHref = currentLocator?.href ?: return false
            val currProg = currentLocator.locations.progression ?: totalProgression
            return bookmarks.any { b ->
                b.locator.href == currHref && abs((b.locator.locations.progression ?: 0f) - currProg) < 0.05f
            }
        }
}
