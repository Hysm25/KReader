package io.github.kreader.engine.cbz

/**
 * Represents a single sorted image page entry inside a CBZ archive.
 */
data class CbzPageEntry(
    val index: Int,
    val entryName: String,
    val compressedSize: Long = -1L,
    val uncompressedSize: Long = -1L
)

/**
 * Aggregated CBZ archive metadata, page directory, and optional `ComicInfo.xml` hints.
 */
data class CbzArchiveMetadata(
    val title: String? = null,
    val series: String? = null,
    val writer: String? = null,
    val pageCount: Int,
    val isMangaRtl: Boolean? = null,
    val coverEntryName: String? = null,
    val pages: List<CbzPageEntry> = emptyList()
)
