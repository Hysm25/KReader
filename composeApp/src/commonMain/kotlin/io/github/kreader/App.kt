package io.github.kreader

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.kreader.core.model.Book
import io.github.kreader.core.model.ExternalBookOpener
import io.github.kreader.core.model.ReaderPreferences
import io.github.kreader.feature.library.LibraryScreen
import io.github.kreader.feature.library.metadata.rememberBookMetadataExtractor
import io.github.kreader.feature.library.rememberLibraryViewModel
import io.github.kreader.feature.library.repository.rememberBookRepository
import io.github.kreader.feature.reader.ManageSystemBars
import io.github.kreader.feature.reader.NavigationDrawerTab
import io.github.kreader.feature.reader.ReaderScreen
import io.github.kreader.feature.reader.ReaderSettingsSheet
import io.github.kreader.feature.reader.getReaderColorScheme
import io.github.kreader.feature.reader.rememberReaderViewModel
import kotlinx.coroutines.launch

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
        libraryViewModel.importBook(src) { importedBook ->
            selectedBook = importedBook
        }
        ExternalBookOpener.clear()
    }

    DisposableEffect(bookRepository) {
        VaultSyncBridge.onResumeCallback = {
            scope.launch {
                bookRepository.refreshVault()
            }
        }
        onDispose {
            VaultSyncBridge.onResumeCallback = null
        }
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
                var showGlobalSettings by remember { mutableStateOf(false) }
                val customThemes by bookRepository.observeCustomThemes().collectAsState(emptyList())

                LibraryScreen(
                    state = libraryUiState,
                    onBookClick = { book -> selectedBook = book },
                    onSelectVaultFolder = { uriOrPath -> libraryViewModel.setVault(uriOrPath) },
                    onRefreshVault = { libraryViewModel.refreshVault() },
                    onChangeVaultFolder = { /* handled inside LibraryScreen via folderPickerLauncher */ },
                    onOpenGlobalSettings = { showGlobalSettings = true },
                    onDeleteBook = { book -> libraryViewModel.deleteBook(book) },
                    onDeleteBooks = { books -> libraryViewModel.deleteBooks(books) },
                    onResetBookProgress = { book -> libraryViewModel.resetBookProgress(book) },
                    onSearchQueryChanged = { query -> libraryViewModel.onSearchQueryChanged(query) },
                    onSortOrderChanged = { sort -> libraryViewModel.onSortOrderChanged(sort) },
                    onExportBookToMarkdown = { book -> bookRepository.exportBookToMarkdown(book.id) }
                )

                ReaderSettingsSheet(
                    isOpen = showGlobalSettings,
                    title = "Global Reading Settings",
                    isGlobal = true,
                    theme = persistedPreferences.theme,
                    typography = persistedPreferences.typography,
                    isScrollMode = persistedPreferences.isScrollMode,
                    customFonts = persistedPreferences.customFonts,
                    customThemes = customThemes,
                    keepScreenOn = persistedPreferences.keepScreenOn,
                    orientationLock = persistedPreferences.orientationLock,
                    onThemeChange = { newTheme ->
                        scope.launch {
                            bookRepository.saveReaderPreferences(persistedPreferences.copy(theme = newTheme))
                        }
                    },
                    onTypographyChange = { newTypo ->
                        scope.launch {
                            bookRepository.saveReaderPreferences(persistedPreferences.copy(typography = newTypo))
                        }
                    },
                    onScrollModeChange = { isScroll ->
                        scope.launch {
                            bookRepository.saveReaderPreferences(persistedPreferences.copy(isScrollMode = isScroll))
                        }
                    },
                    onKeepScreenOnChange = { enabled ->
                        scope.launch {
                            bookRepository.saveReaderPreferences(persistedPreferences.copy(keepScreenOn = enabled))
                        }
                    },
                    onOrientationLockChange = { lock ->
                        scope.launch {
                            bookRepository.saveReaderPreferences(persistedPreferences.copy(orientationLock = lock))
                        }
                    },
                    onResetToDefaults = {
                        scope.launch {
                            bookRepository.resetGlobalPreferences()
                        }
                    },
                    onImportCustomFont = { font ->
                        scope.launch {
                            bookRepository.addCustomFont(font)
                            bookRepository.saveReaderPreferences(
                                persistedPreferences.copy(
                                    typography = persistedPreferences.typography.copy(fontFamily = font.name)
                                )
                            )
                        }
                    },
                    onDeleteCustomFont = { fontName ->
                        scope.launch {
                            bookRepository.deleteCustomFont(fontName)
                            if (persistedPreferences.typography.fontFamily.equals(fontName, ignoreCase = true)) {
                                bookRepository.saveReaderPreferences(
                                    persistedPreferences.copy(
                                        typography = persistedPreferences.typography.copy(fontFamily = "Default")
                                    )
                                )
                            }
                        }
                    },
                    onSaveCustomTheme = { newTheme ->
                        scope.launch {
                            bookRepository.saveCustomTheme(newTheme)
                        }
                    },
                    onDeleteCustomTheme = { themeId ->
                        scope.launch {
                            bookRepository.deleteCustomTheme(themeId)
                        }
                    },
                    onDismiss = { showGlobalSettings = false }
                )
            } else {
                val readerViewModel = rememberReaderViewModel(bookRepository, bookToRead)
                val readerUiState by readerViewModel.uiState.collectAsState()

                ReaderScreen(
                    state = readerUiState,
                    onBackClick = { selectedBook = null },
                    onToggleControls = { readerViewModel.toggleControls() },
                    onPreviousPage = {},
                    onNextPage = {},
                    onSeek = { target -> readerViewModel.seek(target) },
                    onTocClick = { readerViewModel.openNavigationDrawer(NavigationDrawerTab.CONTENTS) },
                    onDismissNavigationDrawer = { readerViewModel.dismissNavigationDrawer() },
                    onTabSelected = { tab -> readerViewModel.selectTab(tab) },
                    onSearchClick = { readerViewModel.openSearch() },
                    onDismissSearch = { readerViewModel.dismissSearch() },
                    onSearchQueryChange = { q -> readerViewModel.updateSearchQuery(q) },
                    onSearchResultsChange = { res -> readerViewModel.updateSearchResults(res) },
                    onIsSearchingChange = { s -> readerViewModel.updateIsSearching(s) },
                    onBookmarkToggle = { readerViewModel.toggleBookmark() },
                    onDeleteBookmark = { id -> readerViewModel.deleteBookmark(id) },
                    onSaveHighlight = { hl -> readerViewModel.saveHighlight(hl) },
                    onDeleteHighlight = { id -> readerViewModel.deleteHighlight(id) },
                    onHighlightClicked = { hl -> readerViewModel.selectHighlightForDetail(hl) },
                    onDismissHighlightDetail = { readerViewModel.selectHighlightForDetail(null) },
                    onSaveHighlightDetail = { id, note, color -> readerViewModel.saveHighlightDetail(id, note, color) },
                    onOpenAddNoteDialog = { loc, text -> readerViewModel.openAddNoteDialog(loc, text) },
                    onDismissAddNoteDialog = { readerViewModel.dismissAddNoteDialog() },
                    onSaveNewNote = { loc, note, color -> readerViewModel.saveNewNote(loc, note, color) },
                    onSettingsClick = { readerViewModel.openSettings() },
                    onDismissSettings = { readerViewModel.dismissSettings() },
                    onThemeChange = { theme -> readerViewModel.changeTheme(theme) },
                    onTypographyChange = { typo -> readerViewModel.changeTypography(typo) },
                    onScrollModeChange = { scroll -> readerViewModel.changeScrollMode(scroll) },
                    onResetToDefaults = { readerViewModel.resetToDefaults() },
                    onProgressChanged = { prog, loc -> readerViewModel.updateProgress(prog, loc) },
                    onTableOfContentsLoaded = { toc -> readerViewModel.setTableOfContents(toc) },
                    customFonts = readerViewModel.customFonts,
                    customThemes = readerUiState.customThemes,
                    onSaveCustomTheme = { theme -> readerViewModel.saveCustomTheme(theme) },
                    onDeleteCustomTheme = { id -> readerViewModel.deleteCustomTheme(id) },
                    onKeepScreenOnChange = { enabled -> readerViewModel.changeKeepScreenOn(enabled) },
                    onOrientationLockChange = { lock -> readerViewModel.changeOrientationLock(lock) },
                    onImportCustomFont = { font -> readerViewModel.importCustomFont(font) },
                    onDeleteCustomFont = { name -> readerViewModel.deleteCustomFont(name) },
                    onDirectionDetected = { isRtl -> readerViewModel.onDirectionDetected(isRtl) },
                    onExportMarkdown = {
                        readerViewModel.exportMarkdown()
                    }
                )
            }
        }
    }
}
