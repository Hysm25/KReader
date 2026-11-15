package io.github.kreader.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

class PdfViewerController {
    var goToPage: ((pageIndex: Int) -> Unit)? = null
    var goNext: (() -> Unit)? = null
    var goPrevious: (() -> Unit)? = null
}

@Composable
fun rememberPdfViewerController(): PdfViewerController {
    return remember { PdfViewerController() }
}
