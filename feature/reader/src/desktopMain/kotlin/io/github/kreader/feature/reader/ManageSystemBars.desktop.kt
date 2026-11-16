package io.github.kreader.feature.reader

import androidx.compose.runtime.Composable
import io.github.kreader.core.model.ReaderTheme

@Composable
actual fun ManageSystemBars(isControlsVisible: Boolean, theme: ReaderTheme) {
    // Desktop does not require system bar management
}
