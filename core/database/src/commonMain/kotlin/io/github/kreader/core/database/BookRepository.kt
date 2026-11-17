package io.github.kreader.core.database

import io.github.kreader.core.model.Book
import io.github.kreader.core.model.BookFormat
import io.github.kreader.core.model.BookSource
import io.github.kreader.core.model.Bookmark
import io.github.kreader.core.model.CustomFont
import io.github.kreader.core.model.CustomTheme
import io.github.kreader.core.model.Highlight
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.ReaderPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.datetime.Clock

interface BookRepository {
    val activeVaultUriOrPath: StateFlow<String?>
    val activeVaultName: StateFlow<String?>

    fun getVaultName(): String?
    fun setVault(uriOrPath: String)
    fun clearVault()
    suspend fun refreshVault()

    fun observeAllBooks(): Flow<List<Book>>
    fun observeBook(id: String): Flow<Book?>
    suspend fun getBook(id: String): Book?
    suspend fun upsertBook(book: Book)
    suspend fun updateProgress(bookId: String, progression: Float, locator: Locator, timestamp: Long)
    suspend fun deleteBook(id: String)

    fun observeBookmarks(bookId: String): Flow<List<Bookmark>>
    suspend fun saveBookmark(bookmark: Bookmark)
    suspend fun deleteBookmark(id: String)

    fun observeHighlights(bookId: String): Flow<List<Highlight>>
    suspend fun saveHighlight(highlight: Highlight)
    suspend fun deleteHighlight(id: String)

    fun observeReaderPreferences(): Flow<ReaderPreferences>
    suspend fun getReaderPreferences(): ReaderPreferences
    suspend fun saveReaderPreferences(preferences: ReaderPreferences)
    fun observeEffectivePreferences(bookId: String): Flow<ReaderPreferences>
    suspend fun getEffectivePreferences(bookId: String): ReaderPreferences
    suspend fun savePreferences(bookId: String, preferences: ReaderPreferences, applyToThisBookOnly: Boolean = true)
    suspend fun resetPreferences(bookId: String)
    suspend fun resetGlobalPreferences()
    suspend fun addCustomFont(font: CustomFont)
    suspend fun deleteCustomFont(fontName: String)
    fun observeCustomThemes(): Flow<List<CustomTheme>>
    suspend fun saveCustomTheme(theme: CustomTheme)
    suspend fun deleteCustomTheme(themeId: String)
    suspend fun exportBookToMarkdown(bookId: String): String?
}

class JsonVaultBookRepository(
    val vaultStorage: VaultStorage,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher)
) : BookRepository {

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    override val activeVaultUriOrPath: StateFlow<String?> = vaultStorage.activeVaultUriOrPath

    private val _activeVaultName = MutableStateFlow(vaultStorage.getVaultName())
    override val activeVaultName: StateFlow<String?> = _activeVaultName.asStateFlow()

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    private val _preferences = MutableStateFlow(ReaderPreferences())
    private val _workspace = MutableStateFlow(VaultWorkspace())
    private val _annotations = MutableStateFlow(VaultAnnotations())
    private val _customThemes = MutableStateFlow<List<CustomTheme>>(emptyList())

    init {
        scope.launch {
            if (vaultStorage.getActiveVault() != null) {
                loadVaultState()
            }
        }
        vaultStorage.onVaultContentChanged = {
            scope.launch {
                refreshVault()
            }
        }
    }

    override fun getVaultName(): String? = vaultStorage.getVaultName()

    override fun setVault(uriOrPath: String) {
        vaultStorage.setActiveVault(uriOrPath)
        _activeVaultName.value = vaultStorage.getVaultName()
        scope.launch {
            loadVaultState()
        }
    }

    override fun clearVault() {
        vaultStorage.setActiveVault(null)
        _activeVaultName.value = null
        _books.value = emptyList()
        _workspace.value = VaultWorkspace()
        _annotations.value = VaultAnnotations()
        _customThemes.value = emptyList()
    }

    private suspend fun loadVaultState() = withContext(ioDispatcher) {
        _activeVaultName.value = vaultStorage.getVaultName()

        // 1. Load appearance.json
        val appearanceJson = vaultStorage.readConfig(APPEARANCE_FILE)
        var loadedPrefs = if (!appearanceJson.isNullOrBlank()) {
            runCatching { json.decodeFromString<ReaderPreferences>(appearanceJson) }.getOrDefault(ReaderPreferences())
        } else {
            val def = ReaderPreferences()
            vaultStorage.writeConfig(APPEARANCE_FILE, json.encodeToString(def))
            def
        }

        // 1b. Restore custom fonts from .kreader/fonts/ in vault
        val scannedFonts = vaultStorage.listFonts()
        if (scannedFonts.isNotEmpty()) {
            val existingFontsMap = loadedPrefs.customFonts.associateBy { it.name.lowercase() }
            val mergedFonts = scannedFonts.map { sf ->
                existingFontsMap[sf.name.lowercase()] ?: sf
            }
            val remainingPrefsFonts = loadedPrefs.customFonts.filterNot { ef ->
                scannedFonts.any { it.name.equals(ef.name, ignoreCase = true) }
            }
            loadedPrefs = loadedPrefs.copy(customFonts = mergedFonts + remainingPrefsFonts)
            vaultStorage.writeConfig(APPEARANCE_FILE, json.encodeToString(loadedPrefs))
        }
        _preferences.value = loadedPrefs

        // 1c. Load themes.json
        val themesJson = vaultStorage.readConfig(THEMES_FILE)
        val loadedThemes = if (!themesJson.isNullOrBlank()) {
            runCatching { json.decodeFromString<List<CustomTheme>>(themesJson) }
                .getOrElse {
                    runCatching { json.decodeFromString<VaultThemes>(themesJson).themes }.getOrDefault(emptyList())
                }
        } else {
            emptyList()
        }
        _customThemes.value = loadedThemes

        // 2. Load workspace.json
        val workspaceJson = vaultStorage.readConfig(WORKSPACE_FILE)
        val loadedWorkspace = if (!workspaceJson.isNullOrBlank()) {
            runCatching { json.decodeFromString<VaultWorkspace>(workspaceJson) }.getOrDefault(VaultWorkspace())
        } else {
            VaultWorkspace()
        }
        _workspace.value = loadedWorkspace

        // 3. Load annotations.json
        val annotationsJson = vaultStorage.readConfig(ANNOTATIONS_FILE)
        val loadedAnnotations = if (!annotationsJson.isNullOrBlank()) {
            runCatching { json.decodeFromString<VaultAnnotations>(annotationsJson) }.getOrDefault(VaultAnnotations())
        } else {
            VaultAnnotations()
        }
        _annotations.value = loadedAnnotations

        // 4. Scan EPUBs in vault
        refreshVaultInternal()
    }

    override suspend fun refreshVault(): Unit = withContext(ioDispatcher) {
        if (vaultStorage.getActiveVault() == null) return@withContext
        loadVaultState()
    }

    private suspend fun refreshVaultInternal() = withContext(ioDispatcher) {
        val epubFiles = vaultStorage.listEpubs()
        val currentWorkspace = _workspace.value.books
        val existingBooksMap = _books.value.associateBy { it.id }

        val newBooks = epubFiles.map { epub ->
            val relativePath = epub.relativePath
            val existing = existingBooksMap[relativePath]
            val progressRecord = currentWorkspace[relativePath]

            val cleanBase = relativePath.removeSuffix(".epub").removeSuffix(".EPUB").replace('/', '_').replace('\\', '_')
            val coverFileName = "$cleanBase.jpg"
            val cachedCover = existing?.coverPath ?: vaultStorage.getCoverPath(coverFileName)

            val cleanTitle = existing?.title ?: epub.displayName
                .removeSuffix(".epub")
                .removeSuffix(".EPUB")
                .replace('_', ' ')

            Book(
                id = relativePath,
                title = cleanTitle,
                author = existing?.author,
                description = existing?.description,
                coverPath = cachedCover,
                format = BookFormat.EPUB,
                source = epub.source,
                fileSizeBytes = epub.fileSizeBytes,
                lastReadTimestamp = progressRecord?.lastReadTimestamp ?: existing?.lastReadTimestamp,
                addedTimestamp = epub.lastModified.takeIf { it > 0L } ?: existing?.addedTimestamp ?: 0L,
                totalProgression = progressRecord?.totalProgression ?: existing?.totalProgression ?: 0f,
                lastLocator = progressRecord?.lastLocator ?: existing?.lastLocator
            )
        }

        _books.value = newBooks
    }

    override fun observeAllBooks(): Flow<List<Book>> = _books.asStateFlow()

    override fun observeBook(id: String): Flow<Book?> = _books.map { list -> list.find { it.id == id } }

    override suspend fun getBook(id: String): Book? = withContext(ioDispatcher) {
        _books.value.find { it.id == id }
    }

    override suspend fun upsertBook(book: Book): Unit = withContext(ioDispatcher) {
        val current = _books.value.toMutableList()
        val index = current.indexOfFirst { it.id == book.id }
        if (index >= 0) {
            current[index] = book
        } else {
            current.add(book)
        }
        _books.value = current

        // Cache extracted cover thumbnail inside vault .kreader/covers/
        val coverPath = book.coverPath
        if (coverPath != null) {
            vaultStorage.cacheExtractedCover(book.id, coverPath)
        }
    }

    override suspend fun updateProgress(
        bookId: String,
        progression: Float,
        locator: Locator,
        timestamp: Long
    ): Unit = withContext(ioDispatcher) {
        // 1. Update in-memory books
        _books.value = _books.value.map { book ->
            if (book.id == bookId) {
                book.copy(
                    totalProgression = progression,
                    lastLocator = locator,
                    lastReadTimestamp = timestamp
                )
            } else {
                book
            }
        }

        // 2. Update workspace.json
        val currentWorkspace = _workspace.value
        val updatedBooks = currentWorkspace.books.toMutableMap()
        val existingRecord = updatedBooks[bookId]
        updatedBooks[bookId] = (existingRecord ?: BookProgressRecord(relativePath = bookId)).copy(
            totalProgression = progression,
            lastReadTimestamp = timestamp,
            lastLocator = locator
        )
        val newWorkspace = currentWorkspace.copy(books = updatedBooks)
        _workspace.value = newWorkspace

        vaultStorage.writeConfig(WORKSPACE_FILE, json.encodeToString(newWorkspace))
    }

    override suspend fun deleteBook(id: String): Unit = withContext(ioDispatcher) {
        // 1. Delete physical file from vault
        vaultStorage.deleteEpub(id)

        // 2. Delete cover thumbnail
        val cleanBase = id.removeSuffix(".epub").removeSuffix(".EPUB").replace('/', '_').replace('\\', '_')
        val coverFileName = "$cleanBase.jpg"
        vaultStorage.deleteCover(coverFileName)

        // 3. Remove from workspace.json
        val updatedWorkspace = _workspace.value.copy(
            books = _workspace.value.books.filterKeys { it != id }
        )
        _workspace.value = updatedWorkspace
        vaultStorage.writeConfig(WORKSPACE_FILE, json.encodeToString(updatedWorkspace))

        // 4. Remove from annotations.json
        val updatedAnnotations = _annotations.value.copy(
            bookmarks = _annotations.value.bookmarks.filterKeys { it != id },
            highlights = _annotations.value.highlights.filterKeys { it != id }
        )
        _annotations.value = updatedAnnotations
        vaultStorage.writeConfig(ANNOTATIONS_FILE, json.encodeToString(updatedAnnotations))

        // 5. Remove from in-memory books
        _books.value = _books.value.filterNot { it.id == id }
    }

    override fun observeBookmarks(bookId: String): Flow<List<Bookmark>> = _annotations.map { annotations ->
        annotations.bookmarks[bookId] ?: emptyList()
    }

    override suspend fun saveBookmark(bookmark: Bookmark): Unit = withContext(ioDispatcher) {
        val current = _annotations.value
        val bookMap = current.bookmarks.toMutableMap()
        val list = (bookMap[bookmark.bookId] ?: emptyList()).filterNot { it.id == bookmark.id } + bookmark
        bookMap[bookmark.bookId] = list
        val updated = current.copy(bookmarks = bookMap)
        _annotations.value = updated
        vaultStorage.writeConfig(ANNOTATIONS_FILE, json.encodeToString(updated))
    }

    override suspend fun deleteBookmark(id: String): Unit = withContext(ioDispatcher) {
        val current = _annotations.value
        val bookMap = current.bookmarks.toMutableMap()
        for ((key, list) in bookMap) {
            if (list.any { it.id == id }) {
                bookMap[key] = list.filterNot { it.id == id }
                break
            }
        }
        val updated = current.copy(bookmarks = bookMap)
        _annotations.value = updated
        vaultStorage.writeConfig(ANNOTATIONS_FILE, json.encodeToString(updated))
    }

    override fun observeHighlights(bookId: String): Flow<List<Highlight>> = _annotations.map { annotations ->
        annotations.highlights[bookId] ?: emptyList()
    }

    override suspend fun saveHighlight(highlight: Highlight): Unit = withContext(ioDispatcher) {
        val current = _annotations.value
        val bookMap = current.highlights.toMutableMap()
        val list = (bookMap[highlight.bookId] ?: emptyList()).filterNot { it.id == highlight.id } + highlight
        bookMap[highlight.bookId] = list
        val updated = current.copy(highlights = bookMap)
        _annotations.value = updated
        vaultStorage.writeConfig(ANNOTATIONS_FILE, json.encodeToString(updated))
    }

    override suspend fun deleteHighlight(id: String): Unit = withContext(ioDispatcher) {
        val current = _annotations.value
        val bookMap = current.highlights.toMutableMap()
        for ((key, list) in bookMap) {
            if (list.any { it.id == id }) {
                bookMap[key] = list.filterNot { it.id == id }
                break
            }
        }
        val updated = current.copy(highlights = bookMap)
        _annotations.value = updated
        vaultStorage.writeConfig(ANNOTATIONS_FILE, json.encodeToString(updated))
    }

    override fun observeReaderPreferences(): Flow<ReaderPreferences> = _preferences.asStateFlow()

    override suspend fun getReaderPreferences(): ReaderPreferences = withContext(ioDispatcher) {
        _preferences.value
    }

    override suspend fun saveReaderPreferences(preferences: ReaderPreferences): Unit = withContext(ioDispatcher) {
        _preferences.value = preferences
        vaultStorage.writeConfig(APPEARANCE_FILE, json.encodeToString(preferences))
    }

    override fun observeEffectivePreferences(bookId: String): Flow<ReaderPreferences> =
        combine(_preferences, _workspace) { global, ws ->
            ws.books[bookId]?.preferencesOverride ?: global
        }

    override suspend fun getEffectivePreferences(bookId: String): ReaderPreferences = withContext(ioDispatcher) {
        _workspace.value.books[bookId]?.preferencesOverride ?: _preferences.value
    }

    override suspend fun savePreferences(bookId: String, preferences: ReaderPreferences, applyToThisBookOnly: Boolean): Unit = withContext(ioDispatcher) {
        if (applyToThisBookOnly) {
            val currentWorkspace = _workspace.value
            val updatedBooks = currentWorkspace.books.toMutableMap()
            val existingRecord = updatedBooks[bookId] ?: BookProgressRecord(relativePath = bookId)
            updatedBooks[bookId] = existingRecord.copy(preferencesOverride = preferences)
            val newWorkspace = currentWorkspace.copy(books = updatedBooks)
            _workspace.value = newWorkspace
            vaultStorage.writeConfig(WORKSPACE_FILE, json.encodeToString(newWorkspace))
        } else {
            _preferences.value = preferences
            vaultStorage.writeConfig(APPEARANCE_FILE, json.encodeToString(preferences))
            val currentWorkspace = _workspace.value
            if (currentWorkspace.books[bookId]?.preferencesOverride != null) {
                val updatedBooks = currentWorkspace.books.toMutableMap()
                val existingRecord = updatedBooks[bookId]!!
                updatedBooks[bookId] = existingRecord.copy(preferencesOverride = null)
                val newWorkspace = currentWorkspace.copy(books = updatedBooks)
                _workspace.value = newWorkspace
                vaultStorage.writeConfig(WORKSPACE_FILE, json.encodeToString(newWorkspace))
            }
        }
    }

    override suspend fun resetPreferences(bookId: String): Unit = withContext(ioDispatcher) {
        val currentWorkspace = _workspace.value
        val updatedBooks = currentWorkspace.books.toMutableMap()
        val existingRecord = updatedBooks[bookId]
        if (existingRecord?.preferencesOverride != null) {
            updatedBooks[bookId] = existingRecord.copy(preferencesOverride = null)
            val newWorkspace = currentWorkspace.copy(books = updatedBooks)
            _workspace.value = newWorkspace
            vaultStorage.writeConfig(WORKSPACE_FILE, json.encodeToString(newWorkspace))
        }
    }

    override suspend fun resetGlobalPreferences(): Unit = withContext(ioDispatcher) {
        val defaultPrefs = ReaderPreferences()
        _preferences.value = defaultPrefs
        vaultStorage.writeConfig(APPEARANCE_FILE, json.encodeToString(defaultPrefs))
    }

    override suspend fun addCustomFont(font: CustomFont): Unit = withContext(ioDispatcher) {
        val persisted = vaultStorage.persistFont(font)
        val current = getReaderPreferences()
        val updatedList = current.customFonts.filterNot { it.name.equals(persisted.name, ignoreCase = true) } + persisted
        val updated = current.copy(customFonts = updatedList)
        saveReaderPreferences(updated)
    }

    override suspend fun deleteCustomFont(fontName: String): Unit = withContext(ioDispatcher) {
        vaultStorage.deleteFont(fontName)
        val current = getReaderPreferences()
        val updatedList = current.customFonts.filterNot { it.name.equals(fontName, ignoreCase = true) }
        val newTypography = if (current.typography.fontFamily.equals(fontName, ignoreCase = true)) {
            current.typography.copy(fontFamily = "Default")
        } else {
            current.typography
        }
        val updated = current.copy(customFonts = updatedList, typography = newTypography)
        saveReaderPreferences(updated)
    }

    override suspend fun exportBookToMarkdown(bookId: String): String? = withContext(ioDispatcher) {
        val book = _books.value.find { it.id == bookId } ?: return@withContext null
        val bookmarks = _annotations.value.bookmarks[bookId].orEmpty()
        val highlights = _annotations.value.highlights[bookId].orEmpty()
        val progPct = "${((book.totalProgression ?: 0f) * 100).toInt()}%"
        val nowIso = Clock.System.now().toString()

        val sb = StringBuilder()
        sb.appendLine("---")
        sb.appendLine("title: \"${book.title.replace("\"", "\\\"")}\"")
        sb.appendLine("author: \"${(book.author ?: "Unknown Author").replace("\"", "\\\"")}\"")
        sb.appendLine("progress: \"$progPct\"")
        sb.appendLine("total_bookmarks: ${bookmarks.size}")
        sb.appendLine("total_highlights: ${highlights.size}")
        sb.appendLine("exported_at: \"$nowIso\"")
        sb.appendLine("---")
        sb.appendLine()
        sb.appendLine("# ${book.title}")
        sb.appendLine("*By ${book.author ?: "Unknown Author"}*  ")
        sb.appendLine("**Reading Progress:** $progPct")
        sb.appendLine()

        // Extract and include full book chapters in spine reading order
        val fullBookContent = vaultStorage.extractEpubTextContent(book.source)
        if (!fullBookContent.isNullOrBlank()) {
            sb.appendLine(fullBookContent)
            sb.appendLine()
            sb.appendLine("---")
            sb.appendLine()
        }

        if (bookmarks.isNotEmpty()) {
            sb.appendLine("## 🔖 Bookmarks")
            sb.appendLine()
            for (b in bookmarks) {
                val chapter = b.locator.title?.takeIf { it.isNotBlank() }
                    ?: b.locator.href.substringBefore('#').substringAfterLast('/').removeSuffix(".xhtml").removeSuffix(".html").replace('_', ' ')
                val prog = ((b.locator.locations.totalProgression ?: b.locator.locations.progression ?: 0f) * 100).toInt()
                sb.appendLine("- **$chapter** (Progression: $prog%)")
                if (b.locator.locations.position != null) {
                    sb.appendLine("  - Position: ${b.locator.locations.position}")
                }
            }
            sb.appendLine()
        }

        if (highlights.isNotEmpty()) {
            sb.appendLine("## 💡 Highlights & Notes")
            sb.appendLine()
            for (h in highlights) {
                val chapter = h.locator.title?.takeIf { it.isNotBlank() }
                    ?: h.locator.href.substringBefore('#').substringAfterLast('/').removeSuffix(".xhtml").removeSuffix(".html").replace('_', ' ')
                val quote = h.locator.text?.highlight ?: ""
                val prog = ((h.locator.locations.totalProgression ?: h.locator.locations.progression ?: 0f) * 100).toInt()

                sb.appendLine("### $chapter")
                if (quote.isNotBlank()) {
                    quote.lines().forEach { line ->
                        sb.appendLine("> $line")
                    }
                    sb.appendLine()
                }
                if (!h.annotation.isNullOrBlank()) {
                    sb.appendLine("- **Note:** ${h.annotation}")
                }
                sb.appendLine("- **Progression:** $prog% | **Color:** `${h.colorHex}`")
                sb.appendLine()
            }
        }

        val cleanTitle = book.title
            .replace("[^a-zA-Z0-9_ \\u0600-\\u06FF-]".toRegex(), "")
            .trim()
            .ifEmpty { "Book_Notes" }

        vaultStorage.exportMarkdown("$cleanTitle.md", sb.toString())
    }

    override fun observeCustomThemes(): Flow<List<CustomTheme>> = _customThemes.asStateFlow()

    override suspend fun saveCustomTheme(theme: CustomTheme): Unit = withContext(ioDispatcher) {
        val current = _customThemes.value.toMutableList()
        val index = current.indexOfFirst { it.id == theme.id }
        if (index >= 0) {
            current[index] = theme
        } else {
            current.add(theme)
        }
        _customThemes.value = current
        vaultStorage.writeConfig(THEMES_FILE, json.encodeToString(current))
    }

    override suspend fun deleteCustomTheme(themeId: String): Unit = withContext(ioDispatcher) {
        val current = _customThemes.value.filterNot { it.id == themeId }
        _customThemes.value = current
        vaultStorage.writeConfig(THEMES_FILE, json.encodeToString(current))
    }

    companion object {
        const val APPEARANCE_FILE = "appearance.json"
        const val WORKSPACE_FILE = "workspace.json"
        const val ANNOTATIONS_FILE = "annotations.json"
        const val THEMES_FILE = "themes.json"
    }
}
