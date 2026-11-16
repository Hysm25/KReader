package io.github.kreader.engine.epub

import android.content.Context
import android.net.Uri
import io.github.kreader.core.model.BookSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.shared.util.toUrl
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
            val url: AbsoluteUrl = when (source) {
                is BookSource.FilePath -> File(source.path).toUrl()
                is BookSource.ContentUri -> {
                    val uri = Uri.parse(source.uriString)
                    uri.toAbsoluteUrl()
                        ?: AbsoluteUrl(source.uriString)
                        ?: (Url(source.uriString) as? AbsoluteUrl)
                        ?: throw IllegalStateException("Cannot resolve Readium URL for ${source.uriString}")
                }
                else -> throw IllegalArgumentException("Unsupported BookSource type: $source")
            }

            val asset = assetRetriever.retrieve(url).getOrElse { error ->
                throw IllegalStateException("Failed to retrieve EPUB asset: ${error.message}")
            }

            publicationOpener.open(asset, allowUserInteraction = false).getOrElse { error ->
                throw IllegalStateException("Failed to open publication: ${error.message}")
            }
        }
    }
}
