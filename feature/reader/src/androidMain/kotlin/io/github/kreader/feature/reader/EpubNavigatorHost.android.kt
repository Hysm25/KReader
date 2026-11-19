package io.github.kreader.feature.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import io.github.kreader.core.engine.TocItem
import io.github.kreader.core.model.Book
import io.github.kreader.core.model.CustomFont
import io.github.kreader.core.model.Highlight
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ReaderTypography
import io.github.kreader.core.model.ThemeMode
import io.github.kreader.core.model.isRtlLanguage
import io.github.kreader.core.model.isRtlText
import io.github.kreader.engine.epub.ReadiumEpubOpener
import io.github.kreader.engine.epub.ReadiumFontResources
import io.github.kreader.engine.epub.toDomain
import io.github.kreader.engine.epub.toReadium
import io.github.kreader.engine.epub.toTocItem
import java.io.File
import java.util.Collections
import java.util.WeakHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.DelicateReadiumApi
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.LocatorCollection
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.publication.services.search.SearchIterator
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.util.Try

private const val HIGHLIGHTS_GROUP = "highlights"
private const val SEARCH_GROUP = "search-highlight"
private const val SEARCH_DECORATION_ID = "search-current"

@OptIn(ExperimentalReadiumApi::class, DelicateReadiumApi::class)
@Composable
actual fun EpubNavigatorHost(
    book: Book,
    initialLocator: Locator?,
    theme: ReaderTheme,
    typography: ReaderTypography,
    isScrollMode: Boolean,
    isTwoPageSpread: Boolean,
    controller: EpubNavigatorController,
    customFonts: List<CustomFont>,
    highlights: List<Highlight>,
    onHighlightClicked: (String) -> Unit,
    onAddHighlight: (id: String, locator: Locator, colorHex: String) -> Unit,
    onAddNoteRequested: (Locator, String) -> Unit,
    onProgressChanged: (progression: Float, locator: Locator) -> Unit,
    onTableOfContentsLoaded: (List<TocItem>) -> Unit,
    onDirectionDetected: (isRtl: Boolean) -> Unit,
    onTap: () -> Unit,
    onPageCountLoaded: (Int) -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val fragmentActivity = remember(context) { context.findFragmentActivity() }
    val scope = rememberCoroutineScope()

    val currentOnProgressChanged by rememberUpdatedState(onProgressChanged)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnHighlightClicked by rememberUpdatedState(onHighlightClicked)
    val currentOnAddHighlight by rememberUpdatedState(onAddHighlight)
    val currentOnAddNoteRequested by rememberUpdatedState(onAddNoteRequested)
    val currentOnTableOfContentsLoaded by rememberUpdatedState(onTableOfContentsLoaded)
    val currentOnDirectionDetected by rememberUpdatedState(onDirectionDetected)
    val currentOnPageCountLoaded by rememberUpdatedState(onPageCountLoaded)

    var publication by remember(book.id) { mutableStateOf<Publication?>(null) }
    var navigator by remember(book.id) { mutableStateOf<EpubNavigatorFragment?>(null) }
    var isNavigatorReady by remember(book.id) { mutableStateOf(false) }
    var detectedRtl by remember(book.id) { mutableStateOf<Boolean?>(null) }
    var isLoading by remember(book.id) { mutableStateOf(true) }
    var errorMessage by remember(book.id) { mutableStateOf<String?>(null) }

    val opener = remember(context) { ReadiumEpubOpener(context.applicationContext) }

    // Imported fonts are part of the publication resource container, so changing them requires
    // opening a new publication. Keep the key stable across recompositions.
    val fontsKey = remember(customFonts) {
        customFonts.joinToString("|") { "${it.name}:${it.path}" }
    }

    // 1. Open the publication and resolve its metadata, TOC and position count off the UI thread.
    LaunchedEffect(book.id, fontsKey) {
        isLoading = true
        errorMessage = null
        isNavigatorReady = false
        navigator = null
        publication = null
        detectedRtl = null

        withContext(Dispatchers.IO) {
            opener.open(book.source, book.id, customFonts)
        }.fold(
            onSuccess = { opened ->
                val (rtl, toc, pageCount) = withContext(Dispatchers.Default) {
                    val readingProgression = opened.metadata.readingProgression
                    val hasRtlProgression =
                        readingProgression == org.readium.r2.shared.publication.ReadingProgression.RTL
                    val hasRtlLanguage = opened.metadata.languages.any { isRtlLanguage(it) }
                    val hasRtlText = isRtlText(opened.metadata.title) ||
                        isRtlText(book.title) ||
                        isRtlText(book.author.orEmpty()) ||
                        isRtlText(book.description.orEmpty()) ||
                        opened.tableOfContents.any { isRtlText(it.title.orEmpty()) }

                    Triple(
                        hasRtlProgression || hasRtlLanguage || hasRtlText,
                        opened.tableOfContents.map { it.toTocItem(opened) },
                        runCatching { opened.positions().size }.getOrDefault(0)
                    )
                }

                publication = opened
                detectedRtl = rtl
                currentOnDirectionDetected(rtl)
                currentOnTableOfContentsLoaded(toc)
                if (pageCount > 0) {
                    currentOnPageCountLoaded(pageCount)
                }
                isLoading = false
            },
            onFailure = { error ->
                errorMessage = error.message ?: "Failed to open EPUB"
                isLoading = false
            }
        )
    }

    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val effectiveRtl = detectedRtl == true

    val preferences = remember(theme, typography, isScrollMode, book.id, effectiveRtl, isSystemDark) {
        buildReadiumPreferences(
            theme = theme,
            typography = typography,
            isScrollMode = isScrollMode,
            isRtl = effectiveRtl,
            isSystemInDarkTheme = isSystemDark
        )
    }

    // 2. Submit official Readium preferences whenever they change.
    var submittedPreferences by remember(book.id) {
        mutableStateOf<org.readium.r2.navigator.epub.EpubPreferences?>(null)
    }
    LaunchedEffect(navigator, isNavigatorReady, preferences) {
        val fragment = navigator ?: return@LaunchedEffect
        if (!isNavigatorReady || !fragment.isAdded) return@LaunchedEffect
        if (submittedPreferences == preferences) return@LaunchedEffect

        runCatching { fragment.submitPreferences(preferences) }
            .onSuccess { submittedPreferences = preferences }
    }

    // 3. Wire the imperative controller to official navigator APIs.
    DisposableEffect(navigator, publication, isNavigatorReady) {
        val fragment = navigator
        val pub = publication

        fun ready(): EpubNavigatorFragment? =
            fragment?.takeIf { isNavigatorReady && it.isAdded && it.context != null }

        fun clearSearchHighlight(nav: EpubNavigatorFragment) {
            scope.launch {
                runCatching { nav.applyDecorations(emptyList(), group = SEARCH_GROUP) }
            }
        }

        controller.goTo = { locator ->
            ready()?.let { nav ->
                clearSearchHighlight(nav)
                nav.go(locator.toReadium(), animated = true)
            }
        }
        controller.goToSearchResult = { locator ->
            ready()?.let { nav ->
                val readiumLocator = locator.toReadium()
                nav.go(readiumLocator, animated = false)
                scope.launch {
                    nav.applyDecorations(
                        listOf(searchDecoration(readiumLocator)),
                        group = SEARCH_GROUP
                    )
                }
            }
        }
        controller.goNext = {
            ready()?.let { nav ->
                clearSearchHighlight(nav)
                nav.goForward(animated = true)
            }
        }
        controller.goPrevious = {
            ready()?.let { nav ->
                clearSearchHighlight(nav)
                nav.goBackward(animated = true)
            }
        }
        controller.seekToProgression = { targetProgression ->
            ready()?.let { nav ->
                clearSearchHighlight(nav)
                scope.launch {
                    val target = targetProgression.toDouble().coerceIn(0.0, 1.0)
                    val locator = pub?.let { opened ->
                        runCatching { opened.locateProgression(target) }.getOrNull()
                            ?: opened.readingOrder.takeIf { it.isNotEmpty() }?.let { order ->
                                val index = (target * (order.size - 1)).toInt()
                                    .coerceIn(0, order.size - 1)
                                opened.locatorFromLink(order[index])
                            }
                    }
                    if (locator != null) {
                        nav.go(locator, animated = false)
                    }
                }
            }
        }
        controller.clearSelection = {
            ready()?.clearSelection()
        }
        controller.highlightSearchResult = { locator ->
            ready()?.let { nav ->
                val readiumLocator = locator.toReadium()
                scope.launch {
                    nav.applyDecorations(
                        listOf(searchDecoration(readiumLocator)),
                        group = SEARCH_GROUP
                    )
                }
            }
        }
        controller.clearSearchHighlight = {
            ready()?.let { nav ->
                scope.launch {
                    nav.applyDecorations(emptyList(), group = SEARCH_GROUP)
                }
            }
        }
        controller.search = { query ->
            withContext(Dispatchers.IO) {
                val results = mutableListOf<Locator>()
                if (pub != null && query.isNotBlank()) {
                    val iterator = pub.search(query).unwrapReadium<SearchIterator>()
                    if (iterator != null) {
                        try {
                            var count = 0
                            while (count < SEARCH_RESULT_LIMIT) {
                                val page = iterator.next().unwrapReadium<LocatorCollection>()
                                    ?: break
                                if (page.locators.isEmpty()) break
                                page.locators.forEach { locator ->
                                    results += locator.toDomain()
                                    count++
                                }
                            }
                        } finally {
                            iterator.close()
                        }
                    }
                }
                results
            }
        }

        onDispose {
            controller.goTo = null
            controller.goToSearchResult = null
            controller.goNext = null
            controller.goPrevious = null
            controller.seekToProgression = null
            controller.clearSelection = null
            controller.highlightSearchResult = null
            controller.clearSearchHighlight = null
            controller.search = null
        }
    }

    // 4. Render highlights through the official decoration API. Decorations are retained by the
    //    navigator and re-applied to every resource, including chapters loaded later.
    val isDarkTheme = when (theme.mode) {
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemDark
        else -> false
    }
    val isSepia = theme.mode == ThemeMode.SEPIA
    LaunchedEffect(navigator, isNavigatorReady, highlights, isDarkTheme, isSepia) {
        val fragment = navigator ?: return@LaunchedEffect
        if (!isNavigatorReady || !fragment.isAdded) return@LaunchedEffect

        val decorations = highlights.map { highlight ->
            Decoration(
                id = highlight.id,
                locator = highlight.locator.toReadium(),
                style = Decoration.Style.Highlight(
                    tint = calculateHighlightTintColor(highlight.colorHex, isDarkTheme, isSepia),
                    isActive = false
                )
            )
        }
        runCatching { fragment.applyDecorations(decorations, group = HIGHLIGHTS_GROUP) }
    }

    // 5. Observe the navigator's official current locator. It already carries the position and
    //    total progression resolved by Readium's PositionsService.
    LaunchedEffect(navigator, isNavigatorReady) {
        val fragment = navigator ?: return@LaunchedEffect
        if (!isNavigatorReady) return@LaunchedEffect

        var lastKey: String? = null
        fragment.currentLocator.collect { readiumLocator ->
            val locator = readiumLocator.toDomain()
            val progression = locator.locations.totalProgression
                ?: locator.locations.progression
                ?: return@collect
            val key = "${locator.href}|${locator.locations.position}|$progression"
            if (key != lastKey) {
                lastKey = key
                currentOnProgressChanged(progression.coerceIn(0f, 1f), locator)
            }
        }
    }

    when {
        isLoading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        errorMessage != null -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Error opening publication: $errorMessage",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        publication != null && fragmentActivity != null -> {
            val pub = publication!!
            val containerId = remember(book.id) { View.generateViewId() }
            val fragmentTag = remember(book.id) { "epub_nav_${book.id}" }
            val configuredFragments = remember(book.id) {
                Collections.newSetFromMap(WeakHashMap<EpubNavigatorFragment, Boolean>())
            }

            var activeSelection by remember { mutableStateOf<ActiveSelection?>(null) }
            val selectionCallback = remember(scope) {
                SelectionActionModeCallback(
                    scope = scope,
                    fragmentProvider = { navigator },
                    onSelectionActive = { selection -> activeSelection = selection },
                    onSelectionDismissed = { mode ->
                        if (activeSelection?.actionMode == mode) {
                            activeSelection = null
                        }
                    }
                )
            }

            fun dismissSelection() {
                val selection = activeSelection ?: return
                activeSelection = null
                navigator?.clearSelection()
                selection.actionMode?.finish()
            }

            fun attach(fragment: EpubNavigatorFragment) {
                if (!configuredFragments.add(fragment)) return

                fragment.addInputListener(
                    DirectionalNavigationAdapter(navigator = fragment, animatedTransition = true)
                )
                fragment.addInputListener(object : InputListener {
                    override fun onTap(event: TapEvent): Boolean {
                        if (activeSelection != null) {
                            dismissSelection()
                            return true
                        }
                        controller.clearSearchHighlight?.invoke()
                        currentOnTap()
                        return true
                    }
                })
                fragment.addDecorationListener(
                    HIGHLIGHTS_GROUP,
                    object : DecorableNavigator.Listener {
                        override fun onDecorationActivated(
                            event: DecorableNavigator.OnActivatedEvent
                        ): Boolean {
                            currentOnHighlightClicked(event.decoration.id)
                            return true
                        }
                    }
                )
            }

            DisposableEffect(fragmentActivity, fragmentTag, pub) {
                val fragmentManager = fragmentActivity.supportFragmentManager

                val lifecycleCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
                    override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
                        if (f.tag == fragmentTag && f is EpubNavigatorFragment) {
                            attach(f)
                            navigator = f
                            isNavigatorReady = true
                        }
                    }

                    override fun onFragmentStopped(fm: FragmentManager, f: Fragment) {
                        if (f.tag == fragmentTag && navigator === f) {
                            isNavigatorReady = false
                        }
                    }

                    override fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) {
                        if (f.tag == fragmentTag && navigator === f) {
                            isNavigatorReady = false
                            navigator = null
                            configuredFragments.remove(f)
                        }
                    }
                }

                fragmentManager.registerFragmentLifecycleCallbacks(lifecycleCallbacks, false)

                val existing = (fragmentManager.findFragmentByTag(fragmentTag)
                    ?: fragmentManager.findFragmentById(containerId)) as? EpubNavigatorFragment
                if (existing != null &&
                    existing.isAdded &&
                    existing.context != null &&
                    existing.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                ) {
                    attach(existing)
                    navigator = existing
                    isNavigatorReady = true
                }

                onDispose {
                    fragmentManager.unregisterFragmentLifecycleCallbacks(lifecycleCallbacks)
                    if (!fragmentActivity.isFinishing && !fragmentActivity.isDestroyed) {
                        val fragment = fragmentManager.findFragmentByTag(fragmentTag)
                            ?: fragmentManager.findFragmentById(containerId)
                        if (fragment != null) {
                            runCatching {
                                fragmentManager.beginTransaction()
                                    .remove(fragment)
                                    .commitNowAllowingStateLoss()
                            }
                        }
                    }
                    isNavigatorReady = false
                    navigator = null
                }
            }

            BoxWithConstraints(modifier = modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        FragmentContainerView(ctx).apply {
                            id = containerId
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { view ->
                        runCatching {
                            val fragmentManager = fragmentActivity.supportFragmentManager
                            if (fragmentManager.isDestroyed || fragmentActivity.isFinishing) {
                                return@runCatching
                            }

                            var fragment = (fragmentManager.findFragmentByTag(fragmentTag)
                                ?: fragmentManager.findFragmentById(view.id)) as? EpubNavigatorFragment

                            if (fragment != null &&
                                (!fragment.isAdded || fragment.isDetached || fragment.id != view.id)
                            ) {
                                fragmentManager.beginTransaction()
                                    .remove(fragment)
                                    .commitNowAllowingStateLoss()
                                fragment = null
                            }

                            if (fragment == null) {
                                val configuration = EpubNavigatorFragment.Configuration(
                                    selectionActionModeCallback = selectionCallback,
                                    shouldApplyInsetsPadding = false
                                ).apply {
                                    useReadiumCssFontSize = false
                                    customFonts
                                        .filter { File(it.path).isFile }
                                        .forEach { font ->
                                            addFontFamilyDeclaration(FontFamily(font.name)) {
                                                addFontFace {
                                                    addSource(ReadiumFontResources.url(font))
                                                }
                                            }
                                        }
                                }

                                val factory = EpubNavigatorFactory(pub).createFragmentFactory(
                                    initialLocator = initialLocator?.toReadium(),
                                    initialPreferences = preferences,
                                    configuration = configuration
                                )
                                // The factory is only needed to instantiate this fragment; restoring
                                // it keeps the host FragmentManager untouched for other screens.
                                val previousFactory = fragmentManager.fragmentFactory
                                fragmentManager.fragmentFactory = factory
                                try {
                                    fragmentManager.beginTransaction()
                                        .replace(
                                            view.id,
                                            EpubNavigatorFragment::class.java,
                                            null,
                                            fragmentTag
                                        )
                                        .commitNowAllowingStateLoss()
                                } finally {
                                    fragmentManager.fragmentFactory = previousFactory
                                }
                            }
                        }.onFailure { error ->
                            errorMessage = "Failed to initialize reader: ${error.message}"
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                SelectionMenu(
                    selection = activeSelection,
                    theme = theme,
                    isSystemInDarkTheme = isSystemDark,
                    onHighlight = { colorHex ->
                        val selection = activeSelection
                        if (selection != null) {
                            val id = "hl_${System.currentTimeMillis()}_${(1000..9999).random()}"
                            currentOnAddHighlight(id, selection.domainLocator, colorHex)
                            dismissSelection()
                        }
                    },
                    onAddNote = {
                        val selection = activeSelection
                        if (selection != null) {
                            val text = selection.text.ifBlank {
                                selection.domainLocator.text?.highlight.orEmpty()
                            }
                            currentOnAddNoteRequested(selection.domainLocator, text)
                            dismissSelection()
                        }
                    },
                    onCopy = {
                        val selection = activeSelection
                        if (selection != null) {
                            val text = selection.text.ifBlank {
                                selection.domainLocator.text?.highlight.orEmpty()
                            }
                            if (text.isNotEmpty()) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                    as? ClipboardManager
                                clipboard?.setPrimaryClip(
                                    ClipData.newPlainText("KReader Selection", text)
                                )
                            }
                            dismissSelection()
                        }
                    }
                )
            }
        }
    }
}

private const val SEARCH_RESULT_LIMIT = 100

private fun searchDecoration(locator: org.readium.r2.shared.publication.Locator): Decoration =
    Decoration(
        id = SEARCH_DECORATION_ID,
        locator = locator,
        style = Decoration.Style.Highlight(
            tint = android.graphics.Color.argb(160, 255, 179, 0),
            isActive = true
        )
    )

private data class SelectionRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

private data class ActiveSelection(
    val readiumLocator: org.readium.r2.shared.publication.Locator,
    val domainLocator: Locator,
    val text: String,
    val rect: SelectionRect?,
    val actionMode: ActionMode?
)

private data class HighlightColorSwatch(
    val hex: String,
    val color: androidx.compose.ui.graphics.Color,
    val name: String
)

@Composable
private fun SelectionMenu(
    selection: ActiveSelection?,
    theme: ReaderTheme,
    isSystemInDarkTheme: Boolean,
    onHighlight: (String) -> Unit,
    onAddNote: () -> Unit,
    onCopy: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val swatches = rememberThemeHarmonizedSwatches(theme, isSystemInDarkTheme)
        val menuWidth = (swatches.size * 34 + 110).dp
        val menuHeight = 52.dp
        val rect = selection?.rect

        val (rawMenuX, rawMenuY) = remember(rect, maxWidth, maxHeight, density, menuWidth) {
            if (rect != null) {
                val leftDp = with(density) { rect.left.toDp() }
                val rightDp = with(density) { rect.right.toDp() }
                val topDp = with(density) { rect.top.toDp() }
                val bottomDp = with(density) { rect.bottom.toDp() }

                val centerX = (leftDp + rightDp) / 2
                val x = (centerX - menuWidth / 2).coerceIn(
                    12.dp,
                    (maxWidth - menuWidth - 12.dp).coerceAtLeast(12.dp)
                )
                val y = if (topDp >= menuHeight + 16.dp) {
                    (topDp - menuHeight - 10.dp).coerceAtLeast(12.dp)
                } else {
                    (bottomDp + 10.dp).coerceAtMost(
                        (maxHeight - menuHeight - 12.dp).coerceAtLeast(12.dp)
                    )
                }
                x to y
            } else {
                ((maxWidth - menuWidth) / 2).coerceAtLeast(12.dp) to 72.dp
            }
        }

        val menuX by animateDpAsState(
            targetValue = rawMenuX,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "selectionMenuX"
        )
        val menuY by animateDpAsState(
            targetValue = rawMenuY,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "selectionMenuY"
        )

        AnimatedVisibility(
            visible = selection != null,
            enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.88f, animationSpec = tween(150)),
            exit = fadeOut(tween(100)) + scaleOut(targetScale = 0.88f, animationSpec = tween(100)),
            modifier = Modifier.offset(x = menuX, y = menuY)
        ) {
            if (selection != null) {
                Material3SelectionMenu(
                    swatches = swatches,
                    onHighlight = onHighlight,
                    onAddNote = onAddNote,
                    onCopy = onCopy
                )
            }
        }
    }
}

@Composable
private fun rememberThemeHarmonizedSwatches(
    theme: ReaderTheme,
    isSystemInDarkTheme: Boolean
): List<HighlightColorSwatch> {
    val isDark = when (theme.mode) {
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme
        else -> false
    }
    val isSepia = theme.mode == ThemeMode.SEPIA

    return remember(theme.mode, isDark, isSepia) {
        when {
            isSepia -> listOf(
                HighlightColorSwatch("#F59E0B", androidx.compose.ui.graphics.Color(0xFFF59E0B), "Warm Amber"),
                HighlightColorSwatch("#84CC16", androidx.compose.ui.graphics.Color(0xFF84CC16), "Warm Olive"),
                HighlightColorSwatch("#0D9488", androidx.compose.ui.graphics.Color(0xFF0D9488), "Vintage Teal"),
                HighlightColorSwatch("#E11D48", androidx.compose.ui.graphics.Color(0xFFE11D48), "Rose"),
                HighlightColorSwatch("#78350F", androidx.compose.ui.graphics.Color(0xFF78350F), "Espresso Ink")
            )
            isDark -> listOf(
                HighlightColorSwatch("#FACC15", androidx.compose.ui.graphics.Color(0xFFFACC15), "Goldenrod"),
                HighlightColorSwatch("#4ADE80", androidx.compose.ui.graphics.Color(0xFF4ADE80), "Emerald Mint"),
                HighlightColorSwatch("#38BDF8", androidx.compose.ui.graphics.Color(0xFF38BDF8), "Sky Cyan"),
                HighlightColorSwatch("#F472B6", androidx.compose.ui.graphics.Color(0xFFF472B6), "Soft Rose"),
                HighlightColorSwatch("#A5B4FC", androidx.compose.ui.graphics.Color(0xFFA5B4FC), "Luminous Iris")
            )
            else -> listOf(
                HighlightColorSwatch("#FEF08A", androidx.compose.ui.graphics.Color(0xFFFEF08A), "Canary Yellow"),
                HighlightColorSwatch("#BBF7D0", androidx.compose.ui.graphics.Color(0xFFBBF7D0), "Mint Green"),
                HighlightColorSwatch("#BAE6FD", androidx.compose.ui.graphics.Color(0xFFBAE6FD), "Sky Blue"),
                HighlightColorSwatch("#FBCFE8", androidx.compose.ui.graphics.Color(0xFFFBCFE8), "Pastel Pink"),
                HighlightColorSwatch("#334155", androidx.compose.ui.graphics.Color(0xFF334155), "Deep Slate Ink")
            )
        }
    }
}

private fun calculateHighlightTintColor(colorHex: String, isDark: Boolean, isSepia: Boolean): Int {
    val base = try {
        android.graphics.Color.parseColor(colorHex)
    } catch (_: Exception) {
        android.graphics.Color.YELLOW
    }
    val r = android.graphics.Color.red(base)
    val g = android.graphics.Color.green(base)
    val b = android.graphics.Color.blue(base)

    val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0

    val alpha = when {
        isDark -> if (luminance > 0.65) 75 else 95
        isSepia -> if (luminance < 0.35) 55 else 82
        else -> if (luminance < 0.35) 48 else 88
    }
    return android.graphics.Color.argb(alpha, r, g, b)
}

private class SelectionActionModeCallback(
    private val scope: CoroutineScope,
    private val fragmentProvider: () -> EpubNavigatorFragment?,
    private val onSelectionActive: (ActiveSelection) -> Unit,
    private val onSelectionDismissed: (ActionMode?) -> Unit
) : ActionMode.Callback {

    private var updateJob: Job? = null

    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        menu?.clear()
        mode?.title = ""
        mode?.subtitle = ""
        fetchSelection(mode, attempts = 12)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        menu?.clear()
        fetchSelection(mode, attempts = 2)
        return false
    }

    private fun fetchSelection(mode: ActionMode?, attempts: Int) {
        updateJob?.cancel()
        updateJob = scope.launch {
            repeat(attempts) { attempt ->
                val fragment = fragmentProvider() ?: return@launch
                val selection = fragment.currentSelection()
                if (selection != null) {
                    val rect = selection.rect
                    onSelectionActive(
                        ActiveSelection(
                            readiumLocator = selection.locator,
                            domainLocator = selection.locator.toDomain(),
                            text = selection.locator.text.highlight.orEmpty(),
                            rect = rect?.let { SelectionRect(it.left, it.top, it.right, it.bottom) },
                            actionMode = mode
                        )
                    )
                    // The selection is already known; a later handle movement refreshes the
                    // position through onPrepareActionMode.
                    if (rect != null) return@launch
                }

                if (attempt < attempts - 1) {
                    delay(20)
                }
            }
        }
    }

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean = false

    override fun onDestroyActionMode(mode: ActionMode?) {
        updateJob?.cancel()
        onSelectionDismissed(mode)
    }
}

@Composable
private fun Material3SelectionMenu(
    swatches: List<HighlightColorSwatch>,
    onHighlight: (colorHex: String) -> Unit,
    onAddNote: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            swatches.forEach { swatch ->
                Surface(
                    onClick = { onHighlight(swatch.hex) },
                    shape = CircleShape,
                    color = swatch.color,
                    modifier = Modifier.size(28.dp),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                ) {}
            }

            VerticalDivider(
                modifier = Modifier
                    .height(24.dp)
                    .padding(horizontal = 2.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )

            IconButton(onClick = onAddNote, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Notes,
                    contentDescription = "Add note",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = "Copy text",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private inline fun <reified T> Any?.unwrapReadium(): T? {
    if (this is T) return this
    if (this is Try.Success<*, *>) return this.value as? T
    return null
}
