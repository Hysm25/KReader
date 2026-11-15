package io.github.kreader.feature.library.filepicker

import androidx.compose.runtime.Composable
import io.github.kreader.core.model.BookSource

@Composable
expect fun rememberFilePickerLauncher(onFilePicked: (BookSource) -> Unit): () -> Unit
