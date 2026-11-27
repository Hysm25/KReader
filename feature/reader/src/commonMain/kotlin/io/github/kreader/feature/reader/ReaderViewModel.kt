package io.github.kreader.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.github.kreader.core.database.BookRepository
import io.github.kreader.core.engine.TocItem
import io.github.kreader.core.model.Book
import io.github.kreader.core.model.BookFormat
import io.github.kreader.core.model.Bookmark
import io.github.kreader.core.model.CbzImageFit
import io.github.kreader.core.model.CbzReadingMode
import io.github.kreader.core.model.CustomFont
import io.github.kreader.core.model.CustomTheme
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.OrientationLock
import io.github.kreader.core.model.ReaderPreferences
import io.github.kreader.core.model.ReaderPreferencesOverride
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ReaderTypography
import io.github.kreader.core.model.ReaderTypographyOverride
import io.github.kreader.core.model.isRtlText
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class ReaderViewModel(
    private val bookRepository: BookRepository,
    val book: Book,
    private val scope: CoroutineScope
) {
    private val _uiState = MutableStateFlow(
        ReaderUiState(
            book = book,
            currentLocator = book.lastLocator,
            totalProgression = book.totalProgression,
            isRtl = if (book.format == BookFormat.CBZ) {
                book.isManga == true
            } else {
                isRtlText(book.title) || isRtlText(book.author.orEmpty()) || isRtlText(book.description.orEmpty())
            },
            totalPageCount = book.pageCount ?: 0,
            cbzReadingMode = if (book.format == BookFormat.CBZ && book.isManga == true) {
                CbzReadingMode.MANGA_RTL
            } else {
                CbzReadingMode.COMIC_LTR
            }
        )
    )
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var currentEffectivePreferences = ReaderPreferences()
    private var globalPreferences = ReaderPreferences()
    private var typographySaveJob: Job? = null
    private var pendingTypography: ReaderTypography? = null
    private var cbzMangaHintApplied = false

    init {
        observeData()
    }

    private fun observeData() {
        bookRepository.observeReaderPreferences().onEach { prefs ->
            globalPreferences = prefs
        }.launchIn(scope)

        bookRepository.observeEffectivePreferences(book.id).onEach { prefs ->
            currentEffectivePreferences = prefs
            val isFirstOpenCbzManga = book.format == BookFormat.CBZ &&
                book.lastLocator == null &&
                book.totalProgression == 0f &&
                book.isManga == true &&
                !cbzMangaHintApplied
            val effectiveCbzMode = if (isFirstOpenCbzManga) {
                CbzReadingMode.MANGA_RTL
            } else {
                prefs.cbzReadingMode
            }
            _uiState.update { current ->
                current.copy(
                    theme = prefs.theme,
                    typography = pendingTypography ?: prefs.typography,
                    isScrollMode = prefs.isScrollMode,
                    pageLayout = prefs.pageLayout,
                    keepScreenOn = prefs.keepScreenOn,
                    volumeKeyNavigation = prefs.volumeKeyNavigation,
                    orientationLock = prefs.orientationLock,
                    customFonts = prefs.customFonts,
                    cbzReadingMode = effectiveCbzMode,
                    cbzImageFit = prefs.cbzImageFit,
                    isRtl = if (book.format == BookFormat.CBZ) {
                        effectiveCbzMode == CbzReadingMode.MANGA_RTL
                    } else {
                        current.isRtl
                    }
                )
            }
        }.launchIn(scope)

        bookRepository.observeBookmarks(book.id).onEach { bookmarks ->
            _uiState.update { it.copy(bookmarks = bookmarks) }
        }.launchIn(scope)

        bookRepository.observeCustomThemes().onEach { themes ->
            _uiState.update { it.copy(customThemes = themes) }
        }.launchIn(scope)
    }

    fun toggleControls() {
        _uiState.update { it.copy(isControlsVisible = !it.isControlsVisible) }
    }

    fun openNavigationDrawer(tab: NavigationDrawerTab = NavigationDrawerTab.CONTENTS) {
        _uiState.update { it.copy(isNavigationDrawerOpen = true, isControlsVisible = false, navigationDrawerTab = tab) }
    }

    fun dismissNavigationDrawer() {
        _uiState.update { it.copy(isNavigationDrawerOpen = false) }
    }

    fun selectTab(tab: NavigationDrawerTab) {
        _uiState.update { it.copy(navigationDrawerTab = tab) }
    }

    fun openSearch() {
        _uiState.update { it.copy(isSearchOpen = true, isControlsVisible = false) }
    }

    fun dismissSearch() {
        _uiState.update { it.copy(isSearchOpen = false) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun updateSearchResults(results: List<Locator>) {
        _uiState.update { it.copy(searchResults = results) }
    }

    fun updateIsSearching(searching: Boolean) {
        _uiState.update { it.copy(isSearching = searching) }
    }

    fun openSettings() {
        _uiState.update { it.copy(isSettingsOpen = true, isControlsVisible = false) }
    }

    fun dismissSettings() {
        _uiState.update { it.copy(isSettingsOpen = false) }
    }

    fun updateProgress(progression: Float, locator: Locator) {
        val current = _uiState.value
        if (current.currentLocator == locator &&
            abs(current.totalProgression - progression) < 0.0001f
        ) {
            return
        }
        _uiState.update {
            it.copy(
                totalProgression = progression,
                currentLocator = locator
            )
        }
        scope.launch {
            val now = Clock.System.now().toEpochMilliseconds()
            bookRepository.updateProgress(book.id, progression, locator, now)
        }
    }

    fun seek(targetProgression: Float) {
        // Only update the transient UI state. The navigator emits the authoritative locator
        // (with chapter progression and position) once the jump completes.
        _uiState.update { it.copy(totalProgression = targetProgression) }
    }

    fun toggleBookmark() {
        val currentState = _uiState.value
        val currentLoc = currentState.currentLocator ?: book.lastLocator ?: Locator(href = "", type = "application/epub+zip")
        val existing = findMatchingBookmark(currentState.bookmarks, currentLoc, currentState.totalProgression)
        if (existing != null) {
            _uiState.update { it.copy(bookmarks = it.bookmarks.filterNot { b -> b.id == existing.id }) }
            scope.launch {
                bookRepository.deleteBookmark(existing.id)
            }
        } else {
            val chapterTitle = findTocTitleForHref(currentState.tocItems, currentLoc.href)
                ?: currentLoc.title
                ?: "Page ${currentLoc.locations.position ?: ""}".trim().ifEmpty {
                    "Bookmark at ${(currentState.totalProgression * 100).toInt()}%"
                }
            val newBookmark = Bookmark(
                id = "bm_${Clock.System.now().toEpochMilliseconds()}_${Random.nextInt(1000, 9999)}",
                bookId = book.id,
                locator = currentLoc,
                title = chapterTitle,
                createdTimestamp = Clock.System.now().toEpochMilliseconds()
            )
            _uiState.update { it.copy(bookmarks = it.bookmarks + newBookmark) }
            scope.launch {
                bookRepository.saveBookmark(newBookmark)
            }
        }
    }

    fun deleteBookmark(bookmarkId: String) {
        _uiState.update { current ->
            current.copy(bookmarks = current.bookmarks.filterNot { it.id == bookmarkId })
        }
        scope.launch {
            bookRepository.deleteBookmark(bookmarkId)
        }
    }

    fun changeTheme(newTheme: ReaderTheme) {
        _uiState.update { it.copy(theme = newTheme) }
        scope.launch {
            bookRepository.updateBookPreferencesOverride(book.id) { current ->
                val themeOverride = newTheme.takeIf { it != globalPreferences.theme }
                val override = (current ?: ReaderPreferencesOverride()).copy(theme = themeOverride)
                override.takeUnless { it.isEmpty() }
            }
        }
    }

    fun changeTypography(newTypography: ReaderTypography) {
        pendingTypography = newTypography
        _uiState.update { it.copy(typography = newTypography) }
        typographySaveJob?.cancel()
        typographySaveJob = scope.launch {
            // Debounce persistence so continuous slider drags update the reader live without
            // writing the vault on every frame.
            delay(200)
            withContext(NonCancellable) {
                persistTypographyOverride(newTypography)
                pendingTypography = null
            }
        }
    }

    private suspend fun persistTypographyOverride(newTypography: ReaderTypography) {
        bookRepository.updateBookPreferencesOverride(book.id) { current ->
            val global = globalPreferences.typography
            val typography = ReaderTypographyOverride(
                fontFamily = newTypography.fontFamily
                    .takeIf { it != global.fontFamily },
                fontSizeSp = newTypography.fontSizeSp
                    .takeIf { it != global.fontSizeSp },
                lineHeightMultiplier = newTypography.lineHeightMultiplier
                    .takeIf { it != global.lineHeightMultiplier },
                letterSpacingSp = newTypography.letterSpacingSp
                    .takeIf { it != global.letterSpacingSp },
                textAlignment = newTypography.textAlignment
                    .takeIf { it != global.textAlignment },
                paddingHorizontal = newTypography.paddingHorizontal
                    .takeIf { it != global.paddingHorizontal },
                paddingVertical = newTypography.paddingVertical
                    .takeIf { it != global.paddingVertical },
                overrideFont = newTypography.overrideFont
                    .takeIf { it != global.overrideFont }
            )
            val override = (current ?: ReaderPreferencesOverride()).copy(
                typography = typography.takeUnless { it.isEmpty() }
            )
            override.takeUnless { it.isEmpty() }
        }
    }

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    fun flushPendingTypography() {
        val pending = pendingTypography ?: return
        pendingTypography = null
        typographySaveJob?.cancel()
        GlobalScope.launch(Dispatchers.Default) {
            runCatching { persistTypographyOverride(pending) }
        }
    }

    fun changeScrollMode(isScroll: Boolean) {
        _uiState.update { it.copy(isScrollMode = isScroll) }
        scope.launch {
            bookRepository.updateBookPreferencesOverride(book.id) { current ->
                val scrollOverride = isScroll.takeIf { it != globalPreferences.isScrollMode }
                val override = (current ?: ReaderPreferencesOverride()).copy(isScrollMode = scrollOverride)
                override.takeUnless { it.isEmpty() }
            }
        }
    }

    fun resetToDefaults() {
        pendingTypography = null
        typographySaveJob?.cancel()
        scope.launch {
            bookRepository.resetPreferences(book.id)
        }
    }

    fun setTableOfContents(toc: List<TocItem>) {
        _uiState.update { it.copy(tocItems = toc) }
    }

    fun setTotalPageCount(count: Int) {
        _uiState.update { it.copy(totalPageCount = count) }
    }

    fun onCbzMetadataLoaded(pageCount: Int, isMangaRtl: Boolean?, detectedMode: CbzReadingMode? = null) {
        _uiState.update { it.copy(totalPageCount = pageCount) }
        if (book.format != BookFormat.CBZ) return
        scope.launch {
            val hasExplicitOverride = bookRepository.hasExplicitCbzReadingModeOverride(book.id)
            if (!hasExplicitOverride) {
                val chosenMode = detectedMode ?: if (isMangaRtl == true) CbzReadingMode.MANGA_RTL else null
                if (chosenMode != null) {
                    cbzMangaHintApplied = true
                    _uiState.update {
                        it.copy(
                            cbzReadingMode = chosenMode,
                            isRtl = chosenMode == CbzReadingMode.MANGA_RTL
                        )
                    }
                }
            }
        }
    }

    fun changeCbzReadingMode(mode: CbzReadingMode) {
        cbzMangaHintApplied = true
        _uiState.update {
            it.copy(
                cbzReadingMode = mode,
                isRtl = if (book.format == BookFormat.CBZ) mode == CbzReadingMode.MANGA_RTL else it.isRtl
            )
        }
        scope.launch {
            bookRepository.updateCbzPreferencesOverride(book.id) { current ->
                val override = (current ?: ReaderPreferencesOverride()).copy(cbzReadingMode = mode)
                override.takeUnless { it.isEmpty() }
            }
        }
    }

    fun changeCbzImageFit(fit: CbzImageFit) {
        _uiState.update { it.copy(cbzImageFit = fit) }
        scope.launch {
            bookRepository.updateCbzPreferencesOverride(book.id) { current ->
                val override = (current ?: ReaderPreferencesOverride()).copy(cbzImageFit = fit)
                override.takeUnless { it.isEmpty() }
            }
        }
    }

    fun saveCustomTheme(newTheme: CustomTheme) {
        scope.launch {
            bookRepository.saveCustomTheme(newTheme)
        }
    }

    fun deleteCustomTheme(themeId: String) {
        scope.launch {
            bookRepository.deleteCustomTheme(themeId)
        }
    }

    fun changeKeepScreenOn(enabled: Boolean) {
        _uiState.update { it.copy(keepScreenOn = enabled) }
        scope.launch {
            bookRepository.updateBookPreferencesOverride(book.id) { current ->
                val keepOverride = enabled.takeIf { it != globalPreferences.keepScreenOn }
                val override = (current ?: ReaderPreferencesOverride()).copy(keepScreenOn = keepOverride)
                override.takeUnless { it.isEmpty() }
            }
        }
    }

    fun changeVolumeKeyNavigation(enabled: Boolean) {
        _uiState.update { it.copy(volumeKeyNavigation = enabled) }
        scope.launch {
            bookRepository.updateBookPreferencesOverride(book.id) { current ->
                val volumeOverride = enabled.takeIf { it != globalPreferences.volumeKeyNavigation }
                val override = (current ?: ReaderPreferencesOverride()).copy(volumeKeyNavigation = volumeOverride)
                override.takeUnless { it.isEmpty() }
            }
        }
    }

    fun changeOrientationLock(lock: OrientationLock) {
        _uiState.update { it.copy(orientationLock = lock) }
        scope.launch {
            bookRepository.updateBookPreferencesOverride(book.id) { current ->
                val orientOverride = lock.takeIf { it != globalPreferences.orientationLock }
                val override = (current ?: ReaderPreferencesOverride()).copy(orientationLock = orientOverride)
                override.takeUnless { it.isEmpty() }
            }
        }
    }

    fun importCustomFont(font: CustomFont) {
        scope.launch {
            bookRepository.addCustomFont(font)
            val updatedTypography = _uiState.value.typography.copy(fontFamily = font.name, overrideFont = true)
            pendingTypography = null
            typographySaveJob?.cancel()
            _uiState.update { it.copy(typography = updatedTypography) }
            withContext(NonCancellable) {
                persistTypographyOverride(updatedTypography)
            }
        }
    }

    fun deleteCustomFont(fontName: String) {
        scope.launch {
            bookRepository.deleteCustomFont(fontName)
            if (_uiState.value.typography.fontFamily.equals(fontName, ignoreCase = true)) {
                val updatedTypography = _uiState.value.typography.copy(fontFamily = "Default", overrideFont = false)
                pendingTypography = null
                typographySaveJob?.cancel()
                _uiState.update { it.copy(typography = updatedTypography) }
                withContext(NonCancellable) {
                    persistTypographyOverride(updatedTypography)
                }
            }
        }
    }

    fun onDirectionDetected(isRtl: Boolean) {
        if (isRtl != _uiState.value.isRtl) {
            _uiState.update { it.copy(isRtl = isRtl) }
        }
    }

    suspend fun exportMarkdown(): String? {
        return bookRepository.exportBookToMarkdown(book.id)
    }

    val customFonts: List<CustomFont>
        get() = currentEffectivePreferences.customFonts
}

@Composable
fun rememberReaderViewModel(
    bookRepository: BookRepository,
    book: Book
): ReaderViewModel {
    val scope = rememberCoroutineScope()
    val viewModel = remember(bookRepository, book.id) {
        ReaderViewModel(bookRepository, book, scope)
    }
    DisposableEffect(viewModel) {
        onDispose {
            viewModel.flushPendingTypography()
        }
    }
    return viewModel
}
