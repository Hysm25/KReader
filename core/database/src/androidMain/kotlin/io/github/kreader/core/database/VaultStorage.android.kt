package io.github.kreader.core.database

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.documentfile.provider.DocumentFile
import io.github.kreader.core.model.BookFormat
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
    actual var onVaultContentChanged: (() -> Unit)? = null

    private var treeContentObserver: ContentObserver? = null
    @Volatile
    private var suppressObserverUntilMs: Long = 0L

    private fun markSelfWrite(windowMs: Long = 2000L) {
        suppressObserverUntilMs = System.currentTimeMillis() + windowMs
    }

    init {
        registerVaultContentObserver(_activeVaultUriOrPath.value)
    }

    private fun registerVaultContentObserver(uriString: String?) {
        unregisterVaultContentObserver()
        if (uriString == null) return
        runCatching {
            val uri = Uri.parse(uriString)
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                private var lastTriggerTime = 0L
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    val now = System.currentTimeMillis()
                    if (now < suppressObserverUntilMs) return
                    if (uri?.toString()?.contains(".kreader") == true) return
                    if (now - lastTriggerTime > 800L) {
                        lastTriggerTime = now
                        onVaultContentChanged?.invoke()
                    }
                }
            }
            context.contentResolver.registerContentObserver(uri, true, observer)
            treeContentObserver = observer
        }
    }

    private fun unregisterVaultContentObserver() {
        treeContentObserver?.let {
            runCatching {
                context.contentResolver.unregisterContentObserver(it)
            }
            treeContentObserver = null
        }
    }

    private val fileMutexes = ConcurrentHashMap<String, Mutex>()
    private fun getFileMutex(fileName: String): Mutex =
        fileMutexes.getOrPut(fileName) { Mutex() }
    private val coverMutex = Mutex()
    private val configFileUriCache = ConcurrentHashMap<String, Uri>()

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
        configFileUriCache.clear()
        registerVaultContentObserver(uriOrPath)
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

    actual suspend fun listBooks(): List<VaultBookFile> = scanVault().books

    actual suspend fun scanVault(): VaultScanResult = withContext(Dispatchers.IO) {
        val root = getRootDoc() ?: return@withContext VaultScanResult()
        val kreaderDir = root.findFile(".kreader")
        if (kreaderDir != null) {
            cleanupStrayDuplicates(kreaderDir)
        }
        val books = mutableListOf<VaultBookFile>()
        val seriesCovers = mutableMapOf<String, BookSource>()
        scanDocumentTree(root, "", books, seriesCovers)
        VaultScanResult(books = books, seriesCovers = seriesCovers)
    }

    actual suspend fun readBytesFromSource(source: BookSource): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            when (source) {
                is BookSource.FilePath -> File(source.path).takeIf { it.isFile && it.length() > 0L }?.readBytes()
                is BookSource.ContentUri -> context.contentResolver.openInputStream(Uri.parse(source.uriString))?.use { it.readBytes() }
                is BookSource.InactiveSource -> null
            }
        }.getOrNull()
    }

    private fun scanDocumentTree(
        dir: DocumentFile,
        parentPath: String,
        results: MutableList<VaultBookFile>,
        seriesCovers: MutableMap<String, BookSource>
    ) {
        val children = dir.listFiles()
        var customCoverSource: BookSource? = null
        var hasCbzInDir = false

        for (child in children) {
            val name = child.name.orEmpty()
            if (child.isDirectory) {
                if (name == ".kreader") continue
                val subPath = if (parentPath.isEmpty()) name else "$parentPath/$name"
                scanDocumentTree(child, subPath, results, seriesCovers)
            } else if (child.isFile) {
                val ext = name.substringAfterLast('.', "").lowercase()
                val format = BookFormat.fromExtension(ext)
                if (format != null) {
                    if (format == BookFormat.CBZ) {
                        hasCbzInDir = true
                    }
                    val relPath = if (parentPath.isEmpty()) name else "$parentPath/$name"
                    results.add(
                        VaultBookFile(
                            relativePath = relPath,
                            displayName = name,
                            source = BookSource.ContentUri(
                                uriString = child.uri.toString(),
                                displayName = name
                            ),
                            format = format,
                            fileSizeBytes = child.length(),
                            lastModified = child.lastModified()
                        )
                    )
                } else if (parentPath.isNotEmpty() && isCustomSeriesCoverFile(name) && customCoverSource == null) {
                    customCoverSource = BookSource.ContentUri(
                        uriString = child.uri.toString(),
                        displayName = name
                    )
                }
            }
        }

        if (parentPath.isNotEmpty() && hasCbzInDir && customCoverSource != null) {
            seriesCovers[parentPath] = customCoverSource
        }
    }

    private fun isCustomSeriesCoverFile(name: String): Boolean {
        val stem = name.substringBeforeLast('.', "")
        val ext = name.substringAfterLast('.', "").lowercase()
        return stem.equals("cover", ignoreCase = true) &&
            ext in setOf("jpg", "jpeg", "png", "webp", "bmp")
    }

    actual suspend fun readConfig(fileName: String): String? {
        val mutex = getFileMutex(fileName)
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                val cachedUri = configFileUriCache[fileName]
                if (cachedUri != null) {
                    val content = runCatching {
                        context.contentResolver.openInputStream(cachedUri)?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()
                    if (content != null) return@withContext content
                    configFileUriCache.remove(fileName)
                }
                val kreaderDir = getOrCreateKReaderDir() ?: return@withContext null
                val file = kreaderDir.findFile(fileName) ?: return@withContext null
                configFileUriCache[fileName] = file.uri
                runCatching {
                    context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
        }
    }

    actual suspend fun writeConfig(fileName: String, content: String): Unit {
        val mutex = getFileMutex(fileName)
        mutex.withLock {
            markSelfWrite()
            withContext(Dispatchers.IO) {
                val cachedUri = configFileUriCache[fileName]
                if (cachedUri != null) {
                    val success = runCatching {
                        context.contentResolver.openOutputStream(cachedUri, "wt")?.use { out ->
                            out.write(content.toByteArray(Charsets.UTF_8))
                            out.flush()
                        }
                        true
                    }.getOrDefault(false)
                    if (success) return@withContext
                    configFileUriCache.remove(fileName)
                }

                val kreaderDir = getOrCreateKReaderDir() ?: return@withContext

                // Overwrite existing file or create once if absent
                val file = kreaderDir.findFile(fileName) ?: kreaderDir.createFile("application/json", fileName) ?: return@withContext
                configFileUriCache[fileName] = file.uri
                runCatching {
                    context.contentResolver.openOutputStream(file.uri, "wt")?.use { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                }
            }
        }
    }

    actual suspend fun deleteBookFile(relativePath: String): Boolean = withContext(Dispatchers.IO) {
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
            val cleanBase = coverCacheBaseName(relativePath)
            val coverName = "$cleanBase.$ext"
            val bytes = localFile.readBytes()
            saveCover(coverName, bytes)
        }.getOrNull()
    }

    actual suspend fun saveCover(coverName: String, bytes: ByteArray): String? = coverMutex.withLock {
        markSelfWrite()
        withContext(Dispatchers.IO) {
            // 1. Save inside vault .kreader/covers/
            runCatching {
                val coversDir = getOrCreateSubDir("covers")
                if (coversDir != null) {
                    val nameWithoutExt = coverName.substringBeforeLast(".")
                    val ext = coverName.substringAfterLast(".", "jpg")
                    val strayRegex = Regex("^" + Regex.escape(nameWithoutExt) + """( \(\d+\))\.""" + Regex.escape(ext) + "$")
                    for (f in coversDir.listFiles()) {
                        val fn = f.name.orEmpty()
                        if (strayRegex.matches(fn)) {
                            runCatching { f.delete() }
                        }
                    }

                    val coverFile = coversDir.findFile(coverName) ?: coversDir.createFile("image/jpeg", coverName)
                    if (coverFile != null) {
                        context.contentResolver.openOutputStream(coverFile.uri, "wt")?.use { out ->
                            out.write(bytes)
                            out.flush()
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
    }

    actual suspend fun getCoverPath(coverName: String): String? = withContext(Dispatchers.IO) {
        val cleanBase = stripBookExtension(coverName).substringBeforeLast(".")
        val cacheCoversDir = File(context.cacheDir, "covers")
        val localFiles = cacheCoversDir.listFiles()
        val matchingLocal = localFiles?.firstOrNull { file ->
            val b = stripBookExtension(file.name).substringBeforeLast(".")
            b == cleanBase && file.length() > 0L
        }
        if (matchingLocal != null) {
            return@withContext matchingLocal.absolutePath
        }

        // Restore from vault .kreader/covers/ if not present in cache
        val coversDir = getOrCreateSubDir("covers") ?: return@withContext null
        val coverDoc = coversDir.listFiles().firstOrNull { doc ->
            val b = doc.name?.let { stripBookExtension(it).substringBeforeLast(".") }
            b == cleanBase
        } ?: return@withContext null
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
            val cleanBase = stripBookExtension(coverName).substringBeforeLast(".")
            val cacheCover = File(context.cacheDir, "covers")
            cacheCover.listFiles()?.forEach { file ->
                val b = stripBookExtension(file.name).substringBeforeLast(".")
                if (b == cleanBase) file.delete()
            }

            val coversDir = getOrCreateSubDir("covers")
            coversDir?.listFiles()?.forEach { doc ->
                val b = doc.name?.let { stripBookExtension(it).substringBeforeLast(".") }
                if (b == cleanBase) doc.delete()
            }
            true
        }.getOrDefault(false)
    }

    actual suspend fun saveFont(fontFileName: String, bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        markSelfWrite()
        // 1. Save in vault .kreader/fonts/
        runCatching {
            val fontsDir = getOrCreateSubDir("fonts")
            if (fontsDir != null) {
                val mimeType = when (fontFileName.substringAfterLast('.', "").lowercase()) {
                    "otf" -> "font/otf"
                    "woff" -> "font/woff"
                    "woff2" -> "font/woff2"
                    else -> "font/ttf"
                }
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
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext !in setOf("ttf", "otf", "woff", "woff2")) continue
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
        markSelfWrite()
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

    actual suspend fun exportMarkdown(fileName: String, markdownContent: String): String? = withContext(Dispatchers.IO) {
        markSelfWrite()
        runCatching {
            val root = getRootDoc() ?: return@withContext null
            val notesDir = root.findFile("KReader Notes") ?: root.createDirectory("KReader Notes") ?: root
            val safeName = if (fileName.endsWith(".md", ignoreCase = true)) fileName else "$fileName.md"
            val existing = notesDir.findFile(safeName)
            if (existing != null) {
                existing.delete()
            }
            val doc = notesDir.createFile("text/markdown", safeName) ?: return@withContext null
            context.contentResolver.openOutputStream(doc.uri, "wt")?.use { out ->
                out.write(markdownContent.toByteArray(Charsets.UTF_8))
            }
            "KReader Notes/$safeName"
        }.getOrNull()
    }

    actual suspend fun extractEpubTextContent(source: BookSource): String? = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        val workingFile = when (source) {
            is BookSource.FilePath -> File(source.path)
            is BookSource.ContentUri -> {
                val input = runCatching {
                    context.contentResolver.openInputStream(Uri.parse(source.uriString))
                }.getOrNull() ?: return@withContext null
                val temp = File.createTempFile("kreader_export_", ".epub", context.cacheDir)
                tempFile = temp
                input.use { inStream ->
                    temp.outputStream().use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }
                temp
            }
            else -> return@withContext null
        }

        try {
            if (!workingFile.exists() || workingFile.length() == 0L) return@withContext null
            extractEpubFromZip(workingFile)
        } finally {
            tempFile?.delete()
        }
    }

    private fun extractEpubFromZip(file: File): String? {
        return runCatching {
            java.util.zip.ZipFile(file).use { zip ->
                val containerEntry = zip.getEntry("META-INF/container.xml")
                    ?: zip.entries().asSequence().firstOrNull { it.name.equals("META-INF/container.xml", ignoreCase = true) }
                    ?: return null
                val containerXml = zip.getInputStream(containerEntry).bufferedReader().readText()
                val opfPath = Regex("""full-path=["']([^"']+)["']""").find(containerXml)?.groupValues?.get(1) ?: "OEBPS/content.opf"
                val opfDir = if (opfPath.contains('/')) opfPath.substringBeforeLast('/') else ""

                val opfEntry = zip.getEntry(opfPath)
                    ?: zip.entries().asSequence().firstOrNull { it.name.equals(opfPath, ignoreCase = true) }
                    ?: return null
                val opfXml = zip.getInputStream(opfEntry).bufferedReader().readText()

                val manifestMap = mutableMapOf<String, String>()
                val itemRegex = Regex("""<item\s+[^>]*>""", RegexOption.IGNORE_CASE)
                itemRegex.findAll(opfXml).forEach { match ->
                    val tag = match.value
                    val id = Regex("""id=["']([^"']+)["']""").find(tag)?.groupValues?.get(1)
                    val href = Regex("""href=["']([^"']+)["']""").find(tag)?.groupValues?.get(1)
                    if (id != null && href != null) {
                        manifestMap[id] = href
                    }
                }

                val spineXml = Regex("""<spine[^>]*>(.*?)</spine>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                    .find(opfXml)?.groupValues?.get(1) ?: ""
                val spineIds = Regex("""<itemref\s+[^>]*idref=["']([^"']+)["']""").findAll(spineXml).map { it.groupValues[1] }.toList()

                val chapters = mutableListOf<String>()
                for (idref in spineIds) {
                    val href = manifestMap[idref] ?: continue
                    val zipPath = resolveZipPath(opfDir, href)
                    val entry = zip.getEntry(zipPath)
                        ?: zip.entries().asSequence().firstOrNull { it.name.equals(zipPath, ignoreCase = true) }
                        ?: continue

                    val xhtml = zip.getInputStream(entry).bufferedReader().readText()
                    val md = convertHtmlToMarkdown(xhtml)
                    if (md.isNotBlank()) {
                        chapters.add(md)
                    }
                }

                if (chapters.isEmpty()) null else chapters.joinToString("\n\n---\n\n")
            }
        }.getOrNull()
    }

    private fun resolveZipPath(baseDir: String, href: String): String {
        val cleanHref = href.substringBefore('#').substringBefore('?')
        val combined = if (baseDir.isEmpty()) cleanHref else "$baseDir/$cleanHref"
        val parts = mutableListOf<String>()
        for (part in combined.split('/')) {
            when (part) {
                "", "." -> continue
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(part)
            }
        }
        return parts.joinToString("/")
    }
}
