package io.github.kreader.core.model

import kotlinx.serialization.Serializable

/**
 * Abstraction for book storage location across desktop paths, Android SAF URIs, and iOS sandbox URLs.
 */
@Serializable
sealed interface BookSource {
    val identifier: String
    val displayName: String

    @Serializable
    data class FilePath(
        val path: String,
        override val displayName: String
    ) : BookSource {
        override val identifier: String get() = path
    }

    @Serializable
    data class ContentUri(
        val uriString: String,
        override val displayName: String
    ) : BookSource {
        override val identifier: String get() = uriString
    }

    @Serializable
    data class InactiveSource(
        override val identifier: String,
        override val displayName: String
    ) : BookSource
}
