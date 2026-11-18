package io.github.kreader.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.github.kreader.core.database.BookRepository
import io.github.kreader.core.engine.TocItem
import io.github.kreader.core.model.Book
import io.github.kreader.core.model.Bookmark
import io.github.kreader.core.model.CustomFont
import io.github.kreader.core.model.CustomTheme
import io.github.kreader.core.model.Highlight
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.OrientationLock
import io.github.kreader.core.model.ReaderPreferences
import io.github.kreader.core.model.ReaderPreferencesOverride
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ReaderTypography
import io.github.kreader.core.model.ReaderTypographyOverride
import io.github.kreader.core.model.isRtlText
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class ReaderViewModel(
    private val bookRepository: BookRepository,
    val book: Book,
    private val scope: CoroutineScope
) {
    private val _uiState = MutableStateFlow(
        ReaderUiState(
            book = book,
            currentLocator = book.lastLocator,
            totalProgression = book.totalProgression,
            isRtl = isRtlText(book.title) || isRtlText(book.author.orEmpty()) || isRtlText(book.description.orEmpty())
        )
    )
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var currentEffectivePreferences = ReaderPreferences()

    init {
        observeData()
    }

    private fun observeData() {
        bookRepository.observeEffectivePreferences(book.id).onEach { prefs ->
            currentEffectivePreferences = prefs
            _uiState.update { current ->
                current.copy(
                    theme = prefs.theme,
                    typography = prefs.typography,
                    isScrollMode = prefs.isScrollMode,
                    pageLayout = prefs.pageLayout,
                    keepScreenOn = prefs.keepScreenOn,
                    orientationLock = prefs.orientationLock
                )
            }
        }.launchIn(scope)

        bookRepository.observeBookmarks(book.id).onEach { bookmarks ->
            _uiState.update { it.copy(bookmarks = bookmarks) }
        }.launchIn(scope)

        bookRepository.observeHighlights(book.id).onEach { highlights ->
            _uiState.update { it.copy(highlights = highlights) }
        }.launchIn(scope)

        bookRepository.observeCustomThemes().onEach { themes ->
            _uiState.update { it.copy(customThemes = themes) }
        }.launchIn(scope)
    }

    fun toggleControls() {
        _uiState.update { it.copy(isControlsVisible = !it.isControlsVisible) }
    }

    fun openNavigationDrawer(tab: NavigationDrawerTab = NavigationDrawerTab.CONTENTS) {
        _uiState.update { it.copy(isNavigationDrawerOpen = true, isControlsVisible = false, navigationDrawerTab = tab) }
    }

    fun dismissNavigationDrawer() {
        _uiState.update { it.copy(isNavigationDrawerOpen = false) }
    }

    fun selectTab(tab: NavigationDrawerTab) {
        _uiState.update { it.copy(navigationDrawerTab = tab) }
    }

    fun openSearch() {
        _uiState.update { it.copy(isSearchOpen = true, isControlsVisible = false) }
    }

    fun dismissSearch() {
        _uiState.update { it.copy(isSearchOpen = false) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun updateSearchResults(results: List<Locator>) {
        _uiState.update { it.copy(searchResults = results) }
    }

    fun updateIsSearching(searching: Boolean) {
        _uiState.update { it.copy(isSearching = searching) }
    }

    fun openSettings() {
        _uiState.update { it.copy(isSettingsOpen = true, isControlsVisible = false) }
    }

    fun dismissSettings() {
        _uiState.update { it.copy(isSettingsOpen = false) }
    }

    fun updateProgress(progression: Float, locator: Locator) {
        _uiState.update {
            it.copy(
                totalProgression = progression,
                currentLocator = locator
            )
        }
        scope.launch {
            val now = Clock.System.now().toEpochMilliseconds()
            bookRepository.updateProgress(book.id, progression, locator, now)
        }
    }

    fun seek(targetProgression: Float) {
        _uiState.update { it.copy(totalProgression = targetProgression) }
        scope.launch {
            val now = Clock.System.now().toEpochMilliseconds()
            val loc = _uiState.value.currentLocator ?: book.lastLocator ?: Locator(href = "", type = "application/epub+zip")
            val updatedLoc = loc.copy(
                locations = loc.locations.copy(
                    totalProgression = targetProgression,
                    progression = targetProgression
                )
            )
            bookRepository.updateProgress(book.id, targetProgression, updatedLoc, now)
        }
    }

    fun toggleBookmark() {
        scope.launch {
            val currentState = _uiState.value
            val currHref = currentState.currentLocator?.href ?: book.lastLocator?.href
            val currProg = currentState.currentLocator?.locations?.progression ?: currentState.totalProgression
            val bookmarks = currentState.bookmarks
            val existing = bookmarks.find { b ->
                (currHref != null && b.locator.href == currHref) &&
                    abs((b.locator.locations.progression ?: 0f) - currProg) < 0.05f
            }
            if (existing != null) {
                bookRepository.deleteBookmark(existing.id)
            } else {
                val currentLoc = currentState.currentLocator ?: book.lastLocator ?: Locator(href = "", type = "application/epub+zip")
                val chapterTitle = currentState.tocItems.find {
                    it.locator.href.substringBefore("#") == currentLoc.href.substringBefore("#")
                }?.title ?: currentLoc.title ?: "Bookmark at ${(currentState.totalProgression * 100).toInt()}%"
                val newBookmark = Bookmark(
                    id = "bm_${Clock.System.now().toEpochMilliseconds()}_${Random.nextInt(1000, 9999)}",
                    bookId = book.id,
                    locator = currentLoc,
                    title = chapterTitle,
                    createdTimestamp = Clock.System.now().toEpochMilliseconds()
                )
                bookRepository.saveBookmark(newBookmark)
            }
        }
    }

    fun deleteBookmark(bookmarkId: String) {
        _uiState.update { current ->
            current.copy(bookmarks = current.bookmarks.filterNot { it.id == bookmarkId })
        }
        scope.launch {
            bookRepository.deleteBookmark(bookmarkId)
        }
    }

    fun saveHighlight(highlight: Highlight) {
        _uiState.update { current ->
            val updated = current.highlights.filterNot { it.id == highlight.id } + highlight
            current.copy(highlights = updated)
        }
        scope.launch {
            bookRepository.saveHighlight(highlight)
        }
    }

    fun deleteHighlight(highlightId: String) {
        _uiState.update { current ->
            current.copy(highlights = current.highlights.filterNot { it.id == highlightId })
        }
        scope.launch {
            bookRepository.deleteHighlight(highlightId)
        }
    }

    fun selectHighlightForDetail(highlight: Highlight?) {
        _uiState.update { it.copy(selectedHighlightForDetail = highlight) }
    }

    fun saveHighlightDetail(id: String, note: String, colorHex: String) {
        val existing = _uiState.value.highlights.find { it.id == id }
        if (existing != null) {
            val updated = existing.copy(
                annotation = note.ifBlank { null },
                colorHex = colorHex,
                modifiedTimestamp = Clock.System.now().toEpochMilliseconds()
            )
            _uiState.update { current ->
                val updatedHighlights = current.highlights.map { if (it.id == id) updated else it }
                current.copy(
                    highlights = updatedHighlights,
                    selectedHighlightForDetail = null
                )
            }
            scope.launch {
                bookRepository.saveHighlight(updated)
            }
        } else {
            _uiState.update { it.copy(selectedHighlightForDetail = null) }
        }
    }

    fun openAddNoteDialog(locator: Locator, selectedText: String) {
        _uiState.update {
            it.copy(
                pendingNoteLocator = locator,
                pendingNoteText = selectedText
            )
        }
    }

    fun dismissAddNoteDialog() {
        _uiState.update {
            it.copy(
                pendingNoteLocator = null,
                pendingNoteText = null
            )
        }
    }

    fun saveNewNote(locator: Locator, note: String, colorHex: String) {
        val newHighlight = Highlight(
            id = "hl_${Clock.System.now().toEpochMilliseconds()}_${Random.nextInt(1000, 9999)}",
            bookId = book.id,
            locator = locator,
            colorHex = colorHex,
            annotation = note.ifBlank { null },
            createdTimestamp = Clock.System.now().toEpochMilliseconds(),
            modifiedTimestamp = Clock.System.now().toEpochMilliseconds()
        )
        _uiState.update { current ->
            val updated = current.highlights.filterNot { it.id == newHighlight.id } + newHighlight
            current.copy(
                highlights = updated,
                pendingNoteLocator = null,
                pendingNoteText = null
            )
        }
        scope.launch {
            bookRepository.saveHighlight(newHighlight)
        }
    }

    fun changeTheme(newTheme: ReaderTheme) {
        _uiState.update { it.copy(theme = newTheme) }
        scope.launch {
            bookRepository.updateBookPreferencesOverride(book.id) { current ->
                (current ?: ReaderPreferencesOverride()).copy(theme = newTheme)
            }
        }
    }

    fun changeTypography(newTypography: ReaderTypography) {
        val prev = _uiState.value.typography
        _uiState.update { it.copy(typography = newTypography) }
        scope.launch {
            bookRepository.updateBookPreferencesOverride(book.id) { current ->
                val prevTypo = current?.typography ?: ReaderTypographyOverride()
                val updatedTypo = prevTypo.copy(
                    fontFamily = if (newTypography.fontFamily != prev.fontFamily) newTypography.fontFamily else prevTypo.fontFamily,
                    fontSizeSp = if (newTypography.fontSizeSp != prev.fontSizeSp) newTypography.fontSizeSp else prevTypo.fontSizeSp,
                    lineHeightMultiplier = if (newTypography.lineHeightMultiplier != prev.lineHeightMultiplier) newTypography.lineHeightMultiplier else prevTypo.lineHeightMultiplier,
                    letterSpacingSp = if (newTypography.letterSpacingSp != prev.letterSpacingSp) newTypography.letterSpacingSp else prevTypo.letterSpacingSp,
                    textAlignment = if (newTypography.textAlignment != prev.textAlignment) newTypography.textAlignment else prevTypo.textAlignment,
                    paddingHorizontal = if (newTypography.paddingHorizontal != prev.paddingHorizontal) newTypography.paddingHorizontal else prevTypo.paddingHorizontal,
                    paddingVertical = if (newTypography.paddingVertical != prev.paddingVertical) newTypography.paddingVertical else prevTypo.paddingVertical
                )
                (current ?: ReaderPreferencesOverride()).copy(typography = updatedTypo)
            }
        }
    }

    fun changeScrollMode(isScroll: Boolean) {
        _uiState.update { it.copy(isScrollMode = isScroll) }
        scope.launch {
            bookRepository.updateBookPreferencesOverride(book.id) { current ->
                (current ?: ReaderPreferencesOverride()).copy(isScrollMode = isScroll)
            }
        }
    }

    fun resetToDefaults() {
        scope.launch {
            bookRepository.resetPreferences(book.id)
        }
    }

    fun setTableOfContents(toc: List<TocItem>) {
        _uiState.update { it.copy(tocItems = toc) }
    }

    fun saveCustomTheme(newTheme: CustomTheme) {
        scope.launch {
            bookRepository.saveCustomTheme(newTheme)
        }
    }

    fun deleteCustomTheme(themeId: String) {
        scope.launch {
            bookRepository.deleteCustomTheme(themeId)
        }
    }

    fun changeKeepScreenOn(enabled: Boolean) {
        _uiState.update { it.copy(keepScreenOn = enabled) }
        scope.launch {
            bookRepository.updateBookPreferencesOverride(book.id) { current ->
                (current ?: ReaderPreferencesOverride()).copy(keepScreenOn = enabled)
            }
        }
    }

    fun changeOrientationLock(lock: OrientationLock) {
        _uiState.update { it.copy(orientationLock = lock) }
        scope.launch {
            bookRepository.updateBookPreferencesOverride(book.id) { current ->
                (current ?: ReaderPreferencesOverride()).copy(orientationLock = lock)
            }
        }
    }

    fun importCustomFont(font: CustomFont) {
        scope.launch {
            bookRepository.addCustomFont(font)
            val updatedTypography = _uiState.value.typography.copy(fontFamily = font.name)
            _uiState.update { it.copy(typography = updatedTypography) }
            bookRepository.updateBookPreferencesOverride(book.id) { current ->
                val prevTypo = current?.typography ?: ReaderTypographyOverride()
                (current ?: ReaderPreferencesOverride()).copy(
                    typography = prevTypo.copy(fontFamily = font.name)
                )
            }
        }
    }

    fun deleteCustomFont(fontName: String) {
        scope.launch {
            bookRepository.deleteCustomFont(fontName)
            if (_uiState.value.typography.fontFamily.equals(fontName, ignoreCase = true)) {
                val updatedTypography = _uiState.value.typography.copy(fontFamily = "Default")
                _uiState.update { it.copy(typography = updatedTypography) }
                bookRepository.updateBookPreferencesOverride(book.id) { current ->
                    val prevTypo = current?.typography ?: ReaderTypographyOverride()
                    (current ?: ReaderPreferencesOverride()).copy(
                        typography = prevTypo.copy(fontFamily = "Default")
                    )
                }
            }
        }
    }

    fun onDirectionDetected(isRtl: Boolean) {
        if (isRtl != _uiState.value.isRtl) {
            _uiState.update { it.copy(isRtl = isRtl) }
        }
    }

    suspend fun exportMarkdown(): String? {
        return bookRepository.exportBookToMarkdown(book.id)
    }

    val customFonts: List<CustomFont>
        get() = currentEffectivePreferences.customFonts
}

@Composable
fun rememberReaderViewModel(
    bookRepository: BookRepository,
    book: Book
): ReaderViewModel {
    val scope = rememberCoroutineScope()
    return remember(bookRepository, book.id) {
        ReaderViewModel(bookRepository, book, scope)
    }
}
