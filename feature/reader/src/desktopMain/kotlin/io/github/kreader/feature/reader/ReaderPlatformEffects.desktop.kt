package io.github.kreader.feature.reader

import androidx.compose.runtime.Composable
import io.github.kreader.core.model.OrientationLock

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    // No-op on desktop
}

@Composable
actual fun ManageOrientation(orientationLock: OrientationLock) {
    // No-op on desktop
}

@Composable
actual fun ManageBrightness(brightness: Float) {
    // No-op on desktop
}
