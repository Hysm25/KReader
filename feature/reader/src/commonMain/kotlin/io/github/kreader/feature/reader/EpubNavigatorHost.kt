package io.github.kreader.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.kreader.core.engine.TocItem
import io.github.kreader.core.model.Book
import io.github.kreader.core.model.CustomFont
import io.github.kreader.core.model.Highlight
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
    controller: EpubNavigatorController,
    customFonts: List<CustomFont> = emptyList(),
    highlights: List<Highlight> = emptyList(),
    onHighlightClicked: (String) -> Unit = {},
    onAddHighlight: (id: String, locator: Locator, colorHex: String) -> Unit = { _, _, _ -> },
    onAddNoteRequested: (Locator, String) -> Unit = { _, _ -> },
    onProgressChanged: (progression: Float, locator: Locator) -> Unit,
    onTableOfContentsLoaded: (List<TocItem>) -> Unit,
    onDirectionDetected: (isRtl: Boolean) -> Unit = {},
    onTap: () -> Unit,
    modifier: Modifier = Modifier
)
