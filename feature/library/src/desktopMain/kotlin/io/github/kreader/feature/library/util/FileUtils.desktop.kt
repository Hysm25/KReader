package io.github.kreader.feature.library.util

import java.io.File

actual val isDesktopPlatform: Boolean = true

actual fun deleteLocalFile(path: String): Boolean {
    return runCatching { File(path).delete() }.getOrDefault(false)
}
