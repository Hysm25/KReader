package io.github.kreader

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.kreader.core.model.Book
import io.github.kreader.core.model.BookFormat
import io.github.kreader.core.model.Bookmark
import io.github.kreader.core.model.ExternalBookOpener
import io.github.kreader.core.model.Highlight
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.PageLayout
import io.github.kreader.core.model.ReaderPreferences
import io.github.kreader.core.model.isRtlText
import io.github.kreader.feature.library.LibraryScreen
import io.github.kreader.feature.library.metadata.rememberBookMetadataExtractor
import io.github.kreader.feature.library.rememberLibraryViewModel
import io.github.kreader.feature.library.repository.rememberBookRepository
import io.github.kreader.feature.reader.ManageSystemBars
import io.github.kreader.feature.reader.NavigationDrawerTab
import io.github.kreader.feature.reader.ReaderScreen
import io.github.kreader.feature.reader.ReaderUiState
import io.github.kreader.feature.reader.getReaderColorScheme
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

@Composable
fun App() {
    val bookRepository = rememberBookRepository()
    val metadataExtractor = rememberBookMetadataExtractor()
    val libraryViewModel = rememberLibraryViewModel(bookRepository, metadataExtractor)
    val libraryUiState by libraryViewModel.uiState.collectAsState()
    val persistedPreferences by bookRepository.observeReaderPreferences().collectAsState(ReaderPreferences())
    val incomingSource by ExternalBookOpener.incomingSource.collectAsState()
    val scope = rememberCoroutineScope()

    var selectedBook by remember { mutableStateOf<Book?>(null) }

    LaunchedEffect(incomingSource) {
        val src = incomingSource ?: return@LaunchedEffect
        libraryViewModel.importBook(src)
        ExternalBookOpener.clear()
    }

    val activeTheme = persistedPreferences.theme
    ManageSystemBars(isControlsVisible = true, theme = activeTheme)

    MaterialTheme(colorScheme = getReaderColorScheme(activeTheme)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val bookToRead = selectedBook
            if (bookToRead == null) {
                LibraryScreen(
                    state = libraryUiState,
                    onBookClick = { book -> selectedBook = book },
                    onSelectVaultFolder = { uriOrPath -> libraryViewModel.setVault(uriOrPath) },
                    onRefreshVault = { libraryViewModel.refreshVault() },
                    onChangeVaultFolder = { /* handled inside LibraryScreen via folderPickerLauncher */ },
                    onDeleteBook = { book -> libraryViewModel.deleteBook(book) },
                    onDeleteBooks = { books -> libraryViewModel.deleteBooks(books) },
                    onResetBookProgress = { book -> libraryViewModel.resetBookProgress(book) },
                    onSearchQueryChanged = { query -> libraryViewModel.onSearchQueryChanged(query) },
                    onSortOrderChanged = { sort -> libraryViewModel.onSortOrderChanged(sort) }
                )
            } else {
                val effectivePreferences by bookRepository.observeEffectivePreferences(bookToRead.id).collectAsState(persistedPreferences)
                val bookmarks by bookRepository.observeBookmarks(bookToRead.id).collectAsState(emptyList())
                val highlights by bookRepository.observeHighlights(bookToRead.id).collectAsState(emptyList())

                var readerState by remember(bookToRead.id) {
                    val initialIsRtl = isRtlText(bookToRead.title) ||
                        isRtlText(bookToRead.author.orEmpty()) ||
                        isRtlText(bookToRead.description.orEmpty())
                    mutableStateOf(
                        ReaderUiState(
                            book = bookToRead,
                            currentLocator = bookToRead.lastLocator,
                            totalProgression = bookToRead.totalProgression,
                            theme = effectivePreferences.theme,
                            typography = effectivePreferences.typography,
                            isScrollMode = effectivePreferences.isScrollMode,
                            pageLayout = effectivePreferences.pageLayout,
                            isRtl = initialIsRtl
                        )
                    )
                }

                LaunchedEffect(effectivePreferences) {
                    readerState = readerState.copy(
                        theme = effectivePreferences.theme,
                        typography = effectivePreferences.typography,
                        isScrollMode = effectivePreferences.isScrollMode,
                        pageLayout = effectivePreferences.pageLayout
                    )
                }

                val currentUiState = readerState.copy(
                    bookmarks = bookmarks,
                    highlights = highlights
                )

                ReaderScreen(
                    state = currentUiState,
                    onBackClick = { selectedBook = null },
                    onToggleControls = {
                        readerState = readerState.copy(isControlsVisible = !readerState.isControlsVisible)
                    },
                    onPreviousPage = {},
                    onNextPage = {},
                    onSeek = { targetProgression ->
                        readerState = readerState.copy(totalProgression = targetProgression)
                        scope.launch {
                            val now = Clock.System.now().toEpochMilliseconds()
                            val loc = readerState.currentLocator ?: bookToRead.lastLocator ?: Locator(href = "", type = "application/epub+zip")
                            val updatedLoc = loc.copy(
                                locations = loc.locations.copy(
                                    totalProgression = targetProgression,
                                    progression = targetProgression
                                )
                            )
                            bookRepository.updateProgress(bookToRead.id, targetProgression, updatedLoc, now)
                        }
                    },
                    onTocClick = {
                        readerState = readerState.copy(isNavigationDrawerOpen = true, isControlsVisible = false)
                    },
                    onDismissNavigationDrawer = {
                        readerState = readerState.copy(isNavigationDrawerOpen = false)
                    },
                    onTabSelected = { tab ->
                        readerState = readerState.copy(navigationDrawerTab = tab)
                    },
                    onSearchClick = {
                        readerState = readerState.copy(isSearchOpen = true, isControlsVisible = false)
                    },
                    onDismissSearch = {
                        readerState = readerState.copy(isSearchOpen = false)
                    },
                    onSearchQueryChange = { query ->
                        readerState = readerState.copy(searchQuery = query)
                    },
                    onSearchResultsChange = { results ->
                        readerState = readerState.copy(searchResults = results)
                    },
                    onIsSearchingChange = { searching ->
                        readerState = readerState.copy(isSearching = searching)
                    },
                    onBookmarkToggle = {
                        scope.launch {
                            val currHref = currentUiState.currentLocator?.href ?: bookToRead.lastLocator?.href
                            val currProg = currentUiState.currentLocator?.locations?.progression ?: currentUiState.totalProgression
                            val existing = bookmarks.find { b ->
                                (currHref != null && b.locator.href == currHref) &&
                                abs((b.locator.locations.progression ?: 0f) - currProg) < 0.05f
                            }
                            if (existing != null) {
                                bookRepository.deleteBookmark(existing.id)
                            } else {
                                val currentLoc = currentUiState.currentLocator ?: bookToRead.lastLocator ?: Locator(href = "", type = "application/epub+zip")
                                val chapterTitle = currentUiState.tocItems.find {
                                    it.locator.href.substringBefore("#") == currentLoc.href.substringBefore("#")
                                }?.title ?: currentLoc.title ?: "Bookmark at ${(currentUiState.totalProgression * 100).toInt()}%"
                                val newBookmark = Bookmark(
                                    id = "bm_${Clock.System.now().toEpochMilliseconds()}_${Random.nextInt(1000, 9999)}",
                                    bookId = bookToRead.id,
                                    locator = currentLoc,
                                    title = chapterTitle,
                                    createdTimestamp = Clock.System.now().toEpochMilliseconds()
                                )
                                bookRepository.saveBookmark(newBookmark)
                            }
                        }
                    },
                    onDeleteBookmark = { bookmarkId ->
                        scope.launch {
                            bookRepository.deleteBookmark(bookmarkId)
                        }
                    },
                    onSaveHighlight = { highlight ->
                        scope.launch {
                            bookRepository.saveHighlight(highlight)
                        }
                    },
                    onDeleteHighlight = { highlightId ->
                        scope.launch {
                            bookRepository.deleteHighlight(highlightId)
                        }
                    },
                    onHighlightClicked = { highlight ->
                        readerState = readerState.copy(selectedHighlightForDetail = highlight)
                    },
                    onDismissHighlightDetail = {
                        readerState = readerState.copy(selectedHighlightForDetail = null)
                    },
                    onSaveHighlightDetail = { id, note, colorHex ->
                        scope.launch {
                            val existing = highlights.find { it.id == id }
                            if (existing != null) {
                                val updated = existing.copy(
                                    annotation = note.ifBlank { null },
                                    colorHex = colorHex,
                                    modifiedTimestamp = Clock.System.now().toEpochMilliseconds()
                                )
                                bookRepository.saveHighlight(updated)
                            }
                        }
                        readerState = readerState.copy(selectedHighlightForDetail = null)
                    },
                    onOpenAddNoteDialog = { locator, selectedText ->
                        readerState = readerState.copy(
                            pendingNoteLocator = locator,
                            pendingNoteText = selectedText
                        )
                    },
                    onDismissAddNoteDialog = {
                        readerState = readerState.copy(
                            pendingNoteLocator = null,
                            pendingNoteText = null
                        )
                    },
                    onSaveNewNote = { locator, note, colorHex ->
                        val newHighlight = Highlight(
                            id = "hl_${Clock.System.now().toEpochMilliseconds()}_${Random.nextInt(1000, 9999)}",
                            bookId = bookToRead.id,
                            locator = locator,
                            colorHex = colorHex,
                            annotation = note.ifBlank { null },
                            createdTimestamp = Clock.System.now().toEpochMilliseconds(),
                            modifiedTimestamp = Clock.System.now().toEpochMilliseconds()
                        )
                        scope.launch {
                            bookRepository.saveHighlight(newHighlight)
                        }
                        readerState = readerState.copy(
                            pendingNoteLocator = null,
                            pendingNoteText = null
                        )
                    },
                    onSettingsClick = {
                        readerState = readerState.copy(isSettingsOpen = true, isControlsVisible = false)
                    },
                    onDismissSettings = {
                        readerState = readerState.copy(isSettingsOpen = false)
                    },
                    onThemeChange = { newTheme ->
                        readerState = readerState.copy(theme = newTheme)
                        scope.launch {
                            val updated = effectivePreferences.copy(theme = newTheme)
                            bookRepository.savePreferences(bookToRead.id, updated, readerState.isApplyToThisBookOnly)
                        }
                    },
                    onTypographyChange = { newTypography ->
                        readerState = readerState.copy(typography = newTypography)
                        scope.launch {
                            val updated = effectivePreferences.copy(typography = newTypography)
                            bookRepository.savePreferences(bookToRead.id, updated, readerState.isApplyToThisBookOnly)
                        }
                    },
                    onScrollModeChange = { isScroll ->
                        readerState = readerState.copy(isScrollMode = isScroll)
                        scope.launch {
                            val updated = effectivePreferences.copy(isScrollMode = isScroll)
                            bookRepository.savePreferences(bookToRead.id, updated, readerState.isApplyToThisBookOnly)
                        }
                    },
                    onPageLayoutChange = { newLayout ->
                        readerState = readerState.copy(pageLayout = newLayout)
                        scope.launch {
                            val updated = effectivePreferences.copy(pageLayout = newLayout)
                            bookRepository.savePreferences(bookToRead.id, updated, readerState.isApplyToThisBookOnly)
                        }
                    },
                    onScopeChange = { isThisBookOnly ->
                        readerState = readerState.copy(isApplyToThisBookOnly = isThisBookOnly)
                    },
                    onResetToDefaults = {
                        scope.launch {
                            bookRepository.resetPreferences(bookToRead.id)
                        }
                    },
                    onProgressChanged = { progression, locator ->
                        readerState = readerState.copy(
                            totalProgression = progression,
                            currentLocator = locator
                        )
                        scope.launch {
                            val now = Clock.System.now().toEpochMilliseconds()
                            bookRepository.updateProgress(bookToRead.id, progression, locator, now)
                        }
                    },
                    onTableOfContentsLoaded = { toc ->
                        readerState = readerState.copy(tocItems = toc)
                    },
                    customFonts = effectivePreferences.customFonts,
                    onImportCustomFont = { font ->
                        scope.launch {
                            bookRepository.addCustomFont(font)
                            val updatedTypography = readerState.typography.copy(fontFamily = font.name)
                            readerState = readerState.copy(typography = updatedTypography)
                            val updated = effectivePreferences.copy(typography = updatedTypography)
                            bookRepository.savePreferences(bookToRead.id, updated, readerState.isApplyToThisBookOnly)
                        }
                    },
                    onDeleteCustomFont = { fontName ->
                        scope.launch {
                            bookRepository.deleteCustomFont(fontName)
                            if (readerState.typography.fontFamily.equals(fontName, ignoreCase = true)) {
                                val updatedTypography = readerState.typography.copy(fontFamily = "Default")
                                readerState = readerState.copy(typography = updatedTypography)
                                val updated = effectivePreferences.copy(typography = updatedTypography)
                                bookRepository.savePreferences(bookToRead.id, updated, readerState.isApplyToThisBookOnly)
                            }
                        }
                    },
                    onDirectionDetected = { isRtl ->
                        if (isRtl != readerState.isRtl) {
                            readerState = readerState.copy(isRtl = isRtl)
                        }
                    }
                )
            }
        }
    }
}
