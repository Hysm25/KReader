package io.github.kreader.core.engine

import io.github.kreader.core.model.Book
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ReaderTypography
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface ReaderEngine {
    val state: StateFlow<ReaderEngineState>
    val events: SharedFlow<ReaderEngineEvent>

    suspend fun open(book: Book, initialLocator: Locator?): Result<Unit>
    suspend fun close()
    suspend fun goTo(locator: Locator): Result<Unit>
    suspend fun next(): Result<Unit>
    suspend fun previous(): Result<Unit>
    suspend fun applyTheme(theme: ReaderTheme)
    suspend fun applyTypography(typography: ReaderTypography)
    suspend fun search(query: String): List<Locator>
}
