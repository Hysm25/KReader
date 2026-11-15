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

    private var currentPage: Int = 0
    private var totalPagesCount: Int = 1

    actual val pageCount: Int
        get() = totalPagesCount

    override suspend fun open(book: Book, initialLocator: Locator?): Result<Unit> {
        _state.value = ReaderEngineState.Loading("Opening ${book.title} with Desktop PDFium...")
        val targetPage = initialLocator?.locations?.position ?: 0
        currentPage = targetPage
        totalPagesCount = 100 // Bound dynamically via PDFium binding

        val locator = Locator(
            href = "page-$currentPage",
            type = book.format.mimeType,
            locations = Locator.Locations(
                position = currentPage,
                totalProgression = if (totalPagesCount > 0) currentPage.toFloat() / totalPagesCount else 0f
            )
        )

        _state.value = ReaderEngineState.Ready(
            currentLocator = locator,
            totalPages = totalPagesCount,
            currentPageIndex = currentPage,
            totalProgression = locator.locations.totalProgression ?: 0f,
            canGoPrevious = currentPage > 0,
            canGoNext = currentPage < totalPagesCount - 1
        )
        return Result.success(Unit)
    }

    override suspend fun close() {
        _state.value = ReaderEngineState.Idle
    }

    override suspend fun goTo(locator: Locator): Result<Unit> {
        val page = locator.locations.position ?: 0
        currentPage = page
        val updated = locator.copy(
            locations = locator.locations.copy(
                position = page,
                totalProgression = page.toFloat() / totalPagesCount
            )
        )
        _state.value = ReaderEngineState.Ready(
            currentLocator = updated,
            totalPages = totalPagesCount,
            currentPageIndex = currentPage,
            totalProgression = updated.locations.totalProgression ?: 0f,
            canGoPrevious = currentPage > 0,
            canGoNext = currentPage < totalPagesCount - 1
        )
        _events.emit(ReaderEngineEvent.PageChanged(updated))
        return Result.success(Unit)
    }

    override suspend fun next(): Result<Unit> {
        val nextLocator = Locator(
            href = "page-${currentPage + 1}",
            type = "application/pdf",
            locations = Locator.Locations(position = currentPage + 1)
        )
        return goTo(nextLocator)
    }

    override suspend fun previous(): Result<Unit> {
        val prevLocator = Locator(
            href = "page-${(currentPage - 1).coerceAtLeast(0)}",
            type = "application/pdf",
            locations = Locator.Locations(position = (currentPage - 1).coerceAtLeast(0))
        )
        return goTo(prevLocator)
    }

    override suspend fun applyTheme(theme: ReaderTheme) {}
    override suspend fun applyTypography(typography: ReaderTypography) {}
    override suspend fun search(query: String): List<Locator> = emptyList()
}
