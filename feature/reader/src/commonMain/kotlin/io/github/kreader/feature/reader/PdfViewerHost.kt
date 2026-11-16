package io.github.kreader.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.kreader.core.model.Book
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.ReaderTheme

@Composable
expect fun PdfViewerHost(
    book: Book,
    initialPage: Int,
    theme: ReaderTheme,
    isScrollMode: Boolean,
    isRtl: Boolean = false,
    controller: PdfViewerController,
    onPageChanged: (pageIndex: Int, totalPages: Int, locator: Locator) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
)
