package io.github.kreader.feature.library.metadata

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.kreader.core.model.BookFormat
import io.github.kreader.core.model.BookSource
import io.github.kreader.core.model.isRtlLanguage
import io.github.kreader.core.model.isRtlText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

class AndroidBookMetadataExtractor(private val context: Context) : BookMetadataExtractor {

    private val coversDir: File by lazy {
        File(context.cacheDir, "covers").apply { mkdirs() }
    }

    override suspend fun extract(source: BookSource, bookId: String): ExtractedBookMetadata = withContext(Dispatchers.IO) {
        val fileSizeBytes = queryFileSize(source)
        val metadata = extractEpub(source, bookId, fileSizeBytes)
        metadata.copy(localSource = source)
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

    private fun extractEpub(
        source: BookSource,
        bookId: String,
        fileSizeBytes: Long
    ): ExtractedBookMetadata {
        var tempFile: File? = null
        val workingFile = when (source) {
            is BookSource.FilePath -> File(source.path)
            is BookSource.ContentUri -> {
                val input = openInputStream(source) ?: return extractGeneric(source, BookFormat.EPUB, fileSizeBytes)
                val temp = File(context.cacheDir, "tmp_meta_${System.currentTimeMillis()}_${bookId.hashCode()}.epub")
                tempFile = temp
                input.use { inStream ->
                    temp.outputStream().use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }
                temp
            }
            else -> return extractGeneric(source, BookFormat.EPUB, fileSizeBytes)
        }

        try {
            if (!workingFile.exists() || workingFile.length() == 0L) {
                return extractGeneric(source, BookFormat.EPUB, fileSizeBytes)
            }

            ZipFile(workingFile).use { zip ->
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

                val lang = Regex("""<dc:language[^>]*>(.*?)</dc:language>""", RegexOption.DOT_MATCHES_ALL)
                    .find(opfXml)?.groupValues?.get(1)?.cleanXml()?.trim().orEmpty()

                val isRtl = Regex("""page-progression-direction=["']([^"']+)["']""")
                    .find(opfXml)?.groupValues?.get(1)?.equals("rtl", ignoreCase = true) == true ||
                    isRtlLanguage(lang) ||
                    isRtlText(title) ||
                    isRtlText(author) ||
                    isRtlText(description)

                val safeId = bookId.replace('/', '_').replace('\\', '_')
                val coverPath = extractEpubCover(zip, opfXml, opfPath, safeId)

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
            tempFile?.delete()
        }
    }

    private fun extractEpubCover(zip: ZipFile, opfXml: String, opfPath: String, safeBookId: String): String? {
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
        val targetCoverFile = File(coversDir, "${safeBookId}.$ext")
        zip.getInputStream(coverEntry).use { inStream ->
            targetCoverFile.outputStream().use { outStream ->
                inStream.copyTo(outStream)
            }
        }
        return targetCoverFile.absolutePath
    }

    private fun extractGeneric(source: BookSource, format: BookFormat = BookFormat.EPUB, fileSizeBytes: Long): ExtractedBookMetadata {
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
