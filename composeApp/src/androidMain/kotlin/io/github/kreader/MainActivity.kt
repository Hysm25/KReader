package io.github.kreader

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import io.github.kreader.core.engine.ReaderKeyEventDispatcher
import io.github.kreader.core.model.BookSource
import io.github.kreader.core.model.ExternalBookOpener

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureHighRefreshRateAndHardwareAcceleration()
        enableEdgeToEdge()
        handleIncomingIntent(intent)
        setContent {
            App()
        }
    }

    private fun configureHighRefreshRateAndHardwareAcceleration() {
        // 1. Enable Hardware Acceleration for the Window & WebViews
        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )

        // 2. Unlock High Refresh Rate (60Hz / 90Hz / 120Hz)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            }

            if (display != null) {
                val supportedModes = display.supportedModes
                val currentMode = display.mode
                val bestMode = supportedModes
                    .filter { it.physicalWidth == currentMode.physicalWidth && it.physicalHeight == currentMode.physicalHeight }
                    .maxByOrNull { it.refreshRate }
                    ?: supportedModes.maxByOrNull { it.refreshRate }

                if (bestMode != null && bestMode.modeId != 0) {
                    val params = window.attributes
                    params.preferredDisplayModeId = bestMode.modeId
                    window.attributes = params
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (ReaderKeyEventDispatcher.onVolumeUp?.invoke() == true) return true
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (ReaderKeyEventDispatcher.onVolumeDown?.invoke() == true) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val uri: Uri = intent?.data ?: return
        val scheme = uri.scheme
        val displayName = uri.lastPathSegment ?: "Imported Book"
        val source: BookSource = if (scheme == "file") {
            BookSource.FilePath(uri.path ?: uri.toString(), displayName)
        } else {
            BookSource.ContentUri(uri.toString(), displayName)
        }
        ExternalBookOpener.open(source)
    }
}
