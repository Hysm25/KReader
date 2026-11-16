package io.github.kreader.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kreader.core.engine.TocItem
import io.github.kreader.core.model.Book
import io.github.kreader.core.model.CustomFont
import io.github.kreader.core.model.Highlight
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ReaderTypography

@Composable
actual fun EpubNavigatorHost(
    book: Book,
    initialLocator: Locator?,
    theme: ReaderTheme,
    typography: ReaderTypography,
    isScrollMode: Boolean,
    controller: EpubNavigatorController,
    customFonts: List<CustomFont>,
    highlights: List<Highlight>,
    onHighlightClicked: (String) -> Unit,
    onAddHighlight: (Locator, String) -> Unit,
    onAddNoteRequested: (Locator, String) -> Unit,
    onProgressChanged: (progression: Float, locator: Locator) -> Unit,
    onTableOfContentsLoaded: (List<TocItem>) -> Unit,
    onDirectionDetected: (isRtl: Boolean) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier
) {
    DisposableEffect(Unit) {
        controller.goTo = { locator ->
            onProgressChanged(locator.locations.totalProgression ?: 0f, locator)
        }
        controller.goNext = {
            val nextProg = ((initialLocator?.locations?.totalProgression ?: 0f) + 0.05f).coerceAtMost(1f)
            val updated = (initialLocator ?: Locator.initial("content.xhtml", "application/epub+zip")).copy(
                locations = Locator.Locations(totalProgression = nextProg, progression = nextProg)
            )
            onProgressChanged(nextProg, updated)
        }
        controller.goPrevious = {
            val prevProg = ((initialLocator?.locations?.totalProgression ?: 0f) - 0.05f).coerceAtLeast(0f)
            val updated = (initialLocator ?: Locator.initial("content.xhtml", "application/epub+zip")).copy(
                locations = Locator.Locations(totalProgression = prevProg, progression = prevProg)
            )
            onProgressChanged(prevProg, updated)
        }
        onDispose {
            controller.goTo = null
            controller.goNext = null
            controller.goPrevious = null
        }
    }

    LaunchedEffect(book.id) {
        onTableOfContentsLoaded(
            listOf(
                TocItem(title = "Chapter 1", locator = Locator.initial("chapter1.xhtml", "application/epub+zip")),
                TocItem(title = "Chapter 2", locator = Locator.initial("chapter2.xhtml", "application/epub+zip"))
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onTap)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Reading: ${book.title}\n(${((initialLocator?.locations?.totalProgression ?: 0f) * 100).toInt()}%)",
            style = MaterialTheme.typography.titleMedium
        )
    }
}
