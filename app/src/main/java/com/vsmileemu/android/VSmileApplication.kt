package com.vsmileemu.android

import android.app.Application
import android.util.Log
import com.vsmileemu.android.util.CrashHandler

class VSmileApplication : Application() {
    
    companion object {
        private const val TAG = "VSmileApplication"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Log at ERROR level to ensure it shows up in logcat
        Log.e(TAG, "╔═══════════════════════════════════════════╗")
        Log.e(TAG, "║  VSmileApplication.onCreate() STARTED   ║")
        Log.e(TAG, "╚═══════════════════════════════════════════╝")
        
        try {
            // Install crash handler
            CrashHandler.setup(this)
            Log.e(TAG, "✓ Crash handler installed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "✗ Failed to install crash handler", e)
        }
        
        Log.e(TAG, "✓ VSmile Emulator Application initialized")
    }
}

