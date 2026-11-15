package io.github.kreader.core.engine

import io.github.kreader.core.model.Locator

sealed interface ReaderEngineState {
    data object Idle : ReaderEngineState

    data class Loading(val message: String = "Loading publication...") : ReaderEngineState

    data class Ready(
        val currentLocator: Locator,
        val totalPages: Int? = null,
        val currentPageIndex: Int? = null,
        val totalProgression: Float = 0f,
        val tableOfContents: List<TocItem> = emptyList(),
        val canGoPrevious: Boolean = false,
        val canGoNext: Boolean = false
    ) : ReaderEngineState

    data class Error(
        val error: Throwable,
        val userFriendlyMessage: String
    ) : ReaderEngineState
}

sealed interface ReaderEngineEvent {
    data class PageChanged(val locator: Locator) : ReaderEngineEvent
    data class TextSelected(val selectedText: String, val locator: Locator) : ReaderEngineEvent
    data class HighlightClicked(val highlightId: String) : ReaderEngineEvent
    data class Tap(val x: Float, val y: Float) : ReaderEngineEvent
    data class ErrorOccurred(val message: String) : ReaderEngineEvent
}
