package io.github.kreader

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
import io.github.kreader.core.model.Book
import io.github.kreader.core.model.BookFormat
import io.github.kreader.core.model.Bookmark
import io.github.kreader.core.model.ExternalBookOpener
import io.github.kreader.core.model.Highlight
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.ReaderPreferences
import io.github.kreader.feature.library.LibraryScreen
import io.github.kreader.feature.library.metadata.rememberBookMetadataExtractor
import io.github.kreader.feature.library.rememberLibraryViewModel
import io.github.kreader.feature.library.repository.rememberBookRepository
import io.github.kreader.feature.reader.NavigationDrawerTab
import io.github.kreader.feature.reader.ReaderScreen
import io.github.kreader.feature.reader.ReaderUiState
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

@Composable
fun App() {
    MaterialTheme {
        Surface {
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

            val bookToRead = selectedBook
            if (bookToRead == null) {
                LibraryScreen(
                    state = libraryUiState,
                    onBookClick = { book -> selectedBook = book },
                    onImportBook = { source -> libraryViewModel.importBook(source) },
                    onDeleteBook = { book -> libraryViewModel.deleteBook(book) },
                    onSearchQueryChanged = { query -> libraryViewModel.onSearchQueryChanged(query) },
                    onFilterChanged = { filter -> libraryViewModel.onFilterChanged(filter) },
                    onSortOrderChanged = { sort -> libraryViewModel.onSortOrderChanged(sort) }
                )
            } else {
                val bookmarks by bookRepository.observeBookmarks(bookToRead.id).collectAsState(emptyList())
                val highlights by bookRepository.observeHighlights(bookToRead.id).collectAsState(emptyList())

                var readerState by remember(bookToRead.id, persistedPreferences) {
                    val initialPage = (bookToRead.lastLocator?.locations?.position?.minus(1) ?: 0).coerceAtLeast(0)
                    mutableStateOf(
                        ReaderUiState(
                            book = bookToRead,
                            currentLocator = bookToRead.lastLocator,
                            totalProgression = bookToRead.totalProgression,
                            currentPageIndex = if (bookToRead.format == BookFormat.PDF) initialPage else null,
                            theme = persistedPreferences.theme,
                            typography = persistedPreferences.typography,
                            isScrollMode = persistedPreferences.isScrollMode
                        )
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
                            if (bookToRead.format == BookFormat.PDF) {
                                val currPos = currentUiState.currentPageIndex?.let { it + 1 }
                                    ?: (currentUiState.currentLocator?.locations?.position ?: 1)
                                val existing = bookmarks.find { it.locator.locations.position == currPos }
                                if (existing != null) {
                                    bookRepository.deleteBookmark(existing.id)
                                } else {
                                    val total = currentUiState.totalPages ?: 1
                                    val prog = if (total > 0) (currPos - 1).toFloat() / total else currentUiState.totalProgression
                                    val currentLoc = Locator(
                                        href = "page-$currPos",
                                        type = "application/pdf",
                                        title = "Page $currPos",
                                        locations = Locator.Locations(
                                            position = currPos,
                                            progression = prog,
                                            totalProgression = prog
                                        )
                                    )
                                    val newBookmark = Bookmark(
                                        id = "bm_${Clock.System.now().toEpochMilliseconds()}_${Random.nextInt(1000, 9999)}",
                                        bookId = bookToRead.id,
                                        locator = currentLoc,
                                        title = "Page $currPos",
                                        createdTimestamp = Clock.System.now().toEpochMilliseconds()
                                    )
                                    bookRepository.saveBookmark(newBookmark)
                                }
                            } else {
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
                            bookRepository.saveReaderPreferences(persistedPreferences.copy(theme = newTheme))
                        }
                    },
                    onTypographyChange = { newTypography ->
                        readerState = readerState.copy(typography = newTypography)
                        scope.launch {
                            bookRepository.saveReaderPreferences(persistedPreferences.copy(typography = newTypography))
                        }
                    },
                    onScrollModeChange = { isScroll ->
                        readerState = readerState.copy(isScrollMode = isScroll)
                        scope.launch {
                            bookRepository.saveReaderPreferences(persistedPreferences.copy(isScrollMode = isScroll))
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
                    onPdfPageChanged = { pageIndex, totalPages, locator ->
                        val prog = if (totalPages > 0) pageIndex.toFloat() / totalPages else 0f
                        readerState = readerState.copy(
                            currentPageIndex = pageIndex,
                            totalPages = totalPages,
                            totalProgression = prog,
                            currentLocator = locator
                        )
                        scope.launch {
                            val now = Clock.System.now().toEpochMilliseconds()
                            bookRepository.updateProgress(bookToRead.id, prog, locator, now)
                        }
                    },
                    onTableOfContentsLoaded = { toc ->
                        readerState = readerState.copy(tocItems = toc)
                    },
                    customFonts = persistedPreferences.customFonts,
                    onImportCustomFont = { font ->
                        scope.launch {
                            bookRepository.addCustomFont(font)
                            val updatedTypography = readerState.typography.copy(fontFamily = font.name)
                            readerState = readerState.copy(typography = updatedTypography)
                            bookRepository.saveReaderPreferences(persistedPreferences.copy(typography = updatedTypography))
                        }
                    },
                    onDeleteCustomFont = { fontName ->
                        scope.launch {
                            bookRepository.deleteCustomFont(fontName)
                            if (readerState.typography.fontFamily.equals(fontName, ignoreCase = true)) {
                                val updatedTypography = readerState.typography.copy(fontFamily = "Default")
                                readerState = readerState.copy(typography = updatedTypography)
                                bookRepository.saveReaderPreferences(persistedPreferences.copy(typography = updatedTypography))
                            }
                        }
                    },
                    onDirectionDetected = { isRtl ->
                        if (isRtl != readerState.typography.isRtl) {
                            readerState = readerState.copy(
                                typography = readerState.typography.copy(isRtl = isRtl)
                            )
                        }
                    }
                )
            }
        }
    }
}
