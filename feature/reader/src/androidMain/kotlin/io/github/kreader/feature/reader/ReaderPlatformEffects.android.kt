package io.github.kreader.feature.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import io.github.kreader.core.model.OrientationLock

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled, view) {
        val prev = view.keepScreenOn
        view.keepScreenOn = enabled
        onDispose {
            view.keepScreenOn = prev
        }
    }
}

@Composable
actual fun ManageOrientation(orientationLock: OrientationLock) {
    val context = LocalContext.current
    DisposableEffect(orientationLock, context) {
        val activity = context.findActivity()
        if (activity != null) {
            val originalOrientation = activity.requestedOrientation
            val targetOrientation = when (orientationLock) {
                OrientationLock.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                OrientationLock.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                OrientationLock.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            activity.requestedOrientation = targetOrientation
            onDispose {
                activity.requestedOrientation = originalOrientation
            }
        } else {
            onDispose {}
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
