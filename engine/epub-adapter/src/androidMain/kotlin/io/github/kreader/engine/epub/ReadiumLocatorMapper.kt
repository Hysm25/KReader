package io.github.kreader.engine.epub

import android.net.Uri
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
            fragments = locations.fragments,
            otherLocations = locations.otherLocations.mapNotNull { (k, v) ->
                if (v != null) k to encodeOtherLocationValue(v) else null
            }.toMap()
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
    val parsedUrl = Url(href)
        ?: Url.fromDecodedPath(href)
        ?: runCatching {
            val encoded = Uri.encode(href, "/#?=&;:@+$")
            Url(encoded)
        }.getOrNull()
        ?: Url("#")!!
    val parsedMediaType = MediaType(type) ?: MediaType.BINARY

    val parsedOtherLocations: Map<String, Any> = locations.otherLocations.mapValues { (_, value) ->
        decodeOtherLocationValue(value)
    }

    return org.readium.r2.shared.publication.Locator(
        href = parsedUrl,
        mediaType = parsedMediaType,
        title = title,
        locations = org.readium.r2.shared.publication.Locator.Locations(
            fragments = locations.fragments,
            progression = locations.progression?.toDouble(),
            position = locations.position,
            totalProgression = locations.totalProgression?.toDouble(),
            otherLocations = parsedOtherLocations
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

private fun encodeOtherLocationValue(value: Any): String = when (value) {
    is org.json.JSONObject, is org.json.JSONArray -> value.toString()
    is Map<*, *> -> org.json.JSONObject(value).toString()
    is Collection<*> -> org.json.JSONArray(value).toString()
    else -> value.toString()
}

private fun decodeOtherLocationValue(value: String): Any {
    val trimmed = value.trim()
    if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
        return runCatching { jsonObjectToMap(org.json.JSONObject(trimmed)) }.getOrDefault(value)
    }
    if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
        return runCatching { jsonArrayToList(org.json.JSONArray(trimmed)) }.getOrDefault(value)
    }
    return value
}

private fun jsonObjectToMap(obj: org.json.JSONObject): Map<String, Any> {
    val map = LinkedHashMap<String, Any>(obj.length())
    val keys = obj.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val v = obj.opt(key) ?: continue
        if (v === org.json.JSONObject.NULL) continue
        map[key] = when (v) {
            is org.json.JSONObject -> jsonObjectToMap(v)
            is org.json.JSONArray -> jsonArrayToList(v)
            else -> v
        }
    }
    return map
}

private fun jsonArrayToList(arr: org.json.JSONArray): List<Any> {
    val list = ArrayList<Any>(arr.length())
    for (i in 0 until arr.length()) {
        val v = arr.opt(i) ?: continue
        if (v === org.json.JSONObject.NULL) continue
        list.add(
            when (v) {
                is org.json.JSONObject -> jsonObjectToMap(v)
                is org.json.JSONArray -> jsonArrayToList(v)
                else -> v
            }
        )
    }
    return list
}
