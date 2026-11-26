package io.github.kreader.feature.reader

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.kreader.core.model.CustomFont
import java.io.File

@Composable
actual fun rememberFontPickerLauncher(onFontPicked: (CustomFont) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val displayName = queryFontDisplayName(context, uri) ?: uri.lastPathSegment ?: "ImportedFont.ttf"
            val fontName = displayName.substringBeforeLast(".").trim().ifBlank { "ImportedFont" }
            val ext = displayName.substringAfterLast(".", "ttf").lowercase()
                .takeIf { it in setOf("ttf", "otf", "woff", "woff2") } ?: "ttf"
            val fontsDir = File(context.filesDir, "fonts").apply { mkdirs() }
            val safeBase = fontName
                .replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_")
                .trim()
                .ifBlank { "ImportedFont" }
            val safeFileName = "$safeBase.$ext"
            val destFile = File(fontsDir, safeFileName)
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (destFile.exists() && destFile.length() > 0L) {
                    onFontPicked(CustomFont(name = fontName, path = destFile.absolutePath))
                }
            }
        }
    }
    return {
        launcher.launch(arrayOf(
            "font/ttf",
            "font/otf",
            "font/woff",
            "font/woff2",
            "application/x-font-ttf",
            "application/x-font-otf",
            "application/font-sfnt",
            "application/font-woff",
            "application/font-woff2",
            "*/*"
        ))
    }
}

private fun queryFontDisplayName(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                cursor.getString(index)
            } else null
        }
    }.getOrNull()
}
