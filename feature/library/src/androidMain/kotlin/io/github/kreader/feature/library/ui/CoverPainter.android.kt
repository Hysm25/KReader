package io.github.kreader.feature.library.ui

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import java.io.File

@Composable
actual fun rememberCoverPainter(coverPath: String?): Painter? {
    if (coverPath.isNullOrBlank()) return null

    return remember(coverPath) {
        val file = File(coverPath)
        if (file.exists() && file.length() > 0) {
            runCatching {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                bitmap?.asImageBitmap()?.let { BitmapPainter(it) }
            }.getOrNull()
        } else {
            null
        }
    }
}
