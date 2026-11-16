package io.github.kreader.feature.library

import androidx.compose.runtime.Immutable
import io.github.kreader.core.model.Book

enum class BookFilter {
    ALL,
    EPUB
}

enum class BookSortOrder {
    RECENTLY_READ,
    RECENTLY_ADDED,
    TITLE
}

@Immutable
data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val filteredBooks: List<Book> = emptyList(),
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
}
