package io.github.kreader.core.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextDirectionUtilsTest {

    @Test
    fun testIsRtlTextWithArabicCharacters() {
        assertTrue(isRtlText("رواية البؤساء"))
        assertTrue(isRtlText("مرحبا بالعالم"))
        assertTrue(isRtlText("Chapter 1: مقدمة الكتاب"))
    }

    @Test
    fun testIsRtlTextWithHebrewCharacters() {
        assertTrue(isRtlText("שלום עולם"))
    }

    @Test
    fun testIsRtlTextWithLatinCharacters() {
        assertFalse(isRtlText("Les Misérables"))
        assertFalse(isRtlText("The Great Gatsby"))
        assertFalse(isRtlText("12345!@#$"))
    }

    @Test
    fun testIsRtlTextWithEmptyOrNull() {
        assertFalse(isRtlText(null))
        assertFalse(isRtlText(""))
        assertFalse(isRtlText("   "))
    }

    @Test
    fun testIsRtlLanguage() {
        assertTrue(isRtlLanguage("ar"))
        assertTrue(isRtlLanguage("ara"))
        assertTrue(isRtlLanguage("Arabic"))
        assertTrue(isRtlLanguage("fa"))
        assertTrue(isRtlLanguage("ur"))
        assertTrue(isRtlLanguage("he"))
        assertTrue(isRtlLanguage("heb"))
        assertTrue(isRtlLanguage("hebrew"))

        assertFalse(isRtlLanguage("en"))
        assertFalse(isRtlLanguage("eng"))
        assertFalse(isRtlLanguage("fr"))
        assertFalse(isRtlLanguage("de"))
        assertFalse(isRtlLanguage(null))
        assertFalse(isRtlLanguage(""))
    }
}
