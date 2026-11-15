package io.github.kreader.engine.epub

import android.content.Context
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
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.search.search

@OptIn(ExperimentalReadiumApi::class)
actual class EpubReaderEngine actual constructor() : ReaderEngine {

    private val _state = MutableStateFlow<ReaderEngineState>(ReaderEngineState.Idle)
    override val state: StateFlow<ReaderEngineState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ReaderEngineEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<ReaderEngineEvent> = _events.asSharedFlow()

    var publication: Publication? = null
        private set

    private var opener: ReadiumEpubOpener? = null

    fun initialize(context: Context) {
        if (opener == null) {
            opener = ReadiumEpubOpener(context.applicationContext)
        }
    }

    override suspend fun open(book: Book, initialLocator: Locator?): Result<Unit> {
        _state.value = ReaderEngineState.Loading("Opening ${book.title} with Readium...")

        val currentOpener = opener
        if (currentOpener == null) {
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

        return currentOpener.open(book.source, book.id).fold(
            onSuccess = { pub ->
                publication = pub
                val toc = pub.tableOfContents.map { it.toTocItem(pub) }
                val startLocator = initialLocator ?: book.lastLocator ?: pub.readingOrder.firstOrNull()?.let {
                    pub.locatorFromLink(it)?.toDomain()
                } ?: Locator.initial(
                    href = pub.readingOrder.firstOrNull()?.href?.toString() ?: "content.xhtml",
                    type = book.format.mimeType
                )

                _state.value = ReaderEngineState.Ready(
                    currentLocator = startLocator,
                    totalProgression = startLocator.locations.totalProgression ?: 0f,
                    tableOfContents = toc,
                    canGoPrevious = true,
                    canGoNext = true
                )
                Result.success(Unit)
            },
            onFailure = { error ->
                _state.value = ReaderEngineState.Error(
                    error = error,
                    userFriendlyMessage = error.message ?: "Failed to open EPUB publication"
                )
                Result.failure(error)
            }
        )
    }

    override suspend fun close() {
        publication?.close()
        publication = null
        _state.value = ReaderEngineState.Idle
    }

    override suspend fun goTo(locator: Locator): Result<Unit> {
        val currentState = _state.value
        if (currentState is ReaderEngineState.Ready) {
            val updated = currentState.copy(
                currentLocator = locator,
                totalProgression = locator.locations.totalProgression ?: currentState.totalProgression
            )
            _state.value = updated
            _events.emit(ReaderEngineEvent.PageChanged(locator))
            return Result.success(Unit)
        }
        return Result.failure(IllegalStateException("Engine not ready"))
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

    override suspend fun applyTheme(theme: ReaderTheme) {}
    override suspend fun applyTypography(typography: ReaderTypography) {}
    override suspend fun search(query: String): List<Locator> {
        val pub = publication ?: return emptyList()
        val searchTry = pub.search(query)
        val results = mutableListOf<Locator>()
        searchTry.onSuccess { iterator ->
            try {
                var page = iterator.next().getOrNull()
                var count = 0
                while (page != null && count < 100) {
                    for (loc in page.locators) {
                        results.add(loc.toDomain())
                        count++
                    }
                    page = iterator.next().getOrNull()
                }
            } finally {
                iterator.close()
            }
        }
        return results
    }
}
