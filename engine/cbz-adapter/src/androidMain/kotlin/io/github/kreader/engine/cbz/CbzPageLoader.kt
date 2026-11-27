package io.github.kreader.engine.cbz

import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

/**
 * Bounded LRU memory cache + adjacent page preloader (`current - 1`, `current + 1`, `current + 2`)
 * backed by an open [AndroidCbzArchive].
 */
class CbzPageLoader(
    private val archive: AndroidCbzArchive
) : Closeable {

    private val maxCacheKb: Int = (Runtime.getRuntime().maxMemory() / 1024L / 8L)
        .toInt()
        .coerceIn(24 * 1024, 96 * 1024)

    private val pageCache = object : LruCache<String, CbzDecodedPage>(maxCacheKb) {
        override fun sizeOf(key: String, value: CbzDecodedPage): Int {
            return (value.approximateByteCount / 1024).coerceAtLeast(1)
        }
    }

    private val inFlightLoads = ConcurrentHashMap<String, Deferred<CbzDecodedPage?>>()
    private val preloadSemaphore = Semaphore(2)
    private var preloadJob: Job? = null

    fun getCached(pageIndex: Int, isWebtoon: Boolean, reqWidth: Int, reqHeight: Int): CbzDecodedPage? {
        val key = cacheKey(pageIndex, isWebtoon, reqWidth, reqHeight)
        val cached = pageCache.get(key)
        if (cached != null && cached.slices.none { it.isRecycled }) {
            return cached
        }
        return null
    }

    suspend fun loadPage(
        pageIndex: Int,
        isWebtoon: Boolean,
        reqWidth: Int,
        reqHeight: Int
    ): CbzDecodedPage? {
        if (pageIndex !in 0 until archive.pageCount) return null
        val key = cacheKey(pageIndex, isWebtoon, reqWidth, reqHeight)

        getCached(pageIndex, isWebtoon, reqWidth, reqHeight)?.let { return it }

        return coroutineScope {
            val existing = inFlightLoads[key]
            if (existing != null && existing.isActive) {
                return@coroutineScope existing.await()
            }

            val deferred = async(Dispatchers.IO) {
                try {
                    val decoded = archive.decodePage(
                        pageIndex = pageIndex,
                        reqWidth = reqWidth,
                        reqHeight = reqHeight,
                        isWebtoon = isWebtoon
                    )
                    if (decoded != null) {
                        pageCache.put(key, decoded)
                    }
                    decoded
                } finally {
                    inFlightLoads.remove(key)
                }
            }
            inFlightLoads[key] = deferred
            deferred.await()
        }
    }

    fun preloadAround(
        currentPage: Int,
        isWebtoon: Boolean,
        reqWidth: Int,
        reqHeight: Int,
        scope: CoroutineScope
    ) {
        val total = archive.pageCount
        if (total <= 1) return

        val targets = listOf(currentPage + 1, currentPage + 2, currentPage - 1)
            .filter { it in 0 until total }
            .distinct()

        preloadJob?.cancel()
        preloadJob = scope.launch(Dispatchers.IO) {
            for (target in targets) {
                if (getCached(target, isWebtoon, reqWidth, reqHeight) != null) continue
                preloadSemaphore.withPermit {
                    runCatching {
                        loadPage(target, isWebtoon, reqWidth, reqHeight)
                    }
                }
            }
        }
    }

    override fun close() {
        preloadJob?.cancel()
        inFlightLoads.values.forEach { it.cancel() }
        inFlightLoads.clear()
        pageCache.evictAll()
        archive.close()
    }

    private fun cacheKey(pageIndex: Int, isWebtoon: Boolean, reqWidth: Int, reqHeight: Int): String {
        val wBucket = ((reqWidth.coerceIn(720, 2160) + 127) / 128) * 128
        val hBucket = if (isWebtoon) 0 else ((reqHeight.coerceIn(1080, 3840) + 127) / 128) * 128
        return "$pageIndex#$isWebtoon#$wBucket#$hBucket"
    }
}
