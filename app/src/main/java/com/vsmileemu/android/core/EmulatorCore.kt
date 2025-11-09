package com.vsmileemu.android.core

import android.util.Log

/**
 * Native wrapper for the VSmile emulator core
 */
class EmulatorCore {
    
    companion object {
        private const val TAG = "EmulatorCore"
        private var libraryLoaded = false
        private var libraryError: Throwable? = null
        
        fun ensureLibraryLoaded() {
            if (libraryLoaded) return
            if (libraryError != null) {
                throw RuntimeException("Native library failed to load previously", libraryError)
            }
            
            try {
                Log.i(TAG, "Attempting to load native library: vsmile_android")
                System.loadLibrary("vsmile_android")
                libraryLoaded = true
                Log.i(TAG, "✓ Native library loaded successfully")
            } catch (e: Throwable) {
                libraryError = e
                Log.e(TAG, "✗ Failed to load native library: ${e.message}", e)
                throw e
            }
        }
    }
    
    init {
        ensureLibraryLoaded()
    }
    
    private var initialized = false
    
    /**
     * Initialize the emulator with ROM data
     * @param sysrom System ROM (2MB), null to use dummy ROM
     * @param cartrom Cartridge ROM (up to 8MB)
     * @param usePAL true for PAL timing (50Hz), false for NTSC (60Hz)
     * @return true if initialization succeeded
     */
    fun initialize(
        sysrom: ByteArray?,
        cartrom: ByteArray,
        usePAL: Boolean = true
    ): Boolean {
        if (initialized) {
            Log.w(TAG, "Emulator already initialized")
            return true
        }
        
        val result = nativeInit(sysrom, cartrom, cartrom.size, usePAL)
        initialized = result
        
        if (result) {
            Log.i(TAG, "Emulator initialized: ${cartrom.size} byte ROM, ${if (usePAL) "PAL" else "NTSC"} timing")
        } else {
            Log.e(TAG, "Failed to initialize emulator")
        }
        
        return result
    }
    
    /**
     * Run one frame of emulation
     */
    fun runFrame() {
        if (!initialized) {
            Log.w(TAG, "Cannot run frame: emulator not initialized")
            return
        }
        nativeRunFrame()
    }
    
    /**
     * Get the current video frame buffer (320x240x2 bytes, RGB565)
     * @return Frame buffer as ByteArray, or null if not initialized
     */
    fun getFrameBuffer(): ByteArray? {
        if (!initialized) return null
        return nativeGetFrameBuffer()
    }
    
    /**
     * Get audio samples for the current frame (stereo 16-bit)
     * @return Audio samples as ShortArray, or null if not initialized
     */
    fun getAudioSamples(): ShortArray? {
        if (!initialized) return null
        return nativeGetAudioSamples()
    }
    
    /**
     * Send controller input to the emulator
     */
    fun sendInput(
        enter: Boolean = false,
        help: Boolean = false,
        back: Boolean = false,
        abc: Boolean = false,
        red: Boolean = false,
        yellow: Boolean = false,
        blue: Boolean = false,
        green: Boolean = false,
        joyX: Int = 0,  // -5 to +5
        joyY: Int = 0   // -5 to +5
    ) {
        if (!initialized) return
        nativeSendInput(enter, help, back, abc, red, yellow, blue, green, joyX, joyY)
    }
    
    /**
     * Press the console ON button
     */
    fun pressOnButton(pressed: Boolean) {
        if (!initialized) return
        nativePressOnButton(pressed)
    }
    
    /**
     * Destroy the emulator instance
     */
    fun destroy() {
        if (!initialized) return
        nativeDestroy()
        initialized = false
        Log.i(TAG, "Emulator destroyed")
    }
    
    // Native methods
    private external fun nativeInit(
        sysrom: ByteArray?,
        cartrom: ByteArray,
        cartSize: Int,
        usePAL: Boolean
    ): Boolean
    
    private external fun nativeRunFrame()
    private external fun nativeGetFrameBuffer(): ByteArray?
    private external fun nativeGetAudioSamples(): ShortArray?
    private external fun nativeSendInput(
        enter: Boolean,
        help: Boolean,
        back: Boolean,
        abc: Boolean,
        red: Boolean,
        yellow: Boolean,
        blue: Boolean,
        green: Boolean,
        joyX: Int,
        joyY: Int
    )
    private external fun nativePressOnButton(pressed: Boolean)
    private external fun nativeDestroy()
}





