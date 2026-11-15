package io.github.kreader.feature.library.repository

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.kreader.core.database.BookRepository
import io.github.kreader.core.database.DatabaseFactory
import io.github.kreader.core.database.DesktopDriverFactory
import io.github.kreader.core.database.SqlDelightBookRepository

@Composable
actual fun rememberBookRepository(): BookRepository {
    return remember {
        val driverFactory = DesktopDriverFactory()
        val database = DatabaseFactory.createDatabase(driverFactory)
        SqlDelightBookRepository(database)
    }
}
