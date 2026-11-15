package io.github.kreader.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

class DesktopDriverFactory(private val databaseDir: File? = null) : DriverFactory {
    override fun createDriver(): SqlDriver {
        val targetDir = databaseDir ?: File(System.getProperty("user.home"), ".kreader").apply { mkdirs() }
        val dbFile = File(targetDir, "kreader.db")
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        
        if (!dbFile.exists() || dbFile.length() == 0L) {
            KReaderDatabase.Schema.create(driver)
        }
        return driver
    }
}
