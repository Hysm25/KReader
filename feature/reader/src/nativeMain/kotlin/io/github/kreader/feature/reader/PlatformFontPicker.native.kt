package io.github.kreader.feature.reader

import androidx.compose.runtime.Composable
import io.github.kreader.core.model.CustomFont

@Composable
actual fun rememberFontPickerLauncher(onFontPicked: (CustomFont) -> Unit): () -> Unit {
    return {}
}
