package io.github.kreader.feature.library.util

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import io.github.kreader.core.model.Book
import io.github.kreader.core.model.BookSource
import java.io.File

@Composable
actual fun rememberBookShareLauncher(): (List<Book>) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { books ->
            if (books.isNotEmpty()) {
                val uris = books.mapNotNull { book ->
                    val path = (book.source as? BookSource.FilePath)?.path
                    if (path != null) {
                        val file = File(path)
                        if (file.exists()) {
                            runCatching {
                                FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                            }.getOrNull()
                        } else null
                    } else null
                }
                if (uris.isNotEmpty()) {
                    val intent = if (uris.size == 1) {
                        Intent(Intent.ACTION_SEND).apply {
                            type = "application/epub+zip"
                            putExtra(Intent.EXTRA_STREAM, uris.first())
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    } else {
                        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            type = "application/epub+zip"
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    }
                    val chooser = Intent.createChooser(intent, "Share Book(s)")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                }
            }
        }
    }
}
