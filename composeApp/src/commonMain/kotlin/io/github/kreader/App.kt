package io.github.kreader

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.kreader.core.model.Book
import io.github.kreader.feature.library.LibraryScreen
import io.github.kreader.feature.library.LibraryUiState
import io.github.kreader.feature.reader.ReaderScreen
import io.github.kreader.feature.reader.ReaderUiState

@Composable
fun App() {
    MaterialTheme {
        Surface {
            var selectedBook by remember { mutableStateOf<Book?>(null) }

            if (selectedBook == null) {
                LibraryScreen(
                    state = LibraryUiState(),
                    onBookClick = { book -> selectedBook = book },
                    onImportClick = { /* Handle file picker import */ }
                )
            } else {
                var readerState by remember(selectedBook) {
                    mutableStateOf(ReaderUiState(book = selectedBook))
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
