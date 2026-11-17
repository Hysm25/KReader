package io.github.kreader.feature.library.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// 24 MB cache for downsampled cover thumbnails (maxSize in KB matching sizeOf in KB)
private val coverBitmapCache = object : LruCache<String, Bitmap>(24 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int {
        return (value.byteCount / 1024).coerceAtLeast(1)
    }
}

private fun decodeDownsampledBitmap(filePath: String, reqWidth: Int = 360, reqHeight: Int = 540): Bitmap? {
    return runCatching {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(filePath, boundsOptions)

        var inSampleSize = 1
        if (boundsOptions.outHeight > reqHeight || boundsOptions.outWidth > reqWidth) {
            val halfHeight = boundsOptions.outHeight / 2
            val halfWidth = boundsOptions.outWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeFile(filePath, decodeOptions)
    }.getOrNull()
}

@Composable
actual fun rememberCoverPainter(coverPath: String?): Painter? {
    if (coverPath.isNullOrBlank()) return null

    val cached = remember(coverPath) { coverBitmapCache.get(coverPath) }
    if (cached != null && !cached.isRecycled) {
        return remember(cached) { BitmapPainter(cached.asImageBitmap()) }
    }

    val bitmapState = produceState<Bitmap?>(initialValue = null, key1 = coverPath) {
        val file = File(coverPath)
        if (file.exists() && file.length() > 0L) {
            val decoded = withContext(Dispatchers.IO) {
                decodeDownsampledBitmap(file.absolutePath)
            }
            if (decoded != null) {
                coverBitmapCache.put(coverPath, decoded)
                value = decoded
            }
        }
    }

    val bmp = bitmapState.value ?: return null
    return remember(bmp) { BitmapPainter(bmp.asImageBitmap()) }
}
