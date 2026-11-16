package io.github.kreader.feature.reader

import androidx.compose.runtime.Composable
import io.github.kreader.core.model.ReaderTheme

@Composable
expect fun ManageSystemBars(isControlsVisible: Boolean, theme: ReaderTheme)
