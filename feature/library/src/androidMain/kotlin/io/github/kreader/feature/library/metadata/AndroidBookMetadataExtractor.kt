package io.github.kreader.feature.library.metadata

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.kreader.core.model.BookFormat
import io.github.kreader.core.model.BookSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

class AndroidBookMetadataExtractor(private val context: Context) : BookMetadataExtractor {

    private val coversDir: File by lazy {
        File(context.filesDir, "covers").apply { mkdirs() }
    }

    override suspend fun extract(source: BookSource, bookId: String): ExtractedBookMetadata = withContext(Dispatchers.IO) {
        val format = detectFormat(source)
        val fileSizeBytes = queryFileSize(source)

        when (format) {
            BookFormat.EPUB -> extractEpub(source, bookId, fileSizeBytes)
            BookFormat.PDF -> extractPdf(source, bookId, fileSizeBytes)
            BookFormat.CBZ -> extractGeneric(source, format, fileSizeBytes)
        }
    }

    private fun detectFormat(source: BookSource): BookFormat {
        val name = source.displayName.lowercase()
        return when {
            name.endsWith(".epub") -> BookFormat.EPUB
            name.endsWith(".pdf") -> BookFormat.PDF
            name.endsWith(".cbz") || name.endsWith(".zip") -> BookFormat.CBZ
            else -> BookFormat.EPUB
        }
    }

    private fun queryFileSize(source: BookSource): Long {
        return runCatching {
            when (source) {
                is BookSource.FilePath -> File(source.path).length()
                is BookSource.ContentUri -> {
                    val uri = Uri.parse(source.uriString)
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                        val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (index >= 0 && cursor.moveToFirst()) cursor.getLong(index) else 0L
                    } ?: 0L
                }
                else -> 0L
            }
        }.getOrDefault(0L)
    }

    private fun openInputStream(source: BookSource): InputStream? {
        return when (source) {
            is BookSource.FilePath -> File(source.path).inputStream()
            is BookSource.ContentUri -> context.contentResolver.openInputStream(Uri.parse(source.uriString))
            else -> null
        }
    }

    private fun openParcelFileDescriptor(source: BookSource): ParcelFileDescriptor? {
        return when (source) {
            is BookSource.FilePath -> ParcelFileDescriptor.open(File(source.path), ParcelFileDescriptor.MODE_READ_ONLY)
            is BookSource.ContentUri -> context.contentResolver.openFileDescriptor(Uri.parse(source.uriString), "r")
            else -> null
        }
    }

    private fun extractEpub(source: BookSource, bookId: String, fileSizeBytes: Long): ExtractedBookMetadata {
        val tempFile = File(context.cacheDir, "temp_${bookId}.epub")
        try {
            val input = openInputStream(source) ?: return extractGeneric(source, BookFormat.EPUB, fileSizeBytes)
            input.use { inStream ->
                tempFile.outputStream().use { outStream ->
                    inStream.copyTo(outStream)
                }
            }

            ZipFile(tempFile).use { zip ->
                val containerEntry = zip.getEntry("META-INF/container.xml")
                    ?: zip.entries().asSequence().firstOrNull { it.name.equals("META-INF/container.xml", ignoreCase = true) }
                val containerXml = containerEntry?.let { zip.getInputStream(it).bufferedReader().readText() }
                val opfPath = containerXml?.let {
                    Regex("""full-path=["']([^"']+)["']""").find(it)?.groupValues?.get(1)
                } ?: "OEBPS/content.opf"

                val opfEntry = zip.getEntry(opfPath)
                    ?: zip.entries().asSequence().firstOrNull { it.name.equals(opfPath, ignoreCase = true) }
                val opfXml = opfEntry?.let { zip.getInputStream(it).bufferedReader().readText() }.orEmpty()

                val title = Regex("""<dc:title[^>]*>(.*?)</dc:title>""", RegexOption.DOT_MATCHES_ALL)
                    .find(opfXml)?.groupValues?.get(1)?.cleanXml()?.trim()
                    ?.ifBlank { null }
                    ?: source.displayName.removeSuffix(".epub").removeSuffix(".EPUB")

                val author = Regex("""<dc:creator[^>]*>(.*?)</dc:creator>""", RegexOption.DOT_MATCHES_ALL)
                    .find(opfXml)?.groupValues?.get(1)?.cleanXml()?.trim()
                    ?.ifBlank { null }

                val description = Regex("""<dc:description[^>]*>(.*?)</dc:description>""", RegexOption.DOT_MATCHES_ALL)
                    .find(opfXml)?.groupValues?.get(1)?.cleanXml()?.trim()
                    ?.ifBlank { null }

                val isRtl = Regex("""page-progression-direction=["']([^"']+)["']""")
                    .find(opfXml)?.groupValues?.get(1)?.equals("rtl", ignoreCase = true) == true

                val coverPath = extractEpubCover(zip, opfXml, opfPath, bookId)

                return ExtractedBookMetadata(
                    title = title,
                    author = author,
                    description = description,
                    format = BookFormat.EPUB,
                    coverPath = coverPath,
                    isRtl = isRtl,
                    fileSizeBytes = fileSizeBytes
                )
            }
        } catch (e: Exception) {
            return extractGeneric(source, BookFormat.EPUB, fileSizeBytes)
        } finally {
            tempFile.delete()
        }
    }

    private fun extractEpubCover(zip: ZipFile, opfXml: String, opfPath: String, bookId: String): String? {
        val epub3Match = Regex("""<item[^>]*properties=["'][^"']*cover-image[^"']*["'][^>]*href=["']([^"']+)["']""").find(opfXml)
            ?: Regex("""<item[^>]*href=["']([^"']+)["'][^>]*properties=["'][^"']*cover-image[^"']*["']""").find(opfXml)

        val coverId = Regex("""<meta[^>]*name=["']cover["'][^>]*content=["']([^"']+)["']""").find(opfXml)?.groupValues?.get(1)
            ?: Regex("""<meta[^>]*content=["']([^"']+)["'][^>]*name=["']cover["']""").find(opfXml)?.groupValues?.get(1)

        val epub2Match = if (coverId != null) {
            Regex("""<item[^>]*id=["']${Regex.escape(coverId)}["'][^>]*href=["']([^"']+)["']""").find(opfXml)
        } else null

        val fallbackMatch = Regex("""<item[^>]*href=["']([^"']*(?:cover|title)[^"']*\.(?:jpg|jpeg|png|webp))["']""", RegexOption.IGNORE_CASE).find(opfXml)

        val relativeHref = epub3Match?.groupValues?.get(1)
            ?: epub2Match?.groupValues?.get(1)
            ?: fallbackMatch?.groupValues?.get(1)
            ?: return null

        val opfDir = File(opfPath).parent?.replace('\\', '/')
        val targetPath = if (opfDir != null) normalizePath("$opfDir/$relativeHref") else relativeHref

        val coverEntry = zip.getEntry(targetPath)
            ?: zip.entries().asSequence().firstOrNull { it.name.equals(targetPath, ignoreCase = true) }
            ?: zip.getEntry(relativeHref)
            ?: zip.entries().asSequence().firstOrNull { it.name.endsWith(File(relativeHref).name, ignoreCase = true) }
            ?: return null

        val ext = File(coverEntry.name).extension.ifBlank { "jpg" }
        val targetCoverFile = File(coversDir, "${bookId}.$ext")
        zip.getInputStream(coverEntry).use { inStream ->
            targetCoverFile.outputStream().use { outStream ->
                inStream.copyTo(outStream)
            }
        }
        return targetCoverFile.absolutePath
    }

    private fun extractPdf(source: BookSource, bookId: String, fileSizeBytes: Long): ExtractedBookMetadata {
        val title = source.displayName.removeSuffix(".pdf").removeSuffix(".PDF")
        var coverPath: String? = null

        val pfd = openParcelFileDescriptor(source)
        if (pfd != null) {
            try {
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount > 0) {
                        renderer.openPage(0).use { page ->
                            val targetWidth = 360
                            val targetHeight = (targetWidth * (page.height.toFloat() / page.width.toFloat())).toInt().coerceIn(200, 600)
                            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            canvas.drawColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val coverFile = File(coversDir, "${bookId}.jpg")
                            coverFile.outputStream().use { out ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                            }
                            bitmap.recycle()
                            coverPath = coverFile.absolutePath
                        }
                    }
                }
            } catch (ignored: Exception) {
            } finally {
                runCatching { pfd.close() }
            }
        }

        return ExtractedBookMetadata(
            title = title,
            format = BookFormat.PDF,
            coverPath = coverPath,
            fileSizeBytes = fileSizeBytes
        )
    }

    private fun extractGeneric(source: BookSource, format: BookFormat, fileSizeBytes: Long): ExtractedBookMetadata {
        return ExtractedBookMetadata(
            title = source.displayName.substringBeforeLast('.'),
            format = format,
            coverPath = null,
            fileSizeBytes = fileSizeBytes
        )
    }

    private fun normalizePath(path: String): String {
        val segments = path.replace('\\', '/').split('/')
        val resolved = mutableListOf<String>()
        for (seg in segments) {
            when (seg) {
                "", "." -> continue
                ".." -> if (resolved.isNotEmpty()) resolved.removeAt(resolved.lastIndex)
                else -> resolved.add(seg)
            }
        }
        return resolved.joinToString("/")
    }

    private fun String.cleanXml(): String {
        return replace(Regex("<.*?>"), "")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }
}

@Composable
actual fun rememberBookMetadataExtractor(): BookMetadataExtractor {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidBookMetadataExtractor(context) }
}
