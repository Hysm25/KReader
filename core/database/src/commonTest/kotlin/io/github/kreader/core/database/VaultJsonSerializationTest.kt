package io.github.kreader.core.database

import io.github.kreader.core.model.Bookmark
import io.github.kreader.core.model.CustomTheme
import io.github.kreader.core.model.Highlight
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.PageLayout
import io.github.kreader.core.model.ReaderPreferences
import io.github.kreader.core.model.ReaderPreferencesOverride
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ReaderTypography
import io.github.kreader.core.model.ReaderTypographyOverride
import io.github.kreader.core.model.ThemeMode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VaultJsonSerializationTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    @Test
    fun testWorkspaceJsonRoundTrip() {
        val workspace = VaultWorkspace(
            books = mapOf(
                "Fiction/Book1.epub" to BookProgressRecord(
                    relativePath = "Fiction/Book1.epub",
                    totalProgression = 0.65f,
                    lastReadTimestamp = 1700000000000L,
                    lastLocator = Locator.initial("ch2.xhtml", "application/xhtml+xml"),
                    preferencesOverride = ReaderPreferencesOverride(
                        theme = ReaderTheme(mode = ThemeMode.DARK),
                        typography = ReaderTypographyOverride(fontFamily = "Amiri")
                    )
                )
            )
        )

        val encoded = json.encodeToString(workspace)
        val decoded = json.decodeFromString<VaultWorkspace>(encoded)

        assertEquals(1, decoded.books.size)
        val record = decoded.books["Fiction/Book1.epub"]
        assertTrue(record != null)
        assertEquals(0.65f, record.totalProgression)
        assertEquals("ch2.xhtml", record.lastLocator?.href)
        assertEquals(ThemeMode.DARK, record.preferencesOverride?.theme?.mode)
        assertEquals("Amiri", record.preferencesOverride?.typography?.fontFamily)
        assertEquals(null, record.preferencesOverride?.typography?.fontSizeSp)
    }

    @Test
    fun testAnnotationsJsonRoundTrip() {
        val locator = Locator.initial("ch1.xhtml", "application/xhtml+xml")
        val annotations = VaultAnnotations(
            bookmarks = mapOf(
                "BookA.epub" to listOf(
                    Bookmark(
                        id = "bm_1",
                        bookId = "BookA.epub",
                        locator = locator,
                        title = "Interesting Section",
                        createdTimestamp = 1700000000000L
                    )
                )
            ),
            highlights = mapOf(
                "BookA.epub" to listOf(
                    Highlight(
                        id = "hl_1",
                        bookId = "BookA.epub",
                        locator = locator,
                        colorHex = "#FFEB3B",
                        annotation = "My note",
                        createdTimestamp = 1700000000000L,
                        modifiedTimestamp = 1700000000000L
                    )
                )
            )
        )

        val encoded = json.encodeToString(annotations)
        val decoded = json.decodeFromString<VaultAnnotations>(encoded)

        assertEquals(1, decoded.bookmarks["BookA.epub"]?.size)
        assertEquals(1, decoded.highlights["BookA.epub"]?.size)
        assertEquals("My note", decoded.highlights["BookA.epub"]?.first()?.annotation)
        assertEquals("#FFEB3B", decoded.highlights["BookA.epub"]?.first()?.colorHex)
    }

    @Test
    fun testAppearanceJsonRoundTrip() {
        val preferences = ReaderPreferences(
            theme = ReaderTheme(mode = ThemeMode.AMOLED, backgroundColorHex = "#000000", textColorHex = "#E0E0E0"),
            typography = ReaderTypography(fontFamily = "Literata", fontSizeSp = 17f, paddingHorizontal = 20),
            pageLayout = PageLayout.SINGLE,
            isScrollMode = false
        )

        val encoded = json.encodeToString(preferences)
        val decoded = json.decodeFromString<ReaderPreferences>(encoded)

        assertEquals(ThemeMode.AMOLED, decoded.theme.mode)
        assertEquals("#000000", decoded.theme.backgroundColorHex)
        assertEquals("Literata", decoded.typography.fontFamily)
        assertEquals(20, decoded.typography.paddingHorizontal)
    }

    @Test
    fun testThemesJsonRoundTrip() {
        val customThemes = listOf(
            CustomTheme(
                id = "theme_forest",
                name = "Forest Green",
                backgroundColorHex = "#1B2E1E",
                textColorHex = "#D0E8D0",
                accentColorHex = "#4CAF50"
            )
        )

        val encoded = json.encodeToString(customThemes)
        val decoded = json.decodeFromString<List<CustomTheme>>(encoded)

        assertEquals(1, decoded.size)
        assertEquals("Forest Green", decoded.first().name)
        assertEquals("#1B2E1E", decoded.first().backgroundColorHex)
    }
}
