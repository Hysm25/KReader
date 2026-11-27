package io.github.kreader.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.kreader.core.engine.TocItem
import io.github.kreader.core.model.Book
import io.github.kreader.core.model.CustomFont
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ReaderTypography

@Composable
expect fun EpubNavigatorHost(
    book: Book,
    initialLocator: Locator?,
    theme: ReaderTheme,
    typography: ReaderTypography,
    isScrollMode: Boolean,
    isTwoPageSpread: Boolean = true,
    controller: EpubNavigatorController,
    customFonts: List<CustomFont> = emptyList(),
    onSearchSelectedText: (String) -> Unit = {},
    onProgressChanged: (progression: Float, locator: Locator) -> Unit,
    onTableOfContentsLoaded: (List<TocItem>) -> Unit,
    onDirectionDetected: (isRtl: Boolean) -> Unit = {},
    onTap: () -> Unit,
    onPageCountLoaded: (Int) -> Unit = {},
    modifier: Modifier = Modifier
)
