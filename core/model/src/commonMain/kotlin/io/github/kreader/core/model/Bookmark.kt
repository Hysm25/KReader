package io.github.kreader.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Bookmark(
    val id: String,
    val bookId: String,
    val locator: Locator,
    val title: String? = null,
    val createdTimestamp: Long
)
