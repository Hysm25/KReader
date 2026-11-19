package io.github.kreader.engine.epub

import io.github.kreader.core.model.CustomFont
import java.io.File
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.RelativeUrl
import org.readium.r2.shared.util.Url

/**
 * Exposes user-imported font files through the publication's own resource container.
 *
 * The EPUB navigator serves every `https://readium/publication/` request from the opened
 * [org.readium.r2.shared.publication.Publication] container. Registering imported fonts as regular
 * publication resources lets Readium inject a standard `@font-face` declaration through
 * `EpubNavigatorFragment.Configuration.addFontFamilyDeclaration`, with no JavaScript, `<style>`
 * injection or `data:` URI.
 */
object ReadiumFontResources {

    private const val PUBLICATION_BASE_URL = "https://readium/publication/"

    private val SAFE_NAME = Regex("[^a-z0-9_-]+")

    /**
     * Stable relative publication href for the given imported font.
     *
     * The href is deterministic so that reopening the same book keeps the same resource identity.
     */
    fun resourcePath(font: CustomFont): String {
        val file = File(font.path)
        val extension = file.extension.lowercase().ifBlank { "ttf" }
        val safeName = font.name.lowercase()
            .replace(SAFE_NAME, "_")
            .trim('_')
            .take(48)
            .ifBlank { "font" }
        val fingerprint = font.path.hashCode().toUInt().toString(16)
        return "kreader-fonts/$safeName-$fingerprint.$extension"
    }

    /**
     * Relative href used as the container key and manifest resource link.
     */
    fun href(font: CustomFont): RelativeUrl =
        requireNotNull(Url.fromDecodedPath(resourcePath(font))) {
            "Cannot build a font href for ${font.path}"
        }

    /**
     * Absolute URL served by the navigator, suitable for a `@font-face` source.
     */
    fun url(font: CustomFont): AbsoluteUrl =
        requireNotNull(AbsoluteUrl(PUBLICATION_BASE_URL + href(font))) {
            "Cannot build a font URL for ${font.path}"
        }
}
