package com.vsmileemu.android.data.preferences

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vsmileemu.android.controller.ColorButtonAnchor
import com.vsmileemu.android.controller.ColorButtonLayout
import com.vsmileemu.android.controller.ControllerAction
import com.vsmileemu.android.data.model.EmulatorConfig
import com.vsmileemu.android.data.model.VideoTiming
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

class AppPreferences(private val context: Context) {
    
    companion object {
        private val SETUP_COMPLETED = booleanPreferencesKey("setup_completed")
        private val STORAGE_URI = stringPreferencesKey("storage_uri")
        private val BIOS_FOUND = booleanPreferencesKey("bios_found")
        private val VIDEO_TIMING = stringPreferencesKey("video_timing")
        private val AUDIO_ENABLED = booleanPreferencesKey("audio_enabled")
        private val AUDIO_VOLUME = floatPreferencesKey("audio_volume")
        private val SHOW_FPS = booleanPreferencesKey("show_fps")
        private val FAST_FORWARD = booleanPreferencesKey("fast_forward_enabled")
        private val CONTROLLER_OPACITY = floatPreferencesKey("controller_opacity")
        private val CONTROLLER_SIZE = floatPreferencesKey("controller_size")
        private val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        private val PIXEL_SCALE = stringPreferencesKey("pixel_scale")
        private val HIDE_CONTROLLER_WHEN_EXTERNAL = booleanPreferencesKey("hide_controller_when_external")
        private val CONTROLLER_LAYOUT = stringPreferencesKey("controller_layout")
        private val CONTROLLER_ANCHOR = stringPreferencesKey("controller_anchor")
        private fun controllerMappingKey(action: ControllerAction) =
            intPreferencesKey("controller_mapping_${action.name.lowercase()}")
    }
    
    val setupCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SETUP_COMPLETED] ?: false
    }
    
    suspend fun setSetupCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SETUP_COMPLETED] = completed
        }
    }
    
    val storageUri: Flow<Uri?> = context.dataStore.data.map { preferences ->
        preferences[STORAGE_URI]?.let { Uri.parse(it) }
    }
    
    suspend fun setStorageUri(uri: Uri) {
        context.dataStore.edit { preferences ->
            preferences[STORAGE_URI] = uri.toString()
        }
    }
    
    val biosFound: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[BIOS_FOUND] ?: false
    }
    
    suspend fun setBiosFound(found: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[BIOS_FOUND] = found
        }
    }
    
    val emulatorConfig: Flow<EmulatorConfig> = context.dataStore.data.map { preferences ->
        EmulatorConfig(
            videoTiming = VideoTiming.valueOf(
                preferences[VIDEO_TIMING] ?: VideoTiming.PAL.name
            ),
            audioEnabled = preferences[AUDIO_ENABLED] ?: true,
            audioVolume = preferences[AUDIO_VOLUME] ?: 1.0f,
            showFps = preferences[SHOW_FPS] ?: false,
            fastForwardEnabled = preferences[FAST_FORWARD] ?: true,
            controllerOpacity = preferences[CONTROLLER_OPACITY] ?: 0.7f,
            controllerSize = preferences[CONTROLLER_SIZE] ?: 1.0f,
            hapticFeedback = preferences[HAPTIC_FEEDBACK] ?: true,
            hideVirtualControllerWhenExternal = preferences[HIDE_CONTROLLER_WHEN_EXTERNAL] ?: false
        )
    }
    
    suspend fun updateEmulatorConfig(config: EmulatorConfig) {
        context.dataStore.edit { preferences ->
            preferences[VIDEO_TIMING] = config.videoTiming.name
            preferences[AUDIO_ENABLED] = config.audioEnabled
            preferences[AUDIO_VOLUME] = config.audioVolume
            preferences[SHOW_FPS] = config.showFps
            preferences[FAST_FORWARD] = config.fastForwardEnabled
            preferences[CONTROLLER_OPACITY] = config.controllerOpacity
            preferences[CONTROLLER_SIZE] = config.controllerSize
            preferences[HAPTIC_FEEDBACK] = config.hapticFeedback
            preferences[HIDE_CONTROLLER_WHEN_EXTERNAL] = config.hideVirtualControllerWhenExternal
        }
    }
    
    suspend fun resetAll() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
    
    // Synchronous helpers for simpler access
    suspend fun getStorageUri(): String? {
        return storageUri.first()?.toString()
    }
    
    // Individual setting accessors
    val showFps: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SHOW_FPS] ?: true  // Default to showing FPS
    }
    
    suspend fun setShowFps(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_FPS] = show
        }
    }
    
    val pixelScale: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PIXEL_SCALE] ?: "FIT_SCREEN"
    }
    
    suspend fun setPixelScale(scale: String) {
        context.dataStore.edit { preferences ->
            preferences[PIXEL_SCALE] = scale
        }
    }

    val hideVirtualControllerWhenExternal: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[HIDE_CONTROLLER_WHEN_EXTERNAL] ?: false
    }

    suspend fun setHideVirtualControllerWhenExternal(hide: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HIDE_CONTROLLER_WHEN_EXTERNAL] = hide
        }
    }

    val controllerLayout: Flow<ColorButtonLayout> = context.dataStore.data.map { preferences ->
        preferences[CONTROLLER_LAYOUT]?.let { runCatching { ColorButtonLayout.valueOf(it) }.getOrNull() }
            ?: ColorButtonLayout.GRID
    }

    suspend fun setControllerLayout(layout: ColorButtonLayout) {
        context.dataStore.edit { preferences ->
            preferences[CONTROLLER_LAYOUT] = layout.name
        }
    }

    val controllerAnchor: Flow<ColorButtonAnchor> = context.dataStore.data.map { preferences ->
        preferences[CONTROLLER_ANCHOR]?.let { runCatching { ColorButtonAnchor.valueOf(it) }.getOrNull() }
            ?: ColorButtonAnchor.RIGHT
    }

    suspend fun setControllerAnchor(anchor: ColorButtonAnchor) {
        context.dataStore.edit { preferences ->
            preferences[CONTROLLER_ANCHOR] = anchor.name
        }
    }

    val controllerMappings: Flow<Map<ControllerAction, Int>> = context.dataStore.data.map { preferences ->
        ControllerAction.entries.associateWith { action ->
            preferences[controllerMappingKey(action)] ?: action.defaultKeyCode
        }
    }

    suspend fun setControllerMapping(action: ControllerAction, keyCode: Int) {
        context.dataStore.edit { preferences ->
            ControllerAction.entries.forEach { other ->
                if (preferences[controllerMappingKey(other)] == keyCode && other != action) {
                    preferences.remove(controllerMappingKey(other))
                }
            }
            preferences[controllerMappingKey(action)] = keyCode
        }
    }

    suspend fun resetControllerMappings() {
        context.dataStore.edit { preferences ->
            ControllerAction.entries.forEach { action ->
                preferences.remove(controllerMappingKey(action))
            }
        }
    }
}



