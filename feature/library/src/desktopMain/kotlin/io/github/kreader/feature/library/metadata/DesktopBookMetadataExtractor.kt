package io.github.kreader.feature.library.metadata

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.kreader.core.model.BookFormat
import io.github.kreader.core.model.BookSource
import io.github.kreader.core.model.isRtlLanguage
import io.github.kreader.core.model.isRtlText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

class DesktopBookMetadataExtractor(
    private val coversDir: File = File(System.getProperty("user.home"), ".kreader/covers").apply { mkdirs() }
) : BookMetadataExtractor {

    override suspend fun extract(source: BookSource, bookId: String): ExtractedBookMetadata = withContext(Dispatchers.IO) {
        val file = when (source) {
            is BookSource.FilePath -> File(source.path)
            else -> File(source.displayName)
        }

        val fileSizeBytes = if (file.exists()) file.length() else 0L
        val metadata = extractEpub(file, source, bookId, fileSizeBytes)
        val effectiveSource = if (file.exists()) BookSource.FilePath(path = file.absolutePath, displayName = source.displayName) else source
        metadata.copy(localSource = effectiveSource)
    }

    private fun detectFormat(name: String): BookFormat = BookFormat.EPUB

    private fun extractEpub(file: File, source: BookSource, bookId: String, fileSizeBytes: Long): ExtractedBookMetadata {
        if (!file.exists()) return extractGeneric(source, BookFormat.EPUB, fileSizeBytes)

        return try {
            ZipFile(file).use { zip ->
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
                    ?: file.nameWithoutExtension

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

                val coverPath = extractEpubCover(zip, opfXml, opfPath, bookId)

                ExtractedBookMetadata(
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
            extractGeneric(source, BookFormat.EPUB, fileSizeBytes)
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
            ?: return null

        val safeBookId = bookId.replace('/', '_').replace('\\', '_')
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
    return remember { DesktopBookMetadataExtractor() }
}
