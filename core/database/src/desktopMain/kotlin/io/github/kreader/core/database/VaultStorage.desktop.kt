package io.github.kreader.core.database

import io.github.kreader.core.model.BookSource
import io.github.kreader.core.model.CustomFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.prefs.Preferences

actual class VaultStorage {

    private val prefs: Preferences = Preferences.userRoot().node("io.github.kreader.vault")
    private val _activeVaultUriOrPath = MutableStateFlow<String?>(loadInitialVault())
    actual val activeVaultUriOrPath: StateFlow<String?> = _activeVaultUriOrPath.asStateFlow()

    private fun loadInitialVault(): String? {
        val path = prefs.get("active_vault_path", null) ?: return null
        val dir = File(path)
        return if (dir.exists() && dir.isDirectory) path else null
    }

    actual fun getActiveVault(): String? = _activeVaultUriOrPath.value

    actual fun setActiveVault(uriOrPath: String?) {
        if (uriOrPath != null) {
            prefs.put("active_vault_path", uriOrPath)
        } else {
            prefs.remove("active_vault_path")
        }
        _activeVaultUriOrPath.value = uriOrPath
    }

    actual fun getVaultName(): String? {
        val path = getActiveVault() ?: return null
        return File(path).name.ifBlank { "Books" }
    }

    private fun getVaultDir(): File? {
        val path = getActiveVault() ?: return null
        val dir = File(path)
        return if (dir.exists() && dir.isDirectory) dir else null
    }

    private fun getKReaderDir(): File? {
        val vault = getVaultDir() ?: return null
        return File(vault, ".kreader").apply { mkdirs() }
    }

    actual suspend fun listEpubs(): List<VaultEpubFile> = withContext(Dispatchers.IO) {
        val vault = getVaultDir() ?: return@withContext emptyList()
        val results = mutableListOf<VaultEpubFile>()
        scanFileTree(vault, vault, results)
        results
    }

    private fun scanFileTree(rootDir: File, currentDir: File, results: MutableList<VaultEpubFile>) {
        val files = currentDir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                if (file.name == ".kreader") continue
                scanFileTree(rootDir, file, results)
            } else if (file.isFile && file.name.endsWith(".epub", ignoreCase = true)) {
                val relPath = file.relativeTo(rootDir).path.replace('\\', '/')
                results.add(
                    VaultEpubFile(
                        relativePath = relPath,
                        displayName = file.name,
                        source = BookSource.FilePath(path = file.absolutePath, displayName = file.name),
                        fileSizeBytes = file.length(),
                        lastModified = file.lastModified()
                    )
                )
            }
        }
    }

    actual suspend fun readConfig(fileName: String): String? = withContext(Dispatchers.IO) {
        val kreader = getKReaderDir() ?: return@withContext null
        val file = File(kreader, fileName)
        if (file.exists()) file.readText() else null
    }

    actual suspend fun writeConfig(fileName: String, content: String): Unit = withContext(Dispatchers.IO) {
        val kreader = getKReaderDir() ?: return@withContext
        File(kreader, fileName).writeText(content)
    }

    actual suspend fun deleteEpub(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        val vault = getVaultDir() ?: return@withContext false
        val file = File(vault, relativePath)
        file.delete()
    }

    actual suspend fun cacheExtractedCover(relativePath: String, localCoverPath: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val localFile = File(localCoverPath)
            if (!localFile.exists() || localFile.length() == 0L) return@withContext null
            val ext = localFile.extension.ifBlank { "jpg" }
            val coverName = "${relativePath.replace('/', '_').replace('\\', '_')}.$ext"
            val bytes = localFile.readBytes()
            saveCover(coverName, bytes)
        }.getOrNull()
    }

    actual suspend fun saveCover(coverName: String, bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        val kreader = getKReaderDir() ?: return@withContext null
        val coversDir = File(kreader, "covers").apply { mkdirs() }
        val coverFile = File(coversDir, coverName)
        coverFile.writeBytes(bytes)
        coverFile.absolutePath
    }

    actual suspend fun getCoverPath(coverName: String): String? = withContext(Dispatchers.IO) {
        val kreader = getKReaderDir() ?: return@withContext null
        val coversDir = File(kreader, "covers")
        if (!coversDir.exists()) return@withContext null
        val baseName = coverName.substringBeforeLast(".")
        val coverFile = coversDir.listFiles()?.firstOrNull { it.name.substringBeforeLast(".") == baseName && it.length() > 0L }
        coverFile?.absolutePath
    }

    actual suspend fun deleteCover(coverName: String): Boolean = withContext(Dispatchers.IO) {
        val kreader = getKReaderDir() ?: return@withContext false
        File(kreader, "covers/$coverName").delete()
    }

    actual suspend fun saveFont(fontFileName: String, bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        val kreader = getKReaderDir() ?: return@withContext null
        val fontsDir = File(kreader, "fonts").apply { mkdirs() }
        val fontFile = File(fontsDir, fontFileName)
        fontFile.writeBytes(bytes)
        fontFile.absolutePath
    }

    actual suspend fun persistFont(font: CustomFont): CustomFont = withContext(Dispatchers.IO) {
        val kreader = getKReaderDir() ?: return@withContext font
        val fontsDir = File(kreader, "fonts").apply { mkdirs() }
        val srcFile = File(font.path)
        if (srcFile.exists()) {
            val destFile = File(fontsDir, srcFile.name)
            srcFile.copyTo(destFile, overwrite = true)
            font.copy(path = destFile.absolutePath)
        } else {
            font
        }
    }

    actual suspend fun listFonts(): List<CustomFont> = withContext(Dispatchers.IO) {
        val kreader = getKReaderDir() ?: return@withContext emptyList()
        val fontsDir = File(kreader, "fonts")
        if (!fontsDir.exists() || !fontsDir.isDirectory) return@withContext emptyList()
        fontsDir.listFiles()?.filter {
            it.isFile && (it.name.endsWith(".ttf", ignoreCase = true) || it.name.endsWith(".otf", ignoreCase = true))
        }?.map {
            CustomFont(name = it.nameWithoutExtension, path = it.absolutePath)
        } ?: emptyList()
    }

    actual suspend fun deleteFont(fontFileName: String): Boolean = withContext(Dispatchers.IO) {
        val kreader = getKReaderDir() ?: return@withContext false
        val fontsDir = File(kreader, "fonts")
        var deleted = false
        if (fontsDir.exists()) {
            fontsDir.listFiles()?.forEach { file ->
                if (file.name.equals(fontFileName, ignoreCase = true) ||
                    file.nameWithoutExtension.equals(fontFileName, ignoreCase = true)
                ) {
                    if (file.delete()) deleted = true
                }
            }
        }
        deleted
    }
}
