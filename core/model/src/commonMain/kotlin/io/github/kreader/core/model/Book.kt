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
    val lastLocator: Locator? = null
)
