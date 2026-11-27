package io.github.kreader.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.github.kreader.core.database.BookRepository
import io.github.kreader.core.model.Book
import io.github.kreader.core.model.BookFormat
import io.github.kreader.core.model.BookSource
import io.github.kreader.core.model.CbzSeries
import io.github.kreader.engine.cbz.CbzNaturalSort
import io.github.kreader.feature.library.metadata.BookMetadataExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.Clock
import kotlin.random.Random
import java.util.Collections

class LibraryViewModel(
    private val bookRepository: BookRepository,
    private val metadataExtractor: BookMetadataExtractor,
    private val scope: CoroutineScope
) {
    private val _uiState = MutableStateFlow(LibraryUiState(isLoading = true))
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val extractingBookIds = Collections.synchronizedSet(mutableSetOf<String>())
    private val extractionSemaphore = Semaphore(3)

    init {
        observeVaultInfo()
        observeBooks()
    }

    private fun observeVaultInfo() {
        bookRepository.activeVaultUriOrPath.onEach { uri ->
            _uiState.update { it.copy(vaultPath = uri) }
        }.launchIn(scope)

        bookRepository.activeVaultName.onEach { name ->
            _uiState.update { it.copy(vaultName = name) }
        }.launchIn(scope)
    }

    private var latestSeriesCovers: Map<String, String> = emptyMap()

    private fun observeBooks() {
        combine(
            bookRepository.observeAllBooks(),
            bookRepository.observeSeriesCovers()
        ) { allBooks, seriesCovers ->
            allBooks to seriesCovers
        }.onEach { (allBooks, seriesCovers) ->
            latestSeriesCovers = seriesCovers
            val (seriesList, allItems) = buildSeriesAndItems(allBooks, seriesCovers)
            _uiState.update { current ->
                current.copy(
                    books = allBooks,
                    seriesList = seriesList,
                    filteredBooks = filterAndSortBooks(allBooks, current.searchQuery, current.selectedFilter, current.selectedSortOrder),
                    filteredItems = filterAndSortItems(allItems, current.searchQuery, current.selectedFilter, current.selectedSortOrder),
                    isLoading = false,
                    errorMessage = null
                )
            }

            // Prioritize standalone books and first chapters of each CBZ series so covers appear immediately
            val prioritized = buildList {
                val seenSeries = mutableSetOf<String>()
                val deferred = mutableListOf<Book>()
                for (book in allBooks) {
                    val seriesFolder = book.cbzSeriesFolder
                    if (seriesFolder == null || seenSeries.add(seriesFolder)) {
                        add(book)
                    } else {
                        deferred.add(book)
                    }
                }
                addAll(deferred)
            }

            // Asynchronously extract metadata for books missing author, cover, or CBZ page count (bounded concurrency)
            for (book in prioritized) {
                val needsMetadata = book.author == null || book.coverPath == null ||
                    (book.format == BookFormat.CBZ && book.pageCount == null)
                if (needsMetadata && !extractingBookIds.contains(book.id)) {
                    extractingBookIds.add(book.id)
                    scope.launch(Dispatchers.IO) {
                        extractionSemaphore.withPermit {
                            runCatching {
                                val meta = metadataExtractor.extract(book.source, book.id)
                                val resolvedAuthor = meta.author ?: book.author ?: if (book.format == BookFormat.CBZ) "" else "Unknown Author"
                                val updated = book.copy(
                                    title = meta.title.ifBlank { book.title },
                                    author = resolvedAuthor,
                                    description = meta.description ?: book.description,
                                    coverPath = meta.coverPath ?: book.coverPath,
                                    pageCount = meta.pageCount ?: book.pageCount,
                                    isManga = meta.isManga ?: book.isManga
                                )
                                bookRepository.upsertBook(updated)
                            }
                        }
                    }
                }
            }
        }.launchIn(scope)
    }

    fun openSeries(seriesId: String) {
        _uiState.update { it.copy(selectedSeriesId = seriesId) }
    }

    fun dismissSeries() {
        _uiState.update { it.copy(selectedSeriesId = null) }
    }

    fun setVault(uriOrPath: String) {
        extractingBookIds.clear()
        bookRepository.setVault(uriOrPath)
    }

    fun refreshVault() {
        extractingBookIds.clear()
        scope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            runCatching {
                bookRepository.refreshVault()
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun clearVault() {
        extractingBookIds.clear()
        bookRepository.clearVault()
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { current ->
            val (_, allItems) = buildSeriesAndItems(current.books, latestSeriesCovers)
            current.copy(
                searchQuery = query,
                filteredBooks = filterAndSortBooks(current.books, query, current.selectedFilter, current.selectedSortOrder),
                filteredItems = filterAndSortItems(allItems, query, current.selectedFilter, current.selectedSortOrder)
            )
        }
    }

    fun onFilterChanged(filter: BookFilter) {
        _uiState.update { current ->
            val (_, allItems) = buildSeriesAndItems(current.books, latestSeriesCovers)
            current.copy(
                selectedFilter = filter,
                filteredBooks = filterAndSortBooks(current.books, current.searchQuery, filter, current.selectedSortOrder),
                filteredItems = filterAndSortItems(allItems, current.searchQuery, filter, current.selectedSortOrder)
            )
        }
    }

    fun onSortOrderChanged(sortOrder: BookSortOrder) {
        _uiState.update { current ->
            val (_, allItems) = buildSeriesAndItems(current.books, latestSeriesCovers)
            current.copy(
                selectedSortOrder = sortOrder,
                filteredBooks = filterAndSortBooks(current.books, current.searchQuery, current.selectedFilter, sortOrder),
                filteredItems = filterAndSortItems(allItems, current.searchQuery, current.selectedFilter, sortOrder)
            )
        }
    }

    fun importBook(source: BookSource, onImported: ((Book) -> Unit)? = null) {
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
                    totalProgression = 0f,
                    pageCount = metadata.pageCount,
                    isManga = metadata.isManga
                )
                bookRepository.upsertBook(book)
                onImported?.invoke(book)
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
        extractingBookIds.remove(book.id)
        scope.launch {
            runCatching {
                bookRepository.deleteBook(book.id)
            }
        }
    }

    fun deleteSeries(series: CbzSeries) {
        series.chapters.forEach { extractingBookIds.remove(it.id) }
        _uiState.update { current ->
            if (current.selectedSeriesId == series.id) current.copy(selectedSeriesId = null) else current
        }
        scope.launch {
            runCatching {
                bookRepository.deleteSeries(series.id)
            }
        }
    }

    fun deleteItems(items: List<LibraryItem>) {
        scope.launch {
            for (item in items) {
                when (item) {
                    is LibraryItem.SingleBook -> {
                        extractingBookIds.remove(item.book.id)
                        runCatching { bookRepository.deleteBook(item.book.id) }
                    }
                    is LibraryItem.Series -> {
                        item.series.chapters.forEach { extractingBookIds.remove(it.id) }
                        runCatching { bookRepository.deleteSeries(item.series.id) }
                    }
                }
            }
        }
    }

    fun resetSeriesProgress(series: CbzSeries) {
        scope.launch {
            for (chapter in series.chapters) {
                runCatching {
                    val initialLoc = io.github.kreader.core.model.Locator(
                        href = "page_0",
                        type = BookFormat.CBZ.mimeType,
                        title = "Page 1",
                        locations = io.github.kreader.core.model.Locator.Locations(
                            progression = 0f,
                            position = 1,
                            totalProgression = 0f
                        )
                    )
                    bookRepository.updateProgress(chapter.id, 0f, initialLoc, Clock.System.now().toEpochMilliseconds())
                }
            }
        }
    }

    fun deleteBooks(books: List<Book>) {
        books.forEach { extractingBookIds.remove(it.id) }
        scope.launch {
            for (book in books) {
                runCatching {
                    bookRepository.deleteBook(book.id)
                }
            }
        }
    }

    fun resetBookProgress(book: Book) {
        scope.launch {
            runCatching {
                val initialLoc = when (book.format) {
                    BookFormat.CBZ -> io.github.kreader.core.model.Locator(
                        href = "page_0",
                        type = BookFormat.CBZ.mimeType,
                        title = "Page 1",
                        locations = io.github.kreader.core.model.Locator.Locations(
                            progression = 0f,
                            position = 1,
                            totalProgression = 0f
                        )
                    )
                    BookFormat.EPUB -> io.github.kreader.core.model.Locator.initial("content.xhtml", BookFormat.EPUB.mimeType)
                }
                bookRepository.updateProgress(book.id, 0f, initialLoc, Clock.System.now().toEpochMilliseconds())
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
                BookFilter.BOOKS -> book.format == BookFormat.EPUB
                BookFilter.COMICS -> book.format == BookFormat.CBZ
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

    private fun buildSeriesAndItems(
        books: List<Book>,
        seriesCovers: Map<String, String>
    ): Pair<List<CbzSeries>, List<LibraryItem>> {
        val standaloneItems = mutableListOf<LibraryItem.SingleBook>()
        val cbzGroups = linkedMapOf<String, MutableList<Book>>()

        for (book in books) {
            val seriesFolder = book.cbzSeriesFolder
            if (seriesFolder != null) {
                cbzGroups.getOrPut(seriesFolder) { mutableListOf() }.add(book)
            } else {
                standaloneItems.add(LibraryItem.SingleBook(book))
            }
        }

        val seriesList = mutableListOf<CbzSeries>()
        val seriesItems = mutableListOf<LibraryItem.Series>()

        for ((seriesFolder, rawChapters) in cbzGroups) {
            val sortedChapters = rawChapters.sortedWith { a, b ->
                CbzNaturalSort.compare(a.source.displayName, b.source.displayName)
            }
            val folderTitle = seriesFolder.substringAfterLast('/')
            val customCover = seriesCovers[seriesFolder]
            val firstChapterCover = sortedChapters.firstOrNull { !it.coverPath.isNullOrBlank() }?.coverPath
            val lastRead = sortedChapters.mapNotNull { it.lastReadTimestamp }.maxOrNull()
            val added = sortedChapters.maxOfOrNull { it.addedTimestamp } ?: 0L
            val avgProgression = if (sortedChapters.isNotEmpty()) {
                (sortedChapters.sumOf { it.totalProgression.toDouble() } / sortedChapters.size.toDouble())
                    .toFloat()
                    .coerceIn(0f, 1f)
            } else {
                0f
            }

            val series = CbzSeries(
                id = seriesFolder,
                title = folderTitle,
                chapters = sortedChapters,
                coverPath = customCover ?: firstChapterCover,
                lastReadTimestamp = lastRead,
                addedTimestamp = added,
                totalProgression = avgProgression
            )
            seriesList.add(series)
            seriesItems.add(LibraryItem.Series(series))
        }

        return seriesList to (standaloneItems + seriesItems)
    }

    private fun filterAndSortItems(
        items: List<LibraryItem>,
        query: String,
        filter: BookFilter,
        sortOrder: BookSortOrder
    ): List<LibraryItem> {
        val lower = query.trim().lowercase()
        val filtered = items.filter { item ->
            val matchesFilter = when (filter) {
                BookFilter.ALL -> true
                BookFilter.BOOKS -> item is LibraryItem.SingleBook && item.book.format == BookFormat.EPUB
                BookFilter.COMICS -> item is LibraryItem.Series ||
                    (item is LibraryItem.SingleBook && item.book.format == BookFormat.CBZ)
            }
            val matchesQuery = if (lower.isEmpty()) {
                true
            } else {
                when (item) {
                    is LibraryItem.SingleBook ->
                        item.book.title.lowercase().contains(lower) ||
                            (item.book.author?.lowercase()?.contains(lower) == true)
                    is LibraryItem.Series ->
                        item.series.title.lowercase().contains(lower) ||
                            item.series.chapters.any { it.title.lowercase().contains(lower) }
                }
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

fun resolveContinueReadingChapter(series: CbzSeries): Book? {
    val chapters = series.chapters
    if (chapters.isEmpty()) return null
    val latestRead = chapters.filter { it.lastReadTimestamp != null }.maxByOrNull { it.lastReadTimestamp!! }
        ?: return chapters.first()
    if (latestRead.totalProgression < 0.99f) {
        return latestRead
    }
    val idx = chapters.indexOfFirst { it.id == latestRead.id }
    return if (idx >= 0 && idx + 1 < chapters.size) chapters[idx + 1] else latestRead
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
