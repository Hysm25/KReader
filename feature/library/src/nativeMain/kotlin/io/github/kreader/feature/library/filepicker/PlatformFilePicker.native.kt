package io.github.kreader.feature.library.filepicker

import androidx.compose.runtime.Composable
import io.github.kreader.core.model.BookSource

@Composable
actual fun rememberFilePickerLauncher(onFilePicked: (BookSource) -> Unit): () -> Unit {
    return {}
}

@Composable
actual fun rememberFolderPickerLauncher(onFolderPicked: (String) -> Unit): () -> Unit {
    return {}
}
