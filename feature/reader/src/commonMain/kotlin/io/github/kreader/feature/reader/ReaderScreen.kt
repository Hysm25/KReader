package io.github.kreader.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.kreader.core.engine.ReaderEngineState
import io.github.kreader.core.engine.TocItem
import io.github.kreader.core.model.BookFormat
import io.github.kreader.core.model.Highlight
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ReaderTypography
import io.github.kreader.core.model.ThemeMode
import kotlin.random.Random
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

@Composable
fun ReaderScreen(
    state: ReaderUiState,
    onBackClick: () -> Unit,
    onToggleControls: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onSeek: (Float) -> Unit,
    onTocClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit = {},
    onBookmarkToggle: () -> Unit = {},
    onDismissNavigationDrawer: () -> Unit = {},
    onTabSelected: (NavigationDrawerTab) -> Unit = {},
    onDeleteBookmark: (String) -> Unit = {},
    onDeleteHighlight: (String) -> Unit = {},
    onSaveHighlight: (Highlight) -> Unit = {},
    onHighlightClicked: (Highlight) -> Unit = {},
    onDismissHighlightDetail: () -> Unit = {},
    onSaveHighlightDetail: (id: String, note: String, colorHex: String) -> Unit = { _, _, _ -> },
    onOpenAddNoteDialog: (locator: Locator, selectedText: String) -> Unit = { _, _ -> },
    onDismissAddNoteDialog: () -> Unit = {},
    onSaveNewNote: (locator: Locator, note: String, colorHex: String) -> Unit = { _, _, _ -> },
    onSearchQueryChange: (String) -> Unit = {},
    onPerformSearch: ((String) -> Unit)? = null,
    onSearchResultsChange: (List<Locator>) -> Unit = {},
    onIsSearchingChange: (Boolean) -> Unit = {},
    onDismissSearch: () -> Unit = {},
    onDismissToc: () -> Unit = {},
    onDismissSettings: () -> Unit = {},
    onThemeChange: (ReaderTheme) -> Unit = {},
    onTypographyChange: (ReaderTypography) -> Unit = {},
    onScrollModeChange: (Boolean) -> Unit = {},
    onProgressChanged: (progression: Float, locator: Locator) -> Unit = { _, _ -> },
    onTableOfContentsLoaded: (List<TocItem>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val epubController = rememberEpubNavigatorController()
    val scope = rememberCoroutineScope()

    val screenBgColor = when (state.theme.mode) {
        ThemeMode.LIGHT -> Color(0xFFFFFFFF)
        ThemeMode.SEPIA -> Color(0xFFFBF0D9)
        ThemeMode.DARK -> if (state.theme.backgroundColorHex.equals("#000000", ignoreCase = true)) {
            Color(0xFF000000)
        } else {
            Color(0xFF1E1E1E)
        }
        ThemeMode.SYSTEM -> MaterialTheme.colorScheme.background
    }

    val executeSearch: () -> Unit = {
        val q = state.searchQuery
        if (q.isNotBlank()) {
            if (onPerformSearch != null) {
                onPerformSearch(q)
            } else {
                scope.launch {
                    onIsSearchingChange(true)
                    val results = epubController.search?.invoke(q) ?: emptyList()
                    onSearchResultsChange(results)
                    onIsSearchingChange(false)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(screenBgColor)
        ) {
            val book = state.book
            if (book != null && book.format == BookFormat.EPUB) {
                // Readium EPUB Navigator Host with Highlights & Selection Handling
                EpubNavigatorHost(
                    book = book,
                    initialLocator = book.lastLocator,
                    theme = state.theme,
                    typography = state.typography,
                    isScrollMode = state.isScrollMode,
                    controller = epubController,
                    highlights = state.highlights,
                    onHighlightClicked = { highlightId ->
                        val h = state.highlights.find { it.id == highlightId }
                        if (h != null) {
                            onHighlightClicked(h)
                        }
                    },
                    onAddHighlight = { locator, colorHex ->
                        val newHighlight = Highlight(
                            id = "hl_${Clock.System.now().toEpochMilliseconds()}_${Random.nextInt(1000, 9999)}",
                            bookId = book.id,
                            locator = locator,
                            colorHex = colorHex,
                            annotation = null,
                            createdTimestamp = Clock.System.now().toEpochMilliseconds(),
                            modifiedTimestamp = Clock.System.now().toEpochMilliseconds()
                        )
                        onSaveHighlight(newHighlight)
                    },
                    onAddNoteRequested = { locator, selectedText ->
                        onOpenAddNoteDialog(locator, selectedText)
                    },
                    onProgressChanged = onProgressChanged,
                    onTableOfContentsLoaded = onTableOfContentsLoaded,
                    onTap = onToggleControls,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Fallback for PDF or Other Formats
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onToggleControls
                        )
                ) {
                    when (val engineState = state.engineState) {
                        is ReaderEngineState.Loading -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                        is ReaderEngineState.Ready -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Content: ${engineState.currentLocator.href} (${(engineState.totalProgression * 100).toInt()}%)",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                        is ReaderEngineState.Error -> {
                            Text(
                                text = "Error: ${engineState.userFriendlyMessage}",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        ReaderEngineState.Idle -> {
                            Text(
                                text = "Reader Idle",
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }

            // Top Bar Overlay
            AnimatedVisibility(
                visible = state.isControlsVisible,
                enter = slideInVertically(initialOffsetY = { -it }),
                exit = slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                ReaderTopBar(
                    title = state.book?.title ?: "Reading",
                    isBookmarked = state.isCurrentBookmarked,
                    onBackClick = onBackClick,
                    onSearchClick = onSearchClick,
                    onBookmarkToggle = onBookmarkToggle,
                    onTocClick = onTocClick,
                    onSettingsClick = onSettingsClick
                )
            }

            // Bottom Bar Overlay
            AnimatedVisibility(
                visible = state.isControlsVisible,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val readyState = state.engineState as? ReaderEngineState.Ready
                val prog = if (state.book?.format == BookFormat.EPUB) {
                    state.totalProgression.takeIf { it > 0f } ?: (state.book?.totalProgression ?: 0f)
                } else {
                    readyState?.totalProgression ?: 0f
                }
                val currentPage = readyState?.currentPageIndex
                val totalPages = readyState?.totalPages
                val pageInfo = if (currentPage != null && totalPages != null) {
                    "${currentPage + 1} / $totalPages"
                } else {
                    "${(prog * 100).toInt()}%"
                }

                ReaderBottomBar(
                    progression = prog,
                    pageInfo = pageInfo,
                    onSeek = onSeek,
                    onPreviousPage = {
                        if (book?.format == BookFormat.EPUB) {
                            epubController.goPrevious?.invoke()
                        } else {
                            onPreviousPage()
                        }
                    },
                    onNextPage = {
                        if (book?.format == BookFormat.EPUB) {
                            epubController.goNext?.invoke()
                        } else {
                            onNextPage()
                        }
                    }
                )
            }

            // Navigation Drawer (Contents / TOC, Bookmarks, Highlights & Notes)
            ReaderNavigationDrawer(
                isOpen = state.isNavigationDrawerOpen,
                currentTab = state.navigationDrawerTab,
                onTabSelected = onTabSelected,
                tocItems = state.tocItems,
                bookmarks = state.bookmarks,
                highlights = state.highlights,
                currentLocator = state.currentLocator,
                onNavigateToLocator = { locator ->
                    epubController.goTo?.invoke(locator)
                    onDismissNavigationDrawer()
                },
                onDeleteBookmark = onDeleteBookmark,
                onDeleteHighlight = onDeleteHighlight,
                onHighlightClicked = onHighlightClicked,
                onDismiss = onDismissNavigationDrawer
            )

            // In-Book Full-Text Search Sheet
            ReaderSearchSheet(
                isOpen = state.isSearchOpen,
                query = state.searchQuery,
                results = state.searchResults,
                isSearching = state.isSearching,
                onQueryChange = onSearchQueryChange,
                onSearch = executeSearch,
                onResultClick = { locator ->
                    epubController.goTo?.invoke(locator)
                    onDismissSearch()
                },
                onDismiss = onDismissSearch
            )

            // Highlight Detail Dialog (View/Edit Annotation & Color, Delete)
            HighlightDetailDialog(
                highlight = state.selectedHighlightForDetail,
                onSaveAnnotation = onSaveHighlightDetail,
                onDeleteHighlight = onDeleteHighlight,
                onDismiss = onDismissHighlightDetail
            )

            // Add Note Dialog (Prompted from Text Selection)
            AddNoteDialog(
                locator = state.pendingNoteLocator,
                selectedText = state.pendingNoteText,
                onSaveNote = onSaveNewNote,
                onDismiss = onDismissAddNoteDialog
            )

            // Reading Settings Modal Sheet (Theme, Mode, Arabic Font, Tashkeel Spacing)
            ReaderSettingsSheet(
                isOpen = state.isSettingsOpen,
                theme = state.theme,
                typography = state.typography,
                isScrollMode = state.isScrollMode,
                onThemeChange = onThemeChange,
                onTypographyChange = onTypographyChange,
                onScrollModeChange = onScrollModeChange,
                onDismiss = onDismissSettings
            )
        }
    }
}
