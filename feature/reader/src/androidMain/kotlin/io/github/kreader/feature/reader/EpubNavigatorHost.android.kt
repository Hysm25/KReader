package io.github.kreader.feature.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.util.Base64
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.core.view.doOnLayout
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
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
import io.github.kreader.core.model.isRtlLanguage
import io.github.kreader.core.model.isRtlText
import io.github.kreader.engine.epub.ReadiumEpubOpener
import io.github.kreader.engine.epub.toDomain
import io.github.kreader.engine.epub.toReadium
import io.github.kreader.engine.epub.toTocItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.LocatorCollection
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.publication.services.search.SearchIterator
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.util.Try
import java.io.File
import java.util.Collections
import java.util.WeakHashMap

@OptIn(ExperimentalReadiumApi::class)
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

    var publication by remember { mutableStateOf<Publication?>(null) }
    var currentFragment by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
    var isNavigatorReady by remember { mutableStateOf(false) }
    var detectedRtl by remember { mutableStateOf<Boolean?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var lastSearchHighlightTimestamp by remember(book.id) { mutableLongStateOf(0L) }
    var lastSearchProgression by remember(book.id) { mutableStateOf<Float?>(null) }
    var totalPositionsCount by remember(book.id) { mutableIntStateOf(0) }

    val searchLocatorsCache = remember(book.id) {
        Collections.synchronizedMap(LinkedHashMap<String, org.readium.r2.shared.publication.Locator>())
    }

    val configuredFragments = remember(book.id) {
        Collections.newSetFromMap(WeakHashMap<EpubNavigatorFragment, Boolean>())
    }

    val opener = remember(context) { ReadiumEpubOpener(context.applicationContext) }

    // 1. Open publication and auto-detect reading direction (backgrounded on IO to prevent UI stutters)
    LaunchedEffect(book.id) {
        isLoading = true
        errorMessage = null
        isNavigatorReady = false
        currentFragment = null
        withContext(Dispatchers.IO) {
            opener.open(book.source, book.id)
        }.fold(
            onSuccess = { pub ->
                publication = pub
                val (isPubRtl, toc, pageCount) = withContext(Dispatchers.Default) {
                    val readingProg = pub.metadata.readingProgression
                    val hasRtlProgression = readingProg == org.readium.r2.shared.publication.ReadingProgression.RTL
                    val hasRtlLanguage = pub.metadata.languages.any { isRtlLanguage(it) }
                    val hasRtlText = isRtlText(pub.metadata.title) ||
                        isRtlText(book.title) ||
                        isRtlText(book.author.orEmpty()) ||
                        isRtlText(book.description.orEmpty()) ||
                        pub.tableOfContents.any { isRtlText(it.title.orEmpty()) }

                    val rtl = hasRtlProgression || hasRtlLanguage || hasRtlText
                    val items = pub.tableOfContents.map { it.toTocItem(pub) }
                    val count = runCatching { pub.positions().size }.getOrDefault(0)
                    Triple(rtl, items, count)
                }
                detectedRtl = isPubRtl
                totalPositionsCount = pageCount
                currentOnDirectionDetected(isPubRtl)
                currentOnTableOfContentsLoaded(toc)
                if (pageCount > 0) {
                    currentOnPageCountLoaded(pageCount)
                }
                isLoading = false
            },
            onFailure = { err ->
                errorMessage = err.message ?: "Failed to open EPUB"
                isLoading = false
            }
        )
    }

    // 2. Build preferences (automatic reading direction + custom font support + system dark mode)
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

    // Pre-warm font cache immediately in IO thread for instant custom font rendering
    LaunchedEffect(customFonts) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            for (f in customFonts) {
                runCatching {
                    val file = File(f.path)
                    if (file.exists() && file.length() > 0L) {
                        fontBase64Cache.getOrPut(f.path) {
                            val fontBytes = file.readBytes()
                            val b64 = Base64.encodeToString(fontBytes, Base64.NO_WRAP)
                            val fmt = if (file.extension.equals("otf", ignoreCase = true)) "opentype" else "truetype"
                            fmt to b64
                        }
                    }
                }
            }
        }
    }

    // One-shot realignment once navigator is ready to resolve initial padding overlap glitch & enforce full width
    LaunchedEffect(isNavigatorReady) {
        if (isNavigatorReady) {
            val frag = currentFragment ?: return@LaunchedEffect
            kotlinx.coroutines.delay(80)
            runCatching {
                if (frag.isAdded && frag.context != null) {
                    val loc = frag.currentLocator.value
                    frag.go(loc, animated = false)
                    frag.view?.requestLayout()
                }
            }
        }
    }

    // Re-snap when inter-page gap/margins change
    LaunchedEffect(typography.paddingHorizontal, typography.paddingVertical) {
        val frag = currentFragment ?: return@LaunchedEffect
        if (isNavigatorReady && frag.isAdded && frag.context != null) {
            runCatching {
                kotlinx.coroutines.delay(60)
                val loc = frag.currentLocator.value
                frag.go(loc, animated = false)
            }
        }
    }

    // 3. Submit preferences on change (deferred until fragment is attached and ready, without redundant submissions)
    var lastSubmittedPreferences by remember(book.id) { mutableStateOf<org.readium.r2.navigator.epub.EpubPreferences?>(null) }
    LaunchedEffect(currentFragment, preferences, isNavigatorReady) {
        val frag = currentFragment ?: return@LaunchedEffect
        if (isNavigatorReady && frag.isAdded && frag.context != null) {
            if (lastSubmittedPreferences != preferences) {
                runCatching {
                    frag.submitPreferences(preferences)
                    lastSubmittedPreferences = preferences
                    injectReaderStyles(frag)
                }
            }
        }
    }

    // 4. Inject custom @font-face CSS if user selected an imported font (cached & debounced)
    var lastInjectedFontName by remember(book.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(currentFragment, typography.fontFamily, customFonts, isNavigatorReady) {
        val frag = currentFragment ?: return@LaunchedEffect
        if (isNavigatorReady && frag.isAdded && frag.context != null) {
            val fontName = typography.fontFamily
            if (fontName != lastInjectedFontName) {
                val selectedCustomFont = customFonts.find { it.name.equals(fontName, ignoreCase = true) }
                if (selectedCustomFont != null) {
                    injectFontFace(frag, selectedCustomFont)
                } else {
                    clearFontFace(frag)
                }
                lastInjectedFontName = fontName
            }
        }
    }

    // 5. Wire controller navigation commands & search
    DisposableEffect(currentFragment, publication, isNavigatorReady) {
        controller.goTo = { locator ->
            val frag = currentFragment
            if (isNavigatorReady && frag != null && frag.isAdded && frag.context != null) {
                val key = locatorCacheKey(locator)
                val target = searchLocatorsCache[key] ?: locator.toReadium()
                frag.go(target, animated = true)
            }
        }
        controller.goToSearchResult = { domainLoc ->
            val frag = currentFragment
            if (isNavigatorReady && frag != null && frag.isAdded && frag.context != null) {
                val key = locatorCacheKey(domainLoc)
                val readiumLoc = searchLocatorsCache[key] ?: domainLoc.toReadium()
                lastSearchProgression = domainLoc.locations.totalProgression ?: domainLoc.locations.progression
                lastSearchHighlightTimestamp = System.currentTimeMillis()
                frag.go(readiumLoc, animated = false)
                scope.launch {
                    kotlinx.coroutines.delay(120)
                    lastSearchHighlightTimestamp = System.currentTimeMillis()
                    val searchDeco = Decoration(
                        id = "search_match_${System.currentTimeMillis()}",
                        locator = readiumLoc,
                        style = Decoration.Style.Highlight(
                            tint = android.graphics.Color.argb(160, 255, 179, 0),
                            isActive = true
                        )
                    )
                    frag.applyDecorations(listOf(searchDeco), group = "search-highlight")
                }
            }
        }
        controller.goNext = {
            val frag = currentFragment
            if (isNavigatorReady && frag != null && frag.isAdded && frag.context != null) {
                frag.goForward(animated = true)
            }
        }
        controller.goPrevious = {
            val frag = currentFragment
            if (isNavigatorReady && frag != null && frag.isAdded && frag.context != null) {
                frag.goBackward(animated = true)
            }
        }
        controller.seekToProgression = { targetProg ->
            val frag = currentFragment
            val pub = publication
            if (isNavigatorReady && frag != null && frag.isAdded && frag.context != null && pub != null) {
                scope.launch {
                    val targetDouble = targetProg.toDouble().coerceIn(0.0, 1.0)
                    val readiumLocator = runCatching {
                        pub.locateProgression(targetDouble)
                    }.getOrNull()

                    if (readiumLocator != null) {
                        frag.go(readiumLocator, animated = false)
                    } else if (pub.readingOrder.isNotEmpty()) {
                        val order = pub.readingOrder
                        val index = (targetDouble * (order.size - 1)).toInt().coerceIn(0, order.size - 1)
                        val link = order[index]
                        val baseLocator = pub.locatorFromLink(link)
                            ?: Locator(
                                href = link.href.toString(),
                                type = link.mediaType?.toString() ?: "application/xhtml+xml"
                            ).toReadium()
                        frag.go(baseLocator, animated = false)
                    }
                }
            }
        }
        controller.clearSelection = {
            val frag = currentFragment
            if (isNavigatorReady && frag != null && frag.isAdded && frag.context != null) {
                frag.clearSelection()
            }
        }
        controller.highlightSearchResult = { locator ->
            val frag = currentFragment
            if (isNavigatorReady && frag != null && frag.isAdded && frag.context != null) {
                val key = locatorCacheKey(locator)
                val target = searchLocatorsCache[key] ?: locator.toReadium()
                lastSearchHighlightTimestamp = System.currentTimeMillis()
                scope.launch {
                    val searchDeco = Decoration(
                        id = "search_match_${System.currentTimeMillis()}",
                        locator = target,
                        style = Decoration.Style.Highlight(
                            tint = android.graphics.Color.argb(160, 255, 179, 0),
                            isActive = true
                        )
                    )
                    frag.applyDecorations(listOf(searchDeco), group = "search-highlight")
                }
            }
        }
        controller.clearSearchHighlight = {
            val frag = currentFragment
            if (isNavigatorReady && frag != null && frag.isAdded && frag.context != null) {
                scope.launch {
                    frag.applyDecorations(emptyList(), group = "search-highlight")
                }
            }
        }
        controller.search = { query ->
            val pub = publication
            val results = mutableListOf<Locator>()
            if (pub != null) {
                val searchIterator = pub.search(query).unwrapReadium<SearchIterator>()
                if (searchIterator != null) {
                    try {
                        var count = 0
                        while (count < 100) {
                            val page = searchIterator.next().unwrapReadium<LocatorCollection>()
                            if (page == null || page.locators.isEmpty()) break
                            for (loc in page.locators) {
                                val domainLoc = loc.toDomain()
                                val key = locatorCacheKey(domainLoc)
                                searchLocatorsCache[key] = loc
                                results.add(domainLoc)
                                count++
                            }
                        }
                    } finally {
                        searchIterator.close()
                    }
                }
            }
            results
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

    // 6. Host in FragmentContainerView
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

            val activeDecorations = remember { mutableStateListOf<Decoration>() }
            var activeSelection by remember { mutableStateOf<ActiveSelection?>(null) }

            val selectionCallback = remember(scope) {
                SelectionActionModeCallback(
                    scope = scope,
                    fragmentProvider = { currentFragment },
                    onSelectionActive = { sel ->
                        activeSelection = sel
                    },
                    onSelectionDismissed = {
                        activeSelection = null
                    }
                )
            }

            // Register FragmentLifecycleCallbacks to safely configure listeners AFTER fragment is STARTED
            DisposableEffect(fragmentActivity, fragmentTag) {
                val fm = fragmentActivity.supportFragmentManager
                val lifecycleCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
                    override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
                        if (f.tag == fragmentTag && f is EpubNavigatorFragment) {
                            scope.launch {
                                injectReaderStyles(f)
                                val fontName = typography.fontFamily
                                val selectedCustomFont = customFonts.find { it.name.equals(fontName, ignoreCase = true) }
                                if (selectedCustomFont != null) {
                                    injectFontFace(f, selectedCustomFont)
                                }
                            }
                        }
                    }

                    override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
                        if (f.tag == fragmentTag && f is EpubNavigatorFragment && f.isAdded && f.context != null) {
                            if (!configuredFragments.contains(f)) {
                                configuredFragments.add(f)
                                f.addInputListener(
                                    DirectionalNavigationAdapter(
                                        navigator = f,
                                        animatedTransition = true
                                    )
                                )
                                f.addInputListener(object : InputListener {
                                    override fun onTap(event: TapEvent): Boolean {
                                        if (activeSelection != null) {
                                            f.clearSelection()
                                            activeSelection?.actionMode?.finish()
                                            activeSelection = null
                                            return true
                                        }
                                        controller.clearSearchHighlight?.invoke()
                                        currentOnTap()
                                        return true
                                    }
                                })
                                f.addDecorationListener(
                                    "highlights",
                                    object : DecorableNavigator.Listener {
                                        override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
                                            currentOnHighlightClicked(event.decoration.id)
                                            return true
                                        }
                                    }
                                )
                            }
                            scope.launch {
                                injectReaderStyles(f)
                                val fontName = typography.fontFamily
                                val selectedCustomFont = customFonts.find { it.name.equals(fontName, ignoreCase = true) }
                                if (selectedCustomFont != null) {
                                    injectFontFace(f, selectedCustomFont)
                                }
                            }
                            isNavigatorReady = true
                            if (currentFragment !== f) {
                                currentFragment = f
                            }
                        }
                    }

                    override fun onFragmentStopped(fm: FragmentManager, f: Fragment) {
                        if (f.tag == fragmentTag) {
                            isNavigatorReady = false
                        }
                    }

                    override fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) {
                        if (f.tag == fragmentTag) {
                            isNavigatorReady = false
                            if (currentFragment === f) {
                                currentFragment = null
                            }
                        }
                    }
                }

                fm.registerFragmentLifecycleCallbacks(lifecycleCallbacks, false)

                val existing = (fm.findFragmentByTag(fragmentTag) ?: fm.findFragmentById(containerId)) as? EpubNavigatorFragment
                if (existing != null && existing.isAdded && existing.context != null &&
                    existing.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                ) {
                    if (!configuredFragments.contains(existing)) {
                        configuredFragments.add(existing)
                        existing.addInputListener(
                            DirectionalNavigationAdapter(
                                navigator = existing,
                                animatedTransition = true
                            )
                        )
                        existing.addInputListener(object : InputListener {
                            override fun onTap(event: TapEvent): Boolean {
                                if (activeSelection != null) {
                                    existing.clearSelection()
                                    activeSelection?.actionMode?.finish()
                                    activeSelection = null
                                    return true
                                }
                                controller.clearSearchHighlight?.invoke()
                                currentOnTap()
                                return true
                            }
                        })
                        existing.addDecorationListener(
                            "highlights",
                            object : DecorableNavigator.Listener {
                                override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
                                    currentOnHighlightClicked(event.decoration.id)
                                    return true
                                }
                            }
                        )
                    }
                    isNavigatorReady = true
                    currentFragment = existing
                }

                onDispose {
                    fm.unregisterFragmentLifecycleCallbacks(lifecycleCallbacks)
                    if (!fragmentActivity.isFinishing && !fragmentActivity.isDestroyed) {
                        val frag = fm.findFragmentByTag(fragmentTag) ?: fm.findFragmentById(containerId)
                        if (frag != null) {
                            runCatching {
                                fm.beginTransaction().remove(frag).commitNowAllowingStateLoss()
                            }
                        }
                    }
                    isNavigatorReady = false
                    currentFragment = null
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
                            var lastW = -1
                            var lastH = -1
                            doOnLayout { view ->
                                if (view.width > 0 && view.height > 0 && (view.width != lastW || view.height != lastH)) {
                                    val isFirstLayout = lastW == -1
                                    lastW = view.width
                                    lastH = view.height
                                    if (isFirstLayout) {
                                        runCatching {
                                            val fm = fragmentActivity.supportFragmentManager
                                            val frag = (fm.findFragmentByTag(fragmentTag) ?: fm.findFragmentById(view.id)) as? EpubNavigatorFragment
                                            if (frag != null && frag.isAdded && frag.context != null) {
                                                frag.view?.post {
                                                    runCatching {
                                                        val loc = frag.currentLocator.value
                                                        frag.go(loc, animated = false)
                                                        frag.view?.requestLayout()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    update = { view ->
                        runCatching {
                            val fm = fragmentActivity.supportFragmentManager
                            if (fm.isDestroyed || fragmentActivity.isFinishing) return@runCatching

                            var fragment = (fm.findFragmentByTag(fragmentTag) ?: fm.findFragmentById(view.id)) as? EpubNavigatorFragment

                            if (fragment != null && (!fragment.isAdded || fragment.isDetached || fragment.id != view.id)) {
                                fm.beginTransaction().remove(fragment).commitNowAllowingStateLoss()
                                fragment = null
                            }

                            if (fragment == null) {
                                val factory = EpubNavigatorFactory(pub)
                                val fragmentFactory = factory.createFragmentFactory(
                                    initialLocator = initialLocator?.toReadium(),
                                    initialPreferences = preferences,
                                    configuration = EpubNavigatorFragment.Configuration(
                                        selectionActionModeCallback = selectionCallback
                                    )
                                )
                                fm.fragmentFactory = fragmentFactory
                                fm.beginTransaction()
                                    .replace(view.id, EpubNavigatorFragment::class.java, null, fragmentTag)
                                    .commitNowAllowingStateLoss()
                            }
                        }.onFailure { e ->
                            errorMessage = "Failed to initialize reader: ${e.message}"
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Material 3 Floating Selection Menu
                val density = LocalDensity.current
                val currentSel = activeSelection
                val rect = currentSel?.rect

                val menuWidth = 264.dp
                val menuHeight = 52.dp
                val containerWidth = maxWidth
                val containerHeight = maxHeight

                val (menuX, menuY) = remember(rect, containerWidth, containerHeight, density) {
                    if (rect != null) {
                        val leftDp = with(density) { rect.left.toDp() }
                        val rightDp = with(density) { rect.right.toDp() }
                        val topDp = with(density) { rect.top.toDp() }
                        val bottomDp = with(density) { rect.bottom.toDp() }

                        val centerX = (leftDp + rightDp) / 2
                        val x = (centerX - menuWidth / 2).coerceIn(12.dp, (containerWidth - menuWidth - 12.dp).coerceAtLeast(12.dp))

                        val y = if (topDp >= menuHeight + 16.dp) {
                            (topDp - menuHeight - 10.dp).coerceAtLeast(12.dp)
                        } else {
                            (bottomDp + 10.dp).coerceAtMost((containerHeight - menuHeight - 12.dp).coerceAtLeast(12.dp))
                        }
                        x to y
                    } else {
                        val x = ((containerWidth - menuWidth) / 2).coerceAtLeast(12.dp)
                        val y = 72.dp
                        x to y
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = currentSel != null,
                    enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(150)) +
                            androidx.compose.animation.scaleIn(initialScale = 0.88f, animationSpec = androidx.compose.animation.core.tween(150)),
                    exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(100)) +
                           androidx.compose.animation.scaleOut(targetScale = 0.88f, animationSpec = androidx.compose.animation.core.tween(100)),
                    modifier = Modifier.offset(x = menuX, y = menuY)
                ) {
                    if (currentSel != null) {
                        Material3SelectionMenu(
                            selection = currentSel,
                            onHighlight = { colorHex ->
                                val id = "hl_${System.currentTimeMillis()}_${(1000..9999).random()}"
                                currentOnAddHighlight(id, currentSel.domainLocator, colorHex)
                                val baseColor = try {
                                    android.graphics.Color.parseColor(colorHex)
                                } catch (_: Exception) {
                                    android.graphics.Color.YELLOW
                                }
                                val tintColor = android.graphics.Color.argb(
                                    77,
                                    android.graphics.Color.red(baseColor),
                                    android.graphics.Color.green(baseColor),
                                    android.graphics.Color.blue(baseColor)
                                )
                                val deco = Decoration(
                                    id = id,
                                    locator = currentSel.readiumLocator,
                                    style = Decoration.Style.Highlight(tint = tintColor, isActive = false)
                                )
                                val frag = currentFragment
                                if (frag != null && frag.isAdded && frag.context != null) {
                                    scope.launch {
                                        activeDecorations.add(deco)
                                        frag.applyDecorations(activeDecorations.toList(), group = "highlights")
                                    }
                                }
                                currentFragment?.clearSelection()
                                currentSel.actionMode?.finish()
                                activeSelection = null
                            },
                            onAddNote = {
                                val text = currentSel.text.ifBlank {
                                    currentSel.domainLocator.text?.highlight ?: ""
                                }
                                currentOnAddNoteRequested(currentSel.domainLocator, text)
                                currentFragment?.clearSelection()
                                currentSel.actionMode?.finish()
                                activeSelection = null
                            },
                            onCopy = {
                                val text = currentSel.text.ifBlank {
                                    currentSel.domainLocator.text?.highlight ?: ""
                                }
                                if (text.isNotEmpty()) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clip = ClipData.newPlainText("KReader Selection", text)
                                    clipboard?.setPrimaryClip(clip)
                                }
                                currentFragment?.clearSelection()
                                currentSel.actionMode?.finish()
                                activeSelection = null
                            }
                        )
                    }
                }
            }

            // Render Highlights as Decorations (guarded by isNavigatorReady & isAdded with subtle ~0.30f pastel alpha, strictly Highlight)
            LaunchedEffect(currentFragment, highlights, isNavigatorReady) {
                val frag = currentFragment ?: return@LaunchedEffect
                if (isNavigatorReady && frag.isAdded && frag.context != null) {
                    runCatching {
                        val decorations = highlights.map { h ->
                            val baseColor = try {
                                android.graphics.Color.parseColor(h.colorHex)
                            } catch (_: Exception) {
                                android.graphics.Color.YELLOW
                            }
                            // Apply soft translucent pastel transparency (~0.30f / 77 alpha int)
                            val tintColor = android.graphics.Color.argb(
                                77,
                                android.graphics.Color.red(baseColor),
                                android.graphics.Color.green(baseColor),
                                android.graphics.Color.blue(baseColor)
                            )
                            Decoration(
                                id = h.id,
                                locator = h.locator.toReadium(),
                                style = Decoration.Style.Highlight(tint = tintColor, isActive = false)
                            )
                        }
                        activeDecorations.clear()
                        activeDecorations.addAll(decorations)
                        frag.applyDecorations(activeDecorations.toList(), group = "highlights")
                    }
                }
            }

            // Observe currentLocator for progress persistence (deferred until fragment is STARTED)
            LaunchedEffect(currentFragment, isNavigatorReady) {
                val frag = currentFragment ?: return@LaunchedEffect
                if (!isNavigatorReady || !frag.isAdded || frag.context == null) return@LaunchedEffect
                if (!frag.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@LaunchedEffect

                var lastSpineHref: String? = null
                var lastProgression: Float? = null
                frag.currentLocator.collect { readiumLocator ->
                    if (frag.isAdded && frag.context != null) {
                        // Avoid redundant font re-injection on every page turn within the same chapter
                        val currHref = readiumLocator.href.toString().substringBefore('#')
                        if (currHref != lastSpineHref) {
                            lastSpineHref = currHref
                            injectReaderStyles(frag)
                            val selectedFont = customFonts.find { it.name.equals(typography.fontFamily, ignoreCase = true) }
                            if (selectedFont != null) {
                                injectFontFace(frag, selectedFont)
                            }
                        }

                        runCatching {
                            val domainLocator = readiumLocator.toDomain()
                            val prog: Float = domainLocator.locations.totalProgression
                                ?: run {
                                    val pub = publication
                                    if (pub != null && pub.readingOrder.isNotEmpty()) {
                                        val order = pub.readingOrder
                                        val spineIndex = order.indexOfFirst { link ->
                                            link.href.toString().substringBefore('#') == readiumLocator.href.toString().substringBefore('#')
                                        }
                                        if (spineIndex >= 0) {
                                            val chapterProg = (readiumLocator.locations.progression ?: 0.0).coerceIn(0.0, 1.0)
                                            ((spineIndex + chapterProg) / order.size).toFloat().coerceIn(0f, 1f)
                                        } else null
                                    } else null
                                }
                                ?: (domainLocator.locations.progression ?: 0f)

                            val pos = domainLocator.locations.position
                                ?: if (totalPositionsCount > 0 && prog > 0f) {
                                    ((prog * totalPositionsCount).toInt() + 1).coerceIn(1, totalPositionsCount)
                                } else null

                            val updatedLocator = domainLocator.copy(
                                locations = domainLocator.locations.copy(
                                    totalProgression = prog,
                                    position = pos ?: domainLocator.locations.position
                                )
                            )

                            if (lastProgression != null && kotlin.math.abs(prog - (lastProgression ?: 0f)) > 0.0001f) {
                                val isSearchJump = lastSearchProgression != null && kotlin.math.abs(prog - (lastSearchProgression ?: 0f)) < 0.005f
                                if (!isSearchJump && System.currentTimeMillis() - lastSearchHighlightTimestamp > 3000L) {
                                    controller.clearSearchHighlight?.invoke()
                                }
                            }
                            lastProgression = prog
                            currentOnProgressChanged(prog, updatedLocator)
                        }
                    }
                }
            }
        }
    }
}

private val fontBase64Cache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>()

private suspend fun injectFontFace(frag: EpubNavigatorFragment, font: CustomFont) {
    runCatching {
        val cssJs = withContext(Dispatchers.IO) {
            val file = File(font.path)
            if (!file.exists()) return@withContext null
            val (format, base64) = fontBase64Cache.getOrPut(font.path) {
                val fontBytes = file.readBytes()
                val b64 = Base64.encodeToString(fontBytes, Base64.NO_WRAP)
                val fmt = if (file.extension.equals("otf", ignoreCase = true)) "opentype" else "truetype"
                fmt to b64
            }
            val contentSelectors = "body, p, h1, h2, h3, h4, h5, h6, li, blockquote"
            """
                (function() {
                    var el = document.getElementById('kreader-custom-font');
                    if (!el) {
                        el = document.createElement('style');
                        el.id = 'kreader-custom-font';
                        (document.head || document.documentElement).appendChild(el);
                    }
                    el.textContent = '@font-face { font-family: "${font.name}"; src: url("data:font/$format;charset=utf-8;base64,$base64"); } $contentSelectors { font-family: "${font.name}", sans-serif !important; }';
                })();
            """.trimIndent()
        } ?: return
        frag.evaluateJavascript(cssJs)
        frag.view?.let { root ->
            findWebViews(root).forEach { wv ->
                wv.evaluateJavascript(cssJs, null)
            }
        }
    }
}

private suspend fun clearFontFace(frag: EpubNavigatorFragment) {
    runCatching {
        val js = """
            (function() {
                var el = document.getElementById('kreader-custom-font');
                if (el) { el.remove(); }
            })();
        """.trimIndent()
        frag.evaluateJavascript(js)
        frag.view?.let { root ->
            findWebViews(root).forEach { wv ->
                wv.evaluateJavascript(js, null)
            }
        }
    }
}

private fun locatorCacheKey(loc: Locator): String {
    return "${loc.href}__${loc.locations.progression}__${loc.locations.totalProgression}__${loc.locations.position}__${loc.text?.highlight}__${loc.text?.before}"
}

private suspend fun injectReaderStyles(frag: EpubNavigatorFragment) {
    runCatching {
        val cssJs = """
            (function() {
                var el = document.getElementById('kreader-reader-styles');
                if (!el) {
                    el = document.createElement('style');
                    el.id = 'kreader-reader-styles';
                    (document.head || document.documentElement).appendChild(el);
                }
                el.textContent = 'hr { margin-left: auto !important; margin-right: auto !important; text-align: center !important; }';
            })();
        """.trimIndent()
        frag.evaluateJavascript(cssJs)
        frag.view?.let { root ->
            findWebViews(root).forEach { wv ->
                wv.evaluateJavascript(cssJs, null)
            }
        }
    }
}

private fun findWebViews(view: View): List<WebView> {
    val list = mutableListOf<WebView>()
    if (view is WebView) {
        list.add(view)
    } else if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            list.addAll(findWebViews(view.getChildAt(i)))
        }
    }
    return list
}

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

private class SelectionActionModeCallback(
    private val scope: CoroutineScope,
    private val fragmentProvider: () -> EpubNavigatorFragment?,
    private val onSelectionActive: (ActiveSelection) -> Unit,
    private val onSelectionDismissed: () -> Unit
) : ActionMode.Callback {

    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        menu?.clear()
        mode?.title = ""
        mode?.subtitle = ""
        val fragment = fragmentProvider() ?: return true
        scope.launch {
            val selection = fragment.currentSelection()
            if (selection != null) {
                val rect = selection.rect?.let { r ->
                    SelectionRect(r.left, r.top, r.right, r.bottom)
                }
                onSelectionActive(
                    ActiveSelection(
                        readiumLocator = selection.locator,
                        domainLocator = selection.locator.toDomain(),
                        text = selection.locator.text.highlight ?: "",
                        rect = rect,
                        actionMode = mode
                    )
                )
            }
        }
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        menu?.clear()
        val fragment = fragmentProvider() ?: return false
        scope.launch {
            val selection = fragment.currentSelection()
            if (selection != null) {
                val rect = selection.rect?.let { r ->
                    SelectionRect(r.left, r.top, r.right, r.bottom)
                }
                onSelectionActive(
                    ActiveSelection(
                        readiumLocator = selection.locator,
                        domainLocator = selection.locator.toDomain(),
                        text = selection.locator.text.highlight ?: "",
                        rect = rect,
                        actionMode = mode
                    )
                )
            }
        }
        return false
    }

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean = false

    override fun onDestroyActionMode(mode: ActionMode?) {
        onSelectionDismissed()
    }
}

@Composable
private fun Material3SelectionMenu(
    selection: ActiveSelection,
    onHighlight: (colorHex: String) -> Unit,
    onAddNote: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val highlightColors = remember {
        listOf(
            "#FEF08A", // Yellow
            "#BBF7D0", // Green
            "#BAE6FD", // Blue
            "#FBCFE8"  // Pink
        )
    }

    ElevatedCard(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 8.dp
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            highlightColors.forEach { colorHex ->
                val parsedColor = remember(colorHex) {
                    androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(colorHex))
                }
                Surface(
                    onClick = { onHighlight(colorHex) },
                    shape = CircleShape,
                    color = parsedColor,
                    modifier = Modifier.size(28.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {}
            }

            VerticalDivider(
                modifier = Modifier
                    .height(24.dp)
                    .padding(horizontal = 2.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )

            IconButton(
                onClick = onAddNote,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Notes,
                    contentDescription = "Add note",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(36.dp)
            ) {
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
