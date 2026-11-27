package io.github.kreader.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Book(
    val id: String,
    val title: String,
    val author: String? = null,
    val description: String? = null,
    val coverPath: String? = null,
    val format: BookFormat,
    val source: BookSource,
    val fileSizeBytes: Long = 0L,
    val lastReadTimestamp: Long? = null,
    val addedTimestamp: Long,
    val totalProgression: Float = 0f,
    val lastLocator: Locator? = null,
    val pageCount: Int? = null,
    val isManga: Boolean? = null
) {
    /**
     * Returns the parent folder relative path for CBZ files inside subdirectories (series),
     * or `null` for root-level CBZ files and all EPUBs.
     */
    val cbzSeriesFolder: String?
        get() = if (format == BookFormat.CBZ && id.contains('/')) {
            id.substringBeforeLast('/').takeIf { it.isNotBlank() }
        } else {
            null
        }
}

/**
 * Canonical workspace preference key for a CBZ series folder (`"series:<folderPath>"`).
 */
fun cbzSeriesWorkspaceKey(seriesFolder: String): String = "series:$seriesFolder"

/**
 * Aggregates a subdirectory of `.cbz` chapters into a single library series card.
 */
data class CbzSeries(
    val id: String,
    val title: String,
    val chapters: List<Book>,
    val coverPath: String?,
    val lastReadTimestamp: Long?,
    val addedTimestamp: Long,
    val totalProgression: Float
)
