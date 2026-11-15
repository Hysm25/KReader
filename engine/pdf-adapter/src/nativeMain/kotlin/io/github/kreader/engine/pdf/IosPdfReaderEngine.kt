package io.github.kreader.engine.pdf

import io.github.kreader.core.engine.ReaderEngine
import io.github.kreader.core.engine.ReaderEngineEvent
import io.github.kreader.core.engine.ReaderEngineState
import io.github.kreader.core.model.Book
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ReaderTypography
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

actual class PdfReaderEngine actual constructor() : ReaderEngine {

    private val _state = MutableStateFlow<ReaderEngineState>(ReaderEngineState.Idle)
    override val state: StateFlow<ReaderEngineState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ReaderEngineEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<ReaderEngineEvent> = _events.asSharedFlow()

    override suspend fun open(book: Book, initialLocator: Locator?): Result<Unit> {
        val locator = initialLocator ?: book.lastLocator ?: Locator.initial("page-0", "application/pdf")
        _state.value = ReaderEngineState.Ready(
            currentLocator = locator,
            totalPages = 1,
            currentPageIndex = 0,
            totalProgression = 0f,
            canGoPrevious = false,
            canGoNext = false
        )
        return Result.success(Unit)
    }

    override suspend fun close() {
        _state.value = ReaderEngineState.Idle
    }

    override suspend fun goTo(locator: Locator): Result<Unit> {
        _events.emit(ReaderEngineEvent.PageChanged(locator))
        return Result.success(Unit)
    }

    override suspend fun next(): Result<Unit> = Result.success(Unit)
    override suspend fun previous(): Result<Unit> = Result.success(Unit)
    override suspend fun applyTheme(theme: ReaderTheme) {}
    override suspend fun applyTypography(typography: ReaderTypography) {}
    override suspend fun search(query: String): List<Locator> = emptyList()
}
