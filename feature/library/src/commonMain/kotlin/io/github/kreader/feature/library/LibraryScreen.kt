package io.github.kreader.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kreader.core.model.Book
import io.github.kreader.feature.library.filepicker.rememberFolderPickerLauncher
import io.github.kreader.feature.library.util.rememberBookShareLauncher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onBookClick: (Book) -> Unit,
    onSelectVaultFolder: (String) -> Unit = {},
    onRefreshVault: () -> Unit = {},
    onChangeVaultFolder: () -> Unit = {},
    onOpenGlobalSettings: () -> Unit = {},
    onDeleteBook: (Book) -> Unit,
    onDeleteBooks: (List<Book>) -> Unit = { books -> books.forEach(onDeleteBook) },
    onResetBookProgress: (Book) -> Unit = {},
    onSearchQueryChanged: (String) -> Unit,
    onSortOrderChanged: (BookSortOrder) -> Unit = {},
    onExportBookToMarkdown: (suspend (Book) -> String?)? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val folderPickerLauncher = rememberFolderPickerLauncher { uriOrPath ->
        onSelectVaultFolder(uriOrPath)
    }
    val bookShareLauncher = rememberBookShareLauncher()

    if (io.github.kreader.feature.library.util.isDesktopPlatform) {
        DesktopLibraryLayout(
            state = state,
            onBookClick = onBookClick,
            onSelectVaultFolder = onSelectVaultFolder,
            onRefreshVault = onRefreshVault,
            onChangeVaultFolder = onChangeVaultFolder,
            onOpenGlobalSettings = onOpenGlobalSettings,
            onDeleteBook = onDeleteBook,
            onDeleteBooks = onDeleteBooks,
            onResetBookProgress = onResetBookProgress,
            onSearchQueryChanged = onSearchQueryChanged,
            onSortOrderChanged = onSortOrderChanged,
            onExportBookToMarkdown = onExportBookToMarkdown,
            modifier = modifier
        )
        return
    }

    var selectedBookIds by remember { mutableStateOf(emptySet<String>()) }
    var showMultiDeleteDialog by remember { mutableStateOf(false) }
    val isSelectionMode = selectedBookIds.isNotEmpty()

    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = isSelectionMode || state.searchQuery.isNotEmpty()) {
        if (isSelectionMode) {
            selectedBookIds = emptySet()
        } else if (state.searchQuery.isNotEmpty()) {
            onSearchQueryChanged("")
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
        }
    }

    // Onboarding Screen when no vault is configured
    if (state.vaultPath == null) {
        VaultOnboardingView(
            onSelectFolder = folderPickerLauncher,
            modifier = modifier
        )
        return
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
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                BasicTextField(
                                    value = state.searchQuery,
                                    onValueChange = onSearchQueryChanged,
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { innerTextField ->
                                        if (state.searchQuery.isEmpty()) {
                                            Text(
                                                text = if (state.books.isNotEmpty()) "Search in ${state.vaultName ?: "Books"}..." else "Search library...",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        innerTextField()
                                    }
                                )
                                if (state.searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { onSearchQueryChanged("") },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Clear search",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        IconButton(onClick = onRefreshVault) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh Vault",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = { folderPickerLauncher() }) {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = "Change Folder",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = onOpenGlobalSettings) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Global Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Sort bar (redundant All / EPUB chips removed per requirement 5)
            if (state.books.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
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

                    val bookCountText = "${state.books.size} ${if (state.books.size == 1) "book" else "books"}"
                    val headerText = if (!state.vaultName.isNullOrBlank()) {
                        "${state.vaultName} • $bookCountText"
                    } else {
                        bookCountText
                    }
                    Text(
                        text = headerText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isLoading || state.isRefreshing -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    state.books.isEmpty() -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No EPUBs in \"${state.vaultName ?: "this folder"}\"",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Add .epub files into this folder on your device and tap Refresh.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = onRefreshVault,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Refresh Vault")
                                }
                                TextButton(onClick = { folderPickerLauncher() }) {
                                    Text("Change Folder")
                                }
                            }
                        }
                    }
                    state.displayBooks.isEmpty() && state.searchQuery.isNotEmpty() -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No results for \"${state.searchQuery}\"",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { onSearchQueryChanged("") }) {
                                Text("Clear Search")
                            }
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
                                if (!isSelectionMode) {
                                    selectedBookIds = setOf(book.id)
                                }
                            },
                            onDeleteBook = onDeleteBook,
                            onResetProgress = onResetBookProgress,
                            onExportMarkdown = { book ->
                                if (onExportBookToMarkdown != null) {
                                    scope.launch {
                                        val path = onExportBookToMarkdown(book)
                                        if (path != null) {
                                            snackbarHostState.showSnackbar("Exported to $path")
                                        } else {
                                            snackbarHostState.showSnackbar("Failed to export notes")
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    if (showMultiDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showMultiDeleteDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete ${selectedBookIds.size} Books from Vault?") },
            text = {
                Text(
                    "This will permanently delete the selected ${selectedBookIds.size} EPUB file(s) from your books folder, along with their reading progress and annotations."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMultiDeleteDialog = false
                        val booksToDelete = state.books.filter { selectedBookIds.contains(it.id) }
                        onDeleteBooks(booksToDelete)
                        selectedBookIds = emptySet()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete ${selectedBookIds.size} Files", color = MaterialTheme.colorScheme.onError)
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

@Composable
fun VaultOnboardingView(
    onSelectFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth()
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Welcome to KReader",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "KReader works directly with your books folder like Obsidian. Choose a folder containing your EPUBs — your settings, progress, and notes will be preserved inside a .kreader/ folder right beside your books.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onSelectFolder,
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Choose Books Folder",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
