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
                totalProgression = 0.15f,
                fragments = listOf("point(12)"),
                otherLocations = mapOf("cssSelector" to "p:nth-of-type(3)")
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
        assertEquals("p:nth-of-type(3)", deserialized.locations.otherLocations["cssSelector"])
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

    @Test
    fun testReaderPreferencesOverrideSparseInheritance() {
        val global = ReaderPreferences(
            theme = ReaderTheme(mode = ThemeMode.LIGHT),
            typography = ReaderTypography(fontFamily = "Default", fontSizeSp = 16f, lineHeightMultiplier = 1.4f)
        )

        // Book overrides only fontFamily and overrideFont
        val fontOverride = ReaderPreferencesOverride(
            typography = ReaderTypographyOverride(fontFamily = "Amiri", overrideFont = true)
        )

        val merged = global.mergeWith(fontOverride)
        assertEquals("Amiri", merged.typography.fontFamily)
        assertEquals(true, merged.typography.overrideFont)
        assertEquals(16f, merged.typography.fontSizeSp)
        assertEquals(1.4f, merged.typography.lineHeightMultiplier)
        assertEquals(ThemeMode.LIGHT, merged.theme.mode)

        // Global updates: theme changes to SEPIA and font size changes to 20f
        val updatedGlobal = global.copy(
            theme = ReaderTheme(mode = ThemeMode.SEPIA),
            typography = global.typography.copy(fontSizeSp = 20f)
        )

        val updatedMerged = updatedGlobal.mergeWith(fontOverride)
        assertEquals("Amiri", updatedMerged.typography.fontFamily) // Overridden property preserved
        assertEquals(true, updatedMerged.typography.overrideFont)  // Overridden property preserved
        assertEquals(20f, updatedMerged.typography.fontSizeSp)     // Inherited from updated global
        assertEquals(ThemeMode.SEPIA, updatedMerged.theme.mode)    // Inherited from updated global

        // Null override inherits 100% of global
        val resetMerged = updatedGlobal.mergeWith(null)
        assertEquals("Default", resetMerged.typography.fontFamily)
        assertEquals(false, resetMerged.typography.overrideFont)
        assertEquals(20f, resetMerged.typography.fontSizeSp)
        assertEquals(ThemeMode.SEPIA, resetMerged.theme.mode)
    }

    @Test
    fun testReaderPreferencesOverrideSerializationRoundTrip() {
        val override = ReaderPreferencesOverride(
            theme = ReaderTheme(mode = ThemeMode.DARK),
            typography = ReaderTypographyOverride(
                fontFamily = "Scheherazade",
                fontSizeSp = 22f,
                overrideFont = true
            ),
            isScrollMode = true
        )

        val serialized = json.encodeToString(override)
        val deserialized = json.decodeFromString<ReaderPreferencesOverride>(serialized)

        assertEquals(ThemeMode.DARK, deserialized.theme?.mode)
        assertEquals("Scheherazade", deserialized.typography?.fontFamily)
        assertEquals(true, deserialized.typography?.overrideFont)
        assertEquals(22f, deserialized.typography?.fontSizeSp)
        assertEquals(null, deserialized.typography?.lineHeightMultiplier)
        assertEquals(true, deserialized.isScrollMode)
        assertEquals(null, deserialized.keepScreenOn)
    }

    @Test
    fun testCbzBookAndPreferencesRoundTrip() {
        val cbzBook = Book(
            id = "manga/vol01.cbz",
            title = "Test Manga Vol. 1",
            author = "Mangaka",
            format = BookFormat.CBZ,
            source = BookSource.FilePath(path = "/vault/manga/vol01.cbz", displayName = "vol01.cbz"),
            addedTimestamp = 1700000000000L,
            totalProgression = 0.5f,
            pageCount = 192,
            isManga = true
        )
        val encodedBook = json.encodeToString(cbzBook)
        val decodedBook = json.decodeFromString<Book>(encodedBook)
        assertEquals(BookFormat.CBZ, decodedBook.format)
        assertEquals(192, decodedBook.pageCount)
        assertEquals(true, decodedBook.isManga)
        assertEquals(BookFormat.CBZ, BookFormat.fromExtension(".CBZ"))
        assertEquals(BookFormat.CBZ, BookFormat.fromMimeType("application/x-cbz"))

        val global = ReaderPreferences()
        val cbzOverride = ReaderPreferencesOverride(
            cbzReadingMode = CbzReadingMode.MANGA_RTL,
            cbzImageFit = CbzImageFit.FIT_WIDTH
        )
        val merged = global.mergeWith(cbzOverride)
        assertEquals(CbzReadingMode.MANGA_RTL, merged.cbzReadingMode)
        assertEquals(CbzImageFit.FIT_WIDTH, merged.cbzImageFit)
    }
}
