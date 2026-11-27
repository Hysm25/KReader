package io.github.kreader.engine.cbz

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.kreader.core.model.BookSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves CBZ archives to local `File` handles for O(1) `ZipFile` random page access.
 * - `BookSource.FilePath` is opened directly in place with zero copying.
 * - `BookSource.ContentUri` (SAF) streams are copied atomically to a bounded app cache directory
 *   and evicted LRU-style when exceeding [MAX_CACHE_BYTES] or [MAX_CACHED_ARCHIVES].
 */
object CbzArchiveCache {

    private const val CACHE_DIR_NAME = "cbz_archives"
    private const val MAX_CACHE_BYTES = 256L * 1024L * 1024L
    private const val MAX_CACHED_ARCHIVES = 6
    private const val COPY_BUFFER_BYTES = 64 * 1024

    private val keyMutexes = ConcurrentHashMap<String, Mutex>()

    suspend fun resolveArchiveFile(context: Context, source: BookSource): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                when (source) {
                    is BookSource.FilePath -> {
                        val file = File(source.path)
                        require(file.exists() && file.isFile && file.length() > 0L) {
                            "CBZ file does not exist or is empty: ${source.path}"
                        }
                        file
                    }

                    is BookSource.ContentUri -> {
                        resolveContentUriToCache(context.applicationContext, source)
                    }

                    is BookSource.InactiveSource -> {
                        error("Book source is inactive: ${source.displayName}")
                    }
                }
            }
        }

    private suspend fun resolveContentUriToCache(
        context: Context,
        source: BookSource.ContentUri
    ): File {
        val uri = Uri.parse(source.uriString)
        val expectedSize = queryContentSize(context, uri)
        val cacheKey = sha256("${source.uriString}|${source.displayName}|$expectedSize")
        val mutex = keyMutexes.getOrPut(cacheKey) { Mutex() }

        return mutex.withLock {
            val cacheDir = File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
            val targetFile = File(cacheDir, "$cacheKey.cbz")

            if (targetFile.exists() && targetFile.length() > 0L &&
                (expectedSize <= 0L || targetFile.length() == expectedSize)
            ) {
                targetFile.setLastModified(System.currentTimeMillis())
                return@withLock targetFile
            }

            val tempFile = File(cacheDir, "$cacheKey.cbz.tmp")
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: error("Unable to open CBZ stream for ${source.displayName}")
                inputStream.use { input ->
                    tempFile.outputStream().buffered(COPY_BUFFER_BYTES).use { output ->
                        val buffer = ByteArray(COPY_BUFFER_BYTES)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                    }
                }
                check(tempFile.exists() && tempFile.length() > 0L) {
                    "Copied CBZ archive is empty: ${source.displayName}"
                }
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                targetFile.setLastModified(System.currentTimeMillis())
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }

            evictOldCacheEntries(cacheDir, protectedFile = targetFile)
            targetFile
        }
    }

    private fun queryContentSize(context: Context, uri: Uri): Long {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && cursor.moveToFirst() && !cursor.isNull(idx)) {
                    cursor.getLong(idx)
                } else {
                    -1L
                }
            } ?: -1L
        }.getOrDefault(-1L)
    }

    private fun evictOldCacheEntries(cacheDir: File, protectedFile: File) {
        runCatching {
            val archives = cacheDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".cbz", ignoreCase = true) }
                ?.sortedBy { it.lastModified() }
                ?.toMutableList()
                ?: return

            var totalBytes = archives.sumOf { it.length() }
            while (archives.size > MAX_CACHED_ARCHIVES || totalBytes > MAX_CACHE_BYTES) {
                val victim = archives.firstOrNull { it.absolutePath != protectedFile.absolutePath } ?: break
                val victimSize = victim.length()
                if (victim.delete()) {
                    totalBytes -= victimSize
                }
                archives.remove(victim)
            }
        }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
