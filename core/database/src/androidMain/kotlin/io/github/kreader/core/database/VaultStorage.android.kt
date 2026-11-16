package io.github.kreader.core.database

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.github.kreader.core.model.BookSource
import io.github.kreader.core.model.CustomFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

actual class VaultStorage(private val context: Context) {

    private val prefs = context.getSharedPreferences("kreader_vault_prefs", Context.MODE_PRIVATE)
    private val _activeVaultUriOrPath = MutableStateFlow<String?>(loadInitialVault())
    actual val activeVaultUriOrPath: StateFlow<String?> = _activeVaultUriOrPath.asStateFlow()

    private val fileMutexes = ConcurrentHashMap<String, Mutex>()
    private fun getFileMutex(fileName: String): Mutex =
        fileMutexes.getOrPut(fileName) { Mutex() }

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

    private val subDirMutex = Mutex()

    private fun getOrCreateKReaderDir(): DocumentFile? {
        val root = getRootDoc() ?: return null
        return root.findFile(".kreader") ?: root.createDirectory(".kreader")
    }

    private suspend fun getOrCreateSubDir(dirName: String): DocumentFile? = subDirMutex.withLock {
        withContext(Dispatchers.IO) {
            val kreaderDir = getOrCreateKReaderDir() ?: return@withContext null
            kreaderDir.findFile(dirName) ?: kreaderDir.createDirectory(dirName)
        }
    }

    private fun cleanupStrayDuplicates(kreaderDir: DocumentFile) {
        runCatching {
            val strayPattern = Regex("""^.* \(\d+\)(\.json)?$""")
            for (file in kreaderDir.listFiles()) {
                val name = file.name.orEmpty()
                if (strayPattern.matches(name)) {
                    file.delete()
                }
            }
        }
    }

    actual suspend fun listEpubs(): List<VaultEpubFile> = withContext(Dispatchers.IO) {
        val root = getRootDoc() ?: return@withContext emptyList()
        val kreaderDir = root.findFile(".kreader")
        if (kreaderDir != null) {
            cleanupStrayDuplicates(kreaderDir)
        }
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

    actual suspend fun readConfig(fileName: String): String? {
        val mutex = getFileMutex(fileName)
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                val kreaderDir = getOrCreateKReaderDir() ?: return@withContext null
                val file = kreaderDir.findFile(fileName) ?: return@withContext null
                runCatching {
                    context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
        }
    }

    actual suspend fun writeConfig(fileName: String, content: String): Unit {
        val mutex = getFileMutex(fileName)
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val kreaderDir = getOrCreateKReaderDir() ?: return@withContext

                // Delete any stray duplicate files like "fileName (1).json"
                val nameWithoutExt = fileName.substringBeforeLast(".")
                val ext = fileName.substringAfterLast(".", "json")
                val strayRegex = Regex("^" + Regex.escape(nameWithoutExt) + """( \(\d+\))\.""" + Regex.escape(ext) + "$")
                for (f in kreaderDir.listFiles()) {
                    val fn = f.name.orEmpty()
                    if (strayRegex.matches(fn)) {
                        runCatching { f.delete() }
                    }
                }

                // Overwrite existing file or create once if absent
                val file = kreaderDir.findFile(fileName) ?: kreaderDir.createFile("application/json", fileName) ?: return@withContext
                runCatching {
                    context.contentResolver.openOutputStream(file.uri, "wt")?.use { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                }
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

        // 2. Cache in app cache directory for fast local loading in UI (atomic write)
        runCatching {
            val cacheCoversDir = File(context.cacheDir, "covers").apply { mkdirs() }
            val tempFile = File(cacheCoversDir, "$coverName.tmp")
            tempFile.writeBytes(bytes)
            val localCover = File(cacheCoversDir, coverName)
            if (tempFile.renameTo(localCover)) {
                localCover.absolutePath
            } else {
                tempFile.copyTo(localCover, overwrite = true)
                tempFile.delete()
                localCover.absolutePath
            }
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
                val mimeType = if (fontFileName.endsWith(".otf", ignoreCase = true)) "font/otf" else "font/ttf"
                val fontDoc = fontsDir.findFile(fontFileName) ?: fontsDir.createFile(mimeType, fontFileName)
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

    actual suspend fun persistFont(font: CustomFont): CustomFont = withContext(Dispatchers.IO) {
        runCatching {
            val srcFile = File(font.path)
            if (srcFile.exists() && srcFile.length() > 0L) {
                val bytes = srcFile.readBytes()
                val fileName = srcFile.name
                val savedPath = saveFont(fileName, bytes)
                if (savedPath != null) font.copy(path = savedPath) else font
            } else {
                font
            }
        }.getOrDefault(font)
    }

    actual suspend fun listFonts(): List<CustomFont> = withContext(Dispatchers.IO) {
        runCatching {
            val fontsDir = getOrCreateSubDir("fonts") ?: return@withContext emptyList()
            val localFontsDir = File(context.filesDir, "fonts").apply { mkdirs() }
            val fontDocs = fontsDir.listFiles()
            val result = mutableListOf<CustomFont>()
            for (doc in fontDocs) {
                val name = doc.name ?: continue
                if (!name.endsWith(".ttf", ignoreCase = true) && !name.endsWith(".otf", ignoreCase = true)) continue
                val localFile = File(localFontsDir, name)
                if (!localFile.exists() || localFile.length() == 0L || localFile.length() != doc.length()) {
                    context.contentResolver.openInputStream(doc.uri)?.use { input ->
                        localFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                if (localFile.exists() && localFile.length() > 0L) {
                    val fontName = name.substringBeforeLast(".")
                    result.add(CustomFont(name = fontName, path = localFile.absolutePath))
                }
            }
            result
        }.getOrDefault(emptyList())
    }

    actual suspend fun deleteFont(fontFileName: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val localFontsDir = File(context.filesDir, "fonts")
            localFontsDir.listFiles()?.forEach { file ->
                if (file.name.equals(fontFileName, ignoreCase = true) ||
                    file.nameWithoutExtension.equals(fontFileName, ignoreCase = true)
                ) {
                    file.delete()
                }
            }

            val fontsDir = getOrCreateSubDir("fonts")
            if (fontsDir != null) {
                for (doc in fontsDir.listFiles()) {
                    val name = doc.name ?: continue
                    if (name.equals(fontFileName, ignoreCase = true) ||
                        name.substringBeforeLast(".").equals(fontFileName, ignoreCase = true)
                    ) {
                        doc.delete()
                    }
                }
            }
            true
        }.getOrDefault(false)
    }
}
