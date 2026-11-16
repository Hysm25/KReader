package io.github.kreader.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.github.kreader.core.database.BookRepository
import io.github.kreader.core.model.Book
import io.github.kreader.core.model.BookFormat
import io.github.kreader.core.model.BookSource
import io.github.kreader.feature.library.metadata.BookMetadataExtractor
import io.github.kreader.feature.library.util.deleteLocalFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.random.Random

class LibraryViewModel(
    private val bookRepository: BookRepository,
    private val metadataExtractor: BookMetadataExtractor,
    private val scope: CoroutineScope
) {
    private val _uiState = MutableStateFlow(LibraryUiState(isLoading = true))
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        observeBooks()
    }

    private fun observeBooks() {
        bookRepository.observeAllBooks()
            .onEach { allBooks ->
                _uiState.update { current ->
                    current.copy(
                        books = allBooks,
                        filteredBooks = filterAndSortBooks(allBooks, current.searchQuery, current.selectedFilter, current.selectedSortOrder),
                        isLoading = false,
                        errorMessage = null
                    )
                }
            }
            .launchIn(scope)
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { current ->
            current.copy(
                searchQuery = query,
                filteredBooks = filterAndSortBooks(current.books, query, current.selectedFilter, current.selectedSortOrder)
            )
        }
    }

    fun onFilterChanged(filter: BookFilter) {
        _uiState.update { current ->
            current.copy(
                selectedFilter = filter,
                filteredBooks = filterAndSortBooks(current.books, current.searchQuery, filter, current.selectedSortOrder)
            )
        }
    }

    fun onSortOrderChanged(sortOrder: BookSortOrder) {
        _uiState.update { current ->
            current.copy(
                selectedSortOrder = sortOrder,
                filteredBooks = filterAndSortBooks(current.books, current.searchQuery, current.selectedFilter, sortOrder)
            )
        }
    }

    fun importBook(source: BookSource) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val bookId = "book_${Clock.System.now().toEpochMilliseconds()}_${Random.nextInt(1000, 9999)}"
                val metadata = metadataExtractor.extract(source, bookId)
                val book = Book(
                    id = bookId,
                    title = metadata.title,
                    author = metadata.author,
                    description = metadata.description,
                    coverPath = metadata.coverPath,
                    format = metadata.format,
                    source = metadata.localSource ?: source,
                    fileSizeBytes = metadata.fileSizeBytes,
                    addedTimestamp = Clock.System.now().toEpochMilliseconds(),
                    totalProgression = 0f
                )
                bookRepository.upsertBook(book)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Failed to import publication"
                    )
                }
            }
        }
    }

    fun deleteBook(book: Book) {
        scope.launch {
            runCatching {
                val cover = book.coverPath
                if (!cover.isNullOrBlank()) {
                    deleteLocalFile(cover)
                }
                val src = book.source
                if (src is BookSource.FilePath) {
                    deleteLocalFile(src.path)
                }
                bookRepository.deleteBook(book.id)
            }
        }
    }

    private fun filterAndSortBooks(
        books: List<Book>,
        query: String,
        filter: BookFilter,
        sortOrder: BookSortOrder
    ): List<Book> {
        val filtered = books.filter { book ->
            val matchesFilter = when (filter) {
                BookFilter.ALL -> true
                BookFilter.EPUB -> book.format == BookFormat.EPUB
                BookFilter.PDF -> book.format == BookFormat.PDF
            }
            val matchesQuery = if (query.isBlank()) {
                true
            } else {
                val lower = query.trim().lowercase()
                book.title.lowercase().contains(lower) || (book.author?.lowercase()?.contains(lower) == true)
            }
            matchesFilter && matchesQuery
        }

        return when (sortOrder) {
            BookSortOrder.RECENTLY_READ -> filtered.sortedByDescending { it.lastReadTimestamp ?: 0L }
            BookSortOrder.RECENTLY_ADDED -> filtered.sortedByDescending { it.addedTimestamp }
            BookSortOrder.TITLE -> filtered.sortedBy { it.title.lowercase() }
        }
    }
}

@Composable
fun rememberLibraryViewModel(
    bookRepository: BookRepository,
    metadataExtractor: BookMetadataExtractor
): LibraryViewModel {
    val scope = rememberCoroutineScope()
    return remember(bookRepository, metadataExtractor) {
        LibraryViewModel(bookRepository, metadataExtractor, scope)
    }
}
