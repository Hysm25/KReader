package io.github.kreader.core.model

import kotlinx.serialization.Serializable

/**
 * Standard Readium Locator model for referencing precise positions inside publications.
 * Conforms to the Readium Locator specification.
 */
@Serializable
data class Locator(
    val href: String,
    val type: String,
    val title: String? = null,
    val locations: Locations = Locations(),
    val text: LocatorText? = null
) {
    @Serializable
    data class Locations(
        val progression: Float? = null,
        val position: Int? = null,
        val totalProgression: Float? = null,
        val fragments: List<String> = emptyList()
    )

    @Serializable
    data class LocatorText(
        val before: String? = null,
        val highlight: String? = null,
        val after: String? = null
    )

    companion object {
        fun initial(href: String, type: String): Locator = Locator(
            href = href,
            type = type,
            locations = Locations(progression = 0f, totalProgression = 0f)
        )
    }
}
