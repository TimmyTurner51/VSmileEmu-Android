package com.vsmileemu.android.data.repository

import android.content.Context
import android.net.Uri
import com.vsmileemu.android.data.model.Rom
import com.vsmileemu.android.util.StorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Repository for managing ROM files
 */
class RomRepository(
    private val context: Context,
    private val storageHelper: StorageHelper = StorageHelper(context)
) {
    
    /**
     * Scan for ROM files in the storage directory
     */
    fun scanRoms(baseUri: Uri): Flow<Result<List<Rom>>> = flow {
        try {
            val romFiles = storageHelper.getRomFiles(baseUri)
            
            val roms = romFiles.mapNotNull { file ->
                val fileName = file.name ?: return@mapNotNull null
                val lastModified = file.lastModified()
                val fileSize = file.length()
                
                // Check if save file exists
                val hasSaveData = storageHelper.getSaveFile(baseUri, fileName) != null
                
                Rom(
                    uri = file.uri,
                    fileName = fileName,
                    fileSize = fileSize,
                    lastModified = lastModified,
                    hasSaveData = hasSaveData
                )
            }
            
            emit(Result.success(roms))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Load ROM file data
     */
    suspend fun loadRom(uri: Uri): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val data = storageHelper.readFile(uri)
            if (data != null) {
                Result.success(data)
            } else {
                Result.failure(Exception("Failed to read ROM file"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Load save data for a ROM
     */
    suspend fun loadSaveData(baseUri: Uri, romName: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val saveFile = storageHelper.getSaveFile(baseUri, romName)
            saveFile?.uri?.let { storageHelper.readFile(it) }
        }
    
    /**
     * Save game data for a ROM
     */
    suspend fun saveSaveData(baseUri: Uri, romName: String, data: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            val saveFile = storageHelper.getSaveFile(baseUri, romName)
                ?: storageHelper.createSaveFile(baseUri, romName)
            
            saveFile?.uri?.let { storageHelper.writeFile(it, data) } ?: false
        }
    
    /**
     * Delete save data for a ROM
     */
    suspend fun deleteSaveData(baseUri: Uri, romName: String): Boolean =
        withContext(Dispatchers.IO) {
            val saveFile = storageHelper.getSaveFile(baseUri, romName)
            saveFile?.uri?.let { storageHelper.deleteFile(it) } ?: false
        }
    
    /**
     * Get list of ROMs (synchronous version for simple UI)
     */
    suspend fun getRomList(baseUri: Uri): List<Rom> = withContext(Dispatchers.IO) {
        val romFiles = storageHelper.getRomFiles(baseUri)
        romFiles.mapNotNull { file ->
            val fileName = file.name ?: return@mapNotNull null
            val hasSaveData = storageHelper.getSaveFile(baseUri, fileName) != null
            Rom(
                uri = file.uri,
                fileName = fileName,
                fileSize = file.length(),
                lastModified = file.lastModified(),
                hasSaveData = hasSaveData
            )
        }
    }
    
    /**
     * Check if BIOS file exists
     */
    suspend fun hasBios(baseUri: Uri): Boolean = withContext(Dispatchers.IO) {
        storageHelper.getBiosUri(baseUri) != null
    }
    
    /**
     * Load BIOS data
     */
    suspend fun loadBios(baseUri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        storageHelper.getBiosUri(baseUri)?.let { biosUri ->
            storageHelper.readFile(biosUri)
        }
    }
}



