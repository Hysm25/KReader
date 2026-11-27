package io.github.kreader.feature.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.kreader.core.engine.TocItem
import io.github.kreader.core.model.Book
import io.github.kreader.core.model.CbzImageFit
import io.github.kreader.core.model.CbzReadingMode
import io.github.kreader.core.model.Locator
import io.github.kreader.engine.cbz.AndroidCbzArchive
import io.github.kreader.engine.cbz.CbzDecodedPage
import io.github.kreader.engine.cbz.CbzPageLoader
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private sealed interface CbzOpenState {
    data object Loading : CbzOpenState
    data class Ready(val loader: CbzPageLoader, val pageCount: Int) : CbzOpenState
    data class Error(val message: String) : CbzOpenState
}

@Composable
actual fun CbzNavigatorHost(
    book: Book,
    initialLocator: Locator?,
    readingMode: CbzReadingMode,
    imageFit: CbzImageFit,
    controller: EpubNavigatorController,
    onProgressChanged: (progression: Float, locator: Locator) -> Unit,
    onTableOfContentsLoaded: (List<TocItem>) -> Unit,
    onPageCountLoaded: (Int) -> Unit,
    onCbzMetadataLoaded: (pageCount: Int, isMangaRtl: Boolean?) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    val currentOnProgressChanged by rememberUpdatedState(onProgressChanged)
    val currentOnTap by rememberUpdatedState(onTap)

    var openState by remember(book.id, book.source) {
        mutableStateOf<CbzOpenState>(CbzOpenState.Loading)
    }

    LaunchedEffect(book.id, book.source) {
        openState = CbzOpenState.Loading
        val result = AndroidCbzArchive.open(context, book.source)
        result.fold(
            onSuccess = { archive ->
                val count = archive.pageCount
                if (count <= 0) {
                    archive.close()
                    openState = CbzOpenState.Error("No readable images found in CBZ archive.")
                } else {
                    val loader = CbzPageLoader(archive)
                    onPageCountLoaded(count)
                    onCbzMetadataLoaded(count, archive.metadata.isMangaRtl)
                    val toc = List(count) { idx ->
                        TocItem(
                            title = "Page ${idx + 1}",
                            locator = cbzPageLocator(idx, count)
                        )
                    }
                    onTableOfContentsLoaded(toc)
                    openState = CbzOpenState.Ready(loader, count)
                }
            },
            onFailure = { err ->
                openState = CbzOpenState.Error(err.message ?: "Failed to open CBZ archive")
            }
        )
    }

    DisposableEffect(openState) {
        val ready = openState as? CbzOpenState.Ready
        onDispose {
            ready?.loader?.close()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val state = openState) {
            CbzOpenState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            is CbzOpenState.Error -> {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                )
            }

            is CbzOpenState.Ready -> {
                val pageCount = state.pageCount
                val pageLoader = state.loader
                var currentPageIndex by remember(book.id, pageCount) {
                    mutableIntStateOf(cbzInitialPageIndex(initialLocator, pageCount))
                }

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val density = LocalDensity.current
                    val viewportWidthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(720) }
                    val viewportHeightPx = with(density) { maxHeight.roundToPx().coerceAtLeast(1080) }

                    when (readingMode) {
                        CbzReadingMode.MANGA_RTL,
                        CbzReadingMode.COMIC_LTR -> {
                            val isRtl = readingMode == CbzReadingMode.MANGA_RTL
                            val pagerState = rememberPagerState(
                                initialPage = currentPageIndex.coerceIn(0, pageCount - 1),
                                pageCount = { pageCount }
                            )

                            DisposableEffect(pagerState, pageCount) {
                                controller.goNext = {
                                    val target = (pagerState.currentPage + 1).coerceAtMost(pageCount - 1)
                                    if (target != pagerState.currentPage) {
                                        scope.launch { pagerState.animateScrollToPage(target) }
                                    }
                                }
                                controller.goPrevious = {
                                    val target = (pagerState.currentPage - 1).coerceAtLeast(0)
                                    if (target != pagerState.currentPage) {
                                        scope.launch { pagerState.animateScrollToPage(target) }
                                    }
                                }
                                controller.goTo = { locator ->
                                    val target = cbzInitialPageIndex(locator, pageCount)
                                    scope.launch { pagerState.scrollToPage(target) }
                                }
                                controller.seekToProgression = { targetProg ->
                                    val target = (targetProg * (pageCount - 1).coerceAtLeast(1))
                                        .roundToInt()
                                        .coerceIn(0, pageCount - 1)
                                    scope.launch { pagerState.scrollToPage(target) }
                                }
                                onDispose {
                                    controller.goNext = null
                                    controller.goPrevious = null
                                    controller.goTo = null
                                    controller.seekToProgression = null
                                }
                            }

                            LaunchedEffect(pagerState, pageCount, viewportWidthPx, viewportHeightPx) {
                                snapshotFlow { pagerState.currentPage }
                                    .distinctUntilChanged()
                                    .collect { page ->
                                        currentPageIndex = page
                                        val locator = cbzPageLocator(page, pageCount)
                                        currentOnProgressChanged(locator.locations.totalProgression ?: 0f, locator)
                                        pageLoader.preloadAround(
                                            currentPage = page,
                                            isWebtoon = false,
                                            reqWidth = viewportWidthPx,
                                            reqHeight = viewportHeightPx,
                                            scope = scope
                                        )
                                    }
                            }

                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                HorizontalPager(
                                    state = pagerState,
                                    reverseLayout = isRtl,
                                    key = { it },
                                    modifier = Modifier.fillMaxSize()
                                ) { pageIndex ->
                                    val decodedPage by produceState<CbzDecodedPage?>(
                                        initialValue = pageLoader.getCached(
                                            pageIndex = pageIndex,
                                            isWebtoon = false,
                                            reqWidth = viewportWidthPx,
                                            reqHeight = viewportHeightPx
                                        ),
                                        key1 = pageIndex,
                                        key2 = viewportWidthPx
                                    ) {
                                        if (value == null) {
                                            value = pageLoader.loadPage(
                                                pageIndex = pageIndex,
                                                isWebtoon = false,
                                                reqWidth = viewportWidthPx,
                                                reqHeight = viewportHeightPx
                                            )
                                        }
                                    }

                                    CbzPagedImageViewport(
                                        decodedPage = decodedPage,
                                        imageFit = imageFit,
                                        isSettledCurrentPage = pagerState.settledPage == pageIndex,
                                        viewportSize = IntSize(viewportWidthPx, viewportHeightPx),
                                        onTap = { currentOnTap() }
                                    )
                                }
                            }
                        }

                        CbzReadingMode.WEBTOON -> {
                            val listState = rememberLazyListState(
                                initialFirstVisibleItemIndex = currentPageIndex.coerceIn(0, pageCount - 1)
                            )

                            DisposableEffect(listState, pageCount) {
                                controller.goNext = {
                                    val target = (listState.firstVisibleItemIndex + 1).coerceAtMost(pageCount - 1)
                                    scope.launch { listState.animateScrollToItem(target) }
                                }
                                controller.goPrevious = {
                                    val target = (listState.firstVisibleItemIndex - 1).coerceAtLeast(0)
                                    scope.launch { listState.animateScrollToItem(target) }
                                }
                                controller.goTo = { locator ->
                                    val target = cbzInitialPageIndex(locator, pageCount)
                                    scope.launch { listState.scrollToItem(target) }
                                }
                                controller.seekToProgression = { targetProg ->
                                    val target = (targetProg * (pageCount - 1).coerceAtLeast(1))
                                        .roundToInt()
                                        .coerceIn(0, pageCount - 1)
                                    scope.launch { listState.scrollToItem(target) }
                                }
                                onDispose {
                                    controller.goNext = null
                                    controller.goPrevious = null
                                    controller.goTo = null
                                    controller.seekToProgression = null
                                }
                            }

                            LaunchedEffect(listState, pageCount, viewportWidthPx, viewportHeightPx) {
                                snapshotFlow { listState.firstVisibleItemIndex }
                                    .distinctUntilChanged()
                                    .collect { firstVisible ->
                                        currentPageIndex = firstVisible
                                        val locator = cbzPageLocator(firstVisible, pageCount)
                                        currentOnProgressChanged(locator.locations.totalProgression ?: 0f, locator)
                                        pageLoader.preloadAround(
                                            currentPage = firstVisible,
                                            isWebtoon = true,
                                            reqWidth = viewportWidthPx,
                                            reqHeight = viewportHeightPx,
                                            scope = scope
                                        )
                                    }
                            }

                            CbzWebtoonViewport(
                                pageCount = pageCount,
                                listState = listState,
                                pageLoader = pageLoader,
                                viewportWidthPx = viewportWidthPx,
                                viewportHeightPx = viewportHeightPx,
                                onTap = { currentOnTap() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CbzPagedImageViewport(
    decodedPage: CbzDecodedPage?,
    imageFit: CbzImageFit,
    isSettledCurrentPage: Boolean,
    viewportSize: IntSize,
    onTap: () -> Unit
) {
    val scaleAnim = remember { Animatable(1f) }
    val offsetAnim = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isSettledCurrentPage) {
        if (!isSettledCurrentPage && scaleAnim.value > 1f) {
            scaleAnim.snapTo(1f)
            offsetAnim.snapTo(Offset.Zero)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(decodedPage?.pageIndex) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tapOffset ->
                        scope.launch {
                            if (scaleAnim.value > 1.05f) {
                                launch { scaleAnim.animateTo(1f, tween(220)) }
                                launch { offsetAnim.animateTo(Offset.Zero, tween(220)) }
                            } else {
                                val targetScale = 2.5f
                                val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
                                val rawOffset = (center - tapOffset) * (targetScale - 1f)
                                val clamped = clampOffset(rawOffset, targetScale, viewportSize)
                                launch { scaleAnim.animateTo(targetScale, tween(220)) }
                                launch { offsetAnim.animateTo(clamped, tween(220)) }
                            }
                        }
                    }
                )
            }
            .pointerInput(decodedPage?.pageIndex, viewportSize) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var panAccum = Offset.Zero
                    var pastSlop = false

                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount == 0) break

                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()

                        if (pressedCount >= 2) {
                            val newScale = (scaleAnim.value * zoomChange).coerceIn(1f, 4f)
                            val centroid = event.calculateCentroid(useCurrent = false)
                            val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
                            val scaleFactor = if (scaleAnim.value > 0f) newScale / scaleAnim.value else 1f
                            val nextOffset = if (newScale <= 1.01f) {
                                Offset.Zero
                            } else {
                                val adjusted = (offsetAnim.value + panChange) * scaleFactor +
                                    (centroid - center) * (1f - scaleFactor)
                                clampOffset(adjusted, newScale, viewportSize)
                            }
                            scope.launch {
                                scaleAnim.snapTo(newScale)
                                offsetAnim.snapTo(nextOffset)
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (scaleAnim.value > 1.02f) {
                            panAccum += panChange
                            if (!pastSlop && panAccum.getDistance() > touchSlop) {
                                pastSlop = true
                            }
                            if (pastSlop) {
                                val nextOffset = clampOffset(
                                    offsetAnim.value + panChange,
                                    scaleAnim.value,
                                    viewportSize
                                )
                                scope.launch {
                                    offsetAnim.snapTo(nextOffset)
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                        // At 1x zoom with 1 pointer: do NOT consume events so HorizontalPager handles swipes cleanly
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (decodedPage == null) {
            CircularProgressIndicator()
        } else {
            val bitmapModifier = Modifier
                .graphicsLayer {
                    scaleX = scaleAnim.value
                    scaleY = scaleAnim.value
                    translationX = offsetAnim.value.x
                    translationY = offsetAnim.value.y
                }

            val firstBitmap = decodedPage.slices.firstOrNull()
            if (firstBitmap != null) {
                val imageBitmap = remember(firstBitmap) { firstBitmap.asImageBitmap() }
                if (imageFit == CbzImageFit.FIT_WIDTH) {
                    val scrollState = rememberScrollState()
                    val aspect = (firstBitmap.width.toFloat() / firstBitmap.height.toFloat()).coerceAtLeast(0.05f)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState, enabled = scaleAnim.value <= 1.02f),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = "Page ${decodedPage.pageIndex + 1}",
                            contentScale = ContentScale.FillWidth,
                            modifier = bitmapModifier
                                .fillMaxWidth()
                                .aspectRatio(aspect)
                        )
                    }
                } else {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = "Page ${decodedPage.pageIndex + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = bitmapModifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun CbzWebtoonViewport(
    pageCount: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    pageLoader: CbzPageLoader,
    viewportWidthPx: Int,
    viewportHeightPx: Int,
    onTap: () -> Unit
) {
    val scaleAnim = remember { Animatable(1f) }
    val offsetAnim = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    val viewportSize = IntSize(viewportWidthPx, viewportHeightPx)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(pageCount) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tapOffset ->
                        scope.launch {
                            if (scaleAnim.value > 1.05f) {
                                launch { scaleAnim.animateTo(1f, tween(220)) }
                                launch { offsetAnim.animateTo(Offset.Zero, tween(220)) }
                            } else {
                                val targetScale = 2.5f
                                val centerX = viewportSize.width / 2f
                                val rawX = (centerX - tapOffset.x) * (targetScale - 1f)
                                val clampedX = clampHorizontalOffset(rawX, targetScale, viewportSize.width)
                                launch { scaleAnim.animateTo(targetScale, tween(220)) }
                                launch { offsetAnim.animateTo(Offset(clampedX, 0f), tween(220)) }
                            }
                        }
                    }
                )
            }
            .pointerInput(pageCount, viewportSize) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var panAccum = Offset.Zero
                    var pastSlop = false

                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount == 0) break

                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()

                        if (pressedCount >= 2) {
                            val newScale = (scaleAnim.value * zoomChange).coerceIn(1f, 4f)
                            val centroid = event.calculateCentroid(useCurrent = false)
                            val centerX = viewportSize.width / 2f
                            val scaleFactor = if (scaleAnim.value > 0f) newScale / scaleAnim.value else 1f
                            val nextX = if (newScale <= 1.01f) {
                                0f
                            } else {
                                val adjustedX = (offsetAnim.value.x + panChange.x) * scaleFactor +
                                    (centroid.x - centerX) * (1f - scaleFactor)
                                clampHorizontalOffset(adjustedX, newScale, viewportSize.width)
                            }
                            scope.launch {
                                scaleAnim.snapTo(newScale)
                                offsetAnim.snapTo(Offset(nextX, 0f))
                                if (abs(panChange.y) > 0.1f) {
                                    listState.dispatchRawDelta(-panChange.y / newScale)
                                }
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (scaleAnim.value > 1.02f) {
                            panAccum += panChange
                            if (!pastSlop && panAccum.getDistance() > touchSlop) {
                                pastSlop = true
                            }
                            if (pastSlop) {
                                val nextX = clampHorizontalOffset(
                                    offsetAnim.value.x + panChange.x,
                                    scaleAnim.value,
                                    viewportSize.width
                                )
                                scope.launch {
                                    offsetAnim.snapTo(Offset(nextX, 0f))
                                    if (abs(panChange.y) > 0.1f) {
                                        listState.dispatchRawDelta(-panChange.y / scaleAnim.value)
                                    }
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                        // At 1x zoom with 1 pointer: LazyColumn receives unmodified touch events
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(0.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scaleAnim.value
                    scaleY = scaleAnim.value
                    translationX = offsetAnim.value.x
                }
        ) {
            items(
                count = pageCount,
                key = { it }
            ) { pageIndex ->
                val decodedPage by produceState<CbzDecodedPage?>(
                    initialValue = pageLoader.getCached(
                        pageIndex = pageIndex,
                        isWebtoon = true,
                        reqWidth = viewportWidthPx,
                        reqHeight = viewportHeightPx
                    ),
                    key1 = pageIndex,
                    key2 = viewportWidthPx
                ) {
                    if (value == null) {
                        value = pageLoader.loadPage(
                            pageIndex = pageIndex,
                            isWebtoon = true,
                            reqWidth = viewportWidthPx,
                            reqHeight = viewportHeightPx
                        )
                    }
                }

                if (decodedPage == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    val page = decodedPage!!
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        for (slice in page.slices) {
                            val imageBitmap = remember(slice) { slice.asImageBitmap() }
                            val aspect = (slice.width.toFloat() / slice.height.toFloat()).coerceAtLeast(0.05f)
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = "Page ${pageIndex + 1}",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(aspect)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun clampOffset(offset: Offset, scale: Float, viewportSize: IntSize): Offset {
    val maxX = ((scale - 1f) * viewportSize.width / 2f).coerceAtLeast(0f)
    val maxY = ((scale - 1f) * viewportSize.height / 2f).coerceAtLeast(0f)
    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY)
    )
}

private fun clampHorizontalOffset(offsetX: Float, scale: Float, viewportWidth: Int): Float {
    val maxX = ((scale - 1f) * viewportWidth / 2f).coerceAtLeast(0f)
    return offsetX.coerceIn(-maxX, maxX)
}
