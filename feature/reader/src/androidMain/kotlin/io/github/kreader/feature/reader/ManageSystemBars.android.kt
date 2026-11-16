package io.github.kreader.feature.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable
actual fun ManageSystemBars(isControlsVisible: Boolean) {
    val context = LocalContext.current
    val activity = context.findActivity() ?: return

    DisposableEffect(isControlsVisible, activity) {
        val window = activity.window
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (isControlsVisible) {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
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
