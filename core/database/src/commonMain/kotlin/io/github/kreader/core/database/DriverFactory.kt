package io.github.kreader.core.database

import app.cash.sqldelight.db.SqlDriver

interface DriverFactory {
    fun createDriver(): SqlDriver
}

object DatabaseFactory {
    fun createDatabase(driverFactory: DriverFactory): KReaderDatabase {
        val driver = driverFactory.createDriver()
        return KReaderDatabase(driver)
    }
}
