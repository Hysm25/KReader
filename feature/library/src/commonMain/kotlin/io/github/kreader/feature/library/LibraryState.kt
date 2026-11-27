package io.github.kreader.feature.library

import androidx.compose.runtime.Immutable
import io.github.kreader.core.model.Book
import io.github.kreader.core.model.CbzSeries

enum class BookFilter {
    ALL,
    BOOKS,
    COMICS
}

enum class BookSortOrder {
    RECENTLY_READ,
    RECENTLY_ADDED,
    TITLE
}

@Immutable
sealed interface LibraryItem {
    val id: String
    val title: String
    val lastReadTimestamp: Long?
    val addedTimestamp: Long

    @Immutable
    data class SingleBook(val book: Book) : LibraryItem {
        override val id: String get() = book.id
        override val title: String get() = book.title
        override val lastReadTimestamp: Long? get() = book.lastReadTimestamp
        override val addedTimestamp: Long get() = book.addedTimestamp
    }

    @Immutable
    data class Series(val series: CbzSeries) : LibraryItem {
        override val id: String get() = "series:${series.id}"
        override val title: String get() = series.title
        override val lastReadTimestamp: Long? get() = series.lastReadTimestamp
        override val addedTimestamp: Long get() = series.addedTimestamp
    }
}

@Immutable
data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val seriesList: List<CbzSeries> = emptyList(),
    val filteredBooks: List<Book> = emptyList(),
    val filteredItems: List<LibraryItem> = emptyList(),
    val selectedSeriesId: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val selectedFilter: BookFilter = BookFilter.ALL,
    val selectedSortOrder: BookSortOrder = BookSortOrder.RECENTLY_READ,
    val errorMessage: String? = null,
    val vaultPath: String? = null,
    val vaultName: String? = null
) {
    val displayBooks: List<Book>
        get() = filteredBooks

    val displayItems: List<LibraryItem>
        get() = filteredItems

    val selectedSeries: CbzSeries?
        get() = selectedSeriesId?.let { id -> seriesList.find { it.id == id } }
}
