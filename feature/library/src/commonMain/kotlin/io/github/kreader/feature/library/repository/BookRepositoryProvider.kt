package io.github.kreader.feature.library.repository

import androidx.compose.runtime.Composable
import io.github.kreader.core.database.BookRepository

@Composable
expect fun rememberBookRepository(): BookRepository
