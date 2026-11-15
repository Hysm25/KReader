package io.github.kreader.feature.reader

import android.content.Context
import android.content.ContextWrapper
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
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import io.github.kreader.core.engine.TocItem
import io.github.kreader.core.model.Book
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

@OptIn(ExperimentalReadiumApi::class)
@Composable
actual fun EpubNavigatorHost(
    book: Book,
    initialLocator: Locator?,
    theme: ReaderTheme,
    typography: ReaderTypography,
    isScrollMode: Boolean,
    controller: EpubNavigatorController,
    highlights: List<Highlight>,
    onHighlightClicked: (String) -> Unit,
    onAddHighlight: (Locator, String) -> Unit,
    onAddNoteRequested: (Locator, String) -> Unit,
    onProgressChanged: (progression: Float, locator: Locator) -> Unit,
    onTableOfContentsLoaded: (List<TocItem>) -> Unit,
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

    var publication by remember { mutableStateOf<Publication?>(null) }
    var currentFragment by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val opener = remember(context) { ReadiumEpubOpener(context.applicationContext) }

    // 1. Open publication
    LaunchedEffect(book.id) {
        isLoading = true
        errorMessage = null
        opener.open(book.source, book.id).fold(
            onSuccess = { pub ->
                publication = pub
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

    // 2. Build preferences
    val preferences = remember(theme, typography, isScrollMode, book.id) {
        buildReadiumPreferences(theme, typography, isScrollMode, isRtl = typography.isRtl)
    }

    // 3. Submit preferences on change
    LaunchedEffect(currentFragment, preferences) {
        currentFragment?.let { frag ->
            runCatching { frag.submitPreferences(preferences) }
        }
    }

    // 4. Wire controller navigation commands & search
    DisposableEffect(currentFragment, publication) {
        controller.goTo = { locator ->
            currentFragment?.go(locator.toReadium(), animated = true)
        }
        controller.goNext = {
            currentFragment?.goForward(animated = true)
        }
        controller.goPrevious = {
            currentFragment?.goBackward(animated = true)
        }
        controller.clearSelection = {
            currentFragment?.clearSelection()
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

    // 5. Host in FragmentContainerView
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

            DisposableEffect(book.id) {
                onDispose {
                    val fm = fragmentActivity.supportFragmentManager
                    if (!fragmentActivity.isFinishing && !fragmentActivity.isDestroyed) {
                        val frag = fm.findFragmentByTag(fragmentTag) ?: fm.findFragmentById(containerId)
                        if (frag != null) {
                            runCatching {
                                fm.beginTransaction().remove(frag).commitNowAllowingStateLoss()
                            }
                        }
                    }
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
                        var fragment = (fm.findFragmentById(view.id) ?: fm.findFragmentByTag(fragmentTag)) as? EpubNavigatorFragment
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
                            fragment = fragmentFactory.instantiate(
                                view.context.classLoader,
                                EpubNavigatorFragment::class.java.name
                            ) as EpubNavigatorFragment

                            fragment.addInputListener(
                                DirectionalNavigationAdapter(
                                    navigator = fragment,
                                    animatedTransition = true
                                )
                            )
                            fragment.addInputListener(object : InputListener {
                                override fun onTap(event: TapEvent): Boolean {
                                    currentOnTap()
                                    return true
                                }
                            })

                            fragment.addDecorationListener(
                                "highlights",
                                object : DecorableNavigator.Listener {
                                    override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
                                        currentOnHighlightClicked(event.decoration.id)
                                        return true
                                    }
                                }
                            )

                            fm.beginTransaction()
                                .replace(view.id, fragment, fragmentTag)
                                .commitNowAllowingStateLoss()
                        }
                        if (currentFragment !== fragment) {
                            currentFragment = fragment
                        }
                    }.onFailure { e ->
                        errorMessage = "Failed to initialize reader: ${e.message}"
                    }
                },
                modifier = modifier.fillMaxSize()
            )

            // Render Highlights as Decorations
            LaunchedEffect(currentFragment, highlights) {
                val frag = currentFragment ?: return@LaunchedEffect
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

            // Observe currentLocator for progress persistence
            LaunchedEffect(currentFragment) {
                val frag = currentFragment ?: return@LaunchedEffect
                frag.currentLocator.collect { readiumLocator ->
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
