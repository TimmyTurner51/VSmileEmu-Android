package com.vsmileemu.android.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helper class for Storage Access Framework operations
 */
class StorageHelper(private val context: Context) {
    
    companion object {
        const val REQUEST_CODE_SELECT_DIRECTORY = 100
        
        // Folder names within the user-selected directory
        const val FOLDER_BIOS = "bios"
        const val FOLDER_ROMS = "roms"
        const val FOLDER_SAVES = "saves"
        const val FOLDER_SCREENSHOTS = "screenshots"
        
        // Valid ROM/BIOS file extensions (case-insensitive)
        private val ROM_EXTENSIONS = setOf("bin", "rom", "v.smile", "dat")
        
        // Common BIOS file names to look for
        private val BIOS_FILENAMES = setOf(
            "vsmile_bios.bin",
            "bios.bin", 
            "system.bin",
            "vsmile.bin",
            "vsmile_bios.rom",
            "bios.rom"
        )
    }
    
    /**
     * Create an intent to select a directory
     */
    fun createSelectDirectoryIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        }
    }
    
    /**
     * Take persistent permissions for a directory URI
     */
    fun takePersistablePermissions(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        
        context.contentResolver.takePersistableUriPermission(uri, flags)
    }
    
    /**
     * Release permissions for a directory URI
     */
    fun releasePersistablePermissions(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        
        try {
            context.contentResolver.releasePersistableUriPermission(uri, flags)
        } catch (e: SecurityException) {
            // Permission already released
        }
    }
    
    /**
     * Create necessary folders in the selected directory
     */
    suspend fun createFolders(baseUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val baseDir = DocumentFile.fromTreeUri(context, baseUri)
                ?: return@withContext Result.failure(Exception("Invalid base directory"))
            
            val foldersToCreate = listOf(
                FOLDER_BIOS,
                FOLDER_ROMS,
                FOLDER_SAVES,
                FOLDER_SCREENSHOTS
            )
            
            for (folderName in foldersToCreate) {
                if (baseDir.findFile(folderName) == null) {
                    baseDir.createDirectory(folderName)
                        ?: return@withContext Result.failure(
                            Exception("Failed to create folder: $folderName")
                        )
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get a specific folder in the base directory
     */
    fun getFolder(baseUri: Uri, folderName: String): DocumentFile? {
        val baseDir = DocumentFile.fromTreeUri(context, baseUri) ?: return null
        return baseDir.findFile(folderName)
    }
    
    /**
     * Check if BIOS file exists
     */
    fun biosExists(baseUri: Uri): Boolean {
        return getBiosUri(baseUri) != null
    }
    
    /**
     * Get BIOS file URI - searches for any recognized BIOS filename
     */
    fun getBiosUri(baseUri: Uri): Uri? {
        val biosFolder = getFolder(baseUri, FOLDER_BIOS) ?: return null
        
        // First, try exact matches for known BIOS filenames
        for (biosName in BIOS_FILENAMES) {
            biosFolder.findFile(biosName)?.let { return it.uri }
        }
        
        // If no exact match, look for any file with valid ROM extension
        // (user might have renamed their BIOS file)
        biosFolder.listFiles().firstOrNull { file ->
            file.isFile && file.name?.let { name ->
                ROM_EXTENSIONS.any { ext -> name.endsWith(".$ext", ignoreCase = true) }
            } == true
        }?.let { return it.uri }
        
        return null
    }
    
    /**
     * Get ROM files from the roms folder
     * Supports multiple file extensions: .bin, .rom, .v.smile, .dat
     */
    suspend fun getRomFiles(baseUri: Uri): List<DocumentFile> = withContext(Dispatchers.IO) {
        val romsFolder = getFolder(baseUri, FOLDER_ROMS) ?: return@withContext emptyList()
        
        romsFolder.listFiles()
            .filter { file ->
                file.isFile && file.name?.let { name ->
                    ROM_EXTENSIONS.any { ext -> name.endsWith(".$ext", ignoreCase = true) }
                } == true
            }
            .sortedBy { it.name }
    }
    
    /**
     * Strip ROM extension from filename
     */
    private fun stripRomExtension(romName: String): String {
        var baseName = romName
        ROM_EXTENSIONS.forEach { ext ->
            if (baseName.endsWith(".$ext", ignoreCase = true)) {
                baseName = baseName.substring(0, baseName.length - ext.length - 1)
            }
        }
        return baseName
    }
    
    /**
     * Get save file for a ROM
     */
    fun getSaveFile(baseUri: Uri, romName: String): DocumentFile? {
        val savesFolder = getFolder(baseUri, FOLDER_SAVES) ?: return null
        val saveFileName = stripRomExtension(romName) + ".sav"
        return savesFolder.findFile(saveFileName)
    }
    
    /**
     * Create save file for a ROM
     */
    suspend fun createSaveFile(baseUri: Uri, romName: String): DocumentFile? =
        withContext(Dispatchers.IO) {
            val savesFolder = getFolder(baseUri, FOLDER_SAVES) ?: return@withContext null
            val saveFileName = stripRomExtension(romName) + ".sav"
            
            // Check if file already exists
            savesFolder.findFile(saveFileName)?.let { return@withContext it }
            
            // Create new file
            savesFolder.createFile("application/octet-stream", saveFileName)
        }
    
    /**
     * Read file contents
     */
    suspend fun readFile(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes()
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Write file contents
     */
    suspend fun writeFile(uri: Uri, data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                output.write(data)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Delete a file
     */
    suspend fun deleteFile(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            DocumentFile.fromSingleUri(context, uri)?.delete() ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get file display name
     */
    fun getFileName(uri: Uri): String? {
        return DocumentFile.fromSingleUri(context, uri)?.name
    }
    
    /**
     * Get file size
     */
    fun getFileSize(uri: Uri): Long {
        return DocumentFile.fromSingleUri(context, uri)?.length() ?: 0L
    }
}




