package io.github.kreader.engine.epub

import io.github.kreader.core.engine.ReaderEngine
import io.github.kreader.core.engine.ReaderEngineEvent
import io.github.kreader.core.engine.ReaderEngineState
import io.github.kreader.core.model.Book
import io.github.kreader.core.model.BookSource
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ReaderTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

actual class EpubReaderEngine actual constructor() : ReaderEngine {

    private val _state = MutableStateFlow<ReaderEngineState>(ReaderEngineState.Idle)
    override val state: StateFlow<ReaderEngineState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ReaderEngineEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<ReaderEngineEvent> = _events.asSharedFlow()

    private var currentBook: Book? = null
    private var document: DesktopEpubDocument? = null
    private var currentSpineIndex = 0

    override suspend fun open(book: Book, initialLocator: Locator?): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            _state.value = ReaderEngineState.Loading("Opening ${book.title} on Desktop...")
            currentBook = book

            val filePath = when (val source = book.source) {
                is BookSource.FilePath -> source.path
                is BookSource.ContentUri -> source.uriString
                else -> throw IllegalArgumentException("Unsupported book source: ${book.source}")
            }

            val file = File(filePath)
            val doc = DesktopEpubDocument.open(file).getOrThrow()
            document = doc

            // Determine starting locator
            val startLocator = initialLocator ?: book.lastLocator ?: run {
                val firstSpine = doc.spine.firstOrNull()
                if (firstSpine != null) {
                    Locator.initial(firstSpine.originalHref, firstSpine.mediaType)
                } else {
                    Locator.initial("content.xhtml", "application/xhtml+xml")
                }
            }

            // Find current spine index
            currentSpineIndex = doc.spine.indexOfFirst {
                it.originalHref.equals(startLocator.href, ignoreCase = true) ||
                startLocator.href.contains(it.originalHref) ||
                it.originalHref.contains(startLocator.href)
            }.coerceAtLeast(0)

            val totalProgression = startLocator.locations.totalProgression
                ?: if (doc.spine.isNotEmpty()) (currentSpineIndex.toFloat() / doc.spine.size.toFloat()) else 0f

            _state.value = ReaderEngineState.Ready(
                currentLocator = startLocator,
                totalPages = doc.spine.size,
                currentPageIndex = currentSpineIndex,
                totalProgression = totalProgression,
                tableOfContents = doc.tableOfContents,
                canGoPrevious = currentSpineIndex > 0,
                canGoNext = currentSpineIndex < doc.spine.size - 1
            )
        }.onFailure { err ->
            _state.value = ReaderEngineState.Error(
                error = err,
                userFriendlyMessage = "Failed to open ${book.title}: ${err.message}"
            )
        }
    }

    override suspend fun close() {
        withContext(Dispatchers.IO) {
            document?.close()
            document = null
            currentBook = null
            currentSpineIndex = 0
            _state.value = ReaderEngineState.Idle
        }
    }

    override suspend fun goTo(locator: Locator): Result<Unit> = withContext(Dispatchers.Default) {
        val currentState = _state.value
        val doc = document
        if (currentState is ReaderEngineState.Ready && doc != null) {
            val spineIdx = doc.spine.indexOfFirst {
                it.originalHref.equals(locator.href, ignoreCase = true) ||
                locator.href.contains(it.originalHref) ||
                it.originalHref.contains(locator.href)
            }.coerceAtLeast(0)

            currentSpineIndex = spineIdx
            val totalProg = locator.locations.totalProgression
                ?: if (doc.spine.isNotEmpty()) (spineIdx.toFloat() / doc.spine.size.toFloat()) else 0f

            val updatedLocator = if (locator.locations.totalProgression == null) {
                locator.copy(locations = locator.locations.copy(totalProgression = totalProg))
            } else {
                locator
            }

            _state.value = currentState.copy(
                currentLocator = updatedLocator,
                currentPageIndex = spineIdx,
                totalProgression = totalProg,
                canGoPrevious = spineIdx > 0,
                canGoNext = spineIdx < doc.spine.size - 1
            )
            _events.emit(ReaderEngineEvent.PageChanged(updatedLocator))
            return@withContext Result.success(Unit)
        }
        Result.failure(IllegalStateException("Desktop engine not ready"))
    }

    override suspend fun next(): Result<Unit> {
        val doc = document ?: return Result.failure(IllegalStateException("Engine not ready"))
        if (currentSpineIndex < doc.spine.size - 1) {
            val nextSpine = doc.spine[currentSpineIndex + 1]
            val nextLocator = Locator.initial(nextSpine.originalHref, nextSpine.mediaType).copy(
                locations = Locator.Locations(
                    totalProgression = (currentSpineIndex + 1).toFloat() / doc.spine.size.toFloat(),
                    progression = 0f
                )
            )
            return goTo(nextLocator)
        }
        return Result.failure(IllegalStateException("Already at the end of the book"))
    }

    override suspend fun previous(): Result<Unit> {
        val doc = document ?: return Result.failure(IllegalStateException("Engine not ready"))
        if (currentSpineIndex > 0) {
            val prevSpine = doc.spine[currentSpineIndex - 1]
            val prevLocator = Locator.initial(prevSpine.originalHref, prevSpine.mediaType).copy(
                locations = Locator.Locations(
                    totalProgression = (currentSpineIndex - 1).toFloat() / doc.spine.size.toFloat(),
                    progression = 0f
                )
            )
            return goTo(prevLocator)
        }
        return Result.failure(IllegalStateException("Already at the beginning of the book"))
    }

    override suspend fun applyTheme(theme: ReaderTheme) {
        // Desktop Compose theme updates are observed directly in EpubNavigatorHost
    }

    override suspend fun applyTypography(typography: ReaderTypography) {
        // Desktop Compose typography updates are observed directly in EpubNavigatorHost
    }

    override suspend fun search(query: String): List<Locator> = withContext(Dispatchers.IO) {
        val doc = document ?: return@withContext emptyList()
        doc.search(query)
    }
}
