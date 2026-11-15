package io.github.kreader.feature.library.filepicker

import androidx.compose.runtime.Composable
import io.github.kreader.core.model.BookSource
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
actual fun rememberFilePickerLauncher(onFilePicked: (BookSource) -> Unit): () -> Unit {
    return {
        val dialog = FileDialog(null as Frame?, "Select EPUB or PDF", FileDialog.LOAD)
        dialog.setFilenameFilter { _, name ->
            val lower = name.lowercase()
            lower.endsWith(".epub") || lower.endsWith(".pdf")
        }
        dialog.isVisible = true
        val dir = dialog.directory
        val file = dialog.file
        if (dir != null && file != null) {
            val chosenFile = File(dir, file)
            if (chosenFile.exists()) {
                onFilePicked(BookSource.FilePath(path = chosenFile.absolutePath, displayName = chosenFile.name))
            }
        }
    }
}
