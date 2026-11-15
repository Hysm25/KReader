package io.github.kreader.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

class NativeDriverFactory : DriverFactory {
    override fun createDriver(): SqlDriver {
        return NativeSqliteDriver(
            schema = KReaderDatabase.Schema,
            name = "kreader.db"
        )
    }
}
