package io.github.kreader.core.engine

import io.github.kreader.core.model.Locator
import kotlinx.serialization.Serializable

@Serializable
data class TocItem(
    val title: String,
    val locator: Locator,
    val children: List<TocItem> = emptyList()
)
