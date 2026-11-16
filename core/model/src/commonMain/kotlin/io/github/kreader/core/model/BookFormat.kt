package io.github.kreader.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class BookFormat(val mimeType: String, val extensions: List<String>) {
    EPUB("application/epub+zip", listOf("epub"));

    companion object {
        fun fromExtension(ext: String): BookFormat? {
            val normalized = ext.lowercase().trimStart('.')
            return entries.firstOrNull { format -> format.extensions.contains(normalized) }
        }

        fun fromMimeType(mime: String): BookFormat? {
            val normalized = mime.lowercase()
            return entries.firstOrNull { format -> format.mimeType.equals(normalized, ignoreCase = true) }
        }
    }
}
