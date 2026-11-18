package io.github.kreader

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.kreader.core.model.BookSource
import io.github.kreader.core.model.ExternalBookOpener
import java.awt.Dimension
import java.awt.FileDialog
import java.awt.Frame
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
        val windowState = rememberWindowState(width = 1280.dp, height = 820.dp)
        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "KReader",
            onKeyEvent = { event ->
                if (event.type == KeyEventType.KeyDown) {
                    val isCtrl = event.isCtrlPressed || event.isMetaPressed
                    if (isCtrl && event.key == Key.O) {
                        // Global Ctrl+O: Open EPUB file dialog
                        val dialog = FileDialog(null as Frame?, "Open EPUB Book", FileDialog.LOAD)
                        dialog.setFilenameFilter { _, name -> name.lowercase().endsWith(".epub") }
                        dialog.isVisible = true
                        val dir = dialog.directory
                        val file = dialog.file
                        if (dir != null && file != null) {
                            val chosenFile = File(dir, file)
                            if (chosenFile.exists()) {
                                ExternalBookOpener.open(
                                    BookSource.FilePath(
                                        path = chosenFile.absolutePath,
                                        displayName = chosenFile.name
                                    )
                                )
                            }
                        }
                        true
                    } else false
                } else false
            }
        ) {
            window.minimumSize = Dimension(720, 500)
            App()
        }
    }
}
