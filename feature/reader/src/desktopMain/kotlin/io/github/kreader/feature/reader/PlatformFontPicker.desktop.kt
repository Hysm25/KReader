package io.github.kreader.feature.reader

import androidx.compose.runtime.Composable
import io.github.kreader.core.model.CustomFont
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
actual fun rememberFontPickerLauncher(onFontPicked: (CustomFont) -> Unit): () -> Unit {
    return {
        val dialog = FileDialog(null as Frame?, "Select Font File", FileDialog.LOAD)
        dialog.setFilenameFilter { _, name ->
            name.endsWith(".ttf", ignoreCase = true) || name.endsWith(".otf", ignoreCase = true)
        }
        dialog.isVisible = true
        val file = dialog.file
        val dir = dialog.directory
        if (file != null && dir != null) {
            val srcFile = File(dir, file)
            val fontName = srcFile.nameWithoutExtension
            val userHome = System.getProperty("user.home")
            val fontsDir = File(userHome, ".kreader/fonts").apply { mkdirs() }
            val destFile = File(fontsDir, srcFile.name)
            runCatching {
                srcFile.copyTo(destFile, overwrite = true)
                if (destFile.exists() && destFile.length() > 0L) {
                    onFontPicked(CustomFont(name = fontName, path = destFile.absolutePath))
                }
            }
        }
    }
}
