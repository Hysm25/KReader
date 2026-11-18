package io.github.kreader.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kreader.core.engine.TocItem
import io.github.kreader.core.model.Book
import io.github.kreader.core.model.BookSource
import io.github.kreader.core.model.CustomFont
import io.github.kreader.core.model.Highlight
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ReaderTypography
import io.github.kreader.core.model.isRtlText
import io.github.kreader.engine.epub.DesktopEpubDocument
import io.github.kreader.engine.epub.DesktopSpineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.io.File

@Composable
actual fun EpubNavigatorHost(
    book: Book,
    initialLocator: Locator?,
    theme: ReaderTheme,
    typography: ReaderTypography,
    isScrollMode: Boolean,
    isTwoPageSpread: Boolean,
    controller: EpubNavigatorController,
    customFonts: List<CustomFont>,
    highlights: List<Highlight>,
    onHighlightClicked: (String) -> Unit,
    onAddHighlight: (id: String, locator: Locator, colorHex: String) -> Unit,
    onAddNoteRequested: (Locator, String) -> Unit,
    onProgressChanged: (progression: Float, locator: Locator) -> Unit,
    onTableOfContentsLoaded: (List<TocItem>) -> Unit,
    onDirectionDetected: (isRtl: Boolean) -> Unit,
    onTap: () -> Unit,
    onPageCountLoaded: (Int) -> Unit,
    modifier: Modifier
) {
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    var document by remember { mutableStateOf<DesktopEpubDocument?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var currentSpineIndex by remember { mutableIntStateOf(0) }
    var currentBlocks by remember { mutableStateOf<List<ReaderContentBlock>>(emptyList()) }
    var isRtl by remember { mutableStateOf(false) }

    // Page-based navigation state (for non-scroll mode)
    var currentPageIndex by remember { mutableIntStateOf(0) }
    val blocksPerPage = 6

    // Decoded image cache
    val imageCache = remember { mutableStateMapOf<String, ImageBitmap>() }

    val currentOnProgressChanged by rememberUpdatedState(onProgressChanged)
    val currentOnTableOfContentsLoaded by rememberUpdatedState(onTableOfContentsLoaded)
    val currentOnDirectionDetected by rememberUpdatedState(onDirectionDetected)
    val currentOnPageCountLoaded by rememberUpdatedState(onPageCountLoaded)

    // Resolve custom font family if selected
    val activeFontFamily = remember(typography.fontFamily, customFonts) {
        val selected = customFonts.firstOrNull { it.name.equals(typography.fontFamily, ignoreCase = true) }
        if (selected != null && File(selected.path).exists()) {
            runCatching {
                FontFamily(androidx.compose.ui.text.platform.Font(File(selected.path)))
            }.getOrNull()
        } else {
            when (typography.fontFamily.lowercase()) {
                "serif" -> FontFamily.Serif
                "sans-serif" -> FontFamily.SansSerif
                "monospace" -> FontFamily.Monospace
                else -> FontFamily.Default
            }
        }
    } ?: FontFamily.Default

    // Resolve theme colors
    val backgroundColor = remember(theme.backgroundColorHex) {
        runCatching {
            val hex = theme.backgroundColorHex.removePrefix("#")
            Color(hex.toLong(16) or 0xFF000000L)
        }.getOrDefault(Color(0xFFFFFFFF))
    }

    val textColor = remember(theme.textColorHex) {
        runCatching {
            val hex = theme.textColorHex.removePrefix("#")
            Color(hex.toLong(16) or 0xFF000000L)
        }.getOrDefault(Color(0xFF1E293B))
    }

    // 1. Open EPUB Document
    LaunchedEffect(book.id) {
        isLoading = true
        errorMessage = null
        withContext(Dispatchers.IO) {
            val path = when (val source = book.source) {
                is BookSource.FilePath -> source.path
                is BookSource.ContentUri -> source.uriString
                else -> null
            }
            if (path == null) {
                errorMessage = "Invalid book source"
                isLoading = false
                return@withContext
            }
            val file = File(path)
            DesktopEpubDocument.open(file).fold(
                onSuccess = { doc ->
                    document = doc
                    val rtl = doc.metadata.isRtl || isRtlText(book.title)
                    isRtl = rtl
                    currentOnDirectionDetected(rtl)
                    currentOnTableOfContentsLoaded(doc.tableOfContents)
                    if (doc.spine.isNotEmpty()) {
                        currentOnPageCountLoaded(doc.spine.size)
                    }

                    // Find initial spine item
                    val initHref = initialLocator?.href
                    val spineIdx = if (!initHref.isNullOrBlank()) {
                        doc.spine.indexOfFirst {
                            it.originalHref.equals(initHref, ignoreCase = true) ||
                            initHref.contains(it.originalHref) ||
                            it.originalHref.contains(initHref)
                        }.coerceAtLeast(0)
                    } else 0

                    currentSpineIndex = spineIdx
                    currentPageIndex = 0

                    // Parse chapter blocks
                    if (doc.spine.isNotEmpty()) {
                        val chapter = doc.spine[spineIdx]
                        val xhtml = doc.readChapter(chapter)
                        currentBlocks = EpubBlockParser.parse(xhtml, chapter.originalHref)
                    }
                    isLoading = false
                },
                onFailure = { err ->
                    errorMessage = err.message ?: "Failed to open EPUB"
                    isLoading = false
                }
            )
        }
    }

    // Update chapter when spine index changes
    fun loadSpineChapter(spineIdx: Int, initialBlockOffset: Int = 0) {
        val doc = document ?: return
        if (spineIdx in doc.spine.indices) {
            currentSpineIndex = spineIdx
            currentPageIndex = initialBlockOffset / blocksPerPage
            val chapter = doc.spine[spineIdx]
            val xhtml = doc.readChapter(chapter)
            currentBlocks = EpubBlockParser.parse(xhtml, chapter.originalHref)

            val totalSpine = doc.spine.size.coerceAtLeast(1)
            val chapterProg = if (currentBlocks.isNotEmpty()) {
                (currentPageIndex * blocksPerPage).toFloat() / currentBlocks.size.toFloat()
            } else 0f
            val totalProg = (spineIdx.toFloat() + chapterProg) / totalSpine.toFloat()

            val loc = Locator(
                href = chapter.originalHref,
                type = chapter.mediaType,
                title = chapter.originalHref,
                locations = Locator.Locations(
                    progression = chapterProg,
                    totalProgression = totalProg
                )
            )
            currentOnProgressChanged(totalProg, loc)
        }
    }

    // Helper navigation actions
    fun advancePage() {
        val maxPages = (currentBlocks.size + blocksPerPage - 1) / blocksPerPage
        if (currentPageIndex < maxPages - 1) {
            currentPageIndex++
            val doc = document ?: return
            val chapter = doc.spine.getOrNull(currentSpineIndex) ?: return
            val chapterProg = (currentPageIndex * blocksPerPage).toFloat() / currentBlocks.size.coerceAtLeast(1).toFloat()
            val totalProg = (currentSpineIndex.toFloat() + chapterProg) / doc.spine.size.coerceAtLeast(1).toFloat()
            val loc = Locator(
                href = chapter.originalHref,
                type = chapter.mediaType,
                locations = Locator.Locations(progression = chapterProg, totalProgression = totalProg)
            )
            currentOnProgressChanged(totalProg, loc)
        } else {
            // Advance to next chapter
            val doc = document ?: return
            if (currentSpineIndex < doc.spine.size - 1) {
                loadSpineChapter(currentSpineIndex + 1, initialBlockOffset = 0)
            }
        }
    }

    fun retreatPage() {
        if (currentPageIndex > 0) {
            currentPageIndex--
            val doc = document ?: return
            val chapter = doc.spine.getOrNull(currentSpineIndex) ?: return
            val chapterProg = (currentPageIndex * blocksPerPage).toFloat() / currentBlocks.size.coerceAtLeast(1).toFloat()
            val totalProg = (currentSpineIndex.toFloat() + chapterProg) / doc.spine.size.coerceAtLeast(1).toFloat()
            val loc = Locator(
                href = chapter.originalHref,
                type = chapter.mediaType,
                locations = Locator.Locations(progression = chapterProg, totalProgression = totalProg)
            )
            currentOnProgressChanged(totalProg, loc)
        } else {
            // Retreat to previous chapter
            if (currentSpineIndex > 0) {
                val doc = document ?: return
                val prevSpine = currentSpineIndex - 1
                val chapter = doc.spine[prevSpine]
                val xhtml = doc.readChapter(chapter)
                val blocks = EpubBlockParser.parse(xhtml, chapter.originalHref)
                val lastOffset = (blocks.size - 1).coerceAtLeast(0)
                loadSpineChapter(prevSpine, initialBlockOffset = lastOffset)
            }
        }
    }

    // Bind Imperative Controller Hooks
    DisposableEffect(document, currentSpineIndex, currentBlocks) {
        controller.goTo = { locator ->
            val doc = document
            if (doc != null) {
                val idx = doc.spine.indexOfFirst {
                    it.originalHref.equals(locator.href, ignoreCase = true) ||
                    locator.href.contains(it.originalHref) ||
                    it.originalHref.contains(locator.href)
                }
                if (idx >= 0) {
                    val targetBlockOffset = locator.locations.position ?: 0
                    loadSpineChapter(idx, initialBlockOffset = targetBlockOffset)
                }
            }
        }
        controller.goNext = {
            advancePage()
        }
        controller.goPrevious = {
            retreatPage()
        }
        controller.seekToProgression = { targetProgression ->
            val doc = document
            if (doc != null && doc.spine.isNotEmpty()) {
                val targetSpineFloat = targetProgression * doc.spine.size.toFloat()
                val targetSpineIdx = targetSpineFloat.toInt().coerceIn(0, doc.spine.size - 1)
                val fraction = targetSpineFloat - targetSpineIdx
                val chapter = doc.spine[targetSpineIdx]
                val xhtml = doc.readChapter(chapter)
                val blocks = EpubBlockParser.parse(xhtml, chapter.originalHref)
                val targetBlock = (fraction * blocks.size).toInt().coerceIn(0, (blocks.size - 1).coerceAtLeast(0))
                loadSpineChapter(targetSpineIdx, initialBlockOffset = targetBlock)
            }
        }
        controller.search = { query ->
            withContext(Dispatchers.IO) {
                document?.search(query) ?: emptyList()
            }
        }

        onDispose {
            controller.goTo = null
            controller.goNext = null
            controller.goPrevious = null
            controller.seekToProgression = null
            controller.search = null
        }
    }

    // Request keyboard focus
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionRight -> {
                            if (isRtl) retreatPage() else advancePage()
                            true
                        }
                        Key.DirectionLeft -> {
                            if (isRtl) advancePage() else retreatPage()
                            true
                        }
                        Key.Spacebar -> {
                            if (event.isShiftPressed) retreatPage() else advancePage()
                            true
                        }
                        Key.PageDown -> {
                            advancePage()
                            true
                        }
                        Key.PageUp -> {
                            retreatPage()
                            true
                        }
                        Key.Escape -> {
                            onTap()
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            errorMessage != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = errorMessage ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            isScrollMode -> {
                // Continuous Scroll Viewport (Constrained line length, centered)
                val listState = rememberLazyListState()
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(max = 760.dp)
                            .padding(
                                horizontal = typography.paddingHorizontal.dp,
                                vertical = typography.paddingVertical.dp
                            )
                    ) {
                        items(currentBlocks, key = { it.id }) { block ->
                            RenderBlock(
                                block = block,
                                highlights = highlights,
                                textColor = textColor,
                                fontFamily = activeFontFamily,
                                typography = typography,
                                isRtl = isRtl,
                                document = document,
                                currentSpineZipPath = document?.spine?.getOrNull(currentSpineIndex)?.zipPath,
                                imageCache = imageCache,
                                onHighlightClicked = onHighlightClicked,
                                onAddHighlight = { id, quote, colorHex ->
                                    val loc = Locator(
                                        href = document?.spine?.getOrNull(currentSpineIndex)?.originalHref ?: "content.xhtml",
                                        type = "application/xhtml+xml",
                                        text = Locator.LocatorText(highlight = quote)
                                    )
                                    onAddHighlight(id, loc, colorHex)
                                },
                                onAddNoteRequested = { quote ->
                                    val loc = Locator(
                                        href = document?.spine?.getOrNull(currentSpineIndex)?.originalHref ?: "content.xhtml",
                                        type = "application/xhtml+xml",
                                        text = Locator.LocatorText(highlight = quote)
                                    )
                                    onAddNoteRequested(loc, quote)
                                }
                            )
                            Spacer(modifier = Modifier.height((typography.fontSizeSp * 0.4f).dp))
                        }
                    }
                }
            }
            else -> {
                // Paginated Viewport: Responsive Two-Page Spread on Widescreen or Centered Single Page
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val isWideScreen = isTwoPageSpread && maxWidth >= 960.dp

                    if (isWideScreen) {
                        // Two-Page Spread: Left Column & Right Column with Center Spine Divider
                        val perColumn = 3
                        val totalPerSpread = perColumn * 2
                        val startIdx = currentPageIndex * totalPerSpread
                        val page1End = (startIdx + perColumn).coerceAtMost(currentBlocks.size)
                        val page2End = (startIdx + totalPerSpread).coerceAtMost(currentBlocks.size)

                        val page1Blocks = if (startIdx < currentBlocks.size) currentBlocks.subList(startIdx, page1End) else emptyList()
                        val page2Blocks = if (page1End < currentBlocks.size) currentBlocks.subList(page1End, page2End) else emptyList()

                        // In RTL: Page 1 is on right, Page 2 is on left!
                        val leftBlocks = if (isRtl) page2Blocks else page1Blocks
                        val rightBlocks = if (isRtl) page1Blocks else page2Blocks

                        Row(
                            modifier = Modifier
                                .fillMaxHeight()
                                .widthIn(max = 1140.dp)
                                .padding(horizontal = 24.dp, vertical = typography.paddingVertical.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            // Left Page Column
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(end = 24.dp)
                            ) {
                                for (block in leftBlocks) {
                                    RenderBlock(
                                        block = block,
                                        highlights = highlights,
                                        textColor = textColor,
                                        fontFamily = activeFontFamily,
                                        typography = typography,
                                        isRtl = isRtl,
                                        document = document,
                                        currentSpineZipPath = document?.spine?.getOrNull(currentSpineIndex)?.zipPath,
                                        imageCache = imageCache,
                                        onHighlightClicked = onHighlightClicked,
                                        onAddHighlight = { id, quote, colorHex ->
                                            val loc = Locator(
                                                href = document?.spine?.getOrNull(currentSpineIndex)?.originalHref ?: "content.xhtml",
                                                type = "application/xhtml+xml",
                                                text = Locator.LocatorText(highlight = quote)
                                            )
                                            onAddHighlight(id, loc, colorHex)
                                        },
                                        onAddNoteRequested = { quote ->
                                            val loc = Locator(
                                                href = document?.spine?.getOrNull(currentSpineIndex)?.originalHref ?: "content.xhtml",
                                                type = "application/xhtml+xml",
                                                text = Locator.LocatorText(highlight = quote)
                                            )
                                            onAddNoteRequested(loc, quote)
                                        }
                                    )
                                    Spacer(modifier = Modifier.height((typography.fontSizeSp * 0.35f).dp))
                                }
                            }

                            // Book Spine Crease Vertical Divider
                            VerticalDivider(
                                color = textColor.copy(alpha = 0.12f),
                                thickness = 1.dp,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(vertical = 12.dp)
                            )

                            // Right Page Column
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(start = 24.dp)
                            ) {
                                for (block in rightBlocks) {
                                    RenderBlock(
                                        block = block,
                                        highlights = highlights,
                                        textColor = textColor,
                                        fontFamily = activeFontFamily,
                                        typography = typography,
                                        isRtl = isRtl,
                                        document = document,
                                        currentSpineZipPath = document?.spine?.getOrNull(currentSpineIndex)?.zipPath,
                                        imageCache = imageCache,
                                        onHighlightClicked = onHighlightClicked,
                                        onAddHighlight = { id, quote, colorHex ->
                                            val loc = Locator(
                                                href = document?.spine?.getOrNull(currentSpineIndex)?.originalHref ?: "content.xhtml",
                                                type = "application/xhtml+xml",
                                                text = Locator.LocatorText(highlight = quote)
                                            )
                                            onAddHighlight(id, loc, colorHex)
                                        },
                                        onAddNoteRequested = { quote ->
                                            val loc = Locator(
                                                href = document?.spine?.getOrNull(currentSpineIndex)?.originalHref ?: "content.xhtml",
                                                type = "application/xhtml+xml",
                                                text = Locator.LocatorText(highlight = quote)
                                            )
                                            onAddNoteRequested(loc, quote)
                                        }
                                    )
                                    Spacer(modifier = Modifier.height((typography.fontSizeSp * 0.35f).dp))
                                }
                            }
                        }
                    } else {
                        // Single Page Column (Centered & Constrained)
                        val startIdx = currentPageIndex * blocksPerPage
                        val endIdx = (startIdx + blocksPerPage).coerceAtMost(currentBlocks.size)
                        val visibleBlocks = if (startIdx < currentBlocks.size) currentBlocks.subList(startIdx, endIdx) else emptyList()

                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .widthIn(max = 760.dp)
                                .padding(
                                    horizontal = typography.paddingHorizontal.dp,
                                    vertical = typography.paddingVertical.dp
                                )
                        ) {
                            for (block in visibleBlocks) {
                                RenderBlock(
                                    block = block,
                                    highlights = highlights,
                                    textColor = textColor,
                                    fontFamily = activeFontFamily,
                                    typography = typography,
                                    isRtl = isRtl,
                                    document = document,
                                    currentSpineZipPath = document?.spine?.getOrNull(currentSpineIndex)?.zipPath,
                                    imageCache = imageCache,
                                    onHighlightClicked = onHighlightClicked,
                                    onAddHighlight = { id, quote, colorHex ->
                                        val loc = Locator(
                                            href = document?.spine?.getOrNull(currentSpineIndex)?.originalHref ?: "content.xhtml",
                                            type = "application/xhtml+xml",
                                            text = Locator.LocatorText(highlight = quote)
                                        )
                                        onAddHighlight(id, loc, colorHex)
                                    },
                                    onAddNoteRequested = { quote ->
                                        val loc = Locator(
                                            href = document?.spine?.getOrNull(currentSpineIndex)?.originalHref ?: "content.xhtml",
                                            type = "application/xhtml+xml",
                                            text = Locator.LocatorText(highlight = quote)
                                        )
                                        onAddNoteRequested(loc, quote)
                                    }
                                )
                                Spacer(modifier = Modifier.height((typography.fontSizeSp * 0.35f).dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderBlock(
    block: ReaderContentBlock,
    highlights: List<Highlight>,
    textColor: Color,
    fontFamily: FontFamily,
    typography: ReaderTypography,
    isRtl: Boolean,
    document: DesktopEpubDocument?,
    currentSpineZipPath: String?,
    imageCache: MutableMap<String, ImageBitmap>,
    onHighlightClicked: (String) -> Unit,
    onAddHighlight: (id: String, quote: String, colorHex: String) -> Unit,
    onAddNoteRequested: (String) -> Unit
) {
    val textDirection = if (isRtl) TextDirection.Rtl else TextDirection.Ltr
    val textAlign = if (isRtl) TextAlign.Right else TextAlign.Left

    when (block) {
        is ReaderContentBlock.Paragraph -> {
            val styledText = remember(block.rawText, highlights) {
                EpubBlockParser.applyHighlights(block.rawText, block.annotatedText, highlights)
            }
            SelectionContainer {
                Text(
                    text = styledText,
                    color = textColor,
                    fontFamily = fontFamily,
                    fontSize = typography.fontSizeSp.sp,
                    lineHeight = (typography.fontSizeSp * typography.lineHeightMultiplier).sp,
                    textAlign = textAlign,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textDirection = textDirection
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        is ReaderContentBlock.Heading -> {
            val headingSize = when (block.level) {
                1 -> (typography.fontSizeSp * 1.6f).sp
                2 -> (typography.fontSizeSp * 1.4f).sp
                3 -> (typography.fontSizeSp * 1.25f).sp
                else -> (typography.fontSizeSp * 1.1f).sp
            }
            Text(
                text = block.annotatedText,
                color = textColor,
                fontFamily = fontFamily,
                fontSize = headingSize,
                fontWeight = FontWeight.Bold,
                lineHeight = (headingSize.value * 1.25f).sp,
                textAlign = textAlign,
                style = MaterialTheme.typography.titleLarge.copy(textDirection = textDirection),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            )
        }
        is ReaderContentBlock.Blockquote -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(textColor.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = block.annotatedText,
                    color = textColor.copy(alpha = 0.85f),
                    fontFamily = fontFamily,
                    fontSize = (typography.fontSizeSp * 0.95f).sp,
                    fontStyle = FontStyle.Italic,
                    lineHeight = (typography.fontSizeSp * typography.lineHeightMultiplier).sp,
                    textAlign = textAlign,
                    style = MaterialTheme.typography.bodyMedium.copy(textDirection = textDirection)
                )
            }
        }
        is ReaderContentBlock.ListItem -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp, horizontal = 8.dp),
                horizontalArrangement = if (isRtl) Arrangement.End else Arrangement.Start
            ) {
                if (!isRtl) {
                    Text(
                        text = "${block.bullet} ",
                        color = textColor.copy(alpha = 0.7f),
                        fontSize = typography.fontSizeSp.sp,
                        fontFamily = fontFamily
                    )
                }
                Text(
                    text = block.annotatedText,
                    color = textColor,
                    fontFamily = fontFamily,
                    fontSize = typography.fontSizeSp.sp,
                    lineHeight = (typography.fontSizeSp * typography.lineHeightMultiplier).sp,
                    textAlign = textAlign,
                    style = MaterialTheme.typography.bodyLarge.copy(textDirection = textDirection)
                )
                if (isRtl) {
                    Text(
                        text = " ${block.bullet}",
                        color = textColor.copy(alpha = 0.7f),
                        fontSize = typography.fontSizeSp.sp,
                        fontFamily = fontFamily
                    )
                }
            }
        }
        is ReaderContentBlock.ImageBlock -> {
            var bitmap by remember(block.imageSource) { mutableStateOf(imageCache[block.imageSource]) }
            LaunchedEffect(block.imageSource) {
                if (bitmap == null && document != null) {
                    withContext(Dispatchers.IO) {
                        val bytes = document.readAssetBytes(block.imageSource, currentSpineZipPath)
                        if (bytes != null && bytes.isNotEmpty()) {
                            runCatching {
                                val skiaImage = Image.makeFromEncoded(bytes)
                                val bmp = skiaImage.toComposeImageBitmap()
                                imageCache[block.imageSource] = bmp
                                bitmap = bmp
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                val currentBmp = bitmap
                if (currentBmp != null) {
                    androidx.compose.foundation.Image(
                        bitmap = currentBmp,
                        contentDescription = block.altText ?: "EPUB Image",
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(140.dp)
                            .background(textColor.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = textColor.copy(alpha = 0.4f),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }
        is ReaderContentBlock.Divider -> {
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                thickness = 1.dp,
                color = textColor.copy(alpha = 0.2f)
            )
        }
    }
}
