package io.github.kreader.core.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object ExternalBookOpener {
    private val _incomingSource = MutableStateFlow<BookSource?>(null)
    val incomingSource = _incomingSource.asStateFlow()

    fun open(source: BookSource) {
        _incomingSource.value = source
    }

    fun clear() {
        _incomingSource.value = null
    }
}
