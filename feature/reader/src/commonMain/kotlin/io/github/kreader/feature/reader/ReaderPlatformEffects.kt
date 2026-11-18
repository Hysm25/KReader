package io.github.kreader.feature.reader

import androidx.compose.runtime.Composable
import io.github.kreader.core.model.OrientationLock

expect val isDesktopPlatform: Boolean

@Composable
expect fun KeepScreenOn(enabled: Boolean)

@Composable
expect fun ManageOrientation(orientationLock: OrientationLock)
