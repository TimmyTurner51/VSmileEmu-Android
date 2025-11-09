package com.vsmileemu.android.data.model

import android.net.Uri
import java.io.File

/**
 * Represents a V.Smile game ROM
 */
data class Rom(
    val uri: Uri,
    val fileName: String,
    val fileSize: Long,
    val lastModified: Long,
    val lastPlayed: Long? = null,
    val playTime: Long = 0L, // Total play time in milliseconds
    val hasSaveData: Boolean = false
) {
    val displayName: String
        get() {
            // Strip common ROM extensions
            val extensions = listOf(".bin", ".rom", ".v.smile", ".dat")
            var name = fileName
            extensions.forEach { ext ->
                if (name.endsWith(ext, ignoreCase = true)) {
                    name = name.substring(0, name.length - ext.length)
                }
            }
            return name
        }
    
    val fileSizeFormatted: String
        get() {
            val kb = fileSize / 1024.0
            val mb = kb / 1024.0
            return when {
                mb >= 1.0 -> String.format("%.2f MB", mb)
                kb >= 1.0 -> String.format("%.2f KB", kb)
                else -> "$fileSize bytes"
            }
        }
}



