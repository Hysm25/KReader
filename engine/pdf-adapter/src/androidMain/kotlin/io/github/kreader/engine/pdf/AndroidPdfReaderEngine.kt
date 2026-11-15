package io.github.kreader.engine.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import io.github.kreader.core.engine.ReaderEngine
import io.github.kreader.core.engine.ReaderEngineEvent
import io.github.kreader.core.engine.ReaderEngineState
import io.github.kreader.core.model.Book
import io.github.kreader.core.model.BookSource
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ReaderTypography
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

actual class PdfReaderEngine actual constructor() : ReaderEngine {

    private val _state = MutableStateFlow<ReaderEngineState>(ReaderEngineState.Idle)
    override val state: StateFlow<ReaderEngineState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ReaderEngineEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<ReaderEngineEvent> = _events.asSharedFlow()

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val renderMutex = Mutex()

    private var fileDescriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var currentPage: Int = 0
    private var totalPagesCount: Int = 0

    actual val pageCount: Int
        get() = totalPagesCount

    private val cacheSize = ((Runtime.getRuntime().maxMemory() / 1024) / 8).toInt().coerceAtLeast(1024)
    private val pageCache = object : LruCache<Int, Bitmap>(cacheSize) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount / 1024
    }

    override suspend fun open(book: Book, initialLocator: Locator?): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            _state.value = ReaderEngineState.Loading("Opening ${book.title} with Android PdfRenderer...")

            renderMutex.withLock {
                closeInternal()

                val file = when (val src = book.source) {
                    is BookSource.FilePath -> File(src.path)
                    else -> File(src.identifier)
                }

                if (!file.exists() || file.length() == 0L) {
                    throw IllegalStateException("PDF file not found at ${file.absolutePath}")
                }

                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                fileDescriptor = pfd
                val pdfRenderer = PdfRenderer(pfd)
                renderer = pdfRenderer
                totalPagesCount = pdfRenderer.pageCount
            }

            // Note: initialLocator position is 1-based when persisted (pageIndex + 1)
            val rawPos = initialLocator?.locations?.position
            val targetPage = if (rawPos != null) {
                (rawPos - 1).coerceIn(0, (totalPagesCount - 1).coerceAtLeast(0))
            } else {
                0
            }
            currentPage = targetPage

            val position1Based = currentPage + 1
            val progression = if (totalPagesCount > 0) currentPage.toFloat() / totalPagesCount else 0f
            val locator = Locator(
                href = "page-$position1Based",
                type = book.format.mimeType,
                title = "Page $position1Based",
                locations = Locator.Locations(
                    position = position1Based,
                    progression = progression,
                    totalProgression = progression
                )
            )

            _state.value = ReaderEngineState.Ready(
                currentLocator = locator,
                totalPages = totalPagesCount,
                currentPageIndex = currentPage,
                totalProgression = progression,
                canGoPrevious = currentPage > 0,
                canGoNext = currentPage < totalPagesCount - 1
            )
        }
    }

    suspend fun getPageBitmap(pageIndex: Int, targetWidth: Int, targetHeight: Int): Bitmap? {
        val cached = synchronized(pageCache) { pageCache.get(pageIndex) }
        if (cached != null && !cached.isRecycled) {
            prefetchNeighbors(pageIndex, targetWidth, targetHeight)
            return cached
        }

        val rendered = renderPageInternal(pageIndex, targetWidth, targetHeight) ?: return null
        synchronized(pageCache) {
            pageCache.put(pageIndex, rendered)
        }
        prefetchNeighbors(pageIndex, targetWidth, targetHeight)
        return rendered
    }

    suspend fun getPageAspectRatio(pageIndex: Int): Float = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            val r = renderer ?: return@withContext 1.414f
            if (pageIndex !in 0 until totalPagesCount) return@withContext 1.414f
            try {
                r.openPage(pageIndex).use { page ->
                    page.height.toFloat() / page.width.toFloat()
                }
            } catch (_: Exception) {
                1.414f
            }
        }
    }

    private suspend fun renderPageInternal(pageIndex: Int, targetWidth: Int, targetHeight: Int): Bitmap? = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            val r = renderer ?: return@withContext null
            if (pageIndex !in 0 until totalPagesCount) return@withContext null
            try {
                r.openPage(pageIndex).use { page ->
                    val ratio = page.height.toFloat() / page.width.toFloat()
                    val w = targetWidth.coerceAtLeast(100)
                    val h = (w * ratio).toInt().coerceAtLeast(100)
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun prefetchNeighbors(pageIndex: Int, targetWidth: Int, targetHeight: Int) {
        engineScope.launch {
            for (neighbor in listOf(pageIndex - 1, pageIndex + 1)) {
                if (neighbor in 0 until totalPagesCount) {
                    val isCached = synchronized(pageCache) { pageCache.get(neighbor) != null }
                    if (!isCached) {
                        val bmp = renderPageInternal(neighbor, targetWidth, targetHeight)
                        if (bmp != null) {
                            synchronized(pageCache) { pageCache.put(neighbor, bmp) }
                        }
                    }
                }
            }
        }
    }

    private fun closeInternal() {
        runCatching { renderer?.close() }
        renderer = null
        runCatching { fileDescriptor?.close() }
        fileDescriptor = null
        synchronized(pageCache) {
            pageCache.evictAll()
        }
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            closeInternal()
            _state.value = ReaderEngineState.Idle
        }
    }

    override suspend fun goTo(locator: Locator): Result<Unit> {
        val rawPos = locator.locations.position
        val page = if (rawPos != null) {
            (rawPos - 1).coerceIn(0, (totalPagesCount - 1).coerceAtLeast(0))
        } else {
            0
        }

        if (page in 0 until totalPagesCount) {
            currentPage = page
            val pos1Based = page + 1
            val prog = if (totalPagesCount > 0) page.toFloat() / totalPagesCount else 0f
            val updated = locator.copy(
                href = "page-$pos1Based",
                title = "Page $pos1Based",
                locations = locator.locations.copy(
                    position = pos1Based,
                    progression = prog,
                    totalProgression = prog
                )
            )
            _state.value = ReaderEngineState.Ready(
                currentLocator = updated,
                totalPages = totalPagesCount,
                currentPageIndex = currentPage,
                totalProgression = prog,
                canGoPrevious = currentPage > 0,
                canGoNext = currentPage < totalPagesCount - 1
            )
            _events.emit(ReaderEngineEvent.PageChanged(updated))
            return Result.success(Unit)
        }
        return Result.failure(IndexOutOfBoundsException("Page $page out of range [0, $totalPagesCount)"))
    }

    override suspend fun next(): Result<Unit> {
        return if (currentPage < totalPagesCount - 1) {
            val nextPage = currentPage + 1
            val pos1Based = nextPage + 1
            val nextLocator = Locator(
                href = "page-$pos1Based",
                type = "application/pdf",
                title = "Page $pos1Based",
                locations = Locator.Locations(
                    position = pos1Based,
                    progression = if (totalPagesCount > 0) nextPage.toFloat() / totalPagesCount else 0f
                )
            )
            goTo(nextLocator)
        } else {
            Result.success(Unit)
        }
    }

    override suspend fun previous(): Result<Unit> {
        return if (currentPage > 0) {
            val prevPage = currentPage - 1
            val pos1Based = prevPage + 1
            val prevLocator = Locator(
                href = "page-$pos1Based",
                type = "application/pdf",
                title = "Page $pos1Based",
                locations = Locator.Locations(
                    position = pos1Based,
                    progression = if (totalPagesCount > 0) prevPage.toFloat() / totalPagesCount else 0f
                )
            )
            goTo(prevLocator)
        } else {
            Result.success(Unit)
        }
    }

    override suspend fun applyTheme(theme: ReaderTheme) {}
    override suspend fun applyTypography(typography: ReaderTypography) {}
    override suspend fun search(query: String): List<Locator> = emptyList()
}
