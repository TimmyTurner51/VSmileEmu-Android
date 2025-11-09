package com.vsmileemu.android.util

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * File-based logger that writes to the VSmile storage directory
 * This bypasses logcat and writes directly to a file we can pull via adb
 */
object FileLogger {
    private const val TAG = "FileLogger"
    private const val LOG_FILE_NAME = "vsmile_emulator_log.txt"
    
    private var logWriter: PrintWriter? = null
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    
    /**
     * Initialize file logger with storage URI
     */
    fun initialize(context: Context, storageUriString: String?) {
        try {
            if (storageUriString == null) {
                Log.w(TAG, "No storage URI provided, trying internal storage")
                // Fallback to internal storage
                logFile = File(context.filesDir, LOG_FILE_NAME)
            } else {
                // Try to write to the VSmile directory
                val storageUri = Uri.parse(storageUriString)
                val storageHelper = StorageHelper(context)
                
                // Get the actual path if it's a file:// URI
                if (storageUri.scheme == "file") {
                    logFile = File(storageUri.path, LOG_FILE_NAME)
                } else {
                    // For content:// URIs, fall back to internal storage
                    logFile = File(context.filesDir, LOG_FILE_NAME)
                }
            }
            
            // Create/open log file
            logFile?.let { file ->
                file.parentFile?.mkdirs()
                logWriter = PrintWriter(FileOutputStream(file, true), true)
                
                log("═══════════════════════════════════════════════════════")
                log("VSmile Emulator Log Started")
                log("Time: ${dateFormat.format(Date())}")
                log("Log file: ${file.absolutePath}")
                log("═══════════════════════════════════════════════════════")
                
                Log.i(TAG, "File logger initialized: ${file.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize file logger", e)
            logWriter = null
        }
    }
    
    /**
     * Log a message with timestamp
     */
    fun log(message: String) {
        val timestamp = dateFormat.format(Date())
        val logLine = "[$timestamp] $message"
        
        // Also log to logcat
        Log.i("FileLogger", message)
        
        try {
            logWriter?.println(logLine)
            logWriter?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to log file", e)
        }
    }
    
    /**
     * Log an error with exception
     */
    fun logError(message: String, throwable: Throwable? = null) {
        log("ERROR: $message")
        throwable?.let {
            log("Exception: ${it.javaClass.name}: ${it.message}")
            it.stackTrace.take(10).forEach { frame ->
                log("  at $frame")
            }
        }
    }
    
    /**
     * Log a variable/value pair
     */
    fun logVar(name: String, value: Any?) {
        log("  $name = $value")
    }
    
    /**
     * Log a section header
     */
    fun logSection(title: String) {
        log("")
        log("─────────────────────────────────────────────────────────")
        log(title)
        log("─────────────────────────────────────────────────────────")
    }
    
    /**
     * Close the log file
     */
    fun close() {
        try {
            log("═══════════════════════════════════════════════════════")
            log("Log Ended")
            log("═══════════════════════════════════════════════════════")
            logWriter?.close()
            logWriter = null
            
            logFile?.let {
                Log.i(TAG, "Log file closed: ${it.absolutePath}")
                Log.i(TAG, "To retrieve: adb pull ${it.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error closing log file", e)
        }
    }
    
    /**
     * Get the log file path for adb pull
     */
    fun getLogFilePath(): String? {
        return logFile?.absolutePath
    }
}


