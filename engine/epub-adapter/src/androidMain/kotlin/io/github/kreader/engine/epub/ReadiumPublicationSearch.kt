package io.github.kreader.engine.epub

import io.github.kreader.core.model.Locator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positionsByReadingOrder
import org.readium.r2.shared.util.getOrElse
import kotlin.coroutines.coroutineContext

/**
 * Executes fast, Unicode-safe in-book search across a Readium [Publication] using
 * [ArabicSearchNormalizer] (handling Arabic Tashkeel, Tatweel, and Alef/Ya variants as well as
 * case-insensitive Latin text) while preserving original DOM text quotes for exact Readium page jumps.
 */
@OptIn(ExperimentalReadiumApi::class)
object ReadiumPublicationSearch {

    private const val MAX_RESULTS = 100

    suspend fun search(publication: Publication, query: String): List<Locator> = withContext(Dispatchers.IO) {
        if (ArabicSearchNormalizer.normalizeQuery(query).isEmpty()) return@withContext emptyList()

        val readingOrder = publication.readingOrder
        if (readingOrder.isEmpty()) return@withContext emptyList()

        val positionsByOrder = runCatching { publication.positionsByReadingOrder() }.getOrNull()
        val totalSpine = readingOrder.size.coerceAtLeast(1)
        val results = mutableListOf<Locator>()

        for ((spineIndex, link) in readingOrder.withIndex()) {
            coroutineContext.ensureActive()
            if (results.size >= MAX_RESULTS) break

            val resource = publication.get(link) ?: continue
            val html = try {
                resource.read().getOrElse { null }?.toString(Charsets.UTF_8)
            } finally {
                runCatching { resource.close() }
            } ?: continue

            val visibleText = ArabicSearchNormalizer.extractVisibleHtmlText(html)
            if (visibleText.isEmpty()) continue

            val matches = ArabicSearchNormalizer.search(
                rawText = visibleText,
                query = query,
                maxMatches = MAX_RESULTS - results.size
            )
            if (matches.isEmpty()) continue

            val chapterPositions = positionsByOrder?.getOrNull(spineIndex).orEmpty()
            val firstTotalProg = chapterPositions.firstOrNull()?.locations?.totalProgression?.toFloat()
                ?: (spineIndex.toFloat() / totalSpine.toFloat())
            val lastTotalProg = chapterPositions.lastOrNull()?.locations?.totalProgression?.toFloat()
                ?: ((spineIndex + 1).toFloat() / totalSpine.toFloat())
            val chapterSpan = (lastTotalProg - firstTotalProg).coerceAtLeast(0f)

            for (match in matches) {
                val totalProg = (firstTotalProg + match.progression * chapterSpan).coerceIn(0f, 1f)
                results.add(
                    Locator(
                        href = link.href.toString(),
                        type = link.mediaType?.toString() ?: "application/xhtml+xml",
                        title = link.title,
                        locations = Locator.Locations(
                            progression = match.progression,
                            totalProgression = totalProg
                        ),
                        text = Locator.LocatorText(
                            before = match.before.takeLast(24),
                            highlight = match.highlight,
                            after = match.after.take(24)
                        )
                    )
                )
            }
        }

        results
    }
}
