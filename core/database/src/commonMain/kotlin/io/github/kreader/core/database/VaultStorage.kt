package io.github.kreader.core.database

import kotlinx.coroutines.flow.StateFlow

expect class VaultStorage {
    val activeVaultUriOrPath: StateFlow<String?>
    fun getActiveVault(): String?
    fun setActiveVault(uriOrPath: String?)
    fun getVaultName(): String?
    suspend fun listEpubs(): List<VaultEpubFile>
    suspend fun readConfig(fileName: String): String?
    suspend fun writeConfig(fileName: String, content: String)
    suspend fun deleteEpub(relativePath: String): Boolean
    suspend fun saveCover(coverName: String, bytes: ByteArray): String?
    suspend fun cacheExtractedCover(relativePath: String, localCoverPath: String): String?
    suspend fun getCoverPath(coverName: String): String?
    suspend fun deleteCover(coverName: String): Boolean
    suspend fun saveFont(fontFileName: String, bytes: ByteArray): String?
    suspend fun deleteFont(fontFileName: String): Boolean
}
