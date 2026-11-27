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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.OrientationLock
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ReaderTypography
import io.github.kreader.core.model.ThemeMode

object ReaderThemePresets {
    val System = ReaderTheme(
        mode = ThemeMode.SYSTEM
    )
    val SystemLight = ReaderTheme(
        mode = ThemeMode.SYSTEM_LIGHT
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

@Composable
fun ReaderNavigationDrawer(
    isOpen: Boolean,
    currentTab: NavigationDrawerTab,
    onTabSelected: (NavigationDrawerTab) -> Unit,
    tocItems: List<TocItem>,
    bookmarks: List<Bookmark>,
    currentLocator: Locator?,
    onNavigateToLocator: (Locator) -> Unit,
    onDeleteBookmark: (String) -> Unit,
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
                .widthIn(max = 400.dp)
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

                // Tabs: Contents, Bookmarks
                TabRow(
                    selectedTabIndex = currentTab.ordinal.coerceIn(0, 1),
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
                        text = { Text("Contents (${tocItems.size})", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                    Tab(
                        selected = currentTab == NavigationDrawerTab.BOOKMARKS,
                        onClick = { onTabSelected(NavigationDrawerTab.BOOKMARKS) },
                        icon = { Icon(Icons.Rounded.Bookmark, contentDescription = null) },
                        text = { Text("Bookmarks (${bookmarks.size})", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
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
                val isActive = normalizeTocHref(currentLocator?.href).let { curr ->
                    curr.isNotBlank() && curr == normalizeTocHref(flatItem.item.locator.href)
                }
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

    val keyboardController = LocalSoftwareKeyboardController.current
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
                .fillMaxHeight(0.85f)
                .fillMaxWidth()
                .widthIn(max = 600.dp)
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
                            if (!isSearching) {
                                IconButton(
                                    onClick = onSearch,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = "Run search",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
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
                                    .clickable {
                                        keyboardController?.hide()
                                        onResultClick(result)
                                    },
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

enum class ReaderSettingsTab {
    THEME,
    TYPOGRAPHY,
    LAYOUT
}

@Composable
fun <T> SegmentedButtonGroup(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { onSelect(value) },
                    shape = RoundedCornerShape(9.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    tonalElevation = if (isSelected) 2.dp else 0.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    isOpen: Boolean,
    theme: ReaderTheme,
    typography: ReaderTypography,
    isScrollMode: Boolean,
    title: String = "Reading Settings",
    isGlobal: Boolean = false,
    customFonts: List<CustomFont> = emptyList(),
    customThemes: List<CustomTheme> = emptyList(),
    keepScreenOn: Boolean = false,
    volumeKeyNavigation: Boolean = true,
    orientationLock: OrientationLock = OrientationLock.AUTO,
    onThemeChange: (ReaderTheme) -> Unit,
    onTypographyChange: (ReaderTypography) -> Unit,
    onScrollModeChange: (Boolean) -> Unit,
    onResetToDefaults: () -> Unit = {},
    onImportCustomFont: (CustomFont) -> Unit = {},
    onDeleteCustomFont: (String) -> Unit = {},
    onSaveCustomTheme: (CustomTheme) -> Unit = {},
    onDeleteCustomTheme: (String) -> Unit = {},
    onKeepScreenOnChange: (Boolean) -> Unit = {},
    onVolumeKeyNavigationChange: (Boolean) -> Unit = {},
    onOrientationLockChange: (OrientationLock) -> Unit = {},
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    val fontPickerLauncher = rememberFontPickerLauncher { importedFont ->
        onImportCustomFont(importedFont)
    }

    val interactionSource = remember { MutableInteractionSource() }

    var selectedTab by remember { mutableStateOf(ReaderSettingsTab.THEME) }
    var isDraggingHorizontalPadding by remember { mutableStateOf(false) }
    var tempHorizontalPadding by remember { mutableFloatStateOf(typography.paddingHorizontal.toFloat()) }
    LaunchedEffect(typography.paddingHorizontal) {
        if (!isDraggingHorizontalPadding) tempHorizontalPadding = typography.paddingHorizontal.toFloat()
    }

    var isDraggingVerticalPadding by remember { mutableStateOf(false) }
    var tempVerticalPadding by remember { mutableFloatStateOf(typography.paddingVertical.toFloat()) }
    LaunchedEffect(typography.paddingVertical) {
        if (!isDraggingVerticalPadding) tempVerticalPadding = typography.paddingVertical.toFloat()
    }

    var isDraggingLineHeight by remember { mutableStateOf(false) }
    var tempLineHeight by remember { mutableFloatStateOf(typography.lineHeightMultiplier) }
    LaunchedEffect(typography.lineHeightMultiplier) {
        if (!isDraggingLineHeight) tempLineHeight = typography.lineHeightMultiplier
    }

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
                .widthIn(max = 600.dp)
                .fillMaxHeight(0.85f)
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
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
            ) {
                // Drag handle & Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                            .align(Alignment.CenterHorizontally)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close settings"
                            )
                        }
                    }
                }

                // 3 Material 3 Tabs
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            thickness = 0.5.dp
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == ReaderSettingsTab.THEME,
                        onClick = { selectedTab = ReaderSettingsTab.THEME },
                        icon = { Icon(Icons.Rounded.Palette, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        text = { Text("Theme") }
                    )
                    Tab(
                        selected = selectedTab == ReaderSettingsTab.TYPOGRAPHY,
                        onClick = { selectedTab = ReaderSettingsTab.TYPOGRAPHY },
                        icon = { Icon(Icons.Rounded.TextFields, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        text = { Text("Typography") }
                    )
                    Tab(
                        selected = selectedTab == ReaderSettingsTab.LAYOUT,
                        onClick = { selectedTab = ReaderSettingsTab.LAYOUT },
                        icon = { Icon(Icons.Rounded.Layers, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        text = { Text("Layout") }
                    )
                }

                // Scrollable tab content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (selectedTab) {
                        ReaderSettingsTab.THEME -> {
                            // 1. Theme Presets Card
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "Theme Preset",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
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
                                            label = "System Light",
                                            bgColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            textColor = MaterialTheme.colorScheme.onSurface,
                                            isSelected = theme.mode == ThemeMode.SYSTEM_LIGHT,
                                            onClick = { onThemeChange(ReaderThemePresets.SystemLight) }
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
                            }

                            // 2. Keep Screen On Card
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Keep Screen On",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
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
                            }
                        }

                        ReaderSettingsTab.TYPOGRAPHY -> {
                            // 1. Override Font Toggle Card
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Override Font",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (typography.overrideFont) "Enforcing custom font across publication" else "Using publisher's embedded fonts",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = typography.overrideFont,
                                        onCheckedChange = { checked ->
                                            val targetFamily = if (checked && (typography.fontFamily.isBlank() || typography.fontFamily.equals("Default", ignoreCase = true))) {
                                                "Serif"
                                            } else {
                                                typography.fontFamily
                                            }
                                            onTypographyChange(typography.copy(overrideFont = checked, fontFamily = targetFamily))
                                        }
                                    )
                                }
                            }

                            // 2. Font Family Card
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Font Family",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        TextButton(onClick = { fontPickerLauncher() }) {
                                            Icon(
                                                imageVector = Icons.Rounded.Add,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Import Font", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }

                                    val baseFontOptions = listOf("Default", "Serif", "Sans-Serif", "Monospace")
                                    val allAvailableFonts = baseFontOptions + customFonts.map { it.name }.filterNot { customName ->
                                        baseFontOptions.any { it.equals(customName, ignoreCase = true) }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        for (fontName in allAvailableFonts) {
                                            val isDefault = fontName.equals("Default", ignoreCase = true)
                                            val isSelected = if (!typography.overrideFont) {
                                                isDefault
                                            } else {
                                                typography.fontFamily.equals(fontName, ignoreCase = true)
                                            }
                                            val isCustomFont = customFonts.any { it.name.equals(fontName, ignoreCase = true) }

                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    if (isDefault) {
                                                        onTypographyChange(typography.copy(fontFamily = "Default", overrideFont = false))
                                                    } else {
                                                        onTypographyChange(typography.copy(fontFamily = fontName, overrideFont = true))
                                                    }
                                                },
                                                label = { Text(if (isDefault) "Default (Publisher)" else fontName) },
                                                trailingIcon = if (isCustomFont) {
                                                    {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Close,
                                                            contentDescription = "Delete $fontName",
                                                            modifier = Modifier
                                                                .size(16.dp)
                                                                .clickable { onDeleteCustomFont(fontName) }
                                                        )
                                                    }
                                                } else null
                                            )
                                        }
                                    }
                                }
                            }

                            // 2. Font Size Card
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Font Size",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
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
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                                        ) {
                                            Text(
                                                text = "${typography.fontSizeSp.toInt()} sp",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
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
                            }

                            // 3. Line Height Card (Continuous Slider)
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Line Height",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                                        ) {
                                            val formatted = kotlin.math.round(tempLineHeight * 10f) / 10f
                                            Text(
                                                text = "${formatted}x",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Slider(
                                        value = tempLineHeight,
                                        onValueChange = {
                                            isDraggingLineHeight = true
                                            tempLineHeight = it
                                            val rounded = kotlin.math.round(it * 10f) / 10f
                                            if (rounded != typography.lineHeightMultiplier) {
                                                onTypographyChange(
                                                    typography.copy(lineHeightMultiplier = rounded)
                                                )
                                            }
                                        },
                                        onValueChangeFinished = {
                                            isDraggingLineHeight = false
                                            val finalVal = kotlin.math.round(tempLineHeight * 10f) / 10f
                                            tempLineHeight = finalVal
                                            onTypographyChange(typography.copy(lineHeightMultiplier = finalVal))
                                        },
                                        valueRange = 1.0f..2.5f,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            // 4. Text Alignment Card (Segmented)
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Text Alignment",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    SegmentedButtonGroup(
                                        options = listOf(
                                            ReaderTypography.TextAlignment.DEFAULT to "Auto",
                                            ReaderTypography.TextAlignment.JUSTIFY to "Justify",
                                            ReaderTypography.TextAlignment.START to "Left"
                                        ),
                                        selected = typography.textAlignment,
                                        onSelect = { onTypographyChange(typography.copy(textAlignment = it)) }
                                    )
                                }
                            }
                        }

                        ReaderSettingsTab.LAYOUT -> {
                            // 1. Reading Mode Card (Segmented)
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Reading Mode",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    SegmentedButtonGroup(
                                        options = listOf(
                                            false to "Paginated",
                                            true to "Scroll"
                                        ),
                                        selected = isScrollMode,
                                        onSelect = { onScrollModeChange(it) }
                                    )
                                }
                            }

                            // 2. Orientation Lock Card (Segmented)
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Orientation",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    SegmentedButtonGroup(
                                        options = listOf(
                                            OrientationLock.AUTO to "Auto",
                                            OrientationLock.PORTRAIT to "Portrait",
                                            OrientationLock.LANDSCAPE to "Landscape"
                                        ),
                                        selected = orientationLock,
                                        onSelect = { onOrientationLockChange(it) }
                                    )
                                }
                            }

                            // 3. Margins Card
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "Page Margins",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    // Horizontal Page Margin
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Horizontal Margin",
                                                style = MaterialTheme.typography.bodyMedium,
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
                                            onValueChange = {
                                                isDraggingHorizontalPadding = true
                                                tempHorizontalPadding = it
                                                val intVal = it.toInt()
                                                if (intVal != typography.paddingHorizontal) {
                                                    onTypographyChange(
                                                        typography.copy(paddingHorizontal = intVal)
                                                    )
                                                }
                                            },
                                            onValueChangeFinished = {
                                                isDraggingHorizontalPadding = false
                                                onTypographyChange(typography.copy(paddingHorizontal = tempHorizontalPadding.toInt()))
                                            },
                                            valueRange = 0f..48f,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    // Vertical Page Margin
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Vertical Margin",
                                                style = MaterialTheme.typography.bodyMedium,
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
                                            onValueChange = {
                                                isDraggingVerticalPadding = true
                                                tempVerticalPadding = it
                                                val intVal = it.toInt()
                                                if (intVal != typography.paddingVertical) {
                                                    onTypographyChange(
                                                        typography.copy(paddingVertical = intVal)
                                                    )
                                                }
                                            },
                                            onValueChangeFinished = {
                                                isDraggingVerticalPadding = false
                                                onTypographyChange(typography.copy(paddingVertical = tempVerticalPadding.toInt()))
                                            },
                                            valueRange = 0f..32f,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }

                            if (!isDesktopPlatform) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Volume Key Navigation",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Turn pages using hardware volume buttons",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = volumeKeyNavigation,
                                            onCheckedChange = onVolumeKeyNavigationChange
                                        )
                                    }
                                }
                            }

                            // 4. Reset Defaults Card
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
                                Text(if (isGlobal) "Reset to Factory Defaults" else "Reset to Global Defaults")
                            }
                        }
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
