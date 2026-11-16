package io.github.kreader.feature.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ThemeMode

@Composable
actual fun ManageSystemBars(isControlsVisible: Boolean, theme: ReaderTheme) {
    val context = LocalContext.current
    val activity = context.findActivity() ?: return
    val isSystemDark = isSystemInDarkTheme()

    val isDarkTheme = when (theme.mode) {
        ThemeMode.LIGHT, ThemeMode.SEPIA -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemDark
    }

    DisposableEffect(isDarkTheme, activity) {
        val window = activity.window
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)

        // Ensure icon contrast matches active ReaderTheme:
        // isAppearanceLightStatusBars = true gives dark icons (for Light & Sepia backgrounds)
        // isAppearanceLightStatusBars = false gives light icons (for Dark & AMOLED backgrounds)
        insetsController.isAppearanceLightStatusBars = !isDarkTheme
        insetsController.isAppearanceLightNavigationBars = !isDarkTheme

        onDispose {}
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
