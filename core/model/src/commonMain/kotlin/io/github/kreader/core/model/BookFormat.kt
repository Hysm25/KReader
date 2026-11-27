package io.github.kreader.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class BookFormat(val mimeType: String, val extensions: List<String>) {
    EPUB("application/epub+zip", listOf("epub")),
    CBZ("application/vnd.comicbook+zip", listOf("cbz"));

    companion object {
        private val alternateMimeTypes = mapOf(
            "application/x-cbz" to CBZ,
            "application/x-comicbook+zip" to CBZ
        )

        fun fromExtension(ext: String): BookFormat? {
            val normalized = ext.lowercase().trimStart('.')
            return entries.firstOrNull { format -> format.extensions.contains(normalized) }
        }

        fun fromMimeType(mime: String): BookFormat? {
            val normalized = mime.lowercase().substringBefore(';').trim()
            return entries.firstOrNull { format -> format.mimeType.equals(normalized, ignoreCase = true) }
                ?: alternateMimeTypes[normalized]
        }
    }
}
