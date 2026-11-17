package io.github.kreader.feature.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kreader.core.engine.TocItem
import io.github.kreader.core.model.Bookmark
import io.github.kreader.core.model.CustomFont
import io.github.kreader.core.model.CustomTheme
import io.github.kreader.core.model.Highlight
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.OrientationLock
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ReaderTypography
import io.github.kreader.core.model.ThemeMode
import io.github.kreader.core.model.toReaderTheme

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
    onExportMarkdown: () -> Unit = {},
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onExportMarkdown) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Notes,
                                contentDescription = "Export to Markdown",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close navigation"
                            )
                        }
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
                lineHeight = 22.sp,
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(highlights) { highlight ->
                val highlightColor = parseHexToColor(highlight.colorHex)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onHighlightClick(highlight) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                    ) {
                        // Colored start accent bar
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .fillMaxHeight()
                                .background(highlightColor)
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Header: Chapter pill & progression
                            val chapterTitle = highlight.locator.title
                                ?.takeIf { it.isNotBlank() }
                                ?: highlight.locator.href.substringBefore('#').substringAfterLast('/')
                                    .removeSuffix(".xhtml")
                                    .removeSuffix(".html")
                                    .replace('_', ' ')
                            val prog = ((highlight.locator.locations.totalProgression ?: highlight.locator.locations.progression ?: 0f) * 100).toInt()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = highlightColor.copy(alpha = 0.25f)
                                ) {
                                    Text(
                                        text = chapterTitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = "$prog%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Highlighted Quote Text
                            Text(
                                text = "“${highlight.locator.text?.highlight ?: "Highlighted text"}”",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Normal),
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Annotation / Note if present
                            if (!highlight.annotation.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "📝 ${highlight.annotation}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        // Action buttons
                        Column(
                            modifier = Modifier
                                .padding(end = 4.dp, top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = { onHighlightDetail(highlight) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Edit annotation",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { onDeleteHighlight(highlight.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = "Delete highlight",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
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
                .fillMaxHeight(0.82f)
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
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .imePadding()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
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

                // M3 Pill Search Bar with embedded trailing clear button
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
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

                Spacer(modifier = Modifier.height(14.dp))

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
                    // Match counter pill
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${results.size} matches",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(results) { result ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onResultClick(result) },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                tonalElevation = 2.dp
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val chapterTitle = result.title
                                            ?.takeIf { it.isNotBlank() }
                                            ?: result.href.substringBefore('#').substringAfterLast('/')
                                                .removeSuffix(".xhtml")
                                                .removeSuffix(".html")
                                                .replace('_', ' ')

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            modifier = Modifier.weight(1f, fill = false)
                                        ) {
                                            Text(
                                                text = chapterTitle,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        val prog = ((result.locations.totalProgression ?: result.locations.progression ?: 0f) * 100).toInt()
                                        Text(
                                            text = "$prog%",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    val before = result.text?.before.orEmpty()
                                    val highlight = result.text?.highlight.orEmpty().ifEmpty { query }
                                    val after = result.text?.after.orEmpty()

                                    val snippetText = buildAnnotatedString {
                                        if (before.isNotEmpty()) {
                                            append("…")
                                            append(before)
                                        }
                                        withStyle(
                                            SpanStyle(
                                                fontWeight = FontWeight.Bold,
                                                background = MaterialTheme.colorScheme.primaryContainer,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        ) {
                                            append(highlight)
                                        }
                                        if (after.isNotEmpty()) {
                                            append(after)
                                            append("…")
                                        }
                                    }

                                    Text(
                                        text = snippetText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis
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
    isApplyToThisBookOnly: Boolean = false,
    customFonts: List<CustomFont> = emptyList(),
    customThemes: List<CustomTheme> = emptyList(),
    keepScreenOn: Boolean = false,
    orientationLock: OrientationLock = OrientationLock.AUTO,
    onThemeChange: (ReaderTheme) -> Unit,
    onTypographyChange: (ReaderTypography) -> Unit,
    onScrollModeChange: (Boolean) -> Unit,
    onScopeChange: (Boolean) -> Unit = {},
    onResetToDefaults: () -> Unit = {},
    onImportCustomFont: (CustomFont) -> Unit = {},
    onDeleteCustomFont: (String) -> Unit = {},
    onSaveCustomTheme: (CustomTheme) -> Unit = {},
    onDeleteCustomTheme: (String) -> Unit = {},
    onKeepScreenOnChange: (Boolean) -> Unit = {},
    onOrientationLockChange: (OrientationLock) -> Unit = {},
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

                // Theme Presets: System, Light, Sepia, Dark, AMOLED, Saved Custom Themes, Custom
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
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
                        for (ct in customThemes) {
                            val ctBg = parseHexToColor(ct.backgroundColorHex)
                            val ctText = parseHexToColor(ct.textColorHex)
                            val isSelected = theme.mode == ThemeMode.CUSTOM &&
                                theme.backgroundColorHex.equals(ct.backgroundColorHex, ignoreCase = true) &&
                                theme.textColorHex.equals(ct.textColorHex, ignoreCase = true)
                            CustomSavedThemeOption(
                                name = ct.name,
                                bgColor = ctBg,
                                textColor = ctText,
                                isSelected = isSelected,
                                onClick = { onThemeChange(ct.toReaderTheme()) },
                                onDelete = { onDeleteCustomTheme(ct.id) }
                            )
                        }
                        ThemeOption(
                            label = "Custom",
                            bgColor = if (theme.mode == ThemeMode.CUSTOM) parseHexToColor(theme.backgroundColorHex) else Color(0xFF2E3440),
                            textColor = if (theme.mode == ThemeMode.CUSTOM) parseHexToColor(theme.textColorHex) else Color(0xFFECEFF4),
                            isSelected = theme.mode == ThemeMode.CUSTOM,
                            onClick = {
                                val currentBg = if (theme.mode == ThemeMode.CUSTOM) theme.backgroundColorHex else "#2E3440"
                                val currentText = if (theme.mode == ThemeMode.CUSTOM) theme.textColorHex else "#ECEFF4"
                                val currentAccent = if (theme.mode == ThemeMode.CUSTOM) theme.accentColorHex else "#88C0D0"
                                onThemeChange(
                                    ReaderTheme(
                                        mode = ThemeMode.CUSTOM,
                                        backgroundColorHex = currentBg,
                                        textColorHex = currentText,
                                        accentColorHex = currentAccent
                                    )
                                )
                            }
                        )
                    }

                    if (theme.mode == ThemeMode.CUSTOM) {
                        Spacer(modifier = Modifier.height(6.dp))
                        CustomThemeEditor(
                            theme = theme,
                            onThemeChange = onThemeChange,
                            onSaveTheme = onSaveCustomTheme
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

                // Screen Orientation Lock
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Orientation",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = orientationLock == OrientationLock.AUTO,
                            onClick = { onOrientationLockChange(OrientationLock.AUTO) },
                            label = { Text("Auto") }
                        )
                        FilterChip(
                            selected = orientationLock == OrientationLock.PORTRAIT,
                            onClick = { onOrientationLockChange(OrientationLock.PORTRAIT) },
                            label = { Text("Portrait") }
                        )
                        FilterChip(
                            selected = orientationLock == OrientationLock.LANDSCAPE,
                            onClick = { onOrientationLockChange(OrientationLock.LANDSCAPE) },
                            label = { Text("Landscape") }
                        )
                    }
                }

                // Keep Screen On
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Keep Screen On",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Prevent display from sleeping while reading",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = keepScreenOn,
                        onCheckedChange = onKeepScreenOnChange
                    )
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

                // Text Alignment: Default, Justify, Align Left
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Text Alignment",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = typography.textAlignment == ReaderTypography.TextAlignment.DEFAULT,
                            onClick = { onTypographyChange(typography.copy(textAlignment = ReaderTypography.TextAlignment.DEFAULT)) },
                            label = { Text("Default") }
                        )
                        FilterChip(
                            selected = typography.textAlignment == ReaderTypography.TextAlignment.JUSTIFY,
                            onClick = { onTypographyChange(typography.copy(textAlignment = ReaderTypography.TextAlignment.JUSTIFY)) },
                            label = { Text("Justify") }
                        )
                        FilterChip(
                            selected = typography.textAlignment == ReaderTypography.TextAlignment.START,
                            onClick = { onTypographyChange(typography.copy(textAlignment = ReaderTypography.TextAlignment.START)) },
                            label = { Text("Align Left") }
                        )
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
                            text = "Line Height",
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
                            label = { Text("1.5x (Normal)") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = typography.lineHeightMultiplier > 1.55f && typography.lineHeightMultiplier <= 1.85f,
                            onClick = { onTypographyChange(typography.copy(lineHeightMultiplier = 1.8f)) },
                            label = { Text("1.8x (Relaxed)") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = typography.lineHeightMultiplier > 1.85f,
                            onClick = { onTypographyChange(typography.copy(lineHeightMultiplier = 2.0f)) },
                            label = { Text("2.0x (Loose)") },
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
                            text = "Inter-Page Gap & Margins",
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
                            text = "Vertical Margin",
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
private fun CustomThemeEditor(
    theme: ReaderTheme,
    onThemeChange: (ReaderTheme) -> Unit,
    onSaveTheme: (CustomTheme) -> Unit = {}
) {
    var bgHex by remember(theme.backgroundColorHex) { mutableStateOf(theme.backgroundColorHex) }
    var textHex by remember(theme.textColorHex) { mutableStateOf(theme.textColorHex) }
    var accentHex by remember(theme.accentColorHex) { mutableStateOf(theme.accentColorHex) }
    var themeName by remember { mutableStateOf("") }

    data class ThemePreset(val name: String, val bg: String, val text: String, val accent: String)
    val presets = listOf(
        ThemePreset("Nord", "#2E3440", "#ECEFF4", "#88C0D0"),
        ThemePreset("Solarized Dark", "#002B36", "#839496", "#268BD2"),
        ThemePreset("Solarized Light", "#FDF6E3", "#657B83", "#2AA198"),
        ThemePreset("Gruvbox Dark", "#282828", "#EBDBB2", "#FE8019"),
        ThemePreset("Gruvbox Light", "#FBF1C7", "#3C3836", "#D65D0E"),
        ThemePreset("Rose Pine", "#191724", "#E0DEF4", "#EBBCBA"),
        ThemePreset("Forest Green", "#1A2E26", "#E2E8F0", "#4ADE80"),
        ThemePreset("Catppuccin", "#1E1E2E", "#CDD6F4", "#CBA6F7")
    )

    val accentPresets = listOf(
        "#3B82F6", // Blue
        "#14B8A6", // Teal
        "#10B981", // Emerald
        "#F59E0B", // Amber
        "#F97316", // Coral
        "#F43F5E", // Rose
        "#8B5CF6", // Purple
        "#6366F1"  // Indigo
    )

    val currentBgColor = parseHexToColor(bgHex)
    val currentTextColor = parseHexToColor(textHex)
    val currentAccentColor = parseHexToColor(accentHex)

    val accentLuminance = 0.299f * currentAccentColor.red + 0.587f * currentAccentColor.green + 0.114f * currentAccentColor.blue
    val onAccentColor = if (accentLuminance > 0.55f) Color(0xFF121212) else Color(0xFFFFFFFF)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Custom Theme Colors",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            // Live Preview Card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = currentBgColor,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Chapter Preview",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = currentTextColor
                    )
                    Text(
                        text = "Live preview of your custom background, text, and primary accent color combination.",
                        style = MaterialTheme.typography.bodySmall,
                        color = currentTextColor
                    )

                    // Accent Preview Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = currentAccentColor
                        ) {
                            Text(
                                text = "Primary Button",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = onAccentColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = currentAccentColor.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "Active Chip",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = currentAccentColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Quick Color Palettes
            Text(
                text = "Preset Palettes",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    val presetBg = parseHexToColor(preset.bg)
                    val presetAcc = parseHexToColor(preset.accent)
                    val isSelected = bgHex.equals(preset.bg, ignoreCase = true) &&
                        textHex.equals(preset.text, ignoreCase = true) &&
                        accentHex.equals(preset.accent, ignoreCase = true)

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(presetBg)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f),
                                shape = CircleShape
                            )
                            .clickable {
                                bgHex = preset.bg
                                textHex = preset.text
                                accentHex = preset.accent
                                onThemeChange(
                                    theme.copy(
                                        mode = ThemeMode.CUSTOM,
                                        backgroundColorHex = preset.bg,
                                        textColorHex = preset.text,
                                        accentColorHex = preset.accent
                                    )
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "A",
                            color = presetAcc,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Primary Accent Color Swatches
            Text(
                text = "Primary Accent Color",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                accentPresets.forEach { hex ->
                    val swatchColor = parseHexToColor(hex)
                    val isSelected = accentHex.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(swatchColor)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f),
                                shape = CircleShape
                            )
                            .clickable {
                                accentHex = hex
                                onThemeChange(
                                    theme.copy(
                                        mode = ThemeMode.CUSTOM,
                                        backgroundColorHex = bgHex,
                                        textColorHex = textHex,
                                        accentColorHex = hex
                                    )
                                )
                            }
                    )
                }
            }

            // Hex Inputs for Background, Text, and Accent
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = bgHex,
                    onValueChange = { newBg ->
                        bgHex = newBg
                        if (newBg.startsWith("#") && (newBg.length == 7 || newBg.length == 9)) {
                            onThemeChange(theme.copy(mode = ThemeMode.CUSTOM, backgroundColorHex = newBg, textColorHex = textHex, accentColorHex = accentHex))
                        }
                    },
                    label = { Text("Background") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = textHex,
                    onValueChange = { newTxt ->
                        textHex = newTxt
                        if (newTxt.startsWith("#") && (newTxt.length == 7 || newTxt.length == 9)) {
                            onThemeChange(theme.copy(mode = ThemeMode.CUSTOM, backgroundColorHex = bgHex, textColorHex = newTxt, accentColorHex = accentHex))
                        }
                    },
                    label = { Text("Text") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = accentHex,
                    onValueChange = { newAcc ->
                        accentHex = newAcc
                        if (newAcc.startsWith("#") && (newAcc.length == 7 || newAcc.length == 9)) {
                            onThemeChange(theme.copy(mode = ThemeMode.CUSTOM, backgroundColorHex = bgHex, textColorHex = textHex, accentColorHex = newAcc))
                        }
                    },
                    label = { Text("Accent") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // Save Custom Theme to Vault
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = themeName,
                    onValueChange = { themeName = it },
                    placeholder = { Text("Theme name...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val name = themeName.trim().ifEmpty { "My Custom Theme" }
                        val id = "theme_${name.lowercase().replace(' ', '_')}_${bgHex.removePrefix("#")}"
                        onSaveTheme(
                            CustomTheme(
                                id = id,
                                name = name,
                                backgroundColorHex = bgHex,
                                textColorHex = textHex,
                                accentColorHex = accentHex
                            )
                        )
                        themeName = ""
                    }
                ) {
                    Icon(imageVector = Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save Theme")
                }
            }
        }
    }
}

@Composable
private fun CustomSavedThemeOption(
    name: String,
    bgColor: Color,
    textColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = name.take(7),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Delete $name",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(14.dp)
                    .clickable(onClick = onDelete)
            )
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
