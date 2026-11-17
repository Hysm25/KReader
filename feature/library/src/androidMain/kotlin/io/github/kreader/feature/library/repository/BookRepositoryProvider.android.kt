package io.github.kreader.feature.library.repository

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.kreader.core.database.BookRepository
import io.github.kreader.core.database.JsonVaultBookRepository
import io.github.kreader.core.database.VaultStorage

@Composable
actual fun rememberBookRepository(): BookRepository {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        val vaultStorage = VaultStorage(context)
        JsonVaultBookRepository(vaultStorage)
    }
}
