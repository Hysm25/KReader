package io.github.kreader.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kreader.core.engine.TocItem
import io.github.kreader.core.model.Bookmark
import io.github.kreader.core.model.CustomFont
import io.github.kreader.core.model.Highlight
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.PageLayout
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ReaderTypography
import io.github.kreader.core.model.ThemeMode

object ReaderThemePresets {
    val System = ReaderTheme(
        mode = ThemeMode.SYSTEM
    )
    val Light = ReaderTheme(
        mode = ThemeMode.LIGHT,
        backgroundColorHex = "#FFFFFF",
        textColorHex = "#1A1A1A"
    )
    val Sepia = ReaderTheme(
        mode = ThemeMode.SEPIA,
        backgroundColorHex = "#FBF0D9",
        textColorHex = "#3B2F2F"
    )
    val Dark = ReaderTheme(
        mode = ThemeMode.DARK,
        backgroundColorHex = "#121212",
        textColorHex = "#E6E6E6"
    )
    val Amoled = ReaderTheme(
        mode = ThemeMode.DARK,
        backgroundColorHex = "#000000",
        textColorHex = "#DCDCDC"
    )
}

object HighlightColors {
    val Yellow = "#FEF08A"
    val Green = "#BBF7D0"
    val Blue = "#BAE6FD"
    val Pink = "#FBCFE8"

    val list = listOf(Yellow, Green, Blue, Pink)
}

@Composable
fun ReaderNavigationDrawer(
    isOpen: Boolean,
    currentTab: NavigationDrawerTab,
    onTabSelected: (NavigationDrawerTab) -> Unit,
    tocItems: List<TocItem>,
    bookmarks: List<Bookmark>,
    highlights: List<Highlight>,
    currentLocator: Locator?,
    onNavigateToLocator: (Locator) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    onDeleteHighlight: (String) -> Unit,
    onHighlightClicked: (Highlight) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onDismiss
            )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.85f)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Navigation & Study",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close navigation"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tabs: Contents, Bookmarks, Highlights & Notes
                TabRow(
                    selectedTabIndex = currentTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            thickness = 0.5.dp
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = currentTab == NavigationDrawerTab.CONTENTS,
                        onClick = { onTabSelected(NavigationDrawerTab.CONTENTS) },
                        icon = { Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null) },
                        text = { Text("Contents (${tocItems.size})", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                    Tab(
                        selected = currentTab == NavigationDrawerTab.BOOKMARKS,
                        onClick = { onTabSelected(NavigationDrawerTab.BOOKMARKS) },
                        icon = { Icon(Icons.Rounded.Bookmark, contentDescription = null) },
                        text = { Text("Bookmarks (${bookmarks.size})", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                    Tab(
                        selected = currentTab == NavigationDrawerTab.HIGHLIGHTS,
                        onClick = { onTabSelected(NavigationDrawerTab.HIGHLIGHTS) },
                        icon = { Icon(Icons.AutoMirrored.Rounded.Notes, contentDescription = null) },
                        text = { Text("Notes (${highlights.size})", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Content
                when (currentTab) {
                    NavigationDrawerTab.CONTENTS -> {
                        ContentsTabContent(
                            tocItems = tocItems,
                            currentLocator = currentLocator,
                            onItemClick = { locator ->
                                onNavigateToLocator(locator)
                                onDismiss()
                            }
                        )
                    }
                    NavigationDrawerTab.BOOKMARKS -> {
                        BookmarksTabContent(
                            bookmarks = bookmarks,
                            onBookmarkClick = { bookmark ->
                                onNavigateToLocator(bookmark.locator)
                                onDismiss()
                            },
                            onDeleteBookmark = onDeleteBookmark
                        )
                    }
                    NavigationDrawerTab.HIGHLIGHTS -> {
                        HighlightsTabContent(
                            highlights = highlights,
                            onHighlightClick = { highlight ->
                                onNavigateToLocator(highlight.locator)
                                onDismiss()
                            },
                            onHighlightDetail = onHighlightClicked,
                            onDeleteHighlight = onDeleteHighlight
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentsTabContent(
    tocItems: List<TocItem>,
    currentLocator: Locator?,
    onItemClick: (Locator) -> Unit
) {
    if (tocItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No Table of Contents available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(flattenToc(tocItems)) { flatItem ->
                val isActive = currentLocator?.href?.substringBefore("#") == flatItem.item.locator.href.substringBefore("#")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent)
                        .clickable { onItemClick(flatItem.item.locator) }
                        .padding(
                            start = (12 + flatItem.depth * 16).dp,
                            end = 12.dp,
                            top = 10.dp,
                            bottom = 10.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = flatItem.item.title,
                        style = if (isActive) {
                            MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarksTabContent(
    bookmarks: List<Bookmark>,
    onBookmarkClick: (Bookmark) -> Unit,
    onDeleteBookmark: (String) -> Unit
) {
    if (bookmarks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No bookmarks yet.\nTap the bookmark icon in the top bar to save a page.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(bookmarks) { bookmark ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { onBookmarkClick(bookmark) }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = bookmark.title ?: bookmark.locator.title ?: "Bookmark",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val prog = ((bookmark.locator.locations.totalProgression ?: bookmark.locator.locations.progression ?: 0f) * 100).toInt()
                        Text(
                            text = "Progress: $prog%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onDeleteBookmark(bookmark.id) }) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete bookmark",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HighlightsTabContent(
    highlights: List<Highlight>,
    onHighlightClick: (Highlight) -> Unit,
    onHighlightDetail: (Highlight) -> Unit,
    onDeleteHighlight: (String) -> Unit
) {
    if (highlights.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No highlights or notes yet.\nSelect text inside the book to highlight or add notes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(highlights) { highlight ->
                val highlightColor = parseHexToColor(highlight.colorHex)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { onHighlightClick(highlight) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(highlightColor)
                            .padding(top = 4.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = highlight.locator.text?.highlight ?: "Highlighted text",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!highlight.annotation.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "📝 ${highlight.annotation}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row {
                        IconButton(onClick = { onHighlightDetail(highlight) }) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "Edit annotation"
                            )
                        }
                        IconButton(onClick = { onDeleteHighlight(highlight.id) }) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "Delete highlight",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class FlatTocItem(
    val item: TocItem,
    val depth: Int
)

private fun flattenToc(items: List<TocItem>, depth: Int = 0): List<FlatTocItem> {
    val result = mutableListOf<FlatTocItem>()
    for (item in items) {
        result.add(FlatTocItem(item, depth))
        if (item.children.isNotEmpty()) {
            result.addAll(flattenToc(item.children, depth + 1))
        }
    }
    return result
}

@Composable
fun ReaderSearchSheet(
    isOpen: Boolean,
    query: String,
    results: List<Locator>,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onResultClick: (Locator) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onDismiss
            )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxHeight(0.8f)
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                        .align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // M3 Pill Search Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
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
                                value = query,
                                onValueChange = onQueryChange,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    if (query.isNotBlank() && !isSearching) onSearch()
                                }),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.weight(1f),
                                decorationBox = { innerTextField ->
                                    if (query.isEmpty()) {
                                        Text(
                                            text = "Search in book...",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            if (query.isNotEmpty()) {
                                IconButton(
                                    onClick = { onQueryChange("") },
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

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close search",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isSearching) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Searching publication...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else if (results.isEmpty() && query.isNotBlank()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No matches found for \"$query\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (results.isEmpty() && query.isBlank()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Search for words or phrases in this book",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    if (results.isNotEmpty()) {
                        Text(
                            text = "${results.size} matches found",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(results) { result ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onResultClick(result) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    val before = result.text?.before.orEmpty()
                                    val highlight = result.text?.highlight.orEmpty()
                                    val after = result.text?.after.orEmpty()

                                    Text(
                                        text = if (highlight.isNotEmpty()) "...$before[$highlight]$after..." else (result.title ?: result.href),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val prog = ((result.locations.totalProgression ?: result.locations.progression ?: 0f) * 100).toInt()
                                    Text(
                                        text = "Progression: $prog%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HighlightDetailDialog(
    highlight: Highlight?,
    onSaveAnnotation: (id: String, note: String, colorHex: String) -> Unit,
    onDeleteHighlight: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (highlight == null) return

    var noteText by remember(highlight.id) { mutableStateOf(highlight.annotation ?: "") }
    var selectedColor by remember(highlight.id) { mutableStateOf(highlight.colorHex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Highlight Details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Highlighted quote
                Text(
                    text = "\"${highlight.locator.text?.highlight ?: ""}\"",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )

                // Color picker swatches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    HighlightColors.list.forEach { hex ->
                        val color = parseHexToColor(hex)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selectedColor == hex) 3.dp else 1.dp,
                                    color = if (selectedColor == hex) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f),
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }

                // Note input
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Note / Annotation") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSaveAnnotation(highlight.id, noteText, selectedColor)
                onDismiss()
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        onDeleteHighlight(highlight.id)
                        onDismiss()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
fun AddNoteDialog(
    locator: Locator?,
    selectedText: String?,
    onSaveNote: (locator: Locator, note: String, colorHex: String) -> Unit,
    onDismiss: () -> Unit
) {
    if (locator == null) return

    var noteText by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(HighlightColors.Yellow) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!selectedText.isNullOrBlank()) {
                    Text(
                        text = "\"$selectedText\"",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Color picker swatches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    HighlightColors.list.forEach { hex ->
                        val color = parseHexToColor(hex)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selectedColor == hex) 3.dp else 1.dp,
                                    color = if (selectedColor == hex) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f),
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Write note...") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveNote(locator, noteText, selectedColor)
                    onDismiss()
                },
                enabled = noteText.isNotBlank()
            ) {
                Text("Save Note")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    isOpen: Boolean,
    theme: ReaderTheme,
    typography: ReaderTypography,
    isScrollMode: Boolean,
    pageLayout: PageLayout = PageLayout.SINGLE,
    isApplyToThisBookOnly: Boolean = false,
    customFonts: List<CustomFont> = emptyList(),
    onThemeChange: (ReaderTheme) -> Unit,
    onTypographyChange: (ReaderTypography) -> Unit,
    onScrollModeChange: (Boolean) -> Unit,
    onPageLayoutChange: (PageLayout) -> Unit = {},
    onScopeChange: (Boolean) -> Unit = {},
    onResetToDefaults: () -> Unit = {},
    onImportCustomFont: (CustomFont) -> Unit = {},
    onDeleteCustomFont: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    val fontPickerLauncher = rememberFontPickerLauncher { importedFont ->
        onImportCustomFont(importedFont)
    }

    val interactionSource = remember { MutableInteractionSource() }

    var tempHorizontalPadding by remember(typography.paddingHorizontal) { mutableStateOf(typography.paddingHorizontal.toFloat()) }
    var tempVerticalPadding by remember(typography.paddingVertical) { mutableStateOf(typography.paddingVertical.toFloat()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onDismiss
            )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                        .align(Alignment.CenterHorizontally)
                )

                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reading Settings",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close settings"
                        )
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 0.5.dp
                )

                // Scope Selector: Apply to This Book vs All Books
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Apply Settings To",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = isApplyToThisBookOnly,
                            onClick = { onScopeChange(true) },
                            label = { Text("This Book") }
                        )
                        FilterChip(
                            selected = !isApplyToThisBookOnly,
                            onClick = { onScopeChange(false) },
                            label = { Text("All Books") }
                        )
                    }
                }

                // Theme Presets: System, Light, Sepia, Dark, AMOLED
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ThemeOption(
                            label = "System",
                            bgColor = MaterialTheme.colorScheme.surfaceVariant,
                            textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            isSelected = theme.mode == ThemeMode.SYSTEM,
                            onClick = { onThemeChange(ReaderThemePresets.System) }
                        )
                        ThemeOption(
                            label = "Light",
                            bgColor = Color(0xFFFFFFFF),
                            textColor = Color(0xFF1A1A1A),
                            isSelected = theme.mode == ThemeMode.LIGHT,
                            onClick = { onThemeChange(ReaderThemePresets.Light) }
                        )
                        ThemeOption(
                            label = "Sepia",
                            bgColor = Color(0xFFFBF0D9),
                            textColor = Color(0xFF3B2F2F),
                            isSelected = theme.mode == ThemeMode.SEPIA,
                            onClick = { onThemeChange(ReaderThemePresets.Sepia) }
                        )
                        ThemeOption(
                            label = "Dark",
                            bgColor = Color(0xFF121212),
                            textColor = Color(0xFFE6E6E6),
                            isSelected = theme.mode == ThemeMode.DARK && !theme.backgroundColorHex.equals("#000000", ignoreCase = true),
                            onClick = { onThemeChange(ReaderThemePresets.Dark) }
                        )
                        ThemeOption(
                            label = "AMOLED",
                            bgColor = Color(0xFF000000),
                            textColor = Color(0xFFDCDCDC),
                            isSelected = theme.mode == ThemeMode.DARK && theme.backgroundColorHex.equals("#000000", ignoreCase = true),
                            onClick = { onThemeChange(ReaderThemePresets.Amoled) }
                        )
                    }
                }

                // Reading Mode: Paginated vs Scroll
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mode",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !isScrollMode,
                            onClick = { onScrollModeChange(false) },
                            label = { Text("Paginated") }
                        )
                        FilterChip(
                            selected = isScrollMode,
                            onClick = { onScrollModeChange(true) },
                            label = { Text("Continuous Scroll") }
                        )
                    }
                }

                // Page Layout: 1 Page vs 2 Pages
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Page Columns",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = pageLayout == PageLayout.SINGLE,
                            onClick = { onPageLayoutChange(PageLayout.SINGLE) },
                            label = { Text("1 Page") }
                        )
                        FilterChip(
                            selected = pageLayout == PageLayout.DOUBLE,
                            onClick = { onPageLayoutChange(PageLayout.DOUBLE) },
                            label = { Text("2 Pages") }
                        )
                    }
                }

                // Font Family: Default + User-imported Fonts (.ttf / .otf) + Import Button
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Font Family",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { fontPickerLauncher() }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import Font", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = typography.fontFamily.isBlank() || typography.fontFamily.equals("Default", ignoreCase = true),
                            onClick = { onTypographyChange(typography.copy(fontFamily = "Default")) },
                            label = { Text("Default") }
                        )
                        for (font in customFonts) {
                            FilterChip(
                                selected = typography.fontFamily.equals(font.name, ignoreCase = true),
                                onClick = { onTypographyChange(typography.copy(fontFamily = font.name)) },
                                label = { Text(font.name) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Delete ${font.name}",
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { onDeleteCustomFont(font.name) }
                                    )
                                }
                            )
                        }
                    }
                }

                // Font Size adjustment
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Font Size",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val newSize = (typography.fontSizeSp - 2f).coerceAtLeast(12f)
                                onTypographyChange(typography.copy(fontSizeSp = newSize))
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Remove,
                                contentDescription = "Decrease font size"
                            )
                        }
                        Text(
                            text = "${typography.fontSizeSp.toInt()} sp",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        IconButton(
                            onClick = {
                                val newSize = (typography.fontSizeSp + 2f).coerceAtMost(36f)
                                onTypographyChange(typography.copy(fontSizeSp = newSize))
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Increase font size"
                            )
                        }
                    }
                }

                // Line Height Spacing (Tashkeel / Diacritics friendly >= 1.5)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Line Height (تباعد الأسطر والتشكيل)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = typography.lineHeightMultiplier <= 1.55f,
                            onClick = { onTypographyChange(typography.copy(lineHeightMultiplier = 1.5f)) },
                            label = { Text("1.5x (عادي)") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = typography.lineHeightMultiplier > 1.55f && typography.lineHeightMultiplier <= 1.85f,
                            onClick = { onTypographyChange(typography.copy(lineHeightMultiplier = 1.8f)) },
                            label = { Text("1.8x (مريح)") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = typography.lineHeightMultiplier > 1.85f,
                            onClick = { onTypographyChange(typography.copy(lineHeightMultiplier = 2.0f)) },
                            label = { Text("2.0x (واسع)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Horizontal Page Margin / Padding Slider
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Horizontal Margin (الهوامش الجانبية)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = "${tempHorizontalPadding.toInt()} dp",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Slider(
                        value = tempHorizontalPadding,
                        onValueChange = { tempHorizontalPadding = it },
                        onValueChangeFinished = {
                            onTypographyChange(typography.copy(paddingHorizontal = tempHorizontalPadding.toInt()))
                        },
                        valueRange = 0f..48f,
                        steps = 11,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Vertical Page Margin / Padding Slider
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Vertical Margin (الهوامش العلوية والسفلية)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = "${tempVerticalPadding.toInt()} dp",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Slider(
                        value = tempVerticalPadding,
                        onValueChange = { tempVerticalPadding = it },
                        onValueChangeFinished = {
                            onTypographyChange(typography.copy(paddingVertical = tempVerticalPadding.toInt()))
                        },
                        valueRange = 0f..32f,
                        steps = 7,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 0.5.dp
                )

                OutlinedButton(
                    onClick = onResetToDefaults,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset to Defaults")
                }
            }
        }
    }
}

@Composable
private fun ThemeOption(
    label: String,
    bgColor: Color,
    textColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(bgColor)
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Aa",
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

private fun parseHexToColor(hex: String): Color {
    val clean = hex.removePrefix("#")
    val colorLong = when (clean.length) {
        6 -> "FF$clean".toLongOrNull(16) ?: 0xFFFEF08A
        8 -> clean.toLongOrNull(16) ?: 0xFFFEF08A
        else -> 0xFFFEF08A
    }
    return Color(colorLong)
}
