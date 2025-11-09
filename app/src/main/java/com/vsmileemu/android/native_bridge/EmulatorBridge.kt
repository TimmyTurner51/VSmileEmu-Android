package com.vsmileemu.android.native_bridge

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * JNI Bridge to native veesem emulator
 * This is the Kotlin interface to the C++ emulator core
 */
class EmulatorBridge {
    
    private var nativeHandle: Long = 0
    
    companion object {
        init {
            try {
                System.loadLibrary("vsmileemu_native")
            } catch (e: UnsatisfiedLinkError) {
                // Library not found - probably testing without native code
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Initialize emulator with ROM data
     * @param biosData BIOS file (can be null for dummy BIOS)
     * @param romData ROM file (required)
     * @param isPAL true for PAL (50Hz), false for NTSC (60Hz)
     * @return true if initialization succeeded
     */
    fun create(biosData: ByteArray?, romData: ByteArray, isPAL: Boolean = true): Boolean {
        if (nativeHandle != 0L) {
            destroy()
        }
        
        nativeHandle = nativeCreate(biosData, romData, isPAL)
        return nativeHandle != 0L
    }
    
    /**
     * Destroy emulator and free resources
     */
    fun destroy() {
        if (nativeHandle != 0L) {
            nativeDestroy(nativeHandle)
            nativeHandle = 0L
        }
    }
    
    /**
     * Run one frame of emulation
     */
    fun runFrame() {
        if (nativeHandle != 0L) {
            nativeRunFrame(nativeHandle)
        }
    }
    
    /**
     * Get framebuffer as DirectByteBuffer (320x240 RGB565)
     * Returns null if not initialized
     */
    fun getFramebuffer(): ByteBuffer? {
        if (nativeHandle == 0L) return null
        
        return nativeGetFramebuffer(nativeHandle)?.also {
            it.order(ByteOrder.LITTLE_ENDIAN)
        }
    }
    
    /**
     * Get audio samples as DirectByteBuffer (16-bit stereo)
     * Returns null if no audio available
     */
    fun getAudioSamples(): ByteBuffer? {
        if (nativeHandle == 0L) return null
        
        return nativeGetAudioSamples(nativeHandle)?.also {
            it.order(ByteOrder.LITTLE_ENDIAN)
        }
    }
    
    /**
     * Update controller input
     */
    fun updateInput(input: ControllerInput) {
        if (nativeHandle != 0L) {
            nativeUpdateInput(
                nativeHandle,
                input.enter,
                input.help,
                input.back,
                input.abc,
                input.red,
                input.yellow,
                input.blue,
                input.green,
                input.joystickX,
                input.joystickY
            )
        }
    }
    
    /**
     * Pause emulation
     */
    fun pause() {
        if (nativeHandle != 0L) {
            nativePause(nativeHandle)
        }
    }
    
    /**
     * Resume emulation
     */
    fun resume() {
        if (nativeHandle != 0L) {
            nativeResume(nativeHandle)
        }
    }
    
    /**
     * Reset emulation
     */
    fun reset() {
        if (nativeHandle != 0L) {
            nativeReset(nativeHandle)
        }
    }
    
    /**
     * Get current FPS
     */
    fun getFPS(): Float {
        return if (nativeHandle != 0L) {
            nativeGetFPS(nativeHandle)
        } else {
            0f
        }
    }
    
    /**
     * Save emulator state
     */
    fun saveState(): ByteArray? {
        return if (nativeHandle != 0L) {
            nativeSaveState(nativeHandle)
        } else {
            null
        }
    }
    
    /**
     * Load emulator state
     */
    fun loadState(stateData: ByteArray): Boolean {
        return if (nativeHandle != 0L) {
            nativeLoadState(nativeHandle, stateData)
        } else {
            false
        }
    }
    
    // Native methods
    private external fun nativeCreate(biosData: ByteArray?, romData: ByteArray, isPAL: Boolean): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeRunFrame(handle: Long)
    private external fun nativeGetFramebuffer(handle: Long): ByteBuffer?
    private external fun nativeGetAudioSamples(handle: Long): ByteBuffer?
    private external fun nativeUpdateInput(
        handle: Long,
        enter: Boolean,
        help: Boolean,
        back: Boolean,
        abc: Boolean,
        red: Boolean,
        yellow: Boolean,
        blue: Boolean,
        green: Boolean,
        joystickX: Int,
        joystickY: Int
    )
    private external fun nativePause(handle: Long)
    private external fun nativeResume(handle: Long)
    private external fun nativeReset(handle: Long)
    private external fun nativeGetFPS(handle: Long): Float
    private external fun nativeSaveState(handle: Long): ByteArray?
    private external fun nativeLoadState(handle: Long, stateData: ByteArray): Boolean
}

/**
 * Controller input state
 * Matches V.Smile controller layout
 */
data class ControllerInput(
    // Face buttons
    val enter: Boolean = false,      // Primary action (like A)
    val help: Boolean = false,       // Help/hints (like B)
    val back: Boolean = false,       // Back/pause
    val abc: Boolean = false,        // Learning Zone/ABC
    
    // Color buttons
    val red: Boolean = false,        // Red button
    val yellow: Boolean = false,     // Yellow button
    val blue: Boolean = false,       // Blue button
    val green: Boolean = false,      // Green button
    
    // Joystick (analog)
    val joystickX: Int = 0,          // -5 to +5
    val joystickY: Int = 0           // -5 to +5
) {
    companion object {
        val EMPTY = ControllerInput()
    }
}






