package io.github.kreader.core.engine

/**
 * Global dispatcher for hardware key events (e.g. Volume Up / Down) forwarded from Android Activity.
 */
object ReaderKeyEventDispatcher {
    var onVolumeUp: (() -> Boolean)? = null
    var onVolumeDown: (() -> Boolean)? = null
}
