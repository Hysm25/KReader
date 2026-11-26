package io.github.kreader.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kreader.core.engine.TocItem
import io.github.kreader.core.model.Bookmark
import io.github.kreader.core.model.CustomFont
import io.github.kreader.core.model.CustomTheme
import io.github.kreader.core.model.Highlight
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.OrientationLock
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ReaderTypography
import io.github.kreader.core.model.ThemeMode
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

enum class DesktopSidebarTab {
    CONTENTS,
    BOOKMARKS,
    NOTES,
    SEARCH
}

/**
 * Cohesive Desktop Reader Layout (Foliate / Apple Books on desktop paradigm):
 * - Clean desktop header with book & chapter title, sidebar toggle, spread mode, font controls.
 * - Docked collapsible sidebar (TOC, Bookmarks, Highlights & Notes, Search) alongside content.
 * - Optimal reading line length (constrained single column or two-page book spread on widescreen).
 * - Desktop appearance flyout popover instead of a mobile bottom sheet.
 * - Slim status progress bar with chapter tracking and scrub slider.
 */
@Composable
fun DesktopReaderLayout(
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
    val epubController = rememberEpubNavigatorController()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Desktop UI State: Docked Sidebar & Appearance Popover
    var isSidebarOpen by remember { mutableStateOf(false) }
    var selectedSidebarTab by remember { mutableStateOf(DesktopSidebarTab.CONTENTS) }
    var showAppearancePopover by remember { mutableStateOf(false) }
    var isTwoPageSpread by remember { mutableStateOf(true) }

    // Resolve screen background color from theme
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val screenBgColor = when (state.theme.mode) {
        ThemeMode.LIGHT -> Color(0xFFFFFFFF)
        ThemeMode.SEPIA -> Color(0xFFFBF0D9)
        ThemeMode.DARK -> if (state.theme.backgroundColorHex.equals("#000000", ignoreCase = true)) Color(0xFF000000) else Color(0xFF141414)
        ThemeMode.SYSTEM -> if (isSystemDark) Color(0xFF141414) else Color(0xFFFAFAFA)
        ThemeMode.SYSTEM_LIGHT -> Color(0xFFFAFAFA)
        ThemeMode.CUSTOM -> parseHexToComposeColor(state.theme.backgroundColorHex, Color.White)
    }

    var liveProgression: Float by remember(state.book?.id) {
        mutableFloatStateOf(state.totalProgression.takeIf { it > 0f } ?: (state.book?.totalProgression ?: 0f))
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

    val rootFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        rootFocusRequester.requestFocus()
    }

    // Keyboard Back / Escape navigation
    BackHandler(enabled = true) {
        when {
            showAppearancePopover -> showAppearancePopover = false
            state.selectedHighlightForDetail != null -> onDismissHighlightDetail()
            state.pendingNoteLocator != null -> onDismissAddNoteDialog()
            isSidebarOpen -> isSidebarOpen = false
            else -> onBackClick()
        }
    }

    MaterialTheme(colorScheme = getReaderColorScheme(state.theme)) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(screenBgColor)
                .focusRequester(rootFocusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        val isCtrl = event.isCtrlPressed || event.isMetaPressed
                        when {
                            event.key == Key.Escape -> {
                                when {
                                    showAppearancePopover -> { showAppearancePopover = false; true }
                                    state.selectedHighlightForDetail != null -> { onDismissHighlightDetail(); true }
                                    state.pendingNoteLocator != null -> { onDismissAddNoteDialog(); true }
                                    isSidebarOpen -> { isSidebarOpen = false; true }
                                    else -> { onBackClick(); true }
                                }
                            }
                            isCtrl && event.key == Key.F -> {
                                isSidebarOpen = true
                                selectedSidebarTab = DesktopSidebarTab.SEARCH
                                true
                            }
                            isCtrl && event.key == Key.T -> {
                                isSidebarOpen = !isSidebarOpen
                                true
                            }
                            isCtrl && event.key == Key.B -> {
                                isSidebarOpen = true
                                selectedSidebarTab = DesktopSidebarTab.BOOKMARKS
                                true
                            }
                            else -> false
                        }
                    } else false
                }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. Desktop Top Header Bar
                DesktopReaderHeader(
                    bookTitle = state.book?.title ?: "Reading",
                    currentChapterTitle = state.tocItems.find {
                        val currHref = liveLocator?.href?.substringBefore('#')
                        currHref != null && it.locator.href.substringBefore('#') == currHref
                    }?.title ?: liveLocator?.title,
                    isBookmarked = state.isCurrentBookmarked,
                    isSidebarOpen = isSidebarOpen,
                    isTwoPageSpread = isTwoPageSpread,
                    onBackClick = onBackClick,
                    onToggleSidebar = { isSidebarOpen = !isSidebarOpen },
                    onToggleSearch = {
                        isSidebarOpen = true
                        selectedSidebarTab = DesktopSidebarTab.SEARCH
                    },
                    onToggleBookmark = onBookmarkToggle,
                    onToggleSpread = { isTwoPageSpread = !isTwoPageSpread },
                    onToggleAppearance = { showAppearancePopover = !showAppearancePopover }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                    thickness = 0.5.dp
                )

                // 2. Middle Row: Docked Sidebar + Reading Viewport
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // Docked Collapsible Sidebar
                    AnimatedVisibility(
                        visible = isSidebarOpen,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally()
                    ) {
                        DesktopReaderSidebar(
                            currentTab = selectedSidebarTab,
                            onTabSelected = { selectedSidebarTab = it },
                            tocItems = state.tocItems,
                            bookmarks = state.bookmarks,
                            highlights = state.highlights,
                            currentLocator = liveLocator,
                            searchQuery = state.searchQuery,
                            searchResults = state.searchResults,
                            isSearching = state.isSearching,
                            onSearchQueryChange = onSearchQueryChange,
                            onPerformSearch = executeSearch,
                            onNavigateToLocator = { locator ->
                                epubController.goTo?.invoke(locator)
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
                            onClose = { isSidebarOpen = false },
                            modifier = Modifier
                                .width(340.dp)
                                .fillMaxHeight()
                        )
                    }

                    if (isSidebarOpen) {
                        VerticalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            thickness = 0.5.dp
                        )
                    }

                    // Reading Viewport Area
                    val book = state.book
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (book != null) {
                            EpubNavigatorHost(
                                book = book,
                                initialLocator = liveLocator ?: book.lastLocator,
                                theme = state.theme,
                                typography = state.typography,
                                isScrollMode = state.isScrollMode,
                                isTwoPageSpread = isTwoPageSpread,
                                controller = epubController,
                                customFonts = customFonts,
                                highlights = state.highlights,
                                onHighlightClicked = { highlightId ->
                                    val h = state.highlights.find { it.id == highlightId }
                                    if (h != null) onHighlightClicked(h)
                                },
                                onAddHighlight = { id, locator, colorHex ->
                                    val newHighlight = Highlight(
                                        id = id,
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
                        } else {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                    thickness = 0.5.dp
                )

                // 3. Desktop Bottom Status & Scrub Bar
                DesktopReaderBottomBar(
                    progression = liveProgression,
                    chapterTitle = state.tocItems.find {
                        val currHref = liveLocator?.href?.substringBefore('#')
                        currHref != null && it.locator.href.substringBefore('#') == currHref
                    }?.title ?: liveLocator?.title,
                    isRtl = state.isRtl,
                    onSeek = { target ->
                        liveProgression = target
                        epubController.seekToProgression?.invoke(target)
                        onSeek(target)
                    },
                    onPreviousPage = { epubController.goPrevious?.invoke() },
                    onNextPage = { epubController.goNext?.invoke() }
                )
            }

            // Desktop Appearance & Typography Popover (Dropdown Dialog)
            if (showAppearancePopover) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { showAppearancePopover = false }
                        )
                ) {
                    DesktopAppearancePopover(
                        theme = state.theme,
                        typography = state.typography,
                        isScrollMode = state.isScrollMode,
                        isTwoPageSpread = isTwoPageSpread,
                        customFonts = customFonts,
                        customThemes = customThemes,
                        onThemeChange = onThemeChange,
                        onTypographyChange = onTypographyChange,
                        onScrollModeChange = onScrollModeChange,
                        onTwoPageSpreadChange = { isTwoPageSpread = it },
                        onResetToDefaults = onResetToDefaults,
                        onImportCustomFont = onImportCustomFont,
                        onDeleteCustomFont = onDeleteCustomFont,
                        onSaveCustomTheme = onSaveCustomTheme,
                        onDeleteCustomTheme = onDeleteCustomTheme,
                        onDismiss = { showAppearancePopover = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 56.dp, end = 20.dp)
                    )
                }
            }

            // Highlight Detail Dialog
            HighlightDetailDialog(
                highlight = state.selectedHighlightForDetail,
                onSaveAnnotation = onSaveHighlightDetail,
                onDeleteHighlight = onDeleteHighlight,
                onDismiss = onDismissHighlightDetail
            )

            // Add Note Dialog
            AddNoteDialog(
                locator = state.pendingNoteLocator,
                selectedText = state.pendingNoteText,
                onSaveNote = onSaveNewNote,
                onDismiss = onDismissAddNoteDialog
            )

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
            )
        }
    }
}

/**
 * Desktop Top Header Bar.
 */
@Composable
private fun DesktopReaderHeader(
    bookTitle: String,
    currentChapterTitle: String?,
    isBookmarked: Boolean,
    isSidebarOpen: Boolean,
    isTwoPageSpread: Boolean,
    onBackClick: () -> Unit,
    onToggleSidebar: () -> Unit,
    onToggleSearch: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleSpread: () -> Unit,
    onToggleAppearance: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Back & Sidebar Toggles
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to Library",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onToggleSidebar,
                    colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(
                        containerColor = if (isSidebarOpen) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                        contentDescription = "Sidebar (TOC / Notes / Search)",
                        tint = if (isSidebarOpen) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(onClick = onToggleSearch) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search in Book",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Center: Book Title & Chapter Heading
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = bookTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!currentChapterTitle.isNullOrBlank()) {
                    Text(
                        text = currentChapterTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right: Bookmark, Spread Toggle, Appearance
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleBookmark) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = "Bookmark",
                        tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onToggleSpread,
                    colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(
                        containerColor = if (isTwoPageSpread) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoStories,
                        contentDescription = "Two-Page Spread Toggle",
                        tint = if (isTwoPageSpread) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onToggleAppearance) {
                    Icon(
                        imageVector = Icons.Rounded.FormatSize,
                        contentDescription = "Appearance & Typography",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Dockable Desktop Sidebar containing TOC, Bookmarks, Notes, and Search.
 */
@Composable
private fun DesktopReaderSidebar(
    currentTab: DesktopSidebarTab,
    onTabSelected: (DesktopSidebarTab) -> Unit,
    tocItems: List<TocItem>,
    bookmarks: List<Bookmark>,
    highlights: List<Highlight>,
    currentLocator: Locator?,
    searchQuery: String,
    searchResults: List<Locator>,
    isSearching: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onPerformSearch: () -> Unit,
    onNavigateToLocator: (Locator) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    onDeleteHighlight: (String) -> Unit,
    onHighlightClicked: (Highlight) -> Unit,
    onExportMarkdown: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Sidebar Header with Tabs & Close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TabRow(
                    selectedTabIndex = currentTab.ordinal,
                    modifier = Modifier.weight(1f),
                    containerColor = Color.Transparent
                ) {
                    Tab(
                        selected = currentTab == DesktopSidebarTab.CONTENTS,
                        onClick = { onTabSelected(DesktopSidebarTab.CONTENTS) },
                        text = { Text("TOC", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = currentTab == DesktopSidebarTab.BOOKMARKS,
                        onClick = { onTabSelected(DesktopSidebarTab.BOOKMARKS) },
                        text = { Text("Bookmarks", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = currentTab == DesktopSidebarTab.NOTES,
                        onClick = { onTabSelected(DesktopSidebarTab.NOTES) },
                        text = { Text("Notes", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = currentTab == DesktopSidebarTab.SEARCH,
                        onClick = { onTabSelected(DesktopSidebarTab.SEARCH) },
                        text = { Text("Search", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    )
                }

                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close Sidebar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                thickness = 0.5.dp
            )

            // Sidebar Body
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (currentTab) {
                    DesktopSidebarTab.CONTENTS -> {
                        if (tocItems.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No Table of Contents available",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                items(tocItems) { item ->
                                    val isCurrent = currentLocator?.href?.startsWith(item.locator.href.substringBefore('#')) == true
                                    Surface(
                                        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onNavigateToLocator(item.locator) }
                                    ) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    DesktopSidebarTab.BOOKMARKS -> {
                        if (bookmarks.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No bookmarks added yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                items(bookmarks, key = { it.id }) { bm ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onNavigateToLocator(bm.locator) }
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = bm.title.orEmpty().ifBlank { "Bookmark" },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            val pct = (bm.locator.locations.progression?.times(100))?.toInt() ?: 0
                                            Text(
                                                text = "$pct% through chapter",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDeleteBookmark(bm.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    DesktopSidebarTab.NOTES -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Export to Markdown button
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${highlights.size} highlights & notes",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TextButton(onClick = onExportMarkdown) {
                                        Icon(Icons.AutoMirrored.Rounded.Notes, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Export to Vault", fontSize = 12.sp)
                                    }
                                }
                            }

                            if (highlights.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "No highlights or notes yet",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    items(highlights, key = { it.id }) { hl ->
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                                .clickable {
                                                    onHighlightClicked(hl)
                                                    onNavigateToLocator(hl.locator)
                                                }
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                val quote = hl.locator.text?.highlight ?: ""
                                                if (quote.isNotBlank()) {
                                                    Text(
                                                        text = "\"$quote\"",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 3,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                val annotation = hl.annotation
                                                if (!annotation.isNullOrBlank()) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = annotation,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    DesktopSidebarTab.SEARCH -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Search input field
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = onSearchQueryChange,
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(onSearch = { onPerformSearch() }),
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        modifier = Modifier
                                            .weight(1f)
                                            .onKeyEvent { keyEvent ->
                                                if (keyEvent.type == KeyEventType.KeyUp &&
                                                    (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                                                ) {
                                                    onPerformSearch()
                                                    true
                                                } else {
                                                    false
                                                }
                                            },
                                        decorationBox = { innerTextField ->
                                            if (searchQuery.isEmpty()) {
                                                Text(
                                                    text = "Search in this book...",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { onSearchQueryChange("") },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Clear",
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (searchResults.isNotEmpty()) "${searchResults.size} matches" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = onPerformSearch,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Search", fontSize = 12.sp)
                                }
                            }

                            if (isSearching) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    items(searchResults) { loc ->
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onNavigateToLocator(loc) }
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)
                                        ) {
                                            Text(
                                                text = loc.text?.highlight ?: loc.title ?: loc.href,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(8.dp),
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Desktop Appearance & Typography Popover (Dropdown / Flyout Card).
 */
@Composable
private fun DesktopAppearancePopover(
    theme: ReaderTheme,
    typography: ReaderTypography,
    isScrollMode: Boolean,
    isTwoPageSpread: Boolean,
    customFonts: List<CustomFont>,
    customThemes: List<CustomTheme>,
    onThemeChange: (ReaderTheme) -> Unit,
    onTypographyChange: (ReaderTypography) -> Unit,
    onScrollModeChange: (Boolean) -> Unit,
    onTwoPageSpreadChange: (Boolean) -> Unit,
    onResetToDefaults: () -> Unit,
    onImportCustomFont: (CustomFont) -> Unit,
    onDeleteCustomFont: (String) -> Unit,
    onSaveCustomTheme: (CustomTheme) -> Unit,
    onDeleteCustomTheme: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(360.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Display & Typography",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Reading Theme Selection
            Text("Theme", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val themes = listOf(
                    Triple("Light", ThemeMode.LIGHT, Color(0xFFFFFFFF)),
                    Triple("Sepia", ThemeMode.SEPIA, Color(0xFFFBF0D9)),
                    Triple("Dark", ThemeMode.DARK, Color(0xFF1E293B)),
                    Triple("Black", ThemeMode.DARK, Color(0xFF000000))
                )
                themes.forEach { (name, mode, color) ->
                    val isSelected = theme.mode == mode && (if (color == Color(0xFF000000)) theme.backgroundColorHex == "#000000" else true)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = if (isSelected) 2.dp else 0.5.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                val hex = if (color == Color(0xFF000000)) "#000000" else if (mode == ThemeMode.LIGHT) "#FFFFFF" else if (mode == ThemeMode.SEPIA) "#FBF0D9" else "#1E293B"
                                val textHex = if (mode == ThemeMode.LIGHT || mode == ThemeMode.SEPIA) "#1E293B" else "#E2E8F0"
                                onThemeChange(theme.copy(mode = mode, backgroundColorHex = hex, textColorHex = textHex))
                            },
                        color = color,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (mode == ThemeMode.LIGHT || mode == ThemeMode.SEPIA) Color(0xFF1E293B) else Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Font Family Selection
            Text("Font Family", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            val baseFonts = listOf("Default", "Serif", "Sans", "Monospace")
            val allFonts = baseFonts + customFonts.map { it.name }.filterNot { it in baseFonts }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                allFonts.take(4).forEach { fontName ->
                    val isSelected = if (!typography.overrideFont || typography.fontFamily.isBlank() || typography.fontFamily.equals("Default", ignoreCase = true)) {
                        fontName == "Default"
                    } else {
                        typography.fontFamily.equals(fontName, ignoreCase = true)
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            val override = fontName != "Default"
                            onTypographyChange(typography.copy(fontFamily = fontName, overrideFont = override))
                        },
                        label = { Text(fontName, fontSize = 11.sp) },
                        modifier = Modifier.height(32.dp)
                    )
                }
            }
            if (allFonts.size > 4) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allFonts.drop(4).forEach { fontName ->
                        val isSelected = typography.overrideFont && typography.fontFamily.equals(fontName, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                onTypographyChange(typography.copy(fontFamily = fontName, overrideFont = true))
                            },
                            label = { Text(fontName, fontSize = 11.sp) },
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Font Size Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Font Size", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text("${typography.fontSizeSp.toInt()} sp", style = MaterialTheme.typography.labelMedium)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        val newSize = (typography.fontSizeSp - 1f).coerceAtLeast(10f)
                        onTypographyChange(typography.copy(fontSizeSp = newSize))
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("A-", fontSize = 12.sp)
                }
                Slider(
                    value = typography.fontSizeSp,
                    onValueChange = { onTypographyChange(typography.copy(fontSizeSp = it)) },
                    valueRange = 10f..36f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                OutlinedButton(
                    onClick = {
                        val newSize = (typography.fontSizeSp + 1f).coerceAtMost(36f)
                        onTypographyChange(typography.copy(fontSizeSp = newSize))
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("A+", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Layout: Two-Page Spread vs Single Page
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Two-Page Book Spread", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = isTwoPageSpread,
                    onCheckedChange = onTwoPageSpreadChange
                )
            }

            // 4. Layout: Continuous Scroll vs Paginated
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Continuous Scroll", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = isScrollMode,
                    onCheckedChange = onScrollModeChange
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 5. Line Height Multiplier
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Line Spacing", style = MaterialTheme.typography.labelMedium)
                Text("${(typography.lineHeightMultiplier * 10).toInt() / 10f}x", style = MaterialTheme.typography.labelSmall)
            }
            Slider(
                value = typography.lineHeightMultiplier,
                onValueChange = { onTypographyChange(typography.copy(lineHeightMultiplier = it)) },
                valueRange = 1.1f..2.2f,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Desktop Bottom Bar with Progression Scrubbing and Clean Status Display.
 */
@Composable
private fun DesktopReaderBottomBar(
    progression: Float,
    chapterTitle: String?,
    isRtl: Boolean,
    onSeek: (Float) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit
) {
    var sliderValue by remember(progression) { mutableFloatStateOf(progression) }
    var isDragging by remember { mutableStateOf(false) }

    val currentDisplayProg = if (isDragging) sliderValue else progression
    val pct = (currentDisplayProg * 100).toInt()
    val remainingPct = (1f - currentDisplayProg).coerceIn(0f, 1f)
    val remainingMinutes = (remainingPct * 240).toInt()
    val timeText = when {
        remainingPct <= 0.005f -> "Finished"
        remainingMinutes < 1 -> "< 1 min left"
        remainingMinutes < 60 -> "~$remainingMinutes min left"
        else -> "~${remainingMinutes / 60} hr ${remainingMinutes % 60} min left"
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Previous Page Button
            IconButton(
                onClick = onPreviousPage,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.NavigateBefore,
                    contentDescription = "Previous Page",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Slider & Progress Center Block
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    value = currentDisplayProg.coerceIn(0f, 1f),
                    onValueChange = {
                        isDragging = true
                        sliderValue = it
                    },
                    onValueChangeFinished = {
                        isDragging = false
                        onSeek(sliderValue)
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = "$pct% • $timeText",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Next Page Button
            IconButton(
                onClick = onNextPage,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.NavigateNext,
                    contentDescription = "Next Page",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
