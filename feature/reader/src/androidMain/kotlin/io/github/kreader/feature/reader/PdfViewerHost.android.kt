package io.github.kreader.feature.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.kreader.core.model.Book
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ThemeMode
import io.github.kreader.engine.pdf.PdfReaderEngine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
actual fun PdfViewerHost(
    book: Book,
    initialPage: Int,
    theme: ReaderTheme,
    isScrollMode: Boolean,
    isRtl: Boolean,
    controller: PdfViewerController,
    onPageChanged: (pageIndex: Int, totalPages: Int, locator: Locator) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val currentOnPageChanged by rememberUpdatedState(onPageChanged)
    val currentOnTap by rememberUpdatedState(onTap)

    val engine = remember(book.id) { PdfReaderEngine() }
    var isEngineReady by remember(book.id) { mutableStateOf(false) }
    var errorMessage by remember(book.id) { mutableStateOf<String?>(null) }
    var totalPages by remember(book.id) { mutableStateOf(1) }

    LaunchedEffect(book.id) {
        isEngineReady = false
        errorMessage = null
        val initialLoc = Locator(
            href = "page-${initialPage + 1}",
            type = "application/pdf",
            title = "Page ${initialPage + 1}",
            locations = Locator.Locations(position = initialPage + 1)
        )
        engine.open(book, initialLoc).fold(
            onSuccess = {
                totalPages = engine.pageCount.coerceAtLeast(1)
                isEngineReady = true
            },
            onFailure = { err ->
                errorMessage = err.message ?: "Failed to open PDF document"
            }
        )
    }

    DisposableEffect(book.id) {
        onDispose {
            scope.launch {
                engine.close()
            }
            controller.goToPage = null
            controller.goNext = null
            controller.goPrevious = null
        }
    }

    // PDF Reading Theme Color Filter
    val colorFilter = remember(theme) {
        when (theme.mode) {
            ThemeMode.LIGHT -> null
            ThemeMode.SEPIA -> {
                ColorFilter.colorMatrix(
                    ColorMatrix(
                        floatArrayOf(
                            0.90f, 0.05f, 0.05f, 0f, 15f,
                            0.05f, 0.85f, 0.05f, 0f, 10f,
                            0.02f, 0.05f, 0.75f, 0f, 0f,
                            0f,    0f,    0f,    1f, 0f
                        )
                    )
                )
            }
            ThemeMode.DARK -> {
                if (theme.backgroundColorHex.equals("#000000", ignoreCase = true)) {
                    // AMOLED pure black inversion
                    ColorFilter.colorMatrix(
                        ColorMatrix(
                            floatArrayOf(
                                -1f,  0f,  0f, 0f, 255f,
                                 0f, -1f,  0f, 0f, 255f,
                                 0f,  0f, -1f, 0f, 255f,
                                 0f,  0f,  0f, 1f, 0f
                            )
                        )
                    )
                } else {
                    // Dark theme inversion
                    ColorFilter.colorMatrix(
                        ColorMatrix(
                            floatArrayOf(
                                -0.85f,  0f,     0f,    0f, 225f,
                                 0f,    -0.85f,  0f,    0f, 225f,
                                 0f,     0f,    -0.85f, 0f, 225f,
                                 0f,     0f,     0f,    1f, 0f
                            )
                        )
                    )
                }
            }
            ThemeMode.SYSTEM -> null
        }
    }

    when {
        errorMessage != null -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Error: $errorMessage",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        !isEngineReady -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        else -> {
            BoxWithConstraints(modifier = modifier.fillMaxSize()) {
                val screenWidthPx = with(density) { maxWidth.roundToPx() }
                val validInitialPage = initialPage.coerceIn(0, totalPages - 1)

                if (isScrollMode) {
                    // Vertical Continuous Scrolling
                    val listState = rememberLazyListState(initialFirstVisibleItemIndex = validInitialPage)

                    DisposableEffect(listState) {
                        controller.goToPage = { page ->
                            scope.launch {
                                listState.scrollToItem(page.coerceIn(0, totalPages - 1))
                            }
                        }
                        controller.goNext = {
                            scope.launch {
                                val next = (listState.firstVisibleItemIndex + 1).coerceAtMost(totalPages - 1)
                                listState.animateScrollToItem(next)
                            }
                        }
                        controller.goPrevious = {
                            scope.launch {
                                val prev = (listState.firstVisibleItemIndex - 1).coerceAtLeast(0)
                                listState.animateScrollToItem(prev)
                            }
                        }
                        onDispose {}
                    }

                    LaunchedEffect(listState) {
                        snapshotFlow { listState.firstVisibleItemIndex }
                            .distinctUntilChanged()
                            .collect { pageIdx ->
                                val pos1Based = pageIdx + 1
                                val prog = if (totalPages > 0) pageIdx.toFloat() / totalPages else 0f
                                val locator = Locator(
                                    href = "page-$pos1Based",
                                    type = "application/pdf",
                                    title = "Page $pos1Based",
                                    locations = Locator.Locations(
                                        position = pos1Based,
                                        progression = prog,
                                        totalProgression = prog
                                    )
                                )
                                currentOnPageChanged(pageIdx, totalPages, locator)
                            }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(count = totalPages, key = { it }) { pageIdx ->
                            PdfPageItem(
                                pageIndex = pageIdx,
                                engine = engine,
                                colorFilter = colorFilter,
                                screenWidthPx = screenWidthPx,
                                isRtl = isRtl,
                                onNext = {
                                    scope.launch {
                                        val next = (listState.firstVisibleItemIndex + 1).coerceAtMost(totalPages - 1)
                                        listState.animateScrollToItem(next)
                                    }
                                },
                                onPrev = {
                                    scope.launch {
                                        val prev = (listState.firstVisibleItemIndex - 1).coerceAtLeast(0)
                                        listState.animateScrollToItem(prev)
                                    }
                                },
                                onTap = currentOnTap,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    // Horizontal Paginated Mode
                    val pagerState = rememberPagerState(
                        initialPage = validInitialPage,
                        pageCount = { totalPages }
                    )

                    DisposableEffect(pagerState) {
                        controller.goToPage = { page ->
                            scope.launch {
                                pagerState.scrollToPage(page.coerceIn(0, totalPages - 1))
                            }
                        }
                        controller.goNext = {
                            scope.launch {
                                val next = (pagerState.currentPage + 1).coerceAtMost(totalPages - 1)
                                pagerState.animateScrollToPage(next)
                            }
                        }
                        controller.goPrevious = {
                            scope.launch {
                                val prev = (pagerState.currentPage - 1).coerceAtLeast(0)
                                pagerState.animateScrollToPage(prev)
                            }
                        }
                        onDispose {}
                    }

                    LaunchedEffect(pagerState) {
                        snapshotFlow { pagerState.currentPage }
                            .distinctUntilChanged()
                            .collect { pageIdx ->
                                val pos1Based = pageIdx + 1
                                val prog = if (totalPages > 0) pageIdx.toFloat() / totalPages else 0f
                                val locator = Locator(
                                    href = "page-$pos1Based",
                                    type = "application/pdf",
                                    title = "Page $pos1Based",
                                    locations = Locator.Locations(
                                        position = pos1Based,
                                        progression = prog,
                                        totalProgression = prog
                                    )
                                )
                                currentOnPageChanged(pageIdx, totalPages, locator)
                            }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { pageIdx ->
                        PdfPageItem(
                            pageIndex = pageIdx,
                            engine = engine,
                            colorFilter = colorFilter,
                            screenWidthPx = screenWidthPx,
                            isRtl = isRtl,
                            onNext = {
                                scope.launch {
                                    val next = (pagerState.currentPage + 1).coerceAtMost(totalPages - 1)
                                    pagerState.animateScrollToPage(next)
                                }
                            },
                            onPrev = {
                                scope.launch {
                                    val prev = (pagerState.currentPage - 1).coerceAtLeast(0)
                                    pagerState.animateScrollToPage(prev)
                                }
                            },
                            onTap = currentOnTap,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPageItem(
    pageIndex: Int,
    engine: PdfReaderEngine,
    colorFilter: ColorFilter?,
    screenWidthPx: Int,
    isRtl: Boolean,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(pageIndex) { mutableStateOf(true) }

    LaunchedEffect(pageIndex, screenWidthPx) {
        isLoading = true
        bitmap = engine.getPageBitmap(pageIndex, screenWidthPx, (screenWidthPx * 1.414f).toInt())
        isLoading = false
    }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 4f)
        scale = newScale
        if (newScale > 1f) {
            offset += panChange
        } else {
            offset = Offset.Zero
        }
    }

    Box(
        modifier = modifier
            .pointerInput(pageIndex, isRtl) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                    onTap = { tapOffset ->
                        val width = size.width
                        if (width > 0 && scale <= 1.05f) {
                            val fraction = tapOffset.x / width
                            if (fraction < 0.25f) {
                                if (isRtl) onNext() else onPrev()
                            } else if (fraction > 0.75f) {
                                if (isRtl) onPrev() else onNext()
                            } else {
                                onTap()
                            }
                        } else {
                            onTap()
                        }
                    }
                )
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            )
            .transformable(state = transformState),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "PDF Page ${pageIndex + 1}",
                colorFilter = colorFilter,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        } else if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f / 1.414f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
