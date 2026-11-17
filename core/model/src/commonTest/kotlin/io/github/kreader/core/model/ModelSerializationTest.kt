package io.github.kreader.core.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ModelSerializationTest {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    @Test
    fun testReaderPreferencesSerializationRoundTrip() {
        val prefs = ReaderPreferences(
            theme = ReaderTheme(
                mode = ThemeMode.SEPIA,
                backgroundColorHex = "#F4ECD8",
                textColorHex = "#4A3B2C",
                accentColorHex = "#C08A3E"
            ),
            typography = ReaderTypography(
                fontFamily = "Amiri",
                fontSizeSp = 18f,
                lineHeightMultiplier = 1.8f,
                paddingHorizontal = 24,
                textAlignment = ReaderTypography.TextAlignment.JUSTIFY
            ),
            isScrollMode = true,
            pageLayout = PageLayout.SINGLE,
            volumeKeyNavigation = true
        )

        val serialized = json.encodeToString(prefs)
        val deserialized = json.decodeFromString<ReaderPreferences>(serialized)

        assertEquals(prefs.theme.mode, deserialized.theme.mode)
        assertEquals(prefs.theme.backgroundColorHex, deserialized.theme.backgroundColorHex)
        assertEquals(prefs.typography.fontFamily, deserialized.typography.fontFamily)
        assertEquals(prefs.typography.fontSizeSp, deserialized.typography.fontSizeSp)
        assertEquals(prefs.isScrollMode, deserialized.isScrollMode)
        assertEquals(prefs.volumeKeyNavigation, deserialized.volumeKeyNavigation)
    }

    @Test
    fun testLocatorSerializationRoundTrip() {
        val locator = Locator(
            href = "chapter01.xhtml",
            type = "application/xhtml+xml",
            title = "Chapter 1",
            locations = Locator.Locations(
                progression = 0.42f,
                position = 12,
                totalProgression = 0.15f
            ),
            text = Locator.LocatorText(
                highlight = "Important quoted sentence"
            )
        )

        val serialized = json.encodeToString(locator)
        val deserialized = json.decodeFromString<Locator>(serialized)

        assertEquals(locator.href, deserialized.href)
        assertEquals(locator.title, deserialized.title)
        assertEquals(0.42f, deserialized.locations.progression)
        assertEquals("Important quoted sentence", deserialized.text?.highlight)
    }

    @Test
    fun testBookSerializationRoundTrip() {
        val book = Book(
            id = "book_123",
            title = "Test Book",
            author = "Author Name",
            format = BookFormat.EPUB,
            source = BookSource.FilePath(path = "/path/to/book.epub", displayName = "book.epub"),
            addedTimestamp = 1700000000000L,
            totalProgression = 0.25f
        )

        val serialized = json.encodeToString(book)
        val deserialized = json.decodeFromString<Book>(serialized)

        assertEquals(book.id, deserialized.id)
        assertEquals(book.title, deserialized.title)
        assertEquals(book.format, deserialized.format)
        assertEquals(book.source.displayName, deserialized.source.displayName)
    }
}
