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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.kreader.core.engine.ReaderEngineState
import io.github.kreader.core.engine.TocItem
import io.github.kreader.core.model.BookFormat
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ReaderTypography
import io.github.kreader.core.model.ThemeMode

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
                // Readium EPUB Navigator Host
                EpubNavigatorHost(
                    book = book,
                    initialLocator = book.lastLocator,
                    theme = state.theme,
                    typography = state.typography,
                    isScrollMode = state.isScrollMode,
                    controller = epubController,
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
                    onBackClick = onBackClick,
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

            // Table of Contents Modal Sheet
            ReaderTocSheet(
                isOpen = state.isTocOpen,
                tocItems = state.tocItems,
                onItemClick = { locator ->
                    epubController.goTo?.invoke(locator)
                    onDismissToc()
                },
                onDismiss = onDismissToc
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
