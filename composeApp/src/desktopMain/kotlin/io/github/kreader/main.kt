package io.github.kreader

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.kreader.core.model.BookSource
import io.github.kreader.core.model.ExternalBookOpener
import java.io.File

fun main(args: Array<String>) {
    if (args.isNotEmpty()) {
        val file = File(args[0])
        if (file.exists() && file.isFile && file.extension.equals("epub", ignoreCase = true)) {
            ExternalBookOpener.open(
                BookSource.FilePath(
                    path = file.absolutePath,
                    displayName = file.name
                )
            )
        }
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "KReader"
        ) {
            App()
        }
    }
}
