package io.github.kreader.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.kreader.core.engine.TocItem
import io.github.kreader.core.model.Book
import io.github.kreader.core.model.BookFormat
import io.github.kreader.core.model.CbzImageFit
import io.github.kreader.core.model.CbzReadingMode
import io.github.kreader.core.model.Locator
import kotlin.math.roundToInt

fun cbzPageLocator(pageIndex: Int, totalPages: Int): Locator {
    val safeTotal = totalPages.coerceAtLeast(1)
    val clampedPage = pageIndex.coerceIn(0, safeTotal - 1)
    val progression = if (safeTotal <= 1) 0f else clampedPage.toFloat() / (safeTotal - 1).toFloat()
    return Locator(
        href = "page_$clampedPage",
        type = BookFormat.CBZ.mimeType,
        title = "Page ${clampedPage + 1}",
        locations = Locator.Locations(
            progression = progression,
            position = clampedPage + 1,
            totalProgression = progression
        )
    )
}

fun cbzInitialPageIndex(initialLocator: Locator?, totalPages: Int): Int {
    if (totalPages <= 0 || initialLocator == null) return 0
    val pos = initialLocator.locations.position
    if (pos != null && pos > 0) {
        return (pos - 1).coerceIn(0, totalPages - 1)
    }
    if (initialLocator.href.startsWith("page_")) {
        val parsed = initialLocator.href.removePrefix("page_").toIntOrNull()
        if (parsed != null) {
            return parsed.coerceIn(0, totalPages - 1)
        }
    }
    val prog = initialLocator.locations.totalProgression ?: initialLocator.locations.progression ?: 0f
    return (prog * (totalPages - 1).coerceAtLeast(1)).roundToInt().coerceIn(0, totalPages - 1)
}

@Composable
expect fun CbzNavigatorHost(
    book: Book,
    initialLocator: Locator?,
    readingMode: CbzReadingMode,
    imageFit: CbzImageFit,
    controller: EpubNavigatorController,
    onProgressChanged: (progression: Float, locator: Locator) -> Unit,
    onTableOfContentsLoaded: (List<TocItem>) -> Unit,
    onPageCountLoaded: (Int) -> Unit,
    onCbzMetadataLoaded: (pageCount: Int, isMangaRtl: Boolean?) -> Unit = { _, _ -> },
    onTap: () -> Unit,
    modifier: Modifier = Modifier
)
