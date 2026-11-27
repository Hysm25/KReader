package io.github.kreader.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kreader.core.model.Book
import io.github.kreader.core.model.BookFormat
import io.github.kreader.core.model.CbzSeries
import io.github.kreader.feature.library.filepicker.rememberFilePickerLauncher
import io.github.kreader.feature.library.filepicker.rememberFolderPickerLauncher
import io.github.kreader.feature.library.ui.rememberCoverPainter
import kotlinx.coroutines.launch

/**
 * Native Desktop Library Layout modeled after Foliate and Apple Books on desktop.
 * Provides a responsive header with constrained search, direct "Import EPUB" action,
 * vault indicator, spacious book grid with hover feedback, and desktop context menus.
 */
@Composable
fun DesktopLibraryLayout(
    state: LibraryUiState,
    onBookClick: (Book) -> Unit,
    onSelectVaultFolder: (String) -> Unit = {},
    onRefreshVault: () -> Unit = {},
    onChangeVaultFolder: () -> Unit = {},
    onOpenGlobalSettings: () -> Unit = {},
    onDeleteBook: (Book) -> Unit,
    onDeleteBooks: (List<Book>) -> Unit = { books -> books.forEach(onDeleteBook) },
    onDeleteSeries: (CbzSeries) -> Unit = {},
    onResetBookProgress: (Book) -> Unit = {},
    onResetSeriesProgress: (CbzSeries) -> Unit = {},
    onSeriesClick: (CbzSeries) -> Unit = {},
    onDismissSeries: () -> Unit = {},
    onSearchQueryChanged: (String) -> Unit,
    onFilterChanged: (BookFilter) -> Unit = {},
    onSortOrderChanged: (BookSortOrder) -> Unit = {},
    onExportBookToMarkdown: (suspend (Book) -> String?)? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val folderPickerLauncher = rememberFolderPickerLauncher { uriOrPath ->
        onSelectVaultFolder(uriOrPath)
    }

    val filePickerLauncher = rememberFilePickerLauncher { bookSource ->
        io.github.kreader.core.model.ExternalBookOpener.open(bookSource)
    }

    var selectedBookForDelete by remember { mutableStateOf<Book?>(null) }
    var selectedBookForReset by remember { mutableStateOf<Book?>(null) }
    var selectedSeriesForDelete by remember { mutableStateOf<CbzSeries?>(null) }
    var selectedSeriesForReset by remember { mutableStateOf<CbzSeries?>(null) }

    // If no vault is set, show desktop onboarding hero
    if (state.vaultPath == null) {
        DesktopVaultOnboarding(
            onSelectFolder = folderPickerLauncher,
            onOpenFile = filePickerLauncher,
            modifier = modifier
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            DesktopLibraryHeader(
                vaultName = state.vaultName,
                bookCount = state.displayItems.size,
                totalCount = state.books.size,
                searchQuery = state.searchQuery,
                onSearchQueryChanged = onSearchQueryChanged,
                selectedFilter = state.selectedFilter,
                onFilterChanged = onFilterChanged,
                selectedSortOrder = state.selectedSortOrder,
                onSortOrderChanged = onSortOrderChanged,
                onImportBook = filePickerLauncher,
                onChangeVault = folderPickerLauncher,
                onRefreshVault = onRefreshVault,
                onOpenSettings = onOpenGlobalSettings
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.displayItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = when {
                                state.searchQuery.isNotBlank() -> "No books match \"${state.searchQuery}\""
                                state.selectedFilter == BookFilter.COMICS -> "No CBZ comics found in vault"
                                state.selectedFilter == BookFilter.BOOKS -> "No EPUB books found in vault"
                                else -> "No EPUB or CBZ files found in vault"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (state.searchQuery.isNotBlank()) {
                            OutlinedButton(onClick = { onSearchQueryChanged("") }) {
                                Text("Clear Search")
                            }
                        } else if (state.selectedFilter != BookFilter.ALL) {
                            OutlinedButton(onClick = { onFilterChanged(BookFilter.ALL) }) {
                                Text("Show All")
                            }
                        } else {
                            FilledTonalButton(onClick = filePickerLauncher) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Import EPUB / CBZ File")
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 170.dp),
                    contentPadding = PaddingValues(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = state.displayItems,
                        key = { it.id }
                    ) { item ->
                        when (item) {
                            is LibraryItem.SingleBook -> {
                                val book = item.book
                                DesktopBookCard(
                                    book = book,
                                    onClick = { onBookClick(book) },
                                    onDelete = { selectedBookForDelete = book },
                                    onResetProgress = { selectedBookForReset = book },
                                    onExportMarkdown = {
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
                                    }
                                )
                            }
                            is LibraryItem.Series -> {
                                val series = item.series
                                DesktopSeriesCard(
                                    series = series,
                                    onClick = { onSeriesClick(series) },
                                    onDelete = { selectedSeriesForDelete = series },
                                    onResetProgress = { selectedSeriesForReset = series }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    selectedBookForDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { selectedBookForDelete = null },
            title = { Text("Delete Book") },
            text = { Text("Are you sure you want to remove \"${book.title}\" from your library and delete its annotations?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteBook(book)
                        selectedBookForDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedBookForDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Reset Progress Confirmation Dialog
    selectedBookForReset?.let { book ->
        AlertDialog(
            onDismissRequest = { selectedBookForReset = null },
            title = { Text("Reset Reading Progress") },
            text = { Text("Reset reading progress for \"${book.title}\" back to the beginning?") },
            confirmButton = {
                Button(
                    onClick = {
                        onResetBookProgress(book)
                        selectedBookForReset = null
                    }
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedBookForReset = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    selectedSeriesForDelete?.let { series ->
        AlertDialog(
            onDismissRequest = { selectedSeriesForDelete = null },
            title = { Text("Delete Series") },
            text = { Text("Delete '${series.title}' and all ${series.chapters.size} CBZ chapter file(s) from your vault?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSeries(series)
                        selectedSeriesForDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Series")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedSeriesForDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    selectedSeriesForReset?.let { series ->
        AlertDialog(
            onDismissRequest = { selectedSeriesForReset = null },
            title = { Text("Reset Series Progress") },
            text = { Text("Reset reading progress for all ${series.chapters.size} chapters of '${series.title}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        onResetSeriesProgress(series)
                        selectedSeriesForReset = null
                    }
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedSeriesForReset = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    SeriesChaptersSheet(
        series = state.selectedSeries,
        onChapterClick = { chapter ->
            onDismissSeries()
            onBookClick(chapter)
        },
        onDismiss = onDismissSeries
    )
}

/**
 * Desktop Top Header with branding, constrained search, and quick desktop actions.
 */
@Composable
private fun DesktopLibraryHeader(
    vaultName: String?,
    bookCount: Int,
    totalCount: Int,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    selectedFilter: BookFilter,
    onFilterChanged: (BookFilter) -> Unit,
    selectedSortOrder: BookSortOrder,
    onSortOrderChanged: (BookSortOrder) -> Unit,
    onImportBook: () -> Unit,
    onChangeVault: () -> Unit,
    onRefreshVault: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: App Brand & Vault Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                            contentDescription = "KReader",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "KReader",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${vaultName ?: "Vault"} • $bookCount ${if (bookCount == 1) "book" else "books"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Center: Constrained Search Field (max 460.dp)
            Surface(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .widthIn(min = 280.dp, max = 460.dp)
                    .height(42.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChanged,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search books or authors...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChanged("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Right: Format Filter, Desktop Actions & Sort Menu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 16.dp)
            ) {
                listOf(
                    BookFilter.ALL to "All",
                    BookFilter.BOOKS to "Books",
                    BookFilter.COMICS to "Comics"
                ).forEach { (filterValue, label) ->
                    FilterChip(
                        selected = selectedFilter == filterValue,
                        onClick = { onFilterChanged(filterValue) },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }

                // Sorting dropdown
                var sortMenuExpanded by remember { mutableStateOf(false) }
                val sortLabel = when (selectedSortOrder) {
                    BookSortOrder.RECENTLY_READ -> "Recent"
                    BookSortOrder.RECENTLY_ADDED -> "Added"
                    BookSortOrder.TITLE -> "Title"
                }
                Box {
                    FilterChip(
                        selected = true,
                        onClick = { sortMenuExpanded = true },
                        label = { Text(sortLabel, fontSize = 12.sp) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
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

                // Import File Button
                FilledTonalButton(
                    onClick = onImportBook,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import", style = MaterialTheme.typography.labelLarge)
                }

                // Change Vault Folder
                IconButton(
                    onClick = onChangeVault,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = "Switch Vault Folder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Refresh Vault
                IconButton(
                    onClick = onRefreshVault,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh Vault",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Settings
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Global Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Desktop Book Card with 1:1.5 standard proportion, hover elevation, and context menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DesktopBookCard(
    book: Book,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onResetProgress: () -> Unit,
    onExportMarkdown: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .hoverable(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = { menuExpanded = true }
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isHovered) 6.dp else 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Book Cover (1:1.45 aspect ratio)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f / 1.45f)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                val coverPainter = rememberCoverPainter(book.coverPath)
                if (coverPainter != null) {
                    Image(
                        painter = coverPainter,
                        contentDescription = book.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Fallback cover artwork
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = book.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Reading Progress & Format Badges + 3-dots Context Menu Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (book.format == BookFormat.CBZ) {
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f)
                            } else {
                                Color.Black.copy(alpha = 0.65f)
                            }
                        ) {
                            Text(
                                text = book.format.name,
                                color = if (book.format == BookFormat.CBZ) {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                } else {
                                    Color.White
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        val pct = (book.totalProgression * 100).toInt()
                        if (pct > 0) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.Black.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = if (pct >= 100) "Completed" else "$pct%",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Card context menu toggle
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "Options",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Read") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export Notes to Markdown") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Notes, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onExportMarkdown()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Reset Progress") },
                                    leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onResetProgress()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        menuExpanded = false
                                        onDelete()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Card Metadata
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val author = book.author
                if (!author.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Progress Bar
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { book.totalProgression.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DesktopSeriesCard(
    series: CbzSeries,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onResetProgress: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .hoverable(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = { menuExpanded = true }
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isHovered) 6.dp else 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f / 1.45f)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                val coverPainter = rememberCoverPainter(series.coverPath)
                if (coverPainter != null) {
                    Image(
                        painter = coverPainter,
                        contentDescription = series.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = series.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f)
                        ) {
                            Text(
                                text = "CBZ Series",
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = "${series.chapters.size} Ch",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "Series options",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("View Chapters") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Reset Series Progress") },
                                    leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onResetProgress()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Series", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        menuExpanded = false
                                        onDelete()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = series.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${series.chapters.size} ${if (series.chapters.size == 1) "chapter" else "chapters"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { series.totalProgression.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }
        }
    }
}

/**
 * Desktop Onboarding Canvas when no vault folder is chosen yet.
 */
@Composable
private fun DesktopVaultOnboarding(
    onSelectFolder: () -> Unit,
    onOpenFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .padding(32.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(36.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Welcome to KReader",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "A local-first, distraction-free EPUB reader. Sync your reading position, highlights, and annotations directly with an Obsidian vault.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))
                Button(
                    onClick = onSelectFolder,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Choose Books / Obsidian Vault Folder")
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onOpenFile,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Open EPUB / CBZ File Directly")
                }
            }
        }
    }
}
