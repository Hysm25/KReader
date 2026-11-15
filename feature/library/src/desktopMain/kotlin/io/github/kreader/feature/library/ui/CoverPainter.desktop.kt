package io.github.kreader.feature.library.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.File
import javax.imageio.ImageIO

@Composable
actual fun rememberCoverPainter(coverPath: String?): Painter? {
    if (coverPath.isNullOrBlank()) return null

    return remember(coverPath) {
        val file = File(coverPath)
        if (file.exists() && file.length() > 0) {
            runCatching {
                val bufferedImage = ImageIO.read(file)
                bufferedImage?.toComposeImageBitmap()?.let { BitmapPainter(it) }
            }.getOrNull()
        } else {
            null
        }
    }
}
