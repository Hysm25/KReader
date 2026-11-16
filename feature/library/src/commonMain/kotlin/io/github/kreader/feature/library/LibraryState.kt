package io.github.kreader.feature.library

import io.github.kreader.core.model.Book

enum class BookFilter {
    ALL,
    EPUB,
    PDF
}

enum class BookSortOrder {
    RECENTLY_READ,
    RECENTLY_ADDED,
    TITLE
}

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val filteredBooks: List<Book> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val selectedFilter: BookFilter = BookFilter.ALL,
    val selectedSortOrder: BookSortOrder = BookSortOrder.RECENTLY_READ,
    val errorMessage: String? = null
) {
    val displayBooks: List<Book>
        get() = filteredBooks
}
