package io.github.kreader.engine.cbz

import io.github.kreader.core.model.CbzReadingMode
import io.github.kreader.core.model.isRtlText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.imageio.ImageIO

/**
 * JVM/Desktop CBZ archive reader backed by `java.util.zip.ZipFile` for O(1) random page access.
 */
class DesktopCbzArchive private constructor(
    private val zipFile: ZipFile,
    val metadata: CbzArchiveMetadata,
    val pages: List<CbzPageEntry>
) : Closeable {

    private val closed = AtomicBoolean(false)
    val pageCount: Int get() = pages.size

    suspend fun readPageBytes(pageIndex: Int): ByteArray? = withContext(Dispatchers.IO) {
        if (closed.get() || pageIndex !in pages.indices) return@withContext null
        val entry = zipFile.getEntry(pages[pageIndex].entryName) ?: return@withContext null
        runCatching {
            zipFile.getInputStream(entry).use { it.readBytes() }
        }.getOrNull()
    }

    suspend fun extractCoverBytes(): ByteArray? = withContext(Dispatchers.IO) {
        if (closed.get()) return@withContext null
        val coverName = metadata.coverEntryName ?: return@withContext null
        val entry = zipFile.getEntry(coverName) ?: return@withContext null
        runCatching {
            zipFile.getInputStream(entry).use { it.readBytes() }
        }.getOrNull()
    }

    suspend fun detectPreferredReadingMode(seriesOrBookTitle: String): CbzReadingMode = withContext(Dispatchers.IO) {
        if (closed.get() || pages.isEmpty()) {
            return@withContext defaultPagedMode(seriesOrBookTitle)
        }
        val sampleCount = minOf(pages.size, 6)
        val ratios = mutableListOf<Float>()
        for (i in 0 until sampleCount) {
            val entry = zipFile.getEntry(pages[i].entryName) ?: continue
            runCatching {
                zipFile.getInputStream(entry).use { stream ->
                    ImageIO.createImageInputStream(stream)?.use { iis ->
                        val readers = ImageIO.getImageReaders(iis)
                        if (readers.hasNext()) {
                            val reader = readers.next()
                            try {
                                reader.input = iis
                                val w = reader.getWidth(0)
                                val h = reader.getHeight(0)
                                if (w > 0 && h > 0) {
                                    ratios.add(h.toFloat() / w.toFloat())
                                }
                            } finally {
                                reader.dispose()
                            }
                        }
                    }
                }
            }
        }
        if (ratios.isNotEmpty()) {
            val sorted = ratios.sorted()
            val median = sorted[sorted.size / 2]
            val average = ratios.sum() / ratios.size.toFloat()
            if (median >= 2.2f || average >= 2.2f) {
                return@withContext CbzReadingMode.WEBTOON
            }
        }
        defaultPagedMode(seriesOrBookTitle)
    }

    private fun defaultPagedMode(seriesOrBookTitle: String): CbzReadingMode {
        val isMangaOrArabic = metadata.isMangaRtl == true ||
            isRtlText(seriesOrBookTitle) ||
            isRtlText(metadata.title.orEmpty()) ||
            isRtlText(metadata.series.orEmpty())
        return if (isMangaOrArabic) CbzReadingMode.MANGA_RTL else CbzReadingMode.COMIC_LTR
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            runCatching { zipFile.close() }
        }
    }

    companion object {
        fun openFile(file: File): DesktopCbzArchive {
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

                DesktopCbzArchive(zip, metadata, pages)
            } catch (t: Throwable) {
                runCatching { zip.close() }
                throw t
            }
        }
    }
}
