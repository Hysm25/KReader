package io.github.kreader.feature.library.metadata

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.kreader.core.model.BookFormat
import io.github.kreader.core.model.BookSource

class NativeBookMetadataExtractor : BookMetadataExtractor {
    override suspend fun extract(source: BookSource, bookId: String): ExtractedBookMetadata {
        val ext = source.displayName.substringAfterLast('.', "")
        val format = BookFormat.fromExtension(ext) ?: BookFormat.EPUB
        return ExtractedBookMetadata(
            title = source.displayName.substringBeforeLast('.'),
            format = format,
            coverPath = null,
            fileSizeBytes = 0L,
            localSource = source
        )
    }
}

@Composable
actual fun rememberBookMetadataExtractor(): BookMetadataExtractor {
    return remember { NativeBookMetadataExtractor() }
}
