package io.github.kreader.feature.library.metadata

import androidx.compose.runtime.Composable
import io.github.kreader.core.model.BookFormat
import io.github.kreader.core.model.BookSource

data class ExtractedBookMetadata(
    val title: String,
    val author: String? = null,
    val description: String? = null,
    val format: BookFormat,
    val coverPath: String? = null,
    val isRtl: Boolean = false,
    val fileSizeBytes: Long = 0L,
    val localSource: BookSource? = null
)

interface BookMetadataExtractor {
    suspend fun extract(source: BookSource, bookId: String): ExtractedBookMetadata
}

@Composable
expect fun rememberBookMetadataExtractor(): BookMetadataExtractor
