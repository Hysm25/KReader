package io.github.kreader.feature.library.filepicker

import androidx.compose.runtime.Composable
import io.github.kreader.core.model.BookSource
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser

@Composable
actual fun rememberFilePickerLauncher(onFilePicked: (BookSource) -> Unit): () -> Unit {
    return {
        val dialog = FileDialog(null as Frame?, "Select EPUB or CBZ", FileDialog.LOAD)
        dialog.setFilenameFilter { _, name ->
            val lower = name.lowercase()
            lower.endsWith(".epub") || lower.endsWith(".cbz")
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

@Composable
actual fun rememberFolderPickerLauncher(onFolderPicked: (String) -> Unit): () -> Unit {
    return {
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "Choose Books Folder"
        }
        val result = chooser.showOpenDialog(null)
        if (result == JFileChooser.APPROVE_OPTION && chooser.selectedFile != null) {
            onFolderPicked(chooser.selectedFile.absolutePath)
        }
    }
}
