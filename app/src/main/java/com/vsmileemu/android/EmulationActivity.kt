package com.vsmileemu.android

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.lifecycle.lifecycleScope
import com.vsmileemu.android.controller.ColorButtonAnchor
import com.vsmileemu.android.controller.ColorButtonLayout
import com.vsmileemu.android.controller.ControllerAction
import com.vsmileemu.android.core.EmulatorCore
import com.vsmileemu.android.data.preferences.AppPreferences
import com.vsmileemu.android.native_bridge.ControllerInput
import com.vsmileemu.android.audio.AudioManager
import com.vsmileemu.android.ui.emulation.VirtualController
import com.vsmileemu.android.ui.theme.VSmileEmulatorTheme
import com.vsmileemu.android.util.FileLogger
import com.vsmileemu.android.util.StorageHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt

class EmulationActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "EmulationActivity"
        private const val NTSC_FRAME_TIME_NS = 16_666_667L
        private const val PAL_FRAME_TIME_NS = 20_000_000L
        private const val NTSC_AUDIO_FRAMES = 800
        private const val PAL_AUDIO_FRAMES = 960
    }
    
    private lateinit var emulator: EmulatorCore
    private lateinit var storageHelper: StorageHelper
    private lateinit var appPreferences: AppPreferences
    private lateinit var audioManager: AudioManager
    private lateinit var inputManager: InputManager
    
    private var emulationJob: Job? = null
    private var isRunning = false
    private var isEmulatorInitialized = false  // Tracks if ROM has been loaded
    
    // Current frame data - use data class to force StateFlow updates
    data class FrameData(val bitmap: Bitmap, val frameNumber: Long)
    
    private val _currentFrame = MutableStateFlow<FrameData?>(null)
    val currentFrame: StateFlow<FrameData?> = _currentFrame
    
    private val _currentFps = MutableStateFlow(0f)
    val currentFps: StateFlow<Float> = _currentFps
    
    // Settings
    private val _audioEnabled = MutableStateFlow(true)  // Enable by default for testing
    val audioEnabled: StateFlow<Boolean> = _audioEnabled
    
    private val _pixelScale = MutableStateFlow(PixelScale.FIT_SCREEN)
    val pixelScale: StateFlow<PixelScale> = _pixelScale
    
    private val _showFps = MutableStateFlow(true)
    val showFps: StateFlow<Boolean> = _showFps
    
    private val _frameSkip = MutableStateFlow(false)
    val frameSkip: StateFlow<Boolean> = _frameSkip
    
    private val _fastMath = MutableStateFlow(true)
    val fastMath: StateFlow<Boolean> = _fastMath

    private val _hideControllerWhenExternal = MutableStateFlow(false)
    val hideControllerWhenExternal: StateFlow<Boolean> = _hideControllerWhenExternal

    private val _externalControllerConnected = MutableStateFlow(false)
    val externalControllerConnected: StateFlow<Boolean> = _externalControllerConnected

    private var virtualControllerInput: ControllerInput = ControllerInput.EMPTY
    private var hardwareControllerInput: ControllerInput = ControllerInput.EMPTY
    
    private var usePalTiming = true
    private var targetFrameTimeNanos = PAL_FRAME_TIME_NS
    private var audioFramesPerVideoFrame = PAL_AUDIO_FRAMES
    
    // Audio resampler converts SPU output (281.25 kHz) to 48 kHz for AudioTrack
    private val audioResampler = AudioResampler(targetFramesPerVideoFrame = PAL_AUDIO_FRAMES)

    private val _controllerLayout = MutableStateFlow(ColorButtonLayout.GRID)
    val controllerLayout: StateFlow<ColorButtonLayout> = _controllerLayout

    private val _controllerAnchor = MutableStateFlow(ColorButtonAnchor.RIGHT)
    val controllerAnchor: StateFlow<ColorButtonAnchor> = _controllerAnchor

    private val _controllerMappings = MutableStateFlow(
        ControllerAction.entries.associateWith { it.defaultKeyCode }
    )
    val controllerMappings: StateFlow<Map<ControllerAction, Int>> = _controllerMappings
    private var controllerMappingByKey: Map<Int, ControllerAction> =
        ControllerAction.entries.associateBy { it.defaultKeyCode }
    
    // Frame counter for debugging
    private var frameCount = 0L
    private var lastFpsTime = System.currentTimeMillis()

    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            updateExternalControllerState()
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            updateExternalControllerState()
        }

        override fun onInputDeviceChanged(deviceId: Int) {
            updateExternalControllerState()
        }
    }
    
    enum class PixelScale(val displayName: String, val description: String, val scaleFraction: Float) {
        SMALL("Small", "50% of screen width", 0.5f),
        MEDIUM("Medium", "75% of screen width", 0.75f),
        LARGE("Large", "100% of screen width", 1.0f),
        FIT_SCREEN("Fill Screen", "Fill available space", 1.0f)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate() started")
        
        FileLogger.logSection("EmulationActivity.onCreate() STARTED")
        
        try {
            // Keep screen on during emulation
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Log.d(TAG, "Screen wake lock set")
            FileLogger.log("✓ Screen wake lock set")
            
            storageHelper = StorageHelper(this)
            Log.d(TAG, "StorageHelper initialized")
            FileLogger.log("✓ StorageHelper initialized")
            
            appPreferences = AppPreferences(this)
            Log.d(TAG, "AppPreferences initialized")
            FileLogger.log("✓ AppPreferences initialized")
            
            // Load FPS preference
            lifecycleScope.launch {
                appPreferences.showFps.collect { show ->
                    _showFps.value = show
                }
            }

            lifecycleScope.launch {
                appPreferences.controllerLayout.collect { layout ->
                    _controllerLayout.value = layout
                }
            }

            lifecycleScope.launch {
                appPreferences.controllerAnchor.collect { anchor ->
                    _controllerAnchor.value = anchor
                }
            }

            lifecycleScope.launch {
                appPreferences.controllerMappings.collect { mappings ->
                    _controllerMappings.value = mappings
                    controllerMappingByKey = mappings.entries.associate { (action, keyCode) ->
                        keyCode to action
                    }
                }
            }

            lifecycleScope.launch {
                appPreferences.pixelScale.collect { scale ->
                    val parsed = runCatching { PixelScale.valueOf(scale) }.getOrDefault(PixelScale.FIT_SCREEN)
                    _pixelScale.value = parsed
                }
            }

            lifecycleScope.launch {
                appPreferences.hideVirtualControllerWhenExternal.collect { hide ->
                    _hideControllerWhenExternal.value = hide
                }
            }
            
            audioManager = AudioManager()
            audioManager.initialize()
            Log.d(TAG, "AudioManager initialized")
            FileLogger.log("✓ AudioManager initialized")

            inputManager = getSystemService(Context.INPUT_SERVICE) as InputManager
            inputManager.registerInputDeviceListener(inputDeviceListener, null)
            updateExternalControllerState()
            
            try {
                Log.d(TAG, "Creating EmulatorCore (will load native library)...")
                FileLogger.log("Attempting to create EmulatorCore...")
                FileLogger.log("This will load native library: libvsmile_android.so")
                
                emulator = EmulatorCore()
                
                Log.d(TAG, "✓ EmulatorCore created successfully")
                FileLogger.log("✓ EmulatorCore created successfully")
                FileLogger.log("✓ Native library loaded OK")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "✗ Native library not found or failed to load!", e)
                FileLogger.logError("✗ NATIVE LIBRARY LOAD FAILED", e)
                FileLogger.log("Library name: libvsmile_android.so")
                FileLogger.log("This usually means:")
                FileLogger.log("  1. NDK is not installed")
                FileLogger.log("  2. Native code didn't compile")
                FileLogger.log("  3. .so files weren't packaged in APK")
                throw RuntimeException("Native library 'vsmile_android' not found", e)
            } catch (e: Exception) {
                Log.e(TAG, "✗ Failed to create EmulatorCore", e)
                FileLogger.logError("✗ Failed to create EmulatorCore", e)
                throw e
            }
            
            // Get ROM URI from intent
            val romUriString = intent.getStringExtra("ROM_URI")
            val romName = intent.getStringExtra("ROM_NAME") ?: "Unknown ROM"
            
            Log.i(TAG, "ROM URI: $romUriString")
            Log.i(TAG, "ROM Name: $romName")
            
            FileLogger.log("ROM Intent Data:")
            FileLogger.logVar("ROM_URI", romUriString)
            FileLogger.logVar("ROM_NAME", romName)
            
            if (romUriString == null) {
                Log.e(TAG, "No ROM URI provided")
                FileLogger.logError("✗ No ROM URI provided in intent")
                finish()
                return
            }
            
            Log.d(TAG, "Setting up UI...")
            FileLogger.log("Setting up EmulationScreen UI...")
            
            setContent {
                VSmileEmulatorTheme {
                    EmulationScreen(
                        romName = romName,
                        currentFrameData = currentFrame,
                        currentFps = currentFps,
                        showFps = showFps,
                        audioEnabled = audioEnabled,
                        pixelScale = pixelScale,
                        hideControllerWhenExternal = hideControllerWhenExternal,
                        externalControllerConnected = externalControllerConnected,
                         colorButtonLayout = controllerLayout,
                         colorButtonAnchor = controllerAnchor,
                         controllerMappings = controllerMappings,
                        emulator = emulator,
                        onLoadRom = {
                            loadAndStartEmulation(Uri.parse(romUriString))
                        },
                        onVirtualInputChange = { input ->
                            updateVirtualControllerInput(input)
                        },
                        onToggleFps = { enabled ->
                            lifecycleScope.launch {
                                appPreferences.setShowFps(enabled)
                            }
                        },
                        onToggleAudio = { enabled ->
                            _audioEnabled.value = enabled
                            if (enabled) {
                                audioManager.initialize()
                                audioManager.start()
                            } else {
                                audioManager.stop()
                            }
                        },
                        onChangeScale = { scale ->
                            _pixelScale.value = scale
                            lifecycleScope.launch {
                                appPreferences.setPixelScale(scale.name)
                            }
                        },
                        onToggleHideController = { enabled ->
                            lifecycleScope.launch {
                                appPreferences.setHideVirtualControllerWhenExternal(enabled)
                            }
                        },
                        onChangeColorLayout = { layout ->
                            lifecycleScope.launch {
                                appPreferences.setControllerLayout(layout)
                            }
                        },
                        onChangeColorAnchor = { anchor ->
                            lifecycleScope.launch {
                                appPreferences.setControllerAnchor(anchor)
                            }
                        },
                        onUpdateControllerMapping = { action, keyCode ->
                            lifecycleScope.launch {
                                appPreferences.setControllerMapping(action, keyCode)
                            }
                        },
                        onResetControllerMappings = {
                            lifecycleScope.launch {
                                appPreferences.resetControllerMappings()
                            }
                        },
                        onBack = { finish() }
                    )
                }
            }
            Log.i(TAG, "onCreate() completed successfully")
            FileLogger.log("✓ onCreate() completed - waiting for onLoadRom callback")
            
        } catch (e: Exception) {
            Log.e(TAG, "FATAL ERROR in onCreate()", e)
            FileLogger.logError("✗✗✗ FATAL ERROR in onCreate()", e)
            throw e
        }
    }
    
    private fun configureTiming(usePal: Boolean) {
        usePalTiming = usePal
        targetFrameTimeNanos = if (usePal) PAL_FRAME_TIME_NS else NTSC_FRAME_TIME_NS
        audioFramesPerVideoFrame = if (usePal) PAL_AUDIO_FRAMES else NTSC_AUDIO_FRAMES
        audioResampler.updateTargetFramesPerVideoFrame(audioFramesPerVideoFrame)
        FileLogger.log("Timing configured: ${if (usePal) "PAL 50Hz" else "NTSC 60Hz"}")
        Log.i(TAG, "Timing configured: usePal=$usePal, targetFrameTime=$targetFrameTimeNanos ns, audioFrames=$audioFramesPerVideoFrame")
    }

    private fun updateVirtualControllerInput(input: ControllerInput) {
        if (virtualControllerInput != input) {
            virtualControllerInput = input
            sendCombinedInput()
        }
    }

    private fun updateHardwareInput(transform: (ControllerInput) -> ControllerInput) {
        val newInput = transform(hardwareControllerInput)
        if (newInput != hardwareControllerInput) {
            hardwareControllerInput = newInput
            sendCombinedInput()
        }
    }

    private fun sendCombinedInput() {
        if (!::emulator.isInitialized || !isEmulatorInitialized) return
        val combined = combineInputs(virtualControllerInput, hardwareControllerInput)
        emulator.sendInput(
            enter = combined.enter,
            help = combined.help,
            back = combined.back,
            abc = combined.abc,
            red = combined.red,
            yellow = combined.yellow,
            blue = combined.blue,
            green = combined.green,
            joyX = combined.joystickX,
            joyY = combined.joystickY
        )
    }

    private fun combineInputs(virtual: ControllerInput, hardware: ControllerInput): ControllerInput {
        return ControllerInput(
            enter = virtual.enter || hardware.enter,
            help = virtual.help || hardware.help,
            back = virtual.back || hardware.back,
            abc = virtual.abc || hardware.abc,
            red = virtual.red || hardware.red,
            yellow = virtual.yellow || hardware.yellow,
            blue = virtual.blue || hardware.blue,
            green = virtual.green || hardware.green,
            joystickX = if (hardware.joystickX != 0) hardware.joystickX else virtual.joystickX,
            joystickY = if (hardware.joystickY != 0) hardware.joystickY else virtual.joystickY
        )
    }

    private fun isControllerSource(source: Int): Boolean {
        return source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD
    }

    private fun handleControllerKey(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            return false
        }
        val pressed = event.action == KeyEvent.ACTION_DOWN
        updateExternalControllerState()
        val mappedAction = controllerMappingByKey[event.keyCode]
        if (mappedAction != null) {
            return handleMappedAction(mappedAction, pressed)
        }
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                updateHardwareInput { current ->
                    val newX = if (pressed) -5 else if (current.joystickX == -5) 0 else current.joystickX
                    current.copy(joystickX = newX)
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                updateHardwareInput { current ->
                    val newX = if (pressed) 5 else if (current.joystickX == 5) 0 else current.joystickX
                    current.copy(joystickX = newX)
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                updateHardwareInput { current ->
                    val newY = if (pressed) 5 else if (current.joystickY == 5) 0 else current.joystickY
                    current.copy(joystickY = newY)
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                updateHardwareInput { current ->
                    val newY = if (pressed) -5 else if (current.joystickY == -5) 0 else current.joystickY
                    current.copy(joystickY = newY)
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_BUTTON_MODE -> {
                handleMappedAction(ControllerAction.ENTER, pressed)
            }
            else -> false
        }
    }

    private fun handleControllerMotion(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_MOVE) return false
        updateExternalControllerState()

        val horizontal = resolveAxis(event, MotionEvent.AXIS_X, MotionEvent.AXIS_HAT_X)
        val vertical = -resolveAxis(event, MotionEvent.AXIS_Y, MotionEvent.AXIS_HAT_Y)

        updateHardwareInput { it.copy(joystickX = horizontal, joystickY = vertical) }
        return true
    }

    private fun handleMappedAction(action: ControllerAction, pressed: Boolean): Boolean {
        when (action) {
            ControllerAction.ENTER -> updateHardwareInput { it.copy(enter = pressed) }
            ControllerAction.BACK -> updateHardwareInput { it.copy(back = pressed) }
            ControllerAction.HELP -> updateHardwareInput { it.copy(help = pressed) }
            ControllerAction.ABC -> updateHardwareInput { it.copy(abc = pressed) }
            ControllerAction.RED -> updateHardwareInput { it.copy(red = pressed) }
            ControllerAction.YELLOW -> updateHardwareInput { it.copy(yellow = pressed) }
            ControllerAction.BLUE -> updateHardwareInput { it.copy(blue = pressed) }
            ControllerAction.GREEN -> updateHardwareInput { it.copy(green = pressed) }
        }
        return true
    }

    private fun resolveAxis(event: MotionEvent, primaryAxis: Int, hatAxis: Int): Int {
        val primary = getCenteredAxisValue(event, primaryAxis)
        if (abs(primary) > 0.2f) {
            return (primary * 5f).roundToInt().coerceIn(-5, 5)
        }
        val hat = getCenteredAxisValue(event, hatAxis)
        return when {
            hat > 0.5f -> 5
            hat < -0.5f -> -5
            else -> 0
        }
    }

    private fun getCenteredAxisValue(event: MotionEvent, axis: Int): Float {
        val device = event.device ?: return 0f
        val range = device.getMotionRange(axis, event.source) ?: return 0f
        val value = event.getAxisValue(axis)
        return if (abs(value) > range.flat) value else 0f
    }

    private fun updateExternalControllerState() {
        val connected = InputDevice.getDeviceIds().any { deviceId ->
            val device = InputDevice.getDevice(deviceId)
            device != null && isGameController(device)
        }
        if (_externalControllerConnected.value != connected) {
            _externalControllerConnected.value = connected
            FileLogger.log("External controller connected: $connected")
        }
        if (!connected && hardwareControllerInput != ControllerInput.EMPTY) {
            hardwareControllerInput = ControllerInput.EMPTY
            sendCombinedInput()
        }
    }

    private fun isGameController(device: InputDevice?): Boolean {
        if (device == null || device.isVirtual) return false
        val sources = device.sources
        val hasGamepad = sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
        val hasJoystick = sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        val hasDpad = sources and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD
        return hasGamepad || hasJoystick || hasDpad
    }
    
    private fun loadAndStartEmulation(romUri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.i(TAG, "=== loadAndStartEmulation() BEGIN ===")
                Log.i(TAG, "Loading ROM: $romUri")
                
                FileLogger.logSection("loadAndStartEmulation() STARTED")
                FileLogger.logVar("romUri", romUri)
                
                // Get storage URI
                Log.d(TAG, "Getting storage URI...")
                FileLogger.log("Step 1: Getting storage URI...")
                val storageUriString = appPreferences.getStorageUri() ?: run {
                    Log.e(TAG, "No storage URI configured")
                    FileLogger.logError("✗ No storage URI configured")
                    return@launch
                }
                val storageUri = Uri.parse(storageUriString)
                Log.d(TAG, "Storage URI: $storageUri")
                FileLogger.logVar("storageUri", storageUri)
                
                // Load BIOS (optional)
                Log.d(TAG, "Looking for BIOS...")
                FileLogger.log("Step 2: Looking for BIOS file...")
                val biosUri = storageHelper.getBiosUri(storageUri)
                Log.d(TAG, "BIOS URI: $biosUri")
                FileLogger.logVar("biosUri", biosUri ?: "null (optional)")
                
                val biosData = biosUri?.let { 
                    Log.d(TAG, "Reading BIOS file...")
                    FileLogger.log("Reading BIOS file...")
                    storageHelper.readFile(it)
                }
                Log.d(TAG, "BIOS data: ${biosData?.size ?: 0} bytes")
                FileLogger.logVar("biosData.size", biosData?.size ?: 0)
                
                // Load ROM
                Log.d(TAG, "Reading ROM file...")
                FileLogger.log("Step 3: Reading ROM file...")
                val romData = storageHelper.readFile(romUri)
                if (romData == null) {
                    Log.e(TAG, "Failed to read ROM file")
                    FileLogger.logError("✗ Failed to read ROM file")
                    return@launch
                }
                Log.i(TAG, "ROM loaded: ${romData.size} bytes")
                FileLogger.logVar("romData.size", romData.size)
                FileLogger.log("✓ ROM file read successfully")
                
                // Initialize emulator
                Log.d(TAG, "Calling emulator.initialize()...")
                FileLogger.log("Step 4: Calling emulator.initialize()...")
                FileLogger.logVar("biosData", if (biosData != null) "${biosData.size} bytes" else "null")
                FileLogger.logVar("romData", "${romData.size} bytes")
                FileLogger.logVar("usePAL", true)
                
                val usePal = true
                configureTiming(usePal)
                audioResampler.reset()
                
                val success = emulator.initialize(
                    sysrom = biosData,
                    cartrom = romData,
                    usePAL = usePal  // Default to PAL timing
                )
                
                FileLogger.logVar("emulator.initialize() result", success)
                
                if (!success) {
                    Log.e(TAG, "Failed to initialize emulator")
                    FileLogger.logError("✗ emulator.initialize() returned false")
                    return@launch
                }
                Log.i(TAG, "Emulator initialized successfully")
                FileLogger.log("✓ Emulator initialized successfully")
                
                // Mark emulator as initialized
                isEmulatorInitialized = true
                
                // Press ON button to boot
                Log.d(TAG, "Pressing ON button...")
                FileLogger.log("Step 5: Pressing ON button to boot...")
                emulator.pressOnButton(true)
                delay(100)
                emulator.pressOnButton(false)
                Log.d(TAG, "ON button released")
                FileLogger.log("✓ ON button pressed and released")
                
                // Start audio playback
                audioManager.start()
                FileLogger.log("✓ Audio playback started")
                
                // Start emulation loop
                Log.d(TAG, "Starting emulation loop...")
                FileLogger.log("Step 6: Starting emulation loop...")
                withContext(Dispatchers.Main) {
                    startEmulationLoop()
                }
                
                Log.i(TAG, "=== loadAndStartEmulation() SUCCESS ===")
                FileLogger.log("✓✓✓ loadAndStartEmulation() completed successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "=== loadAndStartEmulation() FAILED ===", e)
                FileLogger.logError("✗✗✗ loadAndStartEmulation() CRASHED", e)
                throw e
            }
        }
    }
    
    private val reusableBitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.RGB_565)
    private val reusableBuffer = ByteBuffer.allocateDirect(320 * 240 * 2).order(ByteOrder.LITTLE_ENDIAN)
    
    private fun convertFrameToBitmap(frameData: ByteArray): Bitmap {
        // V.Smile PPU: RGB555+T → RGB565 conversion
        // This is the performance bottleneck - optimize heavily!
        
        reusableBuffer.clear()
        
        // Unroll and optimize the conversion loop
        var i = 0
        while (i < frameData.size - 1) {
            val low = frameData[i].toInt() and 0xFF
            val high = frameData[i + 1].toInt() and 0xFF
            val colorWord = (high shl 8) or low
            
            // Extract RGB555+T: [15:T][14-10:R][9-5:G][4-0:B]
            // Target RGB565: [15-11:R][10-5:G][4-0:B]
            val rgb565 = if ((colorWord and 0x8000) != 0) {
                0  // Transparent = black
            } else {
                // Shift R from bits [14-10] to [15-11]
                // Shift G from bits [9-5] to [10-5] (double it for 6-bit)
                // B stays at [4-0]
                val r5 = (colorWord shr 10) and 0x1F
                val g5 = (colorWord shr 5) and 0x1F
                val b5 = colorWord and 0x1F
                (r5 shl 11) or ((g5 shl 1) shl 5) or b5
            }
            
            reusableBuffer.putShort(rgb565.toShort())
            i += 2
        }
        
        reusableBuffer.rewind()
        reusableBitmap.copyPixelsFromBuffer(reusableBuffer)
        return reusableBitmap
    }
    
    private fun startEmulationLoop() {
        if (isRunning) {
            Log.e(TAG, "⚠⚠⚠ Emulation loop ALREADY RUNNING")
            FileLogger.log("⚠⚠⚠ startEmulationLoop() called but already running!")
            return
        }
        isRunning = true
        
        Log.e(TAG, "═══════════════════════════════════════")
        Log.e(TAG, "STARTING EMULATION LOOP")
        Log.e(TAG, "isRunning = $isRunning")
        Log.e(TAG, "═══════════════════════════════════════")
        FileLogger.logSection("startEmulationLoop() STARTED")
        FileLogger.log("isRunning = $isRunning")
        FileLogger.log("Launching coroutine...")
        
        emulationJob = lifecycleScope.launch(Dispatchers.Default) {
            var frameNumber = 0
            var lastFrameTime = System.nanoTime()
            
            Log.e(TAG, "✓✓✓ Inside emulation coroutine! Loop starting...")
            FileLogger.log("✓✓✓ Inside emulation coroutine!")
            FileLogger.log("isActive = $isActive")
            FileLogger.log("isRunning = $isRunning")
            FileLogger.log("Starting while loop...")
            
            while (isActive && isRunning) {
                val frameStartTime = System.nanoTime()
                
                try {
                    if (frameNumber < 3) {
                        Log.e(TAG, ">>> FRAME $frameNumber starting...")
                        FileLogger.log("Frame $frameNumber: calling emulator.runFrame()")
                    }
                    
                    // Run one frame of emulation
                    emulator.runFrame()
                    
                    if (frameNumber < 3) {
                        FileLogger.log("Frame $frameNumber: runFrame() returned")
                    }
                    
                    // Get audio samples and resample for playback
                    val rawAudioSamples = emulator.getAudioSamples()

                    if (_audioEnabled.value && rawAudioSamples != null && rawAudioSamples.isNotEmpty()) {
                        val processedAudio = audioResampler.resample(rawAudioSamples)

                        if (frameNumber < 5) {
                            val min = rawAudioSamples.minOrNull() ?: 0
                            val max = rawAudioSamples.maxOrNull() ?: 0
                            val avg = rawAudioSamples.average()
                            Log.d(TAG, "Frame $frameNumber audio raw: size=${rawAudioSamples.size}, min=$min, max=$max, avg=${"%.1f".format(avg)}")
                            FileLogger.log("Frame $frameNumber audio raw: size=${rawAudioSamples.size}, min=$min, max=$max, avg=${"%.1f".format(avg)}")

                            val minRes = processedAudio.minOrNull() ?: 0
                            val maxRes = processedAudio.maxOrNull() ?: 0
                            val avgRes = processedAudio.average()
                            Log.d(TAG, "Frame $frameNumber audio resampled: size=${processedAudio.size}, min=$minRes, max=$maxRes, avg=${"%.1f".format(avgRes)}")
                            FileLogger.log("Frame $frameNumber audio resampled: size=${processedAudio.size}, min=$minRes, max=$maxRes, avg=${"%.1f".format(avgRes)}")
                        }

                        val chunks = audioResampler.produceChunks(processedAudio)
                        for (chunk in chunks) {
                            audioManager.writeSamples(chunk)
                        }
                    }
                    
                    // Get frame buffer
                    val frameData = emulator.getFrameBuffer()
                    
                    if (frameNumber < 3) {
                        FileLogger.log("Frame $frameNumber: got ${frameData?.size ?: 0} bytes of frame data")
                    }
                    
                    if (frameData != null && frameData.size >= 320 * 240 * 2) {
                        // Convert to bitmap and update UI (wrap in FrameData to force StateFlow update)
                        val bitmap = convertFrameToBitmap(frameData)
                        _currentFrame.value = FrameData(bitmap, frameNumber.toLong())
                        
                        if (frameNumber < 3) {
                            Log.d(TAG, "Frame $frameNumber: bitmap size=${bitmap.width}x${bitmap.height}")
                            FileLogger.log("Frame $frameNumber: bitmap ${bitmap.width}x${bitmap.height}, converted and _currentFrame updated")
                        }
                        
                        // FPS counter
                        frameCount++
                        val now = System.currentTimeMillis()
                        if (now - lastFpsTime >= 1000) {
                            val fps = frameCount * 1000f / (now - lastFpsTime)
                            _currentFps.value = fps
                            Log.e(TAG, "✓✓✓ FPS: ${"%.1f".format(fps)} ✓✓✓")
                            FileLogger.log("FPS: ${"%.1f".format(fps)}")
                            frameCount = 0
                            lastFpsTime = now
                        }
                    } else {
                        Log.e(TAG, "Invalid frame data: ${frameData?.size ?: 0} bytes")
                        if (frameNumber < 10) {
                            FileLogger.log("Frame $frameNumber: INVALID frame data!")
                        }
                    }
                    
                    frameNumber++
                    
                    // Frame limiter: Sleep for remaining time to hit 60 FPS
                    val frameEndTime = System.nanoTime()
                    val frameTime = frameEndTime - frameStartTime
                    val targetFrameTime = targetFrameTimeNanos
                    val sleepTimeMs = (targetFrameTime - frameTime) / 1_000_000 // Convert to milliseconds
                    if (sleepTimeMs > 0) {
                        delay(sleepTimeMs)
                    }
                    
                    lastFrameTime = frameEndTime
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error in emulation loop at frame $frameNumber", e)
                    FileLogger.logError("✗✗✗ CRASH IN EMULATION LOOP at frame $frameNumber", e)
                    throw e
                }
            }
            Log.i(TAG, "Emulation loop stopped")
            FileLogger.log("Emulation loop stopped cleanly")
        }
    }
    
    override fun onPause() {
        super.onPause()
        isRunning = false
        emulationJob?.cancel()
        audioManager.stop()
        FileLogger.log("EmulationActivity paused - emulation and audio stopped")
    }
    
    override fun onResume() {
        super.onResume()
        FileLogger.log("EmulationActivity resumed")
        
        // Only restart the loop if the emulator was already initialized
        // (e.g., user paused and resumed). Don't start it on first launch -
        // loadAndStartEmulation() will do that after ROM loads.
        if (isEmulatorInitialized && !isRunning) {
            FileLogger.log("Restarting emulation loop and audio after resume...")
            audioManager.start()
            startEmulationLoop()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        FileLogger.log("EmulationActivity destroyed")
        emulationJob?.cancel()
        
        if (::audioManager.isInitialized) {
            FileLogger.log("Releasing audio resources...")
            audioManager.release()
        }
        
        if (::emulator.isInitialized) {
            FileLogger.log("Calling emulator.destroy()...")
            emulator.destroy()
            FileLogger.log("✓ Emulator destroyed")
        }
        
        if (::inputManager.isInitialized) {
            inputManager.unregisterInputDeviceListener(inputDeviceListener)
        }

        FileLogger.log("═══════════════════════════════════════════")
        FileLogger.log("Session ended - closing log file")
        FileLogger.close()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isControllerSource(event.source) && handleControllerKey(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (isControllerSource(event.source) && handleControllerMotion(event)) {
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }
}

@Composable
fun EmulationScreen(
    romName: String,
    currentFrameData: StateFlow<EmulationActivity.FrameData?>,
    currentFps: StateFlow<Float>,
    showFps: StateFlow<Boolean>,
    audioEnabled: StateFlow<Boolean>,
    pixelScale: StateFlow<EmulationActivity.PixelScale>,
    hideControllerWhenExternal: StateFlow<Boolean>,
    externalControllerConnected: StateFlow<Boolean>,
    colorButtonLayout: StateFlow<ColorButtonLayout>,
    colorButtonAnchor: StateFlow<ColorButtonAnchor>,
    controllerMappings: StateFlow<Map<ControllerAction, Int>>,
    emulator: EmulatorCore,
    onLoadRom: () -> Unit,
    onVirtualInputChange: (ControllerInput) -> Unit,
    onToggleFps: (Boolean) -> Unit,
    onToggleAudio: (Boolean) -> Unit,
    onChangeScale: (EmulationActivity.PixelScale) -> Unit,
    onToggleHideController: (Boolean) -> Unit,
    onChangeColorLayout: (ColorButtonLayout) -> Unit,
    onChangeColorAnchor: (ColorButtonAnchor) -> Unit,
    onUpdateControllerMapping: (ControllerAction, Int) -> Unit,
    onResetControllerMappings: () -> Unit,
    onBack: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var currentInput by remember { mutableStateOf(ControllerInput.EMPTY) }
    val frameData by currentFrameData.collectAsState()
    val frame = frameData?.bitmap
    val fps by currentFps.collectAsState()
    val isFpsVisible by showFps.collectAsState()
    val audio by audioEnabled.collectAsState()
    val scale by pixelScale.collectAsState()
    val hideController by hideControllerWhenExternal.collectAsState()
    val controllerConnected by externalControllerConnected.collectAsState()
    val layout by colorButtonLayout.collectAsState()
    val anchor by colorButtonAnchor.collectAsState()
    val controllerMapping by controllerMappings.collectAsState()
    val shouldShowVirtualController = remember(showSettings, hideController, controllerConnected) {
        !showSettings && (!hideController || !controllerConnected)
    }
    var showConnectionBanner by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        onLoadRom()
        delay(1000) // Give emulator time to init
        isLoading = false
    }
    
    // Forward virtual controller updates to activity
    LaunchedEffect(currentInput) {
        onVirtualInputChange(currentInput)
    }

    LaunchedEffect(shouldShowVirtualController) {
        if (!shouldShowVirtualController && currentInput != ControllerInput.EMPTY) {
            currentInput = ControllerInput.EMPTY
        }
    }

    LaunchedEffect(controllerConnected, hideController) {
        if (controllerConnected && hideController) {
            showConnectionBanner = true
            delay(2500)
            showConnectionBanner = false
        } else {
            showConnectionBanner = false
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Emulation screen with centered display and overlaid controls
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                val availableWidth = maxWidth
                val availableHeight = maxHeight
                val targetAspectRatio = 4f / 3f

                val widthLimitedHeight = availableWidth / targetAspectRatio
                val heightLimitedWidth = availableHeight * targetAspectRatio

                val baseWidth = if (widthLimitedHeight <= availableHeight) {
                    availableWidth
                } else {
                    heightLimitedWidth
                }
                val baseHeight = baseWidth / targetAspectRatio

                val displayWidth = baseWidth * scale.scaleFraction
                val displayHeight = baseHeight * scale.scaleFraction

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(displayWidth)
                        .height(displayHeight),
                    contentAlignment = Alignment.Center
                ) {
                    if (frame != null) {
                        Image(
                            bitmap = frame.asImageBitmap(),
                            contentDescription = "Game screen",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds,
                            alignment = Alignment.Center,
                            filterQuality = FilterQuality.None
                        )
                    } else {
                        Text(
                            text = "Waiting for first frame...",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }

            if (isFpsVisible && frame != null) {
                Text(
                    text = if (fps > 0f) "FPS: ${"%.1f".format(fps)}" else "FPS: --",
                    color = if (fps > 0f) Color.Green else Color.Red,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            IconButton(
                onClick = { showSettings = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White
                )
            }

            if (showSettings) {
                com.vsmileemu.android.ui.settings.TabbedSettingsScreen(
                    showFps = isFpsVisible,
                    audioEnabled = audio,
                    pixelScale = scale,
                    frameSkip = false, // TODO: implement
                    fastMath = true, // Already enabled in build
                    hideControllerWhenExternal = hideController,
                    controllerConnected = controllerConnected,
                    controllerLayout = layout,
                    controllerAnchor = anchor,
                    controllerMappings = controllerMapping,
                    onToggleFps = onToggleFps,
                    onToggleAudio = { onToggleAudio(it) },
                    onChangeScale = { onChangeScale(it) },
                    onToggleFrameSkip = { /* TODO */ },
                    onToggleFastMath = { /* Already enabled */ },
                    onToggleHideController = onToggleHideController,
                    onChangeControllerLayout = onChangeColorLayout,
                    onChangeControllerAnchor = onChangeColorAnchor,
                    onUpdateControllerMapping = onUpdateControllerMapping,
                    onResetControllerMappings = onResetControllerMappings,
                    onBack = { showSettings = false }
                )
            }

            if (shouldShowVirtualController) {
                VirtualController(
                    onInputChange = { newInput ->
                        currentInput = newInput
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(200.dp),
                    colorLayout = layout,
                    colorAnchor = anchor
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showConnectionBanner,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = "External controller active",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InGameSettingsScreen(
    audioEnabled: Boolean,
    pixelScale: EmulationActivity.PixelScale,
    onToggleAudio: (Boolean) -> Unit,
    onChangeScale: (EmulationActivity.PixelScale) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emulator Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Audio toggle section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Audio Output", style = MaterialTheme.typography.titleLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Audio", style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = audioEnabled,
                            onCheckedChange = onToggleAudio
                        )
                    }
                    if (audioEnabled) {
                        Text(
                            "Note: Audio may crackle during heavy emulation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Pixel scale options section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Screen Scale", style = MaterialTheme.typography.titleLarge)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        EmulationActivity.PixelScale.entries.forEach { scale ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = pixelScale == scale,
                                    onClick = { onChangeScale(scale) }
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(scale.displayName, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private class AudioResampler(
    private val inputSampleRate: Int = 281_250,
    private val outputSampleRate: Int = 48_000,
    private var targetFramesPerVideoFrame: Int = 800  // 48kHz / 60fps
) {
    private var leftover = ShortArray(0)

    fun resample(input: ShortArray): ShortArray {
        if (input.isEmpty()) return ShortArray(0)
        val framesIn = input.size / 2
        if (framesIn <= 0) return ShortArray(0)

        val framesOut = ((framesIn.toLong() * outputSampleRate + inputSampleRate / 2) / inputSampleRate)
            .toInt()
            .coerceAtLeast(1)
        val output = ShortArray(framesOut * 2)

        val step = inputSampleRate.toDouble() / outputSampleRate.toDouble()
        var position = 0.0
        var outIndex = 0
        for (frame in 0 until framesOut) {
            val index = position.toInt().coerceIn(0, framesIn - 1)
            val frac = position - index
            val nextIndex = if (index + 1 < framesIn) index + 1 else index
            val base = index * 2
            val nextBase = nextIndex * 2
            output[outIndex++] = interpolate(input[base], input[nextBase], frac)
            output[outIndex++] = interpolate(input[base + 1], input[nextBase + 1], frac)
            position += step
        }
        return output
    }

    fun produceChunks(resampled: ShortArray): List<ShortArray> {
        if (resampled.isEmpty()) return emptyList()

        val combined = ShortArray(leftover.size + resampled.size)
        leftover.copyInto(combined, 0, 0, leftover.size)
        resampled.copyInto(combined, leftover.size, 0, resampled.size)

        val chunks = mutableListOf<ShortArray>()
        var offset = 0
        val chunkSamples = targetFramesPerVideoFrame * 2

        while (offset + chunkSamples <= combined.size) {
            val chunk = ShortArray(chunkSamples)
            combined.copyInto(chunk, 0, offset, offset + chunkSamples)
            chunks.add(chunk)
            offset += chunkSamples
        }

        val remainingSamples = combined.size - offset
        leftover = if (remainingSamples > 0) {
            ShortArray(remainingSamples).also { combined.copyInto(it, 0, offset, combined.size) }
        } else ShortArray(0)

        return chunks
    }

    fun updateTargetFramesPerVideoFrame(frames: Int) {
        targetFramesPerVideoFrame = frames.coerceAtLeast(1)
    }

    fun reset() {
        leftover = ShortArray(0)
    }

    private fun interpolate(a: Short, b: Short, fraction: Double): Short {
        if (fraction <= 0.0) return a
        if (fraction >= 1.0) return b
        val aInt = a.toInt()
        val bInt = b.toInt()
        val value = aInt + (bInt - aInt) * fraction
        return value.roundToInt().coerceIn(-32768, 32767).toShort()
    }
}
