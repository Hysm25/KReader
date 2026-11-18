package io.github.kreader.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.kreader.core.model.Locator

class EpubNavigatorController {
    var goTo: ((Locator) -> Unit)? = null
    var goToSearchResult: ((Locator) -> Unit)? = null
    var goNext: (() -> Unit)? = null
    var goPrevious: (() -> Unit)? = null
    var seekToProgression: ((Float) -> Unit)? = null
    var search: (suspend (String) -> List<Locator>)? = null
    var clearSelection: (() -> Unit)? = null
    var highlightSearchResult: ((Locator) -> Unit)? = null
    var clearSearchHighlight: (() -> Unit)? = null
}

@Composable
fun rememberEpubNavigatorController(): EpubNavigatorController {
    return remember { EpubNavigatorController() }
}

