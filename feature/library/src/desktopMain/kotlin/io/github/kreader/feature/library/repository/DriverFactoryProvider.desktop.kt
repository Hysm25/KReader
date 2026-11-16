package io.github.kreader.feature.library.repository

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.kreader.core.database.BookRepository
import io.github.kreader.core.database.JsonVaultBookRepository
import io.github.kreader.core.database.VaultStorage

@Composable
actual fun rememberBookRepository(): BookRepository {
    return remember {
        val vaultStorage = VaultStorage()
        JsonVaultBookRepository(vaultStorage)
    }
}
