package io.github.kreader.core.model

/**
 * Checks if a string contains Arabic, Persian, Urdu, Hebrew or other RTL characters.
 */
fun isRtlText(text: String?): Boolean {
    if (text.isNullOrBlank()) return false
    return text.any { ch ->
        ch in '\u0600'..'\u06FF' || // Arabic
        ch in '\u0750'..'\u077F' || // Arabic Supplement
        ch in '\u08A0'..'\u08FF' || // Arabic Extended-A
        ch in '\uFB50'..'\uFDFF' || // Arabic Presentation Forms-A
        ch in '\uFE70'..'\uFEFF' || // Arabic Presentation Forms-B
        ch in '\u0590'..'\u05FF'    // Hebrew
    }
}

/**
 * Checks if a language code or name represents an RTL language.
 */
fun isRtlLanguage(lang: String?): Boolean {
    if (lang.isNullOrBlank()) return false
    val l = lang.lowercase().trim()
    return l.startsWith("ar") ||
        l.startsWith("fa") ||
        l.startsWith("ur") ||
        l.startsWith("he") ||
        l.startsWith("per") ||
        l.startsWith("urd") ||
        l.startsWith("heb") ||
        l.contains("arabic") ||
        l.contains("persian") ||
        l.contains("hebrew") ||
        l.contains("urdu")
}
