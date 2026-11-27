package io.github.kreader.core.database

import io.github.kreader.core.model.BookSource
import io.github.kreader.core.model.CustomFont
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual class VaultStorage {
    private val _activeVaultUriOrPath = MutableStateFlow<String?>(null)
    actual val activeVaultUriOrPath: StateFlow<String?> = _activeVaultUriOrPath.asStateFlow()
    actual var onVaultContentChanged: (() -> Unit)? = null

    actual fun getActiveVault(): String? = _activeVaultUriOrPath.value
    actual fun setActiveVault(uriOrPath: String?) { _activeVaultUriOrPath.value = uriOrPath }
    actual fun getVaultName(): String? = null
    actual suspend fun listBooks(): List<VaultBookFile> = emptyList()
    actual suspend fun readConfig(fileName: String): String? = null
    actual suspend fun writeConfig(fileName: String, content: String) {}
    actual suspend fun deleteBookFile(relativePath: String): Boolean = false
    actual suspend fun saveCover(coverName: String, bytes: ByteArray): String? = null
    actual suspend fun cacheExtractedCover(relativePath: String, localCoverPath: String): String? = null
    actual suspend fun getCoverPath(coverName: String): String? = null
    actual suspend fun deleteCover(coverName: String): Boolean = false
    actual suspend fun saveFont(fontFileName: String, bytes: ByteArray): String? = null
    actual suspend fun persistFont(font: CustomFont): CustomFont = font
    actual suspend fun listFonts(): List<CustomFont> = emptyList()
    actual suspend fun deleteFont(fontFileName: String): Boolean = false
    actual suspend fun exportMarkdown(fileName: String, markdownContent: String): String? = null
    actual suspend fun extractEpubTextContent(source: BookSource): String? = null
}
