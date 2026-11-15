package io.github.kreader.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Highlight(
    val id: String,
    val bookId: String,
    val locator: Locator,
    val colorHex: String,
    val annotation: String? = null,
    val createdTimestamp: Long,
    val modifiedTimestamp: Long
)
