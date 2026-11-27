package io.github.kreader.engine.cbz

/**
 * Natural sort and image entry filtering utilities for CBZ comic & manga archives.
 */
object CbzNaturalSort {

    private val supportedImageExtensions = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "bmp"
    )

    val comparator: Comparator<String> = Comparator { a, b -> compare(a, b) }

    fun isSupportedImageEntry(entryName: String): Boolean {
        val normalized = entryName.replace('\\', '/')
        if (normalized.isBlank() || normalized.endsWith("/")) return false
        if (normalized.startsWith("__MACOSX/", ignoreCase = true) ||
            normalized.contains("/__MACOSX/", ignoreCase = true)
        ) {
            return false
        }
        val fileName = normalized.substringAfterLast('/')
        if (fileName.startsWith("._") || fileName.startsWith(".")) return false
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in supportedImageExtensions
    }

    fun isComicInfoEntry(entryName: String): Boolean {
        val normalized = entryName.replace('\\', '/').trimStart('/')
        return normalized.equals("ComicInfo.xml", ignoreCase = true) ||
            normalized.endsWith("/ComicInfo.xml", ignoreCase = true)
    }

    /**
     * Compares archive entry paths using natural numeric ordering (e.g. `page2.jpg` < `page10.jpg`)
     * without integer overflow on arbitrarily long digit sequences.
     */
    fun compare(a: String, b: String): Int {
        val normA = a.replace('\\', '/')
        val normB = b.replace('\\', '/')
        var ia = 0
        var ib = 0
        val lenA = normA.length
        val lenB = normB.length

        while (ia < lenA && ib < lenB) {
            val ca = normA[ia]
            val cb = normB[ib]

            if (ca in '0'..'9' && cb in '0'..'9') {
                val endA = findDigitRunEnd(normA, ia)
                val endB = findDigitRunEnd(normB, ib)

                val cmp = compareDigitRuns(normA, ia, endA, normB, ib, endB)
                if (cmp != 0) return cmp

                ia = endA
                ib = endB
            } else {
                val cmpLower = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (cmpLower != 0) return cmpLower
                val cmpExact = ca.compareTo(cb)
                if (cmpExact != 0) return cmpExact
                ia++
                ib++
            }
        }
        return (lenA - ia).compareTo(lenB - ib)
    }

    private fun findDigitRunEnd(s: String, start: Int): Int {
        var i = start
        while (i < s.length && s[i] in '0'..'9') {
            i++
        }
        return i
    }

    private fun compareDigitRuns(
        a: String,
        startA: Int,
        endA: Int,
        b: String,
        startB: Int,
        endB: Int
    ): Int {
        var trimA = startA
        while (trimA < endA && a[trimA] == '0') trimA++
        var trimB = startB
        while (trimB < endB && b[trimB] == '0') trimB++

        val sigLenA = endA - trimA
        val sigLenB = endB - trimB
        if (sigLenA != sigLenB) {
            return sigLenA.compareTo(sigLenB)
        }

        for (offset in 0 until sigLenA) {
            val cmp = a[trimA + offset].compareTo(b[trimB + offset])
            if (cmp != 0) return cmp
        }

        // Tie-break on leading zero count (shorter raw digit run first, e.g. "1" before "01")
        return (endA - startA).compareTo(endB - startB)
    }
}
