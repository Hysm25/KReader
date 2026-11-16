package io.github.kreader.feature.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.kreader.core.model.Book
import io.github.kreader.core.model.BookSource
import io.github.kreader.feature.library.filepicker.rememberFilePickerLauncher
import io.github.kreader.feature.library.util.rememberBookShareLauncher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onBookClick: (Book) -> Unit,
    onImportBook: (BookSource) -> Unit,
    onDeleteBook: (Book) -> Unit,
    onDeleteBooks: (List<Book>) -> Unit = { books -> books.forEach(onDeleteBook) },
    onSearchQueryChanged: (String) -> Unit,
    onFilterChanged: (BookFilter) -> Unit = {},
    onSortOrderChanged: (BookSortOrder) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val filePickerLauncher = rememberFilePickerLauncher { source ->
        onImportBook(source)
    }
    val bookShareLauncher = rememberBookShareLauncher()

    var selectedBookIds by remember { mutableStateOf(emptySet<String>()) }
    var showMultiDeleteDialog by remember { mutableStateOf(false) }
    val isSelectionMode = selectedBookIds.isNotEmpty()

    var isSearchActive by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = isSelectionMode) {
        selectedBookIds = emptySet()
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${selectedBookIds.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { selectedBookIds = emptySet() }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear selection"
                            )
                        }
                    },
                    actions = {
                        val allSelected = state.displayBooks.isNotEmpty() && selectedBookIds.size >= state.displayBooks.size
                        IconButton(
                            onClick = {
                                selectedBookIds = if (allSelected) {
                                    emptySet()
                                } else {
                                    state.displayBooks.map { it.id }.toSet()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SelectAll,
                                contentDescription = if (allSelected) "Deselect all" else "Select all"
                            )
                        }
                        IconButton(
                            onClick = {
                                val selectedBooks = state.books.filter { selectedBookIds.contains(it.id) }
                                bookShareLauncher(selectedBooks)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = "Share selected books"
                            )
                        }
                        IconButton(
                            onClick = { showMultiDeleteDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "Delete selected books",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        if (isSearchActive) {
                            OutlinedTextField(
                                value = state.searchQuery,
                                onValueChange = onSearchQueryChanged,
                                placeholder = { Text("Search title or author...") },
                                singleLine = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = null
                                    )
                                },
                                trailingIcon = {
                                    if (state.searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { onSearchQueryChanged("") }) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Clear search"
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 8.dp)
                            )
                        } else {
                            Column {
                                Text("KReader", fontWeight = FontWeight.Bold)
                                if (state.books.isNotEmpty()) {
                                    Text(
                                        text = "${state.books.size} ${if (state.books.size == 1) "book" else "books"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                isSearchActive = !isSearchActive
                                if (!isSearchActive) onSearchQueryChanged("")
                            }
                        ) {
                            Icon(
                                imageVector = if (isSearchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                                contentDescription = if (isSearchActive) "Close search" else "Search library"
                            )
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(
                    onClick = filePickerLauncher,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Import book"
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.books.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = state.selectedFilter == BookFilter.ALL,
                        onClick = { onFilterChanged(BookFilter.ALL) },
                        label = { Text("All") }
                    )
                    FilterChip(
                        selected = state.selectedFilter == BookFilter.EPUB,
                        onClick = { onFilterChanged(BookFilter.EPUB) },
                        label = { Text("EPUB") }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    val sortLabel = when (state.selectedSortOrder) {
                        BookSortOrder.RECENTLY_READ -> "Recent"
                        BookSortOrder.RECENTLY_ADDED -> "Added"
                        BookSortOrder.TITLE -> "Title"
                    }
                    var sortMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        FilterChip(
                            selected = true,
                            onClick = { sortMenuExpanded = true },
                            label = { Text(sortLabel) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowDropDown,
                                    contentDescription = null
                                )
                            }
                        )
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Recently Read") },
                                onClick = {
                                    onSortOrderChanged(BookSortOrder.RECENTLY_READ)
                                    sortMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Recently Added") },
                                onClick = {
                                    onSortOrderChanged(BookSortOrder.RECENTLY_ADDED)
                                    sortMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Title") },
                                onClick = {
                                    onSortOrderChanged(BookSortOrder.TITLE)
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isLoading && state.books.isEmpty() -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    state.books.isEmpty() -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(64.dp)
                                    .padding(bottom = 16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Your library is empty",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Import EPUB e-books from your device to begin reading completely offline.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(onClick = filePickerLauncher) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import Book")
                            }
                        }
                    }
                    state.displayBooks.isEmpty() -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp)
                        ) {
                            Text(
                                text = if (state.searchQuery.isNotBlank()) "No books match '${state.searchQuery}'" else "No books in this filter",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        BookGrid(
                            books = state.displayBooks,
                            selectedBookIds = selectedBookIds,
                            isSelectionMode = isSelectionMode,
                            onBookClick = { book ->
                                if (isSelectionMode) {
                                    selectedBookIds = if (selectedBookIds.contains(book.id)) {
                                        selectedBookIds - book.id
                                    } else {
                                        selectedBookIds + book.id
                                    }
                                } else {
                                    onBookClick(book)
                                }
                            },
                            onBookLongClick = { book ->
                                selectedBookIds = if (selectedBookIds.contains(book.id)) {
                                    selectedBookIds - book.id
                                } else {
                                    selectedBookIds + book.id
                                }
                            },
                            onDeleteBook = onDeleteBook
                        )
                    }
                }

                if (state.isLoading && state.books.isNotEmpty()) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                    )
                }
            }
        }
    }

    if (showMultiDeleteDialog) {
        val count = selectedBookIds.size
        AlertDialog(
            onDismissRequest = { showMultiDeleteDialog = false },
            title = { Text("Delete Books") },
            text = {
                Text("Are you sure you want to remove $count selected ${if (count == 1) "book" else "books"} and all cached data from your library?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMultiDeleteDialog = false
                        val booksToDelete = state.books.filter { selectedBookIds.contains(it.id) }
                        onDeleteBooks(booksToDelete)
                        selectedBookIds = emptySet()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMultiDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
