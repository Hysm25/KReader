package io.github.kreader.feature.reader

import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import io.github.kreader.core.engine.TocItem
import io.github.kreader.core.model.Book
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ReaderTypography
import io.github.kreader.engine.epub.ReadiumEpubOpener
import io.github.kreader.engine.epub.toDomain
import io.github.kreader.engine.epub.toReadium
import io.github.kreader.engine.epub.toTocItem
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication

@OptIn(ExperimentalReadiumApi::class)
@Composable
actual fun EpubNavigatorHost(
    book: Book,
    initialLocator: Locator?,
    theme: ReaderTheme,
    typography: ReaderTypography,
    isScrollMode: Boolean,
    controller: EpubNavigatorController,
    onProgressChanged: (progression: Float, locator: Locator) -> Unit,
    onTableOfContentsLoaded: (List<TocItem>) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val fragmentActivity = remember(context) { context.findFragmentActivity() }

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
                onTableOfContentsLoaded(toc)
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
        currentFragment?.submitPreferences(preferences)
    }

    // 4. Wire controller navigation commands
    DisposableEffect(currentFragment) {
        controller.goTo = { locator ->
            currentFragment?.go(locator.toReadium(), animated = true)
        }
        controller.goNext = {
            currentFragment?.goForward(animated = true)
        }
        controller.goPrevious = {
            currentFragment?.goBackward(animated = true)
        }
        onDispose {
            controller.goTo = null
            controller.goNext = null
            controller.goPrevious = null
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
                    val fm = fragmentActivity.supportFragmentManager
                    var fragment = fm.findFragmentById(view.id) as? EpubNavigatorFragment
                    if (fragment == null) {
                        val factory = EpubNavigatorFactory(pub)
                        val fragmentFactory = factory.createFragmentFactory(
                            initialLocator = initialLocator?.toReadium(),
                            initialPreferences = preferences
                        )
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
                                onTap()
                                return true
                            }
                        })

                        fm.beginTransaction()
                            .replace(view.id, fragment)
                            .commitAllowingStateLoss()
                    }
                    currentFragment = fragment
                },
                modifier = modifier.fillMaxSize()
            )

            // Observe currentLocator for progress persistence
            LaunchedEffect(currentFragment) {
                currentFragment?.currentLocator?.collect { readiumLocator ->
                    val domainLocator = readiumLocator.toDomain()
                    val prog = domainLocator.locations.totalProgression
                        ?: domainLocator.locations.progression
                        ?: 0f
                    onProgressChanged(prog, domainLocator)
                }
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
