package io.github.kreader.engine.cbz

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import io.github.kreader.core.model.BookSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Decoded CBZ page composed of one bitmap (standard pages) or vertically stacked tiles
 * (tall Webtoon strips exceeding safe GPU texture bounds).
 */
data class CbzDecodedPage(
    val pageIndex: Int,
    val slices: List<Bitmap>,
    val totalWidth: Int,
    val totalHeight: Int
) {
    val approximateByteCount: Int
        get() = slices.sumOf { bmp ->
            if (bmp.isRecycled) 0 else bmp.allocationByteCount
        }
}

/**
 * Native `ZipFile`-backed CBZ archive reader providing O(1) random page access, natural sorting,
 * `ComicInfo.xml` parsing, cover extraction, and OOM-safe bitmap decoding.
 */
class AndroidCbzArchive private constructor(
    private val zipFile: ZipFile,
    val metadata: CbzArchiveMetadata,
    val pages: List<CbzPageEntry>
) : Closeable {

    private val closed = AtomicBoolean(false)

    val pageCount: Int get() = pages.size

    suspend fun extractCoverBytes(maxDimension: Int = 540): ByteArray? = withContext(Dispatchers.IO) {
        if (closed.get()) return@withContext null
        val coverName = metadata.coverEntryName ?: return@withContext null
        val entry = zipFile.getEntry(coverName) ?: return@withContext null
        decodeCoverBytes(entry, maxDimension)
    }

    suspend fun decodePage(
        pageIndex: Int,
        reqWidth: Int,
        reqHeight: Int,
        isWebtoon: Boolean
    ): CbzDecodedPage? = withContext(Dispatchers.IO) {
        if (closed.get() || pageIndex !in pages.indices) return@withContext null
        val entry = zipFile.getEntry(pages[pageIndex].entryName) ?: return@withContext null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            zipFile.getInputStream(entry).use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
        }
        val rawWidth = bounds.outWidth
        val rawHeight = bounds.outHeight
        if (rawWidth <= 0 || rawHeight <= 0) return@withContext null

        val safeTargetWidth = reqWidth.coerceIn(720, 2160)
        val safeTargetHeight = reqHeight.coerceIn(1080, 3840)
        val preferredConfig = if (bounds.outMimeType.equals("image/jpeg", ignoreCase = true)) {
            Bitmap.Config.RGB_565
        } else {
            Bitmap.Config.ARGB_8888
        }
        val bytesPerPixel = if (preferredConfig == Bitmap.Config.RGB_565) 2 else 4

        var sampleSize = computeInitialSampleSize(
            rawWidth = rawWidth,
            rawHeight = rawHeight,
            targetWidth = safeTargetWidth,
            targetHeight = safeTargetHeight,
            isWebtoon = isWebtoon,
            bytesPerPixel = bytesPerPixel
        )

        val estimatedSampledHeight = rawHeight / sampleSize
        if (isWebtoon && estimatedSampledHeight > MAX_TEXTURE_DIMENSION) {
            val tiled = decodeWebtoonSlices(
                entry = entry,
                rawWidth = rawWidth,
                rawHeight = rawHeight,
                initialSampleSize = sampleSize,
                preferredConfig = preferredConfig,
                pageIndex = pageIndex
            )
            if (tiled != null) return@withContext tiled
        }

        // Single-bitmap decode (also fallback for formats unsupported by BitmapRegionDecoder such as GIF/BMP)
        while ((rawHeight / sampleSize) > HARDWARE_SAFE_LIMIT ||
            (rawWidth / sampleSize) > HARDWARE_SAFE_LIMIT ||
            estimatedBytes(rawWidth, rawHeight, sampleSize, bytesPerPixel) > MAX_SINGLE_BITMAP_BYTES
        ) {
            sampleSize *= 2
        }

        var attempt = 0
        while (attempt < 3) {
            try {
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = preferredConfig
                }
                val bitmap = zipFile.getInputStream(entry).use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                } ?: return@withContext null
                return@withContext CbzDecodedPage(
                    pageIndex = pageIndex,
                    slices = listOf(bitmap),
                    totalWidth = bitmap.width,
                    totalHeight = bitmap.height
                )
            } catch (_: OutOfMemoryError) {
                sampleSize *= 2
                attempt++
            }
        }
        null
    }

    private fun decodeWebtoonSlices(
        entry: ZipEntry,
        rawWidth: Int,
        rawHeight: Int,
        initialSampleSize: Int,
        preferredConfig: Bitmap.Config,
        pageIndex: Int
    ): CbzDecodedPage? {
        var sampleSize = initialSampleSize
        while ((rawWidth / sampleSize) > HARDWARE_SAFE_LIMIT) {
            sampleSize *= 2
        }

        val decoder = runCatching {
            zipFile.getInputStream(entry).use { stream ->
                @Suppress("DEPRECATION")
                BitmapRegionDecoder.newInstance(stream, false)
            }
        }.getOrNull() ?: return null

        try {
            val sliceRawHeight = (MAX_TEXTURE_DIMENSION * sampleSize).coerceAtLeast(sampleSize)
            val slices = mutableListOf<Bitmap>()
            var totalOutWidth = 0
            var totalOutHeight = 0
            var top = 0

            while (top < rawHeight) {
                val bottom = (top + sliceRawHeight).coerceAtMost(rawHeight)
                val rect = Rect(0, top, rawWidth, bottom)
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = preferredConfig
                }
                val slice = try {
                    decoder.decodeRegion(rect, opts)
                } catch (_: OutOfMemoryError) {
                    slices.forEach { if (!it.isRecycled) it.recycle() }
                    return null
                }
                if (slice == null) {
                    slices.forEach { if (!it.isRecycled) it.recycle() }
                    return null
                }
                totalOutWidth = maxOf(totalOutWidth, slice.width)
                totalOutHeight += slice.height
                slices.add(slice)
                top = bottom
            }

            return if (slices.isNotEmpty()) {
                CbzDecodedPage(
                    pageIndex = pageIndex,
                    slices = slices,
                    totalWidth = totalOutWidth,
                    totalHeight = totalOutHeight
                )
            } else {
                null
            }
        } finally {
            runCatching { decoder.recycle() }
        }
    }

    private fun decodeCoverBytes(entry: ZipEntry, maxDimension: Int): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            zipFile.getInputStream(entry).use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
        }
        val rawW = bounds.outWidth
        val rawH = bounds.outHeight
        if (rawW <= 0 || rawH <= 0) return null

        var sampleSize = 1
        while ((rawW / (sampleSize * 2)) >= maxDimension || (rawH / (sampleSize * 2)) >= maxDimension) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bitmap = runCatching {
            zipFile.getInputStream(entry).use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            }
        }.getOrNull() ?: return null

        return try {
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                out.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            runCatching { zipFile.close() }
        }
    }

    companion object {
        private const val MAX_TEXTURE_DIMENSION = 3072
        private const val HARDWARE_SAFE_LIMIT = 4096
        private const val MAX_SINGLE_BITMAP_BYTES = 18L * 1024L * 1024L

        suspend fun open(context: Context, source: BookSource): Result<AndroidCbzArchive> =
            withContext(Dispatchers.IO) {
                val fileResult = CbzArchiveCache.resolveArchiveFile(context, source)
                fileResult.mapCatching { file ->
                    openFile(file)
                }
            }

        fun openFile(file: File): AndroidCbzArchive {
            val zip = ZipFile(file)
            return try {
                var comicInfoEntry: ZipEntry? = null
                val imageEntries = mutableListOf<ZipEntry>()

                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    if (CbzNaturalSort.isComicInfoEntry(name)) {
                        if (comicInfoEntry == null || !name.contains('/')) {
                            comicInfoEntry = entry
                        }
                    } else if (CbzNaturalSort.isSupportedImageEntry(name)) {
                        imageEntries.add(entry)
                    }
                }

                imageEntries.sortWith { a, b -> CbzNaturalSort.compare(a.name, b.name) }
                val pages = imageEntries.mapIndexed { idx, entry ->
                    CbzPageEntry(
                        index = idx,
                        entryName = entry.name,
                        compressedSize = entry.compressedSize,
                        uncompressedSize = entry.size
                    )
                }

                val comicInfo = comicInfoEntry?.let { entry ->
                    runCatching {
                        zip.getInputStream(entry).bufferedReader().use { reader ->
                            ComicInfoParser.parse(reader.readText())
                        }
                    }.getOrNull()
                }

                val fallbackTitle = file.nameWithoutExtension.replace('_', ' ')
                val resolvedTitle = comicInfo?.let {
                    ComicInfoParser.resolveDisplayTitle(it, fallbackTitle)
                }

                val metadata = CbzArchiveMetadata(
                    title = resolvedTitle,
                    series = comicInfo?.series,
                    writer = comicInfo?.writer,
                    pageCount = pages.size,
                    isMangaRtl = comicInfo?.isMangaRtl,
                    coverEntryName = pages.firstOrNull()?.entryName,
                    pages = pages
                )

                AndroidCbzArchive(zip, metadata, pages)
            } catch (t: Throwable) {
                runCatching { zip.close() }
                throw t
            }
        }

        private fun computeInitialSampleSize(
            rawWidth: Int,
            rawHeight: Int,
            targetWidth: Int,
            targetHeight: Int,
            isWebtoon: Boolean,
            bytesPerPixel: Int
        ): Int {
            var sampleSize = 1
            if (isWebtoon) {
                while ((rawWidth / (sampleSize * 2)) >= targetWidth) {
                    sampleSize *= 2
                }
            } else {
                while ((rawWidth / (sampleSize * 2)) >= targetWidth &&
                    (rawHeight / (sampleSize * 2)) >= targetHeight
                ) {
                    sampleSize *= 2
                }
                while ((rawWidth / sampleSize) > HARDWARE_SAFE_LIMIT ||
                    (rawHeight / sampleSize) > HARDWARE_SAFE_LIMIT ||
                    estimatedBytes(rawWidth, rawHeight, sampleSize, bytesPerPixel) > MAX_SINGLE_BITMAP_BYTES
                ) {
                    sampleSize *= 2
                }
            }
            return sampleSize.coerceAtLeast(1)
        }

        private fun estimatedBytes(w: Int, h: Int, sampleSize: Int, bytesPerPixel: Int): Long {
            val sw = (w / sampleSize).coerceAtLeast(1).toLong()
            val sh = (h / sampleSize).coerceAtLeast(1).toLong()
            return sw * sh * bytesPerPixel.toLong()
        }
    }
}
