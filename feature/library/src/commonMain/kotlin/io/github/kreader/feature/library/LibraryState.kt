package io.github.kreader.feature.library

import io.github.kreader.core.model.Book

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val errorMessage: String? = null
)
