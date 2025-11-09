package com.vsmileemu.android.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {
    
    companion object {
        private const val TAG = "CrashHandler"
        private const val CRASH_LOG_FILE = "last_crash.txt"
        
        fun setup(context: Context) {
            val handler = CrashHandler(context.applicationContext)
            Thread.setDefaultUncaughtExceptionHandler(handler)
            Log.i(TAG, "Crash handler installed")
        }
        
        fun getLastCrash(context: Context): String? {
            val file = File(context.filesDir, CRASH_LOG_FILE)
            return if (file.exists()) {
                file.readText()
            } else {
                null
            }
        }
        
        fun clearLastCrash(context: Context) {
            val file = File(context.filesDir, CRASH_LOG_FILE)
            if (file.exists()) {
                file.delete()
            }
        }
    }
    
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val crashLog = buildCrashLog(thread, throwable)
            
            // Write to file logger if available
            try {
                FileLogger.logSection("✗✗✗ APP CRASHED ✗✗✗")
                FileLogger.log("Thread: ${thread.name} (ID: ${thread.id})")
                FileLogger.logError("Uncaught exception", throwable)
                FileLogger.close()
            } catch (e: Exception) {
                // FileLogger might not be initialized
                Log.e(TAG, "Couldn't write to FileLogger", e)
            }
            
            // Save to crash file
            val file = File(context.filesDir, CRASH_LOG_FILE)
            file.writeText(crashLog)
            
            Log.e(TAG, "Crash captured and saved to $file")
            Log.e(TAG, crashLog)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving crash log", e)
        }
        
        // Call default handler to let the app crash normally
        defaultHandler?.uncaughtException(thread, throwable)
    }
    
    private fun buildCrashLog(thread: Thread, throwable: Throwable): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val writer = StringWriter()
        val printWriter = PrintWriter(writer)
        
        printWriter.println("═══════════════════════════════════════")
        printWriter.println("VSmile Emulator - Crash Report")
        printWriter.println("═══════════════════════════════════════")
        printWriter.println("Time: $timestamp")
        printWriter.println("Thread: ${thread.name} (ID: ${thread.id})")
        printWriter.println()
        printWriter.println("Exception: ${throwable.javaClass.name}")
        printWriter.println("Message: ${throwable.message}")
        printWriter.println()
        printWriter.println("Stack Trace:")
        printWriter.println("───────────────────────────────────────")
        throwable.printStackTrace(printWriter)
        
        // Include cause if present
        var cause = throwable.cause
        var depth = 1
        while (cause != null && depth < 5) {
            printWriter.println()
            printWriter.println("Caused by (depth $depth):")
            printWriter.println("───────────────────────────────────────")
            cause.printStackTrace(printWriter)
            cause = cause.cause
            depth++
        }
        
        printWriter.println()
        printWriter.println("═══════════════════════════════════════")
        
        return writer.toString()
    }
}

fun Context.copyToClipboard(text: String, label: String = "VSmile Crash Log") {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(this, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
}


