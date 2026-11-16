package io.github.kreader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import io.github.kreader.core.engine.ReaderKeyEventDispatcher
import io.github.kreader.core.model.BookSource
import io.github.kreader.core.model.ExternalBookOpener

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)
        setContent {
            App()
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
