package io.github.kreader.engine.pdf

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import io.github.kreader.core.engine.ReaderEngine
import io.github.kreader.core.engine.ReaderEngineEvent
import io.github.kreader.core.engine.ReaderEngineState
import io.github.kreader.core.model.Book
import io.github.kreader.core.model.BookSource
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ReaderTypography
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

actual class PdfReaderEngine actual constructor() : ReaderEngine {

    private val _state = MutableStateFlow<ReaderEngineState>(ReaderEngineState.Idle)
    override val state: StateFlow<ReaderEngineState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ReaderEngineEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<ReaderEngineEvent> = _events.asSharedFlow()

    private var fileDescriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var currentPage: Int = 0
    private var totalPagesCount: Int = 0

    override suspend fun open(book: Book, initialLocator: Locator?): Result<Unit> = runCatching {
        _state.value = ReaderEngineState.Loading("Opening ${book.title} with Android PdfRenderer...")
        
        val file = when (val src = book.source) {
            is BookSource.FilePath -> File(src.path)
            else -> File(book.id)
        }

        if (file.exists()) {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            fileDescriptor = pfd
            val pdfRenderer = PdfRenderer(pfd)
            renderer = pdfRenderer
            totalPagesCount = pdfRenderer.pageCount
        } else {
            totalPagesCount = 1
        }

        val targetPage = initialLocator?.locations?.position ?: 0
        currentPage = targetPage.coerceIn(0, (totalPagesCount - 1).coerceAtLeast(0))

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
    }

    override suspend fun close() {
        renderer?.close()
        renderer = null
        fileDescriptor?.close()
        fileDescriptor = null
        _state.value = ReaderEngineState.Idle
    }

    override suspend fun goTo(locator: Locator): Result<Unit> {
        val page = locator.locations.position ?: 0
        if (page in 0 until totalPagesCount) {
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
        return Result.failure(IndexOutOfBoundsException("Page $page out of range [0, $totalPagesCount)"))
    }

    override suspend fun next(): Result<Unit> {
        return if (currentPage < totalPagesCount - 1) {
            val nextLocator = Locator(
                href = "page-${currentPage + 1}",
                type = "application/pdf",
                locations = Locator.Locations(position = currentPage + 1)
            )
            goTo(nextLocator)
        } else {
            Result.success(Unit)
        }
    }

    override suspend fun previous(): Result<Unit> {
        return if (currentPage > 0) {
            val prevLocator = Locator(
                href = "page-${currentPage - 1}",
                type = "application/pdf",
                locations = Locator.Locations(position = currentPage - 1)
            )
            goTo(prevLocator)
        } else {
            Result.success(Unit)
        }
    }

    override suspend fun applyTheme(theme: ReaderTheme) {}
    override suspend fun applyTypography(typography: ReaderTypography) {}
    override suspend fun search(query: String): List<Locator> = emptyList()
}
