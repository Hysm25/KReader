package io.github.kreader.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.kreader.core.engine.ReaderEngineState
import io.github.kreader.core.engine.ReaderKeyEventDispatcher
import io.github.kreader.core.engine.TocItem
import io.github.kreader.core.model.CustomFont
import io.github.kreader.core.model.CustomTheme
import io.github.kreader.core.model.Highlight
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.OrientationLock
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
    onResetToDefaults: () -> Unit = {},
    onProgressChanged: (progression: Float, locator: Locator) -> Unit = { _, _ -> },
    onTableOfContentsLoaded: (List<TocItem>) -> Unit = {},
    customFonts: List<CustomFont> = emptyList(),
    customThemes: List<CustomTheme> = emptyList(),
    onImportCustomFont: (CustomFont) -> Unit = {},
    onDeleteCustomFont: (String) -> Unit = {},
    onSaveCustomTheme: (CustomTheme) -> Unit = {},
    onDeleteCustomTheme: (String) -> Unit = {},
    onKeepScreenOnChange: (Boolean) -> Unit = {},
    onOrientationLockChange: (OrientationLock) -> Unit = {},
    onDirectionDetected: (isRtl: Boolean) -> Unit = {},
    onExportMarkdown: (suspend () -> String?)? = null,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val epubController = rememberEpubNavigatorController()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val screenBgColor = when (state.theme.mode) {
        ThemeMode.LIGHT -> Color(0xFFFFFFFF)
        ThemeMode.SEPIA -> Color(0xFFFBF0D9)
        ThemeMode.DARK -> if (state.theme.backgroundColorHex.equals("#000000", ignoreCase = true)) {
            Color(0xFF000000)
        } else {
            Color(0xFF121212)
        }
        ThemeMode.SYSTEM -> if (isSystemDark) Color(0xFF121212) else Color(0xFFFAFAFA)
        ThemeMode.CUSTOM -> parseHexToComposeColor(state.theme.backgroundColorHex, Color.White)
    }

    // Android system back button management: close dialogs/sheets/drawers/controls first, then back to library
    BackHandler(enabled = true) {
        when {
            state.selectedHighlightForDetail != null -> onDismissHighlightDetail()
            state.pendingNoteLocator != null -> onDismissAddNoteDialog()
            state.isSettingsOpen -> onDismissSettings()
            state.isSearchOpen -> onDismissSearch()
            state.isNavigationDrawerOpen -> onDismissNavigationDrawer()
            state.isControlsVisible -> onToggleControls()
            else -> onBackClick()
        }
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

    ManageSystemBars(isControlsVisible = state.isControlsVisible, theme = state.theme)
    KeepScreenOn(enabled = state.keepScreenOn)
    ManageOrientation(orientationLock = state.orientationLock)

    // Live UI progress tracking: updates synchronously on every single page turn with zero debounce
    var liveProgression: Float by remember(state.book?.id) {
        mutableStateOf(state.totalProgression.takeIf { it > 0f } ?: (state.book?.totalProgression ?: 0f))
    }
    var liveLocator: Locator? by remember(state.book?.id) {
        mutableStateOf(state.currentLocator ?: state.book?.lastLocator)
    }

    LaunchedEffect(state.totalProgression, state.currentLocator) {
        liveProgression = state.totalProgression
        if (state.currentLocator != null) {
            liveLocator = state.currentLocator
        }
    }

    // Hardware volume button navigation
    DisposableEffect(state.book?.id, state.isRtl) {
        ReaderKeyEventDispatcher.onVolumeUp = {
            epubController.goPrevious?.invoke()
            true
        }
        ReaderKeyEventDispatcher.onVolumeDown = {
            epubController.goNext?.invoke()
            true
        }
        onDispose {
            ReaderKeyEventDispatcher.onVolumeUp = null
            ReaderKeyEventDispatcher.onVolumeDown = null
        }
    }

    MaterialTheme(colorScheme = getReaderColorScheme(state.theme)) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(screenBgColor)
        ) {
            val book = state.book
            if (book != null) {
                // Background Layer: Fixed viewport size with system bar & cutout insets (excludes IME so keyboard doesn't resize reader)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
                        .padding(
                            vertical = state.typography.paddingVertical.dp
                        )
                ) {
                    // Readium EPUB Navigator Host with Highlights, Annotations & Automatic Progression
                    EpubNavigatorHost(
                    book = book,
                    initialLocator = book.lastLocator,
                    theme = state.theme,
                    typography = state.typography,
                    isScrollMode = state.isScrollMode,
                    controller = epubController,
                    customFonts = customFonts,
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
                    onProgressChanged = { prog, loc ->
                        liveProgression = prog
                        liveLocator = loc
                        onProgressChanged(prog, loc)
                    },
                    onTableOfContentsLoaded = onTableOfContentsLoaded,
                    onDirectionDetected = onDirectionDetected,
                    onTap = onToggleControls,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
                // Fallback Idle/Error/Loading
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

            // Top Bar Overlay (floating)
            AnimatedVisibility(
                visible = state.isControlsVisible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
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

            // Bottom Bar Overlay with RTL-Aware Progress Slider (floating)
            AnimatedVisibility(
                visible = state.isControlsVisible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val prog = liveProgression
                val currentChapterTitle = state.tocItems.find {
                    val currHref = liveLocator?.href?.substringBefore('#')
                    currHref != null && it.locator.href.substringBefore('#') == currHref
                }?.title ?: liveLocator?.title
                val pageInfo = "${(prog * 100).toInt()}%"

                ReaderBottomBar(
                    progression = prog,
                    pageInfo = pageInfo,
                    chapterTitle = currentChapterTitle,
                    isRtl = state.isRtl,
                    onSeek = { targetProg ->
                        liveProgression = targetProg
                        epubController.seekToProgression?.invoke(targetProg)
                        onSeek(targetProg)
                    },
                    onPreviousPage = {
                        epubController.goPrevious?.invoke()
                    },
                    onNextPage = {
                        epubController.goNext?.invoke()
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
                onExportMarkdown = {
                    if (onExportMarkdown != null) {
                        scope.launch {
                            val path = onExportMarkdown()
                            if (path != null) {
                                snackbarHostState.showSnackbar("Exported to $path")
                            } else {
                                snackbarHostState.showSnackbar("Failed to export notes")
                            }
                        }
                    }
                },
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
                    epubController.highlightSearchResult?.invoke(locator)
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

            // Reading Settings Modal Sheet (Theme, Mode, Custom Fonts, Tashkeel Spacing)
            ReaderSettingsSheet(
                isOpen = state.isSettingsOpen,
                theme = state.theme,
                typography = state.typography,
                isScrollMode = state.isScrollMode,
                customFonts = customFonts,
                customThemes = customThemes,
                keepScreenOn = state.keepScreenOn,
                orientationLock = state.orientationLock,
                onThemeChange = onThemeChange,
                onTypographyChange = onTypographyChange,
                onScrollModeChange = onScrollModeChange,
                onResetToDefaults = onResetToDefaults,
                onImportCustomFont = onImportCustomFont,
                onDeleteCustomFont = onDeleteCustomFont,
                onSaveCustomTheme = onSaveCustomTheme,
                onDeleteCustomTheme = onDeleteCustomTheme,
                onKeepScreenOnChange = onKeepScreenOnChange,
                onOrientationLockChange = onOrientationLockChange,
                onDismiss = onDismissSettings
            )

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 16.dp)
            )
        }
    }
}
