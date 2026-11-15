package io.github.kreader.feature.reader

import io.github.kreader.core.engine.ReaderEngineState
import io.github.kreader.core.engine.TocItem
import io.github.kreader.core.model.Book
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ReaderTypography

data class ReaderUiState(
    val book: Book? = null,
    val engineState: ReaderEngineState = ReaderEngineState.Idle,
    val isControlsVisible: Boolean = true,
    val isTocOpen: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val isScrollMode: Boolean = false,
    val theme: ReaderTheme = ReaderTheme(),
    val typography: ReaderTypography = ReaderTypography(lineHeightMultiplier = 1.6f),
    val currentLocator: Locator? = null,
    val totalProgression: Float = 0f,
    val tocItems: List<TocItem> = emptyList()
)
