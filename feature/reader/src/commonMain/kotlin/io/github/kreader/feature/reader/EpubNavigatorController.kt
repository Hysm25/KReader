package io.github.kreader.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.kreader.core.model.Locator

class EpubNavigatorController {
    var goTo: ((Locator) -> Unit)? = null
    var goNext: (() -> Unit)? = null
    var goPrevious: (() -> Unit)? = null
}

@Composable
fun rememberEpubNavigatorController(): EpubNavigatorController {
    return remember { EpubNavigatorController() }
}

