package io.github.kreader.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kreader.core.engine.TocItem
import io.github.kreader.core.model.Book
import io.github.kreader.core.model.CbzImageFit
import io.github.kreader.core.model.CbzReadingMode
import io.github.kreader.core.model.Locator

@Composable
actual fun CbzNavigatorHost(
    book: Book,
    initialLocator: Locator?,
    readingMode: CbzReadingMode,
    imageFit: CbzImageFit,
    controller: EpubNavigatorController,
    previousChapterTitle: String?,
    nextChapterTitle: String?,
    onGoToPreviousChapter: (() -> Unit)?,
    onGoToNextChapter: (() -> Unit)?,
    onProgressChanged: (progression: Float, locator: Locator) -> Unit,
    onTableOfContentsLoaded: (List<TocItem>) -> Unit,
    onPageCountLoaded: (Int) -> Unit,
    onCbzMetadataLoaded: (pageCount: Int, isMangaRtl: Boolean?, detectedMode: CbzReadingMode?) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onTap)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Reading CBZ: ${book.title}",
            style = MaterialTheme.typography.titleMedium
        )
    }
}
