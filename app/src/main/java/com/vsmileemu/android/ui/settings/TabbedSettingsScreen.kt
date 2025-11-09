package com.vsmileemu.android.ui.settings

import androidx.activity.compose.BackHandler
import android.view.KeyEvent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vsmileemu.android.EmulationActivity
import com.vsmileemu.android.controller.ColorButtonAnchor
import com.vsmileemu.android.controller.ColorButtonLayout
import com.vsmileemu.android.controller.ControllerAction
import com.vsmileemu.android.controller.ControllerKeyOption
import com.vsmileemu.android.controller.controllerKeyOptions

enum class SettingsTab {
    GENERAL, EMULATION, CPU, AUDIO, CONTROLLER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabbedSettingsScreen(
    showFps: Boolean,
    audioEnabled: Boolean,
    pixelScale: EmulationActivity.PixelScale,
    frameSkip: Boolean,
    fastMath: Boolean,
    hideControllerWhenExternal: Boolean,
    controllerConnected: Boolean,
    controllerLayout: ColorButtonLayout,
    controllerAnchor: ColorButtonAnchor,
    controllerMappings: Map<ControllerAction, Int>,
    onToggleFps: (Boolean) -> Unit,
    onToggleAudio: (Boolean) -> Unit,
    onChangeScale: (EmulationActivity.PixelScale) -> Unit,
    onToggleFrameSkip: (Boolean) -> Unit,
    onToggleFastMath: (Boolean) -> Unit,
    onToggleHideController: (Boolean) -> Unit,
    onChangeControllerLayout: (ColorButtonLayout) -> Unit,
    onChangeControllerAnchor: (ColorButtonAnchor) -> Unit,
    onUpdateControllerMapping: (ControllerAction, Int) -> Unit,
    onResetControllerMappings: () -> Unit,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(SettingsTab.GENERAL) }
    
    // Handle back button to close settings instead of exiting activity
    BackHandler(onBack = onBack)
    
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
        ) {
            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == SettingsTab.GENERAL,
                    onClick = { selectedTab = SettingsTab.GENERAL },
                    text = { Text("General") }
                )
                Tab(
                    selected = selectedTab == SettingsTab.EMULATION,
                    onClick = { selectedTab = SettingsTab.EMULATION },
                    text = { Text("Emulation") }
                )
                Tab(
                    selected = selectedTab == SettingsTab.CPU,
                    onClick = { selectedTab = SettingsTab.CPU },
                    text = { Text("CPU") }
                )
                Tab(
                    selected = selectedTab == SettingsTab.AUDIO,
                    onClick = { selectedTab = SettingsTab.AUDIO },
                    text = { Text("Audio") }
                )
                Tab(
                    selected = selectedTab == SettingsTab.CONTROLLER,
                    onClick = { selectedTab = SettingsTab.CONTROLLER },
                    text = { Text("Controller") }
                )
            }
            
            // Tab Content
            when (selectedTab) {
                SettingsTab.GENERAL -> GeneralSettings(showFps, onToggleFps)
                SettingsTab.EMULATION -> EmulationSettings(
                    pixelScale,
                    frameSkip,
                    onChangeScale,
                    onToggleFrameSkip
                )
                SettingsTab.CPU -> CpuSettings(fastMath, onToggleFastMath)
                SettingsTab.AUDIO -> AudioSettings(audioEnabled, onToggleAudio)
                SettingsTab.CONTROLLER -> ControllerSettings(
                    hideControllerWhenExternal = hideControllerWhenExternal,
                    controllerConnected = controllerConnected,
                    currentLayout = controllerLayout,
                    currentAnchor = controllerAnchor,
                    mappings = controllerMappings,
                    availableKeys = controllerKeyOptions,
                    onToggleHideController = onToggleHideController,
                    onChangeLayout = onChangeControllerLayout,
                    onChangeAnchor = onChangeControllerAnchor,
                    onUpdateMapping = onUpdateControllerMapping,
                    onResetMappings = onResetControllerMappings
                )
            }
        }
    }
}

@Composable
fun GeneralSettings(
    showFps: Boolean,
    onToggleFps: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "General Settings",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("FPS Counter", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Show frame rate overlay",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = showFps, onCheckedChange = onToggleFps)
                }
            }
        }
    }
}

@Composable
fun EmulationSettings(
    pixelScale: EmulationActivity.PixelScale,
    frameSkip: Boolean,
    onChangeScale: (EmulationActivity.PixelScale) -> Unit,
    onToggleFrameSkip: (Boolean) -> Unit
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Emulation Settings",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        
        // Frame Skip toggle
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Frame Skip", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Skip frames to maintain speed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = frameSkip, onCheckedChange = onToggleFrameSkip)
                }
            }
        }
        
        // Screen Scale options
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Screen Scale", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Choose display resolution",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                EmulationActivity.PixelScale.entries.forEach { scale ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onChangeScale(scale) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = pixelScale == scale,
                                onClick = null // Click handled by Surface
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = scale.displayName,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = scale.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CpuSettings(
    fastMath: Boolean,
    onToggleFastMath: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "CPU Settings",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Fast Math Mode", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Use faster floating-point calculations",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = fastMath, onCheckedChange = onToggleFastMath)
                }
            }
        }
        
        Text(
            "Note: Fast math is already enabled in the build",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
fun AudioSettings(
    audioEnabled: Boolean,
    onToggleAudio: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Audio Settings",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Audio", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Play game audio (27kHz stereo)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = audioEnabled, onCheckedChange = onToggleAudio)
                }
                
                if (audioEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "⚠️ Audio may crackle during heavy emulation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun ControllerSettings(
    hideControllerWhenExternal: Boolean,
    controllerConnected: Boolean,
    currentLayout: ColorButtonLayout,
    currentAnchor: ColorButtonAnchor,
    mappings: Map<ControllerAction, Int>,
    availableKeys: List<ControllerKeyOption>,
    onToggleHideController: (Boolean) -> Unit,
    onChangeLayout: (ColorButtonLayout) -> Unit,
    onChangeAnchor: (ColorButtonAnchor) -> Unit,
    onUpdateMapping: (ControllerAction, Int) -> Unit,
    onResetMappings: () -> Unit
) {
    val scrollState = rememberScrollState()
    val keyLabelMap = remember(availableKeys) {
        availableKeys.associate { it.keyCode to it.label }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Controller Settings",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Hide Virtual Controller",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Automatically hide when a gamepad is connected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = hideControllerWhenExternal,
                        onCheckedChange = onToggleHideController
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    if (controllerConnected) "External controller detected"
                    else "No external controller connected",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (controllerConnected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Virtual Controller Layout",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Choose how the on-screen color buttons are arranged.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ColorButtonLayout.entries.forEach { layout ->
                        FilterChip(
                            selected = currentLayout == layout,
                            onClick = { onChangeLayout(layout) },
                            label = { Text(layout.displayName) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Button Anchor",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ColorButtonAnchor.entries.forEach { anchor ->
                        FilterChip(
                            selected = currentAnchor == anchor,
                            onClick = { onChangeAnchor(anchor) },
                            label = { Text(anchor.displayName) }
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Controller Mapping",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Tap an action to assign a different hardware button.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ControllerAction.entries.forEach { action ->
                    var expanded by remember { mutableStateOf(false) }
                    val currentKey = mappings[action] ?: action.defaultKeyCode
                    val currentLabel = keyLabelMap[currentKey]
                        ?: KeyEvent.keyCodeToString(currentKey).removePrefix("KEYCODE_")

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(action.displayName, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    currentLabel,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Box {
                                    OutlinedButton(onClick = { expanded = true }) {
                                        Text("Change")
                                    }
                                    DropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false }
                                    ) {
                                        availableKeys.forEach { option ->
                                            DropdownMenuItem(
                                                text = { Text(option.label) },
                                                onClick = {
                                                    expanded = false
                                                    onUpdateMapping(action, option.keyCode)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onResetMappings) {
                    Text("Reset to defaults")
                }
            }
        }
    }
}

