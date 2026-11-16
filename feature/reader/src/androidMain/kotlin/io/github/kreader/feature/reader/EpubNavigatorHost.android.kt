package io.github.kreader.feature.reader

import android.content.Context
import android.content.ContextWrapper
import android.util.Base64
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import io.github.kreader.engine.epub.ReadiumEpubOpener
import io.github.kreader.engine.epub.toDomain
import io.github.kreader.engine.epub.toReadium
import io.github.kreader.engine.epub.toTocItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
    controller: EpubNavigatorController,
    customFonts: List<CustomFont>,
    highlights: List<Highlight>,
    onHighlightClicked: (String) -> Unit,
    onAddHighlight: (Locator, String) -> Unit,
    onAddNoteRequested: (Locator, String) -> Unit,
    onProgressChanged: (progression: Float, locator: Locator) -> Unit,
    onTableOfContentsLoaded: (List<TocItem>) -> Unit,
    onDirectionDetected: (isRtl: Boolean) -> Unit,
    onTap: () -> Unit,
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

    var publication by remember { mutableStateOf<Publication?>(null) }
    var currentFragment by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
    var isNavigatorReady by remember { mutableStateOf(false) }
    var detectedRtl by remember { mutableStateOf<Boolean?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val configuredFragments = remember(book.id) {
        Collections.newSetFromMap(WeakHashMap<EpubNavigatorFragment, Boolean>())
    }

    val opener = remember(context) { ReadiumEpubOpener(context.applicationContext) }

    // 1. Open publication and auto-detect reading direction
    LaunchedEffect(book.id) {
        isLoading = true
        errorMessage = null
        isNavigatorReady = false
        currentFragment = null
        opener.open(book.source, book.id).fold(
            onSuccess = { pub ->
                publication = pub
                val readingProg = pub.metadata.readingProgression
                val isPubRtl = (readingProg == org.readium.r2.shared.publication.ReadingProgression.RTL) ||
                    pub.metadata.languages.any { lang ->
                        lang.startsWith("ar", ignoreCase = true) ||
                            lang.startsWith("fa", ignoreCase = true) ||
                            lang.startsWith("ur", ignoreCase = true) ||
                            lang.startsWith("he", ignoreCase = true)
                    }
                detectedRtl = isPubRtl
                currentOnDirectionDetected(isPubRtl)

                val toc = pub.tableOfContents.map { it.toTocItem(pub) }
                currentOnTableOfContentsLoaded(toc)
                isLoading = false
            },
            onFailure = { err ->
                errorMessage = err.message ?: "Failed to open EPUB"
                isLoading = false
            }
        )
    }

    // 2. Build preferences (automatic reading direction + custom font support)
    val effectiveRtl = detectedRtl == true
    val preferences = remember(theme, typography, isScrollMode, book.id, effectiveRtl) {
        buildReadiumPreferences(theme, typography, isScrollMode, isRtl = effectiveRtl)
    }

    // 3. Submit preferences on change (deferred until fragment is attached and ready)
    LaunchedEffect(currentFragment, preferences, isNavigatorReady) {
        val frag = currentFragment ?: return@LaunchedEffect
        if (isNavigatorReady && frag.isAdded && frag.context != null) {
            runCatching { frag.submitPreferences(preferences) }
        }
    }

    // 4. Inject custom @font-face CSS if user selected an imported font
    LaunchedEffect(currentFragment, typography.fontFamily, customFonts, isNavigatorReady) {
        val frag = currentFragment ?: return@LaunchedEffect
        if (isNavigatorReady && frag.isAdded && frag.context != null) {
            val selectedCustomFont = customFonts.find { it.name.equals(typography.fontFamily, ignoreCase = true) }
            if (selectedCustomFont != null) {
                injectFontFace(frag, selectedCustomFont)
            } else {
                clearFontFace(frag)
            }
        }
    }

    // 5. Wire controller navigation commands & search
    DisposableEffect(currentFragment, publication, isNavigatorReady) {
        controller.goTo = { locator ->
            val frag = currentFragment
            if (isNavigatorReady && frag != null && frag.isAdded && frag.context != null) {
                frag.go(locator.toReadium(), animated = true)
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
        controller.clearSelection = {
            val frag = currentFragment
            if (isNavigatorReady && frag != null && frag.isAdded && frag.context != null) {
                frag.clearSelection()
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
                                results.add(loc.toDomain())
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
            controller.goNext = null
            controller.goPrevious = null
            controller.clearSelection = null
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

            val selectionCallback = remember(scope) {
                SelectionActionModeCallback(
                    scope = scope,
                    fragmentProvider = { currentFragment },
                    onHighlight = { loc, color -> currentOnAddHighlight(loc, color) },
                    onAddNote = { loc, text -> currentOnAddNoteRequested(loc, text) }
                )
            }

            // Register FragmentLifecycleCallbacks to safely configure listeners AFTER fragment is STARTED
            DisposableEffect(fragmentActivity, fragmentTag) {
                val fm = fragmentActivity.supportFragmentManager
                val lifecycleCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
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
                modifier = modifier.fillMaxSize()
            )

            // Render Highlights as Decorations (guarded by isNavigatorReady & isAdded)
            LaunchedEffect(currentFragment, highlights, isNavigatorReady) {
                val frag = currentFragment ?: return@LaunchedEffect
                if (isNavigatorReady && frag.isAdded && frag.context != null) {
                    runCatching {
                        val decorations = highlights.map { h ->
                            val tintColor = try {
                                android.graphics.Color.parseColor(h.colorHex)
                            } catch (_: Exception) {
                                android.graphics.Color.YELLOW
                            }
                            Decoration(
                                id = h.id,
                                locator = h.locator.toReadium(),
                                style = Decoration.Style.Highlight(tint = tintColor, isActive = true)
                            )
                        }
                        frag.applyDecorations(decorations, group = "highlights")
                    }
                }
            }

            // Observe currentLocator for progress persistence (deferred until fragment is STARTED)
            LaunchedEffect(currentFragment, isNavigatorReady) {
                val frag = currentFragment ?: return@LaunchedEffect
                if (!isNavigatorReady || !frag.isAdded || frag.context == null) return@LaunchedEffect
                if (!frag.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@LaunchedEffect

                frag.currentLocator.collect { readiumLocator ->
                    if (frag.isAdded && frag.context != null) {
                        val selectedFont = customFonts.find { it.name.equals(typography.fontFamily, ignoreCase = true) }
                        if (selectedFont != null) {
                            injectFontFace(frag, selectedFont)
                        }

                        runCatching {
                            val domainLocator = readiumLocator.toDomain()
                            val prog = domainLocator.locations.totalProgression
                                ?: domainLocator.locations.progression
                                ?: 0f
                            currentOnProgressChanged(prog, domainLocator)
                        }
                    }
                }
            }
        }
    }
}

private suspend fun injectFontFace(frag: EpubNavigatorFragment, font: CustomFont) {
    runCatching {
        val file = File(font.path)
        if (!file.exists()) return
        val fontBytes = file.readBytes()
        val base64 = Base64.encodeToString(fontBytes, Base64.NO_WRAP)
        val format = if (file.extension.equals("otf", ignoreCase = true)) "opentype" else "truetype"
        val js = """
            (function() {
                var el = document.getElementById('kreader-custom-font');
                if (!el) {
                    el = document.createElement('style');
                    el.id = 'kreader-custom-font';
                    document.head.appendChild(el);
                }
                el.textContent = '@font-face { font-family: "${font.name}"; src: url("data:font/$format;charset=utf-8;base64,$base64"); } * { font-family: "${font.name}", sans-serif !important; }';
            })();
        """.trimIndent()
        frag.evaluateJavascript(js)
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
    }
}

private class SelectionActionModeCallback(
    private val scope: CoroutineScope,
    private val fragmentProvider: () -> EpubNavigatorFragment?,
    private val onHighlight: (locator: Locator, colorHex: String) -> Unit,
    private val onAddNote: (locator: Locator, selectedText: String) -> Unit
) : ActionMode.Callback {

    companion object {
        const val MENU_YELLOW = 1001
        const val MENU_GREEN = 1002
        const val MENU_BLUE = 1003
        const val MENU_PINK = 1004
        const val MENU_NOTE = 1005
    }

    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        menu?.add(0, MENU_YELLOW, 0, "🟡 Yellow")
        menu?.add(0, MENU_GREEN, 1, "🟢 Green")
        menu?.add(0, MENU_BLUE, 2, "🔵 Blue")
        menu?.add(0, MENU_PINK, 3, "🔴 Pink")
        menu?.add(0, MENU_NOTE, 4, "📝 Note")
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
        val fragment = fragmentProvider() ?: return false
        if (!fragment.isAdded || fragment.context == null) return false

        val colorHex = when (item?.itemId) {
            MENU_YELLOW -> "#FEF08A"
            MENU_GREEN -> "#BBF7D0"
            MENU_BLUE -> "#BAE6FD"
            MENU_PINK -> "#FBCFE8"
            else -> null
        }
        val isNote = item?.itemId == MENU_NOTE

        if (colorHex != null || isNote) {
            scope.launch {
                val selection = fragment.currentSelection()
                if (selection != null) {
                    val locator = selection.locator.toDomain()
                    val text = locator.text?.highlight ?: ""
                    if (isNote) {
                        onAddNote(locator, text)
                    } else if (colorHex != null) {
                        onHighlight(locator, colorHex)
                    }
                    fragment.clearSelection()
                }
            }
            mode?.finish()
            return true
        }
        return false
    }

    override fun onDestroyActionMode(mode: ActionMode?) {}
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
