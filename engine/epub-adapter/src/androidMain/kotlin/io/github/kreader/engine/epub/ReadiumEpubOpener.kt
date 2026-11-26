package io.github.kreader.engine.epub

import android.content.Context
import android.net.Uri
import io.github.kreader.core.model.BookSource
import io.github.kreader.core.model.CustomFont
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.RelativeUrl
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.data.Container
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.shared.util.resource.InMemoryResource
import org.readium.r2.shared.util.resource.Resource
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.epub.EpubParser

@OptIn(ExperimentalReadiumApi::class)
class ReadiumEpubOpener(private val context: Context) {

    private val httpClient = DefaultHttpClient()
    private val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
    private val publicationParser = EpubParser()

    /**
     * Opens [source] as a Readium [Publication].
     *
     * [customFonts] are attached to the publication resource container so that the navigator can
     * serve them through the standard Readium asset pipeline.
     */
    suspend fun open(
        source: BookSource,
        bookId: String,
        customFonts: List<CustomFont> = emptyList()
    ): Result<Publication> = withContext(Dispatchers.IO) {
        runCatching {
            val url: AbsoluteUrl = when (source) {
                is BookSource.FilePath -> File(source.path).toUrl()
                is BookSource.ContentUri -> {
                    val uri = Uri.parse(source.uriString)
                    uri.toAbsoluteUrl()
                        ?: AbsoluteUrl(source.uriString)
                        ?: throw IllegalStateException(
                            "Cannot resolve Readium URL for ${source.uriString}"
                        )
                }
                is BookSource.InactiveSource ->
                    throw IllegalStateException("Book source is inactive: ${source.identifier}")
            }

            val asset = assetRetriever.retrieve(url).getOrElse { error ->
                throw IllegalStateException("Failed to retrieve EPUB asset: ${error.message}")
            }

            val fonts = customFonts.mapNotNull { loadFont(it) }

            val opener = PublicationOpener(
                publicationParser = publicationParser,
                onCreatePublication = {
                    if (fonts.isNotEmpty()) {
                        container = FontResourceContainer(
                            base = container,
                            resources = fonts.associate { font ->
                                font.href to InMemoryResource(bytes = font.bytes)
                            }
                        )
                        manifest = manifest.copy(
                            resources = manifest.resources + fonts.map { it.link }
                        )
                    }
                }
            )

            opener.open(
                asset = asset,
                credentials = null,
                allowUserInteraction = false,
                warnings = null
            ).getOrElse { error ->
                throw IllegalStateException("Failed to open publication: ${error.message}")
            }
        }
    }

    private class LoadedFont(
        val href: RelativeUrl,
        val bytes: ByteArray,
        val link: Link
    )

    private fun loadFont(font: CustomFont): LoadedFont? {
        val file = File(font.path)
        if (!file.isFile || file.length() == 0L) return null

        val mediaType = when (file.extension.lowercase()) {
            "otf" -> MediaType.OTF
            "woff" -> MediaType.WOFF
            "woff2" -> MediaType.WOFF2
            else -> MediaType.TTF
        }
        val href = ReadiumFontResources.href(font)
        return LoadedFont(
            href = href,
            bytes = file.readBytes(),
            link = Link(href = href, mediaType = mediaType, title = font.name)
        )
    }

    private class FontResourceContainer(
        private val base: Container<Resource>,
        private val resources: Map<Url, Resource>
    ) : Container<Resource> {

        private val resourcesByNormalizedPath: Map<String, Resource> =
            resources.entries.associate { (key, value) ->
                key.toString().trimStart('/') to value
            }

        override val sourceUrl: AbsoluteUrl?
            get() = base.sourceUrl

        override val entries: Set<Url>
            get() = base.entries + resources.keys

        override fun get(url: Url): Resource? =
            resources[url]
                ?: resourcesByNormalizedPath[url.toString().removePrefix("https://readium/publication/").trimStart('/')]
                ?: base[url]

        override fun close() {
            resources.values.forEach { it.close() }
            base.close()
        }
    }
}
