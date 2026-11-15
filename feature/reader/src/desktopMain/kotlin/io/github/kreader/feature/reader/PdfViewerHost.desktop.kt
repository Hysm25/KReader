package io.github.kreader.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.kreader.core.model.Book
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.ReaderTheme

@Composable
actual fun PdfViewerHost(
    book: Book,
    initialPage: Int,
    theme: ReaderTheme,
    isScrollMode: Boolean,
    controller: PdfViewerController,
    onPageChanged: (pageIndex: Int, totalPages: Int, locator: Locator) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        Text("PDF Rendering (Desktop placeholder for ${book.title})")
    }
}
