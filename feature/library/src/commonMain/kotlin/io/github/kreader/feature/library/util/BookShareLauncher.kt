package io.github.kreader.feature.library.util

import androidx.compose.runtime.Composable
import io.github.kreader.core.model.Book

@Composable
expect fun rememberBookShareLauncher(): (List<Book>) -> Unit
