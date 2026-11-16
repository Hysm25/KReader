package io.github.kreader.core.database

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.github.kreader.core.model.BookSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

actual class VaultStorage(private val context: Context) {

    private val prefs = context.getSharedPreferences("kreader_vault_prefs", Context.MODE_PRIVATE)
    private val _activeVaultUriOrPath = MutableStateFlow<String?>(loadInitialVault())
    actual val activeVaultUriOrPath: StateFlow<String?> = _activeVaultUriOrPath.asStateFlow()

    private fun loadInitialVault(): String? {
        val saved = prefs.getString("active_vault_uri", null) ?: return null
        return runCatching {
            val uri = Uri.parse(saved)
            val hasPermission = context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission
            }
            if (hasPermission) saved else null
        }.getOrNull()
    }

    actual fun getActiveVault(): String? {
        return _activeVaultUriOrPath.value
    }

    actual fun setActiveVault(uriOrPath: String?) {
        prefs.edit().apply {
            if (uriOrPath != null) {
                putString("active_vault_uri", uriOrPath)
            } else {
                remove("active_vault_uri")
            }
            apply()
        }
        _activeVaultUriOrPath.value = uriOrPath
    }

    actual fun getVaultName(): String? {
        val uriStr = getActiveVault() ?: return null
        return runCatching {
            val uri = Uri.parse(uriStr)
            DocumentFile.fromTreeUri(context, uri)?.name ?: "Books"
        }.getOrDefault("Books")
    }

    private fun getRootDoc(): DocumentFile? {
        val uriStr = getActiveVault() ?: return null
        return runCatching {
            DocumentFile.fromTreeUri(context, Uri.parse(uriStr))
        }.getOrNull()
    }

    private fun getOrCreateKReaderDir(): DocumentFile? {
        val root = getRootDoc() ?: return null
        return root.findFile(".kreader") ?: root.createDirectory(".kreader")
    }

    private fun getOrCreateSubDir(dirName: String): DocumentFile? {
        val kreaderDir = getOrCreateKReaderDir() ?: return null
        return kreaderDir.findFile(dirName) ?: kreaderDir.createDirectory(dirName)
    }

    actual suspend fun listEpubs(): List<VaultEpubFile> = withContext(Dispatchers.IO) {
        val root = getRootDoc() ?: return@withContext emptyList()
        val results = mutableListOf<VaultEpubFile>()
        scanDocumentTree(root, "", results)
        results
    }

    private fun scanDocumentTree(
        dir: DocumentFile,
        parentPath: String,
        results: MutableList<VaultEpubFile>
    ) {
        val children = dir.listFiles()
        for (child in children) {
            val name = child.name.orEmpty()
            if (child.isDirectory) {
                if (name == ".kreader") continue
                val subPath = if (parentPath.isEmpty()) name else "$parentPath/$name"
                scanDocumentTree(child, subPath, results)
            } else if (child.isFile && name.endsWith(".epub", ignoreCase = true)) {
                val relPath = if (parentPath.isEmpty()) name else "$parentPath/$name"
                results.add(
                    VaultEpubFile(
                        relativePath = relPath,
                        displayName = name,
                        source = BookSource.ContentUri(
                            uriString = child.uri.toString(),
                            displayName = name
                        ),
                        fileSizeBytes = child.length(),
                        lastModified = child.lastModified()
                    )
                )
            }
        }
    }

    actual suspend fun readConfig(fileName: String): String? = withContext(Dispatchers.IO) {
        val kreaderDir = getOrCreateKReaderDir() ?: return@withContext null
        val file = kreaderDir.findFile(fileName) ?: return@withContext null
        runCatching {
            context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
    }

    actual suspend fun writeConfig(fileName: String, content: String): Unit = withContext(Dispatchers.IO) {
        val kreaderDir = getOrCreateKReaderDir() ?: return@withContext
        val file = kreaderDir.findFile(fileName) ?: kreaderDir.createFile("application/json", fileName) ?: return@withContext
        runCatching {
            context.contentResolver.openOutputStream(file.uri, "wt")?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            }
        }
    }

    actual suspend fun deleteEpub(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        val root = getRootDoc() ?: return@withContext false
        val parts = relativePath.split("/")
        var current: DocumentFile = root
        for (i in 0 until parts.size - 1) {
            current = current.findFile(parts[i]) ?: return@withContext false
        }
        val target = current.findFile(parts.last()) ?: return@withContext false
        target.delete()
    }

    actual suspend fun saveCover(coverName: String, bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        // 1. Save inside vault .kreader/covers/
        runCatching {
            val coversDir = getOrCreateSubDir("covers")
            if (coversDir != null) {
                val coverFile = coversDir.findFile(coverName) ?: coversDir.createFile("image/jpeg", coverName)
                if (coverFile != null) {
                    context.contentResolver.openOutputStream(coverFile.uri, "wt")?.use { out ->
                        out.write(bytes)
                    }
                }
            }
        }

        // 2. Cache in app cache directory for fast local loading in UI
        runCatching {
            val cacheCoversDir = File(context.cacheDir, "covers").apply { mkdirs() }
            val localCover = File(cacheCoversDir, coverName)
            localCover.writeBytes(bytes)
            localCover.absolutePath
        }.getOrNull()
    }

    actual suspend fun getCoverPath(coverName: String): String? = withContext(Dispatchers.IO) {
        val baseName = coverName.substringBeforeLast(".")
        val cacheCoversDir = File(context.cacheDir, "covers")
        val localFiles = cacheCoversDir.listFiles()
        val matchingLocal = localFiles?.firstOrNull { it.name.substringBeforeLast(".") == baseName && it.length() > 0L }
        if (matchingLocal != null) {
            return@withContext matchingLocal.absolutePath
        }

        // Restore from vault .kreader/covers/ if not present in cache
        val coversDir = getOrCreateSubDir("covers") ?: return@withContext null
        val coverDoc = coversDir.listFiles().firstOrNull { it.name?.substringBeforeLast(".") == baseName } ?: return@withContext null
        runCatching {
            cacheCoversDir.mkdirs()
            val localCover = File(cacheCoversDir, coverDoc.name ?: coverName)
            context.contentResolver.openInputStream(coverDoc.uri)?.use { input ->
                localCover.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (localCover.exists() && localCover.length() > 0L) localCover.absolutePath else null
        }.getOrNull()
    }

    actual suspend fun deleteCover(coverName: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val cacheCover = File(context.cacheDir, "covers/$coverName")
            if (cacheCover.exists()) cacheCover.delete()

            val coversDir = getOrCreateSubDir("covers")
            coversDir?.findFile(coverName)?.delete()
            true
        }.getOrDefault(false)
    }

    actual suspend fun saveFont(fontFileName: String, bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        // 1. Save in vault .kreader/fonts/
        runCatching {
            val fontsDir = getOrCreateSubDir("fonts")
            if (fontsDir != null) {
                val fontDoc = fontsDir.findFile(fontFileName) ?: fontsDir.createFile("font/ttf", fontFileName)
                if (fontDoc != null) {
                    context.contentResolver.openOutputStream(fontDoc.uri, "wt")?.use { out ->
                        out.write(bytes)
                    }
                }
            }
        }

        // 2. Cache in filesDir/fonts/ for Readium CSS font-face injection
        runCatching {
            val localFontsDir = File(context.filesDir, "fonts").apply { mkdirs() }
            val localFont = File(localFontsDir, fontFileName)
            localFont.writeBytes(bytes)
            localFont.absolutePath
        }.getOrNull()
    }

    actual suspend fun deleteFont(fontFileName: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val localFont = File(context.filesDir, "fonts/$fontFileName")
            if (localFont.exists()) localFont.delete()

            val fontsDir = getOrCreateSubDir("fonts")
            fontsDir?.findFile(fontFileName)?.delete()
            true
        }.getOrDefault(false)
    }
}
