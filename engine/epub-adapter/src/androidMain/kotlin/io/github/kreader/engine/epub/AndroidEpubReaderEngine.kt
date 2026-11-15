package io.github.kreader.engine.epub

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

actual class EpubReaderEngine actual constructor() : ReaderEngine {

    private val _state = MutableStateFlow<ReaderEngineState>(ReaderEngineState.Idle)
    override val state: StateFlow<ReaderEngineState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ReaderEngineEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<ReaderEngineEvent> = _events.asSharedFlow()

    private var currentBook: Book? = null

    override suspend fun open(book: Book, initialLocator: Locator?): Result<Unit> {
        _state.value = ReaderEngineState.Loading("Opening ${book.title} with Readium Android...")
        currentBook = book
        val startLocator = initialLocator ?: book.lastLocator ?: Locator.initial(
            href = "content.xhtml",
            type = book.format.mimeType
        )
        _state.value = ReaderEngineState.Ready(
            currentLocator = startLocator,
            totalProgression = startLocator.locations.totalProgression ?: 0f,
            canGoPrevious = false,
            canGoNext = true
        )
        return Result.success(Unit)
    }

    override suspend fun close() {
        _state.value = ReaderEngineState.Idle
        currentBook = null
    }

    override suspend fun goTo(locator: Locator): Result<Unit> {
        val currentState = _state.value
        if (currentState is ReaderEngineState.Ready) {
            _state.value = currentState.copy(
                currentLocator = locator,
                totalProgression = locator.locations.totalProgression ?: currentState.totalProgression
            )
            _events.emit(ReaderEngineEvent.PageChanged(locator))
            return Result.success(Unit)
        }
        return Result.failure(IllegalStateException("Engine not in ready state"))
    }

    override suspend fun next(): Result<Unit> {
        val currentState = _state.value
        if (currentState is ReaderEngineState.Ready) {
            val nextProg = (currentState.totalProgression + 0.05f).coerceAtMost(1f)
            val updated = currentState.currentLocator.copy(
                locations = currentState.currentLocator.locations.copy(
                    totalProgression = nextProg,
                    progression = nextProg
                )
            )
            return goTo(updated)
        }
        return Result.failure(IllegalStateException("Engine not ready"))
    }

    override suspend fun previous(): Result<Unit> {
        val currentState = _state.value
        if (currentState is ReaderEngineState.Ready) {
            val prevProg = (currentState.totalProgression - 0.05f).coerceAtLeast(0f)
            val updated = currentState.currentLocator.copy(
                locations = currentState.currentLocator.locations.copy(
                    totalProgression = prevProg,
                    progression = prevProg
                )
            )
            return goTo(updated)
        }
        return Result.failure(IllegalStateException("Engine not ready"))
    }

    override suspend fun applyTheme(theme: ReaderTheme) {
        // Platform WebView theme injection (CSS Variables & background/foreground)
    }

    override suspend fun applyTypography(typography: ReaderTypography) {
        // Platform WebView font family, sizing, line-height & RTL direction injection
    }

    override suspend fun search(query: String): List<Locator> {
        return emptyList()
    }
}
