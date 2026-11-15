package io.github.kreader.feature.library.repository

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.kreader.core.database.AndroidDriverFactory
import io.github.kreader.core.database.BookRepository
import io.github.kreader.core.database.DatabaseFactory
import io.github.kreader.core.database.SqlDelightBookRepository

@Composable
actual fun rememberBookRepository(): BookRepository {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        val driverFactory = AndroidDriverFactory(context)
        val database = DatabaseFactory.createDatabase(driverFactory)
        SqlDelightBookRepository(database)
    }
}
