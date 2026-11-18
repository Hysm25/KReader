package io.github.kreader.feature.reader

import androidx.compose.runtime.Composable
import io.github.kreader.core.model.OrientationLock

actual val isDesktopPlatform: Boolean = false

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    // No-op on native platforms
}

@Composable
actual fun ManageOrientation(orientationLock: OrientationLock) {
    // No-op on native platforms
}
