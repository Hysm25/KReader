package io.github.kreader.feature.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
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
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ReaderTypography
import io.github.kreader.core.model.isRtlLanguage
import io.github.kreader.core.model.isRtlText
import io.github.kreader.engine.epub.ReadiumEpubOpener
import io.github.kreader.engine.epub.ReadiumFontResources
import io.github.kreader.engine.epub.ReadiumPublicationSearch
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
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.DelicateReadiumApi
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.publication.services.positions

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
    onSearchSelectedText: (String) -> Unit,
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
    val currentOnSearchSelectedText by rememberUpdatedState(onSearchSelectedText)
    val currentOnTableOfContentsLoaded by rememberUpdatedState(onTableOfContentsLoaded)
    val currentOnDirectionDetected by rememberUpdatedState(onDirectionDetected)
    val currentOnPageCountLoaded by rememberUpdatedState(onPageCountLoaded)
    val currentTypography by rememberUpdatedState(typography)
    val currentCustomFonts by rememberUpdatedState(customFonts)

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

    val preferences = remember(theme, typography, isScrollMode, book.id, effectiveRtl, isSystemDark, customFonts) {
        buildReadiumPreferences(
            theme = theme,
            typography = typography,
            isScrollMode = isScrollMode,
            isRtl = effectiveRtl,
            isSystemInDarkTheme = isSystemDark,
            customFonts = customFonts
        )
    }

    // 2. Submit official Readium preferences whenever they change.
    var submittedPreferences by remember(book.id, navigator) {
        mutableStateOf<org.readium.r2.navigator.epub.EpubPreferences?>(null)
    }
    LaunchedEffect(navigator, isNavigatorReady, preferences) {
        val fragment = navigator ?: return@LaunchedEffect
        if (!isNavigatorReady || !fragment.isAdded) return@LaunchedEffect
        if (submittedPreferences == preferences) return@LaunchedEffect

        runCatching { fragment.submitPreferences(preferences) }
            .onSuccess { submittedPreferences = preferences }
    }

    // Apply font override across all text elements (including h1-h6 headings) and typography customizations
    var lastAppliedOverrideKey by remember(book.id, navigator) { mutableStateOf<String?>(null) }
    LaunchedEffect(navigator, isNavigatorReady, typography, fontsKey) {
        val fragment = navigator ?: return@LaunchedEffect
        if (!isNavigatorReady || !fragment.isAdded || fragment.context == null) return@LaunchedEffect
        val key = "${typography.overrideFont}:${typography.fontFamily}:${typography.fontSizeSp}:${typography.lineHeightMultiplier}:${typography.textAlignment}:$fontsKey"
        if (key != lastAppliedOverrideKey) {
            applyTypographyAndFontOverrides(fragment, typography, customFonts)
            lastAppliedOverrideKey = key
            // Re-apply once after initial WebView DOM parse settles so newly attached chapters
            // never lose custom @font-face or font-scale overrides.
            delay(200)
            if (isNavigatorReady && fragment.isAdded && fragment.context != null) {
                applyTypographyAndFontOverrides(fragment, currentTypography, currentCustomFonts)
            }
        }
    }

    // 3. Wire the imperative controller to official navigator APIs.
    var searchJumpJob by remember(book.id) { mutableStateOf<Job?>(null) }
    DisposableEffect(navigator, publication, isNavigatorReady) {
        val fragment = navigator
        val pub = publication

        fun ready(): EpubNavigatorFragment? =
            fragment?.takeIf { isNavigatorReady && it.isAdded && it.context != null }

        controller.goTo = { locator ->
            ready()?.let { nav ->
                searchJumpJob?.cancel()
                nav.go(locator.toReadium(), animated = true)
            }
        }
        controller.goToSearchResult = { locator ->
            ready()?.let { nav ->
                searchJumpJob?.cancel()
                val readiumLocator = locator.toReadium()
                val currentHref = nav.currentLocator.value.href.toString().substringBefore('#')
                val targetHref = readiumLocator.href.toString().substringBefore('#')
                val isChapterChange = currentHref != targetHref

                nav.go(readiumLocator, animated = false)
                if (isChapterChange) {
                    searchJumpJob = scope.launch {
                        // Wait for target chapter WebView load, apply typography overrides,
                        // and re-snap after reflow so the exact matched page is centered.
                        delay(350)
                        val activeNav = ready() ?: return@launch
                        applyTypographyAndFontOverrides(activeNav, currentTypography, currentCustomFonts)
                        delay(150)
                        ready()?.go(readiumLocator, animated = false)
                    }
                }
            }
        }
        controller.goNext = {
            ready()?.let { nav ->
                nav.goForward(animated = true)
            }
        }
        controller.goPrevious = {
            ready()?.let { nav ->
                nav.goBackward(animated = true)
            }
        }
        controller.seekToProgression = { targetProgression ->
            ready()?.let { nav ->
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
        controller.search = { query ->
            if (pub != null && query.isNotBlank()) {
                ReadiumPublicationSearch.search(pub, query)
            } else {
                emptyList()
            }
        }

        onDispose {
            searchJumpJob?.cancel()
            controller.goTo = null
            controller.goToSearchResult = null
            controller.goNext = null
            controller.goPrevious = null
            controller.seekToProgression = null
            controller.clearSelection = null
            controller.search = null
        }
    }

    // 4. Observe the navigator's official current locator. It already carries the position and
    //    total progression resolved by Readium's PositionsService.
    var lastSpineHref by remember(book.id, navigator) { mutableStateOf<String?>(null) }
    LaunchedEffect(navigator, isNavigatorReady) {
        val fragment = navigator ?: return@LaunchedEffect
        if (!isNavigatorReady) return@LaunchedEffect

        var lastKey: String? = null
        fragment.currentLocator.collect { readiumLocator ->
            val locator = readiumLocator.toDomain()
            val currHref = locator.href.substringBefore('#')
            if (currHref != lastSpineHref) {
                lastSpineHref = currHref
                applyTypographyAndFontOverrides(fragment, currentTypography, currentCustomFonts)
            }
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
                        currentOnTap()
                        return true
                    }
                })
            }

            DisposableEffect(fragmentActivity, fragmentTag, pub) {
                val fragmentManager = fragmentActivity.supportFragmentManager

                val lifecycleCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
                    override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
                        if (f.tag == fragmentTag && f is EpubNavigatorFragment) {
                            attach(f)
                            navigator = f
                            isNavigatorReady = true
                            scope.launch {
                                applyTypographyAndFontOverrides(f, currentTypography, currentCustomFonts)
                            }
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
                    scope.launch {
                        applyTypographyAndFontOverrides(existing, currentTypography, currentCustomFonts)
                    }
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
                                        .filter { File(it.path).let { f -> f.isFile && f.length() > 0L } }
                                        .forEach { font ->
                                            addFontFamilyDeclaration(FontFamily(ReadiumFontResources.cssFamilyName(font))) {
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
                    maxWidthPx = constraints.maxWidth,
                    maxHeightPx = constraints.maxHeight,
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
                    },
                    onSearchInBook = {
                        val selection = activeSelection
                        if (selection != null) {
                            val text = selection.text.ifBlank {
                                selection.domainLocator.text?.highlight.orEmpty()
                            }.trim()
                            dismissSelection()
                            if (text.isNotEmpty()) {
                                currentOnSearchSelectedText(text)
                            }
                        }
                    }
                )
            }
        }
    }
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

@Composable
private fun SelectionMenu(
    selection: ActiveSelection?,
    maxWidthPx: Int,
    maxHeightPx: Int,
    onCopy: () -> Unit,
    onSearchInBook: () -> Unit
) {
    val density = LocalDensity.current
    var measuredWidthPx by remember { mutableIntStateOf(with(density) { 210.dp.roundToPx() }) }
    var measuredHeightPx by remember { mutableIntStateOf(with(density) { 46.dp.roundToPx() }) }

    val marginPx = with(density) { 12.dp.roundToPx() }
    val gapPx = with(density) { 12.dp.roundToPx() }

    val rect = selection?.rect
    val (menuXPx, menuYPx) = remember(rect, maxWidthPx, maxHeightPx, measuredWidthPx, measuredHeightPx) {
        if (rect != null) {
            val rawLeft = minOf(rect.left, rect.right)
            val rawRight = maxOf(rect.left, rect.right)
            val rawTop = minOf(rect.top, rect.bottom)
            val rawBottom = maxOf(rect.top, rect.bottom)

            val centerX = ((rawLeft + rawRight) / 2f).toInt()
            val maxAllowedX = (maxWidthPx - measuredWidthPx - marginPx).coerceAtLeast(marginPx)
            val x = (centerX - measuredWidthPx / 2).coerceIn(marginPx, maxAllowedX)

            val maxAllowedY = (maxHeightPx - measuredHeightPx - marginPx).coerceAtLeast(marginPx)
            val aboveY = rawTop.toInt() - measuredHeightPx - gapPx
            val belowY = rawBottom.toInt() + gapPx
            val y = when {
                aboveY >= marginPx -> aboveY.coerceIn(marginPx, maxAllowedY)
                belowY <= maxAllowedY -> belowY.coerceIn(marginPx, maxAllowedY)
                else -> marginPx
            }
            x to y
        } else {
            val x = ((maxWidthPx - measuredWidthPx) / 2).coerceAtLeast(marginPx)
            val y = with(density) { 56.dp.roundToPx() }.coerceIn(
                marginPx,
                (maxHeightPx - measuredHeightPx - marginPx).coerceAtLeast(marginPx)
            )
            x to y
        }
    }

    AnimatedVisibility(
        visible = selection != null,
        enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.92f, animationSpec = tween(120)),
        exit = fadeOut(tween(90)) + scaleOut(targetScale = 0.92f, animationSpec = tween(90)),
        modifier = Modifier.absoluteOffset { IntOffset(menuXPx, menuYPx) }
    ) {
        if (selection != null) {
            ElevatedCard(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
                modifier = Modifier.onSizeChanged { size ->
                    if (size.width > 0 && size.height > 0) {
                        measuredWidthPx = size.width
                        measuredHeightPx = size.height
                    }
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    TextButton(
                        onClick = onCopy,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Copy",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }

                    VerticalDivider(
                        modifier = Modifier.height(20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )

                    TextButton(
                        onClick = onSearchInBook,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Search",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private class SelectionActionModeCallback(
    private val scope: CoroutineScope,
    private val fragmentProvider: () -> EpubNavigatorFragment?,
    private val onSelectionActive: (ActiveSelection) -> Unit,
    private val onSelectionDismissed: (ActionMode?) -> Unit
) : ActionMode.Callback2() {

    private var updateJob: Job? = null
    private var lastPollMs: Long = 0L

    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        menu?.clear()
        mode?.title = ""
        mode?.subtitle = ""
        fetchSelection(mode, attempts = 6, stepDelayMs = 16L)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        menu?.clear()
        fetchSelection(mode, attempts = 4, stepDelayMs = 16L)
        return true
    }

    override fun onGetContentRect(mode: ActionMode?, view: View?, outRect: Rect?) {
        outRect?.set(0, 0, 0, 0)
        val now = System.currentTimeMillis()
        if (now - lastPollMs >= 60L) {
            lastPollMs = now
            fetchSelection(mode, attempts = 2, stepDelayMs = 16L)
        }
    }

    private fun fetchSelection(mode: ActionMode?, attempts: Int, stepDelayMs: Long) {
        updateJob?.cancel()
        updateJob = scope.launch {
            repeat(attempts) { attempt ->
                val fragment = fragmentProvider() ?: return@launch
                val selection = fragment.currentSelection()
                if (selection != null) {
                    val rect = selection.rect
                    val selectedText = selection.locator.text.highlight.orEmpty()
                    if (selectedText.isNotBlank() && (rect != null || attempt == attempts - 1)) {
                        onSelectionActive(
                            ActiveSelection(
                                readiumLocator = selection.locator,
                                domainLocator = selection.locator.toDomain(),
                                text = selectedText,
                                rect = rect?.let { SelectionRect(it.left, it.top, it.right, it.bottom) },
                                actionMode = mode
                            )
                        )
                        return@launch
                    }
                }
                if (attempt < attempts - 1) {
                    delay(stepDelayMs)
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

private fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun findWebViews(view: View): List<WebView> {
    val result = mutableListOf<WebView>()
    if (view is WebView) {
        result.add(view)
    } else if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            result.addAll(findWebViews(view.getChildAt(i)))
        }
    }
    return result
}

private suspend fun applyTypographyAndFontOverrides(
    frag: EpubNavigatorFragment,
    typography: ReaderTypography,
    customFonts: List<CustomFont>
) {
    runCatching {
        val js = withContext(Dispatchers.IO) {
            val activeCustomFonts = customFonts.filter { font ->
                val file = File(font.path)
                file.isFile && file.length() > 0L
            }
            val fontOverrideJs = buildFontOverrideScript(typography, activeCustomFonts)

            val textAlignRule = when (typography.textAlignment) {
                ReaderTypography.TextAlignment.JUSTIFY -> "text-align: justify !important;"
                ReaderTypography.TextAlignment.START -> "text-align: start !important;"
                ReaderTypography.TextAlignment.DEFAULT -> ""
            }

            val lineHeightRule = if (typography.lineHeightMultiplier > 0f) {
                "line-height: ${typography.lineHeightMultiplier} !important;"
            } else ""

            val typographyOverrideJs = if (textAlignRule.isNotEmpty() || lineHeightRule.isNotEmpty()) {
                """
                (function() {
                    var el = document.getElementById('kreader-typography-overrides');
                    if (!el) {
                        el = document.createElement('style');
                        el.id = 'kreader-typography-overrides';
                        (document.head || document.documentElement).appendChild(el);
                    }
                    el.textContent = `body, p, li, blockquote, div { $lineHeightRule $textAlignRule }`;
                })();
                """.trimIndent()
            } else {
                """
                (function() {
                    var el = document.getElementById('kreader-typography-overrides');
                    if (el) el.remove();
                })();
                """.trimIndent()
            }

            val fontScale = (typography.fontSizeSp / 16.0f).coerceIn(0.5f, 3.0f)
            val fontNormalizeJs = EpubStyleNormalizer.buildNormalizationScript(fontScale)

            "$fontOverrideJs\n$typographyOverrideJs\n$fontNormalizeJs"
        }

        val webViews = withContext(Dispatchers.Main) {
            frag.view?.let(::findWebViews).orEmpty()
        }
        if (webViews.isNotEmpty()) {
            val zoomPercent = (typography.fontSizeSp / 16.0f * 100).toInt().coerceIn(50, 300)
            withContext(Dispatchers.Main) {
                webViews.forEach { wv ->
                    wv.evaluateJavascript(js, null)
                    if (wv.settings.textZoom != zoomPercent) {
                        wv.settings.textZoom = zoomPercent
                    }
                }
            }
        } else {
            frag.evaluateJavascript(js)
        }
    }
}
