package io.github.kreader

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.Surface
import android.view.SurfaceControl
import android.view.View
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
        window.decorView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                applyHighRefreshRateSurfaceTransaction()
            }
            override fun onViewDetachedFromWindow(v: View) {}
        })
        enableEdgeToEdge()
        handleIncomingIntent(intent)
        setContent {
            App()
        }
    }

    override fun onResume() {
        super.onResume()
        configureHighRefreshRateAndHardwareAcceleration()
        applyHighRefreshRateSurfaceTransaction()
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

                val params = window.attributes
                if (bestMode != null && bestMode.modeId != 0) {
                    params.preferredDisplayModeId = bestMode.modeId
                    params.preferredRefreshRate = bestMode.refreshRate
                } else {
                    params.preferredRefreshRate = 120f
                }
                window.attributes = params

                // Android 14+ (API 34+): Boost frame rate on touch interactions
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    runCatching {
                        window.setFrameRateBoostOnTouchEnabled(true)
                    }
                }

                // Apply surface transaction
                applyHighRefreshRateSurfaceTransaction(bestMode?.refreshRate ?: 120f)
            }
        }
    }

    private fun applyHighRefreshRateSurfaceTransaction(targetRate: Float = 120f) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.decorView.post {
                runCatching {
                    val decor = window.decorView
                    val viewRoot = try {
                        val method = decor.javaClass.getMethod("getViewRootImpl")
                        method.invoke(decor)
                    } catch (_: Throwable) { null }

                    val sc = if (viewRoot != null) {
                        try {
                            val method = viewRoot.javaClass.getMethod("getSurfaceControl")
                            method.invoke(viewRoot) as? SurfaceControl
                        } catch (_: Throwable) { null }
                    } else null

                    if (sc != null && sc.isValid) {
                        SurfaceControl.Transaction()
                            .setFrameRate(
                                sc,
                                targetRate,
                                Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                                Surface.CHANGE_FRAME_RATE_ALWAYS
                            )
                            .apply()
                    }

                    // Also try setFrameRate on rootSurfaceControl if exposed by OEM
                    val rsc = decor.rootSurfaceControl
                    if (rsc != null) {
                        try {
                            val method = rsc.javaClass.getMethod(
                                "setFrameRate",
                                Float::class.javaPrimitiveType,
                                Int::class.javaPrimitiveType,
                                Int::class.javaPrimitiveType
                            )
                            method.invoke(rsc, targetRate, 0, 1)
                        } catch (_: Throwable) {}
                    }
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
