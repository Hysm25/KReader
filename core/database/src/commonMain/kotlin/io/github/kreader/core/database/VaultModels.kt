package io.github.kreader.core.database

import io.github.kreader.core.model.BookSource
import io.github.kreader.core.model.Bookmark
import io.github.kreader.core.model.CustomTheme
import io.github.kreader.core.model.Highlight
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.ReaderPreferencesOverride
import kotlinx.serialization.Serializable

@Serializable
data class BookProgressRecord(
    val relativePath: String,
    val totalProgression: Float = 0f,
    val lastReadTimestamp: Long? = null,
    val lastLocator: Locator? = null,
    val preferencesOverride: ReaderPreferencesOverride? = null
)

@Serializable
data class VaultWorkspace(
    val books: Map<String, BookProgressRecord> = emptyMap()
)

@Serializable
data class VaultAnnotations(
    val bookmarks: Map<String, List<Bookmark>> = emptyMap(),
    val highlights: Map<String, List<Highlight>> = emptyMap()
)

@Serializable
data class VaultThemes(
    val themes: List<CustomTheme> = emptyList()
)

data class VaultEpubFile(
    val relativePath: String,
    val displayName: String,
    val source: BookSource,
    val fileSizeBytes: Long = 0L,
    val lastModified: Long = 0L
)
