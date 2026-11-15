package io.github.kreader.engine.epub

import io.github.kreader.core.engine.TocItem
import io.github.kreader.core.model.Locator
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

@OptIn(ExperimentalReadiumApi::class)
fun org.readium.r2.shared.publication.Locator.toDomain(): Locator {
    return Locator(
        href = href.toString(),
        type = mediaType.toString(),
        title = title,
        locations = Locator.Locations(
            progression = locations.progression?.toFloat(),
            position = locations.position,
            totalProgression = locations.totalProgression?.toFloat(),
            fragments = locations.fragments
        ),
        text = text.let {
            Locator.LocatorText(
                before = it.before,
                highlight = it.highlight,
                after = it.after
            )
        }
    )
}

@OptIn(ExperimentalReadiumApi::class)
fun Locator.toReadium(): org.readium.r2.shared.publication.Locator {
    val parsedUrl = Url(href) ?: Url.fromDecodedPath(href) ?: Url("#")!!
    val parsedMediaType = MediaType(type) ?: MediaType.BINARY

    return org.readium.r2.shared.publication.Locator(
        href = parsedUrl,
        mediaType = parsedMediaType,
        title = title,
        locations = org.readium.r2.shared.publication.Locator.Locations(
            progression = locations.progression?.toDouble(),
            position = locations.position,
            totalProgression = locations.totalProgression?.toDouble(),
            fragments = locations.fragments
        ),
        text = text?.let {
            org.readium.r2.shared.publication.Locator.Text(
                before = it.before,
                highlight = it.highlight,
                after = it.after
            )
        } ?: org.readium.r2.shared.publication.Locator.Text()
    )
}

@OptIn(ExperimentalReadiumApi::class)
fun Link.toTocItem(publication: Publication): TocItem {
    val readiumLocator = publication.locatorFromLink(this)
    val domainLocator = readiumLocator?.toDomain() ?: Locator(
        href = href.toString(),
        type = mediaType?.toString() ?: "application/xhtml+xml",
        title = title
    )

    return TocItem(
        title = title ?: href.toString(),
        locator = domainLocator,
        children = children.map { it.toTocItem(publication) }
    )
}
