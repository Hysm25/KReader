package io.github.kreader.engine.epub

import android.content.Context
import android.net.Uri
import io.github.kreader.core.model.BookSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.epub.EpubParser
import java.io.File

@OptIn(ExperimentalReadiumApi::class)
class ReadiumEpubOpener(private val context: Context) {

    private val httpClient = DefaultHttpClient()
    private val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
    private val publicationOpener = PublicationOpener(
        publicationParser = EpubParser()
    )

    suspend fun open(source: BookSource, bookId: String): Result<Publication> = withContext(Dispatchers.IO) {
        runCatching {
            val file = when (source) {
                is BookSource.FilePath -> File(source.path)
                is BookSource.ContentUri -> {
                    val cacheFile = File(context.cacheDir, "readium_${bookId}.epub")
                    if (!cacheFile.exists() || cacheFile.length() == 0L) {
                        context.contentResolver.openInputStream(Uri.parse(source.uriString))?.use { input ->
                            cacheFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        } ?: throw IllegalStateException("Cannot open ContentUri: ${source.uriString}")
                    }
                    cacheFile
                }
                else -> throw IllegalArgumentException("Unsupported BookSource type: $source")
            }

            if (!file.exists() || file.length() == 0L) {
                throw IllegalStateException("EPUB file does not exist or is empty at ${file.absolutePath}")
            }

            val asset = assetRetriever.retrieve(file).getOrElse { error ->
                throw IllegalStateException("Failed to retrieve EPUB asset: ${error.message}", error.cause)
            }

            publicationOpener.open(asset).getOrElse { error ->
                throw IllegalStateException("Failed to open publication: ${error.message}", error.cause)
            }
        }
    }
}
