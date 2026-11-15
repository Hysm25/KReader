package io.github.kreader.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kreader.core.engine.TocItem
import io.github.kreader.core.model.Bookmark
import io.github.kreader.core.model.Highlight
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ReaderTypography
import io.github.kreader.core.model.ThemeMode

object ReaderThemePresets {
    val Light = ReaderTheme(
        mode = ThemeMode.LIGHT,
        backgroundColorHex = "#FFFFFF",
        textColorHex = "#121212"
    )
    val Sepia = ReaderTheme(
        mode = ThemeMode.SEPIA,
        backgroundColorHex = "#FBF0D9",
        textColorHex = "#5F4B32"
    )
    val Dark = ReaderTheme(
        mode = ThemeMode.DARK,
        backgroundColorHex = "#1E1E1E",
        textColorHex = "#E8E8E8"
    )
    val Amoled = ReaderTheme(
        mode = ThemeMode.DARK,
        backgroundColorHex = "#000000",
        textColorHex = "#FFFFFF"
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
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(0.88f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                        Text("✕", style = MaterialTheme.typography.titleMedium)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tabs: Contents, Bookmarks, Highlights & Notes
                TabRow(
                    selectedTabIndex = currentTab.ordinal,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = currentTab == NavigationDrawerTab.CONTENTS,
                        onClick = { onTabSelected(NavigationDrawerTab.CONTENTS) },
                        text = { Text("Contents (${tocItems.size})", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                    Tab(
                        selected = currentTab == NavigationDrawerTab.BOOKMARKS,
                        onClick = { onTabSelected(NavigationDrawerTab.BOOKMARKS) },
                        text = { Text("Bookmarks (${bookmarks.size})", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                    Tab(
                        selected = currentTab == NavigationDrawerTab.HIGHLIGHTS,
                        onClick = { onTabSelected(NavigationDrawerTab.HIGHLIGHTS) },
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
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
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
                        text = flatItem.item.title.ifBlank { "Untitled Chapter" },
                        style = if (flatItem.depth == 0) {
                            MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                            )
                        } else {
                            MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
                text = "No bookmarks yet.\nTap the 🏷️ icon in the top bar to bookmark this page.",
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
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
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
                        Text("🗑️", style = MaterialTheme.typography.bodyMedium)
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
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
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
                            Text("✏️", style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = { onDeleteHighlight(highlight.id) }) {
                            Text("🗑️", style = MaterialTheme.typography.bodyMedium)
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
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header with search bar and close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Search book content...") },
                        singleLine = true,
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Text("✕", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onSearch,
                        enabled = query.isNotBlank() && !isSearching
                    ) {
                        Text("Search")
                    }
                    IconButton(onClick = onDismiss) {
                        Text("✕", style = MaterialTheme.typography.titleMedium)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

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
                } else {
                    Text(
                        text = "${results.size} matches found",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(results) { result ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    .clickable {
                                        onResultClick(result)
                                        onDismiss()
                                    }
                                    .padding(12.dp)
                            ) {
                                val before = result.text?.before ?: ""
                                val highlight = result.text?.highlight ?: ""
                                val after = result.text?.after ?: ""
                                Text(
                                    text = "$before[$highlight]$after",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val prog = ((result.locations.totalProgression ?: result.locations.progression ?: 0f) * 100).toInt()
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Location: $prog%",
                                    style = MaterialTheme.typography.bodySmall,
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
    onThemeChange: (ReaderTheme) -> Unit,
    onTypographyChange: (ReaderTypography) -> Unit,
    onScrollModeChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
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
                        Text("✕", style = MaterialTheme.typography.titleMedium)
                    }
                }

                HorizontalDivider()

                // Theme Presets: Light, Sepia, Dark, AMOLED
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
                            label = "Light",
                            bgColor = Color(0xFFFFFFFF),
                            textColor = Color(0xFF111111),
                            isSelected = theme.mode == ThemeMode.LIGHT,
                            onClick = { onThemeChange(ReaderThemePresets.Light) }
                        )
                        ThemeOption(
                            label = "Sepia",
                            bgColor = Color(0xFFFBF0D9),
                            textColor = Color(0xFF5F4B32),
                            isSelected = theme.mode == ThemeMode.SEPIA,
                            onClick = { onThemeChange(ReaderThemePresets.Sepia) }
                        )
                        ThemeOption(
                            label = "Dark",
                            bgColor = Color(0xFF1E1E1E),
                            textColor = Color(0xFFE8E8E8),
                            isSelected = theme.mode == ThemeMode.DARK && theme.backgroundColorHex != "#000000",
                            onClick = { onThemeChange(ReaderThemePresets.Dark) }
                        )
                        ThemeOption(
                            label = "AMOLED",
                            bgColor = Color(0xFF000000),
                            textColor = Color(0xFFFFFFFF),
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

                // Progression Direction: LTR vs RTL
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Progression",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !typography.isRtl,
                            onClick = { onTypographyChange(typography.copy(isRtl = false)) },
                            label = { Text("LTR") }
                        )
                        FilterChip(
                            selected = typography.isRtl,
                            onClick = { onTypographyChange(typography.copy(isRtl = true)) },
                            label = { Text("RTL (العربية)") }
                        )
                    }
                }

                // Font Family: Default, Amiri (Arabic Serif), Cairo (Arabic Sans)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Font Family",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = typography.fontFamily.equals("Default", ignoreCase = true),
                            onClick = { onTypographyChange(typography.copy(fontFamily = "Default")) },
                            label = { Text("Default") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = typography.fontFamily.equals("Amiri", ignoreCase = true),
                            onClick = { onTypographyChange(typography.copy(fontFamily = "Amiri")) },
                            label = { Text("Amiri (أميري)") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = typography.fontFamily.equals("Cairo", ignoreCase = true),
                            onClick = { onTypographyChange(typography.copy(fontFamily = "Cairo")) },
                            label = { Text("Cairo (كايرو)") },
                            modifier = Modifier.weight(1f)
                        )
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
                            Text("A-", style = MaterialTheme.typography.titleMedium)
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
                            Text("A+", style = MaterialTheme.typography.titleMedium)
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
