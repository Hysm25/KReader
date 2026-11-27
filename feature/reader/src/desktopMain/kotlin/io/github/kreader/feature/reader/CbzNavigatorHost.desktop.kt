package io.github.kreader.feature.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.kreader.core.engine.TocItem
import io.github.kreader.core.model.Book
import io.github.kreader.core.model.BookSource
import io.github.kreader.core.model.CbzImageFit
import io.github.kreader.core.model.CbzReadingMode
import io.github.kreader.core.model.Locator
import io.github.kreader.engine.cbz.DesktopCbzArchive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import java.io.File
import kotlin.math.roundToInt

private sealed interface DesktopCbzState {
    data object Loading : DesktopCbzState
    data class Ready(val archive: DesktopCbzArchive, val pageCount: Int) : DesktopCbzState
    data class Error(val message: String) : DesktopCbzState
}

@Composable
actual fun CbzNavigatorHost(
    book: Book,
    initialLocator: Locator?,
    readingMode: CbzReadingMode,
    imageFit: CbzImageFit,
    controller: EpubNavigatorController,
    previousChapterTitle: String?,
    nextChapterTitle: String?,
    onGoToPreviousChapter: (() -> Unit)?,
    onGoToNextChapter: (() -> Unit)?,
    onProgressChanged: (progression: Float, locator: Locator) -> Unit,
    onTableOfContentsLoaded: (List<TocItem>) -> Unit,
    onPageCountLoaded: (Int) -> Unit,
    onCbzMetadataLoaded: (pageCount: Int, isMangaRtl: Boolean?, detectedMode: CbzReadingMode?) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier
) {
    val scope = rememberCoroutineScope()
    val currentOnProgressChanged by rememberUpdatedState(onProgressChanged)
    val currentOnTap by rememberUpdatedState(onTap)

    var cbzState by remember(book.id, book.source) { mutableStateOf<DesktopCbzState>(DesktopCbzState.Loading) }
    val bitmapCache = remember(book.id) {
        object : LinkedHashMap<Int, ImageBitmap>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ImageBitmap>?): Boolean {
                return size > 24
            }
        }
    }

    LaunchedEffect(book.id, book.source) {
        cbzState = DesktopCbzState.Loading
        val path = (book.source as? BookSource.FilePath)?.path
        if (path == null) {
            cbzState = DesktopCbzState.Error("Unsupported CBZ source on desktop")
            return@LaunchedEffect
        }
        val opened = withContext(Dispatchers.IO) {
            runCatching { DesktopCbzArchive.openFile(File(path)) }
        }
        opened.fold(
            onSuccess = { archive ->
                if (archive.pageCount <= 0) {
                    archive.close()
                    cbzState = DesktopCbzState.Error("No readable images found in CBZ archive.")
                } else {
                    val detectedMode = withContext(Dispatchers.IO) {
                        archive.detectPreferredReadingMode(book.cbzSeriesFolder?.substringAfterLast('/') ?: book.title)
                    }
                    onPageCountLoaded(archive.pageCount)
                    onCbzMetadataLoaded(archive.pageCount, archive.metadata.isMangaRtl, detectedMode)
                    onTableOfContentsLoaded(
                        List(archive.pageCount) { idx ->
                            TocItem(title = "Page ${idx + 1}", locator = cbzPageLocator(idx, archive.pageCount))
                        }
                    )
                    cbzState = DesktopCbzState.Ready(archive, archive.pageCount)
                }
            },
            onFailure = { err ->
                cbzState = DesktopCbzState.Error(err.message ?: "Failed to open CBZ archive")
            }
        )
    }

    DisposableEffect(cbzState) {
        val ready = cbzState as? DesktopCbzState.Ready
        onDispose {
            ready?.archive?.close()
            bitmapCache.clear()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val state = cbzState) {
            DesktopCbzState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            is DesktopCbzState.Error -> Text(
                text = state.message,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
            is DesktopCbzState.Ready -> {
                val pageCount = state.pageCount
                val archive = state.archive
                var currentPageIndex by remember(book.id, pageCount) {
                    mutableIntStateOf(cbzInitialPageIndex(initialLocator, pageCount))
                }

                when (readingMode) {
                    CbzReadingMode.MANGA_RTL,
                    CbzReadingMode.COMIC_LTR -> {
                        val pagerState = rememberPagerState(
                            initialPage = currentPageIndex.coerceIn(0, pageCount - 1),
                            pageCount = { pageCount }
                        )

                        DisposableEffect(pagerState, pageCount) {
                            controller.goNext = {
                                if (pagerState.currentPage < pageCount - 1) {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                } else {
                                    onGoToNextChapter?.invoke()
                                }
                            }
                            controller.goPrevious = {
                                if (pagerState.currentPage > 0) {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                } else {
                                    onGoToPreviousChapter?.invoke()
                                }
                            }
                            controller.goTo = { locator ->
                                scope.launch { pagerState.scrollToPage(cbzInitialPageIndex(locator, pageCount)) }
                            }
                            controller.seekToProgression = { prog ->
                                val target = (prog * (pageCount - 1).coerceAtLeast(1)).roundToInt().coerceIn(0, pageCount - 1)
                                scope.launch { pagerState.scrollToPage(target) }
                            }
                            onDispose {
                                controller.goNext = null
                                controller.goPrevious = null
                                controller.goTo = null
                                controller.seekToProgression = null
                            }
                        }

                        LaunchedEffect(pagerState, pageCount) {
                            snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect { page ->
                                currentPageIndex = page
                                val loc = cbzPageLocator(page, pageCount)
                                currentOnProgressChanged(loc.locations.totalProgression ?: 0f, loc)
                            }
                        }

                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            HorizontalPager(
                                state = pagerState,
                                reverseLayout = readingMode == CbzReadingMode.MANGA_RTL,
                                key = { it },
                                modifier = Modifier.fillMaxSize()
                            ) { pageIndex ->
                                val bmp by produceState<ImageBitmap?>(
                                    initialValue = synchronized(bitmapCache) { bitmapCache[pageIndex] },
                                    key1 = pageIndex
                                ) {
                                    if (value == null) {
                                        val loaded = withContext(Dispatchers.IO) {
                                            val bytes = archive.readPageBytes(pageIndex) ?: return@withContext null
                                            runCatching { SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
                                        }
                                        if (loaded != null) {
                                            synchronized(bitmapCache) { bitmapCache[pageIndex] = loaded }
                                            value = loaded
                                        }
                                    }
                                }
                                var zoom by remember(pageIndex) { mutableStateOf(1f) }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(pageIndex) {
                                            detectTapGestures(
                                                onTap = { currentOnTap() },
                                                onDoubleTap = { zoom = if (zoom > 1.1f) 1f else 2.5f }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (bmp == null) {
                                        CircularProgressIndicator()
                                    } else {
                                        Image(
                                            bitmap = bmp!!,
                                            contentDescription = "Page ${pageIndex + 1}",
                                            contentScale = if (imageFit == CbzImageFit.FIT_WIDTH) ContentScale.FillWidth else ContentScale.Fit,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer { scaleX = zoom; scaleY = zoom }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    CbzReadingMode.WEBTOON -> {
                        val listState = rememberLazyListState(
                            initialFirstVisibleItemIndex = currentPageIndex.coerceIn(0, pageCount - 1)
                        )

                        DisposableEffect(listState, pageCount) {
                            controller.goNext = {
                                if (listState.firstVisibleItemIndex < pageCount - 1) {
                                    scope.launch { listState.animateScrollToItem(listState.firstVisibleItemIndex + 1) }
                                } else {
                                    onGoToNextChapter?.invoke()
                                }
                            }
                            controller.goPrevious = {
                                if (listState.firstVisibleItemIndex > 0) {
                                    scope.launch { listState.animateScrollToItem(listState.firstVisibleItemIndex - 1) }
                                } else {
                                    onGoToPreviousChapter?.invoke()
                                }
                            }
                            controller.goTo = { locator ->
                                scope.launch { listState.scrollToItem(cbzInitialPageIndex(locator, pageCount)) }
                            }
                            controller.seekToProgression = { prog ->
                                val target = (prog * (pageCount - 1).coerceAtLeast(1)).roundToInt().coerceIn(0, pageCount - 1)
                                scope.launch { listState.scrollToItem(target) }
                            }
                            onDispose {
                                controller.goNext = null
                                controller.goPrevious = null
                                controller.goTo = null
                                controller.seekToProgression = null
                            }
                        }

                        LaunchedEffect(listState, pageCount) {
                            snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().collect { page ->
                                currentPageIndex = page
                                val loc = cbzPageLocator(page, pageCount)
                                currentOnProgressChanged(loc.locations.totalProgression ?: 0f, loc)
                            }
                        }

                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(0.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = { currentOnTap() })
                                }
                        ) {
                            items(count = pageCount, key = { it }) { pageIndex ->
                                val bmp by produceState<ImageBitmap?>(
                                    initialValue = synchronized(bitmapCache) { bitmapCache[pageIndex] },
                                    key1 = pageIndex
                                ) {
                                    if (value == null) {
                                        val loaded = withContext(Dispatchers.IO) {
                                            val bytes = archive.readPageBytes(pageIndex) ?: return@withContext null
                                            runCatching { SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
                                        }
                                        if (loaded != null) {
                                            synchronized(bitmapCache) { bitmapCache[pageIndex] = loaded }
                                            value = loaded
                                        }
                                    }
                                }
                                if (bmp == null) {
                                    Box(modifier = Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                } else {
                                    val aspect = (bmp!!.width.toFloat() / bmp!!.height.toFloat()).coerceAtLeast(0.05f)
                                    Image(
                                        bitmap = bmp!!,
                                        contentDescription = "Page ${pageIndex + 1}",
                                        contentScale = ContentScale.FillWidth,
                                        modifier = Modifier.fillMaxWidth().aspectRatio(aspect)
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
