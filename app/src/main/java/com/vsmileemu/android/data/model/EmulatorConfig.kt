package com.vsmileemu.android.data.model

/**
 * Emulator configuration
 */
data class EmulatorConfig(
    val videoTiming: VideoTiming = VideoTiming.PAL,
    val audioEnabled: Boolean = true,
    val audioVolume: Float = 1.0f,
    val showFps: Boolean = false,
    val fastForwardEnabled: Boolean = true,
    val controllerOpacity: Float = 0.7f,
    val controllerSize: Float = 1.0f,
    val hapticFeedback: Boolean = true,
    val hideVirtualControllerWhenExternal: Boolean = false
)

enum class VideoTiming {
    PAL,    // 50 Hz
    NTSC    // 60 Hz
}









