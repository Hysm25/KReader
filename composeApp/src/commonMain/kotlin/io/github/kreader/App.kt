package io.github.kreader

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.kreader.core.model.Book
import io.github.kreader.feature.library.LibraryScreen
import io.github.kreader.feature.library.metadata.rememberBookMetadataExtractor
import io.github.kreader.feature.library.rememberLibraryViewModel
import io.github.kreader.feature.library.repository.rememberBookRepository
import io.github.kreader.feature.reader.ReaderScreen
import io.github.kreader.feature.reader.ReaderUiState

@Composable
fun App() {
    MaterialTheme {
        Surface {
            val bookRepository = rememberBookRepository()
            val metadataExtractor = rememberBookMetadataExtractor()
            val libraryViewModel = rememberLibraryViewModel(bookRepository, metadataExtractor)
            val libraryUiState by libraryViewModel.uiState.collectAsState()

            var selectedBook by remember { mutableStateOf<Book?>(null) }

            val bookToRead = selectedBook
            if (bookToRead == null) {
                LibraryScreen(
                    state = libraryUiState,
                    onBookClick = { book -> selectedBook = book },
                    onImportBook = { source -> libraryViewModel.importBook(source) },
                    onDeleteBook = { book -> libraryViewModel.deleteBook(book) },
                    onSearchQueryChanged = { query -> libraryViewModel.onSearchQueryChanged(query) }
                )
            } else {
                var readerState by remember(bookToRead.id) {
                    mutableStateOf(ReaderUiState(book = bookToRead))
                }
                ReaderScreen(
                    state = readerState,
                    onBackClick = { selectedBook = null },
                    onToggleControls = {
                        readerState = readerState.copy(isControlsVisible = !readerState.isControlsVisible)
                    },
                    onPreviousPage = {},
                    onNextPage = {},
                    onSeek = {},
                    onTocClick = {},
                    onSettingsClick = {}
                )
            }
        }
    }
}
